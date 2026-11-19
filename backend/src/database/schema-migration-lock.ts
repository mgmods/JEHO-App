import { QueryRunner } from 'typeorm';

/**
 * One database-wide JEHO schema-migration mutex.
 *
 * A fair process-local FIFO gate serializes Nest providers before they contend
 * for PostgreSQL's session advisory lock. This avoids the 30-second retry
 * convoy that occurs when every provider races the same non-blocking lock at
 * startup. PostgreSQL still coordinates separate API replicas.
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

const localLockOwners = new WeakSet<QueryRunner>();
const localLockReleaseTurns = new WeakMap<QueryRunner, () => void>();
let localLockTail: Promise<void> = Promise.resolve();

export async function tryAcquireJehoSchemaLock(
  runner: QueryRunner,
): Promise<boolean> {
  // PostgreSQL session advisory locks are re-entrant; avoid incrementing the
  // lock count if a caller accidentally asks twice on the same QueryRunner.
  if (localLockOwners.has(runner)) return true;

  // Queue local providers fairly. Only the head of this queue contacts
  // PostgreSQL, so a single API process doesn't make every bootstrap wait
  // for its own 30-second retry timer.
  const previousTurn = localLockTail;
  let releaseTurn!: () => void;
  const currentTurn = new Promise<void>((resolve) => {
    releaseTurn = resolve;
  });
  localLockTail = previousTurn.then(() => currentTurn);
  await previousTurn;

  try {
    const locked = await tryAcquireJehoSessionAdvisoryLock(runner, JEHO_SCHEMA_LOCK_ID);
    if (locked) {
      localLockOwners.add(runner);
      localLockReleaseTurns.set(runner, releaseTurn);
      return true;
    }
    releaseTurn();
    return false;
  } catch (error) {
    releaseTurn();
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
  const releaseTurn = localLockReleaseTurns.get(runner);
  localLockReleaseTurns.delete(runner);
  try {
    await releaseJehoSessionAdvisoryLock(runner, JEHO_SCHEMA_LOCK_ID);
  } finally {
    // Always advance the local queue, even if PostgreSQL required discarding
    // the connection while confirming the unlock.
    releaseTurn?.();
  }
}
