/**
 * Shared Mikoo economy presets — chips, area ratios, spin multipliers.
 * Applied across hash + baishun gateways so every table feels consistent.
 */

/** Expanded stake tray (UI chips). */
export const MIKOO_BET_CHIPS = [50, 100, 200, 500, 1000, 2000, 5000, 10_000] as const;

/** Crash UI only wires 4 chip nodes — extra chips freeze some builds. */
export const MIKOO_CRASH_CHIPS = [50, 200, 1000, 5000] as const;

/**
 * Multipliers shown on symbols / paid on multi-area wins when the layout allows.
 * Order preferred by product: x2 x3 x4 x10 x20 x25 x50.
 */
export const MIKOO_WIN_MULTS = [2, 3, 4, 10, 20, 25, 50] as const;

/** Greedy box / 8-area boards — 8 icons (pad mid range). */
export const GREEDY_BOX_RATIOS = [2, 3, 4, 10, 20, 25, 50, 5] as const;

/** Luck-car real multipliers (UI uses milli = ratio * 1000). */
export const LUCK_CAR_RATIOS = [2, 3, 4, 10, 20, 25, 50, 5] as const;

export const LUCK_CAR_RATIOS_MILLI = LUCK_CAR_RATIOS.map((r) => r * 1000);

/** Bounty football 10 areas. */
export const BOUNTY_AREA_RATIOS = [2, 3, 4, 10, 20, 25, 50, 5, 8, 15] as const;

/** Bounty has a richer stake UI historically — keep room for big plays. */
export const BOUNTY_BET_CHIPS = [50, 100, 500, 1000, 2000, 5000, 10_000, 50_000] as const;

/** Lucky77 three bet icons. */
export const LUCKY77_AREA_RATIOS = [2, 3, 50] as const;

export function chipsList(): number[] {
  return [...MIKOO_BET_CHIPS];
}

/** Pick a random win mult from the table (for decorating symbols). */
export function randomWinMult(): number {
  return MIKOO_WIN_MULTS[Math.floor(Math.random() * MIKOO_WIN_MULTS.length)]!;
}
