/** Clamp a percent share independently (0–100). Shares do not need to sum to 100. */
export function clampSharePct(value: number, fallback = 0): number {
  const n = Number(value);
  if (!Number.isFinite(n)) return fallback;
  return Math.min(100, Math.max(0, n));
}

/**
 * Independent gift split: each party gets floor(gross × their%).
 * If the configured percentages sum over 100%, scale down proportionally.
 */
export function independentAgencyGiftSplit(
  gross: number,
  hostPct: number,
  ownerPct: number,
  platformPct: number,
  options?: { ownerIsReceiver?: boolean },
) {
  const g = Math.max(0, Math.floor(Number(gross) || 0));
  const h = clampSharePct(hostPct);
  const o = options?.ownerIsReceiver ? 0 : clampSharePct(ownerPct);
  const p = clampSharePct(platformPct);
  let hostDiamonds = Math.floor((g * h) / 100);
  let agentShare = Math.floor((g * o) / 100);
  let platformCut = Math.floor((g * p) / 100);
  const sum = hostDiamonds + agentShare + platformCut;
  if (sum > g && sum > 0) {
    hostDiamonds = Math.floor((hostDiamonds * g) / sum);
    agentShare = Math.floor((agentShare * g) / sum);
    platformCut = Math.max(0, g - hostDiamonds - agentShare);
  }
  return { hostDiamonds, agentShare, platformCut };
}
