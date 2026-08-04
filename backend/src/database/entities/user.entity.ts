import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  UpdateDateColumn,
  OneToOne,
  OneToMany,
  Index,
} from 'typeorm';
import { UserProfile } from './user-profile.entity';
import { Device } from './device.entity';
import { Follow } from './follow.entity';
import { Wallet } from './wallet.entity';
import { UserVip } from './user-vip.entity';
import { AgencyMember } from './agency-member.entity';

export enum UserStatus {
  ACTIVE = 'active',
  BANNED = 'banned',
  DELETED = 'deleted',
  SUSPENDED = 'suspended',
}

export enum Gender {
  MALE = 'male',
  FEMALE = 'female',
  OTHER = 'other',
  UNSPECIFIED = 'unspecified',
}

/** Platform staff role (in-app moderation + dashboard super). */
export enum StaffRole {
  NONE = 'none',
  MANAGER = 'manager',
  SUPER = 'super',
}

@Entity('users')
export class User {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index({ unique: true })
  @Column({ type: 'varchar', length: 64, nullable: true })
  email: string | null;

  @Index({ unique: true })
  @Column({ type: 'varchar', length: 32, nullable: true })
  phone: string | null;

  @Index({ unique: true })
  @Column({ type: 'varchar', length: 48 })
  username: string;

  /** Stable numeric public ID shown in-app (like major apps). Never the store/login name. */
  @Index({ unique: true })
  @Column({ type: 'varchar', length: 16, nullable: true })
  publicId: string | null;

  @Column({ type: 'varchar', length: 255, nullable: true, select: false })
  passwordHash: string | null;

  @Column({ type: 'varchar', length: 128, default: '' })
  displayName: string;

  @Column({ type: 'varchar', length: 512, nullable: true })
  avatarUrl: string | null;

  @Column({ type: 'enum', enum: Gender, default: Gender.UNSPECIFIED })
  gender: Gender;

  @Column({ type: 'date', nullable: true })
  birthday: Date | null;

  @Column({ type: 'enum', enum: UserStatus, default: UserStatus.ACTIVE })
  status: UserStatus;

  @Column({ type: 'boolean', default: false })
  isGuest: boolean;

  @Column({ type: 'boolean', default: false })
  isAdmin: boolean;

  /**
   * Platform staff: manager (room/people moderation in-app),
   * super (full room powers + dashboard admin flag).
   * Stored as varchar so prod (synchronize=false) can ALTER IF NOT EXISTS easily.
   */
  @Column({ type: 'varchar', length: 16, nullable: true, default: null })
  staffRole: string | null;

  @Column({ type: 'boolean', default: false })
  emailVerified: boolean;

  @Column({ type: 'boolean', default: false })
  phoneVerified: boolean;

  /** Admin-approved female identity verification (selfie review). */
  @Column({ type: 'boolean', default: false })
  genderVerified: boolean;

  @Column({ type: 'varchar', length: 64, nullable: true })
  googleId: string | null;

  @Column({ type: 'varchar', length: 64, nullable: true })
  facebookId: string | null;

  @Column({ type: 'varchar', length: 64, nullable: true })
  appleId: string | null;

  @Column({ type: 'int', default: 1 })
  level: number;

  @Column({ type: 'bigint', default: 0 })
  experience: number;

  @Column({ type: 'varchar', length: 512, nullable: true, select: false })
  refreshTokenHash: string | null;

  @Column({ type: 'int', default: 0 })
  nsfwStrikeCount: number;

  @Column({ type: 'timestamptz', nullable: true })
  lastOnlineAt: Date | null;

  /** Inviter who bound this account via promo/invite code (once). */
  @Index()
  @Column({ type: 'uuid', nullable: true })
  invitedByUserId: string | null;

  @Column({ type: 'timestamptz', nullable: true })
  inviteBoundAt: Date | null;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt: Date;

  @OneToOne(() => UserProfile, (p) => p.user)
  profile: UserProfile;

  @OneToMany(() => Device, (d) => d.user)
  devices: Device[];

  @OneToMany(() => Follow, (f) => f.follower)
  following: Follow[];

  @OneToMany(() => Follow, (f) => f.following)
  followers: Follow[];

  @OneToOne(() => Wallet, (w) => w.user)
  wallet: Wallet;

  @OneToMany(() => UserVip, (v) => v.user)
  vips: UserVip[];

  @OneToMany(() => AgencyMember, (m) => m.user)
  agencyMemberships: AgencyMember[];
}
