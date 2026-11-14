-- Mikoo room-more display toggles (synced for all viewers).
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS "chatZoneEnabled" boolean NOT NULL DEFAULT true;
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS "charmEnabled" boolean NOT NULL DEFAULT true;
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS "bannerEnabled" boolean NOT NULL DEFAULT true;
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS "micInteractEnabled" boolean NOT NULL DEFAULT true;
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS "entryEffectsEnabled" boolean NOT NULL DEFAULT true;
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS "lowGiftEffectsEnabled" boolean NOT NULL DEFAULT true;
