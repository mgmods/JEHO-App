-- Promo economy tables (vanity leases + monthly progress)
CREATE TABLE IF NOT EXISTS "vanity_ids" (
  "id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "publicId" varchar(16) NOT NULL,
  "status" varchar(16) NOT NULL DEFAULT 'available',
  "priceCoins" int NOT NULL DEFAULT 0,
  "ownerUserId" uuid NULL,
  "reservedUntil" timestamptz NULL,
  "purchasedAt" timestamptz NULL,
  "expiresAt" timestamptz NULL,
  "previousPublicId" varchar(32) NULL,
  "createdAt" timestamptz NOT NULL DEFAULT now(),
  "updatedAt" timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS "UQ_vanity_ids_publicId" ON "vanity_ids" ("publicId");

CREATE TABLE IF NOT EXISTS "user_promo_progress" (
  "id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "userId" uuid NOT NULL,
  "monthKey" varchar(7) NOT NULL,
  "usdSpent" decimal(12,2) NOT NULL DEFAULT 0,
  "claimedOfferIds" text NOT NULL DEFAULT '[]',
  "createdAt" timestamptz NOT NULL DEFAULT now(),
  "updatedAt" timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS "UQ_user_promo_progress_user_month"
  ON "user_promo_progress" ("userId", "monthKey");
