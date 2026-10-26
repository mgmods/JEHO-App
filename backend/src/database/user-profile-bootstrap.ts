import { DataSource } from 'typeorm';

/**
 * Additive compatibility migration for profile columns used by newer JEHO
 * builds. Older production databases may predate medal display and DM privacy
 * settings. Add only missing columns; never rewrite or delete profile data.
 */
export async function ensureUserProfileColumns(dataSource: DataSource): Promise<void> {
  const rows = await dataSource.query(
    `SELECT 1 FROM information_schema.tables
     WHERE table_schema = 'jeho_own' AND table_name = 'user_profiles'
     LIMIT 1`,
  );
  if (!rows.length) {
    console.warn('[UserProfileBootstrap] jeito_own.user_profiles table not found; skipping additive profile migration.');
    return;
  }

  await dataSource.query(`
    ALTER TABLE "jeho_own"."user_profiles"
      ADD COLUMN IF NOT EXISTS "displayedMedalIds" text,
      ADD COLUMN IF NOT EXISTS "allowDmFromStrangers" boolean NOT NULL DEFAULT true,
      ADD COLUMN IF NOT EXISTS "dmEnabled" boolean NOT NULL DEFAULT true,
      ADD COLUMN IF NOT EXISTS "dmAudience" varchar(16) NOT NULL DEFAULT 'everyone',
      ADD COLUMN IF NOT EXISTS "dmGiftGateEnabled" boolean NOT NULL DEFAULT false,
      ADD COLUMN IF NOT EXISTS "dmRequiredGiftId" uuid
  `);

  console.log('[UserProfileBootstrap] Verified medal display and DM privacy columns.');
}
