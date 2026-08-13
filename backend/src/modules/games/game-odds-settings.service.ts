import { Injectable, Logger, OnModuleInit } from '@nestjs/common';
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
export class GameOddsSettingsService implements OnModuleInit {
  private readonly log = new Logger(GameOddsSettingsService.name);

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
      next = sanitizeGameOdds({ ...current, ...body });
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
