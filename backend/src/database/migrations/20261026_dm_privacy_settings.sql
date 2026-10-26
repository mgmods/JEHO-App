-- Persisted direct-message privacy controls. Safe to run repeatedly.
ALTER TABLE IF EXISTS jeho_own.user_profiles
  ADD COLUMN IF NOT EXISTS "dmEnabled" boolean NOT NULL DEFAULT true,
  ADD COLUMN IF NOT EXISTS "dmAudience" varchar(16) NOT NULL DEFAULT 'everyone';

ALTER TABLE IF EXISTS public.user_profiles
  ADD COLUMN IF NOT EXISTS "dmEnabled" boolean NOT NULL DEFAULT true,
  ADD COLUMN IF NOT EXISTS "dmAudience" varchar(16) NOT NULL DEFAULT 'everyone';

ALTER TABLE IF EXISTS jeho_own.user_profiles
  ADD CONSTRAINT user_profiles_dm_audience_check
  CHECK ("dmAudience" IN ('everyone', 'followers', 'friends', 'nobody'));

ALTER TABLE IF EXISTS public.user_profiles
  ADD CONSTRAINT user_profiles_dm_audience_check
  CHECK ("dmAudience" IN ('everyone', 'followers', 'friends', 'nobody'));
