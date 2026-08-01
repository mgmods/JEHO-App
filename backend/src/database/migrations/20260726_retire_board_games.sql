-- Retire ONO / Domino / Ludo from live catalog and matchmaking leftovers.
-- Keeps boss-battle rows; only strips retired casual kinds from app_games JSON.

UPDATE app_settings
SET value = COALESCE((
  SELECT jsonb_agg(elem)::text
  FROM jsonb_array_elements(value::jsonb) elem
  WHERE lower(COALESCE(elem->>'id', '')) NOT IN ('ono', 'domino', 'ludo')
    AND lower(COALESCE(elem->>'playUrl', '')) NOT LIKE '%ono.html%'
    AND lower(COALESCE(elem->>'playUrl', '')) NOT LIKE '%domino.html%'
    AND lower(COALESCE(elem->>'playUrl', '')) NOT LIKE '%ludo.html%'
), '[]')
WHERE key = 'app_games'
  AND value IS NOT NULL
  AND value <> ''
  AND left(trim(value), 1) = '[';

DELETE FROM casual_matches
WHERE kind IN ('ono', 'domino', 'ludo');
