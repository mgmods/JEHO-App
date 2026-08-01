CREATE TABLE IF NOT EXISTS slot_game_sessions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  "userId" UUID NOT NULL,
  "gameId" VARCHAR(64) NOT NULL,
  "roomId" UUID,
  code VARCHAR(128) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'active',
  "totalBetCoins" INT NOT NULL DEFAULT 0,
  "totalWinCoins" INT NOT NULL DEFAULT 0,
  "spinCount" INT NOT NULL DEFAULT 0,
  "lastHeartbeatAt" TIMESTAMPTZ,
  "endedAt" TIMESTAMPTZ,
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_slot_sessions_user ON slot_game_sessions ("userId");
CREATE INDEX IF NOT EXISTS idx_slot_sessions_game ON slot_game_sessions ("gameId");
CREATE INDEX IF NOT EXISTS idx_slot_sessions_room ON slot_game_sessions ("roomId");
CREATE INDEX IF NOT EXISTS idx_slot_sessions_active ON slot_game_sessions ("roomId", status, "lastHeartbeatAt");
