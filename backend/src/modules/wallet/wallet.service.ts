import {
  Injectable,
  NotFoundException,
  BadRequestException,
  ForbiddenException,
  OnModuleInit,
  Logger,
  Optional,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { DataSource, Repository } from 'typeorm';
import { Wallet } from '../../database/entities/wallet.entity';
import {
  WalletTransaction,
  TransactionType,
  CurrencyType,
} from '../../database/entities/wallet-transaction.entity';
import {
  RechargeOrder,
  RechargeStatus,
  PaymentProvider,
} from '../../database/entities/recharge-order.entity';
import {
  WithdrawRequest,
  WithdrawStatus,
} from '../../database/entities/withdraw-request.entity';
import { AppSetting } from '../../database/entities/app-setting.entity';
import {
  RechargeAgent,
  RechargeAgentStatus,
} from '../../database/entities/recharge-agent.entity';
import { User } from '../../database/entities/user.entity';
import {
  AgencyMember,
  AgencyMemberStatus,
  AgencyRole,
} from '../../database/entities/agency-member.entity';
import { AgencyStatus } from '../../database/entities/agency.entity';
import { paginate, PaginationDto } from '../../common/dto/pagination.dto';
import {
  PRICING_VERSION,
  STANDARD_RECHARGE_PACKAGES,
  HOST_DIAMOND_TRADE,
} from '../../common/pricing-catalog';
import { bootCatalogSeedEnabled } from '../../common/db-authoritative';
import { CreateRechargeDto, ExchangeDto, WithdrawDto } from './dto/wallet.dto';
import { TasksService } from '../tasks/tasks.service';
import { NotificationsService } from '../notifications/notifications.service';
import { NotificationType } from '../../database/entities/notification.entity';
import { PromotionsService } from '../promotions/promotions.service';

const DIAMOND_TO_COIN_RATE = 0.55;
/**
 * Withdraw USD per diamond. Must stay BELOW effective coin buy price
 * (~$0.000099 at $0.99/10k) so gift→diamond→cashout cannot bankrupt the store.
 */
const DIAMOND_TO_FIAT = 0.00005;

/** Fallback identical to canonical catalog (Play productId = sku). */
const DEFAULT_PACKAGES = STANDARD_RECHARGE_PACKAGES.map((p) => ({ ...p }));

@Injectable()
export class WalletService implements OnModuleInit {
  private readonly logger = new Logger(WalletService.name);

  constructor(
    @InjectRepository(Wallet) private readonly walletsRepo: Repository<Wallet>,
    @InjectRepository(WalletTransaction)
    private readonly txRepo: Repository<WalletTransaction>,
    @InjectRepository(RechargeOrder)
    private readonly rechargeRepo: Repository<RechargeOrder>,
    @InjectRepository(WithdrawRequest)
    private readonly withdrawRepo: Repository<WithdrawRequest>,
    @InjectRepository(AppSetting)
    private readonly settingsRepo: Repository<AppSetting>,
    @InjectRepository(User)
    private readonly usersRepo: Repository<User>,
    @InjectRepository(AgencyMember)
    private readonly membersRepo: Repository<AgencyMember>,
    private readonly dataSource: DataSource,
    private readonly tasksService: TasksService,
    private readonly notifications: NotificationsService,
    @Optional() private readonly promotions?: PromotionsService,
  ) {}

  async onModuleInit() {
    try {
      await this.dataSource.query(
        `ALTER TABLE wallets ADD COLUMN IF NOT EXISTS "traderDiamonds" BIGINT NOT NULL DEFAULT 0`,
      );
    } catch (err) {
      this.logger.warn(`ensure traderDiamonds: ${(err as Error).message}`);
    }
    try {
      await this.dataSource.query(
        `ALTER TABLE wallets ADD COLUMN IF NOT EXISTS "agencyDiamonds" BIGINT NOT NULL DEFAULT 0`,
      );
    } catch (err) {
      this.logger.warn(`ensure agencyDiamonds: ${(err as Error).message}`);
    }
    try {
      // Retire silver: fold any remaining balance into gold coins and zero the column.
      const result = await this.dataSource.query(`
        UPDATE wallets
        SET coins = COALESCE(coins, 0) + COALESCE("silverCoins", 0),
            "silverCoins" = 0
        WHERE COALESCE("silverCoins", 0) <> 0
      `);
      this.logger.log(`Silver coins retired into coins (affected rows logged by driver)`);
      void result;
    } catch (err) {
      this.logger.warn(`retire silverCoins: ${(err as Error).message}`);
    }
    try {
      await this.ensureCanonicalPackages();
    } catch (err) {
      this.logger.warn(`ensureCanonicalPackages: ${(err as Error).message}`);
    }
  }

  /** DB packages win. Code defaults only for empty installs when BOOT_SEED_CATALOGS=1. */
  async ensureCanonicalPackages() {
    const pkgRow = await this.settingsRepo.findOne({
      where: { key: 'recharge_packages' },
    });
    if (pkgRow?.value) {
      // Live dashboard / production packages — never force-reset from code.
      return;
    }
    if (!bootCatalogSeedEnabled()) {
      this.logger.log('Recharge packages: DB authoritative (empty, seed disabled)');
      return;
    }

    const verKey = 'pricing.version';
    const verRow = await this.settingsRepo.findOne({ where: { key: verKey } });

    await this.savePackages([...STANDARD_RECHARGE_PACKAGES] as any);

    // Keep store_offers aligned so Play billing never falls back to fat bonuses.
    const offersRow = await this.settingsRepo.findOne({ where: { key: 'store_offers' } });
    const offersValue = JSON.stringify(
      STANDARD_RECHARGE_PACKAGES.map((pkg) => ({
        sku: pkg.sku,
        coins: pkg.coins,
        bonusCoins: pkg.bonusCoins,
        priceUsd: pkg.priceUsd,
        active: true,
      })),
    );
    if (!offersRow) {
      await this.settingsRepo.save(
        this.settingsRepo.create({
          key: 'store_offers',
          value: offersValue,
          description: 'Play/store offer fallbacks (canonical)',
        }),
      );
    } else {
      offersRow.value = offersValue;
      await this.settingsRepo.save(offersRow);
    }

    if (!verRow) {
      await this.settingsRepo.save(
        this.settingsRepo.create({
          key: verKey,
          value: PRICING_VERSION,
          description: 'Canonical pricing catalog version',
        }),
      );
    } else {
      verRow.value = PRICING_VERSION;
      await this.settingsRepo.save(verRow);
    }
    this.logger.log(`Seeded empty recharge packages to ${PRICING_VERSION}`);
  }

  async getWallet(userId: string) {
    let wallet = await this.walletsRepo.findOne({ where: { userId } });
    if (!wallet) {
      wallet = await this.walletsRepo.save(
        this.walletsRepo.create({ userId }),
      );
    }
    // Legacy: migrate leftover game points into coins (one-shot per wallet).
    const leftoverPoints = Math.max(0, Math.floor(Number(wallet.gamePoints || 0)));
    if (leftoverPoints > 0) {
      wallet.coins = Number(wallet.coins || 0) + leftoverPoints;
      wallet.gamePoints = 0;
      wallet = await this.walletsRepo.save(wallet);
    }
    const diamonds = Number(wallet.diamonds);
    const agencyDiamonds = Number((wallet as any).agencyDiamonds || 0);
    const traderDiamonds = Number(wallet.traderDiamonds || 0);
    const economy = await this.economyConfig();
    const target = economy.withdrawTargetDiamonds;
    const fiatRate = economy.diamondUsdRate;
    return {
      ...wallet,
      coins: Number(wallet.coins),
      /** Personal-room gift earnings — platform withdraw pool */
      diamonds,
      /** Agency-room host share + owner commission — agency withdraw pool */
      agencyDiamonds,
      traderDiamonds,
      personalDiamonds: diamonds,
      personalUsd: Number((diamonds * fiatRate).toFixed(4)),
      agencyUsd: Number((agencyDiamonds * fiatRate).toFixed(4)),
      silverCoins: 0,
      gamePoints: 0,
      withdrawTargetDiamonds: target,
      withdrawProgress: target > 0 ? Math.min(1, diamonds / target) : 1,
      canWithdraw: diamonds >= target,
      canWithdrawAgency: agencyDiamonds >= target,
      diamondUsdRate: fiatRate,
    };
  }

  async economyConfig() {
    const [fiatRate, coinRate, minWithdraw] =
      await Promise.all([
        this.numberSetting('economy.diamondUsdRate', DIAMOND_TO_FIAT, 0.000001, DIAMOND_TO_FIAT),
        // Hard cap 1.0 — never allow diamond→coin mint printers (was 1000).
        this.numberSetting('economy.diamondCoinRate', DIAMOND_TO_COIN_RATE, 0.01, 1),
        this.numberSetting('economy.minWithdrawDiamonds', 10000, 10000, 1_000_000_000),
      ]);
    const target = Math.floor(minWithdraw);
    return {
      diamondUsdRate: fiatRate,
      diamondCoinRate: coinRate,
      /** التارجت = الحد الأدنى للسحب بالألماس القابل للسحب */
      minWithdrawDiamonds: target,
      withdrawTargetDiamonds: target,
      currency: 'USD',
      managedByAdmin: true,
    };
  }

  /**
   * Cashout package grid (Mikoo-style): USD label + diamond cost.
   * Admin may override via setting `withdraw_packages` JSON array.
   */
  async listWithdrawPackages() {
    const economy = await this.economyConfig();
    const rate = Number(economy.diamondUsdRate) || DIAMOND_TO_FIAT;
    const min = Math.max(1, Math.floor(Number(economy.minWithdrawDiamonds) || 10000));
    const raw = await this.settingsRepo.findOne({ where: { key: 'withdraw_packages' } });
    let configured: Array<{ id?: string; diamonds?: number; usd?: number; label?: string }> = [];
    if (raw?.value) {
      try {
        const parsed = JSON.parse(raw.value);
        if (Array.isArray(parsed)) configured = parsed;
        else if (Array.isArray(parsed?.items)) configured = parsed.items;
      } catch {
        configured = [];
      }
    }
    const defaultsUsd = [0.5, 1, 2.5, 5, 10, 20, 50, 100];
    const source =
      configured.length > 0
        ? configured
        : defaultsUsd.map((usd, i) => ({
            id: String(i + 1),
            usd,
            diamonds: Math.max(min, Math.round(usd / rate)),
            label: `$${usd % 1 === 0 ? usd.toFixed(0) : usd.toFixed(2)}`,
          }));
    const items = source
      .map((row, i) => {
        const usd =
          Number(row.usd) > 0
            ? Number(row.usd)
            : Number(row.diamonds) > 0
              ? Number((Number(row.diamonds) * rate).toFixed(2))
              : 0;
        const diamonds =
          Number(row.diamonds) > 0
            ? Math.floor(Number(row.diamonds))
            : usd > 0
              ? Math.max(min, Math.round(usd / rate))
              : 0;
        if (diamonds < min || usd <= 0) return null;
        const label =
          (row.label && String(row.label).trim()) ||
          `$${usd % 1 === 0 ? usd.toFixed(0) : usd.toFixed(2)}`;
        return {
          id: String(row.id || i + 1),
          diamonds,
          usd: Number(usd.toFixed(2)),
          label,
          currency: 'USD',
        };
      })
      .filter(Boolean);
    return {
      items,
      diamondUsdRate: rate,
      minWithdrawDiamonds: min,
      currency: 'USD',
    };
  }

  /** Admin: persist withdraw package grid (USD + diamonds). */
  async saveWithdrawPackages(items: Array<Record<string, unknown>>) {
    const economy = await this.economyConfig();
    const rate = Number(economy.diamondUsdRate) || DIAMOND_TO_FIAT;
    const min = Math.max(1, Math.floor(Number(economy.minWithdrawDiamonds) || 10000));
    const cleaned = (Array.isArray(items) ? items : [])
      .map((row, i) => {
        const usd = Number(row?.usd) || 0;
        let diamonds = Math.floor(Number(row?.diamonds) || 0);
        if (usd > 0 && diamonds <= 0) {
          diamonds = Math.max(min, Math.round(usd / rate));
        }
        if (diamonds < min || usd <= 0) return null;
        const label =
          (row?.label && String(row.label).trim()) ||
          `$${usd % 1 === 0 ? usd.toFixed(0) : Number(usd).toFixed(2)}`;
        return {
          id: String(row?.id || i + 1),
          usd: Number(Number(usd).toFixed(2)),
          diamonds,
          label,
        };
      })
      .filter(Boolean);
    let row = await this.settingsRepo.findOne({ where: { key: 'withdraw_packages' } });
    if (!row) {
      row = this.settingsRepo.create({
        key: 'withdraw_packages',
        value: JSON.stringify(cleaned),
        description: 'Mikoo-style withdraw packages (USD + diamonds)',
      });
    } else {
      row.value = JSON.stringify(cleaned);
    }
    await this.settingsRepo.save(row);
    return this.listWithdrawPackages();
  }

  async transactions(userId: string, query: PaginationDto) {
    const [items, total] = await this.txRepo.findAndCount({
      where: { userId },
      skip: query.skip,
      take: query.limit || 20,
      order: { createdAt: 'DESC' },
    });
    return paginate(items, total, query.page || 1, query.limit || 20);
  }

  async resolvePackageBySku(sku: string) {
    if (!sku || !String(sku).trim()) {
      throw new BadRequestException('sku required');
    }
    const { items } = await this.listPackages();
    const pkg = (items || []).find(
      (p: { sku?: string }) => p && String(p.sku) === String(sku),
    ) as
      | {
          sku: string;
          coins: number;
          bonusCoins?: number;
          priceUsd: number;
          label?: string;
        }
      | undefined;
    if (!pkg || !Number(pkg.coins) || !Number.isFinite(Number(pkg.priceUsd))) {
      throw new BadRequestException(`Unknown package SKU: ${sku}`);
    }
    return {
      sku: String(pkg.sku),
      coins: Math.max(1, Math.floor(Number(pkg.coins))),
      bonusCoins: Math.max(0, Math.floor(Number(pkg.bonusCoins) || 0)),
      priceUsd: Number(pkg.priceUsd),
      label: pkg.label ? String(pkg.label) : String(pkg.coins),
    };
  }

  async createRecharge(userId: string, dto: CreateRechargeDto) {
    const provider = dto.provider as PaymentProvider;
    if (!Object.values(PaymentProvider).includes(provider)) {
      throw new BadRequestException('Invalid payment provider');
    }
    const pkg = await this.resolvePackageBySku(dto.sku);
    const order = await this.rechargeRepo.save(
      this.rechargeRepo.create({
        userId,
        sku: pkg.sku,
        coins: pkg.coins,
        bonusCoins: pkg.bonusCoins,
        amountFiat: pkg.priceUsd,
        currency: dto.currency || 'USD',
        provider,
        status: RechargeStatus.PENDING,
      }),
    );
    return order;
  }

  async getOrderForUser(orderId: string, userId: string) {
    const order = await this.rechargeRepo.findOne({ where: { id: orderId } });
    if (!order || order.userId !== userId) {
      throw new NotFoundException('Order not found');
    }
    return order;
  }

  async completeRecharge(orderId: string, providerPaymentId?: string) {
    return this.dataSource.transaction(async (manager) => {
      const order = await manager.findOne(RechargeOrder, {
        where: { id: orderId },
        lock: { mode: 'pessimistic_write' },
      });
      if (!order) throw new NotFoundException('Order not found');
      if (order.status === RechargeStatus.COMPLETED) return order;
      if (order.status !== RechargeStatus.PENDING) {
        throw new BadRequestException('Order not payable');
      }

      const wallet = await manager.findOne(Wallet, {
        where: { userId: order.userId },
        lock: { mode: 'pessimistic_write' },
      });
      if (!wallet) throw new NotFoundException('Wallet not found');

      const creditCoins =
        Number(order.coins) + Math.max(0, Number(order.bonusCoins) || 0);
      if (creditCoins > 0) {
        wallet.coins = Number(wallet.coins) + creditCoins;
        wallet.totalRecharged = Number(wallet.totalRecharged) + creditCoins;
        await manager.save(wallet);
      }

      order.status = RechargeStatus.COMPLETED;
      order.completedAt = new Date();
      order.providerPaymentId = providerPaymentId || order.providerPaymentId;
      await manager.save(order);

      if (creditCoins > 0) {
        await manager.save(
          manager.create(WalletTransaction, {
            userId: order.userId,
            type: TransactionType.RECHARGE,
            currency: CurrencyType.COINS,
            amount: creditCoins,
            balanceAfter: Number(wallet.coins),
            referenceType: 'recharge_order',
            referenceId: order.id,
            description: `Recharged ${creditCoins} coins`,
          }),
        );
      }
      return order;
    }).then(async (order) => {
      void this.tasksService.recordProgress(order.userId, 'recharge', 1).catch(() => undefined);
      const usd = Number(order.amountFiat) || 0;
      if (usd > 0 && this.promotions) {
        void this.promotions
          .onUserRechargeCompleted(order.userId, usd)
          .catch((err) =>
            this.logger.warn(`promo after recharge: ${(err as Error).message}`),
          );
      }
      return order;
    });
  }

  async exchange(userId: string, dto: ExchangeDto) {
    const rate = await this.numberSetting(
      'economy.diamondCoinRate',
      DIAMOND_TO_COIN_RATE,
      0.01,
      1,
    );
    return this.dataSource.transaction(async (manager) => {
      const wallet = await manager.findOne(Wallet, {
        where: { userId },
        lock: { mode: 'pessimistic_write' },
      });
      if (!wallet || Number(wallet.diamonds) < dto.diamonds) {
        throw new BadRequestException('Insufficient diamonds');
      }
      const coins = Math.floor(dto.diamonds * rate);
      wallet.diamonds = Number(wallet.diamonds) - dto.diamonds;
      wallet.coins = Number(wallet.coins) + coins;
      await manager.save(wallet);

      await manager.save(
        manager.create(WalletTransaction, {
          userId,
          type: TransactionType.EXCHANGE,
          currency: CurrencyType.DIAMONDS,
          amount: -dto.diamonds,
          balanceAfter: Number(wallet.diamonds),
          description: `Exchanged ${dto.diamonds} diamonds for ${coins} coins`,
        }),
      );
      await manager.save(
        manager.create(WalletTransaction, {
          userId,
          type: TransactionType.EXCHANGE,
          currency: CurrencyType.COINS,
          amount: coins,
          balanceAfter: Number(wallet.coins),
          description: `Received ${coins} coins from diamond exchange`,
        }),
      );
      return this.getWallet(userId);
    }).then(async (wallet) => {
      void this.tasksService.recordProgress(userId, 'recharge', 1).catch(() => undefined);
      return wallet;
    });
  }

  private async assertActiveAgencyHost(userId: string) {
    const member = await this.membersRepo.findOne({
      where: {
        userId,
        isActive: true,
        status: AgencyMemberStatus.ACTIVE,
      },
      relations: ['agency'],
    });
    if (
      !member ||
      ![AgencyRole.OWNER, AgencyRole.MANAGER, AgencyRole.HOST].includes(member.role)
    ) {
      throw new ForbiddenException('التبادل متاح للمضيفات المسجّلات بوكالة فقط');
    }
    if (member.agency?.status !== AgencyStatus.ACTIVE) {
      throw new ForbiddenException('الوكالة غير نشطة — لا يمكن التبادل حالياً');
    }
    const user = await this.usersRepo.findOne({ where: { id: userId } });
    if (String(user?.gender || '').toLowerCase() !== 'female') {
      throw new ForbiddenException('التبادل بين المضيفات فقط');
    }
    return member;
  }

  /**
   * Hostess↔hostess diamond swap: deducted from sender withdrawable diamonds,
   * credited to receiver traderDiamonds (تاجر collection).
   */
  async tradeDiamondsWithHost(
    fromUserId: string,
    toUserId: string,
    amount: number,
  ) {
    const diamonds = Math.floor(Number(amount) || 0);
    if (diamonds < HOST_DIAMOND_TRADE.minAmount) {
      throw new BadRequestException(
        `الحد الأدنى للتبادل ${HOST_DIAMOND_TRADE.minAmount} ماسة`,
      );
    }
    if (diamonds > HOST_DIAMOND_TRADE.maxAmount) {
      throw new BadRequestException(
        `الحد الأقصى للتبادل ${HOST_DIAMOND_TRADE.maxAmount} ماسة`,
      );
    }
    if (!toUserId || toUserId === fromUserId) {
      throw new BadRequestException('اختر مضيفة أخرى للتبادل');
    }
    await this.assertActiveAgencyHost(fromUserId);
    await this.assertActiveAgencyHost(toUserId);

    const refId = `host_trade:${fromUserId}:${toUserId}:${Date.now()}`;
    return this.dataSource.transaction(async (manager) => {
      await manager.query('SELECT pg_advisory_xact_lock(hashtext($1))', [
        `host-trade:${fromUserId}`,
      ]);
      const sender = await manager.findOne(Wallet, {
        where: { userId: fromUserId },
        lock: { mode: 'pessimistic_write' },
      });
      if (!sender || Number(sender.diamonds) < diamonds) {
        throw new BadRequestException('رصيد الماس غير كافٍ');
      }
      let receiver = await manager.findOne(Wallet, {
        where: { userId: toUserId },
        lock: { mode: 'pessimistic_write' },
      });
      if (!receiver) {
        receiver = await manager.save(
          manager.create(Wallet, {
            userId: toUserId,
            coins: 0,
            diamonds: 0,
            traderDiamonds: 0,
            gamePoints: 0,
          }),
        );
        receiver = await manager.findOne(Wallet, {
          where: { userId: toUserId },
          lock: { mode: 'pessimistic_write' },
        });
      }
      if (!receiver) throw new NotFoundException('محفظة المستلمة غير موجودة');

      sender.diamonds = Number(sender.diamonds) - diamonds;
      receiver.traderDiamonds = Number(receiver.traderDiamonds || 0) + diamonds;
      await manager.save(sender);
      await manager.save(receiver);

      await manager.save(
        manager.create(WalletTransaction, {
          userId: fromUserId,
          type: TransactionType.EXCHANGE,
          currency: CurrencyType.DIAMONDS,
          amount: -diamonds,
          balanceAfter: Number(sender.diamonds),
          referenceType: 'host_trade_send',
          referenceId: refId,
          description: `تبديل ماس مع مضيفة`,
          metadata: { toUserId, diamonds },
        }),
      );
      await manager.save(
        manager.create(WalletTransaction, {
          userId: toUserId,
          type: TransactionType.EXCHANGE,
          currency: CurrencyType.DIAMONDS,
          amount: diamonds,
          balanceAfter: Number(receiver.traderDiamonds || 0),
          referenceType: 'host_trade_receive',
          referenceId: `${refId}:recv`,
          description: `استلام ماس تاجر من مضيفة`,
          metadata: { fromUserId, diamonds, trader: true },
        }),
      );

      return {
        ok: true,
        sent: diamonds,
        toUserId,
        sender: {
          diamonds: Number(sender.diamonds),
          traderDiamonds: Number(sender.traderDiamonds || 0),
        },
        receiver: {
          diamonds: Number(receiver.diamonds),
          traderDiamonds: Number(receiver.traderDiamonds || 0),
        },
      };
    }).then(async (result) => {
      const senderUser = await this.usersRepo.findOne({ where: { id: fromUserId } });
      const senderName =
        senderUser?.displayName || senderUser?.username || 'مضيفة';
      void this.notifications.notifyUser(
        toUserId,
        NotificationType.WALLET,
        'استلام ماس تاجر',
        `استلمت ${diamonds} ماسة تاجر من ${senderName}.`,
        {
          fromUserId,
          diamonds,
          officialNews: true,
          action: 'host_trade_receive',
        },
      );
      void this.notifications.notifyUser(
        fromUserId,
        NotificationType.WALLET,
        'تم تحويل الماس',
        `أرسلت ${diamonds} ماسة بنجاح.`,
        {
          toUserId,
          diamonds,
          officialNews: true,
          action: 'host_trade_send',
        },
      );
      return result;
    });
  }

  async listPackages() {
    const row = await this.settingsRepo.findOne({ where: { key: 'recharge_packages' } });
    let items = DEFAULT_PACKAGES;
    if (row?.value) {
      try {
        const parsed = JSON.parse(row.value);
        if (Array.isArray(parsed) && parsed.length) items = parsed;
      } catch {
        /* keep defaults */
      }
    }
    return { items };
  }

  async savePackages(items: Array<Record<string, unknown>>) {
    if (!Array.isArray(items) || !items.length) {
      throw new BadRequestException('packages required');
    }
    const normalized = items.map((p, i) => {
      const imageUrl = String(p.imageUrl || p.iconUrl || '').trim();
      const row: Record<string, unknown> = {
        id: String(p.id || i + 1),
        sku: String(p.sku || `coins_${p.coins || i}`),
        coins: Number(p.coins) || 0,
        bonusCoins: Number(p.bonusCoins) || 0,
        priceUsd: Number(p.priceUsd) || 0,
        label: String(p.label || p.coins || ''),
        popular: !!p.popular,
      };
      if (imageUrl) {
        row.imageUrl = imageUrl;
        row.iconUrl = imageUrl;
      }
      return row;
    });
    let row = await this.settingsRepo.findOne({ where: { key: 'recharge_packages' } });
    if (!row) {
      row = this.settingsRepo.create({
        key: 'recharge_packages',
        value: JSON.stringify(normalized),
        description: 'In-app coin recharge packages',
      });
    } else {
      row.value = JSON.stringify(normalized);
    }
    await this.settingsRepo.save(row);
    return { items: normalized };
  }

  async requestWithdraw(userId: string, dto: WithdrawDto) {
    const [fiatRate, minWithdraw] = await Promise.all([
      this.numberSetting('economy.diamondUsdRate', DIAMOND_TO_FIAT, 0.000001, DIAMOND_TO_FIAT),
      this.numberSetting('economy.minWithdrawDiamonds', 10000, 10000, 1_000_000_000),
    ]);
    if (dto.diamonds < minWithdraw) {
      throw new BadRequestException(
        `Minimum withdrawal is ${Math.floor(minWithdraw)} diamonds`,
      );
    }

    const viaAgent = dto.method === 'agent';
    const sourceRaw = String((dto.payoutDetails as any)?.source || '')
      .trim()
      .toLowerCase();
    const isAgencySource =
      sourceRaw === 'agency_commission' ||
      sourceRaw === 'agency_host' ||
      sourceRaw === 'agency' ||
      sourceRaw === 'agency_room' ||
      sourceRaw === 'agency_earnings';
    // Personal diamond withdraw vs agency-room pool (host share / owner commission) — never mixed.
    if (viaAgent && isAgencySource) {
      throw new BadRequestException(
        'سحب أرباح روم الوكالة يتم عبر إدارة المنصة مباشرة — وليس عبر وكيل الشحن',
      );
    }
    let agentId: string | null = null;
    if (viaAgent) {
      const rawAgentId = (dto.agentId || '').trim();
      if (!rawAgentId) {
        throw new BadRequestException('اختر وكيلاً للسحب عبر الوكيل');
      }
      const agent = await this.dataSource.getRepository(RechargeAgent).findOne({
        where: {
          id: rawAgentId,
          status: RechargeAgentStatus.ACTIVE,
          listedInDirectory: true,
        },
      });
      if (!agent) {
        throw new BadRequestException('وكيل الشحن غير متاح — اختر وكيل شحن معتمد');
      }
      if (agent.userId === userId) {
        throw new BadRequestException('لا يمكنك السحب عبر حساب الوكيل الخاص بك');
      }
      agentId = agent.id;
    }

    return this.dataSource.transaction(async (manager) => {
      const wallet = await manager.findOne(Wallet, {
        where: { userId },
        lock: { mode: 'pessimistic_write' },
      });
      if (!wallet) {
        throw new BadRequestException('Insufficient diamonds');
      }
      const personalBal = Number(wallet.diamonds || 0);
      const agencyBal = Number((wallet as any).agencyDiamonds || 0);
      if (isAgencySource) {
        if (agencyBal < dto.diamonds) {
          throw new BadRequestException(
            'رصيد أرباح الوكالة غير كافٍ (منفصل عن أرباح الروم الشخصي)',
          );
        }
        (wallet as any).agencyDiamonds = agencyBal - dto.diamonds;
      } else {
        if (personalBal < dto.diamonds) {
          throw new BadRequestException(
            'رصيد أرباح الروم الشخصي غير كافٍ (منفصل عن عمولة الوكالة)',
          );
        }
        wallet.diamonds = personalBal - dto.diamonds;
      }
      await manager.save(wallet);

      const amountFiat = Number((dto.diamonds * fiatRate).toFixed(2));
      const stream = isAgencySource ? 'agency' : 'personal';
      const agencyKind =
        sourceRaw === 'agency_host' || sourceRaw === 'host'
          ? 'agency_host'
          : 'agency_commission';
      const sourceTag = isAgencySource
        ? agencyKind
        : 'personal_room';
      const request = await manager.save(
        manager.create(WithdrawRequest, {
          userId,
          diamonds: dto.diamonds,
          amountFiat,
          method: dto.method,
          agentId,
          payoutDetails: {
            ...(dto.payoutDetails || {}),
            channel: viaAgent
              ? 'agent'
              : isAgencySource
                ? 'agency_pool'
                : 'personal_room',
            source: sourceTag,
            stream,
          },
          status: WithdrawStatus.PENDING,
        }),
      );

      await manager.save(
        manager.create(WalletTransaction, {
          userId,
          type: TransactionType.WITHDRAW,
          currency: CurrencyType.DIAMONDS,
          amount: -dto.diamonds,
          balanceAfter: isAgencySource
            ? Number((wallet as any).agencyDiamonds || 0)
            : Number(wallet.diamonds),
          referenceType: isAgencySource
            ? 'withdraw_request_agency'
            : 'withdraw_request_personal',
          referenceId: request.id,
          description: isAgencySource
            ? agencyKind === 'agency_host'
              ? `سحب أرباح مضيفة (روم وكالة) ${dto.diamonds} ماسة`
              : `سحب عمولة وكالة ${dto.diamonds} ماسة`
            : `سحب أرباح روم شخصي ${dto.diamonds} ماسة`,
          metadata: { stream, source: sourceTag, method: dto.method },
        }),
      );
      return request;
    });
  }

  async listWithdraws(userId: string, query: PaginationDto) {
    const [items, total] = await this.withdrawRepo.findAndCount({
      where: { userId },
      order: { createdAt: 'DESC' },
      skip: query.skip,
      take: query.limit || 20,
    });
    return paginate(
      items.map((w) => ({
        id: w.id,
        diamonds: Number(w.diamonds),
        amountFiat: Number(w.amountFiat),
        method: w.method,
        agentId: w.agentId || null,
        channel: w.method === 'agent' ? 'agent' : 'self',
        payoutDetails: w.payoutDetails,
        status: w.status,
        adminNote: w.adminNote || null,
        createdAt: w.createdAt,
        updatedAt: w.updatedAt,
      })),
      total,
      query.page || 1,
      query.limit || 20,
    );
  }

  async adminAdjust(
    userId: string,
    coinsDelta: number,
    diamondsDelta: number,
    note: string,
    _silverCoinsDelta = 0,
    gamePointsDelta = 0,
  ) {
    // silverCoinsDelta + gamePointsDelta folded into coins (game points currency retired).
    const coins =
      Number(coinsDelta || 0) +
      Number(_silverCoinsDelta || 0) +
      Number(gamePointsDelta || 0);
    return this.dataSource.transaction(async (manager) => {
      let wallet = await manager.findOne(Wallet, {
        where: { userId },
        lock: { mode: 'pessimistic_write' },
      });
      if (!wallet) {
        wallet = await manager.save(manager.create(Wallet, { userId }));
      }
      // Absorb any leftover points into coins once.
      const leftover = Math.max(0, Math.floor(Number(wallet.gamePoints || 0)));
      wallet.coins = Number(wallet.coins) + coins + leftover;
      wallet.diamonds = Number(wallet.diamonds) + diamondsDelta;
      wallet.gamePoints = 0;
      if (wallet.coins < 0 || wallet.diamonds < 0) {
        throw new BadRequestException('Balance cannot go negative');
      }
      await manager.save(wallet);
      if (coins !== 0 || leftover > 0) {
        await manager.save(
          manager.create(WalletTransaction, {
            userId,
            type: TransactionType.ADMIN_ADJUST,
            currency: CurrencyType.COINS,
            amount: coins + leftover,
            balanceAfter: Number(wallet.coins),
            description: note,
          }),
        );
      }
      if (diamondsDelta !== 0) {
        await manager.save(
          manager.create(WalletTransaction, {
            userId,
            type: TransactionType.ADMIN_ADJUST,
            currency: CurrencyType.DIAMONDS,
            amount: diamondsDelta,
            balanceAfter: Number(wallet.diamonds),
            description: note,
          }),
        );
      }
      return wallet;
    }).then(async (wallet) => {
      const parts: string[] = [];
      if (coins !== 0) {
        parts.push(
          coins > 0
            ? `تم إضافة ${coins} عملة إلى محفظتك`
            : `تم خصم ${Math.abs(coins)} عملة من محفظتك`,
        );
      }
      if (diamondsDelta !== 0) {
        parts.push(
          diamondsDelta > 0
            ? `تم إضافة ${diamondsDelta} ماسة`
            : `تم خصم ${Math.abs(diamondsDelta)} ماسة`,
        );
      }
      if (gamePointsDelta !== 0) {
        // Folded into coins above — no separate game-points notification.
      }
      if (parts.length) {
        const reason = (note || '').trim();
        void this.notifications.notifyUser(
          userId,
          NotificationType.WALLET,
          'تحديث المحفظة',
          parts.join(' · ') + (reason ? `\n${reason}` : ''),
          {
            officialNews: true,
            action: 'admin_adjust',
            coinsDelta: coins,
            diamondsDelta,
            targetUserId: userId,
          },
        );
      }
      return wallet;
    });
  }

  /** Credit coins after a game rewarded ad (capped per UTC day). */
  async creditGameAdReward(
    userId: string,
    coins: number,
    dailyCap: number,
  ) {
    const amount = Math.max(0, Math.floor(Number(coins) || 0));
    const cap = Math.max(0, Math.floor(Number(dailyCap) || 0));
    if (amount <= 0) {
      return { credited: false, coins: 0, remainingToday: 0, balance: 0 };
    }
    return this.dataSource.transaction(async (manager) => {
      const day = new Date().toISOString().slice(0, 10);
      const dayPrefix = `game_ad:${day}:`;
      const claimedToday = await manager
        .createQueryBuilder(WalletTransaction, 't')
        .where('t.userId = :userId', { userId })
        .andWhere('t.referenceType = :rt', { rt: 'game_ad_reward' })
        .andWhere('t.referenceId LIKE :prefix', { prefix: `${dayPrefix}%` })
        .getCount();

      if (cap > 0 && claimedToday >= cap) {
        const wallet = await manager.findOne(Wallet, { where: { userId } });
        return {
          credited: false,
          coins: 0,
          remainingToday: 0,
          balance: Number(wallet?.coins || 0),
          dailyCapReached: true,
        };
      }

      let wallet = await manager.findOne(Wallet, {
        where: { userId },
        lock: { mode: 'pessimistic_write' },
      });
      if (!wallet) {
        wallet = await manager.save(manager.create(Wallet, { userId }));
      }
      wallet.coins = Number(wallet.coins) + amount;
      await manager.save(wallet);
      const claimIndex = claimedToday + 1;
      await manager.save(
        manager.create(WalletTransaction, {
          userId,
          type: TransactionType.GAME_AD_REWARD,
          currency: CurrencyType.COINS,
          amount,
          balanceAfter: Number(wallet.coins),
          description: `مكافأة إعلان لعبة`,
          referenceType: 'game_ad_reward',
          referenceId: `${dayPrefix}${claimIndex}`,
        }),
      );
      const remaining =
        cap > 0 ? Math.max(0, cap - claimIndex) : Number.MAX_SAFE_INTEGER;
      return {
        credited: true,
        coins: amount,
        remainingToday: remaining === Number.MAX_SAFE_INTEGER ? -1 : remaining,
        balance: Number(wallet.coins),
        dailyCapReached: false,
      };
    });
  }

  /** Credit coins for watching a drama episode (once per user+episode). */
  async creditDramaWatchReward(
    userId: string,
    episodeId: string,
    coins: number,
  ) {
    const amount = Math.max(0, Math.floor(Number(coins) || 0));
    if (amount <= 0) {
      return { credited: false, coins: 0, alreadyClaimed: false, balance: 0 };
    }
    return this.dataSource.transaction(async (manager) => {
      const existing = await manager.findOne(WalletTransaction, {
        where: {
          userId,
          referenceType: 'drama_episode',
          referenceId: String(episodeId),
        },
      });
      if (existing) {
        const wallet = await manager.findOne(Wallet, { where: { userId } });
        return {
          credited: false,
          coins: 0,
          alreadyClaimed: true,
          balance: Number(wallet?.coins || 0),
        };
      }

      let wallet = await manager.findOne(Wallet, {
        where: { userId },
        lock: { mode: 'pessimistic_write' },
      });
      if (!wallet) {
        wallet = await manager.save(manager.create(Wallet, { userId }));
      }
      wallet.coins = Number(wallet.coins) + amount;
      await manager.save(wallet);
      await manager.save(
        manager.create(WalletTransaction, {
          userId,
          type: TransactionType.DRAMA_WATCH,
          currency: CurrencyType.COINS,
          amount,
          balanceAfter: Number(wallet.coins),
          description: `مكافأة مشاهدة حلقة دراما`,
          referenceType: 'drama_episode',
          referenceId: String(episodeId),
        }),
      );
      return {
        credited: true,
        coins: amount,
        alreadyClaimed: false,
        balance: Number(wallet.coins),
      };
    });
  }

  private async numberSetting(
    key: string,
    fallback: number,
    min: number,
    max: number,
  ): Promise<number> {
    const row = await this.settingsRepo.findOne({ where: { key } });
    const parsed = Number(row?.value);
    if (!Number.isFinite(parsed)) return fallback;
    return Math.min(max, Math.max(min, parsed));
  }
}
