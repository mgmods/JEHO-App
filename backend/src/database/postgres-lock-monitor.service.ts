import { Injectable, Logger, OnModuleDestroy, OnModuleInit } from '@nestjs/common';
import { DataSource } from 'typeorm';
import { JEHO_SCHEMA_LOCK_NAMESPACE, JEHO_ADVISORY_LOCKS, tryAcquireJehoSessionAdvisoryLock, releaseJehoAdvisoryLock } from './schema-migration-lock';

/**
 * Lightweight, cluster-serialized diagnostics for PostgreSQL lock queues.
 * It does not terminate sessions or mutate application data. The output names
 * the blocked query and its actual blocker so production lock incidents can be
 * fixed from evidence instead of guessing from lock_timeout messages.
 */
@Injectable()
export class PostgresLockMonitorService implements OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger(PostgresLockMonitorService.name);
  private timer: ReturnType<typeof setInterval> | null = null;
  private running = false;
  private warnedUnavailable = false;
  private lastBlockerSignature = '';
  private lastBlockerLogAt = 0;

  constructor(private readonly dataSource: DataSource) {}

  onModuleInit(): void {
    this.timer = setInterval(() => void this.inspect(), 10_000);
    this.timer.unref?.();
    void this.inspect();
  }

  onModuleDestroy(): void {
    if (this.timer) clearInterval(this.timer);
    this.timer = null;
  }

  private async inspect(): Promise<void> {
    if (this.running || !this.dataSource.isInitialized) return;
    this.running = true;
    const runner = this.dataSource.createQueryRunner();
    let monitorLockAcquired = false;
    try {
      await runner.connect();
      await runner.query("SET lock_timeout = '250ms'");
      await runner.query("SET statement_timeout = '3s'");
      monitorLockAcquired = await tryAcquireJehoSessionAdvisoryLock(runner, JEHO_ADVISORY_LOCKS.lockMonitor);
      if (!monitorLockAcquired) return;

      // Recover only orphaned legacy JEHO schema DDL sessions.
      // Normal business transactions and pg_dump sessions are never terminated.
      const staleSchemaSql = [
        "SELECT pid::int AS pid, LEFT(COALESCE(query, ''), 500) AS query,",
        "GREATEST(0, EXTRACT(EPOCH FROM (clock_timestamp() - xact_start)))::int AS xact_age_seconds,",
        "COALESCE(application_name, '') AS application_name",
        "FROM pg_stat_activity",
        "WHERE datname = current_database()",
        "  AND pid <> pg_backend_pid()",
        "  AND state = 'active'",
        "  AND xact_start IS NOT NULL",
        "  AND clock_timestamp() - xact_start > interval '15 minutes'",
        "  AND COALESCE(application_name, '') IN ('', 'jeho-own-api', 'jeho-own-bootstrap')",
        "  AND query ~* '^\\s*ALTER\\s+TABLE\\s+(?:\"?jeho_own\"?\\.)?\"?[A-Za-z_][A-Za-z0-9_]*\"?'",
        "ORDER BY xact_start ASC",
        "LIMIT 10",
      ].join('\\n');
      const staleSchemaSessions = await runner.query(staleSchemaSql);
      for (const session of staleSchemaSessions) {
        try {
          const terminated = await runner.query('SELECT pg_terminate_backend($1) AS terminated', [session.pid]);
          if (terminated?.[0]?.terminated === true) {
            this.logger.warn(`[JEHO DB LOCK RECOVERY] Terminated stale schema DDL session pid=${session.pid}, age=${session.xact_age_seconds}s, query=${session.query}`);
          }
        } catch (error) {
          this.logger.warn(`[JEHO DB LOCK RECOVERY] Could not terminate stale schema DDL pid=${session.pid}: ${error instanceof Error ? error.message : String(error)}`);
        }
      }
      const blockers = await runner.query(`
        SELECT
          blocked.pid::int AS blocked_pid,
          COALESCE(blocked.application_name, '') AS blocked_application,
          COALESCE(blocked.state, '') AS blocked_state,
          COALESCE(blocked.wait_event, '') AS blocked_wait_event,
          GREATEST(0, EXTRACT(EPOCH FROM (clock_timestamp() - blocked.query_start)))::int AS blocked_for_seconds,
          CASE WHEN blocked.xact_start IS NULL THEN NULL
            ELSE GREATEST(0, EXTRACT(EPOCH FROM (clock_timestamp() - blocked.xact_start)))::int
          END AS blocked_transaction_seconds,
          LEFT(COALESCE(blocked.query, ''), 500) AS blocked_query,
          blocker.pid::int AS blocker_pid,
          COALESCE(blocker.application_name, '') AS blocker_application,
          COALESCE(blocker.state, '') AS blocker_state,
          CASE WHEN blocker.xact_start IS NULL THEN NULL
            ELSE GREATEST(0, EXTRACT(EPOCH FROM (clock_timestamp() - blocker.xact_start)))::int
          END AS blocker_transaction_seconds,
          LEFT(COALESCE(blocker.query, ''), 500) AS blocker_query
        FROM pg_stat_activity AS blocked
        CROSS JOIN LATERAL unnest(pg_blocking_pids(blocked.pid)) AS blockers(pid)
        JOIN pg_stat_activity AS blocker ON blocker.pid = blockers.pid
        WHERE blocked.datname = current_database()
          AND blocked.usename = current_user
          AND blocked.pid <> pg_backend_pid()
          AND blocked.state = 'active'
          AND blocked.wait_event_type = 'Lock'
        ORDER BY blocked.query_start ASC
        LIMIT 20
      `);

      if (blockers.length) {
        const signature = blockers
          .map((row: any) => `${row.blocked_pid}:${row.blocker_pid}:${row.blocked_query}`)
          .join('|');
        const now = Date.now();
        if (signature !== this.lastBlockerSignature || now - this.lastBlockerLogAt >= 10_000) {
          this.logger.error(
            `[JEHO DB LOCK MONITOR] Active PostgreSQL blockers: ${JSON.stringify(blockers)}`,
          );
          this.lastBlockerSignature = signature;
          this.lastBlockerLogAt = now;
        }
      } else {
        this.lastBlockerSignature = '';
      }
      this.warnedUnavailable = false;
    } catch (error) {
      if (!this.warnedUnavailable) {
        this.warnedUnavailable = true;
        this.logger.warn(
          `PostgreSQL lock diagnostics unavailable: ${error instanceof Error ? error.message : String(error)}`,
        );
      }
    } finally {
      if (monitorLockAcquired) {
        await releaseJehoAdvisoryLock(runner, JEHO_SCHEMA_LOCK_NAMESPACE, JEHO_ADVISORY_LOCKS.lockMonitor).catch((unlockError) => this.logger.warn(`Lock monitor advisory unlock failed; connection discarded: ${(unlockError as Error).message}`));
      }
      await runner.query('RESET lock_timeout').catch(() => undefined);
      await runner.query('RESET statement_timeout').catch(() => undefined);
      await runner.release().catch(() => undefined);
      this.running = false;
    }
  }
}
