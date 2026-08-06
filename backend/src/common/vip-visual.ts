/**
 * Fixed Mikoo-style VIP art tiers (visual 1–7).
 * Not mall frames — paths only used for paid VIP membership wear.
 */

export const VIP_VISUAL_MAX = 7;
export const VIP_ASSET_VER = '20260806vipfix1';

/** Map membership level → art tier (VIP 8–100 still wear VIP7 art). */
export function vipVisualTier(vipLevel: number): number {
  const n = Math.floor(Number(vipLevel) || 0);
  if (n <= 0) return 0;
  return Math.min(VIP_VISUAL_MAX, Math.max(1, n));
}

export function fixedVipFrameUrl(vipLevel: number): string | null {
  const t = vipVisualTier(vipLevel);
  if (t <= 0) return null;
  return `/assets/cosmetics/vip/ud_vip_tou_${t}.webp?v=${VIP_ASSET_VER}`;
}

export function fixedVipHeadUrl(vipLevel: number): string | null {
  const t = vipVisualTier(vipLevel);
  if (t <= 0) return null;
  return `/assets/cosmetics/vip/ic_head_vip_${t}.webp?v=${VIP_ASSET_VER}`;
}

export function fixedVipMedalUrl(vipLevel: number): string | null {
  const t = vipVisualTier(vipLevel);
  if (t <= 0) return null;
  return `/assets/cosmetics/vip/vip_medal_mikoo_${t}.png?v=${VIP_ASSET_VER}`;
}

/**
 * Static VIP badge art (ud_vip_tou / vip_tou_fixed) is NOT mall/SVGA wear.
 * Mikoo keeps vip_tou separate from avatar_wear — do not equip over head frames.
 */
export function isStaticVipTouUrl(url?: string | null): boolean {
  if (!url) return false;
  const u = String(url);
  return (
    u.includes('ud_vip_tou_') ||
    u.includes('vip_tou_fixed_') ||
    u.includes('/assets/cosmetics/vip/ud_vip_tou')
  );
}
