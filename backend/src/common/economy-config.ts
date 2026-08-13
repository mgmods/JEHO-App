/**
 * Runtime-editable economy settings — SINGLE CLEAN MODEL.
 *
 * Two currencies only:
 *   - coins   : bought with real money (recharge). Spent on gifts only.
 *   - diamonds: earned by receiving gifts. Withdrawn for USD. ONE pool.
 *
 * Gift flow:
 *   sender pays gift.coinPrice  →  receiver side earns gift.diamondValue
 *   (diamondValue is the per-unit diamond mint; platform margin is the gap
 *    between the coin price paid and the diamond value handed out).
 *
 * Split of the minted diamonds (agency rooms only):
 *   host keeps hostPercent%, agency owner earns agencyOwnerPercent%.
 *   There is NO separate platform cut here — the margin was already taken at
 *   the coin→diamond mint. Personal-room gifts go 100% to the receiver.
 *
 * Everything here is loaded from `app_settings` on boot and mutated live from
 * the dashboard (Admin → Economy Settings). Never hardcode these numbers.
 */

export interface EconomyGiftSplit {
  /** @deprecated Kept for storage compat. Always 0 in the clean model. */
  platformPercent: number;
  /** Host share of a gift's minted diamonds (agency room). */
  hostPercent: number;
  /** Agency owner commission share of a gift's minted diamonds. */
  agencyOwnerPercent: number;
}

export interface EconomyConfigShape {
  /** Recharge density for display/seed: coins granted per $1. */
  coinsPerUsd: number;
  /** Withdraw value of ONE diamond in USD. */
  diamondUsd: number;
  /** Coins → diamonds mint ratio for regular gifts (0 < r ≤ 1). */
  giftDiamondRatio: number;
  /** Coins → diamonds mint ratio for lucky gifts. */
  luckyGiftDiamondRatio: number;
  /** Hard ceiling on diamonds minted per single gift unit. */
  maxDiamondsPerUnit: number;
  /** Lucky-box max multiplier (safety cap on the biggest win). */
  luckyGiftMaxMultiplier: number;
  /** Lucky-box target expected value (used by seeds only). */
  luckyGiftTargetEv: number;
  /** Host / agency split of a gift's minted diamonds (single source of truth). */
  defaultGiftSplit: EconomyGiftSplit;
  /** Single withdraw threshold (diamonds) for both host and agency payouts. */
  minWithdrawDiamonds: number;
  /**
   * When false (default), the gift catalog API zeroes out `diamondValue` so
   * the app shows only the coin price under each gift. Diamonds are still
   * minted normally on send — this is a UI-only mask.
   */
  showDiamondValueInApp: boolean;
}

export const ECONOMY_DEFAULTS: EconomyConfigShape = Object.freeze({
  coinsPerUsd: 10000,
  diamondUsd: 0.00005,
  // Payout to the receiver side = giftDiamondRatio × diamondUsd × coinsPerUsd.
  // 0.8 × 0.00005 × 10000 = 0.40 → host+agency earn 40% of the gift's USD value,
  // the platform keeps 60% (taken at the coin→diamond mint). Fully editable live.
  giftDiamondRatio: 0.8,
  luckyGiftDiamondRatio: 0.3,
  // High enough that a proportional ladder holds up to the priciest gift
  // (top gift ≈ 1,000,000 coins × 0.8 = 800,000 ♦). Lower it from the dashboard
  // if you want a hard cap on the biggest gifts.
  maxDiamondsPerUnit: 2_000_000,
  luckyGiftMaxMultiplier: 4,
  luckyGiftTargetEv: 0.38,
  defaultGiftSplit: Object.freeze({
    platformPercent: 0,
    hostPercent: 70,
    agencyOwnerPercent: 30,
  }),
  minWithdrawDiamonds: 200000,
  // App published to Play Store shows only the coin price — diamonds masked by default.
  showDiamondValueInApp: false,
}) as EconomyConfigShape;

/**
 * Mutable live cache. Read-only by consumers, updated by EconomySettingsService.
 * Never replace the object reference — mutate its fields so all `import { ECONOMY }`
 * sites keep pointing at the same live object.
 */
export const ECONOMY: EconomyConfigShape = {
  coinsPerUsd: ECONOMY_DEFAULTS.coinsPerUsd,
  diamondUsd: ECONOMY_DEFAULTS.diamondUsd,
  giftDiamondRatio: ECONOMY_DEFAULTS.giftDiamondRatio,
  luckyGiftDiamondRatio: ECONOMY_DEFAULTS.luckyGiftDiamondRatio,
  maxDiamondsPerUnit: ECONOMY_DEFAULTS.maxDiamondsPerUnit,
  luckyGiftMaxMultiplier: ECONOMY_DEFAULTS.luckyGiftMaxMultiplier,
  luckyGiftTargetEv: ECONOMY_DEFAULTS.luckyGiftTargetEv,
  defaultGiftSplit: { ...ECONOMY_DEFAULTS.defaultGiftSplit },
  minWithdrawDiamonds: ECONOMY_DEFAULTS.minWithdrawDiamonds,
  showDiamondValueInApp: ECONOMY_DEFAULTS.showDiamondValueInApp,
};

function clamp(n: unknown, min: number, max: number, fallback: number): number {
  const v = Number(n);
  if (!Number.isFinite(v)) return fallback;
  return Math.min(max, Math.max(min, v));
}

/** Apply a partial patch (from the settings service). Silently rejects garbage values. */
export function applyEconomyPatch(patch: Partial<EconomyConfigShape>): EconomyConfigShape {
  if (patch.coinsPerUsd !== undefined) {
    ECONOMY.coinsPerUsd = Math.round(
      clamp(patch.coinsPerUsd, 100, 10_000_000, ECONOMY_DEFAULTS.coinsPerUsd),
    );
  }
  if (patch.diamondUsd !== undefined) {
    ECONOMY.diamondUsd = clamp(patch.diamondUsd, 0.000001, 1, ECONOMY_DEFAULTS.diamondUsd);
  }
  if (patch.giftDiamondRatio !== undefined) {
    ECONOMY.giftDiamondRatio = clamp(patch.giftDiamondRatio, 0.01, 1, ECONOMY_DEFAULTS.giftDiamondRatio);
  }
  if (patch.luckyGiftDiamondRatio !== undefined) {
    ECONOMY.luckyGiftDiamondRatio = clamp(
      patch.luckyGiftDiamondRatio,
      0.01,
      1,
      ECONOMY_DEFAULTS.luckyGiftDiamondRatio,
    );
  }
  if (patch.maxDiamondsPerUnit !== undefined) {
    ECONOMY.maxDiamondsPerUnit = Math.round(
      clamp(patch.maxDiamondsPerUnit, 1, 10_000_000, ECONOMY_DEFAULTS.maxDiamondsPerUnit),
    );
  }
  if (patch.luckyGiftMaxMultiplier !== undefined) {
    ECONOMY.luckyGiftMaxMultiplier = Math.round(
      clamp(patch.luckyGiftMaxMultiplier, 1, 1000, ECONOMY_DEFAULTS.luckyGiftMaxMultiplier),
    );
  }
  if (patch.luckyGiftTargetEv !== undefined) {
    ECONOMY.luckyGiftTargetEv = clamp(patch.luckyGiftTargetEv, 0.01, 1, ECONOMY_DEFAULTS.luckyGiftTargetEv);
  }
  if (patch.minWithdrawDiamonds !== undefined) {
    ECONOMY.minWithdrawDiamonds = Math.round(
      clamp(patch.minWithdrawDiamonds, 1, 1_000_000_000, ECONOMY_DEFAULTS.minWithdrawDiamonds),
    );
  }
  if (patch.showDiamondValueInApp !== undefined) {
    ECONOMY.showDiamondValueInApp = !!patch.showDiamondValueInApp;
  }
  if (patch.defaultGiftSplit) {
    const g = patch.defaultGiftSplit;
    // Clean model: only host + agency owner. Platform share is always 0 here.
    let h = clamp(g.hostPercent, 0, 100, ECONOMY_DEFAULTS.defaultGiftSplit.hostPercent);
    let a = clamp(
      g.agencyOwnerPercent,
      0,
      100,
      ECONOMY_DEFAULTS.defaultGiftSplit.agencyOwnerPercent,
    );
    const sum = h + a;
    if (sum > 0 && Math.abs(sum - 100) > 0.001) {
      h = (h * 100) / sum;
      a = Math.max(0, 100 - h);
    }
    ECONOMY.defaultGiftSplit = {
      platformPercent: 0,
      hostPercent: Number(h.toFixed(2)),
      agencyOwnerPercent: Number(a.toFixed(2)),
    };
  }
  return { ...ECONOMY, defaultGiftSplit: { ...ECONOMY.defaultGiftSplit } };
}
