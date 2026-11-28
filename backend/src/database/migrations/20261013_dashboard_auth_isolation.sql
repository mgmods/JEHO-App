-- JEHO-OWN: isolate dashboard identities from mobile app accounts.
-- Additive-only migration; no users, credentials, or other data are deleted.
ALTER TABLE admin_users
  ADD COLUMN IF NOT EXISTS "displayName" varchar(120) NOT NULL DEFAULT 'Dashboard Admin';

ALTER TABLE admin_users
  ADD COLUMN IF NOT EXISTS "staffRole" varchar(16) NOT NULL DEFAULT 'operator';

ALTER TABLE admin_users
  ADD COLUMN IF NOT EXISTS "dashboardPermissions" jsonb NULL;
