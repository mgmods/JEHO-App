-- Safe cleanup for legacy tables that have no entity, module, controller,
-- dashboard route, or Android client path in the current application.
-- A pg_dump must be completed successfully before this migration is run.

BEGIN;

-- Preserve likes written by the superseded drama_likes implementation.
DO $cleanup$
BEGIN
  IF to_regclass('public.drama_likes') IS NOT NULL THEN
    EXECUTE $migration$
      INSERT INTO drama_reactions (
        id,
        "userId",
        "targetType",
        "targetId",
        "createdAt"
      )
      SELECT
        id,
        "userId",
        "targetType",
        "targetId",
        "createdAt"
      FROM drama_likes
      ON CONFLICT ("userId", "targetType", "targetId") DO NOTHING
    $migration$;
  END IF;
END
$cleanup$;

DROP TABLE IF EXISTS drama_likes;

-- The fruit wheel was never connected to a Nest module or client route.
-- Drop the child first so no CASCADE is needed.
DROP TABLE IF EXISTS fruit_wheel_bets;
DROP TABLE IF EXISTS fruit_wheel_rounds;

DELETE FROM app_settings
WHERE key LIKE 'games.fruit_wheel.%';

COMMIT;
