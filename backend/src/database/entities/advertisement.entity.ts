import { Entity, PrimaryGeneratedColumn, Column, CreateDateColumn, UpdateDateColumn, Index } from 'typeorm';

export enum AdvertisementStatus {
  QUEUED = 'queued',
  PLAYING = 'playing',
  COMPLETED = 'completed',
  REJECTED = 'rejected',
  REMOVED = 'removed',
}

@Entity('room_advertisements')
@Index('idx_room_ads_queue', ['status', 'queueOrder', 'createdAt'])
@Index('idx_room_ads_sender_day', ['senderId', 'createdAt'])
export class Advertisement {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ type: 'uuid' })
  senderId: string;

  @Column({ type: 'varchar', length: 128 })
  senderName: string;

  @Column({ type: 'varchar', length: 512, nullable: true })
  senderImageUrl: string | null;

  @Column({ type: 'varchar', length: 500 })
  text: string;

  @Column({ type: 'enum', enum: AdvertisementStatus, default: AdvertisementStatus.QUEUED })
  status: AdvertisementStatus;

  @Index()
  @Column({ type: 'bigint', default: 0 })
  queueOrder: number;

  @Column({ type: 'timestamptz', nullable: true })
  startedAt: Date | null;

  @Column({ type: 'timestamptz', nullable: true })
  completedAt: Date | null;

  @Column({ type: 'timestamptz', nullable: true })
  nextEligibleAt: Date | null;

  @Column({ type: 'varchar', length: 64, nullable: true })
  rejectionCode: string | null;

  @Column({ type: 'varchar', length: 255, nullable: true })
  rejectionReason: string | null;

  @Column({ type: 'varchar', length: 128, nullable: true })
  idempotencyKey: string | null;

  @Column({ type: 'varchar', length: 64, nullable: true })
  displayTemplateId: string | null;

  @Column({ type: 'int', default: 0 })
  displayDurationMs: number;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt: Date;
}