import { DataSource } from 'typeorm';
import { tryAcquireJehoSchemaLock, releaseJehoSchemaLock } from './schema-migration-lock';

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

  // Run additive DDL as separate autocommit statements. A single large transaction
  // retains AccessExclusive table locks until every CP table/index step completes,
  // which can queue ordinary API SELECTs behind a waiting ALTER TABLE.
  const runner = dataSource.createQueryRunner();
  let lockAcquired = false;
  try {
    await runner.connect();
    await runner.query("SET lock_timeout = '250ms'");
    await runner.query("SET statement_timeout = '30s'");
    lockAcquired = await tryAcquireJehoSchemaLock(runner);
    if (!lockAcquired) {
      throw new Error('[CpBootstrap] JEHO schema migration lock is busy; retrying without waiting on PostgreSQL.');
    }

    await runner.query(`
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
    await runner.query(`
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
    await runner.query('ALTER TABLE "jeho_own"."cp_requests" ADD COLUMN IF NOT EXISTS "giftId" uuid NULL');
    await runner.query('ALTER TABLE "jeho_own"."cp_requests" ADD COLUMN IF NOT EXISTS "giftName" varchar(160) NULL');
    await runner.query('ALTER TABLE "jeho_own"."cp_requests" ADD COLUMN IF NOT EXISTS "giftIconUrl" varchar(512) NULL');
    await runner.query('ALTER TABLE "jeho_own"."cp_requests" ADD COLUMN IF NOT EXISTS "giftAnimationUrl" varchar(512) NULL');
    await runner.query('ALTER TABLE "jeho_own"."cp_requests" ALTER COLUMN "roomId" DROP NOT NULL');
    await runner.query('ALTER TABLE "jeho_own"."cp_requests" ALTER COLUMN "priceCoins" DROP NOT NULL');

    await runner.query('ALTER TABLE "jeho_own"."cp_requests" ADD COLUMN IF NOT EXISTS "cpPoints" bigint NULL');
    await runner.query('ALTER TABLE "jeho_own"."user_profiles" ADD COLUMN IF NOT EXISTS "cpBackgroundUrl" varchar(512)');
    await runner.query('ALTER TABLE "jeho_own"."user_profiles" ADD COLUMN IF NOT EXISTS "cpLogoUrl" varchar(512)');

    // CP-only cosmetic enum values are additive and safe to re-apply.
    const cosmeticEnumRows = await runner.query(
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
      await runner.query(`ALTER TYPE "${enumSchema}"."${enumName}" ADD VALUE IF NOT EXISTS 'cp_background'`);
      await runner.query(`ALTER TYPE "${enumSchema}"."${enumName}" ADD VALUE IF NOT EXISTS 'cp_logo'`);
    }

    await runner.query(`
      CREATE TABLE IF NOT EXISTS "jeho_own"."cp_memories" (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "relationshipId" uuid NOT NULL REFERENCES "jeho_own"."cp_relationships"(id) ON DELETE CASCADE,
        "userId" uuid NOT NULL REFERENCES "jeho_own"."users"(id) ON DELETE CASCADE,
        text varchar(500) NOT NULL DEFAULT '',
        "imageUrl" varchar(2048),
        "createdAt" timestamptz NOT NULL DEFAULT now()
      )
    `);
    await runner.query(`
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
    await runner.query(`
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
    await runner.query('CREATE INDEX IF NOT EXISTS idx_cp_memories_relationship_created ON "jeho_own"."cp_memories"("relationshipId", "createdAt" DESC)');

    await runner.query(`
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
    await runner.query(`
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

    // Normalize only single-column CHECK constraints on the type column. Keep
    // compound/unrelated checks. Add the replacement as NOT VALID, then validate
    // in a separate statement so the full scan uses PostgreSQL's weaker validation lock.
    for (const target of [
      { tableName: 'cp_relationships', constraintName: 'chk_cp_relationship_type_unified' },
      { tableName: 'cp_requests', constraintName: 'chk_cp_request_type_unified' },
    ]) {
      const typeColumn = await runner.query(
        'SELECT 1 FROM information_schema.columns WHERE table_schema = $1 AND table_name = $2 AND column_name = $3 LIMIT 1',
        ['jeho_own', target.tableName, 'type'],
      );
      if (!typeColumn.length) throw new Error(`[CpBootstrap] Missing type column on ${target.tableName}.`);

      const loadTypeChecks = () => runner.query(
        `SELECT c.conname, c.convalidated, pg_get_constraintdef(c.oid) AS definition,
                (c.conkey = ARRAY[a.attnum]::smallint[]) AS only_type_column
         FROM pg_constraint c
         JOIN pg_class t ON t.oid = c.conrelid
         JOIN pg_namespace n ON n.oid = t.relnamespace
         JOIN pg_attribute a ON a.attrelid = t.oid AND a.attname = 'type' AND NOT a.attisdropped
         WHERE n.nspname = $1 AND t.relname = $2 AND c.contype = 'c'` ,
        ['jeho_own', target.tableName],
      );
      let checks = await loadTypeChecks();
      const acceptsRelationship = (check: any) =>
        check.only_type_column === true && String(check.definition || '').toLowerCase().includes('relationship');
      if (!checks.some((check: any) => acceptsRelationship(check))) {
        const legacyTypes = ['friendship', 'brotherhood', 'engagement', 'marriage', 'love'];
        for (const check of checks) {
          const definition = String(check.definition || '').toLowerCase();
          if (check.only_type_column !== true || !legacyTypes.some((legacy) => definition.includes(legacy))) continue;
          const quotedName = String(check.conname).replaceAll('"', '""');
          await runner.query(`ALTER TABLE "jeho_own"."${target.tableName}" DROP CONSTRAINT "${quotedName}"`);
        }
        await runner.query(
          `ALTER TABLE "jeho_own"."${target.tableName}" ADD CONSTRAINT "${target.constraintName}" CHECK (type IN ('relationship','friendship','brotherhood','love','engagement','marriage')) NOT VALID`,
        );
      }

      checks = await loadTypeChecks();
      for (const check of checks) {
        if (acceptsRelationship(check) && check.convalidated !== true) {
          const quotedName = String(check.conname).replaceAll('"', '""');
          await runner.query(`ALTER TABLE "jeho_own"."${target.tableName}" VALIDATE CONSTRAINT "${quotedName}"`);
        }
      }
    }
    await runner.query('CREATE INDEX IF NOT EXISTS idx_cp_relationship_type_status_points ON "jeho_own"."cp_relationships"(type,status,points DESC)');
    await runner.query('CREATE INDEX IF NOT EXISTS idx_cp_relationship_user_a_status ON "jeho_own"."cp_relationships"("userAId",status)');
    await runner.query('CREATE INDEX IF NOT EXISTS idx_cp_relationship_user_b_status ON "jeho_own"."cp_relationships"("userBId",status)');
    await runner.query('CREATE INDEX IF NOT EXISTS idx_cp_request_recipient_status_expiry ON "jeho_own"."cp_requests"("toUserId",status,"expiresAt")');
    await runner.query('CREATE INDEX IF NOT EXISTS idx_cp_request_sender_status ON "jeho_own"."cp_requests"("fromUserId",status)');
    await runner.query('CREATE INDEX IF NOT EXISTS idx_cp_audit_created_at ON "jeho_own"."cp_audit_logs"("createdAt" DESC)');
    await runner.query('CREATE INDEX IF NOT EXISTS idx_cp_point_events_relationship_created ON "jeho_own"."cp_point_events"("relationshipId","createdAt" DESC)');
  } finally {
    if (lockAcquired) {
      await releaseJehoSchemaLock(runner).catch(() => undefined);
    }
    await runner.query('RESET lock_timeout').catch(() => undefined);
    await runner.query('RESET statement_timeout').catch(() => undefined);
    await runner.release().catch(() => undefined);
  }

  console.log('[CpBootstrap] CP tables/indexes verified (additive, independently committed steps).');
}
