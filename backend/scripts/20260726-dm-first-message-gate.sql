ALTER TABLE user_profiles
  ADD COLUMN IF NOT EXISTS "publicChatCount" INT NOT NULL DEFAULT 0;

INSERT INTO app_settings (id, key, value, description, "createdAt", "updatedAt")
SELECT gen_random_uuid(), v.key, v.value, v.description, NOW(), NOW()
FROM (VALUES
  ('features.dm_require_gift', 'false', 'Require a gift before the first private message'),
  ('features.dm_gift_min_coins', '1', 'Minimum gift coins to unlock first DM'),
  ('features.dm_require_public_activity', 'false', 'Require N public room chats before first DM'),
  ('features.dm_public_activity_count', '10', 'Public room chat count required before first DM')
) AS v(key, value, description)
WHERE NOT EXISTS (SELECT 1 FROM app_settings s WHERE s.key = v.key);
