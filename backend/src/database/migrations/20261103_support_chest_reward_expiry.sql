-- Support chest temporary gift rewards expire without deleting permanent wallet credits.
-- Safe to run repeatedly on existing JEHO databases.
DO $$
BEGIN
  IF to_regclass('jeho_own.gift_sends') IS NOT NULL THEN
    ALTER TABLE jeho_own.gift_sends ADD COLUMN IF NOT EXISTS "expiresAt" timestamptz;
    CREATE INDEX IF NOT EXISTS "IDX_gift_sends_expiresAt" ON jeho_own.gift_sends ("expiresAt");
  END IF;
  IF to_regclass('public.gift_sends') IS NOT NULL THEN
    ALTER TABLE public.gift_sends ADD COLUMN IF NOT EXISTS "expiresAt" timestamptz;
    CREATE INDEX IF NOT EXISTS "IDX_gift_sends_expiresAt" ON public.gift_sends ("expiresAt");
  END IF;
END $$;
