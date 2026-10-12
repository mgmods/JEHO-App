const { Client } = require('pg');
require('dotenv').config();

async function main() {
  const connectionString = process.env.DATABASE_URL;
  if (!connectionString) throw new Error('DATABASE_URL is required; no database changes were made.');
  const client = new Client({ connectionString, ssl: process.env.DATABASE_SSL === 'true' ? { rejectUnauthorized: false } : undefined });
  await client.connect();
  try {
    await client.query('BEGIN');
    await client.query('CREATE SCHEMA IF NOT EXISTS jeho_own');
    await client.query('SET LOCAL search_path TO jeho_own');
    await client.query(`CREATE TABLE IF NOT EXISTS cp_relationships (
      id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
      "userAId" uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      "userBId" uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      type varchar(24) NOT NULL CHECK (type IN ('friendship','brotherhood','love','engagement','marriage')),
      status varchar(16) NOT NULL DEFAULT 'active' CHECK (status IN ('active','ended')),
      points bigint NOT NULL DEFAULT 0 CHECK (points >= 0),
      level integer NOT NULL DEFAULT 1 CHECK (level >= 1),
      "startedAt" timestamptz NOT NULL DEFAULT now(),
      "endedAt" timestamptz NULL,
      "endedByUserId" uuid NULL REFERENCES users(id) ON DELETE SET NULL,
      "createdAt" timestamptz NOT NULL DEFAULT now(),
      "updatedAt" timestamptz NOT NULL DEFAULT now(),
      CHECK ("userAId" <> "userBId")
    )`);
    await client.query(`CREATE INDEX IF NOT EXISTS idx_cp_relationship_type_status_points ON cp_relationships(type,status,points DESC)`);
    await client.query(`CREATE INDEX IF NOT EXISTS idx_cp_relationship_user_a_status ON cp_relationships("userAId",status)`);
    await client.query(`CREATE INDEX IF NOT EXISTS idx_cp_relationship_user_b_status ON cp_relationships("userBId",status)`);
    await client.query(`CREATE TABLE IF NOT EXISTS cp_requests (
      id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
      "fromUserId" uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      "toUserId" uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      type varchar(24) NOT NULL CHECK (type IN ('friendship','brotherhood','love','engagement','marriage')),
      status varchar(16) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','accepted','rejected','expired','cancelled')),
      "roomId" uuid NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
      "priceCoins" bigint NOT NULL CHECK ("priceCoins" > 0),
      "idempotencyKey" varchar(128) NOT NULL UNIQUE,
      "expiresAt" timestamptz NOT NULL,
      "respondedAt" timestamptz NULL,
      "relationshipId" uuid NULL REFERENCES cp_relationships(id) ON DELETE SET NULL,
      "refundedAt" timestamptz NULL,
      "createdAt" timestamptz NOT NULL DEFAULT now(),
      "updatedAt" timestamptz NOT NULL DEFAULT now(),
      CHECK ("fromUserId" <> "toUserId")
    )`);
    await client.query(`CREATE INDEX IF NOT EXISTS idx_cp_request_recipient_status_expiry ON cp_requests("toUserId",status,"expiresAt")`);
    await client.query(`CREATE INDEX IF NOT EXISTS idx_cp_request_sender_status ON cp_requests("fromUserId",status)`);
    await client.query('COMMIT');
    console.log('CP schema created/verified. No existing rows were deleted or reset.');
  } catch (error) {
    await client.query('ROLLBACK');
    throw error;
  } finally {
    await client.end();
  }
}
main().catch(error => { console.error(error.message); process.exit(1); });
