-- Private, per-recipient custom gift grants. Media remains in the existing durable uploads pipeline.
CREATE TABLE IF NOT EXISTS custom_gift_grants (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "userId" uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  "giftId" uuid NOT NULL UNIQUE REFERENCES gifts(id) ON DELETE CASCADE,
  "grantedBy" uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  "mediaType" varchar(16) NOT NULL CHECK ("mediaType" IN ('image','video','gif')),
  "mediaUrl" varchar(512) NOT NULL,
  "durationDays" integer NULL CHECK ("durationDays" IS NULL OR "durationDays" >= 1),
  "startsAt" timestamptz NOT NULL DEFAULT now(),
  "expiresAt" timestamptz NULL,
  "revokedAt" timestamptz NULL,
  "createdAt" timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_custom_gift_grants_owner_expiry ON custom_gift_grants ("userId", "expiresAt");
CREATE INDEX IF NOT EXISTS idx_custom_gift_grants_gift_id ON custom_gift_grants ("giftId");
