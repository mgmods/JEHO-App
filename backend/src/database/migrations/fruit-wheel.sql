-- Fruit Wheel tables (run if DB_SYNCHRONIZE=false)
CREATE TABLE IF NOT EXISTS fruit_wheel_rounds (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "roomId" uuid NOT NULL,
  status varchar(16) NOT NULL DEFAULT 'betting',
  "bettingEndsAt" timestamptz NOT NULL,
  "resultSector" varchar(16) NULL,
  "resultIndex" int NULL,
  "totalBets" bigint NOT NULL DEFAULT 0,
  "totalPayout" bigint NOT NULL DEFAULT 0,
  "createdAt" timestamptz NOT NULL DEFAULT now(),
  "updatedAt" timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_fruit_wheel_rounds_room ON fruit_wheel_rounds ("roomId");

CREATE TABLE IF NOT EXISTS fruit_wheel_bets (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "roundId" uuid NOT NULL,
  "roomId" uuid NOT NULL,
  "userId" uuid NOT NULL,
  sector varchar(16) NOT NULL,
  amount bigint NOT NULL DEFAULT 0,
  payout bigint NOT NULL DEFAULT 0,
  "createdAt" timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_fruit_wheel_bet_round_user_sector UNIQUE ("roundId", "userId", sector)
);
CREATE INDEX IF NOT EXISTS idx_fruit_wheel_bets_round ON fruit_wheel_bets ("roundId");
CREATE INDEX IF NOT EXISTS idx_fruit_wheel_bets_room ON fruit_wheel_bets ("roomId");
CREATE INDEX IF NOT EXISTS idx_fruit_wheel_bets_user ON fruit_wheel_bets ("userId");

INSERT INTO app_settings (id, key, value, "createdAt", "updatedAt")
SELECT gen_random_uuid(), v.key, v.value, now(), now()
FROM (VALUES
  ('games.fruit_wheel.enabled', 'true'),
  ('games.fruit_wheel.bet_seconds', '20'),
  ('games.fruit_wheel.min_bet', '100'),
  ('games.fruit_wheel.max_bet', '100000')
) AS v(key, value)
WHERE NOT EXISTS (SELECT 1 FROM app_settings s WHERE s.key = v.key);
