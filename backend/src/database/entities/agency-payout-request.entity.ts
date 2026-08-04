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
import { Agency } from './agency.entity';

export enum AgencyPayoutStatus {
  PENDING = 'pending',
  PAID = 'paid',
  REJECTED = 'rejected',
  CANCELLED = 'cancelled',
}

/**
 * Host requests cash settlement from their agency (not platform withdraw).
 * Diamonds are held from host wallet on create; refunded on reject/cancel; kept on paid.
 * Agency owner pays USD off-platform using payoutDetails, then marks paid.
 */
@Entity('agency_payout_requests')
export class AgencyPayoutRequest {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ type: 'uuid' })
  agencyId: string;

  @ManyToOne(() => Agency, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'agencyId' })
  agency: Agency;

  @Index()
  @Column({ type: 'uuid' })
  hostUserId: string;

  @ManyToOne(() => User, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'hostUserId' })
  host: User;

  @Column({ type: 'bigint' })
  diamonds: number;

  @Column({ type: 'decimal', precision: 12, scale: 4 })
  amountUsd: number;

  @Column({ type: 'double precision', default: 0.00005 })
  diamondUsdRate: number;

  /** paypal | bank | usdt | cash | other */
  @Column({ type: 'varchar', length: 32 })
  method: string;

  @Column({ type: 'simple-json' })
  payoutDetails: Record<string, unknown>;

  @Column({
    type: 'enum',
    enum: AgencyPayoutStatus,
    default: AgencyPayoutStatus.PENDING,
  })
  status: AgencyPayoutStatus;

  @Column({ type: 'uuid', nullable: true })
  reviewedById: string | null;

  @Column({ type: 'text', nullable: true })
  reviewNote: string | null;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt: Date;
}
