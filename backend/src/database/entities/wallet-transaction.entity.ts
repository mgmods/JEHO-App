import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  ManyToOne,
  JoinColumn,
  CreateDateColumn,
  Index,
} from 'typeorm';
import { User } from './user.entity';

export enum TransactionType {
  RECHARGE = 'recharge',
  GIFT_SEND = 'gift_send',
  GIFT_RECEIVE = 'gift_receive',
  EXCHANGE = 'exchange',
  WITHDRAW = 'withdraw',
  REFUND = 'refund',
  ADMIN_ADJUST = 'admin_adjust',
  LUCKY_REWARD = 'lucky_reward',
  INVITE_REWARD = 'invite_reward',
  ROOM_ENTRY = 'room_entry',
  DRAMA_WATCH = 'drama_watch',
  GAME_AD_REWARD = 'game_ad_reward',
}

export enum CurrencyType {
  COINS = 'coins',
  DIAMONDS = 'diamonds',
}

@Entity('wallet_transactions')
@Index('uq_wallet_tx_user_reference', ['userId', 'referenceType', 'referenceId'], {
  unique: true,
  where: '"referenceType" IS NOT NULL AND "referenceId" IS NOT NULL',
})
export class WalletTransaction {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ type: 'uuid' })
  userId: string;

  @ManyToOne(() => User, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'userId' })
  user: User;

  @Column({ type: 'enum', enum: TransactionType })
  type: TransactionType;

  @Column({ type: 'enum', enum: CurrencyType })
  currency: CurrencyType;

  @Column({ type: 'bigint' })
  amount: number;

  @Column({ type: 'bigint' })
  balanceAfter: number;

  @Column({ type: 'varchar', length: 255, nullable: true })
  referenceType: string | null;

  @Column({ type: 'varchar', length: 255, nullable: true })
  referenceId: string | null;

  @Column({ type: 'varchar', length: 255, nullable: true })
  description: string | null;

  @Column({ type: 'simple-json', nullable: true })
  metadata: Record<string, unknown> | null;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;
}
