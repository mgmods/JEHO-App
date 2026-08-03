/**
 * Official host/agency monthly target salary ladder (coins = diamonds 1:1 display).
 * Editable via dashboard host_monthly_target stages.
 */
export type HostSalaryLadderRow = {
  stage: number;
  targetCoins: number;
  hostSalaryUsd: number;
  agentSalaryUsd: number;
  totalUsd: number;
};

export const HOST_SALARY_LADDER_VERSION = '20260803-salary-v1';

/** 1 coin target unit displayed as 1 diamond (policy table). */
export const HOST_SALARY_COIN_EQUALS_DIAMOND = true;

export const HOST_SALARY_LADDER: readonly HostSalaryLadderRow[] = [
  { stage: 1, targetCoins: 15_000, hostSalaryUsd: 1, agentSalaryUsd: 0, totalUsd: 1 },
  { stage: 2, targetCoins: 30_000, hostSalaryUsd: 2, agentSalaryUsd: 0, totalUsd: 2 },
  { stage: 3, targetCoins: 45_000, hostSalaryUsd: 3, agentSalaryUsd: 0, totalUsd: 3 },
  { stage: 4, targetCoins: 60_000, hostSalaryUsd: 4, agentSalaryUsd: 0, totalUsd: 4 },
  { stage: 5, targetCoins: 75_000, hostSalaryUsd: 5, agentSalaryUsd: 0, totalUsd: 5 },
  { stage: 6, targetCoins: 100_000, hostSalaryUsd: 7, agentSalaryUsd: 0, totalUsd: 7 },
  { stage: 7, targetCoins: 150_000, hostSalaryUsd: 10, agentSalaryUsd: 2, totalUsd: 12 },
  { stage: 8, targetCoins: 200_000, hostSalaryUsd: 15, agentSalaryUsd: 5, totalUsd: 20 },
  { stage: 9, targetCoins: 400_000, hostSalaryUsd: 22, agentSalaryUsd: 8, totalUsd: 30 },
  { stage: 10, targetCoins: 700_000, hostSalaryUsd: 30, agentSalaryUsd: 10, totalUsd: 40 },
  { stage: 11, targetCoins: 1_000_000, hostSalaryUsd: 38, agentSalaryUsd: 12, totalUsd: 50 },
  { stage: 12, targetCoins: 1_200_000, hostSalaryUsd: 50, agentSalaryUsd: 18, totalUsd: 68 },
  { stage: 13, targetCoins: 1_400_000, hostSalaryUsd: 65, agentSalaryUsd: 22, totalUsd: 87 },
  { stage: 14, targetCoins: 1_600_000, hostSalaryUsd: 75, agentSalaryUsd: 25, totalUsd: 100 },
  { stage: 15, targetCoins: 1_800_000, hostSalaryUsd: 90, agentSalaryUsd: 30, totalUsd: 120 },
  { stage: 16, targetCoins: 2_000_000, hostSalaryUsd: 105, agentSalaryUsd: 45, totalUsd: 150 },
  { stage: 17, targetCoins: 2_200_000, hostSalaryUsd: 120, agentSalaryUsd: 60, totalUsd: 180 },
  { stage: 18, targetCoins: 2_400_000, hostSalaryUsd: 135, agentSalaryUsd: 80, totalUsd: 215 },
  { stage: 19, targetCoins: 2_600_000, hostSalaryUsd: 150, agentSalaryUsd: 100, totalUsd: 250 },
  { stage: 20, targetCoins: 2_800_000, hostSalaryUsd: 170, agentSalaryUsd: 130, totalUsd: 300 },
  { stage: 21, targetCoins: 3_000_000, hostSalaryUsd: 200, agentSalaryUsd: 170, totalUsd: 370 },
  { stage: 22, targetCoins: 3_500_000, hostSalaryUsd: 240, agentSalaryUsd: 220, totalUsd: 460 },
  { stage: 23, targetCoins: 4_000_000, hostSalaryUsd: 300, agentSalaryUsd: 300, totalUsd: 600 },
  { stage: 24, targetCoins: 4_500_000, hostSalaryUsd: 370, agentSalaryUsd: 400, totalUsd: 770 },
  { stage: 25, targetCoins: 5_000_000, hostSalaryUsd: 450, agentSalaryUsd: 550, totalUsd: 1000 },
  { stage: 26, targetCoins: 5_500_000, hostSalaryUsd: 550, agentSalaryUsd: 700, totalUsd: 1250 },
  { stage: 27, targetCoins: 6_000_000, hostSalaryUsd: 700, agentSalaryUsd: 900, totalUsd: 1600 },
  { stage: 28, targetCoins: 6_500_000, hostSalaryUsd: 850, agentSalaryUsd: 1150, totalUsd: 2000 },
  { stage: 29, targetCoins: 7_000_000, hostSalaryUsd: 1000, agentSalaryUsd: 1450, totalUsd: 2450 },
  { stage: 30, targetCoins: 8_000_000, hostSalaryUsd: 1200, agentSalaryUsd: 1800, totalUsd: 3000 },
  { stage: 31, targetCoins: 9_000_000, hostSalaryUsd: 2200, agentSalaryUsd: 1400, totalUsd: 3600 },
  { stage: 32, targetCoins: 10_000_000, hostSalaryUsd: 2600, agentSalaryUsd: 1600, totalUsd: 4200 },
  { stage: 33, targetCoins: 12_000_000, hostSalaryUsd: 3000, agentSalaryUsd: 1800, totalUsd: 4800 },
  { stage: 34, targetCoins: 15_000_000, hostSalaryUsd: 3400, agentSalaryUsd: 2000, totalUsd: 5400 },
] as const;

export function salaryLadderToHostTargetStages() {
  return HOST_SALARY_LADDER.map((row) => {
    const host = Number(row.hostSalaryUsd) || 0;
    const agent = Number(row.agentSalaryUsd) || 0;
    const total = Number(row.totalUsd) || host + agent;
    return {
      id: `salary_${row.stage}`,
      title: `مرحلة ${row.stage}`,
      threshold: row.targetCoins,
      rewardCoins: 0,
      rewardDiamonds: 0,
      hostSalaryUsd: host,
      agentSalaryUsd: agent,
      totalUsd: total,
      coinEqualsDiamond: true,
    };
  });
}

/** True when stages look like the pre-salary ladder (no USD columns). */
export function stagesMissingSalaryFields(
  stages: Array<{ hostSalaryUsd?: number; agentSalaryUsd?: number }> | null | undefined,
): boolean {
  if (!Array.isArray(stages) || stages.length === 0) return true;
  return !stages.some(
    (s) => Number(s?.hostSalaryUsd) > 0 || Number(s?.agentSalaryUsd) > 0,
  );
}

/** Attach official USD salaries by matching targetCoins / threshold. */
export function enrichStagesWithOfficialSalaries<
  T extends {
    threshold?: number;
    hostSalaryUsd?: number;
    agentSalaryUsd?: number;
    totalUsd?: number;
    coinEqualsDiamond?: boolean;
  },
>(stages: T[]): T[] {
  const byThreshold = new Map(
    HOST_SALARY_LADDER.map((r) => [r.targetCoins, r] as const),
  );
  return stages.map((s) => {
    if (Number(s.hostSalaryUsd) > 0 || Number(s.agentSalaryUsd) > 0) {
      return { ...s, coinEqualsDiamond: s.coinEqualsDiamond !== false };
    }
    const row = byThreshold.get(Math.floor(Number(s.threshold) || 0));
    if (!row) return { ...s, coinEqualsDiamond: true };
    const host = row.hostSalaryUsd;
    const agent = row.agentSalaryUsd;
    return {
      ...s,
      hostSalaryUsd: host,
      agentSalaryUsd: agent,
      totalUsd: row.totalUsd || host + agent,
      coinEqualsDiamond: true,
    };
  });
}
