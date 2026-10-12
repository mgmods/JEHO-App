import { Entity, PrimaryGeneratedColumn, Column, CreateDateColumn, UpdateDateColumn, Index } from 'typeorm';
import { CpType } from './cp-relationship.entity';

export enum CpRequestStatus {
  PENDING = 'pending',
  ACCEPTED = 'accepted',
  REJECTED = 'rejected',
  EXPIRED = 'expired',
  CANCELLED = 'cancelled',
}

@Entity('cp_requests')
@Index('idx_cp_request_recipient_status_expiry', ['toUserId', 'status', 'expiresAt'])
@Index('idx_cp_request_sender_status', ['fromUserId', 'status'])
@Index('uq_cp_request_idempotency_key', ['idempotencyKey'], { unique: true })
export class CpRequest {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ type: 'uuid' })
  fromUserId: string;

  @Column({ type: 'uuid' })
  toUserId: string;

  @Column({ type: 'varchar', length: 24 })
  type: CpType;

  @Column({ type: 'varchar', length: 16, default: CpRequestStatus.PENDING })
  status: CpRequestStatus;

  @Column({ type: 'uuid' })
  roomId: string;

  @Column({ type: 'bigint' })
  priceCoins: string;

  @Column({ type: 'varchar', length: 128 })
  idempotencyKey: string;

  @Column({ type: 'timestamptz' })
  expiresAt: Date;

  @Column({ type: 'timestamptz', nullable: true })
  respondedAt: Date | null;

  @Column({ type: 'uuid', nullable: true })
  relationshipId: string | null;

  @Column({ type: 'timestamptz', nullable: true })
  refundedAt: Date | null;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt: Date;
}
