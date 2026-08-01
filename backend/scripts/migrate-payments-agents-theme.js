require('dotenv').config();
const { Client } = require('pg');

const client = new Client({
  host: process.env.DB_HOST || 'localhost',
  port: Number(process.env.DB_PORT || 5432),
  user: process.env.DB_USER || process.env.DB_USERNAME || 'postgres',
  password: process.env.DB_PASSWORD || 'postgres',
  database: process.env.DB_NAME || process.env.DB_DATABASE || 'auralive',
  ssl: process.env.DB_SSL === 'true' ? { rejectUnauthorized: false } : undefined,
});

async function addEnumValue(enumName, value) {
  if (!/^[a-zA-Z0-9_]+$/.test(enumName) || !/^[a-zA-Z0-9_]+$/.test(value)) {
    throw new Error(`Invalid enum identifiers: ${enumName}/${value}`);
  }
  await client.query(`
    DO $$ BEGIN
      IF EXISTS (SELECT 1 FROM pg_type WHERE typname = '${enumName}') THEN
        IF NOT EXISTS (
          SELECT 1
          FROM pg_enum e
          JOIN pg_type t ON t.oid = e.enumtypid
          WHERE t.typname = '${enumName}' AND e.enumlabel = '${value}'
        ) THEN
          EXECUTE format('ALTER TYPE %I ADD VALUE %L', '${enumName}', '${value}');
        END IF;
      END IF;
    END $$;
  `);
}

async function ensureEnumValues() {
  // TypeORM typically names enums like recharge_orders_provider_enum
  const providerEnums = [
    'recharge_orders_provider_enum',
    'recharge_order_provider_enum',
    'paymentprovider',
  ];
  for (const name of providerEnums) {
    await addEnumValue(name, 'binance_pay');
    await addEnumValue(name, 'binance_wallet');
    await addEnumValue(name, 'recharge_agent');
  }
}

const sql = `
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- recharge_orders: package pricing + lifecycle columns
ALTER TABLE recharge_orders ADD COLUMN IF NOT EXISTS sku varchar(64);
ALTER TABLE recharge_orders ADD COLUMN IF NOT EXISTS "bonusCoins" integer NOT NULL DEFAULT 0;
ALTER TABLE recharge_orders ADD COLUMN IF NOT EXISTS "expiresAt" timestamptz;
ALTER TABLE recharge_orders ADD COLUMN IF NOT EXISTS "completedAt" timestamptz;

DO $$ BEGIN
  -- widen amountFiat if still narrow decimal
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'recharge_orders' AND column_name = 'amountFiat'
  ) THEN
    ALTER TABLE recharge_orders
      ALTER COLUMN "amountFiat" TYPE decimal(30,8)
      USING "amountFiat"::decimal(30,8);
  END IF;
EXCEPTION WHEN others THEN
  RAISE NOTICE 'amountFiat alter skipped: %', SQLERRM;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS "UQ_recharge_provider_payment"
  ON recharge_orders (provider, "providerPaymentId")
  WHERE "providerPaymentId" IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS "UQ_recharge_provider_order"
  ON recharge_orders (provider, "providerOrderId")
  WHERE "providerOrderId" IS NOT NULL;

-- payment webhook idempotency
CREATE TABLE IF NOT EXISTS payment_webhook_events (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  provider varchar(32) NOT NULL,
  "eventId" varchar(191) NOT NULL,
  "eventType" varchar(64),
  "orderId" uuid,
  payload text,
  processed boolean NOT NULL DEFAULT false,
  "createdAt" timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS "UQ_payment_webhook_event"
  ON payment_webhook_events (provider, "eventId");
CREATE INDEX IF NOT EXISTS "IDX_payment_webhook_events_provider"
  ON payment_webhook_events (provider);

-- recharge agent tables
DO $$ BEGIN
  CREATE TYPE recharge_agent_status_enum AS ENUM ('pending', 'active', 'suspended', 'rejected');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
  CREATE TYPE recharge_agent_source_enum AS ENUM ('admin', 'application');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
  CREATE TYPE agent_ledger_type_enum AS ENUM (
    'float_credit', 'float_debit', 'sale', 'commission', 'adjustment'
  );
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

CREATE TABLE IF NOT EXISTS recharge_agents (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "userId" uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  status recharge_agent_status_enum NOT NULL DEFAULT 'pending',
  source recharge_agent_source_enum NOT NULL DEFAULT 'admin',
  "floatCoins" bigint NOT NULL DEFAULT 0,
  "commissionBps" integer NOT NULL DEFAULT 0,
  "dailyLimitCoins" integer NOT NULL DEFAULT 500000,
  "dailySoldCoins" integer NOT NULL DEFAULT 0,
  "dailySoldKey" varchar(16),
  notes text,
  "reviewedBy" uuid,
  "reviewedAt" timestamptz,
  "createdAt" timestamptz NOT NULL DEFAULT now(),
  "updatedAt" timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS "UQ_recharge_agent_user" ON recharge_agents ("userId");
CREATE INDEX IF NOT EXISTS "IDX_recharge_agents_userId" ON recharge_agents ("userId");

CREATE TABLE IF NOT EXISTS recharge_agent_applications (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "userId" uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  contact varchar(128),
  region varchar(64),
  reason text,
  status recharge_agent_status_enum NOT NULL DEFAULT 'pending',
  "reviewNote" text,
  "reviewedBy" uuid,
  "reviewedAt" timestamptz,
  "createdAt" timestamptz NOT NULL DEFAULT now(),
  "updatedAt" timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS "IDX_recharge_agent_applications_userId"
  ON recharge_agent_applications ("userId");

CREATE TABLE IF NOT EXISTS recharge_agent_ledger (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "agentId" uuid NOT NULL REFERENCES recharge_agents(id) ON DELETE CASCADE,
  type agent_ledger_type_enum NOT NULL,
  amount bigint NOT NULL,
  "balanceAfter" bigint NOT NULL,
  "referenceId" uuid,
  "referenceType" varchar(64),
  description text,
  "actorUserId" uuid,
  "createdAt" timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS "IDX_recharge_agent_ledger_agentId"
  ON recharge_agent_ledger ("agentId");

CREATE TABLE IF NOT EXISTS agent_recharges (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "agentId" uuid NOT NULL REFERENCES recharge_agents(id) ON DELETE CASCADE,
  "recipientUserId" uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  sku varchar(64),
  coins integer NOT NULL,
  "chargedFloat" integer NOT NULL,
  "commissionCoins" integer NOT NULL DEFAULT 0,
  status varchar(16) NOT NULL DEFAULT 'completed',
  "idempotencyKey" varchar(96) NOT NULL,
  "walletTxId" uuid,
  "createdAt" timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS "UQ_agent_recharge_idempotency"
  ON agent_recharges ("idempotencyKey");
CREATE INDEX IF NOT EXISTS "IDX_agent_recharges_agentId" ON agent_recharges ("agentId");
CREATE INDEX IF NOT EXISTS "IDX_agent_recharges_recipientUserId"
  ON agent_recharges ("recipientUserId");

-- theme / join toast columns
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS "backgroundUrl" varchar(512);
`;

async function main() {
  await client.connect();
  try {
    // Enum ADD VALUE cannot run inside a transaction block on older PG versions
    await ensureEnumValues();

    await client.query('BEGIN');
    await client.query(sql);
    await client.query('COMMIT');
    console.log('Payments / agents / theme migration applied.');
  } catch (error) {
    try {
      await client.query('ROLLBACK');
    } catch {
      /* ignore */
    }
    throw error;
  } finally {
    await client.end();
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
