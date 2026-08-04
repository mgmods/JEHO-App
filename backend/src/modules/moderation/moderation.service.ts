import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { AppSetting } from '../../database/entities/app-setting.entity';

export type ModerationConfig = {
  enabled: boolean;
  confidence: number;
  consecutiveHits: number;
  warnStrikes: number;
  muteStrikes: number;
  streamBanStrikes: number;
  permBanStrikes: number;
  streamBanHours: number;
  scanIntervalSec: number;
  cooldownSec: number;
};

@Injectable()
export class ModerationService {
  constructor(
    @InjectRepository(AppSetting) private readonly settingsRepo: Repository<AppSetting>,
  ) {}

  /** Live-video NSFW moderation removed; clients receive a disabled config. */
  async getConfig(): Promise<ModerationConfig> {
    return {
      enabled: false,
      confidence: 0.78,
      consecutiveHits: 3,
      warnStrikes: 1,
      muteStrikes: 2,
      streamBanStrikes: 3,
      permBanStrikes: 5,
      streamBanHours: 24,
      scanIntervalSec: 5,
      cooldownSec: 45,
    };
  }
}
