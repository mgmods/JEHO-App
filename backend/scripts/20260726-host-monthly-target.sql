-- Monthly host diamond target stages + claim ledger.
CREATE TABLE IF NOT EXISTS host_target_claims (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  "userId" UUID NOT NULL,
  "yearMonth" VARCHAR(7) NOT NULL,
  "stageIndex" INT NOT NULL,
  "rewardCoins" BIGINT NOT NULL DEFAULT 0,
  "rewardDiamonds" BIGINT NOT NULL DEFAULT 0,
  "claimedAt" TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_host_target_claim
  ON host_target_claims ("userId", "yearMonth", "stageIndex");

CREATE INDEX IF NOT EXISTS idx_host_target_claims_user
  ON host_target_claims ("userId");

INSERT INTO app_settings (id, key, value, description, "createdAt", "updatedAt")
SELECT gen_random_uuid(),
       'host.monthly_target_stages',
       '[{"diamonds":100000,"rewardCoins":5000,"rewardDiamonds":0,"label":"المرحلة 1"},{"diamonds":500000,"rewardCoins":25000,"rewardDiamonds":0,"label":"المرحلة 2"},{"diamonds":1000000,"rewardCoins":60000,"rewardDiamonds":0,"label":"المرحلة 3"},{"diamonds":5000000,"rewardCoins":350000,"rewardDiamonds":0,"label":"المرحلة 4"}]',
       'Monthly host diamond target stages (cumulative rewards)',
       NOW(),
       NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM app_settings WHERE key = 'host.monthly_target_stages'
);
