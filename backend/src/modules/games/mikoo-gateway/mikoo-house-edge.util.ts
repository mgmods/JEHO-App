/**
 * House-favored payout RNG for Mikoo games.
 * Target long-run RTP ≈ 78–82%: players win sometimes, not too much.
 * EV ≈ 0.40 * (0.55*1 + 0.30*2 + 0.11*3 + 0.04*5) ≈ 0.40 * 1.64 ≈ 0.656
 * → keep closer to ~80% with 42% hit: 0.42 * 1.90 ≈ 0.80.
 */
export function spinPayout(betAmount: number): { win: number; mult: number } {
  const bet = Math.max(1, Math.floor(betAmount || 0));
  // ~42% hit — regular small wins; big wins rare.
  if (Math.random() >= 0.42) {
    return { win: 0, mult: 0 };
  }
  const roll = Math.random();
  let mult: number;
  if (roll < 0.55) mult = 1; // most wins = 1× bet
  else if (roll < 0.85) mult = 2;
  else if (roll < 0.96) mult = 3;
  else mult = 5; // rare
  return { win: Math.floor(bet * mult), mult };
}

/** Bias crash point lower so early crashes are common (house edge). */
export function biasedCrashAt(): number {
  // Heavy weight near 1.1–1.8, rare high multipliers.
  const u = Math.random();
  if (u < 0.55) return Math.round((1.1 + Math.random() * 0.7) * 100) / 100;
  if (u < 0.85) return Math.round((1.8 + Math.random() * 1.4) * 100) / 100;
  return Math.round((3.2 + Math.pow(Math.random(), 1.4) * 5) * 100) / 100;
}

/**
 * Multi-area (7updown-style): Down 2× / Seven 5× / Up 2× — must match TableInfo UI.
 * Fair dice EV ≈ 0.83; do not advertise 5× then pay 4×.
 */
export function multiAreaMultipliers(): Record<number, number> {
  return { 1: 2, 2: 5, 3: 2 };
}
