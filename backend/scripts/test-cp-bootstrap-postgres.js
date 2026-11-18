const assert = require('node:assert/strict');
require('ts-node/register');
const { DataSource } = require('typeorm');
const { ensureCpTables } = require('../src/database/cp-bootstrap');

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
    await testFreshInstall(ds);
    await testLegacyUpgrade(ds);
    console.log('PASS: CP bootstrap fresh-install, legacy-upgrade, idempotency, and data-preservation checks.');
  } finally {
    await ds.query('DROP SCHEMA IF EXISTS jeho_own CASCADE');
    await ds.destroy();
  }
}

main().catch((error) => {
  console.error('FAIL: CP PostgreSQL integration test:', error);
  process.exitCode = 1;
});
