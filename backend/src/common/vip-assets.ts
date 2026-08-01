/** Shared VIP medal asset paths — Mikoo VIP1–7 only (no invented VIP100 art). */
export function vipMedalTier(level: number): number {
  return Math.min(7, Math.max(1, Math.floor(Number(level) || 1)));
}

/** Canonical Mikoo VIP sword-medal URL used by dashboard + app. */
export function vipMedalUrl(level: number, cacheTag = '20260801vip7'): string {
  return `/assets/cosmetics/vip/vip_medal_mikoo_${vipMedalTier(level)}.png?v=${cacheTag}`;
}
