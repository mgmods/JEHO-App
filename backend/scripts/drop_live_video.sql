-- Drop LIVE VIDEO tables, enums, settings, and leftover columns.
-- Safe to re-run: uses IF EXISTS / exception guards where needed.

BEGIN;

-- PK battles first (FK to live_streams)
DROP TABLE IF EXISTS pk_battles CASCADE;
DROP TABLE IF EXISTS live_streams CASCADE;
DROP TABLE IF EXISTS live_moderation_events CASCADE;

-- Enums used only by live video
DROP TYPE IF EXISTS live_status CASCADE;
DROP TYPE IF EXISTS live_mode CASCADE;
DROP TYPE IF EXISTS pk_status CASCADE;
DROP TYPE IF EXISTS live_moderation_action CASCADE;

-- App settings keys related to live video / NSFW live moderation
DELETE FROM app_settings
WHERE key IN (
  'live.video_enabled',
  'live.welcome_message',
  'requireStreamReview',
  'live_nsfw_enabled',
  'live_nsfw_confidence',
  'live_nsfw_consecutive',
  'live_nsfw_warn_strikes',
  'live_nsfw_mute_strikes',
  'live_nsfw_stream_ban_strikes',
  'live_nsfw_perm_ban_strikes',
  'live_nsfw_stream_ban_hours',
  'live_nsfw_scan_interval_sec',
  'live_nsfw_cooldown_sec'
)
OR key LIKE 'live.guestRequests.%'
OR key LIKE 'live_nsfw_%'
OR key LIKE 'live.%';

-- Optional column cleanups
ALTER TABLE IF EXISTS gift_sends DROP COLUMN IF EXISTS "streamId";
ALTER TABLE IF EXISTS users DROP COLUMN IF EXISTS "liveBanUntil";

COMMIT;
