/**
 * Shared Mikoo economy presets — chips, area ratios, spin multipliers.
 * High display mults are OK because settlement lands via inverse-weight RTP (≈75%).
 */

/** Expanded stake tray (UI chips). */
export const MIKOO_BET_CHIPS = [50, 100, 200, 500, 1000, 2000, 5000, 10_000] as const;

/** Crash UI only wires 4 chip nodes — extra chips freeze some builds. */
export const MIKOO_CRASH_CHIPS = [50, 200, 1000, 5000] as const;

/**
 * Multipliers shown on symbols / paid on multi-area wins when the layout allows.
 * Cap peak at 25 for greedy boards (50 was EV-broken under uniform land).
 */
export const MIKOO_WIN_MULTS = [2, 3, 4, 10, 15, 20, 25] as const;

/**
 * Greedy box / 8-area — peaks capped; land is inverse-weighted to RTP~75%.
 * (Was [2,3,4,10,20,25,50,5] → 625% RTP on x50 under uniform.)
 */
export const GREEDY_BOX_RATIOS = [2, 3, 4, 6, 10, 12, 15, 5] as const;

/** Luck-car real multipliers (UI uses milli = ratio * 1000). */
export const LUCK_CAR_RATIOS = [2, 3, 4, 6, 10, 12, 15, 5] as const;

export const LUCK_CAR_RATIOS_MILLI = LUCK_CAR_RATIOS.map((r) => r * 1000);

/** Bounty football 10 areas — peaks soft (hard cap is game-payout-guard). */
export const BOUNTY_AREA_RATIOS = [2, 3, 4, 5, 6, 8, 10, 4, 6, 7] as const;

/** Same max chip as other multi boards (was 50k → giant single-area stacks). */
export const BOUNTY_BET_CHIPS = [50, 100, 500, 1000, 2000, 5000, 10_000] as const;

/**
 * Lucky77 three bet icons — peak was x50 (556% RTP under uniform).
 * Now ×10 display; land weights use inverse mult × target RTP.
 */
export const LUCKY77_AREA_RATIOS = [2, 3, 10] as const;

/**
 * Lucky77 UI wires exactly CHIPCOUNTS=4 nodes (chip0..chip3).
 * Sending more than 4 stake values stacks labels / overlays on the same seats.
 */
export const LUCKY77_BET_CHIPS = [50, 100, 200, 500] as const;

export function chipsList(): number[] {
  return [...MIKOO_BET_CHIPS];
}

export function lucky77ChipsList(): number[] {
  return [...LUCKY77_BET_CHIPS];
}

/** Pick a random win mult from the table (for decorating symbols). */
export function randomWinMult(): number {
  return MIKOO_WIN_MULTS[Math.floor(Math.random() * MIKOO_WIN_MULTS.length)]!;
}
