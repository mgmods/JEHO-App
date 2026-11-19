import assert from 'node:assert/strict';
import test from 'node:test';
import { QueryRunner } from 'typeorm';
import {
  JEHO_SCHEMA_LOCK_NAMESPACE,
  JEHO_SCHEMA_LOCK_ID,
  releaseJehoSchemaLock,
  tryAcquireJehoSchemaLock,
} from './schema-migration-lock';

type QueryHandler = (sql: string, parameters?: unknown[]) => Promise<any>;

function fakeRunner(name: string, handler: QueryHandler, events: string[], discarded: { count: number }): QueryRunner {
  const runner = {
    query: async (sql: string, parameters?: unknown[]) => {
      events.push(name + ':' + (sql.includes('pg_try_advisory_lock') ? 'acquire' : sql.includes('pg_advisory_unlock') ? 'unlock' : 'other'));
      return handler(sql, parameters);
    },
    release: async () => undefined,
    releasePostgresConnection: async () => { discarded.count += 1; },
  };
  return runner as unknown as QueryRunner;
}

test('JEHO schema lock serializes FIFO and advances after failed acquire/unlock', async () => {
  const events: string[] = [];
  const discarded = { count: 0 };
  const a = fakeRunner('a', async (sql) => sql.includes('pg_try_advisory_lock')
    ? [{ locked: true }] : [{ unlocked: true }], events, discarded);
  const b = fakeRunner('b', async (sql) => sql.includes('pg_try_advisory_lock')
    ? [{ locked: true }] : [{ unlocked: true }], events, discarded);

  assert.equal(await tryAcquireJehoSchemaLock(a), true);
  const waitingB = tryAcquireJehoSchemaLock(b);
  await Promise.resolve();
  assert.deepEqual(events, ['a:acquire']);
  await releaseJehoSchemaLock(a);
  assert.equal(await waitingB, true);
  assert.deepEqual(events, ['a:acquire', 'a:unlock', 'b:acquire']);
  await releaseJehoSchemaLock(b);

  const queued = Array.from({ length: 12 }, (_, index) =>
    fakeRunner(`q${index}`, async (sql) => sql.includes('pg_try_advisory_lock')
      ? [{ locked: true }] : [{ unlocked: true }], events, discarded),
  );
  const queuedAcquisitions = queued.map((runner) => tryAcquireJehoSchemaLock(runner));
  for (let index = 0; index < queued.length; index += 1) {
    assert.equal(await queuedAcquisitions[index], true);
    await releaseJehoSchemaLock(queued[index]);
  }
  const queuedOrder = events
    .filter((event) => /^q\\d+:acquire$/.test(event))
    .map((event) => Number(event.slice(1, event.indexOf(':'))));
  assert.deepEqual(queuedOrder, Array.from({ length: 12 }, (_, index) => index));

  const busy = fakeRunner('busy', async (sql) => sql.includes('pg_try_advisory_lock')
    ? [{ locked: false }] : [{ unlocked: true }], events, discarded);
  const afterBusy = fakeRunner('after-busy', async (sql) => sql.includes('pg_try_advisory_lock')
    ? [{ locked: true }] : [{ unlocked: true }], events, discarded);
  assert.equal(await tryAcquireJehoSchemaLock(busy), false);
  assert.equal(await tryAcquireJehoSchemaLock(afterBusy), true);
  await releaseJehoSchemaLock(afterBusy);

  const failing = fakeRunner('failing', async (sql) => {
    if (sql.includes('pg_try_advisory_lock')) throw new Error('simulated connection failure');
    return [{ unlocked: true }];
  }, events, discarded);
  const afterFailure = fakeRunner('after-failure', async (sql) => sql.includes('pg_try_advisory_lock')
    ? [{ locked: true }] : [{ unlocked: true }], events, discarded);
  await assert.rejects(tryAcquireJehoSchemaLock(failing), /simulated connection failure/);
  assert.equal(await tryAcquireJehoSchemaLock(afterFailure), true);
  await releaseJehoSchemaLock(afterFailure);

  const unlockFailure = fakeRunner('unlock-failure', async (sql) => sql.includes('pg_try_advisory_lock')
    ? [{ locked: true }] : [{ unlocked: false }], events, discarded);
  const afterUnlockFailure = fakeRunner('after-unlock-failure', async (sql) => sql.includes('pg_try_advisory_lock')
    ? [{ locked: true }] : [{ unlocked: true }], events, discarded);
  assert.equal(await tryAcquireJehoSchemaLock(unlockFailure), true);
  await assert.rejects(releaseJehoSchemaLock(unlockFailure), /was not released/);
  assert.equal(discarded.count, 1);
  assert.equal(await tryAcquireJehoSchemaLock(afterUnlockFailure), true);
  await releaseJehoSchemaLock(afterUnlockFailure);

  assert.equal(JEHO_SCHEMA_LOCK_NAMESPACE, 741902611);
  assert.equal(JEHO_SCHEMA_LOCK_ID, 99);
});
