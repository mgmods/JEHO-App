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
