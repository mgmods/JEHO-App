import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  ManyToOne,
  OneToMany,
  JoinColumn,
  CreateDateColumn,
  UpdateDateColumn,
  Index,
} from 'typeorm';
import { User } from './user.entity';
import { AgencyMember } from './agency-member.entity';

export enum AgencyStatus {
  PENDING = 'pending',
  ACTIVE = 'active',
  SUSPENDED = 'suspended',
  REJECTED = 'rejected',
}

@Entity('agencies')
export class Agency {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index({ unique: true })
  @Column({ type: 'varchar', length: 128 })
  name: string;

  @Column({ type: 'text', nullable: true })
  description: string | null;

  @Column({ type: 'varchar', length: 512, nullable: true })
  logoUrl: string | null;

  @Index()
  @Column({ type: 'uuid' })
  ownerId: string;

  @ManyToOne(() => User, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'ownerId' })
  owner: User;

  @Column({ type: 'enum', enum: AgencyStatus, default: AgencyStatus.PENDING })
  status: AgencyStatus;

  @Column({ type: 'int', default: 0 })
  memberCount: number;

  @Column({ type: 'bigint', default: 0 })
  totalDiamonds: number;

  @Column({ type: 'decimal', precision: 5, scale: 2, default: 20 })
  commissionPercent: number;

  /** Unique invite/activation code — hosts join with this code only. */
  @Index({ unique: true })
  @Column({ type: 'varchar', length: 16, nullable: true })
  activationCode: string | null;

  /**
   * Short public discovery ID (admin-assigned only). Shareable / searchable.
   * Distinct from activationCode (join secret) and internal uuid.
   */
  @Index({ unique: true })
  @Column({ type: 'varchar', length: 16, nullable: true })
  publicId: string | null;

  /**
   * Official verification badge — admin only. Shown when agency is ACTIVE.
   * Revoked by admin on policy breach (isVerified = false) and hidden when suspended.
   */
  @Column({ type: 'boolean', default: false })
  isVerified: boolean;

  @Column({ type: 'timestamptz', nullable: true })
  verifiedAt: Date | null;

  /**
   * Admin-assigned exclusive vip_badge cosmetic code for this agency’s hosts/owner.
   * Mall cannot sell agency-exclusive frames; only admin grants.
   */
  @Column({ type: 'varchar', length: 64, nullable: true })
  exclusiveFrameCode: string | null;

  /**
   * Admin-assigned exclusive room_card cosmetic code for agency live rooms / directory frame.
   */
  @Column({ type: 'varchar', length: 64, nullable: true })
  exclusiveRoomCardCode: string | null;

  /** Optional static exclusive frame preview URL for directory cards when offline. */
  @Column({ type: 'varchar', length: 512, nullable: true })
  exclusiveFrameUrl: string | null;

  /** Preset used when notifying a newly approved host. */
  @Column({ type: 'varchar', length: 32, default: 'welcome' })
  notificationStyle: string;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt: Date;

  @OneToMany(() => AgencyMember, (m) => m.agency)
  members: AgencyMember[];
}
