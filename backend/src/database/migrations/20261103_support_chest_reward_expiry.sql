-- Support chest temporary gift rewards expire without deleting permanent wallet credits.
-- Safe to run repeatedly on existing JEHO databases.
ALTER TABLE IF EXISTS jeho_own.gift_sends
  ADD COLUMN IF NOT EXISTS "expiresAt" timestamptz;

ALTER TABLE IF EXISTS public.gift_sends
  ADD COLUMN IF NOT EXISTS "expiresAt" timestamptz;

CREATE INDEX IF NOT EXISTS "IDX_gift_sends_expiresAt"
  ON jeho_own.gift_sends ("expiresAt");

CREATE INDEX IF NOT EXISTS "IDX_gift_sends_expiresAt_public"
  ON public.gift_sends ("expiresAt");
