import { DataSource } from 'typeorm';

/**
 * Additive startup migration for vanity-ID visual options.
 * Existing vanity IDs and ownership/lease data are preserved.
 */
export async function ensureVanityIdStyleColumns(dataSource: DataSource): Promise<void> {
  const rows = await dataSource.query(
    `SELECT 1 FROM information_schema.tables
     WHERE table_schema = 'jeho_own' AND table_name = 'vanity_ids' LIMIT 1`,
  );
  if (!rows.length) return;

  await dataSource.query(`
    ALTER TABLE "jeho_own"."vanity_ids"
      ADD COLUMN IF NOT EXISTS "styleMode" varchar(16) NOT NULL DEFAULT 'static',
      ADD COLUMN IF NOT EXISTS "styleColor1" varchar(7) NOT NULL DEFAULT '#52E8FF',
      ADD COLUMN IF NOT EXISTS "styleColor2" varchar(7) NOT NULL DEFAULT '#C084FC',
      ADD COLUMN IF NOT EXISTS "styleColor3" varchar(7) NOT NULL DEFAULT '#FF7AC8',
      ADD COLUMN IF NOT EXISTS "styleGlow" boolean NOT NULL DEFAULT true
  `);
}
