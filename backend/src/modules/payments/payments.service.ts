import {
  Injectable,
  Logger,
  BadRequestException,
  NotFoundException,
  ServiceUnavailableException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { InjectRepository } from '@nestjs/typeorm';
import { DataSource, Repository } from 'typeorm';
import Stripe from 'stripe';
import * as crypto from 'crypto';
import { GoogleAuth } from 'google-auth-library';
import {
  RechargeOrder,
  RechargeStatus,
  PaymentProvider,
} from '../../database/entities/recharge-order.entity';
import { PaymentWebhookEvent } from '../../database/entities/payment-webhook-event.entity';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { User } from '../../database/entities/user.entity';
import { WalletService } from '../wallet/wallet.service';
import { PaymentSettingsService } from './payment-settings.service';
import { FourthwallClient } from './fourthwall.client';
import { STANDARD_RECHARGE_PACKAGES } from '../../common/pricing-catalog';
import {
  IsInt,
  IsNumber,
  IsOptional,
  IsString,
  Min,
  IsObject,
  MaxLength,
  IsIn,
} from 'class-validator';
import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';

export class StripeCheckoutDto {
  @ApiProperty({ example: 'coins_70000' })
  @IsString()
  @MaxLength(64)
  sku: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  currency?: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  successUrl?: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  cancelUrl?: string;
}

export class FourthwallCheckoutDto {
  @ApiProperty({ example: 'coins_70000' })
  @IsString()
  @MaxLength(64)
  sku: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  currency?: string;
}

export class PaypalCreateDto {
  @ApiProperty({ example: 'coins_70000' })
  @IsString()
  @MaxLength(64)
  sku: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  currency?: string;
}

export class PlayBillingVerifyDto {
  @ApiProperty()
  @IsString()
  purchaseToken: string;

  @ApiProperty()
  @IsString()
  productId: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  orderId?: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsInt()
  @Min(1)
  coins?: number;

  @ApiPropertyOptional()
  @IsOptional()
  @IsNumber()
  amountFiat?: number;
}

export class CryptoOrderDto {
  @ApiProperty({ example: 'coins_70000' })
  @IsString()
  @MaxLength(64)
  sku: string;

  @ApiProperty({ example: 'USDT' })
  @IsString()
  cryptoCurrency: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsObject()
  metadata?: Record<string, unknown>;
}

export class BinancePayOrderDto {
  @ApiProperty({ example: 'coins_70000' })
  @IsString()
  @MaxLength(64)
  sku: string;
}

export class BinanceWalletDepositAddressDto {
  @ApiProperty({ example: 'TRX', enum: ['TRX', 'BSC'] })
  @IsString()
  @IsIn(['TRX', 'BSC', 'trx', 'bsc'])
  network: 'TRX' | 'BSC';
}

export class BinanceWalletOrderDto {
  @ApiProperty({ example: 'coins_70000' })
  @IsString()
  @MaxLength(64)
  sku: string;

  @ApiProperty({ example: 'TRX', enum: ['TRX', 'BSC'] })
  @IsString()
  @IsIn(['TRX', 'BSC', 'trx', 'bsc'])
  network: 'TRX' | 'BSC';
}

const BINANCE_WALLET_NETWORKS = new Set(['TRX', 'BSC']);
const BINANCE_WALLET_COIN = 'USDT';
const BINANCE_WALLET_AMOUNT_TOLERANCE = 0.005;

@Injectable()
export class PaymentsService {
  private readonly logger = new Logger(PaymentsService.name);
  private stripe: Stripe | null = null;
  private readonly googlePlayAuth = new GoogleAuth({
    scopes: ['https://www.googleapis.com/auth/androidpublisher'],
  });

  constructor(
    private readonly configService: ConfigService,
    private readonly dataSource: DataSource,
    @InjectRepository(RechargeOrder)
    private readonly ordersRepo: Repository<RechargeOrder>,
    @InjectRepository(PaymentWebhookEvent)
    private readonly webhookEventsRepo: Repository<PaymentWebhookEvent>,
    @InjectRepository(AppSetting)
    private readonly settingsRepo: Repository<AppSetting>,
    @InjectRepository(User)
    private readonly usersRepo: Repository<User>,
    private readonly walletService: WalletService,
    private readonly paymentSettingsService: PaymentSettingsService,
  ) {
    const key = this.configService.get<string>('app.stripe.secretKey');
    if (key && key.startsWith('sk_')) {
      this.stripe = new Stripe(key, { apiVersion: '2024-11-20.acacia' as any });
    }
  }

  async createStripeCheckout(userId: string, dto: StripeCheckoutDto) {
    const pkg = await this.walletService.resolvePackageBySku(dto.sku);
    const currency = (dto.currency || 'USD').toUpperCase();
    const order = await this.ordersRepo.save(
      this.ordersRepo.create({
        userId,
        sku: pkg.sku,
        coins: pkg.coins,
        bonusCoins: pkg.bonusCoins,
        amountFiat: pkg.priceUsd,
        currency,
        provider: PaymentProvider.STRIPE,
        status: RechargeStatus.PENDING,
      }),
    );

    if (!this.stripe) {
      return {
        order,
        checkout: null,
        message: 'Stripe not configured — order created as pending',
      };
    }

    const session = await this.stripe.checkout.sessions.create({
      mode: 'payment',
      success_url: dto.successUrl || 'https://auralive.app/wallet/success',
      cancel_url: dto.cancelUrl || 'https://auralive.app/wallet/cancel',
      line_items: [
        {
          quantity: 1,
          price_data: {
            currency: currency.toLowerCase(),
            unit_amount: Math.round(Number(pkg.priceUsd) * 100),
            product_data: {
              name: `JEHO CHAT ${pkg.coins} Coins`,
              description: `In-app coin recharge (${pkg.sku})`,
            },
          },
        },
      ],
      metadata: {
        orderId: order.id,
        userId,
        sku: pkg.sku,
        coins: String(pkg.coins),
        bonusCoins: String(pkg.bonusCoins),
      },
    });

    order.providerOrderId = session.id;
    order.providerPayload = { sessionId: session.id, url: session.url };
    await this.ordersRepo.save(order);

    return { order, checkoutUrl: session.url, sessionId: session.id };
  }

  private async fourthwallClient(): Promise<FourthwallClient> {
    const cfg = await this.loadFourthwallConfig();
    return new FourthwallClient(
      cfg.apiUser,
      cfg.apiPassword,
      cfg.storefrontToken,
      cfg.shopDomain,
    );
  }

  private async loadFourthwallConfig(): Promise<{
    apiUser: string;
    apiPassword: string;
    storefrontToken: string;
    shopDomain: string;
    webhookSecret: string;
  }> {
    const row = await this.settingsRepo.findOne({
      where: { key: 'fourthwall_config' },
    });
    let db: Record<string, any> = {};
    try {
      const raw = row?.value as any;
      db = typeof raw === 'string' ? JSON.parse(raw || '{}') : { ...(raw || {}) };
    } catch {
      db = {};
    }
    return {
      apiUser: String(
        db.apiUser ||
          this.configService.get<string>('app.fourthwall.apiUser') ||
          '',
      ).trim(),
      apiPassword: String(
        db.apiPassword ||
          this.configService.get<string>('app.fourthwall.apiPassword') ||
          '',
      ).trim(),
      storefrontToken: String(
        db.storefrontToken ||
          this.configService.get<string>('app.fourthwall.storefrontToken') ||
          '',
      ).trim(),
      shopDomain: String(
        db.shopDomain ||
          this.configService.get<string>('app.fourthwall.shopDomain') ||
          '',
      ).trim(),
      webhookSecret: String(
        db.webhookSecret ||
          this.configService.get<string>('app.fourthwall.webhookSecret') ||
          '',
      ).trim(),
    };
  }

  private async ensureFourthwallProviderEnum() {
    try {
      await this.dataSource.query(`
        DO $$ BEGIN
          ALTER TYPE recharge_orders_provider_enum ADD VALUE IF NOT EXISTS 'fourthwall';
        EXCEPTION WHEN others THEN
          BEGIN
            ALTER TYPE "recharge_orders_provider_enum" ADD VALUE IF NOT EXISTS 'fourthwall';
          EXCEPTION WHEN others THEN NULL;
          END;
        END $$;
      `);
    } catch (e) {
      this.logger.warn(
        `fourthwall enum ensure skipped: ${(e as Error).message || e}`,
      );
    }
  }

  private async rememberFourthwallVariant(sku: string, variantId: string) {
    const row = await this.settingsRepo.findOne({
      where: { key: 'fourthwall_variant_map' },
    });
    let map: Record<string, string> = {};
    try {
      const raw = row?.value as any;
      map = typeof raw === 'string' ? JSON.parse(raw || '{}') : { ...(raw || {}) };
    } catch {
      map = {};
    }
    map[sku] = variantId;
    const encoded = JSON.stringify(map);
    if (row) {
      row.value = encoded;
      await this.settingsRepo.save(row);
    } else {
      await this.settingsRepo.save(
        this.settingsRepo.create({
          key: 'fourthwall_variant_map',
          value: encoded,
          description: 'Fourthwall variantId per JEHO package SKU',
        }),
      );
    }
  }

  private claimCodeForOrder(orderId: string): string {
    return `JEHO-${orderId.replace(/-/g, '').slice(-8).toUpperCase()}`;
  }

  async createFourthwallCheckout(userId: string, dto: FourthwallCheckoutDto) {
    await this.ensureFourthwallProviderEnum();
    const client = await this.fourthwallClient();
    if (!client.configured) {
      throw new ServiceUnavailableException(
        'Fourthwall غير مُعدّ — من لوحة التحكم: الإعدادات → الدفع → Fourthwall',
      );
    }
    const pkg = await this.walletService.resolvePackageBySku(dto.sku);
    const currency = (dto.currency || 'USD').toUpperCase();

    let product;
    try {
      product = await client.ensureCoinPackageProduct({
        sku: pkg.sku,
        coins: pkg.coins,
        bonusCoins: pkg.bonusCoins,
        priceUsd: Number(pkg.priceUsd),
      });
      await this.rememberFourthwallVariant(pkg.sku, product.variantId);
    } catch (e) {
      throw new BadRequestException(
        `تعذر تجهيز باقة البطاقة على Fourthwall: ${(e as Error).message || e}`,
      );
    }
    // Guard: never open checkout with a stale Fourthwall price (e.g. $0.99 for a $200 pack).
    const want = Number(pkg.priceUsd);
    const got = Number(product.priceUsd);
    if (want > 0 && got > 0 && Math.abs(want - got) > 0.05) {
      this.logger.error(
        `Fourthwall price mismatch sku=${pkg.sku} package=$${want} product=$${got} productId=${product.productId}`,
      );
      throw new BadRequestException(
        `سعر باقة البطاقة غير متزامن ($${got} بدل $${want}). أعد المحاولة أو من لوحة التحكم: مزامنة Fourthwall.`,
      );
    }
    const variantId = product.variantId;

    // One pending card checkout per user — cancel older open ones.
    await this.ordersRepo
      .createQueryBuilder()
      .update(RechargeOrder)
      .set({ status: RechargeStatus.CANCELLED })
      .where('userId = :userId', { userId })
      .andWhere('provider = :provider', { provider: PaymentProvider.FOURTHWALL })
      .andWhere('status = :status', { status: RechargeStatus.PENDING })
      .execute();

    const order = await this.ordersRepo.save(
      this.ordersRepo.create({
        userId,
        sku: pkg.sku,
        coins: pkg.coins,
        bonusCoins: pkg.bonusCoins,
        amountFiat: pkg.priceUsd,
        currency,
        provider: PaymentProvider.FOURTHWALL,
        status: RechargeStatus.PENDING,
        expiresAt: new Date(Date.now() + 60 * 60 * 1000),
      }),
    );
    const claimCode = this.claimCodeForOrder(order.id);

    let cart: { id: string };
    try {
      cart = await client.createCartWithVariant(variantId, currency);
    } catch (e) {
      order.status = RechargeStatus.FAILED;
      order.providerPayload = {
        error: (e as Error).message || String(e),
        variantId,
        productId: product.productId,
      };
      await this.ordersRepo.save(order);
      throw new BadRequestException(
        `تعذر إنشاء سلة Fourthwall: ${(e as Error).message || e}`,
      );
    }

    let userEmail = '';
    try {
      const u = await this.usersRepo.findOne({ where: { id: userId } as any });
      userEmail = String(u?.email || '').trim().toLowerCase();
    } catch {
      userEmail = '';
    }

    // Prefer cartId + currency=USD so geo local currencies don't confuse the shopper.
    const checkoutUrl = client.cartHasItems(cart as any)
      ? client.checkoutUrl(cart.id, currency)
      : client.checkoutUrlForVariant(variantId, currency);
    const cartCheckoutUrl = client.checkoutUrl(cart.id, currency);
    order.providerOrderId = cart.id;
    order.providerPayload = {
      cartId: cart.id,
      variantId,
      productId: product.productId,
      claimCode,
      checkoutUrl,
      cartCheckoutUrl,
      cartHasItems: client.cartHasItems(cart as any),
      userEmail,
      package: {
        sku: pkg.sku,
        coins: pkg.coins,
        bonusCoins: pkg.bonusCoins,
        priceUsd: pkg.priceUsd,
      },
    };
    await this.ordersRepo.save(order);

    return {
      order,
      checkoutUrl,
      cartId: cart.id,
      claimCode,
      userEmail: userEmail || null,
    };
  }

  async getFourthwallAdminSettings() {
    const cfg = await this.loadFourthwallConfig();
    const mapRow = await this.settingsRepo.findOne({
      where: { key: 'fourthwall_variant_map' },
    });
    let variantMap: Record<string, string> = {};
    try {
      const raw = mapRow?.value as any;
      variantMap =
        typeof raw === 'string' ? JSON.parse(raw || '{}') : { ...(raw || {}) };
    } catch {
      variantMap = {};
    }
    return {
      // Full email shown in dashboard so admin can edit it anytime.
      apiUser: cfg.apiUser || null,
      apiUserConfigured: !!cfg.apiUser,
      apiPasswordConfigured: !!cfg.apiPassword,
      storefrontTokenConfigured: !!cfg.storefrontToken,
      webhookSecretConfigured: !!cfg.webhookSecret,
      apiUserHint: cfg.apiUser ? cfg.apiUser.slice(-18) : null,
      shopDomain: cfg.shopDomain || null,
      shopName: null as string | null,
      webhookUrl: 'https://api.adnova.bbs.tr/api/v1/payments/fourthwall/webhook',
      variantMap,
      configured: !!(
        cfg.apiUser &&
        cfg.apiPassword &&
        cfg.storefrontToken &&
        cfg.shopDomain
      ),
    };
  }

  /** Full secrets for dashboard "show keys" — admin session only. */
  async revealFourthwallAdminSettings() {
    const cfg = await this.loadFourthwallConfig();
    return {
      apiUser: cfg.apiUser || '',
      apiPassword: cfg.apiPassword || '',
      storefrontToken: cfg.storefrontToken || '',
      shopDomain: cfg.shopDomain || '',
      webhookSecret: cfg.webhookSecret || '',
    };
  }

  async updateFourthwallAdminSettings(body: {
    apiUser?: string;
    apiPassword?: string;
    storefrontToken?: string;
    shopDomain?: string;
    webhookSecret?: string;
    clearApiPassword?: boolean;
    clearStorefrontToken?: boolean;
    clearWebhookSecret?: boolean;
  }) {
    const row = await this.settingsRepo.findOne({
      where: { key: 'fourthwall_config' },
    });
    let cur: Record<string, any> = {};
    try {
      const raw = row?.value as any;
      cur = typeof raw === 'string' ? JSON.parse(raw || '{}') : { ...(raw || {}) };
    } catch {
      cur = {};
    }
    if (body.apiUser !== undefined && String(body.apiUser).trim()) {
      cur.apiUser = String(body.apiUser).trim();
    }
    if (body.apiPassword !== undefined && String(body.apiPassword).trim()) {
      cur.apiPassword = String(body.apiPassword).trim();
    }
    if (body.clearApiPassword) cur.apiPassword = '';
    if (body.storefrontToken !== undefined && String(body.storefrontToken).trim()) {
      cur.storefrontToken = String(body.storefrontToken).trim();
    }
    if (body.clearStorefrontToken) cur.storefrontToken = '';
    if (body.shopDomain !== undefined && String(body.shopDomain).trim()) {
      cur.shopDomain = String(body.shopDomain)
        .trim()
        .replace(/^https?:\/\//i, '')
        .replace(/\/+$/, '');
    }
    if (body.webhookSecret !== undefined && String(body.webhookSecret).trim()) {
      cur.webhookSecret = String(body.webhookSecret).trim();
    }
    if (body.clearWebhookSecret) cur.webhookSecret = '';
    cur.updatedAt = new Date().toISOString();

    const encoded = JSON.stringify(cur);
    if (row) {
      row.value = encoded;
      await this.settingsRepo.save(row);
    } else {
      await this.settingsRepo.save(
        this.settingsRepo.create({
          key: 'fourthwall_config',
          value: encoded,
          description: 'Fourthwall card payment credentials',
        }),
      );
    }
    return this.getFourthwallAdminSettings();
  }

  /** Sham Cash (Syria): manual account QR + WhatsApp proof — no payment API. */
  private async loadShamCashConfig(): Promise<{
    enabled: boolean;
    whatsapp: string;
    displayName: string;
    accountName: string;
    accountId: string;
    instructions: string;
  }> {
    const row = await this.settingsRepo.findOne({ where: { key: 'sham_cash_config' } });
    let cur: Record<string, any> = {};
    try {
      const raw = row?.value as any;
      cur = typeof raw === 'string' ? JSON.parse(raw || '{}') : { ...(raw || {}) };
    } catch {
      cur = {};
    }
    return {
      enabled: cur.enabled === true || cur.enabled === 'true' || cur.enabled === 1,
      whatsapp: String(cur.whatsapp || '').trim(),
      displayName: String(cur.displayName || 'شام كاش').trim() || 'شام كاش',
      accountName: String(cur.accountName || '').trim(),
      accountId: String(cur.accountId || cur.iban || cur.accountNumber || '')
        .trim()
        .toLowerCase(),
      instructions: String(cur.instructions || '').trim(),
    };
  }

  private async ensureShamCashProviderEnum() {
    try {
      await this.dataSource.query(`
        DO $$ BEGIN
          ALTER TYPE recharge_orders_provider_enum ADD VALUE IF NOT EXISTS 'sham_cash';
        EXCEPTION WHEN others THEN
          BEGIN
            ALTER TYPE "recharge_orders_provider_enum" ADD VALUE IF NOT EXISTS 'sham_cash';
          EXCEPTION WHEN others THEN NULL;
          END;
        END $$;
      `);
    } catch (e) {
      this.logger.warn(`sham_cash enum ensure skipped: ${(e as Error).message || e}`);
    }
  }

  async getShamCashAdminSettings() {
    const cfg = await this.loadShamCashConfig();
    const digits = cfg.whatsapp.replace(/\D/g, '');
    const hasAccount = cfg.accountId.length >= 8;
    return {
      enabled: cfg.enabled,
      whatsapp: cfg.whatsapp,
      displayName: cfg.displayName,
      accountName: cfg.accountName,
      accountId: cfg.accountId,
      instructions: cfg.instructions,
      configured: hasAccount && digits.length >= 8,
    };
  }

  async updateShamCashAdminSettings(body: {
    enabled?: boolean;
    whatsapp?: string;
    displayName?: string;
    accountName?: string;
    accountId?: string;
    instructions?: string;
  }) {
    const cur = await this.loadShamCashConfig();
    if (body.enabled !== undefined) cur.enabled = !!body.enabled;
    if (body.whatsapp !== undefined) {
      cur.whatsapp = String(body.whatsapp || '')
        .trim()
        .replace(/[^\d+\s-]/g, '');
    }
    if (body.displayName !== undefined) {
      cur.displayName = String(body.displayName || '').trim() || 'شام كاش';
    }
    if (body.accountName !== undefined) {
      cur.accountName = String(body.accountName || '').trim();
    }
    if (body.accountId !== undefined) {
      cur.accountId = String(body.accountId || '')
        .trim()
        .replace(/\s+/g, '')
        .toLowerCase();
    }
    if (body.instructions !== undefined) {
      cur.instructions = String(body.instructions || '').trim();
    }
    const encoded = JSON.stringify({ ...cur, updatedAt: new Date().toISOString() });
    const row = await this.settingsRepo.findOne({ where: { key: 'sham_cash_config' } });
    if (row) {
      row.value = encoded;
      await this.settingsRepo.save(row);
    } else {
      await this.settingsRepo.save(
        this.settingsRepo.create({
          key: 'sham_cash_config',
          value: encoded,
          description: 'Sham Cash (Syria) account + WhatsApp manual recharge',
        }),
      );
    }
    return this.getShamCashAdminSettings();
  }

  /** Public: ready when account id exists (QR). WhatsApp used after payment proof. */
  async getShamCashPublicConfig() {
    const cfg = await this.loadShamCashConfig();
    const hasAccount = cfg.accountId.length >= 8;
    const digits = cfg.whatsapp.replace(/\D/g, '');
    return {
      enabled: hasAccount,
      whatsapp: digits.length >= 8 ? cfg.whatsapp : '',
      displayName: cfg.displayName,
      accountName: cfg.accountName || cfg.displayName,
      accountId: hasAccount ? cfg.accountId : '',
      instructions: hasAccount
        ? cfg.instructions ||
          'حوّل عبر شام كاش بالمسح أو الرقم، ثم أرسل صورة الإثبات على واتساب.'
        : '',
    };
  }

  async createShamCashOrder(userId: string, dto: { sku: string }) {
    await this.ensureShamCashProviderEnum();
    const cfg = await this.loadShamCashConfig();
    if (cfg.accountId.length < 8) {
      throw new ServiceUnavailableException(
        'شام كاش غير مُعدّ — أضف رقم الحساب من لوحة التحكم',
      );
    }
    const pkg = await this.walletService.resolvePackageBySku(dto.sku);

    await this.ordersRepo
      .createQueryBuilder()
      .update(RechargeOrder)
      .set({ status: RechargeStatus.CANCELLED })
      .where('userId = :userId', { userId })
      .andWhere('provider = :provider', { provider: PaymentProvider.SHAM_CASH })
      .andWhere('status = :status', { status: RechargeStatus.PENDING })
      .execute();

    const order = await this.ordersRepo.save(
      this.ordersRepo.create({
        userId,
        sku: pkg.sku,
        coins: pkg.coins,
        bonusCoins: pkg.bonusCoins,
        amountFiat: pkg.priceUsd,
        currency: 'USD',
        provider: PaymentProvider.SHAM_CASH,
        status: RechargeStatus.PENDING,
        expiresAt: new Date(Date.now() + 24 * 60 * 60 * 1000),
        providerPayload: {
          channel: 'sham_cash',
          accountId: cfg.accountId,
          accountName: cfg.accountName || cfg.displayName,
        },
      }),
    );
    const claimCode = this.claimCodeForOrder(order.id);
    order.providerOrderId = claimCode;
    await this.ordersRepo.save(order);

    const digits = cfg.whatsapp.replace(/\D/g, '');
    return {
      order: {
        id: order.id,
        status: order.status,
        sku: order.sku,
        coins: order.coins,
        bonusCoins: order.bonusCoins,
        amountFiat: Number(order.amountFiat),
        claimCode,
      },
      payment: {
        accountId: cfg.accountId,
        accountName: cfg.accountName || cfg.displayName,
        displayName: cfg.displayName,
        whatsapp: digits.length >= 8 ? cfg.whatsapp : '',
        qrPayload: cfg.accountId,
        instructions:
          cfg.instructions ||
          'حوّل عبر شام كاش ثم أرسل صورة الإثبات على واتساب مع رقم الطلب.',
      },
    };
  }

  async getShamCashOrder(userId: string, orderId: string) {
    const order = await this.walletService.getOrderForUser(orderId, userId);
    if (order.provider !== PaymentProvider.SHAM_CASH) {
      throw new NotFoundException('Order not found');
    }
    return {
      id: order.id,
      status: order.status,
      sku: order.sku,
      coins: order.coins,
      bonusCoins: order.bonusCoins,
      amountFiat: Number(order.amountFiat),
      claimCode: order.providerOrderId || this.claimCodeForOrder(order.id),
    };
  }

  async testFourthwallConnection() {
    const client = await this.fourthwallClient();
    if (!client.configured) {
      throw new BadRequestException('Fourthwall credentials incomplete');
    }
    const shop = await client.getShop();
    const publicDomain = String(
      shop?.publicDomain || shop?.domain || shop?.baseUrl || '',
    )
      .replace(/^https?:\/\//i, '')
      .replace(/\/+$/, '');
    // Persist domain when discovered so checkout can open.
    if (publicDomain) {
      const cfg = await this.loadFourthwallConfig();
      if (!cfg.shopDomain || cfg.shopDomain !== publicDomain) {
        await this.updateFourthwallAdminSettings({ shopDomain: publicDomain });
      }
    }
    return {
      ok: true,
      name: shop?.name || null,
      publicDomain: publicDomain || null,
      domain: publicDomain || null,
      status: shop?.status || null,
    };
  }

  async syncFourthwallPackages() {
    const client = await this.fourthwallClient();
    if (!client.configured) {
      throw new BadRequestException('Fourthwall credentials incomplete');
    }
    // Always sync dashboard/DB packages (includes custom $100 / $200 etc).
    const catalog = await this.walletService.listPackages();
    const packages = Array.isArray(catalog?.items) && catalog.items.length
      ? catalog.items
      : STANDARD_RECHARGE_PACKAGES;
    const results: Array<Record<string, unknown>> = [];
    for (const raw of packages as Array<Record<string, unknown>>) {
      const sku = String(raw.sku || '').trim();
      if (!sku) continue;
      const coins = Number(raw.coins) || 0;
      const bonusCoins = Number(raw.bonusCoins) || 0;
      const priceUsd = Number(raw.priceUsd) || 0;
      try {
        const product = await client.ensureCoinPackageProduct({
          sku,
          coins,
          bonusCoins,
          priceUsd,
        });
        await this.rememberFourthwallVariant(sku, product.variantId);
        results.push({
          sku,
          ok: true,
          variantId: product.variantId,
          productId: product.productId,
          priceUsd,
          livePriceUsd: product.priceUsd,
          coins,
        });
      } catch (e) {
        results.push({
          sku,
          ok: false,
          error: (e as Error).message || String(e),
        });
      }
    }
    return { items: results };
  }

  async getFourthwallOrderStatus(userId: string, orderId: string) {
    let order = await this.ordersRepo.findOne({ where: { id: orderId } });
    if (!order || order.userId !== userId) {
      throw new NotFoundException('Order not found');
    }
    if (order.provider !== PaymentProvider.FOURTHWALL) {
      throw new BadRequestException('Not a Fourthwall order');
    }
    // After card success Fourthwall often shows an "order" page while the webhook is late.
    // Poll path actively reconciles via Open API so the app can leave checkout → wallet.
    if (order.status === RechargeStatus.PENDING) {
      try {
        await this.reconcilePendingFourthwallOrder(order);
        order =
          (await this.ordersRepo.findOne({ where: { id: orderId } })) || order;
      } catch (e) {
        this.logger.warn(
          `Fourthwall status reconcile failed: ${(e as Error).message || e}`,
        );
      }
    }
    return {
      id: order.id,
      status: order.status,
      sku: order.sku,
      coins: order.coins,
      bonusCoins: order.bonusCoins,
      amountFiat: order.amountFiat,
      currency: order.currency,
      claimCode: this.claimCodeForOrder(order.id),
      checkoutUrl: (order.providerPayload as any)?.checkoutUrl || null,
      completedAt: order.completedAt,
    };
  }

  /**
   * When webhook doesn't arrive (or is delayed), match our pending JEHO order to a
   * recent Fourthwall order using cartId / email / SKU / amount and credit coins.
   */
  private async reconcilePendingFourthwallOrder(
    order: RechargeOrder,
  ): Promise<boolean> {
    if (order.status === RechargeStatus.COMPLETED) return true;

    const payload = (order.providerPayload || {}) as Record<string, any>;
    const lastAt = payload.lastReconcileAt
      ? new Date(String(payload.lastReconcileAt)).getTime()
      : 0;
    // Avoid hammering Open API on every 1–2s poll from the app.
    if (lastAt && Date.now() - lastAt < 6000) {
      return false;
    }
    order.providerPayload = {
      ...payload,
      lastReconcileAt: new Date().toISOString(),
    };
    await this.ordersRepo.save(order);

    const client = await this.fourthwallClient();
    if (!client.configured) return false;

    const email = String(payload.userEmail || '')
      .trim()
      .toLowerCase();
    const cartId = String(order.providerOrderId || payload.cartId || '').trim();
    const createdAt = order.createdAt
      ? new Date(order.createdAt)
      : new Date(Date.now() - 60 * 60 * 1000);
    const since = new Date(createdAt.getTime() - 10 * 60 * 1000).toISOString();

    let rows: Record<string, any>[] = [];
    if (email.includes('@')) {
      try {
        rows = await client.listOrders({
          size: 15,
          email,
          createdAtGt: since,
        });
      } catch (e) {
        this.logger.warn(
          `Fourthwall listOrders by email: ${(e as Error).message || e}`,
        );
      }
    }
    if (!rows.length) {
      try {
        rows = await client.listOrders({ size: 25, createdAtGt: since });
      } catch (e) {
        this.logger.warn(
          `Fourthwall listOrders recent: ${(e as Error).message || e}`,
        );
      }
    }

    for (const raw of rows) {
      if (!this.fourthwallOrderMatchesLocal(order, raw)) continue;
      const data = {
        ...raw,
        cartId:
          raw.cartId ||
          raw.cart?.id ||
          raw.checkout?.cartId ||
          cartId ||
          undefined,
        // Help credit matcher when webhook payload is incomplete.
        note: `JEHO_SKU:${order.sku} ${this.claimCodeForOrder(order.id)}`,
      };
      const ok = await this.creditFourthwallOrder(data);
      if (ok) {
        this.logger.log(
          `Fourthwall reconciled order=${order.id} via Open API list fw=${raw.id || raw.friendlyId}`,
        );
        return true;
      }
    }
    return false;
  }

  private fourthwallOrderMatchesLocal(
    order: RechargeOrder,
    data: Record<string, any>,
  ): boolean {
    if (!data || typeof data !== 'object') return false;
    const status = String(data.status || data.orderStatus || '').toUpperCase();
    if (status && /CANCEL|FAIL|REFUND|VOID/.test(status)) return false;

    const cartId = String(
      data.cartId || data.cart?.id || data.checkout?.cartId || '',
    ).trim();
    const localCart = String(
      order.providerOrderId ||
        (order.providerPayload as any)?.cartId ||
        '',
    ).trim();
    if (cartId && localCart && cartId === localCart) return true;

    const blob = JSON.stringify(data);
    const skuMatch = blob.match(/JEHO_SKU:([a-zA-Z0-9_\-]+)/i);
    const skuFromDesc = skuMatch ? skuMatch[1] : '';
    if (skuFromDesc && skuFromDesc !== order.sku) return false;

    const total = Number(
      data?.amounts?.total?.value ??
        data?.amounts?.subtotal?.value ??
        data?.total?.value ??
        data?.total ??
        NaN,
    );
    const amountOk =
      !Number.isFinite(total) ||
      Math.abs(total - Number(order.amountFiat || 0)) < 0.08;

    const email = String(
      data.email ||
        data.customer?.email ||
        data.supporter?.email ||
        data.username ||
        '',
    )
      .trim()
      .toLowerCase();
    const localEmail = String(
      (order.providerPayload as any)?.userEmail || '',
    )
      .trim()
      .toLowerCase();
    const emailOk =
      !email ||
      !localEmail ||
      email === localEmail;

    // Prefer amount match (unique pack prices); email alone is too loose for shops
    // with multiple open carts — but we only credit ONE matching pending via credit().
    if (amountOk && emailOk) return true;
    if (skuFromDesc && amountOk) return true;
    return false;
  }

  async handleFourthwallWebhook(rawBody: Buffer, signatureHeader?: string) {
    const cfg = await this.loadFourthwallConfig();
    const secret = cfg.webhookSecret || '';
    if (secret) {
      const expected = crypto
        .createHmac('sha256', secret)
        .update(rawBody)
        .digest('base64');
      const given = String(signatureHeader || '').trim();
      const ok =
        !!given &&
        given.length === expected.length &&
        crypto.timingSafeEqual(Buffer.from(given), Buffer.from(expected));
      if (!ok) {
        this.logger.warn('Fourthwall webhook signature mismatch');
        throw new BadRequestException('Invalid Fourthwall signature');
      }
    } else {
      this.logger.warn(
        'Fourthwall webhook secret missing — accepting unsigned (set in dashboard)',
      );
    }

    let event: any;
    try {
      event = JSON.parse(rawBody.toString('utf8'));
    } catch {
      throw new BadRequestException('Invalid JSON');
    }

    const type = String(event?.type || '');
    await this.webhookEventsRepo.save(
      this.webhookEventsRepo.create({
        provider: 'fourthwall',
        eventId: String(event?.id || `${type}-${Date.now()}`),
        eventType: type || 'unknown',
        payload: event,
      } as any),
    ).catch(() => undefined);

    if (type !== 'ORDER_PLACED' && type !== 'ORDER_UPDATED') {
      return { received: true, type, credited: false };
    }

    let data = (event?.data || {}) as Record<string, any>;
    // ORDER_UPDATED nests the order under data.order
    if (data && data.order && typeof data.order === 'object') {
      data = { ...data.order, update: data.update, checkoutId: data.order.checkoutId || data.checkoutId };
    }
    const fwId = String(data.id || data.orderId || '');
    if (fwId) {
      try {
        const client = await this.fourthwallClient();
        if (client.configured) {
          const full = await client.getOrder(fwId);
          if (full && typeof full === 'object') {
            data = { ...full, ...data };
          }
        }
      } catch (e) {
        this.logger.warn(
          `Fourthwall getOrder enrich failed: ${(e as Error).message || e}`,
        );
      }
    }
    const credited = await this.creditFourthwallOrder(data);
    return { received: true, type, credited };
  }

  private async creditFourthwallOrder(
    data: Record<string, any>,
  ): Promise<boolean> {
    const fwOrderId = String(data.id || data.orderId || '');
    const blob = JSON.stringify(data).toUpperCase();
    const claimMatch = blob.match(/JEHO-[A-Z0-9]{6,12}/);
    const claimCode = claimMatch ? claimMatch[0] : '';
    const skuFromDesc = (() => {
      const m = JSON.stringify(data).match(/JEHO_SKU:([a-zA-Z0-9_\-]+)/i);
      return m ? m[1] : '';
    })();
    const friendlyId = String(data.friendlyId || data.number || '').trim();
    const checkoutId = String(
      data.checkoutId || data.checkout?.id || '',
    ).trim();

    const offers: any[] = Array.isArray(data.offers)
      ? data.offers
      : Array.isArray(data.items)
        ? data.items
        : [];
    const variantIds = offers
      .map(
        (o) =>
          o?.variant?.id ||
          o?.variantId ||
          o?.offerVariantId ||
          o?.variant?.uuid,
      )
      .filter(Boolean)
      .map(String);
    const variantSkus = offers
      .map((o) => o?.variant?.sku || o?.sku)
      .filter(Boolean)
      .map(String);

    let order: RechargeOrder | null = null;

    // 0) Already credited for this Fourthwall order / friendly id
    if (fwOrderId || friendlyId) {
      const existing = await this.ordersRepo.findOne({
        where: [
          ...(fwOrderId
            ? [{ providerPaymentId: fwOrderId, provider: PaymentProvider.FOURTHWALL } as any]
            : []),
          ...(friendlyId
            ? [{ providerPaymentId: friendlyId, provider: PaymentProvider.FOURTHWALL } as any]
            : []),
        ],
      });
      if (existing?.status === RechargeStatus.COMPLETED) return true;
    }

    // 1) cartId stored as providerOrderId (primary — no user note needed)
    const cartId = String(
      data.cartId ||
        data.cart?.id ||
        data.checkout?.cartId ||
        data.checkoutCartId ||
        '',
    );
    if (cartId) {
      order = await this.ordersRepo.findOne({
        where: {
          provider: PaymentProvider.FOURTHWALL,
          providerOrderId: cartId,
          status: RechargeStatus.PENDING,
        },
      });
      if (!order) {
        const recent = await this.ordersRepo.find({
          where: {
            provider: PaymentProvider.FOURTHWALL,
            status: RechargeStatus.PENDING,
          },
          order: { createdAt: 'DESC' },
          take: 40,
        });
        order =
          recent.find((o) => {
            const p = (o.providerPayload || {}) as Record<string, any>;
            return String(p.cartId || '') === cartId;
          }) || null;
      }
    }

    // 1b) checkoutId stored in payload after redirect / enrich
    if (!order && checkoutId) {
      const recent = await this.ordersRepo.find({
        where: {
          provider: PaymentProvider.FOURTHWALL,
          status: RechargeStatus.PENDING,
        },
        order: { createdAt: 'DESC' },
        take: 40,
      });
      order =
        recent.find((o) => {
          const p = (o.providerPayload || {}) as Record<string, any>;
          return String(p.checkoutId || '') === checkoutId;
        }) || null;
    }

    // 2) Claim code if present anywhere in payload
    if (!order && claimCode) {
      const suffix = claimCode.replace(/^JEHO-/i, '').toLowerCase();
      const candidates = await this.ordersRepo.find({
        where: {
          provider: PaymentProvider.FOURTHWALL,
          status: RechargeStatus.PENDING,
        },
        order: { createdAt: 'DESC' },
        take: 80,
      });
      order =
        candidates.find((o) =>
          o.id.replace(/-/g, '').toLowerCase().endsWith(suffix),
        ) || null;
    }

    // 3) Email match + pending (Google account email when autofilled)
    if (!order) {
      const email = String(
        data.email ||
          data.customer?.email ||
          data.supporter?.email ||
          data.username ||
          '',
      )
        .trim()
        .toLowerCase();
      if (email.includes('@')) {
        const user = await this.usersRepo.findOne({ where: { email } as any });
        if (user) {
          const pending = await this.ordersRepo.find({
            where: {
              userId: user.id,
              provider: PaymentProvider.FOURTHWALL,
              status: RechargeStatus.PENDING,
            },
            order: { createdAt: 'DESC' },
            take: 5,
          });
          order = pending[0] || null;
        }
      }
    }

    // 4) SKU from product description JEHO_SKU:coins_10000 (most reliable for digital)
    if (!order && skuFromDesc) {
      const hourAgo = new Date(Date.now() - 2 * 60 * 60 * 1000);
      const pending = await this.ordersRepo
        .createQueryBuilder('o')
        .where('o.provider = :p', { provider: PaymentProvider.FOURTHWALL })
        .andWhere('o.status = :s', { status: RechargeStatus.PENDING })
        .andWhere('o.sku = :sku', { sku: skuFromDesc })
        .andWhere('o.createdAt >= :since', { since: hourAgo })
        .orderBy('o.createdAt', 'DESC')
        .getMany();
      if (pending.length === 1) order = pending[0];
      else if (pending.length > 1) {
        const total = Number(data?.amounts?.total?.value ?? data?.total ?? NaN);
        const byAmount = Number.isFinite(total)
          ? pending.filter((o) => Math.abs(Number(o.amountFiat) - total) < 0.02)
          : [];
        if (byAmount.length === 1) order = byAmount[0];
        else order = pending[0];
      }
    }

    // 5) Single recent pending matching mapped variant
    if (!order && variantIds.length) {
      const mapSetting = await this.settingsRepo.findOne({
        where: { key: 'fourthwall_variant_map' },
      });
      let map: Record<string, string> = {};
      try {
        const raw =
          (mapSetting?.value as any) ||
          this.configService.get<string>('app.fourthwall.variantMapJson') ||
          '{}';
        map = typeof raw === 'string' ? JSON.parse(raw) : (raw as any);
      } catch {
        map = {};
      }
      const skus = Object.entries(map)
        .filter(([, v]) => variantIds.includes(String(v)))
        .map(([k]) => k);
      if (skus.length) {
        const hourAgo = new Date(Date.now() - 60 * 60 * 1000);
        const allPending = await this.ordersRepo
          .createQueryBuilder('o')
          .where('o.provider = :p', { provider: PaymentProvider.FOURTHWALL })
          .andWhere('o.status = :s', { status: RechargeStatus.PENDING })
          .andWhere('o.sku = :sku', { sku: skus[0] })
          .andWhere('o.createdAt >= :since', { since: hourAgo })
          .orderBy('o.createdAt', 'DESC')
          .getMany();
        if (allPending.length >= 1) order = allPending[0];
      }
    }

    if (!order) {
      this.logger.warn(
        `Fourthwall order unmatched fw=${fwOrderId} cart=${cartId} claim=${claimCode} sku=${skuFromDesc} variants=${variantIds.join(',')}`,
      );
      return false;
    }

    if (order.status === RechargeStatus.COMPLETED) return true;

    const paymentRef =
      friendlyId ||
      fwOrderId ||
      String(order.providerOrderId || '');
    order.providerPaymentId = paymentRef.slice(0, 240);
    order.providerPayload = {
      ...(order.providerPayload || {}),
      webhook: {
        fwOrderId,
        friendlyId,
        checkoutId,
        cartId,
        claimCode,
        skuFromDesc,
        variantIds,
        variantSkus,
        at: new Date().toISOString(),
      },
    };
    await this.ordersRepo.save(order);
    await this.walletService.completeRecharge(order.id, paymentRef);
    this.logger.log(
      `Fourthwall credited order=${order.id} user=${order.userId} coins=${order.coins} ref=${paymentRef}`,
    );
    return true;
  }

  async handleStripeWebhook(rawBody: Buffer, signature: string) {
    const secret = this.configService.get<string>('app.stripe.webhookSecret');
    if (!this.stripe || !secret) {
      throw new BadRequestException('Stripe webhook not configured');
    }

    let event: Stripe.Event;
    try {
      event = this.stripe.webhooks.constructEvent(rawBody, signature, secret);
    } catch (err) {
      this.logger.warn(`Stripe signature verify failed: ${(err as Error).message}`);
      throw new BadRequestException('Invalid Stripe signature');
    }

    if (event.type === 'checkout.session.completed') {
      const session = event.data.object as Stripe.Checkout.Session;
      const orderId = session.metadata?.orderId;
      if (orderId) {
        await this.walletService.completeRecharge(orderId, session.payment_intent as string);
      }
    }

    return { received: true, type: event.type };
  }

  async createPaypalOrder(userId: string, dto: PaypalCreateDto) {
    const pkg = await this.walletService.resolvePackageBySku(dto.sku);
    const order = await this.ordersRepo.save(
      this.ordersRepo.create({
        userId,
        sku: pkg.sku,
        coins: pkg.coins,
        bonusCoins: pkg.bonusCoins,
        amountFiat: pkg.priceUsd,
        currency: dto.currency || 'USD',
        provider: PaymentProvider.PAYPAL,
        status: RechargeStatus.PENDING,
        providerPayload: {
          mode: this.configService.get('app.paypal.mode'),
          clientIdConfigured: !!this.configService.get('app.paypal.clientId'),
        },
      }),
    );

    return {
      order,
      approveHint:
        'PayPal order created as pending. Capture requires server-side PayPal verification webhook.',
      capturePath: `/api/v1/payments/paypal/${order.id}/capture`,
    };
  }

  async capturePaypal(_orderId: string, _paypalCaptureId: string) {
    throw new BadRequestException('Provider verification not configured');
  }

  /**
   * Google Play Billing verification.
   * Resolves coins/price from recharge_packages or store_offers by productId (SKU).
   */
  async verifyPlayBilling(userId: string, dto: PlayBillingVerifyDto) {
    if (!dto.purchaseToken || dto.purchaseToken.length < 8) {
      throw new BadRequestException('Invalid purchase token');
    }
    if (!dto.productId) {
      throw new BadRequestException('productId required');
    }

    const pkg = await this.resolveBillableProduct(dto.productId);
    if (!pkg) {
      throw new BadRequestException(`Unknown product SKU: ${dto.productId}`);
    }
    const baseCoins = Math.max(1, Number(pkg.coins || 0));
    const bonusCoins = Math.max(0, Number(pkg.bonusCoins || 0));
    const amountFiat = Number(pkg.priceUsd || dto.amountFiat || 0);

    // Idempotency: same purchase token already credited
    const existing = await this.ordersRepo.findOne({
      where: {
        providerPaymentId: dto.purchaseToken,
        provider: PaymentProvider.GOOGLE_PLAY,
        status: RechargeStatus.COMPLETED,
      },
    });
    if (existing) {
      return this.walletService.getWallet(userId);
    }

    const packageName = this.configService.get<string>('app.googlePlay.packageName');
    const enforce = !!this.configService.get<boolean>('app.googlePlay.enforce');
    type PlayPurchase = {
      purchaseState?: number;
      consumptionState?: number;
      orderId?: string;
      purchaseTimeMillis?: string;
      acknowledgementState?: number;
    };
    let purchase: PlayPurchase = {};
    let verifiedWithGoogle = false;
    try {
      const authClient = await this.googlePlayAuth.getClient();
      const encodedToken = encodeURIComponent(dto.purchaseToken);
      const encodedProduct = encodeURIComponent(dto.productId);
      const encodedPackage = encodeURIComponent(packageName || '');
      const response = await authClient.request<PlayPurchase>({
        method: 'GET',
        url:
          `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/` +
          `${encodedPackage}/purchases/products/${encodedProduct}/tokens/${encodedToken}`,
      });
      purchase = response.data || {};
      verifiedWithGoogle = true;
    } catch (error) {
      const status = (error as { response?: { status?: number } })?.response?.status;
      this.logger.warn(
        `Google Play verification failed (${status || 'configuration'}): ${(error as Error).message}`,
      );
      if (status === 400 || status === 404) {
        throw new BadRequestException('Google Play purchase is invalid');
      }
      if (enforce) {
        throw new ServiceUnavailableException('Google Play verification is unavailable');
      }
      this.logger.warn('Google Play credentials missing — allowing structural verify in non-prod');
    }

    if (verifiedWithGoogle && purchase.purchaseState !== 0) {
      throw new BadRequestException('Google Play purchase is not completed');
    }

    const order = await this.ordersRepo.save(
      this.ordersRepo.create({
        userId,
        sku: String(pkg.sku || dto.productId),
        coins: baseCoins,
        bonusCoins,
        amountFiat,
        currency: 'USD',
        provider: PaymentProvider.GOOGLE_PLAY,
        status: RechargeStatus.PENDING,
        providerOrderId: purchase.orderId || dto.orderId || null,
        providerPaymentId: dto.purchaseToken,
        providerPayload: {
          productId: dto.productId,
          packageName,
          verified: verifiedWithGoogle,
          purchaseState: purchase.purchaseState,
          consumptionState: purchase.consumptionState,
          acknowledgementState: purchase.acknowledgementState,
          purchaseTimeMillis: purchase.purchaseTimeMillis,
        },
      }),
    );

    await this.walletService.completeRecharge(order.id, dto.purchaseToken);
    return this.walletService.getWallet(userId);
  }

  async createCryptoOrder(userId: string, dto: CryptoOrderDto) {
    const pkg = await this.walletService.resolvePackageBySku(dto.sku);
    const order = await this.ordersRepo.save(
      this.ordersRepo.create({
        userId,
        sku: pkg.sku,
        coins: pkg.coins,
        bonusCoins: pkg.bonusCoins,
        amountFiat: pkg.priceUsd,
        currency: dto.cryptoCurrency,
        provider: PaymentProvider.CRYPTO,
        status: RechargeStatus.PENDING,
        providerPayload: {
          cryptoCurrency: dto.cryptoCurrency,
          metadata: dto.metadata || {},
          createdAt: new Date().toISOString(),
        },
      }),
    );
    return {
      order,
      paymentAddressHint:
        'Crypto order created as pending. Confirmation requires processor webhook verification.',
    };
  }

  async confirmCrypto(_orderId: string, _txHash: string) {
    throw new BadRequestException('Provider verification not configured');
  }

  async getBinanceWalletDepositAddress(userId: string, dto: BinanceWalletDepositAddressDto) {
    void userId;
    const network = this.normalizeBinanceWalletNetwork(dto.network);
    await this.paymentSettingsService.assertBinanceWalletConfiguredForProduction();
    const deposit = await this.fetchBinanceDepositAddress(network);
    return {
      coin: BINANCE_WALLET_COIN,
      network,
      address: deposit.address,
      tag: deposit.tag || null,
    };
  }

  async createBinanceWalletOrder(userId: string, dto: BinanceWalletOrderDto) {
    const network = this.normalizeBinanceWalletNetwork(dto.network);
    const pkg = await this.walletService.resolvePackageBySku(dto.sku);
    // Package store prices are USD (e.g. 4.99) — keep 2 decimals, no trailing zeros noise.
    const expectedAmount = Number(Number(pkg.priceUsd).toFixed(2));
    return this.createBinanceUsdtDepositOrder({
      userId,
      network,
      expectedAmount,
      sku: pkg.sku,
      coins: pkg.coins,
      bonusCoins: pkg.bonusCoins,
      purpose: 'wallet_recharge',
    });
  }

  /**
   * Shared USDT deposit order used by wallet packages and agent membership.
   * Amount matching + reconcile poll detect payment in realtime.
   */
  async createBinanceUsdtDepositOrder(opts: {
    userId: string;
    network: string;
    expectedAmount: number;
    sku: string;
    coins?: number;
    bonusCoins?: number;
    purpose?: string;
    meta?: Record<string, unknown>;
    uniqueAmount?: boolean;
  }) {
    const network = this.normalizeBinanceWalletNetwork(opts.network);
    const base = Number(opts.expectedAmount);
    if (!(base > 0) || !Number.isFinite(base)) {
      throw new BadRequestException('expectedAmount must be a positive number');
    }
    // Unique cents for custom quotes (agent) so concurrent same-amount deposits still match.
    let expectedAmount = Number(base.toFixed(2));
    if (opts.uniqueAmount) {
      const uniqueCents = 1 + Math.floor(Math.random() * 9); // +0.01 .. +0.09
      expectedAmount = Number((expectedAmount + uniqueCents / 100).toFixed(2));
    }
    const expiresAt = new Date(Date.now() + 30 * 60 * 1000);

    let deposit: { address: string; tag?: string };
    try {
      await this.paymentSettingsService.assertBinanceWalletConfiguredForProduction();
      deposit = await this.fetchBinanceDepositAddress(network);
    } catch (err) {
      if (err instanceof BadRequestException) throw err;
      deposit = { address: '', tag: undefined };
    }

    const purpose = opts.purpose || 'wallet_recharge';
    const order = await this.ordersRepo.save(
      this.ordersRepo.create({
        userId: opts.userId,
        sku: opts.sku,
        coins: Math.max(0, Number(opts.coins) || 0),
        bonusCoins: Math.max(0, Number(opts.bonusCoins) || 0),
        amountFiat: expectedAmount,
        currency: BINANCE_WALLET_COIN,
        provider: PaymentProvider.BINANCE_WALLET,
        status: RechargeStatus.PENDING,
        expiresAt,
        providerPayload: {
          coin: BINANCE_WALLET_COIN,
          network,
          address: deposit.address,
          tag: deposit.tag || null,
          expectedAmount,
          purpose,
          meta: opts.meta || null,
          createdAt: new Date().toISOString(),
        },
      }),
    );

    if (!deposit.address) {
      return {
        order,
        coin: BINANCE_WALLET_COIN,
        network,
        address: null,
        tag: null,
        amount: expectedAmount,
        expiresAt: order.expiresAt,
        purpose,
        message:
          'Binance Exchange API keys not configured — pending order created for local/dev',
      };
    }

    return {
      order,
      coin: BINANCE_WALLET_COIN,
      network,
      address: deposit.address,
      tag: deposit.tag || null,
      amount: expectedAmount,
      expiresAt: order.expiresAt,
      purpose,
    };
  }

  async getBinanceWalletOrderStatus(userId: string, orderId: string) {
    const order = await this.walletService.getOrderForUser(orderId, userId);
    if (order.provider !== PaymentProvider.BINANCE_WALLET) {
      throw new NotFoundException('Binance wallet order not found');
    }

    if (
      order.status === RechargeStatus.PENDING &&
      order.expiresAt &&
      order.expiresAt.getTime() <= Date.now()
    ) {
      order.status = RechargeStatus.CANCELLED;
      await this.ordersRepo.save(order);
    }

    if (order.status === RechargeStatus.PENDING) {
      try {
        await this.reconcileBinanceWalletDeposits({ orderId: order.id, userId });
      } catch (err) {
        this.logger.warn(`Binance wallet reconcile on status poll failed: ${(err as Error).message}`);
      }
    }

    const refreshed = await this.walletService.getOrderForUser(orderId, userId);
    const payload = (refreshed.providerPayload || {}) as Record<string, unknown>;
    return {
      order: refreshed,
      address: payload.address || null,
      tag: payload.tag || null,
      network: payload.network || null,
      coin: payload.coin || BINANCE_WALLET_COIN,
      amount: payload.expectedAmount ?? refreshed.amountFiat,
      expiresAt: refreshed.expiresAt,
    };
  }

  async reconcileBinanceWalletDeposits(options?: { orderId?: string; userId?: string }) {
    const config = await this.paymentSettingsService.getBinanceWalletConfig();
    if (!config.apiKey || !config.secretKey) {
      return {
        success: false,
        message: 'Binance Exchange API credentials not configured',
        processed: 0,
        matched: [] as Array<Record<string, unknown>>,
        orphans: [] as Array<Record<string, unknown>>,
      };
    }

    const client = this.paymentSettingsService.createClient(config);
    const qb = this.ordersRepo
      .createQueryBuilder('o')
      .where('o.provider = :provider', { provider: PaymentProvider.BINANCE_WALLET })
      .andWhere('o.status = :status', { status: RechargeStatus.PENDING });

    if (options?.userId) {
      qb.andWhere('o.userId = :userId', { userId: options.userId });
    }
    if (options?.orderId) {
      qb.andWhere('o.id = :orderId', { orderId: options.orderId });
    }

    const pendingOrders = await qb.orderBy('o.updatedAt', 'DESC').getMany();
    const now = Date.now();
    for (const stale of pendingOrders) {
      if (stale.expiresAt && stale.expiresAt.getTime() <= now) {
        stale.status = RechargeStatus.CANCELLED;
        await this.ordersRepo.save(stale);
      }
    }
    const livePending = pendingOrders.filter(
      (o) => o.status === RechargeStatus.PENDING,
    );
    const startTime = Date.now() - 48 * 60 * 60 * 1000;
    const history = await client.getDepositHistory(BINANCE_WALLET_COIN, startTime, 100);

    if (!history.success) {
      throw new ServiceUnavailableException(
        history.error || 'Failed to fetch Binance deposit history',
      );
    }

    const deposits = history.data || [];
    let processed = 0;
    const matched: Array<Record<string, unknown>> = [];
    const orphans: Array<Record<string, unknown>> = [];
    const remainingPending = [...livePending];

    for (const deposit of deposits) {
      const txId = String(deposit.txId || '');
      const amount = Number(deposit.amount || 0);
      const status = Number(deposit.status ?? 0);
      const network = String(deposit.network || '');

      if (status !== 1 || !txId || amount <= 0) continue;

      const alreadyUsed = await this.ordersRepo.findOne({
        where: {
          provider: PaymentProvider.BINANCE_WALLET,
          providerPaymentId: txId,
          status: RechargeStatus.COMPLETED,
        },
      });
      if (alreadyUsed) continue;

      const candidates = remainingPending.filter((order) => {
        const payload = (order.providerPayload || {}) as Record<string, unknown>;
        const orderNetwork = String(payload.network || '');
        if (orderNetwork && orderNetwork !== network) return false;
        const expected = Number(order.amountFiat);
        if (!(expected > 0)) return false;
        const tolerance = Math.max(expected * BINANCE_WALLET_AMOUNT_TOLERANCE, 0.00000001);
        return Math.abs(amount - expected) <= tolerance;
      });

      if (candidates.length === 0) {
        orphans.push({ txId, amount, network, coin: deposit.coin, reason: 'no_amount_match' });
        continue;
      }

      const order = candidates[0];
      try {
        await this.walletService.completeRecharge(order.id, txId);
        processed += 1;
        matched.push({
          orderId: order.id,
          userId: order.userId,
          txId,
          amount,
          network,
          coin: deposit.coin,
        });
        const idx = remainingPending.findIndex((o) => o.id === order.id);
        if (idx >= 0) remainingPending.splice(idx, 1);
      } catch (err) {
        this.logger.warn(
          `Failed to complete Binance wallet order ${order.id}: ${(err as Error).message}`,
        );
      }
    }

    return {
      success: true,
      message: `Processed ${processed} deposits`,
      processed,
      matched,
      orphans,
      pendingChecked: pendingOrders.length,
      binanceDeposits: deposits.length,
      requestedOrderId: options?.orderId || null,
      userScope: !!options?.userId,
    };
  }

  /** @deprecated Binance Pay replaced by Exchange wallet deposits */
  async createBinancePayOrder(userId: string, dto: BinancePayOrderDto) {
    return this.createBinanceWalletOrder(userId, { sku: dto.sku, network: 'TRX' });
  }

  /** @deprecated */
  async handleBinancePayWebhook(
    _rawBody: Buffer,
    _headers: Record<string, string | string[] | undefined>,
  ) {
    throw new BadRequestException(
      'Binance Pay webhooks are disabled. Use Binance Exchange wallet deposit reconciliation.',
    );
  }

  /** @deprecated */
  async getBinanceOrderStatus(userId: string, orderId: string) {
    return this.getBinanceWalletOrderStatus(userId, orderId);
  }

  private normalizeBinanceWalletNetwork(network: string): 'TRX' | 'BSC' {
    const normalized = String(network || '').trim().toUpperCase();
    if (!BINANCE_WALLET_NETWORKS.has(normalized)) {
      throw new BadRequestException('network must be TRX or BSC');
    }
    return normalized as 'TRX' | 'BSC';
  }

  private async fetchBinanceDepositAddress(network: 'TRX' | 'BSC') {
    const config = await this.paymentSettingsService.getBinanceWalletConfig();
    if (!config.apiKey || !config.secretKey) {
      throw new BadRequestException('Binance Exchange API credentials are not configured');
    }
    const client = this.paymentSettingsService.createClient(config);
    const result = await client.getDepositAddress(BINANCE_WALLET_COIN, network);
    if (!result.success || !result.data?.address) {
      throw new ServiceUnavailableException(
        result.error || 'Failed to fetch Binance deposit address',
      );
    }
    return {
      address: result.data.address,
      tag: result.data.tag,
    };
  }

  private async resolveBillableProduct(productId: string): Promise<{
    sku?: string;
    coins?: number;
    bonusCoins?: number;
    priceUsd?: number;
  } | null> {
    try {
      return await this.walletService.resolvePackageBySku(productId);
    } catch {
      /* fall through to store offers */
    }

    const offerItems = await this.listStoreOffers();
    const fromOffers = offerItems.find((o) => o && o.sku === productId);
    if (fromOffers) return fromOffers;
    return null;
  }

  private async listStoreOffers(): Promise<
    Array<{ sku?: string; coins?: number; bonusCoins?: number; priceUsd?: number; active?: boolean }>
  > {
    // Always bill from the safe catalog — never trust fat legacy store_offers rows.
    return STANDARD_RECHARGE_PACKAGES.map((pkg) => ({
      sku: pkg.sku,
      coins: pkg.coins,
      bonusCoins: pkg.bonusCoins,
      priceUsd: pkg.priceUsd,
      active: true,
    }));
  }
}
