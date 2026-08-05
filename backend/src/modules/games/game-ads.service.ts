import { BadRequestException, Injectable, Logger, OnModuleInit, Optional } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { WalletService } from '../wallet/wallet.service';
import { TasksService } from '../tasks/tasks.service';

export const GAMES_ADMOB_KEY = 'games.admob';

export type GamesAdmobConfig = {
  adsEnabled: boolean;
  testMode: boolean;
  appId: string;
  bannerEnabled: boolean;
  bannerId: string;
  interstitialEnabled: boolean;
  interstitialId: string;
  /** Show interstitial every N game opens (1 = every open). */
  interstitialEveryNOpens: number;
  /** Also show interstitial when closing a game. */
  interstitialOnClose: boolean;
  rewardedEnabled: boolean;
  rewardedId: string;
  rewardedCoins: number;
  /** Max successful rewarded claims per user per UTC day. */
  rewardedDailyCap: number;
};

const DEFAULT_ADMOB: GamesAdmobConfig = {
  adsEnabled: false,
  testMode: false,
  appId: '',
  bannerEnabled: false,
  bannerId: '',
  interstitialEnabled: true,
  interstitialId: '',
  interstitialEveryNOpens: 3,
  interstitialOnClose: false,
  rewardedEnabled: true,
  rewardedId: '',
  rewardedCoins: 10,
  rewardedDailyCap: 5,
};

@Injectable()
export class GameAdsService implements OnModuleInit {
  private readonly logger = new Logger(GameAdsService.name);

  constructor(
    @InjectRepository(AppSetting)
    private readonly settingsRepo: Repository<AppSetting>,
    private readonly walletService: WalletService,
    @Optional() private readonly tasksService?: TasksService,
  ) {}

  async onModuleInit() {
    const existing = await this.settingsRepo.findOne({
      where: { key: GAMES_ADMOB_KEY },
    });
    if (!existing) {
      await this.settingsRepo.save(
        this.settingsRepo.create({
          key: GAMES_ADMOB_KEY,
          value: JSON.stringify(DEFAULT_ADMOB),
        }),
      );
      this.logger.log('Initialized games.admob defaults');
    }
  }

  private async readConfig(): Promise<GamesAdmobConfig> {
    const row = await this.settingsRepo.findOne({ where: { key: GAMES_ADMOB_KEY } });
    if (!row?.value) return { ...DEFAULT_ADMOB };
    try {
      const parsed = JSON.parse(row.value) as Partial<GamesAdmobConfig>;
      return {
        ...DEFAULT_ADMOB,
        ...parsed,
        testMode: false,
        interstitialEveryNOpens: Math.max(
          1,
          Math.floor(Number(parsed.interstitialEveryNOpens) || DEFAULT_ADMOB.interstitialEveryNOpens),
        ),
        rewardedCoins: Math.max(0, Math.floor(Number(parsed.rewardedCoins) || 0)),
        rewardedDailyCap: Math.max(0, Math.floor(Number(parsed.rewardedDailyCap) || 0)),
      };
    } catch {
      return { ...DEFAULT_ADMOB };
    }
  }

  /** Client-facing ads config (no secrets). */
  async getClientConfig() {
    const cfg = await this.readConfig();
    return {
      adsEnabled: !!cfg.adsEnabled,
      bannerEnabled: !!cfg.bannerEnabled && !!cfg.adsEnabled,
      bannerId: cfg.bannerId || '',
      interstitialEnabled: !!cfg.interstitialEnabled && !!cfg.adsEnabled,
      interstitialId: cfg.interstitialId || '',
      interstitialEveryNOpens: cfg.interstitialEveryNOpens,
      interstitialOnClose: !!cfg.interstitialOnClose && !!cfg.adsEnabled,
      rewardedEnabled: !!cfg.rewardedEnabled && !!cfg.adsEnabled,
      rewardedId: cfg.rewardedId || '',
      rewardedCoins: cfg.rewardedCoins,
      rewardedDailyCap: cfg.rewardedDailyCap,
      appId: cfg.appId || '',
    };
  }

  async adminGetSettings() {
    return this.readConfig();
  }

  async adminPatchSettings(body: Partial<GamesAdmobConfig>) {
    const current = await this.readConfig();
    const merged: GamesAdmobConfig = {
      ...current,
      ...body,
      testMode: false,
      interstitialEveryNOpens: Math.max(
        1,
        Math.floor(
          Number(body.interstitialEveryNOpens ?? current.interstitialEveryNOpens) || 1,
        ),
      ),
      rewardedCoins: Math.max(
        0,
        Math.floor(Number(body.rewardedCoins ?? current.rewardedCoins) || 0),
      ),
      rewardedDailyCap: Math.max(
        0,
        Math.floor(Number(body.rewardedDailyCap ?? current.rewardedDailyCap) || 0),
      ),
    };
    let row = await this.settingsRepo.findOne({ where: { key: GAMES_ADMOB_KEY } });
    if (!row) row = this.settingsRepo.create({ key: GAMES_ADMOB_KEY });
    row.value = JSON.stringify(merged);
    await this.settingsRepo.save(row);
    return this.adminGetSettings();
  }

  async claimRewarded(userId: string) {
    const cfg = await this.readConfig();
    if (!cfg.adsEnabled || !cfg.rewardedEnabled) {
      throw new BadRequestException('Game rewarded ads are disabled');
    }
    if (cfg.rewardedCoins <= 0) {
      throw new BadRequestException('Rewarded coins not configured');
    }
    const result = await this.walletService.creditGameAdReward(
      userId,
      cfg.rewardedCoins,
      cfg.rewardedDailyCap,
    );
    // Count a completed watch toward daily AdMob tasks when coins were granted.
    if (result?.credited === true) {
      try {
        await this.tasksService?.recordProgress(userId, 'ad', 1);
      } catch (err) {
        this.logger.warn(
          `task ad progress: ${err instanceof Error ? err.message : String(err)}`,
        );
      }
    }
    return result;
  }
}
