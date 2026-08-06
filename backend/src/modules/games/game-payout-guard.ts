/**
 * Hard safety rails for ALL coin games (Mikoo / BaiShun / casual / dice / wheel).
 * RTP / inverse-weight house edge sits underneath; these caps stop catastrophic prints
 * if a mult table, uniform land bug, or stake loophole reappears.
 */

export const GAME_PAYOUT = {
  /** Max coins debited on a single bet placement / spin. */
  maxBet: 10_000,
  /**
   * Max coins credited for one settlement (one wallet credit / one round user total).
   * ~1.6× $5 pack at economy-v5 — big but not millions.
   */
  maxWinAbsolute: 80_000,
  /** Cap win as multiple of stake in that settlement. */
  maxWinMult: 12,
  /** Crash fly ratio hard stop before cashout math. */
  maxCrashRatio: 8,
} as const;

export function clampBetAmount(
  amount: number,
  maxBet: number = GAME_PAYOUT.maxBet,
): number {
  const a = Math.floor(Number(amount) || 0);
  if (!Number.isFinite(a) || a < 1) return 0;
  return Math.min(a, Math.max(1, Math.floor(maxBet)));
}

/**
 * Clamp a raw win so it cannot exceed mult×stake or absolute ceiling.
 * When `bet` is 0 / missing, only the absolute ceiling applies.
 */
export function clampGamePayout(opts: {
  bet?: number;
  win: number;
  maxMult?: number;
  maxAbsolute?: number;
}): { win: number; rawWin: number; capped: boolean } {
  const rawWin = Math.max(0, Math.floor(Number(opts.win) || 0));
  if (rawWin <= 0) return { win: 0, rawWin: 0, capped: false };
  const bet = Math.max(0, Math.floor(Number(opts.bet) || 0));
  const maxMult = opts.maxMult ?? GAME_PAYOUT.maxWinMult;
  const maxAbs = opts.maxAbsolute ?? GAME_PAYOUT.maxWinAbsolute;
  let win = rawWin;
  if (bet > 0 && maxMult > 0) {
    win = Math.min(win, Math.floor(bet * maxMult));
  }
  win = Math.min(win, maxAbs);
  return { win, rawWin, capped: win < rawWin };
}
