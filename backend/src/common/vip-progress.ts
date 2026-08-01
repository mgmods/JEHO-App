/** VIP progression derived from support spending (total sent coins). */
export const MAX_VIP_LEVEL = 100;

/**
 * Hard supporter ladder:
 * - Early VIPs are reachable.
 * - High tiers become dramatically harder.
 * - VIP100 remains extremely rare.
 */
export function vipLevelFromSupport(totalSentCoins: number): number {
  const coins = Math.max(0, Math.floor(Number(totalSentCoins) || 0));
  if (coins < 200) return 0;
  if (coins < 2_500) return Math.min(10, Math.max(1, Math.floor(Math.sqrt(coins / 200))));
  if (coins < 20_000) return Math.min(25, 10 + Math.floor(Math.pow((coins - 2_500) / 2_000, 0.82)));
  if (coins < 120_000) return Math.min(45, 25 + Math.floor(Math.pow((coins - 20_000) / 7_000, 0.86)));
  if (coins < 600_000) return Math.min(70, 45 + Math.floor(Math.pow((coins - 120_000) / 20_000, 0.9)));
  if (coins < 2_500_000) return Math.min(90, 70 + Math.floor(Math.pow((coins - 600_000) / 70_000, 0.95)));
  return Math.min(MAX_VIP_LEVEL, 90 + Math.floor(Math.pow((coins - 2_500_000) / 250_000, 0.72)));
}

export function effectiveVipLevel(activeVipLevel: number, totalSentCoins: number): number {
  return Math.max(
    Math.max(0, Math.floor(Number(activeVipLevel) || 0)),
    vipLevelFromSupport(totalSentCoins),
  );
}
