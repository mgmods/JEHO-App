/**
 * Hard safety rails for ALL coin games (Mikoo / BaiShun / casual / dice / wheel).
 * Caps are dashboard-tunable via game.house_odds; defaults stay house-safe.
 */
import { getGameOdds } from './game-odds-runtime';

/** Static defaults (also used before settings hydrate). */
export const GAME_PAYOUT = {
  maxBet: 10_000,
  maxWinAbsolute: 80_000,
  maxWinMult: 12,
  maxCrashRatio: 8,
} as const;

/** Live limits from dashboard (falls back to GAME_PAYOUT). */
export function gamePayoutLimits() {
  try {
    const o = getGameOdds();
    return {
      maxBet: o.maxBet || GAME_PAYOUT.maxBet,
      maxWinAbsolute: o.maxWinAbsolute || GAME_PAYOUT.maxWinAbsolute,
      maxWinMult: o.maxWinMult || GAME_PAYOUT.maxWinMult,
      maxCrashRatio: o.maxCrashRatio || GAME_PAYOUT.maxCrashRatio,
    };
  } catch {
    return { ...GAME_PAYOUT };
  }
}

export function clampBetAmount(
  amount: number,
  maxBet: number = gamePayoutLimits().maxBet,
): number {
  const a = Math.floor(Number(amount) || 0);
  if (!Number.isFinite(a) || a < 1) return 0;
  return Math.min(a, Math.max(1, Math.floor(maxBet)));
}

/**
 * Clamp a raw win so it cannot exceed mult×stake or absolute ceiling.
 */
export function clampGamePayout(opts: {
  bet?: number;
  win: number;
  maxMult?: number;
  maxAbsolute?: number;
}): { win: number; rawWin: number; capped: boolean } {
  const limits = gamePayoutLimits();
  const rawWin = Math.max(0, Math.floor(Number(opts.win) || 0));
  const maxMult = opts.maxMult ?? limits.maxWinMult;
  const maxAbs = opts.maxAbsolute ?? limits.maxWinAbsolute;
  const bet = Math.max(0, Math.floor(Number(opts.bet) || 0));
  let win = Math.min(rawWin, maxAbs);
  if (bet > 0) {
    win = Math.min(win, Math.floor(bet * maxMult));
  }
  return { win, rawWin, capped: win < rawWin };
}
