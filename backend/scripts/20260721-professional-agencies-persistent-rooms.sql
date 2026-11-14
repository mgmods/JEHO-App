BEGIN;

-- Remove obsolete live-video notifications before TypeORM drops that enum value.
DELETE FROM notifications WHERE type::text = 'live';

CREATE TABLE IF NOT EXISTS agency_applications (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  "applicantId" UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  "proposedName" VARCHAR(128) NOT NULL,
  description TEXT NOT NULL,
  "businessPlan" TEXT NOT NULL,
  country VARCHAR(100) NOT NULL,
  "contactEmail" VARCHAR(254) NOT NULL,
  "contactPhone" VARCHAR(32) NOT NULL,
  "socialLink" VARCHAR(512),
  experience TEXT NOT NULL,
  "expectedHostCount" INT NOT NULL,
  "documentUrls" JSONB NOT NULL DEFAULT '[]'::jsonb,
  "termsAccepted" BOOLEAN NOT NULL DEFAULT FALSE,
  status VARCHAR(24) NOT NULL DEFAULT 'pending',
  "reviewNote" TEXT,
  "reviewedById" UUID REFERENCES users(id) ON DELETE SET NULL,
  "agencyId" UUID REFERENCES agencies(id) ON DELETE SET NULL,
  "reviewedAt" TIMESTAMPTZ,
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE agency_applications
  ALTER COLUMN status TYPE VARCHAR(24) USING status::text;
DROP TYPE IF EXISTS agency_application_status;

CREATE INDEX IF NOT EXISTS idx_agency_applications_applicant
  ON agency_applications("applicantId");
CREATE INDEX IF NOT EXISTS idx_agency_applications_status
  ON agency_applications(status);
CREATE UNIQUE INDEX IF NOT EXISTS uq_agency_application_pending_applicant
  ON agency_applications("applicantId") WHERE status = 'pending';

ALTER TABLE agency_members
  ADD COLUMN IF NOT EXISTS status VARCHAR(16) NOT NULL DEFAULT 'active';

INSERT INTO agency_members (
  id, "agencyId", "userId", role, "diamondsContributed",
  status, "isActive", "joinedAt", "updatedAt"
)
SELECT
  gen_random_uuid(), agency.id, agency."ownerId", 'owner', 0,
  'active', TRUE, NOW(), NOW()
FROM agencies agency
WHERE NOT EXISTS (
  SELECT 1 FROM agency_members member
  WHERE member."agencyId" = agency.id AND member."userId" = agency."ownerId"
)
ON CONFLICT ("agencyId", "userId") DO UPDATE SET
  role = 'owner', status = 'active', "isActive" = TRUE;

UPDATE agencies agency
SET "memberCount" = (
  SELECT COUNT(*)::INT
  FROM agency_members member
  WHERE member."agencyId" = agency.id
    AND member.status = 'active'
    AND member."isActive" = TRUE
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_agencies_name_ci
  ON agencies (LOWER(name));

ALTER TABLE rooms ADD COLUMN IF NOT EXISTS "agencyId" UUID;
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS "roomKind" VARCHAR(16) NOT NULL DEFAULT 'standard';
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS "isPersistent" BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS "activeHostId" UUID;
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS "accessMode" VARCHAR(16) NOT NULL DEFAULT 'free';
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS "entryFeeCoins" INT NOT NULL DEFAULT 0;

DO $$ BEGIN
  ALTER TABLE rooms
    ADD CONSTRAINT fk_rooms_agency
    FOREIGN KEY ("agencyId") REFERENCES agencies(id) ON DELETE SET NULL;
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

ALTER TABLE rooms DROP CONSTRAINT IF EXISTS "UQ_room_agency";
DROP INDEX IF EXISTS "UQ_room_agency";

UPDATE rooms
SET
  "roomKind" = 'agency',
  "isPersistent" = TRUE,
  "isPublic" = TRUE,
  "hasPassword" = FALSE,
  "passwordHash" = NULL,
  "accessMode" = 'free',
  "entryFeeCoins" = 0,
  status = 'open'
WHERE "agencyId" IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_room_agency_host
  ON rooms("agencyId", "hostId") WHERE "agencyId" IS NOT NULL;

COMMIT;
