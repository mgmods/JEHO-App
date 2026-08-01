/**
 * VIP is purchase-only (paid membership in user_vips).
 * User level / wealth / charm / gift spend never grant VIP.
 */
export const MAX_VIP_LEVEL = 100;

/** @deprecated Support spend no longer grants VIP. Kept for callers; always returns 0. */
export function vipLevelFromSupport(_totalSentCoins: number): number {
  return 0;
}

/** Display VIP = active paid/admin membership only. */
export function effectiveVipLevel(activeVipLevel: number, _totalSentCoins?: number): number {
  return Math.max(0, Math.floor(Number(activeVipLevel) || 0));
}
