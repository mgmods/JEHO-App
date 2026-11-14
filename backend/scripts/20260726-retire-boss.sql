-- Remove Fire Force / daily boss from catalog and settings.
UPDATE app_settings
SET value = COALESCE((
  SELECT jsonb_agg(elem)::text
  FROM jsonb_array_elements(value::jsonb) elem
  WHERE lower(COALESCE(elem->>'id', '')) NOT IN ('fireforce', 'fire-force', 'boss')
    AND lower(COALESCE(elem->>'playUrl', '')) NOT LIKE '%fireforce%'
), '[]')
WHERE key = 'app_games'
  AND value IS NOT NULL
  AND value <> ''
  AND left(trim(value), 1) = '[';

DELETE FROM app_settings
WHERE key LIKE 'games.boss.%';
