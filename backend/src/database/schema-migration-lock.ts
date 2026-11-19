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

export async function tryAcquireJehoSchemaLock(
  runner: QueryRunner,
): Promise<boolean> {
  const rows = await runner.query(
    'SELECT pg_try_advisory_lock($1, $2) AS locked',
    [JEHO_SCHEMA_LOCK_NAMESPACE, JEHO_SCHEMA_LOCK_ID],
  );
  return rows?.[0]?.locked === true;
}

export async function releaseJehoSchemaLock(runner: QueryRunner): Promise<void> {
  await runner.query(
    'SELECT pg_advisory_unlock($1, $2)',
    [JEHO_SCHEMA_LOCK_NAMESPACE, JEHO_SCHEMA_LOCK_ID],
  );
}
