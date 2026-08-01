-- Full economy + cosmetics wipe (all users). Accounts/chats/follows kept.
-- Run: node scripts/reset-beta-economy.js --confirm

BEGIN;

-- ── History / ledger ─────────────────────────────────────────────────────────
DELETE FROM wallet_transactions;
DELETE FROM gift_sends;
DELETE FROM recharge_orders;
DELETE FROM withdraw_requests;
DELETE FROM payment_webhook_events;
DELETE FROM agent_recharges;
DELETE FROM recharge_agent_ledger;
DELETE FROM lucky_reward_grants;
DELETE FROM lucky_box_opens;
DELETE FROM slot_game_sessions;
DELETE FROM user_game_items;
DELETE FROM contest_entries;
DELETE FROM ranking_snapshots;
DELETE FROM room_access;
DELETE FROM user_cosmetics;
DELETE FROM user_vips;
DELETE FROM host_monthly_progress;
DELETE FROM room_games;
DELETE FROM abuse_logs;

DO $$ BEGIN
  IF to_regclass('public.fruit_wheel_bets') IS NOT NULL THEN
    DELETE FROM fruit_wheel_bets;
  END IF;
  IF to_regclass('public.fruit_wheel_rounds') IS NOT NULL THEN
    DELETE FROM fruit_wheel_rounds;
  END IF;
  IF to_regclass('public.drama_reactions') IS NOT NULL THEN
    DELETE FROM drama_reactions;
  END IF;
END $$;

-- Ephemeral progress keys
DELETE FROM app_settings
WHERE key LIKE 'task_progress\_%' ESCAPE '\'
   OR key LIKE 'task_claims\_%' ESCAPE '\'
   OR key LIKE 'lucky_wheel.today.%'
   OR key LIKE 'dice.today.%'
   OR key LIKE 'host_room_invite_%'
   OR key LIKE 'drama.view.%'
   OR key LIKE 'drama.claim.%';

-- Freeze invite farming until manually re-enabled
INSERT INTO app_settings (id, key, value, "createdAt", "updatedAt")
VALUES (
  gen_random_uuid(),
  'invite.rewards',
  '{"inviteeCoins":0,"inviterCoins":0,"enabled":false}',
  NOW(),
  NOW()
)
ON CONFLICT (key) DO UPDATE
SET value = EXCLUDED.value, "updatedAt" = NOW();

-- Disable drama free-coin farm
INSERT INTO app_settings (id, key, value, "createdAt", "updatedAt")
VALUES (
  gen_random_uuid(),
  'drama.rewards',
  '{"enabled":false,"coinsPerEpisode":0,"watchThreshold":0.8}',
  NOW(),
  NOW()
)
ON CONFLICT (key) DO UPDATE
SET value = EXCLUDED.value, "updatedAt" = NOW();

-- ── Wallets ──────────────────────────────────────────────────────────────────
UPDATE wallets SET
  coins = 0,
  diamonds = 0,
  "traderDiamonds" = 0,
  "silverCoins" = 0,
  "gamePoints" = 0,
  "totalRecharged" = 0,
  "totalWithdrawn" = 0,
  "updatedAt" = NOW();

-- ── Profiles: strip every wearable / frame / badge ───────────────────────────
UPDATE user_profiles SET
  "entryEffectUrl" = NULL,
  "entryAnimationUrl" = NULL,
  "roomCardUrl" = NULL,
  "vipBadgeUrl" = NULL,
  "levelBadgeUrl" = NULL,
  "hostBadgeUrl" = NULL,
  "nameColor" = NULL,
  "dmRequiredGiftId" = NULL,
  "dmGiftGateEnabled" = FALSE,
  "totalReceivedDiamonds" = 0,
  "totalSentCoins" = 0,
  "updatedAt" = NOW();

-- ── Users ────────────────────────────────────────────────────────────────────
UPDATE users SET
  level = 1,
  experience = 0,
  "updatedAt" = NOW();

-- ── Agencies & agents ────────────────────────────────────────────────────────
UPDATE agencies SET
  "totalDiamonds" = 0,
  "updatedAt" = NOW();

UPDATE agency_members SET
  "diamondsContributed" = 0,
  "updatedAt" = NOW();

UPDATE recharge_agents SET
  "floatCoins" = 0,
  "dailySoldCoins" = 0,
  "dailySoldKey" = NULL,
  "updatedAt" = NOW();

-- ── Rooms ────────────────────────────────────────────────────────────────────
UPDATE rooms SET
  "entryFeeCoins" = 0,
  "accessMode" = 'free',
  "roomCardUrl" = NULL,
  "roomCardEquippedById" = NULL,
  "backgroundUrl" = NULL,
  "backgroundEquippedById" = NULL,
  "liveSessionStartedAt" = NULL,
  "updatedAt" = NOW();

COMMIT;
