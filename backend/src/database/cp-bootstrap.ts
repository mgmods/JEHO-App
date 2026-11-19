import { DataSource } from 'typeorm';
import { JEHO_SCHEMA_LOCK_ID, JEHO_SCHEMA_LOCK_NAMESPACE } from './schema-migration-lock';

/**
 * Additive CP schema bootstrap for older deployments.
 * Creates missing CP tables/indexes only; never drops or rewrites relationship/request data.
 */
export async function ensureCpTables(dataSource: DataSource): Promise<void> {
  // CP startup and the following CP service migration require these core tables.
  // Never skip this bootstrap and then let the module fail later with an opaque SQL error.
  const requiredTables = ['users', 'user_profiles', 'gifts', 'app_settings', 'cosmetics'];
  const existingRows = await dataSource.query(
    `SELECT table_name
     FROM information_schema.tables
     WHERE table_schema = 'jeho_own' AND table_name = ANY($1::text[])`,
    [requiredTables],
  );
  const existingTables = new Set(existingRows.map((row: { table_name: string }) => String(row.table_name)));
  const missingTables = requiredTables.filter((tableName) => !existingTables.has(tableName));
  if (missingTables.length) {
    throw new Error(
      `[CpBootstrap] Required core tables are missing in schema jeho_own: ${missingTables.join(', ')}. CP bootstrap was not skipped.`,
    );
  }

  await dataSource.transaction(async (manager) => {
    // Serialize concurrent app instances. Give an in-flight deployment a bounded
    // window to finish, then restore the shorter DDL lock timeout.
    await manager.query("SET LOCAL lock_timeout = '15s'");
    await manager.query("SET LOCAL statement_timeout = '30s'");
    await manager.query('SELECT pg_advisory_xact_lock($1, $2)', [JEHO_SCHEMA_LOCK_NAMESPACE, JEHO_SCHEMA_LOCK_ID]);
    await manager.query("SET LOCAL lock_timeout = '3s'");

    await manager.query(`
      CREATE TABLE IF NOT EXISTS "jeho_own"."cp_relationships" (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "userAId" uuid NOT NULL REFERENCES "jeho_own"."users"(id) ON DELETE CASCADE,
        "userBId" uuid NOT NULL REFERENCES "jeho_own"."users"(id) ON DELETE CASCADE,
        type varchar(24) NOT NULL CHECK (type IN ('relationship','friendship','brotherhood','love','engagement','marriage')),
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

    await manager.query('ALTER TABLE "jeho_own"."cp_requests" ADD COLUMN IF NOT EXISTS "cpPoints" bigint NULL');
    await manager.query('ALTER TABLE "jeho_own"."user_profiles" ADD COLUMN IF NOT EXISTS "cpBackgroundUrl" varchar(512)');
    await manager.query('ALTER TABLE "jeho_own"."user_profiles" ADD COLUMN IF NOT EXISTS "cpLogoUrl" varchar(512)');

    // CP-only cosmetic enum values are additive and safe to re-apply.
    const cosmeticEnumRows = await manager.query(
      `SELECT n.nspname, t.typname
       FROM pg_type t
       JOIN pg_attribute a ON a.atttypid=t.oid
       JOIN pg_class c ON c.oid=a.attrelid
       JOIN pg_namespace n ON n.oid=t.typnamespace
       WHERE n.nspname='jeho_own' AND c.relname='cosmetics'
         AND a.attname='type' AND t.typtype='e'
       LIMIT 1`,
    );
    const enumSchema = String(cosmeticEnumRows?.[0]?.nspname || '').replace(/"/g, '""');
    const enumName = String(cosmeticEnumRows?.[0]?.typname || '').replace(/"/g, '""');
    if (enumSchema && enumName) {
      await manager.query(`ALTER TYPE "${enumSchema}"."${enumName}" ADD VALUE IF NOT EXISTS 'cp_background'`);
      await manager.query(`ALTER TYPE "${enumSchema}"."${enumName}" ADD VALUE IF NOT EXISTS 'cp_logo'`);
    }

    await manager.query(`
      CREATE TABLE IF NOT EXISTS "jeho_own"."cp_memories" (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "relationshipId" uuid NOT NULL REFERENCES "jeho_own"."cp_relationships"(id) ON DELETE CASCADE,
        "userId" uuid NOT NULL REFERENCES "jeho_own"."users"(id) ON DELETE CASCADE,
        text varchar(500) NOT NULL DEFAULT '',
        "imageUrl" varchar(2048),
        "createdAt" timestamptz NOT NULL DEFAULT now()
      )
    `);
    await manager.query(`
      CREATE TABLE IF NOT EXISTS "jeho_own"."cp_level_reward_claims" (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "relationshipId" uuid NOT NULL REFERENCES "jeho_own"."cp_relationships"(id) ON DELETE CASCADE,
        "userId" uuid NOT NULL REFERENCES "jeho_own"."users"(id) ON DELETE CASCADE,
        level integer NOT NULL,
        "cosmeticCode" varchar(64) NOT NULL,
        status varchar(16) NOT NULL DEFAULT 'pending',
        "createdAt" timestamptz NOT NULL DEFAULT now(),
        "grantedAt" timestamptz,
        UNIQUE ("relationshipId", level, "userId", "cosmeticCode")
      )
    `);
    await manager.query(`
      CREATE TABLE IF NOT EXISTS "jeho_own"."cp_level_gift_reward_claims" (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "relationshipId" uuid NOT NULL REFERENCES "jeho_own"."cp_relationships"(id) ON DELETE CASCADE,
        "userId" uuid NOT NULL REFERENCES "jeho_own"."users"(id) ON DELETE CASCADE,
        level integer NOT NULL,
        "giftId" uuid NOT NULL REFERENCES "jeho_own"."gifts"(id) ON DELETE CASCADE,
        status varchar(16) NOT NULL DEFAULT 'pending',
        "createdAt" timestamptz NOT NULL DEFAULT now(),
        "grantedAt" timestamptz,
        UNIQUE ("relationshipId", level, "userId", "giftId")
      )
    `);
    await manager.query('CREATE INDEX IF NOT EXISTS idx_cp_memories_relationship_created ON "jeho_own"."cp_memories"("relationshipId", "createdAt" DESC)');

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

    // Normalize only a single-column CHECK on the type column. Never drop
    // compound or unrelated checks. Skip tables already accepting relationship.
    await manager.query(`DO $$
    DECLARE
      target record;
      item record;
      type_attnum smallint;
    BEGIN
      FOR target IN
        SELECT * FROM (VALUES
          ('cp_relationships', 'chk_cp_relationship_type_unified'),
          ('cp_requests', 'chk_cp_request_type_unified')
        ) AS targets(table_name, constraint_name)
      LOOP
        SELECT a.attnum INTO type_attnum
        FROM pg_attribute a
        JOIN pg_class t ON t.oid = a.attrelid
        JOIN pg_namespace n ON n.oid = t.relnamespace
        WHERE n.nspname = 'jeho_own'
          AND t.relname = target.table_name
          AND a.attname = 'type'
          AND NOT a.attisdropped;

        IF type_attnum IS NULL THEN
          RAISE EXCEPTION '[CpBootstrap] Missing type column on %', target.table_name;
        END IF;

        IF NOT EXISTS (
          SELECT 1
          FROM pg_constraint c
          JOIN pg_class t ON t.oid = c.conrelid
          JOIN pg_namespace n ON n.oid = t.relnamespace
          WHERE n.nspname = 'jeho_own'
            AND t.relname = target.table_name
            AND c.contype = 'c'
            AND c.conkey = ARRAY[type_attnum]::smallint[]
            AND pg_get_constraintdef(c.oid) ILIKE '%relationship%'
        ) THEN
          FOR item IN
            SELECT c.conname
            FROM pg_constraint c
            JOIN pg_class t ON t.oid = c.conrelid
            JOIN pg_namespace n ON n.oid = t.relnamespace
            WHERE n.nspname = 'jeho_own'
              AND t.relname = target.table_name
              AND c.contype = 'c'
              AND c.conkey = ARRAY[type_attnum]::smallint[]
              AND (
                pg_get_constraintdef(c.oid) ILIKE '%friendship%'
                OR pg_get_constraintdef(c.oid) ILIKE '%brotherhood%'
                OR pg_get_constraintdef(c.oid) ILIKE '%engagement%'
                OR pg_get_constraintdef(c.oid) ILIKE '%marriage%'
                OR pg_get_constraintdef(c.oid) ILIKE '%love%'
              )
          LOOP
            EXECUTE format('ALTER TABLE %I.%I DROP CONSTRAINT %I',
              'jeho_own', target.table_name, item.conname);
          END LOOP;

          EXECUTE format(
            'ALTER TABLE %I.%I ADD CONSTRAINT %I CHECK (type IN (''relationship'',''friendship'',''brotherhood'',''love'',''engagement'',''marriage''))',
            'jeho_own', target.table_name, target.constraint_name
          );
        END IF;
      END LOOP;
    END;
    $$`);

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
