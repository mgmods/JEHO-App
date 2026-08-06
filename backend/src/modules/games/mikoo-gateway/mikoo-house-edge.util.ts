/**
 * House-favored payout RNG for Mikoo games.
 * Target long-run player RTP ≈ 70–80% (house edge 20–30%).
 * Win table display — high mults rare; hard clamps in game-payout-guard.
 */
import { clampGamePayout, GAME_PAYOUT } from '../game-payout-guard';
import { MIKOO_WIN_MULTS } from './mikoo-game-economy';

/** Global player return target for multi-area tables. */
export const MULTI_AREA_TARGET_RTP = 0.75;

/**
 * Weighted multipliers so EV when hit ≈ 5.9:
 * Hit rate 0.125 → RTP ≈ 0.74 (house ~26%). Peaks stay in display table (≤25).
 */
export function spinPayout(
  betAmount: number,
  opts?: { hitRate?: number },
): { win: number; mult: number } {
  const bet = Math.max(1, Math.floor(betAmount || 0));
  const hitRate = Math.min(0.25, Math.max(0.05, Number(opts?.hitRate) || 0.125));
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
 * Tail hard-capped at GAME_PAYOUT.maxCrashRatio.
 */
export function biasedCrashAt(): number {
  const maxR = GAME_PAYOUT.maxCrashRatio;
  const u = Math.random();
  // Instant / near instant bust (~12%)
  if (u < 0.12) return 1.0;
  // Early crash 1.01–1.45 (~43%)
  if (u < 0.55) return Math.round((1.01 + Math.random() * 0.44) * 100) / 100;
  // Mid 1.5–3.0 (~27%)
  if (u < 0.82) return Math.round((1.5 + Math.random() * 1.5) * 100) / 100;
  // High tail up to maxR (~18%)
  const tail = 3.0 + Math.pow(Math.random(), 1.6) * Math.max(0.5, maxR - 3);
  return Math.min(maxR, Math.round(tail * 100) / 100);
}

/**
 * Multi-area (7updown-style) display mults.
 * Pay under inverse-weight land, not uniform (see pickWeightedAreaIndex).
 */
export function multiAreaMultipliers(): Record<number, number> {
  // Display slightly under "fair" so even if UI guesses dice feel, house stays safe.
  return { 1: 1.9, 2: 4.5, 3: 1.9 };
}

/**
 * Pick area index 0..n-1 with P_i ∝ (targetRtp / mult_i).
 * When every mult gets P_i * mult_i = targetRtp (normalized), RTP ≈ target for single-icon play.
 */
export function pickWeightedAreaIndex(
  multipliers: readonly number[],
  targetRtp: number = MULTI_AREA_TARGET_RTP,
): number {
  const m = multipliers.map((x) => Math.max(1.01, Number(x) || 1));
  if (!m.length) return 0;
  const tau = Math.min(0.9, Math.max(0.5, Number(targetRtp) || MULTI_AREA_TARGET_RTP));
  const weights = m.map((mult) => tau / mult);
  const sum = weights.reduce((a, b) => a + b, 0);
  let r = Math.random() * sum;
  for (let i = 0; i < weights.length; i++) {
    r -= weights[i]!;
    if (r <= 0) return i;
  }
  return m.length - 1;
}

/** 1-based area id for multi boards that use areas 1..N. */
export function pickWeightedAreaId(
  multipliers: readonly number[],
  targetRtp: number = MULTI_AREA_TARGET_RTP,
): number {
  return pickWeightedAreaIndex(multipliers, targetRtp) + 1;
}

/** Cashout rake on crash (extra house on top of bust distribution). */
export const CRASH_CASHOUT_RAKE = 0.97;
