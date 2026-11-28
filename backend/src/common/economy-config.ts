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
 * Independent gift earnings:
 *   host receives 100% of the configured gift diamond value.
 *   agency owner commission is an additional percentage of that value.
 *   platform accounting is separate and must never be deducted from either payout.
 *
 * Everything here is loaded from `app_settings` on boot and mutated live from
 * the dashboard (Admin → Economy Settings). Never hardcode these numbers.
 */

export interface EconomyGiftSplit {
  /** @deprecated Kept for storage compat. Always 0 in the clean model. */
  platformPercent: number;
  /** Compatibility/display field; host always receives 100% of minted gift diamonds. */
  hostPercent: number;
  /** Agency owner commission percentage, credited in addition to the host's full payout. */
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
  /** Private, additive platform accounting rate; never deducted from host/agency. */
  platformCommissionPercent: number;
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
  // Default: every coin of a regular gift mints one host diamond; dashboard-editable.
  // Agency commission is additional and never subtracted from this host payout.
  giftDiamondRatio: 1,
  luckyGiftDiamondRatio: 0.3,
  platformCommissionPercent: 0,
  // High enough that a proportional ladder holds up to the priciest gift
  // (top gift ≈ 1,000,000 coins × 0.8 = 800,000 ♦). Lower it from the dashboard
  // if you want a hard cap on the biggest gifts.
  maxDiamondsPerUnit: 2_000_000,
  luckyGiftMaxMultiplier: 4,
  luckyGiftTargetEv: 0.38,
  defaultGiftSplit: Object.freeze({
    platformPercent: 0,
    hostPercent: 100,
    agencyOwnerPercent: 20,
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
  platformCommissionPercent: ECONOMY_DEFAULTS.platformCommissionPercent,
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
  // Regular gifts always pay their full coin face value in diamonds.
  // Keep the legacy setting field for compatibility, but never let dashboard
  // payloads lower the regular-gift payout ratio.
  ECONOMY.giftDiamondRatio = 1;
  if (patch.luckyGiftDiamondRatio !== undefined) {
    ECONOMY.luckyGiftDiamondRatio = clamp(
      patch.luckyGiftDiamondRatio,
      0.01,
      1,
      ECONOMY_DEFAULTS.luckyGiftDiamondRatio,
    );
  }
  if (patch.platformCommissionPercent !== undefined) {
    ECONOMY.platformCommissionPercent = clamp(
      patch.platformCommissionPercent,
      0,
      100,
      ECONOMY_DEFAULTS.platformCommissionPercent,
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
    // Independent settings: host payout is always 100%; agency is an extra bonus.
    // Do not normalize the two percentages to 100% — that would silently cut host earnings.
    ECONOMY.defaultGiftSplit = {
      platformPercent: 0,
      hostPercent: 100,
      agencyOwnerPercent: Number(
        clamp(
          g.agencyOwnerPercent,
          0,
          100,
          ECONOMY_DEFAULTS.defaultGiftSplit.agencyOwnerPercent,
        ).toFixed(2),
      ),
    };
  }
  return { ...ECONOMY, defaultGiftSplit: { ...ECONOMY.defaultGiftSplit } };
}
