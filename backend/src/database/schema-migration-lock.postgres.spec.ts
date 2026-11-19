import assert from 'node:assert/strict';
import test from 'node:test';
import { DataSource } from 'typeorm';
import {
  JEHO_SCHEMA_LOCK_ID,
  JEHO_SCHEMA_LOCK_NAMESPACE,
  releaseJehoSessionAdvisoryLock,
  tryAcquireJehoSessionAdvisoryLock,
} from './schema-migration-lock';

const databaseUrl = process.env.SCHEMA_LOCK_TEST_DATABASE_URL;

test('JEHO schema advisory lock coordinates independent PostgreSQL sessions', {
  skip: !databaseUrl,
}, async () => {
  const dataSource = new DataSource({
    type: 'postgres',
    url: databaseUrl,
    extra: { max: 4 },
  });
  await dataSource.initialize();

  const first = dataSource.createQueryRunner();
  const second = dataSource.createQueryRunner();
  let firstOwnsLock = false;
  let secondOwnsLock = false;

  try {
    await Promise.all([first.connect(), second.connect()]);

    firstOwnsLock = await tryAcquireJehoSessionAdvisoryLock(first, JEHO_SCHEMA_LOCK_ID);
    assert.equal(firstOwnsLock, true, 'first PostgreSQL session should acquire the schema lock');

    secondOwnsLock = await tryAcquireJehoSessionAdvisoryLock(second, JEHO_SCHEMA_LOCK_ID);
    assert.equal(secondOwnsLock, false, 'a separate PostgreSQL session must not acquire an owned session lock');

    await releaseJehoSessionAdvisoryLock(first, JEHO_SCHEMA_LOCK_ID);
    firstOwnsLock = false;

    secondOwnsLock = await tryAcquireJehoSessionAdvisoryLock(second, JEHO_SCHEMA_LOCK_ID);
    assert.equal(secondOwnsLock, true, 'second PostgreSQL session should acquire after the first releases');

    await releaseJehoSessionAdvisoryLock(second, JEHO_SCHEMA_LOCK_ID);
    secondOwnsLock = false;

    const remaining = await second.query(
      'SELECT COUNT(*)::int AS count FROM pg_locks WHERE locktype = $1 AND classid = $2 AND objid = $3 AND objsubid = $4 AND pid = pg_backend_pid()',
      ['advisory', JEHO_SCHEMA_LOCK_NAMESPACE, JEHO_SCHEMA_LOCK_ID, 2],
    );
    assert.equal(remaining[0]?.count, 0, 'test session must not retain the JEHO schema advisory lock');
  } finally {
    if (firstOwnsLock) {
      await releaseJehoSessionAdvisoryLock(first, JEHO_SCHEMA_LOCK_ID).catch(() => undefined);
    }
    if (secondOwnsLock) {
      await releaseJehoSessionAdvisoryLock(second, JEHO_SCHEMA_LOCK_ID).catch(() => undefined);
    }
    await Promise.all([first.release(), second.release()]);
    await dataSource.destroy();
  }
});
