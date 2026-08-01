BEGIN;

ALTER TABLE rooms
  ADD COLUMN IF NOT EXISTS "musicUrl" varchar(1024),
  ADD COLUMN IF NOT EXISTS "musicTitle" varchar(160),
  ADD COLUMN IF NOT EXISTS "musicArtist" varchar(160),
  ADD COLUMN IF NOT EXISTS "musicStatus" varchar(16) NOT NULL DEFAULT 'stopped',
  ADD COLUMN IF NOT EXISTS "musicPositionMs" bigint NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS "musicStartedAt" timestamptz;

ALTER TABLE room_moderators
  ADD COLUMN IF NOT EXISTS "canManageMusic" boolean NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS "canChangeFrames" boolean NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS "canControlGames" boolean NOT NULL DEFAULT true,
  ADD COLUMN IF NOT EXISTS "canMute" boolean NOT NULL DEFAULT true,
  ADD COLUMN IF NOT EXISTS "canKick" boolean NOT NULL DEFAULT true,
  ADD COLUMN IF NOT EXISTS "canBan" boolean NOT NULL DEFAULT true,
  ADD COLUMN IF NOT EXISTS "canManageSeats" boolean NOT NULL DEFAULT true,
  ADD COLUMN IF NOT EXISTS "canInvite" boolean NOT NULL DEFAULT true,
  ADD COLUMN IF NOT EXISTS "canManageRoom" boolean NOT NULL DEFAULT false;

ALTER TABLE room_seats
  ADD COLUMN IF NOT EXISTS "isModeratorMuted" boolean NOT NULL DEFAULT false;

ALTER TABLE room_access
  ADD COLUMN IF NOT EXISTS "expiresAt" timestamptz;

ALTER TABLE rooms
  ADD COLUMN IF NOT EXISTS "backgroundEquippedById" uuid,
  ADD COLUMN IF NOT EXISTS "roomCardEquippedById" uuid;

UPDATE rooms
   SET "backgroundEquippedById" = COALESCE("backgroundEquippedById", "hostId")
 WHERE "backgroundUrl" IS NOT NULL;

UPDATE rooms
   SET "roomCardEquippedById" = COALESCE("roomCardEquippedById", "hostId")
 WHERE "roomCardUrl" IS NOT NULL;

UPDATE room_access
   SET "expiresAt" = COALESCE("grantedAt", NOW()) + INTERVAL '12 hours'
 WHERE "grantType" = 'session'
   AND "expiresAt" IS NULL;

ALTER TABLE reports
  ADD COLUMN IF NOT EXISTS "roomId" uuid;

DELETE FROM agency_applications application
WHERE application.status = 'approved'
  AND (
    application."agencyId" IS NULL
    OR NOT EXISTS (
      SELECT 1 FROM agencies agency WHERE agency.id = application."agencyId"
    )
  );

UPDATE app_settings
   SET value = (
     value::jsonb ||
     CASE
       WHEN NOT value::jsonb @> '[{"id":"lucky-wheel"}]'::jsonb
       THEN '[{"id":"lucky-wheel","title":"عجلة الحظ","titleEn":"Lucky Wheel","coverUrl":"https://api.adnova.bbs.tr/games/lucky-wheel/seven-77.png","playUrl":"https://api.adnova.bbs.tr/games/lucky-wheel.html","sortOrder":5}]'::jsonb
       ELSE '[]'::jsonb
     END ||
     CASE
       WHEN NOT value::jsonb @> '[{"id":"dice"}]'::jsonb
       THEN '[{"id":"dice","title":"لعبة النرد","titleEn":"Dice","coverUrl":"https://api.adnova.bbs.tr/games/dice/cover.png","playUrl":"https://api.adnova.bbs.tr/games/dice.html","sortOrder":6}]'::jsonb
       ELSE '[]'::jsonb
     END
   )::text
 WHERE key = 'app_games'
   AND jsonb_typeof(value::jsonb) = 'array';

CREATE TABLE IF NOT EXISTS room_games (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "roomId" uuid NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
  type varchar(32) NOT NULL DEFAULT 'tic_tac_toe',
  status varchar(16) NOT NULL DEFAULT 'waiting',
  "playerXId" uuid REFERENCES users(id) ON DELETE SET NULL,
  "playerOId" uuid REFERENCES users(id) ON DELETE SET NULL,
  board text NOT NULL DEFAULT '[0,0,0,0,0,0,0,0,0]',
  turn varchar(1) NOT NULL DEFAULT 'X',
  winner varchar(8),
  "createdAt" timestamptz NOT NULL DEFAULT NOW(),
  "updatedAt" timestamptz NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS "IDX_room_games_roomId" ON room_games("roomId");

CREATE TABLE IF NOT EXISTS casual_matches (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  kind varchar(16) NOT NULL,
  status varchar(16) NOT NULL DEFAULT 'waiting',
  "player1Id" uuid NOT NULL,
  "player2Id" uuid,
  "roomId" uuid,
  turn integer NOT NULL DEFAULT 1,
  state text,
  "stateVersion" integer NOT NULL DEFAULT 0,
  "lastHeartbeatAt" timestamptz,
  winner varchar(8),
  "createdAt" timestamptz NOT NULL DEFAULT NOW(),
  "updatedAt" timestamptz NOT NULL DEFAULT NOW()
);
ALTER TABLE casual_matches
  ADD COLUMN IF NOT EXISTS "stateVersion" integer NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS "lastHeartbeatAt" timestamptz;
CREATE INDEX IF NOT EXISTS "IDX_casual_matches_status" ON casual_matches(status);
CREATE INDEX IF NOT EXISTS "IDX_casual_matches_roomId" ON casual_matches("roomId");

CREATE TABLE IF NOT EXISTS room_seat_signals (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "roomId" uuid NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
  "userId" uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  kind varchar(16) NOT NULL,
  "seatIndex" integer,
  "displayName" varchar(128),
  "createdById" uuid,
  "expiresAt" timestamptz NOT NULL,
  "createdAt" timestamptz NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_room_seat_signal UNIQUE ("roomId", "userId", kind)
);

CREATE TABLE IF NOT EXISTS room_music_tracks (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "uploadedById" uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  url varchar(1024) NOT NULL UNIQUE,
  title varchar(180) NOT NULL,
  artist varchar(180),
  "isActive" boolean NOT NULL DEFAULT true,
  "createdAt" timestamptz NOT NULL DEFAULT NOW(),
  "updatedAt" timestamptz NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_room_music_tracks_active
  ON room_music_tracks ("isActive", "updatedAt" DESC);

CREATE INDEX IF NOT EXISTS idx_room_seat_signals_room_kind
  ON room_seat_signals ("roomId", kind);
CREATE INDEX IF NOT EXISTS idx_room_seat_signals_expiry
  ON room_seat_signals ("expiresAt");

WITH duplicate_seats AS (
  SELECT id,
         ROW_NUMBER() OVER (
           PARTITION BY "roomId", "userId"
           ORDER BY "isHostSeat" DESC, "updatedAt" DESC
         ) AS row_number
  FROM room_seats
  WHERE "userId" IS NOT NULL
)
UPDATE room_seats seat
SET "userId" = NULL, status = 'empty', "isMuted" = false
FROM duplicate_seats duplicate
WHERE seat.id = duplicate.id AND duplicate.row_number > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uq_room_seats_one_user
  ON room_seats ("roomId", "userId")
  WHERE "userId" IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_reports_room
  ON reports ("roomId");

CREATE INDEX IF NOT EXISTS idx_gift_sends_room_sender
  ON gift_sends ("roomId", "senderId")
  WHERE "roomId" IS NOT NULL;

COMMIT;
