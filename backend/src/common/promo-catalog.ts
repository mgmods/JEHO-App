/**
 * JEHO promo economy — monthly recharge gifts, agent float bonuses,
 * supporter packs (frame + special ID), VIP time packs (never permanent).
 */
export const PROMO_CATALOG_VERSION = '20260806-promos-v2';

/** VIP is always rented — never permanent. Colorful duration packs. */
export const VIP_DURATION_PACKS = [
  {
    id: 'weekly',
    days: 7,
    priceMultiplier: 0.28,
    labelAr: 'أسبوعي',
    labelEn: 'Weekly',
    color: '#4FC3F7',
  },
  {
    id: 'monthly',
    days: 30,
    priceMultiplier: 1,
    labelAr: 'شهري',
    labelEn: 'Monthly',
    color: '#AB47BC',
  },
  {
    id: 'days40',
    days: 40,
    priceMultiplier: 1.25,
    labelAr: '40 يوم',
    labelEn: '40 Days',
    color: '#FF7043',
  },
] as const;

export const VIP_ALLOWED_DAYS = VIP_DURATION_PACKS.map((p) => p.days);

/** Monthly recharge milestones (calendar month, cumulative USD). */
export const MONTHLY_RECHARGE_OFFERS = [
  {
    id: 'monthly_500',
    thresholdUsd: 500,
    rewardDays: 45,
    titleAr: 'العرض الشهري الأول',
    titleEn: 'Monthly offer 1',
    descriptionAr: 'اشحن 500$ خلال شهر واحصل على هدية مميزة 45 يوم',
    descriptionEn: 'Recharge $500 in one month → special gift for 45 days',
    cosmeticCode: 'promo_monthly_gift_45',
  },
  {
    id: 'monthly_1000',
    thresholdUsd: 1000,
    rewardDays: 90,
    titleAr: 'العرض الشهري الثاني',
    titleEn: 'Monthly offer 2',
    descriptionAr: 'اشحن 1000$ خلال شهر واحصل على هدية مميزة 90 يوم',
    descriptionEn: 'Recharge $1000 in one month → special gift for 90 days',
    cosmeticCode: 'promo_monthly_gift_90',
  },
] as const;

/**
 * Recharge-agent float purchase bonus (on balance / float coins).
 * Applied when float is topped up by the matching USDT amount.
 */
  /** Agent float bonuses kept modest — free bonus coins are pure house liability. */
export const AGENT_FLOAT_BONUS_TIERS = [
  { id: 'agent_l1', thresholdUsd: 200, bonusPercent: 5, titleAr: 'المستوى الأول' },
  { id: 'agent_l2', thresholdUsd: 500, bonusPercent: 8, titleAr: 'المستوى الثاني' },
  { id: 'agent_l3', thresholdUsd: 1000, bonusPercent: 10, titleAr: 'المستوى الثالث' },
] as const;

/** Supporter recharge packs: custom frame + special ID for N days. */
export const SUPPORTER_PACKS = [
  {
    id: 'supporter_200',
    thresholdUsd: 200,
    rewardDays: 7,
    titleAr: 'باقة الداعم الأولى',
    titleEn: 'Supporter pack 1',
    descriptionAr: 'إطار مخصص + ID مميز لمدة 7 أيام',
    descriptionEn: 'Custom frame + special ID for 7 days',
    frameCosmeticCode: 'promo_supporter_frame_7',
  },
  {
    id: 'supporter_500',
    thresholdUsd: 500,
    rewardDays: 15,
    titleAr: 'باقة الداعم الثانية',
    titleEn: 'Supporter pack 2',
    descriptionAr: 'إطار مخصص + ID مميز لمدة 15 يومًا',
    descriptionEn: 'Custom frame + special ID for 15 days',
    frameCosmeticCode: 'promo_supporter_frame_15',
  },
  {
    id: 'supporter_1000',
    thresholdUsd: 1000,
    rewardDays: 30,
    titleAr: 'باقة الداعم الثالثة',
    titleEn: 'Supporter pack 3',
    descriptionAr: 'إطار مخصص + ID مميز لمدة 30 يومًا',
    descriptionEn: 'Custom frame + special ID for 30 days',
    frameCosmeticCode: 'promo_supporter_frame_30',
  },
] as const;

export function vipPackForDays(days: number) {
  const d = Math.floor(Number(days) || 30);
  return VIP_DURATION_PACKS.find((p) => p.days === d) || VIP_DURATION_PACKS[1];
}

export function vipPriceForDays(monthlyPrice: number, days: number): number {
  const pack = vipPackForDays(days);
  return Math.max(1, Math.ceil(Number(monthlyPrice || 0) * pack.priceMultiplier));
}

/** Highest matching agent bonus % for a USDT top-up. */
export function agentFloatBonusPercent(usdAmount: number): number {
  const usd = Number(usdAmount) || 0;
  let best = 0;
  for (const tier of AGENT_FLOAT_BONUS_TIERS) {
    if (usd >= tier.thresholdUsd) best = tier.bonusPercent;
  }
  return best;
}

export function currentMonthKey(d = new Date()): string {
  const y = d.getUTCFullYear();
  const m = String(d.getUTCMonth() + 1).padStart(2, '0');
  return `${y}-${m}`;
}
