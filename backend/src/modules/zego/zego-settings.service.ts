import {
  BadRequestException,
  Injectable,
  Logger,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { decryptSecret, encryptSecret, SecretBoxError } from '../../common/crypto/secret-box';
import { PatchZegoSettingsDto } from './dto/patch-zego-settings.dto';

export const ZEGO_CONFIG_KEY = 'zego_cloud_config';

export interface StoredZegoConfig {
  appId?: string | null;
  appSignEnc?: string | null;
  serverSecretEnc?: string | null;
  wsUrl?: string | null;
  wsUrlBak?: string | null;
  updatedAt?: string;
}

export interface ResolvedZegoConfig {
  appId: number;
  appSign: string;
  serverSecret: string;
  wsUrl: string;
  wsUrlBak: string;
  source: 'db' | 'env' | 'mixed' | 'none';
}

export interface MaskedZegoSettings {
  appId: string;
  appIdConfigured: boolean;
  appSignConfigured: boolean;
  serverSecretConfigured: boolean;
  appSignHint: string | null;
  serverSecretHint: string | null;
  wsUrl: string;
  wsUrlBak: string;
  updatedAt: string | null;
  configSource: 'db' | 'env' | 'mixed' | 'none';
}

@Injectable()
export class ZegoSettingsService {
  private readonly logger = new Logger(ZegoSettingsService.name);
  private cache: ResolvedZegoConfig | null = null;

  constructor(
    private readonly configService: ConfigService,
    @InjectRepository(AppSetting)
    private readonly settingsRepo: Repository<AppSetting>,
  ) {}

  async getMaskedSettings(): Promise<MaskedZegoSettings> {
    const resolved = await this.resolveConfig();
    const stored = await this.loadStoredConfig();
    return {
      appId: resolved.appId ? String(resolved.appId) : '',
      appIdConfigured: resolved.appId > 0,
      appSignConfigured: Boolean(resolved.appSign),
      serverSecretConfigured: Boolean(resolved.serverSecret),
      appSignHint: resolved.appSign ? resolved.appSign.slice(-6) : null,
      serverSecretHint: resolved.serverSecret ? resolved.serverSecret.slice(-4) : null,
      wsUrl: resolved.wsUrl || '',
      wsUrlBak: resolved.wsUrlBak || '',
      updatedAt: stored?.updatedAt || null,
      configSource: resolved.source,
    };
  }

  async revealCredentials(): Promise<{
    appId: string;
    appSign: string;
    serverSecret: string;
    wsUrl: string;
    wsUrlBak: string;
  }> {
    const resolved = await this.resolveConfig();
    return {
      appId: resolved.appId ? String(resolved.appId) : '',
      appSign: resolved.appSign || '',
      serverSecret: resolved.serverSecret || '',
      wsUrl: resolved.wsUrl || '',
      wsUrlBak: resolved.wsUrlBak || '',
    };
  }

  /** Public values safe for the mobile app (AppID only — never AppSign / ServerSecret). */
  async getPublicClientConfig(): Promise<{
    appId: number;
    appSign: string;
    wsUrl: string;
    wsUrlBak: string;
  }> {
    const resolved = await this.resolveConfig();
    return {
      appId: resolved.appId,
      // Token auth only. AppSign must stay server-side.
      appSign: '',
      wsUrl: resolved.wsUrl,
      wsUrlBak: resolved.wsUrlBak,
    };
  }

  async updateSettings(dto: PatchZegoSettingsDto): Promise<MaskedZegoSettings> {
    const stored = (await this.loadStoredConfig()) || {};
    const encryptionKey = this.encryptionKey();

    if (dto.appId !== undefined) {
      const trimmed = String(dto.appId || '').trim();
      if (trimmed && !/^\d+$/.test(trimmed)) {
        throw new BadRequestException('ZEGO AppID must be numeric');
      }
      stored.appId = trimmed || null;
    }

    if (dto.clearAppSign) {
      stored.appSignEnc = null;
    } else if (dto.appSign !== undefined && String(dto.appSign).trim()) {
      try {
        stored.appSignEnc = encryptSecret(String(dto.appSign).trim(), encryptionKey);
      } catch (error) {
        this.rethrowCrypto(error);
      }
    }

    if (dto.clearServerSecret) {
      stored.serverSecretEnc = null;
    } else if (dto.serverSecret !== undefined && String(dto.serverSecret).trim()) {
      const secret = String(dto.serverSecret).trim();
      if (secret.length !== 32) {
        throw new BadRequestException('ZEGO ServerSecret must be exactly 32 characters');
      }
      try {
        stored.serverSecretEnc = encryptSecret(secret, encryptionKey);
      } catch (error) {
        this.rethrowCrypto(error);
      }
    }

    if (dto.wsUrl !== undefined) stored.wsUrl = String(dto.wsUrl || '').trim() || null;
    if (dto.wsUrlBak !== undefined) stored.wsUrlBak = String(dto.wsUrlBak || '').trim() || null;
    stored.updatedAt = new Date().toISOString();

    let row = await this.settingsRepo.findOne({ where: { key: ZEGO_CONFIG_KEY } });
    if (!row) {
      row = this.settingsRepo.create({
        key: ZEGO_CONFIG_KEY,
        value: JSON.stringify(stored),
        description: 'ZEGOCLOUD credentials (secrets encrypted)',
      });
    } else {
      row.value = JSON.stringify(stored);
      row.description = row.description || 'ZEGOCLOUD credentials (secrets encrypted)';
    }
    await this.settingsRepo.save(row);
    this.cache = null;
    return this.getMaskedSettings();
  }

  async resolveConfig(): Promise<ResolvedZegoConfig> {
    if (this.cache) return this.cache;
    const stored = await this.loadStoredConfig();
    const env = this.envDefaults();
    const encryptionKey = this.encryptionKey();

    let appSign = env.appSign;
    let serverSecret = env.serverSecret;
    let appId = env.appId;
    let wsUrl = env.wsUrl;
    let wsUrlBak = env.wsUrlBak;
    let usedDb = false;
    let usedEnv = Boolean(env.appId || env.appSign || env.serverSecret);

    if (stored?.appId && /^\d+$/.test(stored.appId)) {
      appId = parseInt(stored.appId, 10);
      usedDb = true;
    }
    if (stored?.appSignEnc) {
      try {
        appSign = decryptSecret(stored.appSignEnc, encryptionKey);
        usedDb = true;
      } catch (error) {
        this.logger.warn(`Failed to decrypt ZEGO AppSign: ${(error as Error).message}`);
      }
    }
    if (stored?.serverSecretEnc) {
      try {
        serverSecret = decryptSecret(stored.serverSecretEnc, encryptionKey);
        usedDb = true;
      } catch (error) {
        this.logger.warn(`Failed to decrypt ZEGO ServerSecret: ${(error as Error).message}`);
      }
    }
    if (stored?.wsUrl) {
      wsUrl = stored.wsUrl;
      usedDb = true;
    }
    if (stored?.wsUrlBak) {
      wsUrlBak = stored.wsUrlBak;
      usedDb = true;
    }

    const source: ResolvedZegoConfig['source'] =
      usedDb && usedEnv ? 'mixed' : usedDb ? 'db' : usedEnv ? 'env' : 'none';

    this.cache = {
      appId: Number.isFinite(appId) ? appId : 0,
      appSign: appSign || '',
      serverSecret: serverSecret || '',
      wsUrl: wsUrl || '',
      wsUrlBak: wsUrlBak || '',
      source,
    };
    return this.cache;
  }

  invalidateCache() {
    this.cache = null;
  }

  private async loadStoredConfig(): Promise<StoredZegoConfig | null> {
    const row = await this.settingsRepo.findOne({ where: { key: ZEGO_CONFIG_KEY } });
    if (!row?.value) return null;
    try {
      return JSON.parse(row.value) as StoredZegoConfig;
    } catch {
      return null;
    }
  }

  private envDefaults() {
    return {
      appId: this.configService.get<number>('app.zego.appId') || 0,
      appSign: this.configService.get<string>('app.zego.appSign') || '',
      serverSecret: this.configService.get<string>('app.zego.serverSecret') || '',
      wsUrl: this.configService.get<string>('app.zego.wsUrl') || '',
      wsUrlBak: this.configService.get<string>('app.zego.wsUrlBak') || '',
    };
  }

  private encryptionKey(): string {
    return (
      this.configService.get<string>('app.settingsEncryptionKey') ||
      process.env.SETTINGS_ENCRYPTION_KEY ||
      ''
    );
  }

  private rethrowCrypto(error: unknown): never {
    if (error instanceof SecretBoxError) {
      throw new BadRequestException(error.message);
    }
    throw error;
  }
}
