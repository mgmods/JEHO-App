-- Additive badge system. Run manually after reviewing the target database/schema.
-- Does not modify VIP tables or existing user/profile data.
CREATE TABLE IF NOT EXISTS badge_definitions (
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
  CONSTRAINT chk_badge_condition_target CHECK ("conditionTarget" IS NULL OR "conditionTarget" > 0)
);

-- Also support safe re-runs if a prior partial deployment created the table.
ALTER TABLE badge_definitions ADD COLUMN IF NOT EXISTS "conditionType" varchar(32);
ALTER TABLE badge_definitions ADD COLUMN IF NOT EXISTS "conditionTarget" integer;

DO $
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'chk_badge_condition_type'
      AND conrelid = 'badge_definitions'::regclass
  ) THEN
    ALTER TABLE badge_definitions
      ADD CONSTRAINT chk_badge_condition_type CHECK (
        "conditionType" IS NULL OR "conditionType" IN ('account_age_days', 'messages_sent', 'followers_count', 'diamonds_received')
      );
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'chk_badge_condition_target'
      AND conrelid = 'badge_definitions'::regclass
  ) THEN
    ALTER TABLE badge_definitions
      ADD CONSTRAINT chk_badge_condition_target CHECK ("conditionTarget" IS NULL OR "conditionTarget" > 0);
  END IF;
END $;

CREATE TABLE IF NOT EXISTS user_badges (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "userId" uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  "badgeId" uuid NOT NULL REFERENCES badge_definitions(id) ON DELETE RESTRICT,
  source varchar(16) NOT NULL DEFAULT 'manual' CHECK (source IN ('manual', 'system')),
  "awardedBy" uuid REFERENCES users(id) ON DELETE SET NULL,
  note varchar(500),
  "awardedAt" timestamptz NOT NULL DEFAULT now(),
  "isRevoked" boolean NOT NULL DEFAULT false,
  "revokedAt" timestamptz
);

CREATE INDEX IF NOT EXISTS idx_user_badges_active_user
  ON user_badges ("userId", "awardedAt") WHERE "isRevoked" = false;
CREATE INDEX IF NOT EXISTS idx_user_badges_badge
  ON user_badges ("badgeId") WHERE "isRevoked" = false;
