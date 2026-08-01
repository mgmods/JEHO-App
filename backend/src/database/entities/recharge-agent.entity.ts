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

export enum RechargeAgentStatus {
  PENDING = 'pending',
  ACTIVE = 'active',
  SUSPENDED = 'suspended',
  REJECTED = 'rejected',
}

export enum RechargeAgentSource {
  ADMIN = 'admin',
  APPLICATION = 'application',
}

@Entity('recharge_agents')
@Unique('UQ_recharge_agent_user', ['userId'])
export class RechargeAgent {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ type: 'uuid' })
  userId: string;

  @ManyToOne(() => User, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'userId' })
  user: User;

  @Column({ type: 'enum', enum: RechargeAgentStatus, default: RechargeAgentStatus.PENDING })
  status: RechargeAgentStatus;

  @Column({ type: 'enum', enum: RechargeAgentSource, default: RechargeAgentSource.ADMIN })
  source: RechargeAgentSource;

  /** Operational float the agent can sell to users. */
  @Column({ type: 'bigint', default: 0 })
  floatCoins: number;

  @Column({ type: 'int', default: 0 })
  commissionBps: number;

  @Column({ type: 'int', default: 500000 })
  dailyLimitCoins: number;

  @Column({ type: 'int', default: 0 })
  dailySoldCoins: number;

  @Column({ type: 'varchar', length: 16, nullable: true })
  dailySoldKey: string | null;

  @Column({ type: 'text', nullable: true })
  notes: string | null;

  /** Public directory: country / region label shown to users. */
  @Column({ type: 'varchar', length: 64, nullable: true })
  country: string | null;

  @Column({ type: 'varchar', length: 64, nullable: true })
  whatsapp: string | null;

  @Column({ type: 'varchar', length: 64, nullable: true })
  telegram: string | null;

  /** When true + active, appears in in-app “contact recharge agent” list. */
  @Column({ type: 'boolean', default: false })
  listedInDirectory: boolean;

  @Column({ type: 'uuid', nullable: true })
  reviewedBy: string | null;

  @Column({ type: 'timestamptz', nullable: true })
  reviewedAt: Date | null;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt: Date;
}

/** Standalone public contact cards (WhatsApp / Telegram) managed from admin. */
@Entity('recharge_agent_contacts')
export class RechargeAgentContact {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ type: 'varchar', length: 80 })
  displayName: string;

  @Index()
  @Column({ type: 'varchar', length: 64 })
  country: string;

  @Column({ type: 'varchar', length: 64, nullable: true })
  whatsapp: string | null;

  @Column({ type: 'varchar', length: 64, nullable: true })
  telegram: string | null;

  @Column({ type: 'text', nullable: true })
  notes: string | null;

  @Column({ type: 'boolean', default: true })
  isActive: boolean;

  @Column({ type: 'int', default: 0 })
  sortOrder: number;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt: Date;
}

@Entity('recharge_agent_applications')
export class RechargeAgentApplication {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ type: 'uuid' })
  userId: string;

  @ManyToOne(() => User, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'userId' })
  user: User;

  @Column({ type: 'varchar', length: 128, nullable: true })
  contact: string | null;

  @Column({ type: 'varchar', length: 64, nullable: true })
  region: string | null;

  @Column({ type: 'text', nullable: true })
  reason: string | null;

  /** Initial stock requested by the applicant after paying in USDT. */
  @Column({ type: 'bigint', default: 0 })
  requestedCoins: number;

  @Column({ type: 'decimal', precision: 18, scale: 8, default: 0 })
  membershipFeeUsdt: number;

  @Column({ type: 'decimal', precision: 18, scale: 8, default: 0 })
  stockCostUsdt: number;

  @Column({ type: 'decimal', precision: 18, scale: 8, default: 0 })
  totalPaidUsdt: number;

  @Column({ type: 'varchar', length: 16, nullable: true })
  paymentNetwork: string | null;

  @Index({ unique: true })
  @Column({ type: 'varchar', length: 160, nullable: true })
  paymentReference: string | null;

  @Column({ type: 'enum', enum: RechargeAgentStatus, default: RechargeAgentStatus.PENDING })
  status: RechargeAgentStatus;

  @Column({ type: 'text', nullable: true })
  reviewNote: string | null;

  @Column({ type: 'uuid', nullable: true })
  reviewedBy: string | null;

  @Column({ type: 'timestamptz', nullable: true })
  reviewedAt: Date | null;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt: Date;
}

export enum AgentLedgerType {
  FLOAT_CREDIT = 'float_credit',
  FLOAT_DEBIT = 'float_debit',
  SALE = 'sale',
  COMMISSION = 'commission',
  ADJUSTMENT = 'adjustment',
}

@Entity('recharge_agent_ledger')
export class RechargeAgentLedger {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ type: 'uuid' })
  agentId: string;

  @ManyToOne(() => RechargeAgent, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'agentId' })
  agent: RechargeAgent;

  @Column({ type: 'enum', enum: AgentLedgerType })
  type: AgentLedgerType;

  @Column({ type: 'bigint' })
  amount: number;

  @Column({ type: 'bigint' })
  balanceAfter: number;

  @Column({ type: 'uuid', nullable: true })
  referenceId: string | null;

  @Column({ type: 'varchar', length: 64, nullable: true })
  referenceType: string | null;

  @Column({ type: 'text', nullable: true })
  description: string | null;

  @Column({ type: 'uuid', nullable: true })
  actorUserId: string | null;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;
}

@Entity('agent_recharges')
@Unique('UQ_agent_recharge_idempotency', ['idempotencyKey'])
export class AgentRecharge {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ type: 'uuid' })
  agentId: string;

  @ManyToOne(() => RechargeAgent, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'agentId' })
  agent: RechargeAgent;

  @Index()
  @Column({ type: 'uuid' })
  recipientUserId: string;

  @ManyToOne(() => User, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'recipientUserId' })
  recipient: User;

  @Column({ type: 'varchar', length: 64, nullable: true })
  sku: string | null;

  @Column({ type: 'int' })
  coins: number;

  @Column({ type: 'int' })
  chargedFloat: number;

  @Column({ type: 'int', default: 0 })
  commissionCoins: number;

  @Column({ type: 'varchar', length: 16, default: 'completed' })
  status: string;

  @Column({ type: 'varchar', length: 96 })
  idempotencyKey: string;

  @Column({ type: 'uuid', nullable: true })
  walletTxId: string | null;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;
}
