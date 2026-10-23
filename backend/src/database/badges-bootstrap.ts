import { DataSource } from 'typeorm';

/**
 * Idempotently create the additive badge tables expected by the current MG-JEHO
 * entities. Existing rows are never dropped, rewritten, or reset.
 */
export async function ensureBadgeTables(dataSource: DataSource): Promise<void> {
  const [state] = await dataSource.query(
    'SELECT to_regclass($1) IS NOT NULL AS users_exists',
    ['"jeho_own"."users"'],
  );

  if (!state?.users_exists) {
    console.warn('[BadgesBootstrap] Skipped badge tables: "jeho_own"."users" does not exist.');
    return;
  }

  await dataSource.transaction(async (manager) => {
    await manager.query(`
      CREATE TABLE IF NOT EXISTS "jeho_own"."badges" (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        name varchar(80) NOT NULL,
        description varchar(500) NOT NULL DEFAULT '',
        "imageUrl" varchar(512) NOT NULL,
        category varchar(32) NOT NULL DEFAULT 'general',
        "awardType" varchar(32) NOT NULL DEFAULT 'manual',
        "achievementKey" varchar(64),
        "achievementThreshold" integer NOT NULL DEFAULT 0,
        "isActive" boolean NOT NULL DEFAULT true,
        "isAnimated" boolean NOT NULL DEFAULT false,
        "sortOrder" integer NOT NULL DEFAULT 0,
        "createdAt" timestamptz NOT NULL DEFAULT now(),
        "updatedAt" timestamptz NOT NULL DEFAULT now()
      )
    `);
    await manager.query(`
      CREATE INDEX IF NOT EXISTS "IDX_badges_active_sort"
      ON "jeho_own"."badges" ("isActive", "sortOrder")
    `);
    await manager.query(`
      CREATE TABLE IF NOT EXISTS "jeho_own"."user_badges" (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "userId" uuid NOT NULL REFERENCES "jeho_own"."users"(id) ON DELETE CASCADE,
        "badgeId" uuid NOT NULL REFERENCES "jeho_own"."badges"(id) ON DELETE CASCADE,
        "grantedByAdminId" uuid,
        source varchar(32) NOT NULL DEFAULT 'manual',
        "grantedAt" timestamptz NOT NULL DEFAULT now(),
        CONSTRAINT "UQ_user_badges_user_badge" UNIQUE ("userId", "badgeId")
      )
    `);
    await manager.query(`
      ALTER TABLE "jeho_own"."user_badges"
      ADD COLUMN IF NOT EXISTS "expiresAt" timestamptz
    `);
    await manager.query(`
      CREATE INDEX IF NOT EXISTS "IDX_user_badges_user_expiry"
      ON "jeho_own"."user_badges" ("userId", "expiresAt")
    `);
    await manager.query(`
      CREATE INDEX IF NOT EXISTS "IDX_user_badges_user"
      ON "jeho_own"."user_badges" ("userId")
    `);
  });

  console.log('[BadgesBootstrap] Verified current MG-JEHO badge tables in schema jeho_own.');
}
