import {
  DEFAULT_GAME_ODDS,
  GameOddsConfig,
  sanitizeGameOdds,
} from './game-odds-config';

/**
 * Process-wide live odds — updated by GameOddsSettingsService on boot/save.
 * Util functions (spin/crash) read here without Nest DI.
 */
let live: GameOddsConfig = { ...DEFAULT_GAME_ODDS };

export function getGameOdds(): GameOddsConfig {
  return live;
}

export function setGameOdds(raw: unknown): GameOddsConfig {
  live = sanitizeGameOdds(raw);
  return live;
}

/** Central server-side availability gate; never rely on client visibility alone. */
export function isGameEnabled(gameId: string): boolean {
  const id = String(gameId || '').trim().toLowerCase();
  return !!id && !live.disabledGames.includes(id);
}

export function getGameWinRate(gameId: string): number | undefined {
  const value = live.gameWinRates?.[String(gameId || '').trim().toLowerCase()];
  return typeof value === 'number' && Number.isFinite(value) ? Math.min(100, Math.max(0, value)) : undefined;
}

export function getGameBetSettings(gameId: string): { minBet: number; maxBet: number; bettingEnabled: boolean; chips: number[] } {
  const id = String(gameId || '').trim().toLowerCase();
  const configured = live.gameBetSettings?.[id];
  const globalMax = Math.max(1, Math.floor(live.maxBet || 20_000));
  if (!configured) return { minBet: 50, maxBet: globalMax, bettingEnabled: true, chips: [50, 100, 500, 1000, 5000].filter((x) => x <= globalMax) };
  const minBet = Math.min(globalMax, Math.max(1, Math.floor(configured.minBet || 1)));
  return {
    minBet,
    maxBet: Math.max(minBet, Math.min(globalMax, Math.floor(configured.maxBet || globalMax))),
    bettingEnabled: configured.bettingEnabled !== false,
    chips: Array.isArray(configured.chips) && configured.chips.length
      ? configured.chips.filter((x) => Number.isInteger(x) && x >= minBet && x <= Math.min(globalMax, Math.floor(configured.maxBet || globalMax))).slice(0, 12)
      : [50, 100, 500, 1000, 5000].filter((x) => x >= minBet && x <= Math.min(globalMax, Math.floor(configured.maxBet || globalMax))),
  };
}

export function isGameBettingEnabled(gameId: string): boolean {
  return isGameEnabled(gameId) && getGameBetSettings(gameId).bettingEnabled;
}

export function getGameRtp(gameId: string): number | undefined {
  const value = live.gameRtpRates?.[String(gameId || '').trim().toLowerCase()];
  return typeof value === 'number' && Number.isFinite(value) ? Math.min(0.82, Math.max(0.55, value)) : undefined;
}
