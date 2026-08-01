import {
  Injectable,
  Optional,
  OnModuleInit,
  Logger,
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
import { RealtimeGateway } from '../realtime/realtime.gateway';
import { CosmeticsService } from '../cosmetics/cosmetics.service';
import { VipService } from '../vip/vip.service';

export type HostTargetStage = {
  id: string;
  threshold: number;
  rewardCoins: number;
  rewardDiamonds: number;
  title?: string;
  /** Optional mall cosmetic code (frame / entry / room card…). */
  rewardCosmeticCode?: string;
  /** Days for temporary cosmetic grant (default 7). */
  rewardCosmeticDays?: number;
  /** Optional VIP level to grant temporarily. */
  rewardVipLevel?: number;
  /** Days for temporary VIP (default 7). */
  rewardVipDays?: number;
};

export type HostMonthlyTargetConfig = {
  enabled: boolean;
  currency: 'diamonds' | 'gift_coins';
  stages: HostTargetStage[];
};

const DEFAULT_CONFIG: HostMonthlyTargetConfig = {
  enabled: true,
  currency: 'diamonds',
  stages: [
    { id: 'stage_1', threshold: 1000, rewardCoins: 50, rewardDiamonds: 0, title: 'مرحلة 1' },
    { id: 'stage_2', threshold: 5000, rewardCoins: 200, rewardDiamonds: 20, title: 'مرحلة 2' },
    { id: 'stage_3', threshold: 15000, rewardCoins: 500, rewardDiamonds: 80, title: 'مرحلة 3' },
    { id: 'stage_4', threshold: 35000, rewardCoins: 1000, rewardDiamonds: 200, title: 'مرحلة 4' },
    { id: 'stage_5', threshold: 75000, rewardCoins: 2500, rewardDiamonds: 500, title: 'مرحلة 5' },
  ],
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
      const parsed = JSON.parse(row.value) as Partial<HostMonthlyTargetConfig>;
      if (!Array.isArray(parsed.stages) || parsed.stages.length === 0) {
        await this.saveConfig({
          ...DEFAULT_CONFIG,
          enabled: parsed.enabled !== false,
          currency: parsed.currency === 'gift_coins' ? 'gift_coins' : 'diamonds',
        });
        this.log.log('Backfilled empty host_monthly_target stages');
      }
    } catch (err) {
      this.log.warn(
        `host_monthly_target seed skipped: ${
          err instanceof Error ? err.message : String(err)
        }`,
      );
    }
  }

  /** Prod may run with synchronize=false — add column if missing. */
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
  }

  yearMonth(date = new Date()) {
    const y = date.getUTCFullYear();
    const m = String(date.getUTCMonth() + 1).padStart(2, '0');
    return `${y}-${m}`;
  }

  private normalizeStage(s: Partial<HostTargetStage> | null | undefined, i: number): HostTargetStage {
    const cosmeticCode = String(s?.rewardCosmeticCode || '').trim();
    const vipLevel = Math.max(0, Math.floor(Number(s?.rewardVipLevel) || 0));
    return {
      id: String(s?.id || `stage_${i + 1}`),
      threshold: Math.max(0, Number(s?.threshold) || 0),
      rewardCoins: Math.max(0, Math.floor(Number(s?.rewardCoins) || 0)),
      rewardDiamonds: Math.max(0, Math.floor(Number(s?.rewardDiamonds) || 0)),
      title: s?.title ? String(s.title) : `مرحلة ${i + 1}`,
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
      return {
        enabled: !!parsed.enabled,
        currency: parsed.currency === 'gift_coins' ? 'gift_coins' : 'diamonds',
        stages: Array.isArray(parsed.stages)
          ? parsed.stages
              .map((s, i) => this.normalizeStage(s, i))
              .sort((a, b) => a.threshold - b.threshold)
          : [...DEFAULT_CONFIG.stages],
      };
    } catch {
      return { ...DEFAULT_CONFIG, stages: [...DEFAULT_CONFIG.stages] };
    }
  }

  async saveConfig(config: HostMonthlyTargetConfig) {
    const normalized: HostMonthlyTargetConfig = {
      enabled: !!config.enabled,
      currency: config.currency === 'gift_coins' ? 'gift_coins' : 'diamonds',
      stages: (config.stages || [])
        .map((s, i) => this.normalizeStage(s, i))
        .sort((a, b) => a.threshold - b.threshold),
    };
    let row = await this.settingsRepo.findOne({
      where: { key: 'host_monthly_target' },
    });
    if (!row) {
      row = this.settingsRepo.create({
        key: 'host_monthly_target',
        value: JSON.stringify(normalized),
        description: 'Host monthly target stages (separate from withdraw target)',
      });
    } else {
      row.value = JSON.stringify(normalized);
    }
    await this.settingsRepo.save(row);
    return normalized;
  }

  async getMyTarget(userId: string) {
    const config = await this.getConfig();
    const yearMonth = this.yearMonth();
    let row = await this.progressRepo.findOne({ where: { userId, yearMonth } });
    if (!row) {
      row = this.progressRepo.create({
        userId,
        yearMonth,
        progress: 0,
        claimedStageIds: [],
        cyclesCompleted: 0,
      });
      await this.progressRepo.save(row);
    }
    const progress = Number(row.progress || 0);
    const claimed = new Set(row.claimedStageIds || []);
    const cyclesCompleted = Math.max(0, Number(row.cyclesCompleted || 0));
    const stages = config.stages.map((s, i) => {
      const isClaimed = claimed.has(s.id);
      const reached = progress >= s.threshold;
      const prev = i > 0 ? config.stages[i - 1] : null;
      const prevDone = !prev || claimed.has(prev.id) || progress >= prev.threshold;
      let status: 'done' | 'current' | 'locked' = 'locked';
      if (isClaimed) status = 'done';
      else if (reached) status = 'done'; // reached but reward pending until claim path — treat as done UI
      else if (prevDone) status = 'current';
      return {
        ...s,
        index: i + 1,
        claimed: isClaimed,
        reached,
        status,
      };
    });
    // First unclaimed / unreached is the active stage.
    const next = stages.find((s) => !s.claimed && !s.reached);
    // Fix statuses: only one current.
    let foundCurrent = false;
    for (const s of stages) {
      if (s.claimed || s.reached) {
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
    return {
      enabled: config.enabled,
      currency: config.currency,
      yearMonth,
      progress,
      cyclesCompleted,
      cycle: cyclesCompleted + 1,
      stages,
      nextThreshold: next?.threshold ?? (config.stages[0]?.threshold ?? null),
      remaining: next
        ? Math.max(0, next.threshold - progress)
        : Math.max(0, (config.stages[0]?.threshold ?? 0) - progress),
      allCompleteThisCycle: !next && config.stages.length > 0 && progress > 0,
    };
  }

  /** Called when a host receives gift diamonds / coins this month. */
  async recordHostProgress(userId: string, amount: number) {
    if (!userId || !amount || amount <= 0) return null;
    const config = await this.getConfig();
    if (!config.enabled || !config.stages.length) return null;
    const maxThreshold = config.stages[config.stages.length - 1].threshold;
    if (maxThreshold <= 0) return null;

    const yearMonth = this.yearMonth();
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
          cyclesCompleted: 0,
        });
      }
      row.progress = Number(row.progress || 0) + Math.floor(amount);
      row.cyclesCompleted = Math.max(0, Number(row.cyclesCompleted || 0));
      const claimed = new Set(row.claimedStageIds || []);
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
                description: `Host monthly target: ${stage.title || stage.id}`,
                metadata: {
                  stageId: stage.id,
                  title: stage.title,
                  cycle: row.cyclesCompleted + 1,
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
                description: `Host monthly target: ${stage.title || stage.id}`,
                metadata: {
                  stageId: stage.id,
                  title: stage.title,
                  cycle: row.cyclesCompleted + 1,
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
        row.cyclesCompleted = Number(row.cyclesCompleted || 0) + 1;
        await awardDueStages();
      }

      row.claimedStageIds = [...claimed];
      await manager.save(wallet);
      await manager.save(row);

      if (newlyClaimed.length) {
        this.realtime?.emitToUser(userId, 'host_target:reward', {
          yearMonth,
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
}
