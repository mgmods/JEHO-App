import { QueryRunner } from 'typeorm';

/**
 * One database-wide JEHO schema-migration mutex.
 *
 * PostgreSQL advisory locks coordinate separate API replicas. The local
 * non-blocking gate prevents Nest providers in the same process from polling
 * PostgreSQL concurrently for the same lock. A busy local gate returns false,
 * allowing each bootstrap's existing bounded retry timer to try again later.
 */
export const JEHO_SCHEMA_LOCK_NAMESPACE = 741902611;
export const JEHO_ADVISORY_LOCKS = {
  schema: 99,
  roomAutoClear: 2001,
  vipReconciliation: 2002,
  storyExpiry: 2003,
  lockMonitor: 2004,
  walletStartup: 2005,
  publicIdBackfill: 2006,
  giftStartup: 2007,
} as const;
export const JEHO_SCHEMA_LOCK_ID = JEHO_ADVISORY_LOCKS.schema;

async function discardPostgresRunner(runner: QueryRunner, error: unknown): Promise<void> {
  const postgresRunner = runner as QueryRunner & {
    releasePostgresConnection?: (err?: Error) => Promise<void>;
  };
  if (typeof postgresRunner.releasePostgresConnection === 'function') {
    await postgresRunner.releasePostgresConnection(
      error instanceof Error ? error : new Error(String(error)),
    ).catch(() => undefined);
  }
}

export async function tryAcquireJehoSessionAdvisoryLock(
  runner: QueryRunner,
  lockId: number,
): Promise<boolean> {
  const rows = await runner.query(
    'SELECT pg_try_advisory_lock($1, $2) AS locked',
    [JEHO_SCHEMA_LOCK_NAMESPACE, lockId],
  );
  return rows?.[0]?.locked === true;
}

export async function releaseJehoSessionAdvisoryLock(
  runner: QueryRunner,
  lockId: number,
): Promise<void> {
  await releaseJehoAdvisoryLock(runner, JEHO_SCHEMA_LOCK_NAMESPACE, lockId);
}

let localLockHeld = false;
const localLockOwners = new WeakSet<QueryRunner>();

export async function tryAcquireJehoSchemaLock(
  runner: QueryRunner,
): Promise<boolean> {
  // PostgreSQL session advisory locks are re-entrant; avoid incrementing the
  // lock count if a caller accidentally asks twice on the same QueryRunner.
  if (localLockOwners.has(runner)) return true;
  if (localLockHeld) return false;

  localLockHeld = true;
  try {
    const locked = await tryAcquireJehoSessionAdvisoryLock(runner, JEHO_SCHEMA_LOCK_ID);
    if (locked) {
      localLockOwners.add(runner);
    } else {
      localLockHeld = false;
    }
    return locked;
  } catch (error) {
    localLockHeld = false;
    throw error;
  }
}

/**
 * Release a JEHO session advisory lock and discard its PostgreSQL client if
 * the unlock cannot be confirmed. Never return a possibly locked session to
 * TypeORM's connection pool.
 */
export async function releaseJehoAdvisoryLock(
  runner: QueryRunner,
  namespace: number,
  lockId: number,
): Promise<void> {
  try {
    const rows = await runner.query(
      'SELECT pg_advisory_unlock($1, $2) AS unlocked',
      [namespace, lockId],
    );
    if (rows?.[0]?.unlocked !== true) {
      throw new Error(`PostgreSQL advisory lock ${namespace}/${lockId} was not released by its owning session.`);
    }
  } catch (error) {
    const postgresRunner = runner as QueryRunner & {
      releasePostgresConnection?: (err?: Error) => Promise<void>;
    };
    if (typeof postgresRunner.releasePostgresConnection === 'function') {
      await postgresRunner.releasePostgresConnection(
        error instanceof Error ? error : new Error(String(error)),
      ).catch(() => undefined);
    }
    throw error;
  }
}

export async function releaseJehoSchemaLock(runner: QueryRunner): Promise<void> {
  const ownsLocalLock = localLockOwners.has(runner);
  if (!ownsLocalLock) return;
  localLockOwners.delete(runner);
  try {
    await releaseJehoSessionAdvisoryLock(runner, JEHO_SCHEMA_LOCK_ID);
  } finally {
    localLockHeld = false;
  }
}
