import { DataSource } from 'typeorm';

/** Additive, idempotent schema for persistent user moments. */
export async function ensureMomentsTables(dataSource: DataSource): Promise<void> {
  const [state] = await dataSource.query(
    'SELECT to_regclass($1) IS NOT NULL AS users_exists',
    ['"jeho_own"."users"'],
  );
  if (!state?.users_exists) {
    console.warn('[MomentsBootstrap] Skipped: users table does not exist yet.');
    return;
  }
  // Extend the existing shared reports enum additively so moment reports appear in the admin Reports page.
  await dataSource.query(`
    DO $
    DECLARE enum_name text;
    BEGIN
      SELECT t.typname INTO enum_name
      FROM pg_type t
      JOIN pg_namespace n ON n.oid = t.typnamespace
      WHERE n.nspname = 'jeho_own'
        AND t.typtype = 'e'
        AND t.typname ILIKE 'reports_target%enum'
      ORDER BY t.typname
      LIMIT 1;
      IF enum_name IS NOT NULL THEN
        EXECUTE format('ALTER TYPE %I.%I ADD VALUE IF NOT EXISTS %L', 'jeho_own', enum_name, 'moment');
      END IF;
    END $;
  `);
  await dataSource.transaction(async (manager) => {
    await manager.query(`
      CREATE TABLE IF NOT EXISTS "jeho_own"."moments" (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "userId" uuid NOT NULL REFERENCES "jeho_own"."users"(id) ON DELETE CASCADE,
        body varchar(2000) NOT NULL DEFAULT '',
        "imageUrl" varchar(1024),
        visibility varchar(16) NOT NULL DEFAULT 'public'
          CHECK (visibility IN ('public', 'followers', 'friends', 'private')),
        "createdAt" timestamptz NOT NULL DEFAULT now(),
        "updatedAt" timestamptz NOT NULL DEFAULT now(),
        CHECK (length(trim(body)) > 0 OR "imageUrl" IS NOT NULL)
      )
    `);
    await manager.query(`CREATE INDEX IF NOT EXISTS "IDX_moments_created" ON "jeho_own"."moments" ("createdAt" DESC)`);
    await manager.query(`CREATE INDEX IF NOT EXISTS "IDX_moments_user_created" ON "jeho_own"."moments" ("userId", "createdAt" DESC)`);
    await manager.query(`
      CREATE TABLE IF NOT EXISTS "jeho_own"."moment_likes" (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "momentId" uuid NOT NULL REFERENCES "jeho_own"."moments"(id) ON DELETE CASCADE,
        "userId" uuid NOT NULL REFERENCES "jeho_own"."users"(id) ON DELETE CASCADE,
        "createdAt" timestamptz NOT NULL DEFAULT now(),
        CONSTRAINT "UQ_moment_likes_moment_user" UNIQUE ("momentId", "userId")
      )
    `);
    await manager.query(`
      CREATE TABLE IF NOT EXISTS "jeho_own"."moment_comments" (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "momentId" uuid NOT NULL REFERENCES "jeho_own"."moments"(id) ON DELETE CASCADE,
        "userId" uuid NOT NULL REFERENCES "jeho_own"."users"(id) ON DELETE CASCADE,
        body varchar(1000) NOT NULL,
        "createdAt" timestamptz NOT NULL DEFAULT now()
      )
    `);
    await manager.query(`CREATE INDEX IF NOT EXISTS "IDX_moment_comments_moment_created" ON "jeho_own"."moment_comments" ("momentId", "createdAt" ASC)`);
  });
  console.log('[MomentsBootstrap] Persistent moments tables are ready.');
}
