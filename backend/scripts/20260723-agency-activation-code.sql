-- Unique activation code + agency notification style per agency.
ALTER TABLE agencies
  ADD COLUMN IF NOT EXISTS "activationCode" VARCHAR(16),
  ADD COLUMN IF NOT EXISTS "notificationStyle" VARCHAR(32) NOT NULL DEFAULT 'welcome';

-- Backfill unique codes for existing agencies (8-char, no ambiguous chars).
DO $$
DECLARE
  r RECORD;
  code TEXT;
  alphabet TEXT := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  i INT;
  ok BOOLEAN;
BEGIN
  FOR r IN SELECT id FROM agencies WHERE "activationCode" IS NULL OR TRIM("activationCode") = '' LOOP
    ok := FALSE;
    WHILE NOT ok LOOP
      code := '';
      FOR i IN 1..8 LOOP
        code := code || substr(alphabet, 1 + floor(random() * length(alphabet))::int, 1);
      END LOOP;
      IF NOT EXISTS (SELECT 1 FROM agencies WHERE "activationCode" = code) THEN
        UPDATE agencies SET "activationCode" = code WHERE id = r.id;
        ok := TRUE;
      END IF;
    END LOOP;
  END LOOP;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_agencies_activation_code
  ON agencies ("activationCode");
