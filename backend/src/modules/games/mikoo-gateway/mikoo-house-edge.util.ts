/**
 * House-favored payout RNG for Mikoo games.
 * Live knobs come from dashboard → game.house_odds (see game-odds-runtime).
 */
import { clampGamePayout, gamePayoutLimits } from '../game-payout-guard';
import { getGameOdds } from '../game-odds-runtime';
import { MIKOO_WIN_MULTS } from './mikoo-game-economy';

/** @deprecated use getGameOdds().playerRtp — kept for import compatibility */
export function MULTI_AREA_TARGET_RTP(): number {
  return getGameOdds().playerRtp;
}

/**
 * Weighted multipliers so EV when hit ≈ 5.9;
 * hitRate from dashboard → RTP ≈ hitRate * 5.9.
 */
export function spinPayout(
  betAmount: number,
  opts?: { hitRate?: number },
): { win: number; mult: number } {
  const bet = Math.max(1, Math.floor(betAmount || 0));
  const odds = getGameOdds();
  const hitRate = Math.min(
    0.25,
    Math.max(0.05, Number(opts?.hitRate) || odds.spinHitRate || 0.115),
  );
  if (Math.random() >= hitRate) {
    return { win: 0, mult: 0 };
  }
  const roll = Math.random();
  let mult: number;
  if (roll < 0.42) mult = 2;
  else if (roll < 0.68) mult = 3;
  else if (roll < 0.83) mult = 4;
  else if (roll < 0.92) mult = 10;
  else if (roll < 0.96) mult = 15;
  else if (roll < 0.985) mult = 20;
  else mult = 25;
  if (!(MIKOO_WIN_MULTS as readonly number[]).includes(mult)) mult = 2;
  const { win } = clampGamePayout({ bet, win: Math.floor(bet * mult) });
  const effectiveMult = bet > 0 && win > 0 ? win / bet : 0;
  return { win, mult: effectiveMult > 0 ? Math.round(effectiveMult * 100) / 100 : 0 };
}

/**
 * Crash bust point — early hard bust common so cashout@1.2–1.5 is not player+.
 * Tail hard-capped at live maxCrashRatio.
 */
export function biasedCrashAt(): number {
  const maxR = gamePayoutLimits().maxCrashRatio;
  const u = Math.random();
  if (u < 0.12) return 1.0;
  if (u < 0.55) return Math.round((1.01 + Math.random() * 0.44) * 100) / 100;
  if (u < 0.82) return Math.round((1.5 + Math.random() * 1.5) * 100) / 100;
  const tail = 3.0 + Math.pow(Math.random(), 1.6) * Math.max(0.5, maxR - 3);
  return Math.min(maxR, Math.round(tail * 100) / 100);
}

export function multiAreaMultipliers(): Record<number, number> {
  return { 1: 1.9, 2: 4.5, 3: 1.9 };
}

export function pickWeightedAreaIndex(
  multipliers: readonly number[],
  targetRtp?: number,
): number {
  const m = multipliers.map((x) => Math.max(1.01, Number(x) || 1));
  if (!m.length) return 0;
  const tau = Math.min(
    0.9,
    Math.max(0.5, Number(targetRtp) || getGameOdds().playerRtp),
  );
  const weights = m.map((mult) => tau / mult);
  const sum = weights.reduce((a, b) => a + b, 0);
  let r = Math.random() * sum;
  for (let i = 0; i < weights.length; i++) {
    r -= weights[i]!;
    if (r <= 0) return i;
  }
  return m.length - 1;
}

export function pickWeightedAreaId(
  multipliers: readonly number[],
  targetRtp?: number,
): number {
  return pickWeightedAreaIndex(multipliers, targetRtp) + 1;
}

/** Cashout rake on crash — live from dashboard. */
export function crashCashoutRake(): number {
  return getGameOdds().crashCashoutRake;
}

/** @deprecated use crashCashoutRake() */
export const CRASH_CASHOUT_RAKE = 0.97;
