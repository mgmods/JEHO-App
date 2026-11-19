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
export const JEHO_SCHEMA_LOCK_ID = 99;

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
    const rows = await runner.query(
      'SELECT pg_try_advisory_lock($1, $2) AS locked',
      [JEHO_SCHEMA_LOCK_NAMESPACE, JEHO_SCHEMA_LOCK_ID],
    );
    const locked = rows?.[0]?.locked === true;
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

export async function releaseJehoSchemaLock(runner: QueryRunner): Promise<void> {
  const ownsLocalLock = localLockOwners.delete(runner);
  try {
    await runner.query(
      'SELECT pg_advisory_unlock($1, $2)',
      [JEHO_SCHEMA_LOCK_NAMESPACE, JEHO_SCHEMA_LOCK_ID],
    );
  } finally {
    if (ownsLocalLock) {
      localLockHeld = false;
    }
  }
}
