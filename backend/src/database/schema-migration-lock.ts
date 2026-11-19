import { QueryRunner } from 'typeorm';

/**
 * One database-wide JEHO schema-migration mutex.
 *
 * Every application-owned DDL/bootstrap path must use this same advisory-lock
 * pair. Session locks are used for multi-statement, autocommit migrations;
 * transaction-scoped locks may use the same pair for atomic migrations.
 */
export const JEHO_SCHEMA_LOCK_NAMESPACE = 741902611;
export const JEHO_SCHEMA_LOCK_ID = 99;

// PostgreSQL advisory locks coordinate separate processes/replicas. This
// additional FIFO mutex prevents every Nest provider in one API process from
// repeatedly polling PostgreSQL for the same lock at the same time.
const localWaiters: Array<() => void> = [];
let localLockHeld = false;
const localLockOwners = new WeakSet<QueryRunner>();

async function acquireLocalSchemaLock(): Promise<void> {
  if (!localLockHeld) {
    localLockHeld = true;
    return;
  }

  await new Promise<void>((resolve) => {
    localWaiters.push(resolve);
  });
}

function releaseLocalSchemaLock(): void {
  const next = localWaiters.shift();
  if (next) {
    next();
  } else {
    localLockHeld = false;
  }
}

export async function tryAcquireJehoSchemaLock(
  runner: QueryRunner,
): Promise<boolean> {
  // PostgreSQL session advisory locks are re-entrant; avoid incrementing the
  // lock count if a caller accidentally asks twice on the same QueryRunner.
  if (localLockOwners.has(runner)) return true;

  await acquireLocalSchemaLock();
  try {
    const rows = await runner.query(
      'SELECT pg_try_advisory_lock($1, $2) AS locked',
      [JEHO_SCHEMA_LOCK_NAMESPACE, JEHO_SCHEMA_LOCK_ID],
    );
    const locked = rows?.[0]?.locked === true;
    if (locked) {
      localLockOwners.add(runner);
    } else {
      releaseLocalSchemaLock();
    }
    return locked;
  } catch (error) {
    releaseLocalSchemaLock();
    throw error;
  }
}

export async function releaseJehoSchemaLock(runner: QueryRunner): Promise<void> {
  const ownsLocalLock = localLockOwners.delete(runner);
  try {
    await runner.query(
      'SELECT pg_advisory_unlock($1, $2)',
      [JEHO_SCHEMA_LOCK_NAMESPACE, JEHO_SCHEMA_LOCK_ID],
    );
  } finally {
    if (ownsLocalLock) {
      releaseLocalSchemaLock();
    }
  }
}
