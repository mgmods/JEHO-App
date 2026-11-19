const assert = require('node:assert/strict');
require('ts-node/register');
const { DataSource } = require('typeorm');
const { ensureCpTables } = require('../src/database/cp-bootstrap');
const { tryAcquireJehoSchemaLock, releaseJehoSchemaLock, tryAcquireJehoSessionAdvisoryLock } = require('../src/database/schema-migration-lock');
const { inspectJehoSchemaForProvisioning } = require('./jeho-empty-schema-guard');

const databaseUrl = process.env.CP_TEST_DATABASE_URL;
if (!databaseUrl) throw new Error('CP_TEST_DATABASE_URL is required. Use only an isolated disposable PostgreSQL database.');

async function resetSchema(ds) {
  await ds.query('DROP SCHEMA IF EXISTS jeho_own CASCADE');
  await ds.query('CREATE SCHEMA jeho_own');
  await ds.query('CREATE EXTENSION IF NOT EXISTS pgcrypto');
}

async function createCoreTables(ds) {
  await ds.query('CREATE TABLE jeho_own.users (id uuid PRIMARY KEY DEFAULT gen_random_uuid())');
  await ds.query('CREATE TABLE jeho_own.user_profiles (id uuid PRIMARY KEY DEFAULT gen_random_uuid())');
  await ds.query('CREATE TABLE jeho_own.gifts (id uuid PRIMARY KEY DEFAULT gen_random_uuid())');
  await ds.query('CREATE TABLE jeho_own.app_settings (id uuid PRIMARY KEY DEFAULT gen_random_uuid())');
  await ds.query('CREATE TABLE jeho_own.cosmetics (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), type text)');
}

async function testFreshInstall(ds) {
  await resetSchema(ds);
  await createCoreTables(ds);
  await ensureCpTables(ds);
  await ensureCpTables(ds);
  const tables = await ds.query(
    `SELECT table_name FROM information_schema.tables
     WHERE table_schema='jeho_own' AND table_name = ANY($1::text[])`,
    [['cp_relationships','cp_requests','cp_memories','cp_level_reward_claims','cp_level_gift_reward_claims','cp_audit_logs','cp_point_events']],
  );
  assert.equal(tables.length, 7, 'fresh install must create all seven CP tables');
  const indexes = await ds.query(
    `SELECT indexname FROM pg_indexes WHERE schemaname='jeho_own'
     AND indexname = ANY($1::text[])`,
    [['idx_cp_relationship_type_status_points','idx_cp_relationship_user_a_status','idx_cp_relationship_user_b_status','idx_cp_request_recipient_status_expiry','idx_cp_request_sender_status','idx_cp_audit_created_at','idx_cp_point_events_relationship_created']],
  );
  assert.equal(indexes.length, 7, 'fresh install must create the expected CP indexes');
}

async function testLegacyUpgrade(ds) {
  await resetSchema(ds);
  await createCoreTables(ds);
  const a = '10000000-0000-4000-8000-000000000001';
  const b = '10000000-0000-4000-8000-000000000002';
  await ds.query('INSERT INTO jeho_own.users(id) VALUES ($1),($2)', [a,b]);
  await ds.query(`CREATE TABLE jeho_own.cp_relationships (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    "userAId" uuid NOT NULL REFERENCES jeho_own.users(id),
    "userBId" uuid NOT NULL REFERENCES jeho_own.users(id),
    type varchar(24) NOT NULL CONSTRAINT old_cp_type_check CHECK (type IN ('love','friendship')),
    status varchar(16) NOT NULL DEFAULT 'active' CHECK (status IN ('active','ended')),
    points bigint NOT NULL DEFAULT 0 CHECK (points >= 0),
    level integer NOT NULL DEFAULT 1 CHECK (level >= 1),
    "startedAt" timestamptz NOT NULL DEFAULT now(),
    "endedAt" timestamptz NULL,
    "endedByUserId" uuid NULL REFERENCES jeho_own.users(id) ON DELETE SET NULL,
    "createdAt" timestamptz NOT NULL DEFAULT now(),
    "updatedAt" timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT preserve_pair_check CHECK ("userAId" <> "userBId")
  )`);
  await ds.query(`CREATE TABLE jeho_own.cp_requests (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    "fromUserId" uuid NOT NULL, "toUserId" uuid NOT NULL,
    type varchar(24) NOT NULL CONSTRAINT old_cp_request_type_check CHECK (type IN ('love','friendship')),
    status varchar(16) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','accepted','rejected','expired','cancelled')),
    "roomId" uuid NOT NULL, "priceCoins" bigint NOT NULL,
    "idempotencyKey" varchar(128) NOT NULL UNIQUE,
    "expiresAt" timestamptz NOT NULL, "respondedAt" timestamptz NULL,
    "relationshipId" uuid NULL REFERENCES jeho_own.cp_relationships(id) ON DELETE SET NULL,
    "refundedAt" timestamptz NULL,
    "createdAt" timestamptz NOT NULL DEFAULT now(),
    "updatedAt" timestamptz NOT NULL DEFAULT now(),
    CHECK ("fromUserId" <> "toUserId")
  )`);
  await ds.query(
    `INSERT INTO jeho_own.cp_relationships("userAId","userBId",type,points) VALUES ($1,$2,'love',125)`,
    [a,b],
  );
  await ds.query(
    `INSERT INTO jeho_own.cp_requests("fromUserId","toUserId",type,"roomId","priceCoins","idempotencyKey","expiresAt")
     VALUES ($1,$2,'love','20000000-0000-4000-8000-000000000001',50,'legacy-cp-request',now()+interval '1 day')`,
    [a,b],
  );
  await ensureCpTables(ds);
  await ensureCpTables(ds);
  const relationship = await ds.query('SELECT type, points FROM jeho_own.cp_relationships');
  assert.equal(relationship.length, 1, 'legacy relationship row must be preserved');
  assert.equal(relationship[0].type, 'love');
  assert.equal(Number(relationship[0].points), 125);
  const request = await ds.query('SELECT type, "priceCoins", "roomId" FROM jeho_own.cp_requests');
  assert.equal(request.length, 1, 'legacy request row must be preserved');
  assert.equal(request[0].type, 'love');
  assert.equal(Number(request[0].priceCoins), 50);
  const nullable = await ds.query(
    `SELECT column_name, is_nullable FROM information_schema.columns
     WHERE table_schema='jeho_own' AND table_name='cp_requests'
       AND column_name IN ('roomId','priceCoins')`,
  );
  assert.equal(nullable.length, 2);
  assert.ok(nullable.every((column) => column.is_nullable === 'YES'), 'legacy request columns must become nullable');
  await ds.query(
    `INSERT INTO jeho_own.cp_relationships("userAId","userBId",type) VALUES ($1,$2,'relationship')`,
    [b,a],
  );
  const preservedConstraint = await ds.query(
    `SELECT 1 FROM pg_constraint
     WHERE conrelid='jeho_own.cp_relationships'::regclass AND conname='preserve_pair_check'`,
  );
  assert.equal(preservedConstraint.length, 1, 'compound/unrelated CHECK constraint must remain intact');
  const validatedTypeConstraints = await ds.query(
    `SELECT conname, convalidated FROM pg_constraint
     WHERE conrelid IN ('jeho_own.cp_relationships'::regclass, 'jeho_own.cp_requests'::regclass)
       AND conname IN ('chk_cp_relationship_type_unified','chk_cp_request_type_unified')`,
  );
  assert.equal(validatedTypeConstraints.length, 2, 'both unified CP type constraints must exist');
  assert.ok(validatedTypeConstraints.every((constraint) => constraint.convalidated === true),
    'CP type constraints must be validated after their low-lock validation pass');
}

async function testCpDdlLockTimeoutAndResume(ds) {
  await resetSchema(ds);
  await createCoreTables(ds);
  await ds.query(`CREATE TABLE jeho_own.cp_requests (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    "fromUserId" uuid NOT NULL,
    "toUserId" uuid NOT NULL,
    type varchar(24) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'pending',
    "roomId" uuid NOT NULL,
    "priceCoins" bigint NOT NULL,
    "idempotencyKey" varchar(128) NOT NULL UNIQUE,
    "expiresAt" timestamptz NOT NULL,
    "respondedAt" timestamptz NULL,
    "relationshipId" uuid NULL,
    "refundedAt" timestamptz NULL,
    "createdAt" timestamptz NOT NULL DEFAULT now(),
    "updatedAt" timestamptz NOT NULL DEFAULT now()
  )`);

  const blocker = ds.createQueryRunner();
  await blocker.connect();
  await blocker.startTransaction();
  try {
    // AccessShare is compatible with normal reads but conflicts with ALTER TABLE's
    // AccessExclusive lock. The CP bootstrap must fail fast instead of queuing reads.
    await blocker.query('SELECT * FROM jeho_own.cp_requests');
    const startedAt = Date.now();
    await assert.rejects(() => ensureCpTables(ds), /lock timeout/i);
    const elapsedMs = Date.now() - startedAt;
    assert.ok(elapsedMs < 2_000, `blocked CP DDL must fail fast (elapsed ${elapsedMs}ms)`);
  } finally {
    if (blocker.isTransactionActive) await blocker.rollbackTransaction().catch(() => undefined);
    await blocker.release();
  }

  // The failed pass may have committed earlier additive steps; a retry must resume
  // safely and finish without dropping or rewriting the existing request row.
  await ensureCpTables(ds);
  const columns = await ds.query(
    `SELECT column_name, is_nullable FROM information_schema.columns
     WHERE table_schema='jeho_own' AND table_name='cp_requests'
       AND column_name IN ('giftId','roomId','priceCoins')`,
  );
  assert.equal(columns.length, 3, 'retry must complete the interrupted additive CP DDL');
  assert.ok(columns.find((column) => column.column_name === 'roomId').is_nullable === 'YES');
  assert.ok(columns.find((column) => column.column_name === 'priceCoins').is_nullable === 'YES');
}

async function testFirstInstallSchemaGuard(ds) {
  await resetSchema(ds);
  let state = await inspectJehoSchemaForProvisioning(ds);
  assert.equal(state.markerExists, false);
  assert.equal(state.isEmpty, true, 'a truly empty app schema must be eligible for first-install provisioning');

  await ds.query('CREATE TABLE jeho_own.partial_install (id integer PRIMARY KEY)');
  state = await inspectJehoSchemaForProvisioning(ds);
  assert.equal(state.isEmpty, false, 'a partially initialized schema without a JEHO marker must be refused');
  assert.equal(state.relations, 1);

  await resetSchema(ds);
  await ds.query('CREATE FUNCTION jeho_own.partial_routine() RETURNS integer LANGUAGE sql AS $$ SELECT 1 $$');
  state = await inspectJehoSchemaForProvisioning(ds);
  assert.equal(state.isEmpty, false, 'a schema containing an application routine must not be treated as empty');

  await resetSchema(ds);
  await ds.query(`
    CREATE TABLE jeho_own.__jeho_provision_state (
      id integer PRIMARY KEY CHECK (id = 1),
      status varchar(16) NOT NULL CHECK (status IN ('running','complete'))
    )
  `);
  await ds.query("INSERT INTO jeho_own.__jeho_provision_state(id,status) VALUES (1,'running')");
  state = await inspectJehoSchemaForProvisioning(ds);
  assert.equal(state.markerExists, true);
  assert.equal(state.status, 'running', 'interrupted first install must be recognized as resumable, not as a clean schema');
}

async function testSchemaLockReleaseFailureDiscardsConnection() {
  let acquireDestroyed = false;
  const uncertainAcquireRunner = {
    async query() {
      throw new Error('simulated acquire response loss');
    },
    async releasePostgresConnection() {
      acquireDestroyed = true;
    },
  };
  await assert.rejects(
    () => tryAcquireJehoSessionAdvisoryLock(uncertainAcquireRunner, 2001),
    /simulated acquire response loss/,
  );
  assert.equal(acquireDestroyed, true, 'a connection with an uncertain lock-acquire result must be discarded');

  let destroyed = false;
  const brokenRunner = {
    async query(sql) {
      if (sql.includes('pg_try_advisory_lock')) return [{ locked: true }];
      throw new Error('simulated unlock failure');
    },
    async releasePostgresConnection() {
      destroyed = true;
    },
  };
  assert.equal(await tryAcquireJehoSchemaLock(brokenRunner), true);
  await assert.rejects(() => releaseJehoSchemaLock(brokenRunner), /simulated unlock failure/);
  assert.equal(destroyed, true, 'a session whose advisory unlock fails must be discarded, not pooled');

  const nextRunner = {
    async query(sql) {
      if (sql.includes('pg_try_advisory_lock')) return [{ locked: true }];
      return [{ unlocked: true }];
    },
  };
  assert.equal(await tryAcquireJehoSchemaLock(nextRunner), true, 'local schema lock gate must recover after a failed unlock');
  await releaseJehoSchemaLock(nextRunner);
}

async function testSharedSchemaMigrationLock(ds) {
  const owner = ds.createQueryRunner();
  const contender = ds.createQueryRunner();
  await owner.connect();
  await contender.connect();
  try {
    const ownerLock = await owner.query(
      'SELECT pg_try_advisory_lock(741902611, 99) AS locked',
    );
    assert.equal(ownerLock[0]?.locked, true, 'first schema migrator must acquire the global lock');

    await contender.startTransaction();
    const blocked = await contender.query(
      'SELECT pg_try_advisory_xact_lock(741902611, 99) AS locked',
    );
    assert.equal(blocked[0]?.locked, false, 'transaction-scoped CP migrations must conflict with session-scoped schema migrations');

    await owner.query('SELECT pg_advisory_unlock(741902611, 99)');
    const acquired = await contender.query(
      'SELECT pg_try_advisory_xact_lock(741902611, 99) AS locked',
    );
    assert.equal(acquired[0]?.locked, true, 'a waiting migrator must acquire the same global lock after release');
    await contender.commitTransaction();
  } finally {
    if (contender.isTransactionActive) await contender.rollbackTransaction().catch(() => undefined);
    await owner.query('SELECT pg_advisory_unlock(741902611, 99)').catch(() => undefined);
    await contender.release();
    await owner.release();
  }
}

async function main() {
  const ds = new DataSource({
    type: 'postgres',
    url: databaseUrl,
    ssl: process.env.CP_TEST_DATABASE_SSL === 'true' ? { rejectUnauthorized: false } : false,
    extra: { max: 4 },
  });
  await ds.initialize();
  try {
    await testSchemaLockReleaseFailureDiscardsConnection();
    await testSharedSchemaMigrationLock(ds);
    await testCpDdlLockTimeoutAndResume(ds);
    await testFirstInstallSchemaGuard(ds);
    await testFreshInstall(ds);
    await testLegacyUpgrade(ds);
    console.log('PASS: schema-lock session disposal after failed unlock, shared migration-lock contention, fail-fast CP DDL lock timeout and resume, empty/partial-schema provisioning guard, CP fresh-install, legacy-upgrade, idempotency, and data-preservation checks.');
  } finally {
    await ds.query('DROP SCHEMA IF EXISTS jeho_own CASCADE');
    await ds.destroy();
  }
}

main().catch((error) => {
  console.error('FAIL: CP PostgreSQL integration test:', error);
  process.exitCode = 1;
});
