import {
  Injectable,
  Optional,
  OnModuleInit,
  Logger,
  BadRequestException,
  ForbiddenException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { DataSource, Repository } from 'typeorm';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { HostMonthlyProgress } from '../../database/entities/host-monthly-progress.entity';
import { Wallet } from '../../database/entities/wallet.entity';
import {
  WalletTransaction,
  TransactionType,
  CurrencyType,
} from '../../database/entities/wallet-transaction.entity';
import { Agency } from '../../database/entities/agency.entity';
import {
  AgencyMember,
  AgencyMemberStatus,
  AgencyRole,
} from '../../database/entities/agency-member.entity';
import { RealtimeGateway } from '../realtime/realtime.gateway';
import { CosmeticsService } from '../cosmetics/cosmetics.service';
import { VipService } from '../vip/vip.service';
import {
  salaryLadderToHostTargetStages,
  stagesBelowMinHostUsd,
  stagesMissingSalaryFields,
  HOST_SALARY_LADDER_VERSION,
  HOST_TARGET_MIN_USD,
} from '../../common/host-salary-ladder';

export type HostTargetStage = {
  id: string;
  threshold: number;
  rewardCoins: number;
  rewardDiamonds: number;
  title?: string;
  /** Policy salary (USD) paid to host when stage is completed — display / settlement. */
  hostSalaryUsd?: number;
  /** Policy salary (USD) paid to agency/agent when stage is completed. */
  agentSalaryUsd?: number;
  /** hostSalaryUsd + agentSalaryUsd (cached for UI). */
  totalUsd?: number;
  /** Table rule: 1 target coin unit = 1 diamond for display. */
  coinEqualsDiamond?: boolean;
  /** Optional mall cosmetic code (frame / entry / room card…). */
  rewardCosmeticCode?: string;
  /** Days for temporary cosmetic grant (default 7). */
  rewardCosmeticDays?: number;
  /** Optional VIP level to grant temporarily. */
  rewardVipLevel?: number;
  /** Days for temporary VIP (default 7). */
  rewardVipDays?: number;
};

export type HostTargetPeriod = 'weekly' | 'monthly';

export type HostMonthlyTargetConfig = {
  enabled: boolean;
  currency: 'diamonds' | 'gift_coins';
  /**
   * Settlement / progress window:
   * - monthly: calendar UTC month (default)
   * - weekly: ISO week UTC (Mon–Sun)
   * Visibility stays host + agency management (not public “everyone”).
   */
  period: HostTargetPeriod;
  stages: HostTargetStage[];
};

const DEFAULT_CONFIG: HostMonthlyTargetConfig = {
  enabled: true,
  currency: 'diamonds',
  period: 'monthly',
  stages: salaryLadderToHostTargetStages(),
};

@Injectable()
export class HostTargetService implements OnModuleInit {
  private readonly log = new Logger(HostTargetService.name);

  constructor(
    @InjectRepository(AppSetting)
    private readonly settingsRepo: Repository<AppSetting>,
    @InjectRepository(HostMonthlyProgress)
    private readonly progressRepo: Repository<HostMonthlyProgress>,
    @InjectRepository(Wallet)
    private readonly walletRepo: Repository<Wallet>,
    @InjectRepository(Agency)
    private readonly agencyRepo: Repository<Agency>,
    @InjectRepository(AgencyMember)
    private readonly membersRepo: Repository<AgencyMember>,
    private readonly dataSource: DataSource,
    @Optional() private readonly realtime?: RealtimeGateway,
    @Optional() private readonly cosmetics?: CosmeticsService,
    @Optional() private readonly vip?: VipService,
  ) {}

  async onModuleInit() {
    try {
      await this.ensureCyclesColumn();
      const row = await this.settingsRepo.findOne({
        where: { key: 'host_monthly_target' },
      });
      if (!row?.value) {
        await this.saveConfig(DEFAULT_CONFIG);
        this.log.log('Seeded default host_monthly_target stages');
        return;
      }
      const parsed = JSON.parse(row.value) as Partial<HostMonthlyTargetConfig> & {
        ladderVersion?: string;
      };
      const stages = Array.isArray(parsed.stages) ? parsed.stages : [];
      const needsUpgrade =
        stages.length === 0 ||
        stagesMissingSalaryFields(stages) ||
        stages.length < 20 ||
        stagesBelowMinHostUsd(stages, HOST_TARGET_MIN_USD) ||
        parsed.ladderVersion !== HOST_SALARY_LADDER_VERSION;
      if (needsUpgrade) {
        await this.saveConfig({
          ...DEFAULT_CONFIG,
          enabled: parsed.enabled !== false,
          currency: parsed.currency === 'gift_coins' ? 'gift_coins' : 'diamonds',
          period: this.normalizePeriod((parsed as { period?: string }).period),
        });
        this.log.log(
          `Upgraded host_monthly_target to salary ladder ${HOST_SALARY_LADDER_VERSION} (min $${HOST_TARGET_MIN_USD})`,
        );
      }
    } catch (err) {
      this.log.warn(
        `host_monthly_target seed skipped: ${
          err instanceof Error ? err.message : String(err)
        }`,
      );
    }
  }

  /** Prod may run with synchronize=false — add cycles / withdrawn ids if missing. */
  private async ensureCyclesColumn() {
    try {
      await this.dataSource.query(`
        ALTER TABLE host_monthly_progress
        ADD COLUMN IF NOT EXISTS "cyclesCompleted" integer NOT NULL DEFAULT 0
      `);
    } catch (err) {
      this.log.warn(
        `cyclesCompleted column ensure skipped: ${
          err instanceof Error ? err.message : String(err)
        }`,
      );
    }
    try {
      await this.dataSource.query(`
        ALTER TABLE host_monthly_progress
        ADD COLUMN IF NOT EXISTS "withdrawnStageIds" text
      `);
    } catch (err) {
      this.log.warn(
        `withdrawnStageIds column ensure skipped: ${
          err instanceof Error ? err.message : String(err)
        }`,
      );
    }
  }

  /** Normalize config period. */
  private normalizePeriod(raw: unknown): HostTargetPeriod {
    const p = String(raw || '').toLowerCase().trim();
    return p === 'weekly' || p === 'week' ? 'weekly' : 'monthly';
  }

  /**
   * Progress bucket key stored in host_monthly_progress.yearMonth (varchar 7).
   * Monthly: `2026-08` (7) | Weekly: `2026W32` (7)
   */
  periodKey(date = new Date(), period: HostTargetPeriod = 'monthly') {
    if (period === 'weekly') {
      return this.isoWeekKey(date);
    }
    return this.yearMonth(date);
  }

  yearMonth(date = new Date()) {
    const y = date.getUTCFullYear();
    const m = String(date.getUTCMonth() + 1).padStart(2, '0');
    return `${y}-${m}`;
  }

  /** Compact ISO week key fitting varchar(7): `2026W32` */
  isoWeekKey(date = new Date()) {
    const d = new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate()));
    d.setUTCDate(d.getUTCDate() + 4 - (d.getUTCDay() || 7));
    const yearStart = new Date(Date.UTC(d.getUTCFullYear(), 0, 1));
    const weekNo = Math.ceil(((d.getTime() - yearStart.getTime()) / 86400000 + 1) / 7);
    return `${d.getUTCFullYear()}W${String(weekNo).padStart(2, '0')}`;
  }

  periodLabelAr(periodKey: string, period: HostTargetPeriod) {
    if (period === 'weekly' || /W\d{1,2}$/i.test(periodKey) || periodKey.includes('-W')) {
      const m = /^(\d{4})W(\d{1,2})$/i.exec(periodKey)
        || /^(\d{4})-W(\d{1,2})$/i.exec(periodKey);
      if (m) return `الأسبوع ${Number(m[2])} · ${m[1]}`;
      return `أسبوع · ${periodKey}`;
    }
    const parts = periodKey.split('-');
    if (parts.length >= 2) {
      const months = [
        '', 'يناير', 'فبراير', 'مارس', 'أبريل', 'مايو', 'يونيو',
        'يوليو', 'أغسطس', 'سبتمبر', 'أكتوبر', 'نوفمبر', 'ديسمبر',
      ];
      const mi = Number(parts[1]);
      return `${months[mi] || parts[1]} ${parts[0]}`;
    }
    return periodKey;
  }

  private normalizeStage(s: Partial<HostTargetStage> | null | undefined, i: number): HostTargetStage {
    const cosmeticCode = String(s?.rewardCosmeticCode || '').trim();
    const vipLevel = Math.max(0, Math.floor(Number(s?.rewardVipLevel) || 0));
    const hostSalaryUsd = Math.max(0, Number(s?.hostSalaryUsd) || 0);
    const agentSalaryUsd = Math.max(0, Number(s?.agentSalaryUsd) || 0);
    const totalUsd = Math.max(
      0,
      Number(s?.totalUsd) || hostSalaryUsd + agentSalaryUsd,
    );
    return {
      id: String(s?.id || `stage_${i + 1}`),
      threshold: Math.max(0, Number(s?.threshold) || 0),
      rewardCoins: Math.max(0, Math.floor(Number(s?.rewardCoins) || 0)),
      rewardDiamonds: Math.max(0, Math.floor(Number(s?.rewardDiamonds) || 0)),
      title: s?.title ? String(s.title) : `مرحلة ${i + 1}`,
      hostSalaryUsd,
      agentSalaryUsd,
      totalUsd,
      coinEqualsDiamond: s?.coinEqualsDiamond !== false,
      ...(cosmeticCode
        ? {
            rewardCosmeticCode: cosmeticCode,
            rewardCosmeticDays: Math.max(
              1,
              Math.min(365, Math.floor(Number(s?.rewardCosmeticDays) || 7)),
            ),
          }
        : {}),
      ...(vipLevel > 0
        ? {
            rewardVipLevel: vipLevel,
            rewardVipDays: Math.max(
              1,
              Math.min(365, Math.floor(Number(s?.rewardVipDays) || 7)),
            ),
          }
        : {}),
    };
  }

  async getConfig(): Promise<HostMonthlyTargetConfig> {
    const row = await this.settingsRepo.findOne({
      where: { key: 'host_monthly_target' },
    });
    if (!row?.value) return { ...DEFAULT_CONFIG, stages: [...DEFAULT_CONFIG.stages] };
    try {
      const parsed = JSON.parse(row.value) as Partial<HostMonthlyTargetConfig>;
      let stages = Array.isArray(parsed.stages)
        ? parsed.stages
            .map((s, i) => this.normalizeStage(s, i))
            .sort((a, b) => a.threshold - b.threshold)
        : [...DEFAULT_CONFIG.stages];
      // Never expose sub-$10 host steps to clients / withdraw package builders.
      if (stagesBelowMinHostUsd(stages, HOST_TARGET_MIN_USD)) {
        stages = salaryLadderToHostTargetStages();
      } else {
        stages = stages.filter(
          (s) =>
            Number(s.hostSalaryUsd) <= 0 ||
            Number(s.hostSalaryUsd) >= HOST_TARGET_MIN_USD - 0.001,
        );
        if (stages.length === 0) stages = salaryLadderToHostTargetStages();
      }
      return {
        enabled: !!parsed.enabled,
        currency: parsed.currency === 'gift_coins' ? 'gift_coins' : 'diamonds',
        period: this.normalizePeriod(parsed.period),
        stages,
      };
    } catch {
      return { ...DEFAULT_CONFIG, stages: [...DEFAULT_CONFIG.stages] };
    }
  }

  async saveConfig(config: HostMonthlyTargetConfig) {
    const MIN = HOST_TARGET_MIN_USD;
    let stages = (config.stages || [])
      .map((s, i) => this.normalizeStage(s, i))
      .sort((a, b) => a.threshold - b.threshold)
      .filter((s) => Number(s.hostSalaryUsd) <= 0 || Number(s.hostSalaryUsd) >= MIN - 0.001);

    if (stages.length === 0 || stagesBelowMinHostUsd(stages, MIN)) {
      stages = salaryLadderToHostTargetStages();
    }

    const normalized: HostMonthlyTargetConfig = {
      enabled: !!config.enabled,
      currency: config.currency === 'gift_coins' ? 'gift_coins' : 'diamonds',
      period: this.normalizePeriod(config.period),
      stages,
    };
    const payload = {
      ...normalized,
      ladderVersion: HOST_SALARY_LADDER_VERSION,
      minHostTargetUsd: HOST_TARGET_MIN_USD,
    };
    let row = await this.settingsRepo.findOne({
      where: { key: 'host_monthly_target' },
    });
    if (!row) {
      row = this.settingsRepo.create({
        key: 'host_monthly_target',
        value: JSON.stringify(payload),
        description:
          'Host target stages (weekly|monthly period). Min host step $10. Visible to host & agency only.',
      });
    } else {
      row.value = JSON.stringify(payload);
    }
    await this.settingsRepo.save(row);
    return normalized;
  }

  async getMyTarget(userId: string) {
    const config = await this.getConfig();
    const yearMonth = this.periodKey(new Date(), config.period);
    let row = await this.progressRepo.findOne({ where: { userId, yearMonth } });
    if (!row) {
      row = this.progressRepo.create({
        userId,
        yearMonth,
        progress: 0,
        claimedStageIds: [],
        withdrawnStageIds: [],
        cyclesCompleted: 0,
      });
      await this.progressRepo.save(row);
    }
    const progress = Number(row.progress || 0);
    const claimed = new Set(row.claimedStageIds || []);
    const withdrawn = new Set(
      Array.isArray(row.withdrawnStageIds) ? row.withdrawnStageIds : [],
    );
    const cyclesCompleted = Math.max(0, Number(row.cyclesCompleted || 0));
    const stages = config.stages.map((s, i) => {
      const isClaimed = claimed.has(s.id);
      const isWithdrawn = withdrawn.has(s.id);
      const reached = progress >= s.threshold;
      return {
        ...s,
        index: i + 1,
        claimed: isClaimed,
        salaryWithdrawn: isWithdrawn,
        reached,
        status: 'locked' as 'done' | 'current' | 'locked',
      };
    });
    // Ladder for salary UI: first stage not yet salary-withdrawn is the only "current".
    let foundCurrent = false;
    for (const s of stages) {
      if (s.salaryWithdrawn) {
        s.status = 'done';
        continue;
      }
      if (!foundCurrent) {
        s.status = 'current';
        foundCurrent = true;
      } else {
        s.status = 'locked';
      }
    }
    const current = stages.find((s) => s.status === 'current');
    const next = current || stages.find((s) => !s.salaryWithdrawn);
    return {
      enabled: config.enabled,
      currency: config.currency,
      period: config.period,
      periodKey: yearMonth,
      periodLabel: this.periodLabelAr(yearMonth, config.period),
      /** @deprecated alias — same as periodKey */
      yearMonth,
      /** host_and_agency for progress glance; withdraw options only for host. */
      visibility: 'host_and_agency',
      progress,
      cyclesCompleted,
      cycle: cyclesCompleted + 1,
      stages,
      currentStageId: current?.id ?? null,
      withdrawnStageIds: [...withdrawn],
      nextThreshold: next?.threshold ?? (config.stages[0]?.threshold ?? null),
      remaining: next
        ? Math.max(0, next.threshold - progress)
        : Math.max(0, (config.stages[0]?.threshold ?? 0) - progress),
      allCompleteThisCycle:
        stages.length > 0 && stages.every((s) => s.salaryWithdrawn),
    };
  }

  /** Called when a host receives gift diamonds / coins this period. */
  async recordHostProgress(userId: string, amount: number) {
    if (!userId || !amount || amount <= 0) return null;
    const config = await this.getConfig();
    if (!config.enabled || !config.stages.length) return null;
    const maxThreshold = config.stages[config.stages.length - 1].threshold;
    if (maxThreshold <= 0) return null;

    const yearMonth = this.periodKey(new Date(), config.period);
    const result = await this.dataSource.transaction(async (manager) => {
      let row = await manager.findOne(HostMonthlyProgress, {
        where: { userId, yearMonth },
        lock: { mode: 'pessimistic_write' },
      });
      if (!row) {
        row = manager.create(HostMonthlyProgress, {
          userId,
          yearMonth,
          progress: 0,
          claimedStageIds: [],
          withdrawnStageIds: [],
          cyclesCompleted: 0,
        });
      }
      row.progress = Number(row.progress || 0) + Math.floor(amount);
      row.cyclesCompleted = Math.max(0, Number(row.cyclesCompleted || 0));
      const claimed = new Set(row.claimedStageIds || []);
      const withdrawn = new Set(
        Array.isArray(row.withdrawnStageIds) ? row.withdrawnStageIds : [],
      );
      const newlyClaimed: Array<HostTargetStage & { cycle: number }> = [];

      let wallet = await manager.findOne(Wallet, {
        where: { userId },
        lock: { mode: 'pessimistic_write' },
      });
      if (!wallet) wallet = manager.create(Wallet, { userId });

      const awardDueStages = async () => {
        for (const stage of config.stages) {
          if (claimed.has(stage.id)) continue;
          if (row.progress < stage.threshold) continue;
          claimed.add(stage.id);
          newlyClaimed.push({ ...stage, cycle: row.cyclesCompleted + 1 });
          if (stage.rewardCoins > 0) {
            wallet.coins = Number(wallet.coins || 0) + stage.rewardCoins;
            await manager.save(
              manager.create(WalletTransaction, {
                userId,
                type: TransactionType.LUCKY_REWARD,
                currency: CurrencyType.COINS,
                amount: stage.rewardCoins,
                balanceAfter: Number(wallet.coins),
                referenceType: 'host_monthly_target',
                referenceId: `${yearMonth}:c${row.cyclesCompleted + 1}:${stage.id}:coins`,
                description: `Host target (${config.period}): ${stage.title || stage.id}`,
                metadata: {
                  stageId: stage.id,
                  title: stage.title,
                  cycle: row.cyclesCompleted + 1,
                  period: config.period,
                  periodKey: yearMonth,
                },
              }),
            );
          }
          if (stage.rewardDiamonds > 0) {
            wallet.diamonds = Number(wallet.diamonds || 0) + stage.rewardDiamonds;
            await manager.save(
              manager.create(WalletTransaction, {
                userId,
                type: TransactionType.LUCKY_REWARD,
                currency: CurrencyType.DIAMONDS,
                amount: stage.rewardDiamonds,
                balanceAfter: Number(wallet.diamonds),
                referenceType: 'host_monthly_target',
                referenceId: `${yearMonth}:c${row.cyclesCompleted + 1}:${stage.id}:diamonds`,
                description: `Host target (${config.period}): ${stage.title || stage.id}`,
                metadata: {
                  stageId: stage.id,
                  title: stage.title,
                  cycle: row.cyclesCompleted + 1,
                  period: config.period,
                  periodKey: yearMonth,
                },
              }),
            );
          }
        }
      };

      await awardDueStages();

      // When every stage in this cycle is claimed, restart from stage 1 (keep overflow).
      let safety = 0;
      while (safety++ < 40) {
        const allClaimed = config.stages.every((s) => claimed.has(s.id));
        if (!allClaimed) break;
        if (row.progress < maxThreshold) break;
        row.progress = Number(row.progress) - maxThreshold;
        claimed.clear();
        withdrawn.clear();
        row.cyclesCompleted = Number(row.cyclesCompleted || 0) + 1;
        await awardDueStages();
      }

      row.claimedStageIds = [...claimed];
      row.withdrawnStageIds = [...withdrawn];
      await manager.save(wallet);
      await manager.save(row);

      if (newlyClaimed.length) {
        this.realtime?.emitToUser(userId, 'host_target:reward', {
          yearMonth,
          period: config.period,
          periodKey: yearMonth,
          progress: Number(row.progress),
          cyclesCompleted: Number(row.cyclesCompleted || 0),
          cycle: Number(row.cyclesCompleted || 0) + 1,
          stages: newlyClaimed,
          at: new Date().toISOString(),
        });
      }
      return {
        progress: Number(row.progress),
        cyclesCompleted: Number(row.cyclesCompleted || 0),
        newlyClaimed,
      };
    });

    for (const stage of result.newlyClaimed || []) {
      const cosmeticCode = String(stage.rewardCosmeticCode || '').trim();
      if (cosmeticCode && this.cosmetics) {
        try {
          await this.cosmetics.grantTemporary(
            userId,
            cosmeticCode,
            Number(stage.rewardCosmeticDays) || 7,
          );
        } catch (err) {
          this.log.warn(
            `host target cosmetic grant failed (${cosmeticCode}): ${
              err instanceof Error ? err.message : String(err)
            }`,
          );
        }
      }
      const vipLevel = Math.max(0, Math.floor(Number(stage.rewardVipLevel) || 0));
      if (vipLevel > 0 && this.vip) {
        try {
          await this.vip.grantTemporaryVip(
            userId,
            vipLevel,
            Number(stage.rewardVipDays) || 7,
          );
        } catch (err) {
          this.log.warn(
            `host target VIP grant failed (VIP${vipLevel}): ${
              err instanceof Error ? err.message : String(err)
            }`,
          );
        }
      }
    }
    return result;
  }

  private async diamondUsdRate(): Promise<number> {
    const row = await this.settingsRepo.findOne({
      where: { key: 'economy.diamondUsdRate' },
    });
    const n = Number(row?.value || 0.00005);
    return Number.isFinite(n) && n > 0 ? n : 0.00005;
  }

  private usdToDiamonds(usd: number, rate: number): number {
    if (usd <= 0 || rate <= 0) return 0;
    return Math.max(0, Math.round(usd / rate));
  }

  /**
   * Withdraw options:
   * - host: ONLY the single current target stage (advance after salary withdraw).
   * - agency: no target — free cash-out from commission balance (client packages).
   */
  async getWithdrawOptions(
    userId: string,
    role: 'host' | 'agency',
    agencyId?: string | null,
  ) {
    const rate = await this.diamondUsdRate();
    const config = await this.getConfig();

    // Agency board is independent of host target / call stats.
    if (role === 'agency') {
      const aid = String(agencyId || '').trim();
      if (!aid) throw new BadRequestException('agencyId required');
      const agency = await this.agencyRepo.findOne({ where: { id: aid } });
      if (!agency) throw new BadRequestException('Agency not found');
      const membership = await this.membersRepo.findOne({
        where: {
          agencyId: aid,
          userId,
          status: AgencyMemberStatus.ACTIVE,
        },
      });
      const isOwner = agency.ownerId === userId;
      const isManager =
        membership?.role === AgencyRole.OWNER ||
        membership?.role === AgencyRole.MANAGER;
      if (!isOwner && !isManager) {
        throw new ForbiddenException('Agency management only');
      }
      return {
        enabled: true,
        role: 'agency' as const,
        diamondUsdRate: rate,
        items: [] as any[],
        fullBalanceAllowed: true,
        targetRequired: false,
        hostsWithLockedStages: 0,
        totalHostsTracked: 0,
        message: 'اسحب عمولة الوكالة من رصيدك المتاح — التارجت خاص بالمضيفات فقط',
      };
    }

    if (!config.enabled || !config.stages?.length) {
      return {
        enabled: false,
        role: 'host' as const,
        diamondUsdRate: rate,
        items: [],
        fullBalanceAllowed: false,
        targetRequired: true,
        hostsWithLockedStages: 0,
        totalHostsTracked: 0,
        message: 'التارجت غير مفعّل',
      };
    }

    const me = await this.getMyTarget(userId);
    const current = (me.stages || []).find((s: any) => s.status === 'current');
    if (!current) {
      return {
        enabled: true,
        role: 'host' as const,
        diamondUsdRate: rate,
        period: me.period,
        periodKey: me.periodKey,
        progress: me.progress,
        items: [],
        fullBalanceAllowed: false,
        targetRequired: true,
        allStagesLocked: true,
        hostsWithLockedStages: 0,
        totalHostsTracked: 1,
        message: 'تم إكمال السلم — سيُعاد من الصفر بعد تسوية الدورة',
      };
    }

    const usd = Math.max(0, Number(current.hostSalaryUsd) || 0);
    const reached = !!current.reached || Number(me.progress) >= Number(current.threshold);
    const cashable = reached && usd >= HOST_TARGET_MIN_USD;
    const item = {
      id: String(current.id),
      stageIndex: Number(current.index) || 0,
      title: current.title || `مرحلة ${current.index}`,
      threshold: Number(current.threshold) || 0,
      hostSalaryUsd: usd,
      agentSalaryUsd: Math.max(0, Number(current.agentSalaryUsd) || 0),
      usd,
      diamonds: this.usdToDiamonds(usd, rate),
      status: cashable ? 'current' : 'current',
      cashable,
      hostsLocked: cashable ? 1 : 0,
      progress: Number(me.progress) || 0,
      remaining: Math.max(0, Number(current.threshold) - Number(me.progress)),
      cycle: me.cycle,
    };

    return {
      enabled: true,
      role: 'host' as const,
      diamondUsdRate: rate,
      period: me.period,
      periodKey: me.periodKey,
      progress: me.progress,
      items: [item],
      fullBalanceAllowed: cashable,
      targetRequired: true,
      allStagesLocked: cashable,
      hostsWithLockedStages: cashable ? 1 : 0,
      totalHostsTracked: 1,
      currentStageId: item.id,
      message: cashable
        ? `مرحلتك الحالية جاهزة للسحب — المرحلة ${item.stageIndex}`
        : `مرحلتك الحالية: ${item.title} — أكملي ${item.remaining} ألماس للفتح`,
    };
  }

  /**
   * After host submits platform withdraw for the current stage salary,
   * advance the ladder (and restart from zero after the last stage).
   */
  async markStageSalaryWithdrawn(userId: string, stageId: string) {
    const sid = String(stageId || '').trim();
    if (!userId || !sid || sid === 'full' || sid === 'full_balance') return null;
    const config = await this.getConfig();
    if (!config.enabled || !config.stages?.length) return null;
    const yearMonth = this.periodKey(new Date(), config.period);

    return this.dataSource.transaction(async (manager) => {
      let row = await manager.findOne(HostMonthlyProgress, {
        where: { userId, yearMonth },
        lock: { mode: 'pessimistic_write' },
      });
      if (!row) {
        row = manager.create(HostMonthlyProgress, {
          userId,
          yearMonth,
          progress: 0,
          claimedStageIds: [],
          withdrawnStageIds: [],
          cyclesCompleted: 0,
        });
      }
      const known = new Set(config.stages.map((s) => String(s.id)));
      if (!known.has(sid)) return null;
      const withdrawn = new Set(
        Array.isArray(row.withdrawnStageIds) ? row.withdrawnStageIds : [],
      );
      withdrawn.add(sid);

      const allPaid = config.stages.every((s) => withdrawn.has(String(s.id)));
      if (allPaid) {
        // Last stage cashed → restart ladder from zero for a new cycle.
        row.cyclesCompleted = Number(row.cyclesCompleted || 0) + 1;
        row.progress = 0;
        row.claimedStageIds = [];
        row.withdrawnStageIds = [];
      } else {
        row.withdrawnStageIds = [...withdrawn];
      }
      await manager.save(row);
      return {
        yearMonth,
        stageId: sid,
        cyclesCompleted: Number(row.cyclesCompleted || 0),
        restarted: allPaid,
        withdrawnStageIds: Array.isArray(row.withdrawnStageIds)
          ? row.withdrawnStageIds
          : [],
      };
    });
  }

  /**
   * Server gate for host target salary package only.
   * Agency commission withdraw never checks host target.
   */
  async assertTargetWithdrawAllowed(
    userId: string,
    role: 'host' | 'agency',
    agencyId: string | null | undefined,
    diamonds: number,
    stageId?: string | null,
  ) {
    if (role === 'agency') {
      // Agency board: no target ladder / no call metrics.
      return {
        enabled: true,
        role: 'agency' as const,
        fullBalanceAllowed: true,
        targetRequired: false,
        items: [],
        message: 'ok',
      };
    }

    const opts = await this.getWithdrawOptions(userId, role, agencyId);
    if (!opts.enabled) {
      throw new BadRequestException(opts.message || 'التارجت غير مفعّل');
    }
    if (!opts.fullBalanceAllowed) {
      throw new BadRequestException(
        opts.message || 'أكملي مرحلتك الحالية قبل السحب',
      );
    }
    const sid = String(stageId || '').trim();
    if (!sid || sid === 'full' || sid === 'full_balance') {
      throw new BadRequestException('اسحبي راتب مرحلتك الحالية فقط');
    }
    const items = Array.isArray(opts.items) ? opts.items : [];
    const byId = items.find((i: any) => String(i.id) === sid && i.cashable);
    if (!byId) {
      throw new BadRequestException('المرحلة المحددة غير جاهزة أو ليست مرحلتك الحالية');
    }
    const expected = Number(byId.diamonds) || 0;
    if (expected > 0 && Math.abs(expected - diamonds) > Math.max(5, expected * 0.02)) {
      throw new BadRequestException('مبلغ السحب لا يطابق راتب المرحلة الحالية');
    }
    return opts;
  }
}
