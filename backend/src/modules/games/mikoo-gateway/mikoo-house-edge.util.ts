/**
 * House-favored payout RNG for Mikoo games.
 * Target long-run RTP ≈ 78–82%.
 * Win table: x2 x3 x4 x10 x20 x25 x50 (product request) — high mults are rare.
 */
import { MIKOO_WIN_MULTS } from './mikoo-game-economy';

/**
 * Weighted multipliers so EV when hit ≈ 5.9:
 * 0.40·2 + 0.25·3 + 0.15·4 + 0.10·10 + 0.05·20 + 0.03·25 + 0.02·50 ≈ 5.9
 * Hit rate ~0.135 → RTP ≈ 0.80.
 */
export function spinPayout(betAmount: number): { win: number; mult: number } {
  const bet = Math.max(1, Math.floor(betAmount || 0));
  if (Math.random() >= 0.135) {
    return { win: 0, mult: 0 };
  }
  const roll = Math.random();
  let mult: number;
  if (roll < 0.4) mult = 2;
  else if (roll < 0.65) mult = 3;
  else if (roll < 0.8) mult = 4;
  else if (roll < 0.9) mult = 10;
  else if (roll < 0.95) mult = 20;
  else if (roll < 0.98) mult = 25;
  else mult = 50;
  // Safety: only pay known mults.
  if (!(MIKOO_WIN_MULTS as readonly number[]).includes(mult)) mult = 2;
  return { win: Math.floor(bet * mult), mult };
}

/** Bias crash point lower so early crashes are common (house edge). */
export function biasedCrashAt(): number {
  const u = Math.random();
  if (u < 0.55) return Math.round((1.1 + Math.random() * 0.7) * 100) / 100;
  if (u < 0.85) return Math.round((1.8 + Math.random() * 1.4) * 100) / 100;
  return Math.round((3.2 + Math.pow(Math.random(), 1.4) * 5) * 100) / 100;
}

/**
 * Multi-area (7updown-style): Down 2× / Seven 5× / Up 2× — must match TableInfo UI.
 * Fair dice EV ≈ 0.83; keep classic ratio set for 3 areas.
 */
export function multiAreaMultipliers(): Record<number, number> {
  return { 1: 2, 2: 5, 3: 2 };
}
