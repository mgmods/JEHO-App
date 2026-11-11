import { Injectable, Logger, OnModuleDestroy, OnModuleInit } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { AppSetting } from '../../database/entities/app-setting.entity';
import {
  DEFAULT_GAME_ODDS,
  GAME_ODDS_SETTING_KEY,
  GameOddsConfig,
  applyGameOddsPreset,
  gameOddsClientPayload,
  sanitizeGameOdds,
} from './game-odds-config';
import { setGameOdds } from './game-odds-runtime';

@Injectable()
export class GameOddsSettingsService implements OnModuleInit, OnModuleDestroy {
  private readonly log = new Logger(GameOddsSettingsService.name);
  private refreshTimer?: ReturnType<typeof setInterval>;

  constructor(
    @InjectRepository(AppSetting)
    private readonly settingsRepo: Repository<AppSetting>,
  ) {}

  async onModuleInit() {
    try {
      await this.reload();
    } catch (e) {
      this.log.warn(
        `game odds load failed, using defaults: ${
          e instanceof Error ? e.message : String(e)
        }`,
      );
      setGameOdds(DEFAULT_GAME_ODDS);
    }
    // Keep each running backend instance in sync with dashboard changes stored in AppSetting.
    this.refreshTimer = setInterval(() => {
      void this.reload().catch((e) => this.log.warn(`game settings refresh failed: ${e instanceof Error ? e.message : String(e)}`));
    }, 5000);
    this.refreshTimer.unref?.();
  }

  onModuleDestroy() {
    if (this.refreshTimer) clearInterval(this.refreshTimer);
  }

  async reload(): Promise<GameOddsConfig> {
    const row = await this.settingsRepo.findOne({
      where: { key: GAME_ODDS_SETTING_KEY },
    });
    let cfg = sanitizeGameOdds(DEFAULT_GAME_ODDS);
    if (row?.value) {
      try {
        cfg = sanitizeGameOdds(JSON.parse(row.value));
      } catch {
        cfg = sanitizeGameOdds(row.value);
      }
    } else {
      // Persist defaults so dashboard has a row immediately.
      await this.settingsRepo.save(
        this.settingsRepo.create({
          key: GAME_ODDS_SETTING_KEY,
          value: JSON.stringify(cfg),
          description: 'House odds / RTP for all coin games',
        }),
      );
    }
    setGameOdds(cfg);
    return cfg;
  }

  async getAdmin() {
    const cfg = await this.reload();
    return gameOddsClientPayload(cfg);
  }

  async patchAdmin(body: Record<string, unknown>) {
    const current = await this.reload();
    let next: GameOddsConfig;
    if (body?.preset && typeof body.preset === 'string') {
      next = applyGameOddsPreset(body.preset, current);
    } else {
      const incomingRates = body?.gameWinRates && typeof body.gameWinRates === 'object' && !Array.isArray(body.gameWinRates)
        ? body.gameWinRates as Record<string, unknown>
        : {};
      const mergedRates: Record<string, number> = { ...current.gameWinRates };
      for (const [id, value] of Object.entries(incomingRates)) {
        const key = String(id).trim().toLowerCase();
        const rate = Number(value);
        if (key && Number.isFinite(rate)) mergedRates[key] = rate;
      }
      const clearRates = Array.isArray(body?.clearGameWinRates)
        ? body.clearGameWinRates.map((id) => String(id || '').trim().toLowerCase()).filter(Boolean)
        : [];
      clearRates.forEach((id) => delete mergedRates[id]);
      const incomingBetSettings = body?.gameBetSettings && typeof body.gameBetSettings === 'object' && !Array.isArray(body.gameBetSettings)
        ? body.gameBetSettings as Record<string, unknown>
        : {};
      const mergedBetSettings: Record<string, unknown> = { ...current.gameBetSettings };
      for (const [id, value] of Object.entries(incomingBetSettings)) {
        const key = String(id).trim().toLowerCase();
        if (!/^[a-z0-9][a-z0-9-]{0,63}$/.test(key) || !value || typeof value !== 'object') continue;
        mergedBetSettings[key] = { ...(mergedBetSettings[key] as Record<string, unknown> || {}), ...(value as Record<string, unknown>) };
      }
      const patch = { ...body, gameWinRates: mergedRates, gameBetSettings: mergedBetSettings };
      delete (patch as Record<string, unknown>).clearGameWinRates;
      next = sanitizeGameOdds({ ...current, ...patch });
    }
    next.version = (current.version || 1) + 1;
    next.updatedAt = new Date().toISOString();

    let row = await this.settingsRepo.findOne({
      where: { key: GAME_ODDS_SETTING_KEY },
    });
    if (!row) {
      row = this.settingsRepo.create({
        key: GAME_ODDS_SETTING_KEY,
        value: JSON.stringify(next),
        description: 'House odds / RTP for all coin games',
      });
    } else {
      row.value = JSON.stringify(next);
    }
    await this.settingsRepo.save(row);
    setGameOdds(next);
    this.log.log(
      `game odds updated rtp=${next.playerRtp} spinHit=${next.spinHitRate} crashRake=${next.crashCashoutRake}`,
    );
    return gameOddsClientPayload(next);
  }
}
