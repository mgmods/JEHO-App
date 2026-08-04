-- Platform staff roles (manager / super) for in-app + dashboard admin.
ALTER TABLE users ADD COLUMN IF NOT EXISTS "staffRole" varchar(16) DEFAULT NULL;

-- Existing dashboard admins become super (explicit role).
UPDATE users
SET "staffRole" = 'super'
WHERE "isAdmin" = true
  AND ("staffRole" IS NULL OR "staffRole" = '' OR "staffRole" = 'none');
