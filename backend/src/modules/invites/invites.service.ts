import {
  BadRequestException,
  Injectable,
  NotFoundException,
  OnModuleInit,
  Logger,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { DataSource, Repository } from 'typeorm';
import { User } from '../../database/entities/user.entity';
import { Wallet } from '../../database/entities/wallet.entity';
import {
  WalletTransaction,
  TransactionType,
  CurrencyType,
} from '../../database/entities/wallet-transaction.entity';
import { AppSetting } from '../../database/entities/app-setting.entity';

@Injectable()
export class InvitesService implements OnModuleInit {
  private readonly log = new Logger(InvitesService.name);

  constructor(
    @InjectRepository(User) private readonly usersRepo: Repository<User>,
    @InjectRepository(Wallet) private readonly walletsRepo: Repository<Wallet>,
    @InjectRepository(WalletTransaction)
    private readonly txRepo: Repository<WalletTransaction>,
    @InjectRepository(AppSetting)
    private readonly settingsRepo: Repository<AppSetting>,
    private readonly dataSource: DataSource,
  ) {}

  async onModuleInit() {
    try {
      const key = 'invite.rewards';
      const row = await this.settingsRepo.findOne({ where: { key } });
      if (!row) {
        await this.settingsRepo.save(
          this.settingsRepo.create({
            key,
            value: JSON.stringify({
              inviterCoins: 100,
              inviteeCoins: 50,
            }),
          }),
        );
        this.log.log('Seeded invite.rewards defaults');
      }
    } catch (err) {
      this.log.warn(
        `invite rewards seed skipped: ${
          err instanceof Error ? err.message : String(err)
        }`,
      );
    }
  }

  private async rewardConfig() {
    const row = await this.settingsRepo.findOne({ where: { key: 'invite.rewards' } });
    let inviterCoins = 100;
    let inviteeCoins = 50;
    let enabled = true;
    try {
      if (row?.value) {
        const parsed = JSON.parse(row.value) as {
          inviterCoins?: number;
          inviteeCoins?: number;
          enabled?: boolean;
        };
        if (parsed.enabled === false) enabled = false;
        inviterCoins = Math.max(0, Math.floor(Number(parsed.inviterCoins) || 0));
        inviteeCoins = Math.max(0, Math.floor(Number(parsed.inviteeCoins) || 0));
      }
    } catch {
      /* defaults */
    }
    if (!enabled) {
      inviterCoins = 0;
      inviteeCoins = 0;
    }
    return { inviterCoins, inviteeCoins, enabled };
  }

  async me(userId: string) {
    const me = await this.usersRepo.findOne({ where: { id: userId } });
    if (!me) throw new NotFoundException('User not found');
    const code = me.publicId || me.username || me.id;
    const invitedCount = await this.usersRepo.count({
      where: { invitedByUserId: userId },
    });
    const rewards = await this.rewardConfig();
    const totalCoins = invitedCount * rewards.inviterCoins;
    let invitedBy: { id: string; displayName: string; publicId: string | null } | null =
      null;
    if (me.invitedByUserId) {
      const inviter = await this.usersRepo.findOne({
        where: { id: me.invitedByUserId },
      });
      if (inviter) {
        invitedBy = {
          id: inviter.id,
          displayName: inviter.displayName || inviter.username,
          publicId: inviter.publicId,
        };
      }
    }
    return {
      code,
      invitedCount,
      totalCoins,
      invitedBy,
      canBind: !me.invitedByUserId,
      rewards,
    };
  }

  async bind(userId: string, rawCode: string) {
    const code = String(rawCode || '').trim();
    if (!code) throw new BadRequestException('أدخل رمز الدعوة');

    const me = await this.usersRepo.findOne({ where: { id: userId } });
    if (!me) throw new NotFoundException('User not found');
    if (me.invitedByUserId) {
      throw new BadRequestException('تم ربط رمز دعوة مسبقاً');
    }

    const inviter = await this.usersRepo.findOne({
      where: [{ publicId: code }, { username: code }, { id: code }],
    });
    if (!inviter) throw new NotFoundException('رمز الدعوة غير صالح');
    if (inviter.id === userId) {
      throw new BadRequestException('لا يمكن استخدام رمزك الشخصي');
    }

    const rewards = await this.rewardConfig();

    await this.dataSource.transaction(async (manager) => {
      me.invitedByUserId = inviter.id;
      me.inviteBoundAt = new Date();
      await manager.save(me);

      if (rewards.inviteeCoins > 0) {
        const inviteeWallet = await manager.findOne(Wallet, {
          where: { userId },
          lock: { mode: 'pessimistic_write' },
        });
        if (inviteeWallet) {
          inviteeWallet.coins = Number(inviteeWallet.coins) + rewards.inviteeCoins;
          await manager.save(inviteeWallet);
          await manager.save(
            manager.create(WalletTransaction, {
              userId,
              type: TransactionType.INVITE_REWARD,
              currency: CurrencyType.COINS,
              amount: rewards.inviteeCoins,
              balanceAfter: Number(inviteeWallet.coins),
              referenceType: 'invite_bind',
              referenceId: inviter.id,
              description: `مكافأة إدخال رمز دعوة`,
            }),
          );
        }
      }

      if (rewards.inviterCoins > 0) {
        const inviterWallet = await manager.findOne(Wallet, {
          where: { userId: inviter.id },
          lock: { mode: 'pessimistic_write' },
        });
        if (inviterWallet) {
          inviterWallet.coins = Number(inviterWallet.coins) + rewards.inviterCoins;
          await manager.save(inviterWallet);
          await manager.save(
            manager.create(WalletTransaction, {
              userId: inviter.id,
              type: TransactionType.INVITE_REWARD,
              currency: CurrencyType.COINS,
              amount: rewards.inviterCoins,
              balanceAfter: Number(inviterWallet.coins),
              referenceType: 'invite_reward',
              referenceId: userId,
              description: `مكافأة دعوة صديق`,
            }),
          );
        }
      }
    });

    return this.me(userId);
  }
}
