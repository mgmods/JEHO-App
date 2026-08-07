/** Clamp a percent share (0–100). */
export function clampSharePct(value: number, fallback = 0): number {
  const n = Number(value);
  if (!Number.isFinite(n)) return fallback;
  return Math.min(100, Math.max(0, n));
}

/**
 * Agency-room gift diamond partition (after coin→diamond mint).
 *
 * Owner-safe rules:
 * 1) Host + agency owner + platform are shares of the **minted diamonds**, not of coins.
 * 2) Percentages are always normalized so they sum to 100% (no “independent” leak).
 * 3) Any leftover floor diamonds go to the **platform** (never unassigned, never extra liability).
 *
 * Recommended owner-safe preset: host 45 / agency 15 / platform 40.
 */
export function independentAgencyGiftSplit(
  gross: number,
  hostPct: number,
  ownerPct: number,
  platformPct: number,
  options?: { ownerIsReceiver?: boolean },
) {
  const g = Math.max(0, Math.floor(Number(gross) || 0));
  if (g <= 0) {
    return { hostDiamonds: 0, agentShare: 0, platformCut: 0 };
  }

  let h = clampSharePct(hostPct, 45);
  let o = options?.ownerIsReceiver ? 0 : clampSharePct(ownerPct, 15);
  let p = clampSharePct(platformPct, 40);

  const sum = h + o + p;
  if (sum <= 0) {
    return { hostDiamonds: 0, agentShare: 0, platformCut: g };
  }
  if (Math.abs(sum - 100) > 0.0001) {
    h = (h * 100) / sum;
    o = (o * 100) / sum;
    p = Math.max(0, 100 - h - o);
  }

  let hostDiamonds = Math.floor((g * h) / 100);
  let agentShare = Math.floor((g * o) / 100);
  if (hostDiamonds + agentShare > g) {
    const s = hostDiamonds + agentShare;
    hostDiamonds = Math.floor((hostDiamonds * g) / s);
    agentShare = Math.floor((agentShare * g) / s);
  }
  // Platform always takes the full residual (floor remainder + its share).
  const platformCut = Math.max(0, g - hostDiamonds - agentShare);
  return { hostDiamonds, agentShare, platformCut };
}
