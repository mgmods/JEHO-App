-- Additive-only VIP visual customization support. Existing cosmetics and users are preserved.
ALTER TYPE cosmetics_type_enum ADD VALUE IF NOT EXISTS 'profile_card';
ALTER TYPE cosmetics_type_enum ADD VALUE IF NOT EXISTS 'chat_bubble';
ALTER TYPE cosmetics_type_enum ADD VALUE IF NOT EXISTS 'nameplate';

ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS "profileCardUrl" varchar(512);
ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS "chatBubbleUrl" varchar(512);
ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS "nameplateUrl" varchar(512);
