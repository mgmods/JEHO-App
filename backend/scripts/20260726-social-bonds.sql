ALTER TABLE social_requests
  ADD COLUMN IF NOT EXISTS "bondGiftCoins" INT NOT NULL DEFAULT 0;

INSERT INTO app_settings (id, key, value, description, "createdAt", "updatedAt")
SELECT gen_random_uuid(), v.key, v.value, v.description, NOW(), NOW()
FROM (VALUES
  ('social.bond_icons', '{"sibling":"","fans":"","love":"","couple":"","relation":"","guardian":"","friend":""}', 'Bond type icon URLs'),
  ('social.bond_gift_coins', '{"sibling":500,"fans":200,"love":1000,"couple":2000,"relation":500,"guardian":800}', 'Bond gift coin costs on accept')
) AS v(key, value, description)
WHERE NOT EXISTS (SELECT 1 FROM app_settings s WHERE s.key = v.key);
