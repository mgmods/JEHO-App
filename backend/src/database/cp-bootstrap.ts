import { DataSource } from 'typeorm';

/**
 * Additive CP schema bootstrap for older deployments.
 * Creates missing CP tables/indexes only; never drops or rewrites relationship/request data.
 */
export async function ensureCpTables(dataSource: DataSource): Promise<void> {
  const [usersTable] = await dataSource.query(
    `SELECT 1 FROM information_schema.tables
     WHERE table_schema = 'jeho_own' AND table_name = 'users' LIMIT 1`,
  );
  if (!usersTable) {
    console.warn('[CpBootstrap] Skipped: jeho_own.users does not exist yet.');
    return;
  }

  await dataSource.transaction(async (manager) => {
    await manager.query(`
      CREATE TABLE IF NOT EXISTS "jeho_own"."cp_relationships" (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "userAId" uuid NOT NULL REFERENCES "jeho_own"."users"(id) ON DELETE CASCADE,
        "userBId" uuid NOT NULL REFERENCES "jeho_own"."users"(id) ON DELETE CASCADE,
        type varchar(24) NOT NULL CHECK (type IN ('friendship','brotherhood','love','engagement','marriage')),
        status varchar(16) NOT NULL DEFAULT 'active' CHECK (status IN ('active','ended')),
        points bigint NOT NULL DEFAULT 0 CHECK (points >= 0),
        level integer NOT NULL DEFAULT 1 CHECK (level >= 1),
        "startedAt" timestamptz NOT NULL DEFAULT now(),
        "endedAt" timestamptz NULL,
        "endedByUserId" uuid NULL REFERENCES "jeho_own"."users"(id) ON DELETE SET NULL,
        "createdAt" timestamptz NOT NULL DEFAULT now(),
        "updatedAt" timestamptz NOT NULL DEFAULT now(),
        CHECK ("userAId" <> "userBId")
      )
    `);
    await manager.query(`
      CREATE TABLE IF NOT EXISTS "jeho_own"."cp_requests" (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "fromUserId" uuid NOT NULL,
        "toUserId" uuid NOT NULL,
        type varchar(24) NOT NULL CHECK (type IN ('friendship','brotherhood','love','engagement','marriage')),
        status varchar(16) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','accepted','rejected','expired','cancelled')),
        "roomId" uuid NULL,
        "priceCoins" bigint NULL,
        "giftId" uuid NULL,
        "giftName" varchar(160) NULL,
        "giftIconUrl" varchar(512) NULL,
        "giftAnimationUrl" varchar(512) NULL,
        "idempotencyKey" varchar(128) NOT NULL UNIQUE,
        "expiresAt" timestamptz NOT NULL,
        "respondedAt" timestamptz NULL,
        "relationshipId" uuid NULL REFERENCES "jeho_own"."cp_relationships"(id) ON DELETE SET NULL,
        "refundedAt" timestamptz NULL,
        "createdAt" timestamptz NOT NULL DEFAULT now(),
        "updatedAt" timestamptz NOT NULL DEFAULT now(),
        CHECK ("fromUserId" <> "toUserId")
      )
    `);
    // Bring forward older CP request tables without touching existing rows.
    await manager.query('ALTER TABLE "jeho_own"."cp_requests" ADD COLUMN IF NOT EXISTS "giftId" uuid NULL');
    await manager.query('ALTER TABLE "jeho_own"."cp_requests" ADD COLUMN IF NOT EXISTS "giftName" varchar(160) NULL');
    await manager.query('ALTER TABLE "jeho_own"."cp_requests" ADD COLUMN IF NOT EXISTS "giftIconUrl" varchar(512) NULL');
    await manager.query('ALTER TABLE "jeho_own"."cp_requests" ADD COLUMN IF NOT EXISTS "giftAnimationUrl" varchar(512) NULL');
    await manager.query('ALTER TABLE "jeho_own"."cp_requests" ALTER COLUMN "roomId" DROP NOT NULL');
    await manager.query('ALTER TABLE "jeho_own"."cp_requests" ALTER COLUMN "priceCoins" DROP NOT NULL');

    await manager.query(`
      CREATE TABLE IF NOT EXISTS "jeho_own"."cp_audit_logs" (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "adminUserId" uuid NULL REFERENCES "jeho_own"."users"(id) ON DELETE SET NULL,
        action varchar(64) NOT NULL,
        "entityType" varchar(64) NOT NULL,
        "entityId" varchar(128) NULL,
        details jsonb NOT NULL DEFAULT '{}'::jsonb,
        "createdAt" timestamptz NOT NULL DEFAULT now()
      )
    `);
    await manager.query(`
      CREATE TABLE IF NOT EXISTS "jeho_own"."cp_point_events" (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "relationshipId" uuid NOT NULL REFERENCES "jeho_own"."cp_relationships"(id) ON DELETE CASCADE,
        "eventType" varchar(64) NOT NULL,
        "referenceType" varchar(128) NOT NULL,
        "referenceId" varchar(255) NOT NULL,
        points bigint NOT NULL CHECK (points >= 0),
        metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
        "createdAt" timestamptz NOT NULL DEFAULT now(),
        CONSTRAINT uq_cp_point_event_reference UNIQUE ("referenceType","referenceId")
      )
    `);

    await manager.query('CREATE INDEX IF NOT EXISTS idx_cp_relationship_type_status_points ON "jeho_own"."cp_relationships"(type,status,points DESC)');
    await manager.query('CREATE INDEX IF NOT EXISTS idx_cp_relationship_user_a_status ON "jeho_own"."cp_relationships"("userAId",status)');
    await manager.query('CREATE INDEX IF NOT EXISTS idx_cp_relationship_user_b_status ON "jeho_own"."cp_relationships"("userBId",status)');
    await manager.query('CREATE INDEX IF NOT EXISTS idx_cp_request_recipient_status_expiry ON "jeho_own"."cp_requests"("toUserId",status,"expiresAt")');
    await manager.query('CREATE INDEX IF NOT EXISTS idx_cp_request_sender_status ON "jeho_own"."cp_requests"("fromUserId",status)');
    await manager.query('CREATE INDEX IF NOT EXISTS idx_cp_audit_created_at ON "jeho_own"."cp_audit_logs"("createdAt" DESC)');
    await manager.query('CREATE INDEX IF NOT EXISTS idx_cp_point_events_relationship_created ON "jeho_own"."cp_point_events"("relationshipId","createdAt" DESC)');
  });

  console.log('[CpBootstrap] CP tables/indexes verified (additive only).');
}
