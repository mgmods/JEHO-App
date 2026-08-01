import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  ManyToOne,
  JoinColumn,
  CreateDateColumn,
  UpdateDateColumn,
  Index,
  Unique,
} from 'typeorm';
import { User } from './user.entity';

export enum RechargeStatus {
  PENDING = 'pending',
  COMPLETED = 'completed',
  FAILED = 'failed',
  CANCELLED = 'cancelled',
  REFUNDED = 'refunded',
}

export enum PaymentProvider {
  STRIPE = 'stripe',
  PAYPAL = 'paypal',
  GOOGLE_PLAY = 'google_play',
  APPLE = 'apple',
  CRYPTO = 'crypto',
  ADMIN = 'admin',
  BINANCE_PAY = 'binance_pay',
  BINANCE_WALLET = 'binance_wallet',
  RECHARGE_AGENT = 'recharge_agent',
  FOURTHWALL = 'fourthwall',
  SHAM_CASH = 'sham_cash',
}

@Entity('recharge_orders')
@Unique('UQ_recharge_provider_payment', ['provider', 'providerPaymentId'])
@Unique('UQ_recharge_provider_order', ['provider', 'providerOrderId'])
export class RechargeOrder {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ type: 'uuid' })
  userId: string;

  @ManyToOne(() => User, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'userId' })
  user: User;

  @Column({ type: 'varchar', length: 64, nullable: true })
  sku: string | null;

  @Column({ type: 'int' })
  coins: number;

  @Column({ type: 'int', default: 0 })
  bonusCoins: number;

  @Column({ type: 'decimal', precision: 30, scale: 8 })
  amountFiat: number;

  @Column({ type: 'varchar', length: 16, default: 'USD' })
  currency: string;

  @Column({ type: 'enum', enum: PaymentProvider })
  provider: PaymentProvider;

  @Column({ type: 'enum', enum: RechargeStatus, default: RechargeStatus.PENDING })
  status: RechargeStatus;

  @Column({ type: 'varchar', length: 255, nullable: true })
  providerOrderId: string | null;

  @Column({ type: 'varchar', length: 255, nullable: true })
  providerPaymentId: string | null;

  @Column({ type: 'simple-json', nullable: true })
  providerPayload: Record<string, unknown> | null;

  @Column({ type: 'timestamptz', nullable: true })
  expiresAt: Date | null;

  @Column({ type: 'timestamptz', nullable: true })
  completedAt: Date | null;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt: Date;
}
