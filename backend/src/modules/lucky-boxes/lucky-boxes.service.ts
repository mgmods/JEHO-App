import {
  Injectable,
  BadRequestException,
  NotFoundException,
  Logger,
  OnModuleInit,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { DataSource, Repository, EntityManager } from 'typeorm';
import * as crypto from 'crypto';
import { LuckyBox, LuckyBoxReward } from '../../database/entities/lucky-box.entity';
import { LuckyBoxOpen } from '../../database/entities/lucky-box-open.entity';
import { LuckyRewardGrant } from '../../database/entities/lucky-reward-grant.entity';
import { Wallet } from '../../database/entities/wallet.entity';
import {
  WalletTransaction,
  TransactionType,
  CurrencyType,
} from '../../database/entities/wallet-transaction.entity';
import { Room } from '../../database/entities/room.entity';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { Agency } from '../../database/entities/agency.entity';
import {
  AgencyMember,
  AgencyRole,
} from '../../database/entities/agency-member.entity';
import { AdminUpsertBoxDto } from './dto/lucky-boxes.dto';
import { paginate, PaginationDto } from '../../common/dto/pagination.dto';
import { bootCatalogSeedEnabled } from '../../common/db-authoritative';

const CURRENCY_MAP: Record<string, CurrencyType> = {
  coins: CurrencyType.COINS,
  diamonds: CurrencyType.DIAMONDS,
};

const WALLET_FIELD: Record<string, keyof Pick<Wallet, 'coins' | 'diamonds' | 'gamePoints'>> = {
  coins: 'coins',
  diamonds: 'diamonds',
  silver: 'coins', // silver retired — maps to coins
  points: 'coins', // game points retired — maps to coins
};

function todayKey(): string {
  return new Date().toISOString().slice(0, 10);
}

@Injectable()
export class LuckyBoxesService implements OnModuleInit {
  private readonly logger = new Logger(LuckyBoxesService.name);

  constructor(
    @InjectRepository(LuckyBox)
    private readonly boxRepo: Repository<LuckyBox>,
    @InjectRepository(LuckyBoxOpen)
    private readonly openRepo: Repository<LuckyBoxOpen>,
    @InjectRepository(LuckyRewardGrant)
    private readonly grantRepo: Repository<LuckyRewardGrant>,
    @InjectRepository(Wallet)
    private readonly walletRepo: Repository<Wallet>,
    @InjectRepository(AppSetting)
    private readonly settingsRepo: Repository<AppSetting>,
    private readonly dataSource: DataSource,
  ) {}

  async onModuleInit() {
    await this.ensureDefaults();
  }

  // ─── Public ──────────────────────────────────────────────

  async listActive(userId?: string) {
    const boxes = await this.boxRepo.find({
      where: { isActive: true },
      order: { createdAt: 'ASC' },
    });

    if (!userId) {
      return boxes.map((b) => ({
        ...b,
        opensToday: 0,
        remainingToday: this.effectiveLimit(b),
      }));
    }

    const day = todayKey();
    const result = await Promise.all(
      boxes.map(async (box) => {
        const opensToday = await this.countUserOpens(userId, box, day);
        const limit = this.effectiveLimit(box);
        return {
          ...box,
          opensToday,
          remainingToday: Math.max(0, limit - opensToday),
        };
      }),
    );
    return result;
  }

  async open(
    userId: string,
    boxId: string,
    opts: { roomId?: string; agencyId?: string } = {},
  ) {
    const enabled = await this.isEnabled();
    if (!enabled) throw new BadRequestException('Lucky boxes are currently disabled');

    return this.dataSource.transaction(async (manager) => {
      const box = await manager.findOne(LuckyBox, {
        where: { id: boxId, isActive: true },
        lock: { mode: 'pessimistic_write' },
      });
      if (!box) throw new NotFoundException('Lucky box not found or inactive');

      if (!box.rewardsJson?.length) {
        throw new BadRequestException('Box has no configured rewards');
      }

      const day = todayKey();
      const limit = this.effectiveLimit(box);
      const opensToday = await this.countUserOpensTx(manager, userId, box, day);
      if (opensToday >= limit) {
        throw new BadRequestException(
          box.kind === 'free_daily'
            ? 'Already claimed this free box'
            : 'Daily limit reached for this box',
        );
      }

      const wallet = await manager.findOne(Wallet, {
        where: { userId },
        lock: { mode: 'pessimistic_write' },
      });
      if (!wallet) throw new NotFoundException('Wallet not found');

      let costPaid = 0;

      if (box.kind === 'paid' && box.costCoins > 0) {
        if (Number(wallet.coins) < box.costCoins) {
          throw new BadRequestException('Insufficient coins');
        }
        wallet.coins = Number(wallet.coins) - box.costCoins;
        costPaid = box.costCoins;
        await manager.save(wallet);

        await manager.save(
          manager.create(WalletTransaction, {
            userId,
            type: TransactionType.EXCHANGE,
            currency: CurrencyType.COINS,
            amount: -box.costCoins,
            balanceAfter: Number(wallet.coins),
            referenceType: 'lucky_box_cost',
            referenceId: `${box.id}:${day}:${opensToday + 1}`,
            description: `Lucky box "${box.title}" cost`,
          }),
        );
      }

      if (box.kind === 'host_funded') {
        if (!opts.roomId) throw new BadRequestException('roomId required for host-funded box');
        const room = await manager.findOne(Room, { where: { id: opts.roomId } });
        if (!room) throw new NotFoundException('Room not found');
        await this.chargePool(
          manager,
          `room_lucky_balance.${room.id}`,
          box.costCoins,
          'Room lucky-box pool has insufficient balance',
        );
        costPaid = box.costCoins;
      }

      if (box.kind === 'agency_funded') {
        if (!opts.agencyId) throw new BadRequestException('agencyId required for agency-funded box');
        await this.chargeAgencyBalance(manager, opts.agencyId, box.costCoins, box.title);
        costPaid = box.costCoins;
      }

      const rngSeed = crypto.randomBytes(16).toString('hex');
      const reward = this.weightedPick(box.rewardsJson, rngSeed);

      const openRecord = await manager.save(
        manager.create(LuckyBoxOpen, {
          boxId: box.id,
          userId,
          roomId: opts.roomId || null,
          agencyId: opts.agencyId || null,
          costPaid,
          rewardType: reward.type,
          rewardAmount: reward.amount,
          dayKey: day,
          metadata: { rngSeed },
        }),
      );

      const walletField = WALLET_FIELD[reward.type];
      if (!walletField) throw new BadRequestException('Unknown reward type');

      const freshWallet = await manager.findOne(Wallet, {
        where: { userId },
        lock: { mode: 'pessimistic_write' },
      });
      if (!freshWallet) throw new NotFoundException('Wallet not found');

      freshWallet[walletField] = (Number(freshWallet[walletField]) || 0) + reward.amount;
      await manager.save(freshWallet);

      let walletTxId: string | null = null;
      const currencyEnum = CURRENCY_MAP[reward.type];
      if (currencyEnum) {
        const tx = await manager.save(
          manager.create(WalletTransaction, {
            userId,
            type: TransactionType.LUCKY_REWARD,
            currency: currencyEnum,
            amount: reward.amount,
            balanceAfter: Number(freshWallet[walletField]),
            referenceType: 'lucky_box',
            referenceId: openRecord.id,
            description: `Lucky box "${box.title}" reward: ${reward.amount} ${reward.type}`,
          }),
        );
        walletTxId = tx.id;
      }

      const grant = await manager.save(
        manager.create(LuckyRewardGrant, {
          openId: openRecord.id,
          userId,
          rewardType: reward.type,
          amount: reward.amount,
          walletTxId,
        }),
      );

      return {
        openId: openRecord.id,
        boxCode: box.code,
        boxTitle: box.title,
        reward: { type: reward.type, amount: reward.amount },
        grantId: grant.id,
        remainingToday: Math.max(0, this.effectiveLimit(box) - opensToday - 1),
      };
    });
  }

  async fundRoom(userId: string, roomId: string, amount: number) {
    return this.dataSource.transaction(async (manager) => {
      const room = await manager.findOne(Room, {
        where: { id: roomId },
        lock: { mode: 'pessimistic_write' },
      });
      if (!room) throw new NotFoundException('Room not found');
      if (room.hostId !== userId && room.activeHostId !== userId) {
        throw new BadRequestException('Only the room host can fund its lucky boxes');
      }
      return this.fundPool(
        manager,
        userId,
        `room_lucky_balance.${roomId}`,
        amount,
        `Funded lucky boxes for room ${roomId}`,
      );
    });
  }

  async fundAgency(userId: string, agencyId: string, amount: number) {
    return this.dataSource.transaction(async (manager) => {
      const agency = await manager.findOne(Agency, { where: { id: agencyId } });
      if (!agency) throw new NotFoundException('Agency not found');
      const member = await manager.findOne(AgencyMember, {
        where: { agencyId, userId },
      });
      const canFund =
        agency.ownerId === userId ||
        member?.role === AgencyRole.OWNER ||
        member?.role === AgencyRole.MANAGER;
      if (!canFund) {
        throw new BadRequestException('Agency owner or manager permission required');
      }
      return this.fundPool(
        manager,
        userId,
        `agency_balance.${agencyId}`,
        amount,
        `Funded lucky boxes for agency ${agencyId}`,
      );
    });
  }

  // ─── Admin ───────────────────────────────────────────────

  async adminUpsertBox(dto: AdminUpsertBoxDto) {
    const rewardsJson: LuckyBoxReward[] = (dto.rewardsJson || []).map((r) => ({
      type: r.type,
      amount: Math.max(0, Math.floor(Number(r.amount) || 0)),
      weight: Math.max(0, Math.floor(Number(r.weight) || 0)),
    }));
    let box: LuckyBox;
    if (dto.id) {
      const existing = await this.boxRepo.findOne({ where: { id: dto.id } });
      if (!existing) throw new NotFoundException('Box not found');
      box = Object.assign(existing, {
        code: dto.code,
        title: dto.title,
        kind: dto.kind,
        costCoins: dto.costCoins ?? existing.costCoins,
        dailyLimitPerUser: dto.dailyLimitPerUser ?? existing.dailyLimitPerUser,
        isActive: dto.isActive ?? existing.isActive,
        rewardsJson,
      });
    } else {
      box = this.boxRepo.create({
        code: dto.code,
        title: dto.title,
        kind: dto.kind,
        costCoins: dto.costCoins ?? 0,
        dailyLimitPerUser: dto.dailyLimitPerUser ?? 1,
        isActive: dto.isActive ?? true,
        rewardsJson,
      });
    }
    return this.boxRepo.save(box);
  }

  async adminListBoxes() {
    return this.boxRepo.find({ order: { createdAt: 'ASC' } });
  }

  async adminDeleteBox(boxId: string) {
    const box = await this.boxRepo.findOne({ where: { id: boxId } });
    if (!box) throw new NotFoundException('Box not found');
    // Hard delete — cascade removes opens/grants via FK; also clear orphans safely.
    await this.dataSource.transaction(async (manager) => {
      const opens = await manager.find(LuckyBoxOpen, { where: { boxId } });
      if (opens.length) {
        const openIds = opens.map((o) => o.id);
        await manager
          .createQueryBuilder()
          .delete()
          .from(LuckyRewardGrant)
          .where('"openId" IN (:...openIds)', { openIds })
          .execute();
        await manager.delete(LuckyBoxOpen, { boxId });
      }
      await manager.delete(LuckyBox, { id: boxId });
    });
    return { deleted: true, id: boxId };
  }

  async adminListOpens(query: PaginationDto) {
    const [items, total] = await this.openRepo.findAndCount({
      relations: ['box'],
      order: { createdAt: 'DESC' },
      skip: query.skip,
      take: query.limit || 20,
    });
    return paginate(items, total, query.page || 1, query.limit || 20);
  }

  // ─── Helpers ─────────────────────────────────────────────

  private weightedPick(rewards: LuckyBoxReward[], seed: string): LuckyBoxReward {
    const totalWeight = rewards.reduce((sum, r) => sum + r.weight, 0);
    if (!Number.isFinite(totalWeight) || totalWeight <= 0) {
      throw new BadRequestException('Box reward weights are invalid');
    }
    const digest = crypto.createHash('sha256').update(seed).digest();
    const sample = digest.readUIntBE(0, 6);
    let roll = (sample / 0x1000000000000) * totalWeight;
    for (const r of rewards) {
      roll -= r.weight;
      if (roll <= 0) return r;
    }
    return rewards[rewards.length - 1];
  }

  private async fundPool(
    manager: EntityManager,
    userId: string,
    key: string,
    amount: number,
    description: string,
  ) {
    const value = Math.max(1, Math.floor(Number(amount) || 0));
    const wallet = await manager.findOne(Wallet, {
      where: { userId },
      lock: { mode: 'pessimistic_write' },
    });
    if (!wallet || Number(wallet.coins) < value) {
      throw new BadRequestException('Insufficient coins');
    }
    wallet.coins = Number(wallet.coins) - value;
    await manager.save(wallet);

    await manager.query('SELECT pg_advisory_xact_lock(hashtext($1))', [key]);
    let setting = await manager.findOne(AppSetting, {
      where: { key },
      lock: { mode: 'pessimistic_write' },
    });
    const balance = Number(setting?.value) || 0;
    if (!setting) {
      setting = manager.create(AppSetting, {
        key,
        value: String(balance + value),
        description,
      });
    } else {
      setting.value = String(balance + value);
    }
    await manager.save(setting);
    await manager.save(
      manager.create(WalletTransaction, {
        userId,
        type: TransactionType.EXCHANGE,
        currency: CurrencyType.COINS,
        amount: -value,
        balanceAfter: Number(wallet.coins),
        referenceType: 'lucky_pool_fund',
        referenceId: crypto.randomUUID(),
        description,
        metadata: { poolKey: key, fundedAmount: value },
      }),
    );
    return { funded: value, poolBalance: balance + value };
  }

  private async chargePool(
    manager: EntityManager,
    key: string,
    amount: number,
    insufficientMessage: string,
  ) {
    if (amount <= 0) return;
    await manager.query('SELECT pg_advisory_xact_lock(hashtext($1))', [key]);
    const setting = await manager.findOne(AppSetting, {
      where: { key },
      lock: { mode: 'pessimistic_write' },
    });
    const current = Number(setting?.value) || 0;
    if (!setting || current < amount) {
      throw new BadRequestException(insufficientMessage);
    }
    setting.value = String(current - amount);
    await manager.save(setting);
  }

  private async chargeAgencyBalance(
    manager: EntityManager,
    agencyId: string,
    amount: number,
    _boxTitle: string,
  ) {
    return this.chargePool(
      manager,
      `agency_balance.${agencyId}`,
      amount,
      'Agency has insufficient balance to fund this box',
    );
  }

  private async isEnabled(): Promise<boolean> {
    const row = await this.settingsRepo.findOne({
      where: { key: 'lucky_box.enabled' },
    });
    if (!row) return true;
    return row.value !== 'false' && row.value !== '0';
  }

  /** Free float box: once per user forever. Paid boxes: dailyLimitPerUser. */
  private effectiveLimit(box: LuckyBox): number {
    if (box.kind === 'free_daily') return 1;
    return Math.max(1, box.dailyLimitPerUser || 1);
  }

  private async countUserOpens(userId: string, box: LuckyBox, day: string): Promise<number> {
    if (box.kind === 'free_daily') {
      return this.openRepo.count({ where: { userId, boxId: box.id } });
    }
    return this.openRepo.count({ where: { userId, boxId: box.id, dayKey: day } });
  }

  private async countUserOpensTx(
    manager: EntityManager,
    userId: string,
    box: LuckyBox,
    day: string,
  ): Promise<number> {
    if (box.kind === 'free_daily') {
      return manager.count(LuckyBoxOpen, { where: { userId, boxId: box.id } });
    }
    return manager.count(LuckyBoxOpen, { where: { userId, boxId: box.id, dayKey: day } });
  }

  private async ensureDefaults() {
    // Never rewrite live boxes/rewards. Only seed empty installs when opt-in.
    if (!bootCatalogSeedEnabled()) {
      this.logger.log('Lucky boxes: DB authoritative (no boot mutate)');
      return;
    }

    // Enforce free box = one claim for life (fixes repeat claiming).
    await this.boxRepo
      .createQueryBuilder()
      .update(LuckyBox)
      .set({ dailyLimitPerUser: 1 })
      .where('kind = :kind', { kind: 'free_daily' })
      .execute();

    const safePremiumRewards = [
      { type: 'coins' as const, amount: 100, weight: 40 },
      { type: 'coins' as const, amount: 300, weight: 28 },
      { type: 'coins' as const, amount: 500, weight: 18 },
      { type: 'coins' as const, amount: 400, weight: 10 },
      { type: 'points' as const, amount: 40, weight: 4 },
    ];

    // Strip cashout diamonds from any existing boxes (economy safety).
    const existingBoxes = await this.boxRepo.find();
    for (const box of existingBoxes) {
      const rewards = Array.isArray(box.rewardsJson) ? box.rewardsJson : [];
      if (!rewards.some((r) => r?.type === 'diamonds')) continue;
      if (box.code === 'premium_box' || box.kind === 'paid') {
        box.rewardsJson = safePremiumRewards;
      } else {
        box.rewardsJson = rewards
          .filter((r) => r?.type !== 'diamonds')
          .map((r) => ({ ...r }));
        if (!box.rewardsJson.length) {
          box.rewardsJson = [
            { type: 'coins', amount: 20, weight: 50 },
            { type: 'coins', amount: 100, weight: 40 },
            { type: 'points', amount: 5, weight: 10 },
          ];
        }
      }
      await this.boxRepo.save(box);
    }

    const count = await this.boxRepo.count();
    if (count > 0) return;

    this.logger.log('Seeding default lucky boxes…');

    await this.boxRepo.save([
      this.boxRepo.create({
        code: 'daily_free',
        title: 'صندوق الحظ',
        kind: 'free_daily',
        costCoins: 0,
        dailyLimitPerUser: 1,
        isActive: true,
        rewardsJson: [
          { type: 'coins', amount: 20, weight: 45 },
          { type: 'coins', amount: 40, weight: 30 },
          { type: 'coins', amount: 100, weight: 20 },
          { type: 'points', amount: 5, weight: 5 },
        ],
      }),
      this.boxRepo.create({
        code: 'premium_box',
        title: 'Premium Box',
        kind: 'paid',
        costCoins: 800,
        dailyLimitPerUser: 1,
        isActive: true,
        rewardsJson: safePremiumRewards,
      }),
    ]);
  }
}
