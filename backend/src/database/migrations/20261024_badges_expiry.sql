-- Add the expiration column required by the badge grant service.
-- Safe for existing installations: repeatable and preserves existing ownership rows.
ALTER TABLE IF EXISTS jeho_own.user_badges
  ADD COLUMN IF NOT EXISTS "expiresAt" timestamptz;

-- Some older installations created the tables in the default schema.
ALTER TABLE IF EXISTS public.user_badges
  ADD COLUMN IF NOT EXISTS "expiresAt" timestamptz;
