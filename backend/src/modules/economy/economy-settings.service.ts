import { Injectable, Logger, OnModuleInit } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { AppSetting } from '../../database/entities/app-setting.entity';
import {
  ECONOMY,
  ECONOMY_DEFAULTS,
  EconomyConfigShape,
  applyEconomyPatch,
} from '../../common/economy-config';

/** Single storage row that holds the whole economy JSON. */
const ECONOMY_SETTING_KEY = 'economy.config.v1';

/**
 * Loads the runtime economy config from `app_settings` and writes updates back.
 * The in-memory cache lives in `ECONOMY` (imported by pricing-catalog and all
 * services), so dashboard edits take effect on the very next request — no
 * reboot needed.
 */
@Injectable()
export class EconomySettingsService implements OnModuleInit {
  private readonly log = new Logger(EconomySettingsService.name);

  constructor(
    @InjectRepository(AppSetting)
    private readonly settingsRepo: Repository<AppSetting>,
  ) {}

  async onModuleInit() {
    try {
      const row = await this.settingsRepo.findOne({ where: { key: ECONOMY_SETTING_KEY } });
      const legacyPlatformRow = await this.settingsRepo.findOne({
        where: { key: 'economy.platform_commission_percent' },
      });
      const legacyPlatformPercent = Number(legacyPlatformRow?.value);
      if (row?.value) {
        const parsed = this.safeParse(row.value);
        if (parsed) {
          applyEconomyPatch(parsed);
          // Preserve an existing private platform percentage from the legacy setting
          // when migrating an older economy JSON that did not contain this field.
          if (
            parsed.platformCommissionPercent === undefined &&
            legacyPlatformRow &&
            Number.isFinite(legacyPlatformPercent)
          ) {
            applyEconomyPatch({ platformCommissionPercent: legacyPlatformPercent });
            await this.persistCurrent('Migrate legacy private platform commission');
          }
          // One-time migration: the new payout contract guarantees that a regular
          // gift mints its full coin value in host diamonds. Preserve the admin's
          // lucky ratio and agency commission while migrating old 70/30 split configs.
          const modelVersionKey = 'economy.gift_payout_model_version';
          const modelVersion = await this.settingsRepo.findOne({ where: { key: modelVersionKey } });
          if (!modelVersion?.value || Number(modelVersion.value) < 2) {
            ECONOMY.giftDiamondRatio = 1;
            ECONOMY.defaultGiftSplit = {
              platformPercent: 0,
              hostPercent: 100,
              agencyOwnerPercent: ECONOMY.defaultGiftSplit.agencyOwnerPercent,
            };
            await this.persistCurrent('Migrate to full host gift payout v2');
            const versionRow = modelVersion || this.settingsRepo.create({
              key: modelVersionKey,
              value: '2',
              description: 'Gift payout model migration version',
            });
            versionRow.value = '2';
            await this.settingsRepo.save(versionRow);
            this.log.log('Migrated gift payout model: full host diamonds + additive agency commission');
          }
          this.log.log(
            `Loaded economy settings from DB (ratio=${ECONOMY.giftDiamondRatio}, cap=${ECONOMY.maxDiamondsPerUnit})`,
          );
          return;
        }
      }
      // Preserve the legacy platform percentage when the main economy row is absent.
      if (legacyPlatformRow && Number.isFinite(legacyPlatformPercent)) {
        applyEconomyPatch({ platformCommissionPercent: legacyPlatformPercent });
      }
      // Seed the row with current defaults so admin sees editable values immediately.
      await this.persistCurrent('First boot seed');
      this.log.log('Seeded economy settings row with defaults');
    } catch (err) {
      this.log.warn(
        `Failed to load economy settings, using defaults: ${
          err instanceof Error ? err.message : String(err)
        }`,
      );
    }
  }

  /** Full snapshot for admin dashboard display. */
  get(): EconomyConfigShape {
    return {
      coinsPerUsd: ECONOMY.coinsPerUsd,
      diamondUsd: ECONOMY.diamondUsd,
      giftDiamondRatio: ECONOMY.giftDiamondRatio,
      luckyGiftDiamondRatio: ECONOMY.luckyGiftDiamondRatio,
      maxDiamondsPerUnit: ECONOMY.maxDiamondsPerUnit,
      luckyGiftMaxMultiplier: ECONOMY.luckyGiftMaxMultiplier,
      luckyGiftTargetEv: ECONOMY.luckyGiftTargetEv,
      minWithdrawDiamonds: ECONOMY.minWithdrawDiamonds,
      showDiamondValueInApp: ECONOMY.showDiamondValueInApp,
      defaultGiftSplit: { ...ECONOMY.defaultGiftSplit },
    };
  }

  /** Read-only defaults so the admin UI can show a "Reset" option. */
  defaults(): EconomyConfigShape {
    return {
      coinsPerUsd: ECONOMY_DEFAULTS.coinsPerUsd,
      diamondUsd: ECONOMY_DEFAULTS.diamondUsd,
      giftDiamondRatio: ECONOMY_DEFAULTS.giftDiamondRatio,
      luckyGiftDiamondRatio: ECONOMY_DEFAULTS.luckyGiftDiamondRatio,
      platformCommissionPercent: ECONOMY_DEFAULTS.platformCommissionPercent,
      maxDiamondsPerUnit: ECONOMY_DEFAULTS.maxDiamondsPerUnit,
      luckyGiftMaxMultiplier: ECONOMY_DEFAULTS.luckyGiftMaxMultiplier,
      luckyGiftTargetEv: ECONOMY_DEFAULTS.luckyGiftTargetEv,
      minWithdrawDiamonds: ECONOMY_DEFAULTS.minWithdrawDiamonds,
      showDiamondValueInApp: ECONOMY_DEFAULTS.showDiamondValueInApp,
      defaultGiftSplit: { ...ECONOMY_DEFAULTS.defaultGiftSplit },
    };
  }

  /** Apply an admin patch, validate/clamp, then persist. Returns the resulting live config. */
  async update(patch: Partial<EconomyConfigShape>): Promise<EconomyConfigShape> {
    applyEconomyPatch(patch);
    await this.persistCurrent('Admin update');
    return this.get();
  }

  /** Reset to hardcoded fallback defaults (still stored in DB row so it's traceable). */
  async reset(): Promise<EconomyConfigShape> {
    applyEconomyPatch(this.defaults());
    await this.persistCurrent('Admin reset to defaults');
    return this.get();
  }

  private async persistCurrent(reason: string) {
    const snapshot = this.get();
    const value = JSON.stringify(snapshot);
    let row = await this.settingsRepo.findOne({ where: { key: ECONOMY_SETTING_KEY } });
    if (!row) {
      row = this.settingsRepo.create({
        key: ECONOMY_SETTING_KEY,
        value,
        description: `Economy config (live). Last change: ${reason}`,
      });
    } else {
      row.value = value;
      row.description = `Economy config (live). Last change: ${reason}`;
    }
    await this.settingsRepo.save(row);
  }

  private safeParse(raw: string): Partial<EconomyConfigShape> | null {
    try {
      const parsed = JSON.parse(raw);
      return parsed && typeof parsed === 'object' ? (parsed as Partial<EconomyConfigShape>) : null;
    } catch {
      this.log.warn('Ignoring invalid economy config JSON in app_settings');
      return null;
    }
  }
}
