-- Additive-only support for the Mysterious Man VIP feature.
-- Existing user/account data is preserved; mode defaults off for everyone.
ALTER TABLE users
  ADD COLUMN IF NOT EXISTS "mysteriousMode" boolean NOT NULL DEFAULT false;
