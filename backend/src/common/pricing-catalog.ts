/**
 * Canonical social-app pricing (USD Play product IDs = sku).
 *
 * ═══════════════════════════════════════════════════════════════════
 * GIFT-FIRST ECONOMY (not “1 diamond = N coins”)
 * ═══════════════════════════════════════════════════════════════════
 * • Users buy COINS (recharge). They spend coins on GIFTS only.
 * • Each gift row defines: coinPrice (cost) + diamondValue (host pool).
 * • diamondValue is the diamond mint for that gift (per unit), capped so
 *   the platform never mints more than the house ratio of coins spent.
 * • Agency split / host salary targets / withdraw all use DIAMONDS from gifts.
 * • There is no product surface of “convert diamonds ↔ coins at a fixed FX”.
 *   Any leftover diamondCoinRate is withdraw-exotics only, not gift pricing.
 *
 * Owner-safe density: ≈ 10,000–11,000 coins per $1.
 * Break-even under gifts (0.35 mint × 60% cashable × $0.00005/d):
 *   liability ≈ $0.0000105 per coin → packages stay profitable.
 */
export const PRICING_VERSION = '20260808economy-v6-giftfirst';

/** Google Play productId == sku. Total coins = coins + bonusCoins. */
export const STANDARD_RECHARGE_PACKAGES = [
  {
    id: '1',
    sku: 'coins_10000',
    coins: 10000,
    bonusCoins: 0,
    priceUsd: 0.99,
    label: '10,000',
    popular: false,
  },
  {
    id: '2',
    sku: 'coins_35000',
    coins: 28000,
    bonusCoins: 2000,
    priceUsd: 2.99,
    label: '30,000',
    popular: false,
  },
  {
    id: '3',
    sku: 'coins_70000',
    coins: 45000,
    bonusCoins: 5000,
    priceUsd: 4.99,
    label: '50,000',
    popular: true,
  },
  {
    id: '4',
    sku: 'coins_141700',
    coins: 90000,
    bonusCoins: 10000,
    priceUsd: 9.99,
    label: '100,000',
    popular: false,
  },
  {
    id: '5',
    sku: 'coins_300000',
    coins: 180000,
    bonusCoins: 20000,
    priceUsd: 19.99,
    label: '200,000',
    popular: false,
  },
  {
    id: '6',
    sku: 'coins_750000',
    coins: 450000,
    bonusCoins: 50000,
    priceUsd: 49.99,
    label: '500,000',
    popular: false,
  },
  {
    id: '7',
    sku: 'coins_2000000',
    coins: 900000,
    bonusCoins: 100000,
    priceUsd: 99.99,
    label: '1,000,000',
    popular: false,
  },
] as const;

import { ECONOMY } from './economy-config';

/**
 * ══════════════════════════════════════════════════════════════════════
 * ECONOMY CONSTANTS ARE DASHBOARD-OWNED — DO NOT HARDCODE
 * ══════════════════════════════════════════════════════════════════════
 * The exports below are backed by `ECONOMY` (backend/src/common/economy-config.ts),
 * which is loaded from `app_settings` on boot and can be changed live from
 * the Admin → Economy Settings page. The literals here are only the "day 0"
 * fallbacks used when the DB row is missing.
 *
 * If you need the current live value at call time, prefer reading `ECONOMY.*`
 * directly. The ES named exports below are Object.defineProperty getters and
 * always return the current value.
 */

/** Live getter helpers — always return the current value from the mutable ECONOMY cache. */
export function giftDiamondRatio(): number { return ECONOMY.giftDiamondRatio; }
export function luckyGiftDiamondRatio(): number { return ECONOMY.luckyGiftDiamondRatio; }
export function giftMaxDiamondsPerUnit(): number { return ECONOMY.maxDiamondsPerUnit; }
export function luckyGiftMaxMultiplier(): number { return ECONOMY.luckyGiftMaxMultiplier; }
export function defaultGiftSplit() { return { ...ECONOMY.defaultGiftSplit }; }

/** Constant kept as-is (seed-only, not a live economy lever). */
export const LUCKY_GIFT_TARGET_EV = ECONOMY.luckyGiftTargetEv;

/**
 * Recommended catalog diamondValue for a gift (explicit, not a FX rate).
 * Send path still safety-caps with mintDiamondsPerUnit.
 */
export function recommendedGiftDiamonds(
  coinPrice: number,
  lucky = false,
): number {
  return mintDiamondsPerUnit(
    coinPrice,
    0,
    lucky ? ECONOMY.luckyGiftDiamondRatio : ECONOMY.giftDiamondRatio,
  );
}

/**
 * Diamonds minted per single gift unit (before split).
 * Source of truth is the gift: catalog diamondValue when set.
 * Hard ceilings: house ratio of coins, and the live absolute cap from ECONOMY.
 * If catalog is 0/missing, fall back to house ratio so paid gifts never mint 0.
 */
export function mintDiamondsPerUnit(
  coinPrice: number,
  catalogDiamondValue: number | null | undefined,
  ratio?: number,
): number {
  const price = Math.max(0, Math.floor(Number(coinPrice) || 0));
  if (price <= 0) return 0;
  const r = ratio !== undefined ? Number(ratio) : ECONOMY.giftDiamondRatio;
  const safeRatio =
    Number.isFinite(r) && r > 0 ? Math.min(1, Math.max(0, r)) : ECONOMY.giftDiamondRatio;
  const fromCoins = Math.floor(price * safeRatio);
  const catalog = Math.max(0, Math.floor(Number(catalogDiamondValue) || 0));
  const raw = catalog <= 0 ? fromCoins : Math.min(catalog, fromCoins);
  const cap = Math.max(1, Math.floor(ECONOMY.maxDiamondsPerUnit));
  return Math.min(cap, Math.max(0, raw));
}

/** Soft currency mall ladder — sinks coins (good for platform). Nothing free in mall. */
export const MALL_COSMETIC_PRICES: Record<string, number[]> = {
  host_badge: [299, 499, 799, 1199, 1599, 1999, 2499, 2999, 3499, 3999],
  vip_badge: [499, 799, 999, 1299, 1599, 1999, 2499, 2999, 3499, 3999],
  level_badge: [199, 299, 399, 499, 699, 899, 1199, 1499, 1999, 2499],
  entry_effect: [399, 699, 999, 1299, 1699, 1999, 2499, 2999, 3499, 3999],
  join_toast: [299, 499, 799, 999, 1299, 1599, 1999, 2499, 2999, 3499],
  room_card: [149, 199, 299, 399, 599, 899, 1199, 1499, 1999, 2499],
  room_background: [99, 149, 199, 249, 299, 399, 499, 599, 799, 999, 1299, 1499, 1799, 1999],
};

/**
 * Agency-host daily engagement with NEW male accounts.
 * Chat rounds: 5–10 messages both-ways within 3 minutes (renews every 24h per pair).
 * Room invite: guest stays up to 2 minutes → hostess earns 40 diamonds (agency hosts only).
 */
export const HOST_NEW_USER_CHAT = {
  minMessages: 5,
  maxMessages: 10,
  /** Must finish the message round within this window or chance ends until next day. */
  maxWindowSeconds: 180,
  /** Males whose account age is within this many days are eligible. */
  newUserDays: 30,
  /** First calendar day badge ("اليوم الأول" / Hi). */
  firstDayHours: 24,
} as const;

export const HOST_ROOM_INVITE_REWARD = {
  /** Auto diamonds when invited new male stays ~2 minutes in host room. */
  diamonds: 15,
  rewardPoints: 0,
  rewardSilver: 0,
  /** Guest must be present this many seconds (capped at 2 minutes). */
  dwellSeconds: 120,
  /** Safety cap: max auto diamond grants per hostess per UTC day. */
  maxRewardsPerHostPerDay: 5,
} as const;

/** Agency opening fee (coins). Runtime override via app_settings.agency_create_price_coins. */
export const AGENCY_CREATE = {
  settingKey: 'agency_create_price_coins',
  /** Explicit free mode (true/false). Zero price alone does not grant free open unless this is true. */
  freeSettingKey: 'agency_create_free',
  defaultCoins: 50_000,
  minCoins: 1_000,
  maxCoins: 5_000_000,
} as const;

/** Host↔host diamond swap: received amount lands in traderDiamonds (not withdrawable cashout pool). */
export const HOST_DIAMOND_TRADE = {
  minAmount: 10,
  maxAmount: 50_000,
} as const;

/** Wealth / popularity display cap (major apps show levels, not raw millions). */
export const MAX_ECONOMY_LEVEL = 600;

/** Wealth (support sent) / popularity (support received) level ladders. */
export const WEALTH_POPULARITY_THRESHOLDS = [
  0, 100, 500, 2_000, 5_000, 15_000, 50_000, 150_000, 500_000, 1_500_000, 5_000_000,
] as const;

/** Map lifetime coins/diamonds to a 1–600 display level (slow growth, hard cap). */
export function levelFromScore(score: number): number {
  const s = Math.max(0, Number(score) || 0);
  if (s <= 0) return 1;
  const raw = Math.floor(Math.cbrt(s / 10));
  return Math.min(MAX_ECONOMY_LEVEL, Math.max(1, raw));
}
