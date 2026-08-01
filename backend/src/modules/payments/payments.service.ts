import {
  Injectable,
  Logger,
  BadRequestException,
  NotFoundException,
  ServiceUnavailableException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import Stripe from 'stripe';
import { GoogleAuth } from 'google-auth-library';
import {
  RechargeOrder,
  RechargeStatus,
  PaymentProvider,
} from '../../database/entities/recharge-order.entity';
import { PaymentWebhookEvent } from '../../database/entities/payment-webhook-event.entity';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { WalletService } from '../wallet/wallet.service';
import { PaymentSettingsService } from './payment-settings.service';
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
    @InjectRepository(RechargeOrder)
    private readonly ordersRepo: Repository<RechargeOrder>,
    @InjectRepository(PaymentWebhookEvent)
    private readonly webhookEventsRepo: Repository<PaymentWebhookEvent>,
    @InjectRepository(AppSetting)
    private readonly settingsRepo: Repository<AppSetting>,
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
