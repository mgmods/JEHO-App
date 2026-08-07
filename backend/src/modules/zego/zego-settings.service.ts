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

  /**
   * Probe (and optionally import) ZEGO credentials from a remote API URL/domain.
   * Supports JEHO-style `/config/zego` and flexible JSON shapes with appId/appSign/serverSecret.
   */
  async importFromRemoteUrl(input: {
    url: string;
    bearerToken?: string;
    apply?: boolean;
  }): Promise<{
    probedUrl: string;
    applied: boolean;
    found: {
      appId: string | null;
      appSign: boolean;
      serverSecret: boolean;
      wsUrl: string | null;
      wsUrlBak: string | null;
    };
    warnings: string[];
    settings?: MaskedZegoSettings;
  }> {
    const raw = String(input.url || '').trim();
    if (!raw) throw new BadRequestException('URL is required');

    let base: URL;
    try {
      base = new URL(raw.includes('://') ? raw : `https://${raw}`);
    } catch {
      throw new BadRequestException('Invalid URL');
    }
    if (!['http:', 'https:'].includes(base.protocol)) {
      throw new BadRequestException('Only http/https URLs are allowed');
    }

    const candidates = this.buildRemoteCandidateUrls(base);
    const warnings: string[] = [];
    let probedUrl = '';
    let parsed: {
      appId?: string;
      appSign?: string;
      serverSecret?: string;
      wsUrl?: string;
      wsUrlBak?: string;
      note?: string;
    } | null = null;

    for (const candidate of candidates) {
      try {
        const json = await this.fetchRemoteJson(candidate, input.bearerToken);
        const mapped = this.mapRemoteZegoPayload(json);
        if (mapped?.note && !mapped.appId && !mapped.serverSecret && !mapped.appSign) {
          warnings.push(mapped.note);
          continue;
        }
        if (mapped?.appId || mapped?.serverSecret || mapped?.appSign || mapped?.wsUrl) {
          probedUrl = candidate;
          parsed = mapped;
          break;
        }
      } catch (err) {
        warnings.push(`${candidate}: ${(err as Error).message}`);
      }
    }

    if (!parsed?.appId && !parsed?.serverSecret && !parsed?.appSign) {
      throw new BadRequestException({
        message:
          'لم يُعثر على إعدادات Zego في الرابط. تأكد أنه API يعيد appId (ومن الأفضل ServerSecret). بعض التطبيقات تستخدم Agora وليس Zego.',
        warnings: warnings.slice(0, 8),
      });
    }

    if (parsed.note) warnings.push(parsed.note);
    if (!parsed.serverSecret) {
      warnings.push(
        'الرابط لم يُرجع ServerSecret — التوكنات لن تُولَّد حتى تضيف ServerSecret يدوياً أو من API إداري.',
      );
    }

    const found = {
      appId: parsed.appId || null,
      appSign: Boolean(parsed.appSign),
      serverSecret: Boolean(parsed.serverSecret),
      wsUrl: parsed.wsUrl || null,
      wsUrlBak: parsed.wsUrlBak || null,
    };

    if (!input.apply) {
      return { probedUrl, applied: false, found, warnings };
    }

    const patch: PatchZegoSettingsDto = {};
    if (parsed.appId) patch.appId = parsed.appId;
    if (parsed.appSign) patch.appSign = parsed.appSign;
    if (parsed.serverSecret) patch.serverSecret = parsed.serverSecret;
    if (parsed.wsUrl) patch.wsUrl = parsed.wsUrl;
    if (parsed.wsUrlBak) patch.wsUrlBak = parsed.wsUrlBak;
    const settings = await this.updateSettings(patch);

    const metaKey = 'zego_import_last_url';
    let meta = await this.settingsRepo.findOne({ where: { key: metaKey } });
    if (!meta) {
      meta = this.settingsRepo.create({
        key: metaKey,
        value: probedUrl || base.origin,
        description: 'Last remote URL used to import ZEGO settings',
      });
    } else {
      meta.value = probedUrl || base.origin;
    }
    await this.settingsRepo.save(meta);

    return { probedUrl, applied: true, found, warnings, settings };
  }

  private buildRemoteCandidateUrls(base: URL): string[] {
    const path = (base.pathname || '/').replace(/\/+$/, '') || '';
    const looksLikeEndpoint =
      /config\/zego|zego-settings|zego|agora\/getKey|client\/app\/config/i.test(path);
    const origin = base.origin;
    const list: string[] = [];
    if (looksLikeEndpoint || path.length > 1) {
      list.push(base.toString());
    }
    const suffixes = [
      '/api/v1/config/zego',
      '/api/config/zego',
      '/config/zego',
      '/api/v1/admin/zego-settings',
      '/admin/zego-settings',
      '/client/app/config',
    ];
    for (const s of suffixes) {
      list.push(`${origin}${s}`);
    }
    return [...new Set(list)];
  }

  private async fetchRemoteJson(
    url: string,
    bearerToken?: string,
  ): Promise<unknown> {
    const headers: Record<string, string> = {
      Accept: 'application/json',
      'User-Agent': 'JEHO-CHAT-Admin/1.0',
    };
    const token = String(bearerToken || '').trim();
    if (token) {
      headers.Authorization = token.toLowerCase().startsWith('bearer ')
        ? token
        : `Bearer ${token}`;
    }
    const res = await fetch(url, {
      method: 'GET',
      headers,
      signal: AbortSignal.timeout(12_000),
      redirect: 'follow',
    });
    const text = await res.text();
    if (!res.ok) {
      throw new Error(`HTTP ${res.status}`);
    }
    try {
      return JSON.parse(text);
    } catch {
      throw new Error('Response is not JSON');
    }
  }

  private mapRemoteZegoPayload(raw: unknown): {
    appId?: string;
    appSign?: string;
    serverSecret?: string;
    wsUrl?: string;
    wsUrlBak?: string;
    note?: string;
  } | null {
    if (!raw || typeof raw !== 'object') return null;
    const root = raw as Record<string, unknown>;
    const data =
      (root.data && typeof root.data === 'object'
        ? (root.data as Record<string, unknown>)
        : null) ||
      (root.result && typeof root.result === 'object'
        ? (root.result as Record<string, unknown>)
        : null) ||
      root;

    const pick = (...keys: string[]) => {
      for (const k of keys) {
        const v = data[k];
        if (v == null || v === '') continue;
        return String(v).trim();
      }
      return '';
    };

    const agoraHint =
      pick('agoraAppId', 'agora_app_id', 'agoraKey', 'agora_key') ||
      (typeof data.agora === 'object' ? 'agora' : '');
    if (agoraHint && !pick('appId', 'app_id', 'zegoAppId', 'zego_app_id')) {
      return {
        note: 'الرابط يبدو لإعدادات Agora (تطبيقات أخرى) وليس Zego — لا يمكن استيرادها كتطبيق Zego.',
      };
    }

    const appId = pick(
      'appId',
      'app_id',
      'zegoAppId',
      'zego_app_id',
      'zegoAppID',
    );
    const appSign = pick('appSign', 'app_sign', 'zegoAppSign', 'zego_app_sign');
    const serverSecret = pick(
      'serverSecret',
      'server_secret',
      'zegoServerSecret',
      'zego_server_secret',
    );
    let wsUrl = pick('wsUrl', 'ws_url', 'zegoWsUrl');
    let wsUrlBak = pick('wsUrlBak', 'ws_url_bak', 'zegoWsUrlBak');

    const cleanSign = appSign && appSign !== 'null' ? appSign : '';
    const cleanSecret = serverSecret || '';

    if (!appId && !cleanSign && !cleanSecret && !wsUrl) return null;

    if (appId && /^\d+$/.test(appId)) {
      if (!wsUrl) {
        wsUrl = `wss://webliveroom${appId}-api.coolzcloud.com/ws`;
      }
      if (!wsUrlBak) {
        wsUrlBak = `wss://webliveroom${appId}-api-bak.coolzcloud.com/ws`;
      }
    }

    return {
      appId: appId && /^\d+$/.test(appId) ? appId : undefined,
      appSign: cleanSign || undefined,
      serverSecret:
        cleanSecret && cleanSecret.length === 32 ? cleanSecret : undefined,
      wsUrl: wsUrl || undefined,
      wsUrlBak: wsUrlBak || undefined,
      note:
        cleanSecret && cleanSecret.length !== 32 && cleanSecret.length > 0
          ? 'ServerSecret الموجود في الرد ليس بطول 32 حرفاً وتم تجاهله.'
          : undefined,
    };
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
