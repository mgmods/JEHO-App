-- Persist each user's public medal showcase (maximum five keys enforced by API).
ALTER TABLE user_profiles
  ADD COLUMN IF NOT EXISTS "displayedMedalIds" text;
