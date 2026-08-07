import {
  BadRequestException,
  ConflictException,
  GoneException,
  Injectable,
  NotFoundException,
  OnModuleInit,
  Optional,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { DataSource, Repository } from 'typeorm';
import {
  AgentLedgerType,
  AgentRecharge,
  RechargeAgent,
  RechargeAgentApplication,
  RechargeAgentContact,
  RechargeAgentLedger,
  RechargeAgentSource,
  RechargeAgentStatus,
} from '../../database/entities/recharge-agent.entity';
import { User, UserStatus } from '../../database/entities/user.entity';
import { Wallet } from '../../database/entities/wallet.entity';
import {
  CurrencyType,
  TransactionType,
  WalletTransaction,
} from '../../database/entities/wallet-transaction.entity';
import {
  PaymentProvider,
  RechargeOrder,
  RechargeStatus,
} from '../../database/entities/recharge-order.entity';
import { AppSetting } from '../../database/entities/app-setting.entity';
import {
  WithdrawRequest,
  WithdrawStatus,
} from '../../database/entities/withdraw-request.entity';
import { GiftSend } from '../../database/entities/gift-send.entity';
import { WalletService } from '../wallet/wallet.service';
import { PaymentsService } from '../payments/payments.service';
import { NotificationsService } from '../notifications/notifications.service';
import { NotificationType } from '../../database/entities/notification.entity';
import { PromotionsService } from '../promotions/promotions.service';

export interface ApplyRechargeAgentDto {
  contact?: string;
  region?: string;
  reason?: string;
  requestedCoins?: number;
  paymentNetwork?: string;
  paymentReference?: string;
  /** Completed Binance USDT deposit order id — auto-fills payment reference from tx. */
  depositOrderId?: string;
  /** Completed Fourthwall / card membership order id. */
  cardOrderId?: string;
}

export interface SellRechargeDto {
  recipientUsernameOrId: string;
  sku?: string;
  coins?: number;
  idempotencyKey: string;
}

export interface AdminAssignRechargeAgentDto {
  userId: string;
  floatCoins?: number;
  commissionBps?: number;
  dailyLimitCoins?: number;
  notes?: string;
}

export interface AdminPatchRechargeAgentDto {
  status?: RechargeAgentStatus;
  commissionBps?: number;
  dailyLimitCoins?: number;
  notes?: string;
  country?: string | null;
  whatsapp?: string | null;
  telegram?: string | null;
  listedInDirectory?: boolean;
}

export interface AdminUpsertAgentContactDto {
  id?: string;
  displayName: string;
  country: string;
  whatsapp?: string | null;
  telegram?: string | null;
  notes?: string | null;
  isActive?: boolean;
  sortOrder?: number;
}

export interface RechargeAgentPricingConfig {
  membershipFeeUsdt: number;
  wholesalePer100CoinsUsdt: number;
  suggestedRetailPer100CoinsUsdt: number;
  minInitialCoins: number;
  maxInitialCoins: number;
}

export const RECHARGE_AGENT_CONFIG_KEY = 'recharge_agent_config';
export const DEFAULT_RECHARGE_AGENT_CONFIG: RechargeAgentPricingConfig = {
  membershipFeeUsdt: 25,
  /**
   * Wholesale: $1.20 / 10k coins — still better than retail ~$5/50k
   * but not free leverage for users to farm gifts.
   */
  wholesalePer100CoinsUsdt: 0.012,
  suggestedRetailPer100CoinsUsdt: 0.016,
  minInitialCoins: 10_000,
  maxInitialCoins: 20_000_000,
};

@Injectable()
export class RechargeAgentsService implements OnModuleInit {
  constructor(
    @InjectRepository(RechargeAgent)
    private readonly agentsRepo: Repository<RechargeAgent>,
    @InjectRepository(RechargeAgentApplication)
    private readonly applicationsRepo: Repository<RechargeAgentApplication>,
    @InjectRepository(RechargeAgentContact)
    private readonly contactsRepo: Repository<RechargeAgentContact>,
    @InjectRepository(AgentRecharge)
    private readonly rechargesRepo: Repository<AgentRecharge>,
    @InjectRepository(AppSetting)
    private readonly settingsRepo: Repository<AppSetting>,
    @InjectRepository(WithdrawRequest)
    private readonly withdrawRepo: Repository<WithdrawRequest>,
    @InjectRepository(GiftSend)
    private readonly giftSendsRepo: Repository<GiftSend>,
    private readonly dataSource: DataSource,
    private readonly walletService: WalletService,
    private readonly paymentsService: PaymentsService,
    private readonly notifications: NotificationsService,
    @Optional() private readonly promotions?: PromotionsService,
  ) {}

  async onModuleInit() {
    try {
      await this.dataSource.query(
        `ALTER TABLE withdraw_requests ADD COLUMN IF NOT EXISTS "agentId" UUID`,
      );
      await this.dataSource.query(
        `ALTER TABLE agency_members ADD COLUMN IF NOT EXISTS status VARCHAR(16) NOT NULL DEFAULT 'active'`,
      );
      await this.dataSource.query(
        `CREATE INDEX IF NOT EXISTS "idx_withdraw_requests_agent" ON withdraw_requests("agentId")`,
      );
    } catch {
      /* column may already exist */
    }
    try {
      // Soft-upgrade legacy default agent wholesale ($0.009 / fee $20) → v3 defaults.
      const row = await this.settingsRepo.findOne({
        where: { key: RECHARGE_AGENT_CONFIG_KEY },
      });
      if (row?.value) {
        let parsed: Partial<RechargeAgentPricingConfig> = {};
        try {
          parsed = JSON.parse(row.value);
        } catch {
          parsed = {};
        }
        const whole = Number(parsed.wholesalePer100CoinsUsdt);
        const fee = Number(parsed.membershipFeeUsdt);
        const isLegacy =
          (whole === 0.009 || whole === 0.0090 || whole === 0.01 || whole === 0.010) &&
          (fee === 20 || fee === 25 || !Number.isFinite(fee));
        if (isLegacy) {
          row.value = JSON.stringify(DEFAULT_RECHARGE_AGENT_CONFIG);
          row.description = 'Recharge agent membership and USDT pricing (economy-v5)';
          await this.settingsRepo.save(row);
        }
      }
    } catch {
      /* ignore seed race */
    }
  }

  async apply(_userId: string, _dto: ApplyRechargeAgentDto) {
    throw new GoneException(
      'طلب الانضمام المدفوع لوكلاء الشحن أُوقف — الوكلاء بيع داخلي يُعيَّنون من الإدارة فقط',
    );
  }

  async publicConfig() {
    return this.getPricingConfig();
  }

  async quoteForCoins(coins: number) {
    const config = await this.getPricingConfig();
    if (!Number.isSafeInteger(coins) || coins <= 0) {
      throw new BadRequestException('coins must be a positive integer');
    }
    return this.quote(config, coins);
  }

  async paymentDetails(userId: string, network?: string) {
    const normalized = this.normalizeNetwork(network);
    const [pricing, deposit] = await Promise.all([
      this.getPricingConfig(),
      this.paymentsService.getBinanceWalletDepositAddress(userId, {
        network: normalized,
      }),
    ]);
    return { ...pricing, ...deposit };
  }

  private async assertCanApplyAsAgent(userId: string) {
    const existingAgent = await this.agentsRepo.findOne({ where: { userId } });
    if (existingAgent?.status === RechargeAgentStatus.ACTIVE) {
      throw new ConflictException('User is already an active recharge agent');
    }
    const pending = await this.applicationsRepo.findOne({
      where: { userId, status: RechargeAgentStatus.PENDING },
      order: { createdAt: 'DESC' },
    });
    if (pending) throw new ConflictException('An application is already pending');
  }

  /** Create a trackable USDT deposit order for agent membership (auto-detect via reconcile). */
  async createDepositOrder(_userId: string, _network: string, _requestedCoins: number) {
    throw new GoneException(
      'طلب الانضمام المدفوع لوكلاء الشحن أُوقف — البيع داخلي فقط',
    );
  }

  /**
   * Card (Fourthwall) checkout for agent membership fee + opening stock.
   * Wallet coins are NOT credited — float is granted only after admin approval.
   */
  async createCardCheckout(_userId: string, _requestedCoins: number) {
    throw new GoneException(
      'طلب الانضمام المدفوع لوكلاء الشحن أُوقف — البيع داخلي فقط',
    );
  }

  async getDepositOrderStatus(userId: string, orderId: string) {
    const status = await this.paymentsService.getBinanceWalletOrderStatus(userId, orderId);
    const order = status.order as {
      status?: string;
      providerPaymentId?: string | null;
    };
    const raw = String(order?.status || '').toLowerCase();
    const paid = raw === 'completed' || raw === 'paid' || raw === 'success';
    return {
      ...status,
      paid,
      txId: order?.providerPaymentId || null,
    };
  }

  async resolveRecipient(tokenRaw: string) {
    const user = await this.findUserByToken(tokenRaw);
    if (!user) throw new NotFoundException('Recipient not found');
    return {
      id: user.id,
      username: user.username,
      publicId: user.publicId,
      displayName: user.displayName,
      avatarUrl: user.avatarUrl,
      status: user.status,
    };
  }

  /** Active recharge agents available for diamond-to-cash via agent. */
  async listAgentsForWithdraw() {
    const agents = await this.agentsRepo.find({
      where: {
        status: RechargeAgentStatus.ACTIVE,
      },
      relations: { user: true },
      order: { createdAt: 'DESC' },
      take: 200,
    });
    return {
      items: agents.map((a) => ({
        id: a.id,
        source: 'recharge_agent' as const,
        displayName: a.user?.displayName || a.user?.username || 'وكيل شحن',
        publicId: a.user?.publicId || null,
        country: a.country || '',
        whatsapp: a.whatsapp,
        telegram: a.telegram,
      })),
      total: agents.length,
    };
  }

  /**
   * Agent looks up a user by public ID / username / UUID:
   * wallet balances + received gifts (so agent can credit their account).
   */
  async userOverviewForAgent(agentUserId: string, queryRaw: string) {
    await this.requireActiveAgent(agentUserId);
    const user = await this.findUserByToken(queryRaw);
    if (!user) throw new NotFoundException('المستخدم غير موجود');

    const wallet = await this.dataSource.getRepository(Wallet).findOne({
      where: { userId: user.id },
    });
    const received = await this.giftSendsRepo.find({
      where: { receiverId: user.id },
      relations: ['gift', 'sender'],
      order: { createdAt: 'DESC' },
      take: 50,
    });
    let receivedDiamonds = 0;
    let receivedCoins = 0;
    for (const g of received) {
      receivedDiamonds += Number(g.diamondsAwarded || 0);
      receivedCoins += Number(g.totalCoins || 0);
    }
    // Full totals (not just last 50)
    const sumRow = await this.giftSendsRepo
      .createQueryBuilder('g')
      .select('COALESCE(SUM(g.diamondsAwarded),0)', 'diamonds')
      .addSelect('COALESCE(SUM(g.totalCoins),0)', 'coins')
      .addSelect('COUNT(*)', 'cnt')
      .where('g.receiverId = :uid', { uid: user.id })
      .getRawOne<{ diamonds: string; coins: string; cnt: string }>();

    return {
      user: {
        id: user.id,
        publicId: user.publicId,
        username: user.username,
        displayName: user.displayName,
        avatarUrl: user.avatarUrl,
      },
      wallet: {
        coins: Number(wallet?.coins || 0),
        diamonds: Number(wallet?.diamonds || 0),
      },
      gifts: {
        receivedCount: Number(sumRow?.cnt || 0),
        receivedDiamondsTotal: Number(sumRow?.diamonds || 0),
        receivedCoinsTotal: Number(sumRow?.coins || receivedCoins),
        recent: received.map((g) => ({
          id: g.id,
          giftName: g.gift?.name || 'هدية',
          quantity: g.quantity,
          diamondsAwarded: Number(g.diamondsAwarded || 0),
          totalCoins: Number(g.totalCoins || 0),
          senderName: g.sender?.displayName || g.sender?.username || '—',
          senderPublicId: g.sender?.publicId || null,
          createdAt: g.createdAt,
        })),
      },
    };
  }

  async listMyWithdraws(agentUserId: string) {
    const agent = await this.requireActiveAgent(agentUserId);
    const [items, total] = await this.withdrawRepo.findAndCount({
      where: { agentId: agent.id, method: 'agent' },
      relations: { user: true },
      order: { createdAt: 'DESC' },
      take: 100,
    });
    return {
      items: items.map((w) => ({
        id: w.id,
        diamonds: Number(w.diamonds),
        amountFiat: Number(w.amountFiat),
        status: w.status,
        method: w.method,
        adminNote: w.adminNote,
        createdAt: w.createdAt,
        user: w.user
          ? {
              id: w.user.id,
              publicId: w.user.publicId,
              displayName: w.user.displayName,
              username: w.user.username,
              avatarUrl: w.user.avatarUrl,
            }
          : null,
      })),
      total,
    };
  }

  async completeWithdraw(agentUserId: string, withdrawId: string, note?: string) {
    const agent = await this.requireActiveAgent(agentUserId);
    return this.dataSource.transaction(async (manager) => {
      const req = await manager.findOne(WithdrawRequest, {
        where: { id: withdrawId },
        lock: { mode: 'pessimistic_write' },
      });
      if (!req) throw new NotFoundException('طلب السحب غير موجود');
      if (req.agentId !== agent.id || req.method !== 'agent') {
        throw new BadRequestException('هذا الطلب غير مخصص لك');
      }
      if (req.status !== WithdrawStatus.PENDING && req.status !== WithdrawStatus.APPROVED) {
        throw new BadRequestException('تمت معالجة الطلب مسبقاً');
      }
      req.status = WithdrawStatus.PAID;
      req.reviewedById = agentUserId;
      req.adminNote = note?.trim() || 'تم الدفع عبر الوكيل';
      await manager.save(req);
      await manager.increment(
        Wallet,
        { userId: req.userId },
        'totalWithdrawn',
        Number(req.diamonds),
      );
      return { ok: true, status: req.status, id: req.id };
    });
  }

  async rejectWithdraw(agentUserId: string, withdrawId: string, note?: string) {
    const agent = await this.requireActiveAgent(agentUserId);
    return this.dataSource.transaction(async (manager) => {
      const req = await manager.findOne(WithdrawRequest, {
        where: { id: withdrawId },
        lock: { mode: 'pessimistic_write' },
      });
      if (!req) throw new NotFoundException('طلب السحب غير موجود');
      if (req.agentId !== agent.id || req.method !== 'agent') {
        throw new BadRequestException('هذا الطلب غير مخصص لك');
      }
      if (req.status !== WithdrawStatus.PENDING && req.status !== WithdrawStatus.APPROVED) {
        throw new BadRequestException('تمت معالجة الطلب مسبقاً');
      }
      req.status = WithdrawStatus.REJECTED;
      req.reviewedById = agentUserId;
      req.adminNote = note?.trim() || 'رفض الوكيل';
      await manager.save(req);

      const refundRef = `withdraw_refund:${req.id}`;
      const already = await manager.findOne(WalletTransaction, {
        where: {
          userId: req.userId,
          referenceType: 'withdraw_refund',
          referenceId: refundRef,
        },
      });
      if (!already) {
        const wallet = await manager.findOne(Wallet, {
          where: { userId: req.userId },
          lock: { mode: 'pessimistic_write' },
        });
        if (wallet) {
          const amount = Number(req.diamonds);
          wallet.diamonds = Number(wallet.diamonds || 0) + amount;
          await manager.save(wallet);
          await manager.save(
            manager.create(WalletTransaction, {
              userId: req.userId,
              type: TransactionType.ADMIN_ADJUST,
              currency: CurrencyType.DIAMONDS,
              amount,
              balanceAfter: Number(wallet.diamonds),
              referenceType: 'withdraw_refund',
              referenceId: refundRef,
              description: `Agent rejected withdraw ${req.id}`,
            }),
          );
        }
      }
      return { ok: true, status: req.status, id: req.id };
    });
  }

  private async requireActiveAgent(userId: string) {
    const agent = await this.agentsRepo.findOne({ where: { userId } });
    if (!agent || agent.status !== RechargeAgentStatus.ACTIVE) {
      throw new BadRequestException('حساب الوكيل غير نشط');
    }
    return agent;
  }

  private async findUserByToken(tokenRaw: string) {
    const token = tokenRaw?.trim();
    if (!token || token.length > 128) {
      throw new BadRequestException('معرّف المستخدم غير صالح');
    }
    const isUuid =
      /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(
        token,
      );
    const repo = this.dataSource.getRepository(User);
    if (isUuid) {
      return repo.findOne({ where: { id: token } });
    }
    // Prefer numeric publicId, then username.
    const byPublic = await repo.findOne({ where: { publicId: token } });
    if (byPublic) return byPublic;
    return repo.findOne({ where: { username: token.toLowerCase() } });
  }

  async myAgent(userId: string) {
    const [agent, application, pricing] = await Promise.all([
      this.agentsRepo.findOne({ where: { userId } }),
      this.applicationsRepo.findOne({
        where: { userId },
        order: { createdAt: 'DESC' },
      }),
      this.getPricingConfig(),
    ]);
    return {
      agent: agent ? this.normalizeAgent(agent) : null,
      application: application ? this.normalizeApplication(application) : null,
      pricing,
    };
  }

  async listMyRecharges(userId: string) {
    const agent = await this.agentsRepo.findOne({ where: { userId } });
    if (!agent) return { items: [], total: 0 };
    const [items, total] = await this.rechargesRepo.findAndCount({
      where: { agentId: agent.id },
      relations: { recipient: true },
      order: { createdAt: 'DESC' },
      take: 100,
    });
    return {
      items: items.map((item) => ({
        ...item,
        recipient: item.recipient
          ? {
              id: item.recipient.id,
              username: item.recipient.username,
              displayName: item.recipient.displayName,
              avatarUrl: item.recipient.avatarUrl,
            }
          : null,
      })),
      total,
    };
  }

  async sellToUser(agentUserId: string, dto: SellRechargeDto) {
    const idempotencyKey = dto.idempotencyKey?.trim();
    if (!idempotencyKey || idempotencyKey.length > 96) {
      throw new BadRequestException('A valid idempotencyKey is required');
    }
    const coins = await this.resolveCoins(dto);
    const recipientToken = dto.recipientUsernameOrId?.trim();
    if (!recipientToken) throw new BadRequestException('Recipient is required');

    return this.dataSource.transaction(async (manager) => {
      const agent = await manager.findOne(RechargeAgent, {
        where: { userId: agentUserId },
        lock: { mode: 'pessimistic_write' },
      });
      if (!agent || agent.status !== RechargeAgentStatus.ACTIVE) {
        throw new BadRequestException('Recharge agent is not active');
      }

      const duplicate = await manager.findOne(AgentRecharge, {
        where: { idempotencyKey },
      });
      if (duplicate) {
        if (duplicate.agentId !== agent.id) {
          throw new ConflictException('Idempotency key is already in use');
        }
        return duplicate;
      }

      const isUuid =
        /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(
          recipientToken,
        );
      const isPublicId = /^\d+$/.test(recipientToken);
      let recipient: User | null = null;
      if (isUuid) {
        recipient = await manager.findOne(User, { where: { id: recipientToken } });
      } else if (isPublicId) {
        recipient = await manager.findOne(User, {
          where: { publicId: recipientToken },
        });
      }
      if (!recipient) {
        recipient = await manager.findOne(User, {
          where: { username: recipientToken.toLowerCase() },
        });
      }
      if (!recipient) throw new NotFoundException('Recipient not found');
      if (recipient.status !== UserStatus.ACTIVE) {
        throw new BadRequestException('Recipient account is not active');
      }
      if (recipient.id === agentUserId) {
        throw new BadRequestException('Agent cannot recharge their own account');
      }

      const today = new Date().toISOString().slice(0, 10);
      if (agent.dailySoldKey !== today) {
        agent.dailySoldKey = today;
        agent.dailySoldCoins = 0;
      }
      if (Number(agent.dailySoldCoins) + coins > Number(agent.dailyLimitCoins)) {
        throw new BadRequestException('Daily recharge limit exceeded');
      }
      if (Number(agent.floatCoins) < coins) {
        throw new BadRequestException('Insufficient agent float');
      }

      agent.floatCoins = Number(agent.floatCoins) - coins;
      agent.dailySoldCoins = Number(agent.dailySoldCoins) + coins;
      await manager.save(agent);

      let wallet = await manager.findOne(Wallet, {
        where: { userId: recipient.id },
        lock: { mode: 'pessimistic_write' },
      });
      if (!wallet) wallet = manager.create(Wallet, { userId: recipient.id });
      wallet.coins = Number(wallet.coins || 0) + coins;
      wallet.totalRecharged = Number(wallet.totalRecharged || 0) + coins;
      await manager.save(wallet);

      const commissionCoins = Math.floor((coins * Number(agent.commissionBps)) / 10_000);
      const recharge = await manager.save(
        manager.create(AgentRecharge, {
          agentId: agent.id,
          recipientUserId: recipient.id,
          sku: dto.sku?.trim() || null,
          coins,
          chargedFloat: coins,
          commissionCoins,
          status: 'completed',
          idempotencyKey,
        }),
      );
      const walletTx = await manager.save(
        manager.create(WalletTransaction, {
          userId: recipient.id,
          type: TransactionType.RECHARGE,
          currency: CurrencyType.COINS,
          amount: coins,
          balanceAfter: Number(wallet.coins),
          referenceType: 'agent_recharge',
          referenceId: recharge.id,
          description: `Recharge agent sale of ${coins} coins`,
          metadata: { agentId: agent.id, sku: dto.sku || null },
        }),
      );
      recharge.walletTxId = walletTx.id;
      await manager.save(recharge);

      await manager.save(
        manager.create(RechargeAgentLedger, {
          agentId: agent.id,
          type: AgentLedgerType.SALE,
          amount: -coins,
          balanceAfter: Number(agent.floatCoins),
          referenceId: recharge.id,
          referenceType: 'agent_recharge',
          description: `Sold ${coins} coins to ${recipient.username}`,
          actorUserId: agentUserId,
        }),
      );
      await manager.save(
        manager.create(RechargeOrder, {
          userId: recipient.id,
          sku: dto.sku?.trim() || null,
          coins,
          bonusCoins: 0,
          amountFiat: 0,
          currency: 'USD',
          provider: PaymentProvider.RECHARGE_AGENT,
          status: RechargeStatus.COMPLETED,
          providerOrderId: recharge.id,
          providerPaymentId: null,
          providerPayload: { agentId: agent.id, agentRechargeId: recharge.id },
          completedAt: new Date(),
        }),
      );

      return recharge;
    }).then(async (recharge) => {
      // Count agent sales toward user monthly/supporter promos (retail USD).
      try {
        const config = await this.getPricingConfig();
        const usd =
          (Number(recharge.coins) / 100) *
          Number(config.suggestedRetailPer100CoinsUsdt || 0.012);
        if (usd > 0 && this.promotions) {
          void this.promotions
            .onUserRechargeCompleted(recharge.recipientUserId, usd)
            .catch(() => undefined);
        }
      } catch {
        /* non-fatal */
      }
      let agentName = 'وكيل الشحن';
      try {
        const agentRow = await this.agentsRepo.findOne({
          where: { id: recharge.agentId },
          relations: ['user'],
        });
        agentName =
          agentRow?.user?.displayName?.trim() ||
          agentRow?.user?.username?.trim() ||
          agentName;
      } catch {
        /* keep default */
      }
      await this.notifications.notifyUser(
        recharge.recipientUserId,
        NotificationType.WALLET,
        'تم شحن رصيدك',
        `تم إضافة ${recharge.coins} عملة إلى محفظتك عبر ${agentName}.`,
        {
          agentId: recharge.agentId,
          coins: recharge.coins,
          officialNews: true,
          action: 'agent_recharge',
          targetUserId: recharge.recipientUserId,
          source: 'recharge_agent',
        },
      );
      return recharge;
    });
  }

  async adminAssign(adminId: string, dto: AdminAssignRechargeAgentDto) {
    this.validateAgentNumbers(dto);
    return this.dataSource.transaction(async (manager) => {
      const user = await manager.findOne(User, { where: { id: dto.userId } });
      if (!user) throw new NotFoundException('User not found');
      let agent = await manager.findOne(RechargeAgent, {
        where: { userId: dto.userId },
        lock: { mode: 'pessimistic_write' },
      });
      const previousFloat = Number(agent?.floatCoins || 0);
      if (!agent) {
        agent = manager.create(RechargeAgent, {
          userId: dto.userId,
          source: RechargeAgentSource.ADMIN,
        });
      }
      agent.status = RechargeAgentStatus.ACTIVE;
      agent.listedInDirectory = false;
      agent.floatCoins = dto.floatCoins ?? previousFloat;
      agent.commissionBps = dto.commissionBps ?? Number(agent.commissionBps || 0);
      agent.dailyLimitCoins =
        dto.dailyLimitCoins ?? Number(agent.dailyLimitCoins || 500_000);
      agent.notes = dto.notes?.trim() || agent.notes || null;
      agent.reviewedBy = adminId;
      agent.reviewedAt = new Date();
      agent = await manager.save(agent);
      if (Number(agent.floatCoins) !== previousFloat) {
        await this.applyFloatCreditBonus(
          manager,
          agent,
          Number(agent.floatCoins) - previousFloat,
          adminId,
          dto.notes || 'Initial float assignment',
        );
      }
      return this.normalizeAgent(agent);
    });
  }

  async adminListAgents() {
    const items = await this.agentsRepo.find({
      relations: { user: true },
      order: { createdAt: 'DESC' },
    });
    return { items: items.map((agent) => this.normalizeAgent(agent)), total: items.length };
  }

  async adminListApplications() {
    // Paid join applications retired — internal assign only.
    return { items: [], total: 0 };
  }

  private async _legacyAdminListApplicationsDisabled() {
    const items = await this.applicationsRepo.find({
      relations: { user: true },
      order: { createdAt: 'DESC' },
    });
    return {
      items: items.map((application) => this.normalizeApplication(application)),
      total: items.length,
    };
  }

  async adminReviewApplication(
    _id: string,
    _action: 'approve' | 'reject',
    _adminId: string,
    _note?: string,
    _options?: { floatCoins?: number; commissionBps?: number },
  ) {
    throw new GoneException(
      'طلبات الانضمام المدفوعة أُوقفت — عيّن وكلاء البيع الداخلي يدوياً',
    );
  }

  /** @deprecated Paid applications retired. */
  private async _legacyAdminReviewApplication(
    id: string,
    action: 'approve' | 'reject',
    adminId: string,
    note?: string,
    options?: { floatCoins?: number; commissionBps?: number },
  ) {
    return this.dataSource.transaction(async (manager) => {
      const application = await manager.findOne(RechargeAgentApplication, {
        where: { id },
        lock: { mode: 'pessimistic_write' },
      });
      if (!application) throw new NotFoundException('Application not found');
      if (application.status !== RechargeAgentStatus.PENDING) {
        throw new BadRequestException('Application has already been reviewed');
      }
      application.status =
        action === 'approve' ? RechargeAgentStatus.ACTIVE : RechargeAgentStatus.REJECTED;
      application.reviewNote = note?.trim() || null;
      application.reviewedBy = adminId;
      application.reviewedAt = new Date();
      await manager.save(application);

      if (action === 'approve') {
        let agent = await manager.findOne(RechargeAgent, {
          where: { userId: application.userId },
          lock: { mode: 'pessimistic_write' },
        });
        const previousFloat = Number(agent?.floatCoins || 0);
        if (!agent) {
          agent = manager.create(RechargeAgent, {
            userId: application.userId,
            source: RechargeAgentSource.APPLICATION,
          });
        }
        agent.status = RechargeAgentStatus.ACTIVE;
        agent.listedInDirectory = true;
        agent.reviewedBy = adminId;
        agent.reviewedAt = new Date();
        if (note?.trim()) agent.notes = note.trim();
        if (options?.commissionBps !== undefined) {
          agent.commissionBps = options.commissionBps;
        }
        if (options?.floatCoins !== undefined) {
          agent.floatCoins = options.floatCoins;
        } else if (Number(application.requestedCoins) > 0) {
          agent.floatCoins = Number(application.requestedCoins);
        }
        agent = await manager.save(agent);
        const floatDelta = Number(agent.floatCoins) - previousFloat;
        if (floatDelta !== 0) {
          await this.applyFloatCreditBonus(
            manager,
            agent,
            floatDelta,
            adminId,
            note || 'Application approval float',
          );
        }
      }
      return application;
    });
  }

  /**
   * Adjust agent float. Positive credits apply agent-tier bonus when USDT paid
   * hits $200/+12%, $500/+15%, $1000/+20%. Pass `usdPaid` for accurate tiers.
   */
  async adminAdjustFloat(
    agentId: string,
    amount: number,
    adminId: string,
    note?: string,
    usdPaid?: number,
  ) {
    if (!Number.isSafeInteger(amount) || amount === 0) {
      throw new BadRequestException('Float amount must be a non-zero integer');
    }
    const config = await this.getPricingConfig();
    let bonusCoins = 0;
    let bonusPercent = 0;
    let usdUsed = 0;
    if (amount > 0 && this.promotions) {
      const estimated =
        (amount / 100) * Number(config.wholesalePer100CoinsUsdt || 0.009);
      usdUsed =
        Number.isFinite(Number(usdPaid)) && Number(usdPaid) > 0
          ? Number(usdPaid)
          : estimated;
      const bonus = this.promotions.computeAgentFloatBonusCoins(amount, usdUsed);
      bonusCoins = bonus.bonusCoins;
      bonusPercent = bonus.bonusPercent;
    }
    const credit = amount + (amount > 0 ? bonusCoins : 0);

    return this.dataSource.transaction(async (manager) => {
      const agent = await manager.findOne(RechargeAgent, {
        where: { id: agentId },
        lock: { mode: 'pessimistic_write' },
      });
      if (!agent) throw new NotFoundException('Recharge agent not found');
      const nextFloat = Number(agent.floatCoins) + credit;
      if (nextFloat < 0) throw new BadRequestException('Agent float cannot be negative');
      agent.floatCoins = nextFloat;
      await manager.save(agent);
      const bonusNote =
        bonusCoins > 0
          ? ` · agent tier +${bonusPercent}% on $${usdUsed.toFixed(0)} (+${bonusCoins} coins)`
          : '';
      await this.saveFloatLedger(
        manager,
        agent,
        credit,
        adminId,
        (note || 'Admin float adjustment') + bonusNote,
      );
      return {
        ...this.normalizeAgent(agent),
        floatCredited: credit,
        bonusPercent,
        bonusCoins,
        usdPaid: usdUsed,
      };
    });
  }

  /** Apply agent float-tier bonus when assigning / approving initial float. */
  private async applyFloatCreditBonus(
    manager: import('typeorm').EntityManager,
    agent: RechargeAgent,
    floatDelta: number,
    adminId: string,
    note: string,
    usdPaid?: number,
  ) {
    if (floatDelta <= 0) {
      await this.saveFloatLedger(manager, agent, floatDelta, adminId, note);
      return { bonusCoins: 0, bonusPercent: 0 };
    }
    const config = await this.getPricingConfig();
    const estimated =
      (floatDelta / 100) * Number(config.wholesalePer100CoinsUsdt || 0.009);
    const usd =
      Number.isFinite(Number(usdPaid)) && Number(usdPaid) > 0
        ? Number(usdPaid)
        : estimated;
    let bonusCoins = 0;
    let bonusPercent = 0;
    if (this.promotions) {
      const bonus = this.promotions.computeAgentFloatBonusCoins(floatDelta, usd);
      bonusCoins = bonus.bonusCoins;
      bonusPercent = bonus.bonusPercent;
    }
    if (bonusCoins > 0) {
      agent.floatCoins = Number(agent.floatCoins) + bonusCoins;
      await manager.save(agent);
    }
    const bonusNote =
      bonusCoins > 0
        ? ` · agent tier +${bonusPercent}% on $${usd.toFixed(0)} (+${bonusCoins} coins)`
        : '';
    await this.saveFloatLedger(
      manager,
      agent,
      floatDelta + bonusCoins,
      adminId,
      note + bonusNote,
    );
    return { bonusCoins, bonusPercent, usdPaid: usd };
  }

  async adminPatchAgent(id: string, dto: AdminPatchRechargeAgentDto, adminId: string) {
    this.validateAgentNumbers(dto);
    const agent = await this.agentsRepo.findOne({ where: { id } });
    if (!agent) throw new NotFoundException('Recharge agent not found');
    if (dto.status !== undefined) {
      if (!Object.values(RechargeAgentStatus).includes(dto.status)) {
        throw new BadRequestException('Invalid agent status');
      }
      agent.status = dto.status;
    }
    if (dto.commissionBps !== undefined) agent.commissionBps = dto.commissionBps;
    if (dto.dailyLimitCoins !== undefined) agent.dailyLimitCoins = dto.dailyLimitCoins;
    if (dto.notes !== undefined) agent.notes = dto.notes.trim() || null;
    if (dto.country !== undefined) agent.country = dto.country?.trim() || null;
    if (dto.whatsapp !== undefined) agent.whatsapp = dto.whatsapp?.trim() || null;
    if (dto.telegram !== undefined) agent.telegram = dto.telegram?.trim() || null;
    // Directory contact listing retired.
    agent.listedInDirectory = false;
    agent.reviewedBy = adminId;
    agent.reviewedAt = new Date();
    return this.normalizeAgent(await this.agentsRepo.save(agent));
  }

  async publicDirectory(_country?: string) {
    // WhatsApp / Telegram external agent directory retired — internal sell only.
    return { items: [], countries: [], total: 0 };
  }

  async adminListContacts() {
    return { items: [], total: 0 };
  }

  async adminUpsertContact(_dto: AdminUpsertAgentContactDto) {
    throw new GoneException(
      'دليل التواصل (واتساب/تيليجرام) أُزيل — وكلاء البيع الداخلي فقط',
    );
  }

  async adminDeleteContact(_id: string) {
    throw new GoneException(
      'دليل التواصل (واتساب/تيليجرام) أُزيل — وكلاء البيع الداخلي فقط',
    );
  }

  async adminDeleteAgent(id: string) {
    const agent = await this.agentsRepo.findOne({ where: { id } });
    if (!agent) throw new NotFoundException('Recharge agent not found');
    await this.agentsRepo.remove(agent);
    return { deleted: true, id, userId: agent.userId };
  }

  async adminGetPricing() {
    return this.getPricingConfig();
  }

  async adminUpdatePricing(
    patch: Partial<RechargeAgentPricingConfig>,
  ): Promise<RechargeAgentPricingConfig> {
    const current = await this.getPricingConfig();
    const next: RechargeAgentPricingConfig = {
      membershipFeeUsdt:
        patch.membershipFeeUsdt !== undefined
          ? Number(patch.membershipFeeUsdt)
          : current.membershipFeeUsdt,
      wholesalePer100CoinsUsdt:
        patch.wholesalePer100CoinsUsdt !== undefined
          ? Number(patch.wholesalePer100CoinsUsdt)
          : current.wholesalePer100CoinsUsdt,
      suggestedRetailPer100CoinsUsdt:
        patch.suggestedRetailPer100CoinsUsdt !== undefined
          ? Number(patch.suggestedRetailPer100CoinsUsdt)
          : current.suggestedRetailPer100CoinsUsdt,
      minInitialCoins:
        patch.minInitialCoins !== undefined
          ? Number(patch.minInitialCoins)
          : current.minInitialCoins,
      maxInitialCoins:
        patch.maxInitialCoins !== undefined
          ? Number(patch.maxInitialCoins)
          : current.maxInitialCoins,
    };
    for (const [key, value] of Object.entries(next)) {
      if (!Number.isFinite(value) || value < 0) {
        throw new BadRequestException(`${key} must be a non-negative number`);
      }
    }
    if (
      !Number.isSafeInteger(next.minInitialCoins) ||
      !Number.isSafeInteger(next.maxInitialCoins) ||
      next.minInitialCoins <= 0 ||
      next.maxInitialCoins < next.minInitialCoins
    ) {
      throw new BadRequestException('Invalid initial coin limits');
    }
    let row = await this.settingsRepo.findOne({
      where: { key: RECHARGE_AGENT_CONFIG_KEY },
    });
    if (!row) {
      row = this.settingsRepo.create({
        key: RECHARGE_AGENT_CONFIG_KEY,
        value: JSON.stringify(next),
        description: 'Recharge agent membership and USDT pricing',
      });
    } else {
      row.value = JSON.stringify(next);
    }
    await this.settingsRepo.save(row);
    return next;
  }

  private async getPricingConfig(): Promise<RechargeAgentPricingConfig> {
    const row = await this.settingsRepo.findOne({
      where: { key: RECHARGE_AGENT_CONFIG_KEY },
    });
    let parsed: Partial<RechargeAgentPricingConfig> = {};
    try {
      parsed = row?.value ? JSON.parse(row.value) : {};
    } catch {
      parsed = {};
    }
    return {
      membershipFeeUsdt: this.nonNegativeNumber(
        parsed.membershipFeeUsdt,
        DEFAULT_RECHARGE_AGENT_CONFIG.membershipFeeUsdt,
      ),
      wholesalePer100CoinsUsdt: this.nonNegativeNumber(
        parsed.wholesalePer100CoinsUsdt,
        DEFAULT_RECHARGE_AGENT_CONFIG.wholesalePer100CoinsUsdt,
      ),
      suggestedRetailPer100CoinsUsdt: this.nonNegativeNumber(
        parsed.suggestedRetailPer100CoinsUsdt,
        DEFAULT_RECHARGE_AGENT_CONFIG.suggestedRetailPer100CoinsUsdt,
      ),
      minInitialCoins: this.positiveInteger(
        parsed.minInitialCoins,
        DEFAULT_RECHARGE_AGENT_CONFIG.minInitialCoins,
      ),
      maxInitialCoins: this.positiveInteger(
        parsed.maxInitialCoins,
        DEFAULT_RECHARGE_AGENT_CONFIG.maxInitialCoins,
      ),
    };
  }

  private quote(config: RechargeAgentPricingConfig, coins: number) {
    const stockCostUsdt = Number(
      ((coins / 100) * config.wholesalePer100CoinsUsdt).toFixed(8),
    );
    const suggestedRetailUsdt = Number(
      ((coins / 100) * config.suggestedRetailPer100CoinsUsdt).toFixed(8),
    );
    const membershipFeeUsdt = Number(config.membershipFeeUsdt.toFixed(8));
    return {
      coins,
      membershipFeeUsdt,
      stockCostUsdt,
      totalUsdt: Number((membershipFeeUsdt + stockCostUsdt).toFixed(8)),
      suggestedRetailUsdt,
      wholesalePer100CoinsUsdt: config.wholesalePer100CoinsUsdt,
      suggestedRetailPer100CoinsUsdt: config.suggestedRetailPer100CoinsUsdt,
    };
  }

  private normalizeNetwork(network?: string): 'TRX' | 'BSC' {
    const value = String(network || 'TRX').trim().toUpperCase();
    if (value !== 'TRX' && value !== 'BSC') {
      throw new BadRequestException('network must be TRX or BSC');
    }
    return value;
  }

  private nonNegativeNumber(value: unknown, fallback: number) {
    const number = Number(value);
    return Number.isFinite(number) && number >= 0 ? number : fallback;
  }

  private positiveInteger(value: unknown, fallback: number) {
    const number = Number(value);
    return Number.isSafeInteger(number) && number > 0 ? number : fallback;
  }

  private async resolveCoins(dto: SellRechargeDto): Promise<number> {
    if (dto.sku?.trim()) {
      const { items } = await this.walletService.listPackages();
      const pkg = items.find((item) => String(item.sku) === dto.sku!.trim());
      if (!pkg) throw new NotFoundException('Recharge package not found');
      const coins = Number(pkg.coins) + Number(pkg.bonusCoins || 0);
      if (!Number.isSafeInteger(coins) || coins <= 0) {
        throw new BadRequestException('Recharge package has invalid coins');
      }
      return coins;
    }
    if (!Number.isSafeInteger(dto.coins) || Number(dto.coins) <= 0) {
      throw new BadRequestException('A positive integer coins value is required');
    }
    return Number(dto.coins);
  }

  private validateAgentNumbers(dto: {
    floatCoins?: number;
    commissionBps?: number;
    dailyLimitCoins?: number;
  }) {
    if (
      dto.floatCoins !== undefined &&
      (!Number.isSafeInteger(dto.floatCoins) || dto.floatCoins < 0)
    ) {
      throw new BadRequestException('floatCoins must be a non-negative integer');
    }
    if (
      dto.commissionBps !== undefined &&
      (!Number.isInteger(dto.commissionBps) ||
        dto.commissionBps < 0 ||
        dto.commissionBps > 10_000)
    ) {
      throw new BadRequestException('commissionBps must be between 0 and 10000');
    }
    if (
      dto.dailyLimitCoins !== undefined &&
      (!Number.isSafeInteger(dto.dailyLimitCoins) || dto.dailyLimitCoins < 0)
    ) {
      throw new BadRequestException('dailyLimitCoins must be a non-negative integer');
    }
  }

  private async saveFloatLedger(
    manager: import('typeorm').EntityManager,
    agent: RechargeAgent,
    amount: number,
    adminId: string,
    note: string,
  ) {
    await manager.save(
      manager.create(RechargeAgentLedger, {
        agentId: agent.id,
        type: amount > 0 ? AgentLedgerType.FLOAT_CREDIT : AgentLedgerType.FLOAT_DEBIT,
        amount,
        balanceAfter: Number(agent.floatCoins),
        referenceId: null,
        referenceType: 'admin_adjustment',
        description: note,
        actorUserId: adminId,
      }),
    );
  }

  private normalizeAgent(agent: RechargeAgent) {
    return {
      ...agent,
      floatCoins: Number(agent.floatCoins),
      dailySoldCoins: Number(agent.dailySoldCoins),
      dailyLimitCoins: Number(agent.dailyLimitCoins),
    };
  }

  private normalizeApplication(application: RechargeAgentApplication) {
    return {
      ...application,
      requestedCoins: Number(application.requestedCoins || 0),
      membershipFeeUsdt: Number(application.membershipFeeUsdt || 0),
      stockCostUsdt: Number(application.stockCostUsdt || 0),
      totalPaidUsdt: Number(application.totalPaidUsdt || 0),
    };
  }
}
