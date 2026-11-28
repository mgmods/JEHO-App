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
  lockMonitor: 2004,
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
  try {
    const rows = await runner.query(
      'SELECT pg_try_advisory_lock($1, $2) AS locked',
      [JEHO_SCHEMA_LOCK_NAMESPACE, lockId],
    );
    return rows?.[0]?.locked === true;
  } catch (error) {
    // The server may have granted the session lock before the client lost the
    // response. Discard this connection so an unconfirmed lock cannot be pooled.
    await discardPostgresRunner(runner, error);
    throw error;
  }
}

export async function releaseJehoSessionAdvisoryLock(
  runner: QueryRunner,
  lockId: number,
): Promise<void> {
  await releaseJehoAdvisoryLock(runner, JEHO_SCHEMA_LOCK_NAMESPACE, lockId);
}

// Serialize every in-process migration through a FIFO gate before asking
// PostgreSQL for the cluster-wide session advisory lock. Returning false merely
// because another provider in this same Nest process is migrating caused
// unrelated bootstraps to defer for 30 seconds even when PostgreSQL was free.
let localLockTail: Promise<void> = Promise.resolve();
const localLockOwners = new WeakSet<QueryRunner>();
const localLockReleases = new WeakMap<QueryRunner, () => void>();

export async function tryAcquireJehoSchemaLock(
  runner: QueryRunner,
): Promise<boolean> {
  // Session advisory locks are re-entrant; do not increment the lock count if
  // the same pinned QueryRunner requests ownership more than once.
  if (localLockOwners.has(runner)) return true;

  let releaseLocal!: () => void;
  const previousLocalOwner = localLockTail;
  localLockTail = new Promise<void>((resolve) => {
    releaseLocal = resolve;
  });
  await previousLocalOwner;

  try {
    const locked = await tryAcquireJehoSessionAdvisoryLock(runner, JEHO_SCHEMA_LOCK_ID);
    if (locked) {
      localLockOwners.add(runner);
      localLockReleases.set(runner, releaseLocal);
      return true;
    }
    releaseLocal();
    return false;
  } catch (error) {
    releaseLocal();
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
    await discardPostgresRunner(runner, error);
    throw error;
  }
}

export async function releaseJehoSchemaLock(runner: QueryRunner): Promise<void> {
  if (!localLockOwners.has(runner)) return;
  localLockOwners.delete(runner);
  const releaseLocal = localLockReleases.get(runner);
  localLockReleases.delete(runner);
  try {
    await releaseJehoSessionAdvisoryLock(runner, JEHO_SCHEMA_LOCK_ID);
  } finally {
    // Always hand the local gate to the next waiter, even if PostgreSQL could
    // not confirm unlock (the underlying connection is discarded in that case).
    releaseLocal?.();
  }
}
