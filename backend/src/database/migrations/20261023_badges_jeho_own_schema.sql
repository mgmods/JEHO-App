-- Ensure badge catalogue and ownership tables exist in the production application schema.
-- Additive-only: preserves existing users, notifications, and badge records.
CREATE SCHEMA IF NOT EXISTS jeho_own;

CREATE TABLE IF NOT EXISTS jeho_own.badges (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name varchar(80) NOT NULL,
  description varchar(500) NOT NULL DEFAULT '',
  "imageUrl" varchar(512) NOT NULL,
  category varchar(32) NOT NULL DEFAULT 'general',
  "awardType" varchar(32) NOT NULL DEFAULT 'manual',
  "achievementKey" varchar(64),
  "achievementThreshold" integer NOT NULL DEFAULT 0,
  "isActive" boolean NOT NULL DEFAULT true,
  "isAnimated" boolean NOT NULL DEFAULT false,
  "sortOrder" integer NOT NULL DEFAULT 0,
  "createdAt" timestamptz NOT NULL DEFAULT now(),
  "updatedAt" timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS "IDX_badges_active_sort"
  ON jeho_own.badges ("isActive", "sortOrder");

CREATE TABLE IF NOT EXISTS jeho_own.user_badges (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "userId" uuid NOT NULL REFERENCES jeho_own.users(id) ON DELETE CASCADE,
  "badgeId" uuid NOT NULL REFERENCES jeho_own.badges(id) ON DELETE CASCADE,
  "grantedByAdminId" uuid,
  source varchar(32) NOT NULL DEFAULT 'manual',
  "grantedAt" timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT "UQ_user_badges_user_badge" UNIQUE ("userId", "badgeId")
);
CREATE INDEX IF NOT EXISTS "IDX_user_badges_user"
  ON jeho_own.user_badges ("userId");
