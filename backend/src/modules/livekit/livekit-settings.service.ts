import {
  BadRequestException,
  Injectable,
  Logger,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { AppSetting } from '../../database/entities/app-setting.entity';
import {
  decryptSecret,
  encryptSecret,
  SecretBoxError,
} from '../../common/crypto/secret-box';

export type VoiceRtcProvider = 'zego' | 'livekit';

export const VOICE_RTC_CONFIG_KEY = 'voice_rtc_config';
export const LIVEKIT_CONFIG_KEY = 'livekit_server_config';

export interface StoredVoiceRtcConfig {
  provider?: VoiceRtcProvider | null;
  updatedAt?: string;
}

export interface StoredLiveKitConfig {
  url?: string | null;
  apiKey?: string | null;
  apiSecretEnc?: string | null;
  updatedAt?: string;
}

export interface ResolvedLiveKitConfig {
  url: string;
  apiKey: string;
  apiSecret: string;
  source: 'db' | 'env' | 'mixed' | 'none';
}

export interface MaskedLiveKitSettings {
  provider: VoiceRtcProvider;
  url: string;
  urlConfigured: boolean;
  apiKey: string;
  apiKeyConfigured: boolean;
  apiSecretConfigured: boolean;
  apiSecretHint: string | null;
  updatedAt: string | null;
  configSource: 'db' | 'env' | 'mixed' | 'none';
  ready: boolean;
}

@Injectable()
export class LiveKitSettingsService {
  private readonly logger = new Logger(LiveKitSettingsService.name);
  private cache: ResolvedLiveKitConfig | null = null;
  private providerCache: VoiceRtcProvider | null = null;

  constructor(
    private readonly configService: ConfigService,
    @InjectRepository(AppSetting)
    private readonly settingsRepo: Repository<AppSetting>,
  ) {}

  async getProvider(): Promise<VoiceRtcProvider> {
    if (this.providerCache) return this.providerCache;
    const row = await this.settingsRepo.findOne({
      where: { key: VOICE_RTC_CONFIG_KEY },
    });
    if (row?.value) {
      try {
        const parsed = JSON.parse(row.value) as StoredVoiceRtcConfig;
        if (parsed.provider === 'livekit' || parsed.provider === 'zego') {
          this.providerCache = parsed.provider;
          return parsed.provider;
        }
      } catch {
        /* fall through */
      }
    }
    const env = String(
      this.configService.get<string>('app.livekit.provider') ||
        process.env.VOICE_RTC_PROVIDER ||
        'zego',
    )
      .trim()
      .toLowerCase();
    const provider: VoiceRtcProvider = env === 'livekit' ? 'livekit' : 'zego';
    this.providerCache = provider;
    return provider;
  }

  async setProvider(provider: VoiceRtcProvider): Promise<VoiceRtcProvider> {
    if (provider !== 'zego' && provider !== 'livekit') {
      throw new BadRequestException('provider must be zego or livekit');
    }
    if (provider === 'livekit') {
      const resolved = await this.resolveConfig();
      if (!resolved.url || !resolved.apiKey || !resolved.apiSecret) {
        throw new BadRequestException(
          'LiveKit URL / API key / secret must be configured before switching',
        );
      }
    }
    const payload: StoredVoiceRtcConfig = {
      provider,
      updatedAt: new Date().toISOString(),
    };
    await this.upsertSetting(
      VOICE_RTC_CONFIG_KEY,
      JSON.stringify(payload),
      'Voice RTC provider (zego | livekit)',
    );
    this.providerCache = provider;
    this.logger.log(`Voice RTC provider set to ${provider}`);
    return provider;
  }

  async getMaskedSettings(): Promise<MaskedLiveKitSettings> {
    const provider = await this.getProvider();
    const resolved = await this.resolveConfig();
    const stored = await this.loadStoredLiveKit();
    return {
      provider,
      url: resolved.url || '',
      urlConfigured: Boolean(resolved.url),
      apiKey: resolved.apiKey ? maskKey(resolved.apiKey) : '',
      apiKeyConfigured: Boolean(resolved.apiKey),
      apiSecretConfigured: Boolean(resolved.apiSecret),
      apiSecretHint: resolved.apiSecret
        ? resolved.apiSecret.slice(-4)
        : null,
      updatedAt: stored?.updatedAt || null,
      configSource: resolved.source,
      ready: Boolean(resolved.url && resolved.apiKey && resolved.apiSecret),
    };
  }

  /** Plain credentials for authenticated admins (same pattern as Zego reveal). */
  async revealCredentials(): Promise<{
    url: string;
    apiKey: string;
    apiSecret: string;
    provider: VoiceRtcProvider;
  }> {
    const provider = await this.getProvider();
    const resolved = await this.resolveConfig();
    return {
      url: resolved.url || '',
      apiKey: resolved.apiKey || '',
      apiSecret: resolved.apiSecret || '',
      provider,
    };
  }

  async updateLiveKitSettings(dto: {
    url?: string;
    apiKey?: string;
    apiSecret?: string;
    clearApiSecret?: boolean;
    provider?: VoiceRtcProvider;
  }): Promise<MaskedLiveKitSettings> {
    const stored = (await this.loadStoredLiveKit()) || {};
    if (dto.url !== undefined) {
      stored.url = String(dto.url || '').trim() || null;
    }
    if (dto.apiKey !== undefined) {
      stored.apiKey = String(dto.apiKey || '').trim() || null;
    }
    if (dto.clearApiSecret) {
      stored.apiSecretEnc = null;
    } else if (dto.apiSecret !== undefined && String(dto.apiSecret).trim()) {
      try {
        stored.apiSecretEnc = encryptSecret(
          String(dto.apiSecret).trim(),
          this.encryptionKey(),
        );
      } catch (e) {
        if (e instanceof SecretBoxError) {
          throw new BadRequestException(e.message);
        }
        throw e;
      }
    }
    stored.updatedAt = new Date().toISOString();
    await this.upsertSetting(
      LIVEKIT_CONFIG_KEY,
      JSON.stringify(stored),
      'LiveKit self-hosted credentials',
    );
    this.cache = null;
    if (dto.provider) {
      await this.setProvider(dto.provider);
    }
    return this.getMaskedSettings();
  }

  async resolveConfig(): Promise<ResolvedLiveKitConfig> {
    if (this.cache) return this.cache;
    const envUrl = String(
      this.configService.get<string>('app.livekit.url') || '',
    ).trim();
    const envKey = String(
      this.configService.get<string>('app.livekit.apiKey') || '',
    ).trim();
    const envSecret = String(
      this.configService.get<string>('app.livekit.apiSecret') || '',
    ).trim();

    const stored = await this.loadStoredLiveKit();
    let secret = '';
    if (stored?.apiSecretEnc) {
      try {
        secret = decryptSecret(stored.apiSecretEnc, this.encryptionKey());
      } catch {
        this.logger.warn('Failed to decrypt LiveKit API secret');
      }
    }
    const url = (stored?.url || envUrl || '').trim();
    const apiKey = (stored?.apiKey || envKey || '').trim();
    if (!secret) secret = envSecret;

    let source: ResolvedLiveKitConfig['source'] = 'none';
    const fromDb = Boolean(stored?.url || stored?.apiKey || stored?.apiSecretEnc);
    const fromEnv = Boolean(envUrl || envKey || envSecret);
    if (fromDb && fromEnv) source = 'mixed';
    else if (fromDb) source = 'db';
    else if (fromEnv) source = 'env';

    this.cache = { url, apiKey, apiSecret: secret, source };
    return this.cache;
  }

  /** Values safe for mobile clients (never secrets). */
  async getPublicClientConfig(): Promise<{
    provider: VoiceRtcProvider;
    livekitUrl: string;
  }> {
    const provider = await this.getProvider();
    const resolved = await this.resolveConfig();
    return {
      provider,
      livekitUrl: provider === 'livekit' ? resolved.url : '',
    };
  }

  private encryptionKey(): string {
    return (
      this.configService.get<string>('app.settingsEncryptionKey') ||
      process.env.SETTINGS_ENCRYPTION_KEY ||
      ''
    );
  }

  private async loadStoredLiveKit(): Promise<StoredLiveKitConfig | null> {
    const row = await this.settingsRepo.findOne({
      where: { key: LIVEKIT_CONFIG_KEY },
    });
    if (!row?.value) return null;
    try {
      return JSON.parse(row.value) as StoredLiveKitConfig;
    } catch {
      return null;
    }
  }

  private async upsertSetting(key: string, value: string, description: string) {
    let row = await this.settingsRepo.findOne({ where: { key } });
    if (!row) {
      row = this.settingsRepo.create({ key, value, description });
    } else {
      row.value = value;
      row.description = description;
    }
    await this.settingsRepo.save(row);
  }
}

function maskKey(key: string): string {
  if (key.length <= 6) return '***';
  return `${key.slice(0, 3)}…${key.slice(-3)}`;
}
