/** Official host/agency salary ladder — mirrors backend host-salary-ladder.ts
 * Minimum host salary / cashout step: **$10** (no $1 / $2 / $4 stages).
 */
export const HOST_TARGET_MIN_USD = 10
export const HOST_SALARY_LADDER_VERSION = '20260807-min10usd-v2'

export const OFFICIAL_SALARY_LADDER = [
  { stage: 1, targetCoins: 150000, hostSalaryUsd: 10, agentSalaryUsd: 2, totalUsd: 12 },
  { stage: 2, targetCoins: 200000, hostSalaryUsd: 15, agentSalaryUsd: 5, totalUsd: 20 },
  { stage: 3, targetCoins: 400000, hostSalaryUsd: 22, agentSalaryUsd: 8, totalUsd: 30 },
  { stage: 4, targetCoins: 700000, hostSalaryUsd: 30, agentSalaryUsd: 10, totalUsd: 40 },
  { stage: 5, targetCoins: 1000000, hostSalaryUsd: 38, agentSalaryUsd: 12, totalUsd: 50 },
  { stage: 6, targetCoins: 1200000, hostSalaryUsd: 50, agentSalaryUsd: 18, totalUsd: 68 },
  { stage: 7, targetCoins: 1400000, hostSalaryUsd: 65, agentSalaryUsd: 22, totalUsd: 87 },
  { stage: 8, targetCoins: 1600000, hostSalaryUsd: 75, agentSalaryUsd: 25, totalUsd: 100 },
  { stage: 9, targetCoins: 1800000, hostSalaryUsd: 90, agentSalaryUsd: 30, totalUsd: 120 },
  { stage: 10, targetCoins: 2000000, hostSalaryUsd: 105, agentSalaryUsd: 45, totalUsd: 150 },
  { stage: 11, targetCoins: 2200000, hostSalaryUsd: 120, agentSalaryUsd: 60, totalUsd: 180 },
  { stage: 12, targetCoins: 2400000, hostSalaryUsd: 135, agentSalaryUsd: 80, totalUsd: 215 },
  { stage: 13, targetCoins: 2600000, hostSalaryUsd: 150, agentSalaryUsd: 100, totalUsd: 250 },
  { stage: 14, targetCoins: 2800000, hostSalaryUsd: 170, agentSalaryUsd: 130, totalUsd: 300 },
  { stage: 15, targetCoins: 3000000, hostSalaryUsd: 200, agentSalaryUsd: 170, totalUsd: 370 },
  { stage: 16, targetCoins: 3500000, hostSalaryUsd: 240, agentSalaryUsd: 220, totalUsd: 460 },
  { stage: 17, targetCoins: 4000000, hostSalaryUsd: 300, agentSalaryUsd: 300, totalUsd: 600 },
  { stage: 18, targetCoins: 4500000, hostSalaryUsd: 370, agentSalaryUsd: 400, totalUsd: 770 },
  { stage: 19, targetCoins: 5000000, hostSalaryUsd: 450, agentSalaryUsd: 550, totalUsd: 1000 },
  { stage: 20, targetCoins: 5500000, hostSalaryUsd: 550, agentSalaryUsd: 700, totalUsd: 1250 },
  { stage: 21, targetCoins: 6000000, hostSalaryUsd: 700, agentSalaryUsd: 900, totalUsd: 1600 },
  { stage: 22, targetCoins: 6500000, hostSalaryUsd: 850, agentSalaryUsd: 1150, totalUsd: 2000 },
  { stage: 23, targetCoins: 7000000, hostSalaryUsd: 1000, agentSalaryUsd: 1450, totalUsd: 2450 },
  { stage: 24, targetCoins: 8000000, hostSalaryUsd: 1200, agentSalaryUsd: 1800, totalUsd: 3000 },
  { stage: 25, targetCoins: 9000000, hostSalaryUsd: 2200, agentSalaryUsd: 1400, totalUsd: 3600 },
  { stage: 26, targetCoins: 10000000, hostSalaryUsd: 2600, agentSalaryUsd: 1600, totalUsd: 4200 },
  { stage: 27, targetCoins: 12000000, hostSalaryUsd: 3000, agentSalaryUsd: 1800, totalUsd: 4800 },
  { stage: 28, targetCoins: 15000000, hostSalaryUsd: 3400, agentSalaryUsd: 2000, totalUsd: 5400 },
]
