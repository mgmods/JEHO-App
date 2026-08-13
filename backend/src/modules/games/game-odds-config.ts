/**
 * Dashboard-tunable house odds for ALL coin games.
 * Player RTP stays below 1 so the app does not lose long-term.
 */
export const GAME_ODDS_SETTING_KEY = 'games.house_odds';

export type GameOddsConfig = {
  version: number;
  updatedAt?: string;
  /** Long-run player return for multi-area boards (0.55–0.82). House = 1 − rtp. */
  playerRtp: number;
  /** Slot / spin hit chance before multiplier (0.05–0.22). */
  spinHitRate: number;
  /** Fishing is harder than slots. */
  fishingHitRate: number;
  /** Crash cashout rake (0.90–0.99). Win *= rake. */
  crashCashoutRake: number;
  /** Hilo payout multiplier on a correct guess (~0.5 win rate). */
  hiloPayoutMult: number;
  /** Royal battle: chance a winning ticket is paid (0.2–0.55). */
  royalBattlePayGate: number;
  /** Safety rails */
  maxBet: number;
  maxWinMult: number;
  maxWinAbsolute: number;
  maxCrashRatio: number;
};

export const DEFAULT_GAME_ODDS: GameOddsConfig = {
  version: 1,
  playerRtp: 0.7,
  spinHitRate: 0.115,
  fishingHitRate: 0.09,
  crashCashoutRake: 0.97,
  hiloPayoutMult: 1.4,
  royalBattlePayGate: 0.36,
  maxBet: 10_000,
  maxWinMult: 12,
  maxWinAbsolute: 80_000,
  maxCrashRatio: 8,
};

/** Presets for big-app style one-click tuning (always house-positive). */
export const GAME_ODDS_PRESETS: Record<string, Partial<GameOddsConfig>> = {
  /** Safer for the app — ~35% house edge on multi, rarer slot hits */
  conservative: {
    playerRtp: 0.62,
    spinHitRate: 0.09,
    fishingHitRate: 0.07,
    crashCashoutRake: 0.95,
    hiloPayoutMult: 1.3,
    royalBattlePayGate: 0.3,
    maxWinMult: 10,
    maxCrashRatio: 6,
  },
  /** Default balanced — ~30% house */
  balanced: {
    playerRtp: 0.7,
    spinHitRate: 0.115,
    fishingHitRate: 0.09,
    crashCashoutRake: 0.97,
    hiloPayoutMult: 1.4,
    royalBattlePayGate: 0.36,
    maxWinMult: 12,
    maxCrashRatio: 8,
  },
  /** More player-friendly but still house+ (~22% edge) */
  generous: {
    playerRtp: 0.78,
    spinHitRate: 0.14,
    fishingHitRate: 0.11,
    crashCashoutRake: 0.98,
    hiloPayoutMult: 1.5,
    royalBattlePayGate: 0.42,
    maxWinMult: 12,
    maxCrashRatio: 8,
  },
};

function clamp(n: number, min: number, max: number, fallback: number): number {
  const v = Number(n);
  if (!Number.isFinite(v)) return fallback;
  return Math.min(max, Math.max(min, v));
}

export function sanitizeGameOdds(raw: unknown): GameOddsConfig {
  let obj: any = raw;
  if (typeof raw === 'string') {
    try {
      obj = JSON.parse(raw);
    } catch {
      return { ...DEFAULT_GAME_ODDS };
    }
  }
  if (!obj || typeof obj !== 'object') return { ...DEFAULT_GAME_ODDS };
  const d = DEFAULT_GAME_ODDS;
  return {
    version: Math.max(1, Math.floor(Number(obj.version) || 1)),
    updatedAt: obj.updatedAt ? String(obj.updatedAt) : undefined,
    // Cap RTP at 82% so the platform cannot be configured to lose.
    playerRtp: clamp(obj.playerRtp, 0.55, 0.82, d.playerRtp),
    spinHitRate: clamp(obj.spinHitRate, 0.05, 0.22, d.spinHitRate),
    fishingHitRate: clamp(obj.fishingHitRate, 0.04, 0.18, d.fishingHitRate),
    crashCashoutRake: clamp(obj.crashCashoutRake, 0.9, 0.99, d.crashCashoutRake),
    hiloPayoutMult: clamp(obj.hiloPayoutMult, 1.15, 1.7, d.hiloPayoutMult),
    royalBattlePayGate: clamp(obj.royalBattlePayGate, 0.2, 0.55, d.royalBattlePayGate),
    maxBet: Math.floor(clamp(obj.maxBet, 100, 50_000, d.maxBet)),
    maxWinMult: Math.floor(clamp(obj.maxWinMult, 3, 25, d.maxWinMult)),
    maxWinAbsolute: Math.floor(clamp(obj.maxWinAbsolute, 1_000, 500_000, d.maxWinAbsolute)),
    maxCrashRatio: Math.floor(clamp(obj.maxCrashRatio, 3, 20, d.maxCrashRatio)),
  };
}

export function applyGameOddsPreset(
  name: string,
  base: GameOddsConfig = DEFAULT_GAME_ODDS,
): GameOddsConfig {
  const patch = GAME_ODDS_PRESETS[name];
  if (!patch) return sanitizeGameOdds(base);
  return sanitizeGameOdds({ ...base, ...patch });
}

/** Every Mikoo/BaiShun coin game controlled by these odds (dashboard coverage list). */
export const GAME_ODDS_COVERED_GAMES = [
  { id: '7updown', via: 'playerRtp' },
  { id: 'bounty-football', via: 'playerRtp' },
  { id: 'camel-racing', via: 'playerRtp' },
  { id: 'cleopatra-slot', via: 'spinHitRate' },
  { id: 'cleopatra-slots', via: 'spinHitRate' },
  { id: 'crash', via: 'crashCashoutRake+maxCrashRatio' },
  { id: 'fishing', via: 'fishingHitRate' },
  { id: 'football-plinko', via: 'spinHitRate' },
  { id: 'fortune-slot', via: 'spinHitRate' },
  { id: 'greedy-box', via: 'playerRtp' },
  { id: 'greedy-lion', via: 'playerRtp' },
  { id: 'hilo', via: 'hiloPayoutMult' },
  { id: 'line-slots', via: 'spinHitRate' },
  { id: 'luck-car', via: 'playerRtp' },
  { id: 'lucky77', via: 'playerRtp' },
  { id: 'megaways-slots', via: 'spinHitRate' },
  { id: 'olympians', via: 'spinHitRate' },
  { id: 'pirate-king', via: 'spinHitRate' },
  { id: 'royal-battle', via: 'royalBattlePayGate' },
  { id: 'slot777', via: 'spinHitRate' },
  { id: 'sugar-rush', via: 'spinHitRate' },
  { id: 'swimsuit-party', via: 'spinHitRate' },
] as const;

export function gameOddsClientPayload(cfg: GameOddsConfig) {
  const houseEdgePct = Math.round((1 - cfg.playerRtp) * 1000) / 10;
  const playerRtpPct = Math.round(cfg.playerRtp * 1000) / 10;
  return {
    ...cfg,
    playerRtpPct,
    houseEdgePct,
    coveredGames: GAME_ODDS_COVERED_GAMES.map((g) => g.id),
    coveredCount: GAME_ODDS_COVERED_GAMES.length,
    presets: Object.keys(GAME_ODDS_PRESETS),
    hints: {
      playerRtp: 'نسبة ما يعود للاعب على المدى الطويل (الباقي ربح التطبيق) — لوحات الرهان المتعددة',
      spinHitRate: 'فرصة فوز كل السلوتات / بلينكو / حفلة السباحة',
      fishingHitRate: 'فرصة صيد السمك (أصعب من السلوت)',
      crashCashoutRake: 'خصم على سحب الكراش (0.97 = يخصم 3%)',
      maxBet: 'أقصى رهان عملات لجولة واحدة — كل الألعاب',
      maxWinAbsolute: 'أقصى ربح عملات لجولة واحدة — كل الألعاب',
    },
  };
}
