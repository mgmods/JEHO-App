/**
 * Canonical social-app pricing (USD Play product IDs = sku).
 * Profitable launch: modest bonuses, host diamond share ~55%, cashout rate below coin buy value.
 */
export const PRICING_VERSION = '20260730mall-paid-v1';

/** Google Play / store productId == sku. */
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
    coins: 35000,
    bonusCoins: 0,
    priceUsd: 2.99,
    label: '35,000',
    popular: false,
  },
  {
    id: '3',
    sku: 'coins_70000',
    coins: 70000,
    bonusCoins: 3000,
    priceUsd: 4.99,
    label: '70,000',
    popular: false,
  },
  {
    id: '4',
    sku: 'coins_141700',
    coins: 141700,
    bonusCoins: 8000,
    priceUsd: 9.99,
    label: '141,700',
    popular: true,
  },
  {
    id: '5',
    sku: 'coins_300000',
    coins: 300000,
    bonusCoins: 25000,
    priceUsd: 19.99,
    label: '300,000',
    popular: false,
  },
  {
    id: '6',
    sku: 'coins_750000',
    coins: 750000,
    bonusCoins: 75000,
    priceUsd: 49.99,
    label: '750,000',
    popular: false,
  },
  {
    id: '7',
    sku: 'coins_2000000',
    coins: 2000000,
    bonusCoins: 250000,
    priceUsd: 99.99,
    label: '2,000,000',
    popular: false,
  },
] as const;

/** Host diamond share of gift coins (rest is platform margin before cashout rate). */
export const GIFT_DIAMOND_RATIO = 0.5;
/** Lucky gifts pay less diamonds — rebate already returns coins to sender. */
export const LUCKY_GIFT_DIAMOND_RATIO = 0.25;
/**
 * Hard ceiling on lucky gift RNG multipliers (platform safety).
 * Soft returns (mul &lt; 1) are allowed for frequent «مردود» feel while EV stays &lt; 1.
 */
export const LUCKY_GIFT_MAX_MULTIPLIER = 8;
/** Target EV of coin rebate vs stake (house keeps the rest + diamond cut). */
export const LUCKY_GIFT_TARGET_EV = 0.52;

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
