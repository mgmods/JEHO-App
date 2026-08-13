import {
  Injectable,
  Optional,
  OnModuleInit,
  Logger,
  BadRequestException,
  ForbiddenException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { DataSource, EntityManager, Repository } from 'typeorm';
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
import { ECONOMY } from '../../common/economy-config';

/**
 * CLEAN ECONOMY: the 28-stage salary ladder is gone. The monthly target is now
 * an OPTIONAL, simple 3-tier diamond goal that is OFF by default. Turn it on
 * from the dashboard and edit the tiers/bonus there.
 */
const HOST_TARGET_VERSION = 'clean-3tier-v1';

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

/** Simple, editable 3-tier diamond goal (thresholds in diamonds earned). */
const SIMPLE_STAGES: HostTargetStage[] = [
  { id: 'tier1', threshold: 200000, rewardCoins: 0, rewardDiamonds: 10000, title: 'المستوى الأول' },
  { id: 'tier2', threshold: 600000, rewardCoins: 0, rewardDiamonds: 40000, title: 'المستوى الثاني' },
  { id: 'tier3', threshold: 1500000, rewardCoins: 0, rewardDiamonds: 120000, title: 'المستوى الثالث' },
];

const DEFAULT_CONFIG: HostMonthlyTargetConfig = {
  // OFF by default — admin can enable it from the dashboard.
  enabled: false,
  currency: 'diamonds',
  period: 'monthly',
  stages: SIMPLE_STAGES.map((s) => ({ ...s })),
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
        // Seed the simple (disabled) default so the dashboard has something to edit.
        await this.saveConfig(DEFAULT_CONFIG);
        this.log.log('Seeded simple host_monthly_target (disabled by default)');
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
      this.log.error(
        `cyclesCompleted column ensure FAILED: ${
          err instanceof Error ? err.message : String(err)
        }`,
      );
    }
    try {
      await this.dataSource.query(`
        ALTER TABLE host_monthly_progress
        ADD COLUMN IF NOT EXISTS "withdrawnStageIds" text
      `);
      await this.dataSource.query(`
        UPDATE host_monthly_progress
        SET "withdrawnStageIds" = '[]'
        WHERE "withdrawnStageIds" IS NULL
      `);
    } catch (err) {
      this.log.error(
        `withdrawnStageIds column ensure FAILED: ${
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
    if (!row?.value) return { ...DEFAULT_CONFIG, stages: DEFAULT_CONFIG.stages.map((s) => ({ ...s })) };
    try {
      const parsed = JSON.parse(row.value) as Partial<HostMonthlyTargetConfig>;
      const stages =
        Array.isArray(parsed.stages) && parsed.stages.length
          ? parsed.stages
              .map((s, i) => this.normalizeStage(s, i))
              .sort((a, b) => a.threshold - b.threshold)
          : DEFAULT_CONFIG.stages.map((s) => ({ ...s }));
      return {
        enabled: !!parsed.enabled,
        currency: 'diamonds',
        period: this.normalizePeriod(parsed.period),
        stages,
      };
    } catch {
      return { ...DEFAULT_CONFIG, stages: DEFAULT_CONFIG.stages.map((s) => ({ ...s })) };
    }
  }

  async saveConfig(config: HostMonthlyTargetConfig) {
    let stages = (config.stages || [])
      .map((s, i) => this.normalizeStage(s, i))
      .sort((a, b) => a.threshold - b.threshold);
    if (stages.length === 0) {
      stages = SIMPLE_STAGES.map((s) => ({ ...s }));
    }

    const normalized: HostMonthlyTargetConfig = {
      enabled: !!config.enabled,
      currency: 'diamonds',
      period: this.normalizePeriod(config.period),
      stages,
    };
    const payload = {
      ...normalized,
      ladderVersion: HOST_TARGET_VERSION,
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
          } else if (Number(stage.hostSalaryUsd) > 0) {
            // Salary ladder: credit host USD step as diamonds so cashout can deduct them.
            const rate = Number(ECONOMY.diamondUsd || 0.00005);
            const salaryDiamonds = this.usdToDiamonds(
              Number(stage.hostSalaryUsd) || 0,
              rate > 0 ? rate : 0.00005,
            );
            if (salaryDiamonds > 0) {
              wallet.diamonds = Number(wallet.diamonds || 0) + salaryDiamonds;
              await manager.save(
                manager.create(WalletTransaction, {
                  userId,
                  type: TransactionType.LUCKY_REWARD,
                  currency: CurrencyType.DIAMONDS,
                  amount: salaryDiamonds,
                  balanceAfter: Number(wallet.diamonds),
                  referenceType: 'host_monthly_target',
                  referenceId: `${yearMonth}:c${row.cyclesCompleted + 1}:${stage.id}:salary`,
                  description: `راتب مرحلة التارجت: ${stage.title || stage.id}`,
                  metadata: {
                    stageId: stage.id,
                    title: stage.title,
                    hostSalaryUsd: stage.hostSalaryUsd,
                    cycle: row.cyclesCompleted + 1,
                    period: config.period,
                    periodKey: yearMonth,
                    kind: 'stage_salary_credit',
                  },
                }),
              );
            }
          }
          // Agency ladder cut (e.g. stage1 host $10 + agent $2) → owner wallet.
          if (Number(stage.agentSalaryUsd) > 0) {
            await this.creditAgencyStageBonus(
              manager,
              userId,
              stage,
              yearMonth,
              config.period,
              Number(row.cyclesCompleted || 0) + 1,
            );
          }
        }
      };

      await awardDueStages();

      // Overflow recycle (wallet-reward ladder only). NEVER clear salary withdrawn ids —
      // that ladder advances only via markStageSalaryWithdrawn / cashout.
      // Clearing withdrawn here re-opened paid stages for hosts (prod incidents).
      const hasWalletReward = config.stages.some(
        (s) => Number(s.rewardCoins) > 0 || Number(s.rewardDiamonds) > 0,
      );
      if (hasWalletReward) {
        let safety = 0;
        while (safety++ < 40) {
          const allClaimed = config.stages.every((s) => claimed.has(s.id));
          if (!allClaimed) break;
          if (row.progress < maxThreshold) break;
          row.progress = Number(row.progress) - maxThreshold;
          claimed.clear();
          // Keep `withdrawn` intact — salary history must not reset on gift overflow.
          row.cyclesCompleted = Number(row.cyclesCompleted || 0) + 1;
          await awardDueStages();
        }
      } else if (row.progress >= maxThreshold) {
        // Salary ladder: keep progress as lifetime bucket for the period (no auto recycle
        // that would re-open already cashed stages). Cap display at max + small overflow.
        // Progress stays; withdraw gate is withdrawnStageIds.
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
        yearMonth,
        period: config.period,
      };
    });

    // Notify agency owners that stage agent cuts landed in their wallet.
    for (const stage of result.newlyClaimed || []) {
      if (!(Number(stage.agentSalaryUsd) > 0)) continue;
      try {
        const membership = await this.membersRepo.findOne({
          where: {
            userId,
            status: AgencyMemberStatus.ACTIVE,
            isActive: true,
          },
          order: { joinedAt: 'DESC' },
        });
        if (!membership?.agencyId) continue;
        const agency = await this.agencyRepo.findOne({
          where: { id: membership.agencyId },
        });
        const ownerId = agency?.ownerId ? String(agency.ownerId) : '';
        if (!ownerId || ownerId === userId) continue;
        const rate = Number(ECONOMY.diamondUsd || 0.00005);
        const diamonds = this.usdToDiamonds(
          Number(stage.agentSalaryUsd) || 0,
          rate > 0 ? rate : 0.00005,
        );
        this.realtime?.emitToUser(ownerId, 'host_target:agent_salary', {
          hostUserId: userId,
          agencyId: membership.agencyId,
          stageId: stage.id,
          title: stage.title,
          agentSalaryUsd: Number(stage.agentSalaryUsd) || 0,
          diamonds,
          period: result.period,
          periodKey: result.yearMonth,
          cycle: stage.cycle,
          at: new Date().toISOString(),
        });
      } catch (err) {
        this.log.warn(
          `host target agent notify failed: ${
            err instanceof Error ? err.message : String(err)
          }`,
        );
      }
    }

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
    const n = Number(ECONOMY.diamondUsd || 0.00005);
    return Number.isFinite(n) && n > 0 ? n : 0.00005;
  }

  private usdToDiamonds(usd: number, rate: number): number {
    if (usd <= 0 || rate <= 0) return 0;
    return Math.max(0, Math.round(usd / rate));
  }

  /**
   * When a host locks a salary-ladder stage, credit the agency owner's
   * agentSalaryUsd cut into their diamond wallet (same pool they withdraw from).
   * Idempotent via referenceId on wallet_transactions.
   */
  private async creditAgencyStageBonus(
    manager: EntityManager,
    hostUserId: string,
    stage: HostTargetStage,
    yearMonth: string,
    period: HostTargetPeriod,
    cycle: number,
  ) {
    const agentUsd = Math.max(0, Number(stage.agentSalaryUsd) || 0);
    if (agentUsd <= 0) return null;
    const membership = await manager.findOne(AgencyMember, {
      where: {
        userId: hostUserId,
        status: AgencyMemberStatus.ACTIVE,
        isActive: true,
      },
      order: { joinedAt: 'DESC' },
    });
    if (!membership?.agencyId) return null;
    const agency = await manager.findOne(Agency, {
      where: { id: membership.agencyId },
    });
    const ownerId = agency?.ownerId ? String(agency.ownerId) : '';
    if (!ownerId || ownerId === hostUserId) return null;

    const rate = Number(ECONOMY.diamondUsd || 0.00005);
    const diamonds = this.usdToDiamonds(agentUsd, rate > 0 ? rate : 0.00005);
    if (diamonds <= 0) return null;

    const referenceId = `${yearMonth}:c${cycle}:${stage.id}:agent:${hostUserId}`;
    const existing = await manager.findOne(WalletTransaction, {
      where: {
        userId: ownerId,
        referenceType: 'host_target_agent_salary',
        referenceId,
      },
    });
    if (existing) return { ownerId, diamonds, alreadyCredited: true };

    let ownerWallet = await manager.findOne(Wallet, {
      where: { userId: ownerId },
      lock: { mode: 'pessimistic_write' },
    });
    if (!ownerWallet) {
      ownerWallet = manager.create(Wallet, { userId: ownerId });
    }
    ownerWallet.diamonds = Number(ownerWallet.diamonds || 0) + diamonds;
    await manager.save(ownerWallet);
    await manager.save(
      manager.create(WalletTransaction, {
        userId: ownerId,
        type: TransactionType.LUCKY_REWARD,
        currency: CurrencyType.DIAMONDS,
        amount: diamonds,
        balanceAfter: Number(ownerWallet.diamonds),
        referenceType: 'host_target_agent_salary',
        referenceId,
        description: `عمولة تارجت مضيفة · ${stage.title || stage.id}`,
        metadata: {
          stageId: stage.id,
          title: stage.title,
          agentSalaryUsd: agentUsd,
          hostUserId,
          agencyId: membership.agencyId,
          cycle,
          period,
          periodKey: yearMonth,
          kind: 'stage_agent_salary_credit',
        },
      }),
    );
    return { ownerId, diamonds, agentUsd, agencyId: membership.agencyId };
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
        message: 'اسحب عمولة الوكالة من رصيدك (هدايا + عمولة مراحل التارجت عند إقفال المضيفة). التارجت خاص بالمضيفات فقط',
      };
    }

    if (!config.enabled || !config.stages?.length) {
      // CLEAN ECONOMY: when the (optional) target ladder is off, hosts withdraw
      // by balance packages + global minWithdraw — same rules as agency.
      return {
        enabled: false,
        role: 'host' as const,
        diamondUsdRate: rate,
        items: [],
        fullBalanceAllowed: true,
        targetRequired: false,
        hostsWithLockedStages: 0,
        totalHostsTracked: 0,
        message: 'التارجت غير مفعّل — اسحبي حسب الحد الأدنى لرصيدك',
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
        periodLabel: me.periodLabel,
        progress: me.progress,
        cycle: me.cycle,
        stages: me.stages,
        totalStages: (me.stages || []).length,
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
    // Tier cashable once diamond threshold reached (150k, 200k, … — not global minWithdraw).
    const cashable = reached;
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
      periodLabel: me.periodLabel,
      progress: me.progress,
      cycle: me.cycle,
      stages: me.stages,
      totalStages: (me.stages || []).length,
      items: [item],
      fullBalanceAllowed: cashable,
      targetRequired: true,
      allStagesLocked: cashable,
      hostsWithLockedStages: cashable ? 1 : 0,
      totalHostsTracked: 1,
      currentStageId: item.id,
      message: cashable
        ? `مرحلتك ${item.stageIndex} جاهزة — اسحبي ${usd > 0 ? `$${usd.toFixed(0)}` : 'راتب المرحلة'} (${item.diamonds.toLocaleString()} ◆)`
        : `مرحلتك ${item.stageIndex}: ${item.title} — أكملي ${item.remaining.toLocaleString()} ◆ (${item.threshold.toLocaleString()} هدف المرحلة)`,
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
      if (!known.has(sid)) {
        this.log.warn(
          `markStageSalaryWithdrawn: unknown stageId=${sid} user=${userId} period=${yearMonth}`,
        );
        return null;
      }
      const withdrawn = new Set(
        Array.isArray(row.withdrawnStageIds) ? row.withdrawnStageIds : [],
      );
      if (withdrawn.has(sid)) {
        // Idempotent — do not re-open or double-count.
        return {
          yearMonth,
          stageId: sid,
          cyclesCompleted: Number(row.cyclesCompleted || 0),
          restarted: false,
          alreadyMarked: true,
          withdrawnStageIds: [...withdrawn],
        };
      }
      // Only allow marking the next unpaid stage in ladder order.
      const nextUnpaid = config.stages.find((s) => !withdrawn.has(String(s.id)));
      if (!nextUnpaid || String(nextUnpaid.id) !== sid) {
        this.log.warn(
          `markStageSalaryWithdrawn: stage ${sid} is not current unpaid for user=${userId}`,
        );
        return null;
      }
      const stageMeta = config.stages.find((s) => String(s.id) === sid);
      if (stageMeta && Number(row.progress || 0) < Number(stageMeta.threshold || 0)) {
        this.log.warn(
          `markStageSalaryWithdrawn: progress ${row.progress} < threshold ${stageMeta.threshold} for ${sid}`,
        );
        return null;
      }
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
    // Target ladder off → min-amount packages only (half/full), no stage gate.
    if (!opts.enabled && opts.fullBalanceAllowed) {
      return { ...opts, message: 'ok' };
    }
    if (!opts.enabled) {
      throw new BadRequestException(opts.message || 'التارجت غير مفعّل');
    }
    if (!opts.fullBalanceAllowed) {
      throw new BadRequestException(
        opts.message || 'أكملي مرحلتك الحالية قبل السحب',
      );
    }
    const sid = String(stageId || '').trim();
    if (!sid || sid === 'full' || sid === 'full_balance' || sid === 'half') {
      // Balance packages (when target off) already allowed above; on target
      // mode only stage ids may be submitted.
      if (opts.targetRequired === false) {
        return { ...opts, message: 'ok' };
      }
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
