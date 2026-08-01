import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  ManyToOne,
  JoinColumn,
  CreateDateColumn,
  UpdateDateColumn,
  Index,
} from 'typeorm';
import { User } from './user.entity';

export enum WithdrawStatus {
  PENDING = 'pending',
  APPROVED = 'approved',
  REJECTED = 'rejected',
  PAID = 'paid',
  CANCELLED = 'cancelled',
}

@Entity('withdraw_requests')
export class WithdrawRequest {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ type: 'uuid' })
  userId: string;

  @ManyToOne(() => User, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'userId' })
  user: User;

  @Column({ type: 'bigint' })
  diamonds: number;

  @Column({ type: 'decimal', precision: 12, scale: 2 })
  amountFiat: number;

  @Column({ type: 'varchar', length: 8, default: 'USD' })
  currency: string;

  @Column({ type: 'varchar', length: 32 })
  method: string;

  /** Recharge-agent who handles cash-out when method === 'agent'. */
  @Index()
  @Column({ type: 'uuid', nullable: true })
  agentId: string | null;

  @Column({ type: 'simple-json' })
  payoutDetails: Record<string, unknown>;

  @Column({ type: 'enum', enum: WithdrawStatus, default: WithdrawStatus.PENDING })
  status: WithdrawStatus;

  @Column({ type: 'uuid', nullable: true })
  reviewedById: string | null;

  @Column({ type: 'text', nullable: true })
  adminNote: string | null;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt: Date;
}
