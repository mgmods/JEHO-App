import { DataSource } from 'typeorm';

/**
 * Additive, idempotent bootstrap for the badge feature.
 * It never drops or rewrites existing records. If the app's users table is
 * missing from the isolated schema, it skips the badge setup and lets the API
 * continue starting instead of taking the service down.
 */
export async function ensureBadgeTables(dataSource: DataSource): Promise<void> {
  const [state] = await dataSource.query(
    'SELECT to_regclass($1) IS NOT NULL AS users_exists',
    ['"jeho_own"."users"'],
  );

  if (!state?.users_exists) {
    console.warn(
      '[BadgesBootstrap] Skipped badge tables: "jeho_own"."users" was not found; API startup continues.',
    );
    return;
  }

  await dataSource.transaction(async (manager) => {
    await manager.query(`
      CREATE TABLE IF NOT EXISTS "jeho_own"."badge_definitions" (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        code varchar(64) NOT NULL UNIQUE,
        name varchar(100) NOT NULL,
        description varchar(1000),
        "imageUrl" varchar(2048),
        "isActive" boolean NOT NULL DEFAULT true,
        "sortOrder" integer NOT NULL DEFAULT 0,
        "conditionType" varchar(32),
        "conditionTarget" integer,
        "createdAt" timestamptz NOT NULL DEFAULT now(),
        "updatedAt" timestamptz NOT NULL DEFAULT now(),
        CONSTRAINT chk_badge_condition_type CHECK (
          "conditionType" IS NULL OR "conditionType" IN ('account_age_days', 'messages_sent', 'followers_count', 'diamonds_received')
        ),
        CONSTRAINT chk_badge_condition_target CHECK (
          "conditionTarget" IS NULL OR "conditionTarget" > 0
        )
      )
    `);

    await manager.query(
      'ALTER TABLE "jeho_own"."badge_definitions" ADD COLUMN IF NOT EXISTS "conditionType" varchar(32)',
    );
    await manager.query(
      'ALTER TABLE "jeho_own"."badge_definitions" ADD COLUMN IF NOT EXISTS "conditionTarget" integer',
    );

    await manager.query(`
      CREATE TABLE IF NOT EXISTS "jeho_own"."user_badges" (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "userId" uuid NOT NULL REFERENCES "jeho_own"."users"(id) ON DELETE CASCADE,
        "badgeId" uuid NOT NULL REFERENCES "jeho_own"."badge_definitions"(id) ON DELETE RESTRICT,
        source varchar(16) NOT NULL DEFAULT 'manual' CHECK (source IN ('manual', 'system')),
        "awardedBy" uuid REFERENCES "jeho_own"."users"(id) ON DELETE SET NULL,
        note varchar(500),
        "awardedAt" timestamptz NOT NULL DEFAULT now(),
        "isRevoked" boolean NOT NULL DEFAULT false,
        "revokedAt" timestamptz
      )
    `);

    await manager.query(`
      CREATE INDEX IF NOT EXISTS idx_user_badges_active_user
      ON "jeho_own"."user_badges" ("userId", "awardedAt")
      WHERE "isRevoked" = false
    `);
    await manager.query(`
      CREATE INDEX IF NOT EXISTS idx_user_badges_badge
      ON "jeho_own"."user_badges" ("badgeId")
      WHERE "isRevoked" = false
    `);
  });

  console.log('[BadgesBootstrap] Badge tables verified/created in schema jeho_own.');
}
