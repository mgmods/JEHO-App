import {

  BadRequestException,

  Injectable,

  InternalServerErrorException,

  Logger,

} from '@nestjs/common';

import { ConfigService } from '@nestjs/config';

import { InjectRepository } from '@nestjs/typeorm';

import { Repository } from 'typeorm';

import { AppSetting } from '../../database/entities/app-setting.entity';

import { decryptSecret, encryptSecret, SecretBoxError } from '../../common/crypto/secret-box';

import { PatchBinancePaySettingsDto } from './dto/patch-binance-pay-settings.dto';

import {

  BinanceWalletClient,

  BINANCE_WALLET_ALLOWED_BASE_URLS,

  BinanceWalletBaseUrl,

} from './binance-wallet.client';



export const BINANCE_PAY_CONFIG_KEY = 'binance_pay_config';



export interface StoredBinanceWalletConfig {

  apiKeyEnc?: string | null;

  secretKeyEnc?: string | null;

  baseUrl?: string;

  accountName?: string;

  updatedAt?: string;

}



export interface BinanceWalletResolvedConfig {

  apiKey: string;

  secretKey: string;

  baseUrl: BinanceWalletBaseUrl;

  accountName: string;

  source: 'db' | 'env' | 'mixed';

}



export interface BinanceWalletMaskedSettings {

  apiKeyConfigured: boolean;

  secretKeyConfigured: boolean;

  apiKeyHint: string | null;

  baseUrl: string;

  accountName: string;

  updatedAt: string | null;

  configSource: 'db' | 'env' | 'mixed' | 'none';

}



@Injectable()

export class PaymentSettingsService {

  private readonly logger = new Logger(PaymentSettingsService.name);



  constructor(

    private readonly configService: ConfigService,

    @InjectRepository(AppSetting)

    private readonly settingsRepo: Repository<AppSetting>,

  ) {}



  async getBinancePayMaskedSettings(): Promise<BinanceWalletMaskedSettings> {

    const stored = await this.loadStoredConfig();

    const env = this.envDefaults();

    const resolved = this.mergeConfig(stored, env);



    let apiKeyHint: string | null = null;

    if (resolved.apiKey) {

      apiKeyHint = resolved.apiKey.length <= 4 ? '****' : resolved.apiKey.slice(-4);

    }



    const hasDbSecrets = !!stored?.apiKeyEnc || !!stored?.secretKeyEnc;

    const hasEnvSecrets = !!env.apiKey || !!env.secretKey;



    let configSource: BinanceWalletMaskedSettings['configSource'] = 'none';

    if (hasDbSecrets && hasEnvSecrets) configSource = 'mixed';

    else if (hasDbSecrets) configSource = 'db';

    else if (hasEnvSecrets) configSource = 'env';



    return {

      apiKeyConfigured: !!resolved.apiKey,

      secretKeyConfigured: !!resolved.secretKey,

      apiKeyHint,

      baseUrl: resolved.baseUrl,

      accountName: resolved.accountName,

      updatedAt: stored?.updatedAt || null,

      configSource,

    };

  }

  async revealBinancePayCredentials(): Promise<{ apiKey: string; secretKey: string }> {

    const config = await this.getBinanceWalletConfig();

    return {

      apiKey: config.apiKey,

      secretKey: config.secretKey,

    };

  }



  async updateBinancePaySettings(

    dto: PatchBinancePaySettingsDto,

  ): Promise<BinanceWalletMaskedSettings> {

    const stored = (await this.loadStoredConfig()) || {};

    const next: StoredBinanceWalletConfig = { ...stored };



    if (dto.clearApiKey) next.apiKeyEnc = null;

    else if (this.hasNewSecret(dto.apiKey)) {

      next.apiKeyEnc = this.encryptField(dto.apiKey!);

    }



    if (dto.clearSecretKey) next.secretKeyEnc = null;

    else if (this.hasNewSecret(dto.secretKey)) {

      next.secretKeyEnc = this.encryptField(dto.secretKey!);

    }



    if (dto.baseUrl !== undefined) {

      const trimmed = dto.baseUrl.trim();

      if (trimmed) {

        next.baseUrl = BinanceWalletClient.assertAllowedBaseUrl(trimmed);

      } else {

        next.baseUrl = undefined;

      }

    }



    if (dto.accountName !== undefined) {

      next.accountName = dto.accountName.trim();

    }



    next.updatedAt = new Date().toISOString();

    await this.saveStoredConfig(next);

    return this.getBinancePayMaskedSettings();

  }



  async testBinancePayConnection(): Promise<{ ok: boolean; message: string }> {

    const config = await this.getBinanceWalletConfig();

    if (!config.apiKey || !config.secretKey) {

      return {

        ok: false,

        message:

          'API key and secret key are required. Configure them in the dashboard or environment.',

      };

    }



    try {

      const client = this.createClient(config);

      return client.testConnection();

    } catch (err) {

      this.logger.warn(`Binance wallet test failed: ${(err as Error).message}`);

      return {

        ok: false,

        message: (err as Error).message || 'Connection test failed',

      };

    }

  }



  async getBinanceWalletConfig(): Promise<BinanceWalletResolvedConfig> {

    const stored = await this.loadStoredConfig();

    const env = this.envDefaults();

    return this.mergeConfig(stored, env);

  }



  /** @deprecated use getBinanceWalletConfig */

  async getBinancePayConfig(): Promise<BinanceWalletResolvedConfig> {

    return this.getBinanceWalletConfig();

  }



  createClient(config: BinanceWalletResolvedConfig): BinanceWalletClient {

    if (!config.apiKey || !config.secretKey) {

      throw new BadRequestException('Binance Exchange API credentials are not configured');

    }

    return new BinanceWalletClient(config.apiKey, config.secretKey, config.baseUrl);

  }



  async assertBinanceWalletConfiguredForProduction(): Promise<void> {

    const isProduction = this.configService.get<string>('app.nodeEnv') === 'production';

    if (!isProduction) return;



    const config = await this.getBinanceWalletConfig();

    if (!config.apiKey || !config.secretKey) {

      throw new BadRequestException(

        'Binance Exchange wallet is not configured. Add API credentials in Admin → Payment Settings before accepting deposits in production.',

      );

    }

  }



  /** @deprecated use assertBinanceWalletConfiguredForProduction */

  async assertBinancePayConfiguredForProduction(): Promise<void> {

    return this.assertBinanceWalletConfiguredForProduction();

  }



  private envDefaults(): Omit<BinanceWalletResolvedConfig, 'source'> {

    const baseUrlRaw =

      this.configService.get<string>('app.binanceWallet.baseUrl') ||

      this.configService.get<string>('app.binancePay.baseUrl') ||

      'https://api.binance.com';

    let baseUrl: BinanceWalletBaseUrl = 'https://api.binance.com';

    try {

      baseUrl = BinanceWalletClient.assertAllowedBaseUrl(baseUrlRaw);

    } catch {

      baseUrl = 'https://api.binance.com';

    }



    return {

      apiKey:

        this.configService.get<string>('app.binanceWallet.apiKey') ||

        this.configService.get<string>('app.binancePay.apiKey') ||

        '',

      secretKey:

        this.configService.get<string>('app.binanceWallet.secretKey') ||

        this.configService.get<string>('app.binancePay.secretKey') ||

        '',

      baseUrl,

      accountName: this.configService.get<string>('app.binanceWallet.accountName') || '',

    };

  }



  private mergeConfig(

    stored: StoredBinanceWalletConfig | null,

    env: Omit<BinanceWalletResolvedConfig, 'source'>,

  ): BinanceWalletResolvedConfig {

    const dbApiKey = this.decryptOptional(stored?.apiKeyEnc);

    const dbSecretKey = this.decryptOptional(stored?.secretKeyEnc);



    const apiKey = dbApiKey || env.apiKey;

    const secretKey = dbSecretKey || env.secretKey;



    const hasDb = !!(dbApiKey || dbSecretKey || stored?.baseUrl || stored?.accountName);

    const hasEnv = !!(env.apiKey || env.secretKey);

    let source: BinanceWalletResolvedConfig['source'] = 'env';

    if (hasDb && hasEnv) source = 'mixed';

    else if (hasDb) source = 'db';



    let baseUrl = env.baseUrl;

    if (stored?.baseUrl?.trim()) {

      try {

        baseUrl = BinanceWalletClient.assertAllowedBaseUrl(stored.baseUrl.trim());

      } catch {

        this.logger.warn(`Ignoring invalid stored Binance baseUrl: ${stored.baseUrl}`);

      }

    }



    return {

      apiKey,

      secretKey,

      baseUrl,

      accountName: stored?.accountName?.trim() || env.accountName,

      source,

    };

  }



  private encryptionKeyRaw(): string | undefined {

    const fromConfig = this.configService.get<string>('app.settingsEncryptionKey');

    const raw = (fromConfig || process.env.SETTINGS_ENCRYPTION_KEY || '').trim();

    return raw || undefined;

  }



  private decryptOptional(enc: string | null | undefined): string {

    if (!enc) return '';

    try {

      return decryptSecret(enc, this.encryptionKeyRaw());

    } catch (err) {

      this.logger.warn(`Failed to decrypt payment secret: ${(err as Error).message}`);

      return '';

    }

  }



  private encryptField(value: string): string {

    try {

      const encrypted = encryptSecret(value, this.encryptionKeyRaw());

      // Fail closed: never persist ciphertext we cannot decrypt with the same key.

      const roundTrip = decryptSecret(encrypted, this.encryptionKeyRaw());

      if (roundTrip !== value) {

        throw new InternalServerErrorException('Payment secret encryption round-trip failed');

      }

      return encrypted;

    } catch (err) {

      if (err instanceof SecretBoxError) {

        throw new BadRequestException(err.message);

      }

      if (err instanceof BadRequestException || err instanceof InternalServerErrorException) {

        throw err;

      }

      throw new InternalServerErrorException('Failed to encrypt payment secret');

    }

  }



  private hasNewSecret(value: string | undefined): boolean {

    return value !== undefined && value.trim().length > 0;

  }



  private async loadStoredConfig(): Promise<StoredBinanceWalletConfig | null> {

    const row = await this.settingsRepo.findOne({ where: { key: BINANCE_PAY_CONFIG_KEY } });

    if (!row?.value) return null;

    try {

      return JSON.parse(row.value) as StoredBinanceWalletConfig;

    } catch {

      this.logger.warn('Invalid binance_pay_config JSON in app_settings');

      return null;

    }

  }



  private async saveStoredConfig(config: StoredBinanceWalletConfig): Promise<void> {

    const value = JSON.stringify(config);

    let row = await this.settingsRepo.findOne({ where: { key: BINANCE_PAY_CONFIG_KEY } });

    if (!row) {

      row = this.settingsRepo.create({

        key: BINANCE_PAY_CONFIG_KEY,

        value,

        description: 'Encrypted Binance Exchange wallet API credentials',

      });

    } else {

      row.value = value;

      row.description = 'Encrypted Binance Exchange wallet API credentials';

    }

    await this.settingsRepo.save(row);

  }



  static allowedBaseUrls(): readonly string[] {

    return BINANCE_WALLET_ALLOWED_BASE_URLS;

  }

}


