import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  OneToOne,
  JoinColumn,
  CreateDateColumn,
  UpdateDateColumn,
} from 'typeorm';
import { User } from './user.entity';

@Entity('user_profiles')
export class UserProfile {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ type: 'uuid', unique: true })
  userId: string;

  @OneToOne(() => User, (u) => u.profile, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'userId' })
  user: User;

  @Column({ type: 'text', nullable: true })
  bio: string | null;

  @Column({ type: 'varchar', length: 128, nullable: true })
  country: string | null;

  /** Mikoo-style: country may change once every 30 days. */
  @Column({ type: 'timestamptz', nullable: true })
  countryChangedAt: Date | null;

  @Column({ type: 'varchar', length: 128, nullable: true })
  city: string | null;

  @Column({ type: 'varchar', length: 16, nullable: true })
  language: string | null;

  @Column({ type: 'simple-array', nullable: true })
  interests: string[] | null;

  /** Album photo URLs (comma-separated via simple-array) */
  @Column({ type: 'simple-array', nullable: true })
  albumUrls: string[] | null;

  @Column({ type: 'varchar', length: 512, nullable: true })
  coverUrl: string | null;

  @Column({ type: 'varchar', length: 512, nullable: true })
  entryEffectUrl: string | null;

  @Column({ type: 'varchar', length: 512, nullable: true })
  entryAnimationUrl: string | null;

  @Column({ type: 'varchar', length: 512, nullable: true })
  roomCardUrl: string | null;

  @Column({ type: 'varchar', length: 512, nullable: true })
  vipBadgeUrl: string | null;

  @Column({ type: 'varchar', length: 512, nullable: true })
  levelBadgeUrl: string | null;

  @Column({ type: 'varchar', length: 512, nullable: true })
  hostBadgeUrl: string | null;

  @Column({ type: 'varchar', length: 64, nullable: true })
  nameColor: string | null;

  @Column({ type: 'int', default: 0 })
  followersCount: number;

  @Column({ type: 'int', default: 0 })
  followingCount: number;

  @Column({ type: 'int', default: 0 })
  friendsCount: number;

  @Column({ type: 'bigint', default: 0 })
  totalReceivedDiamonds: number;

  @Column({ type: 'bigint', default: 0 })
  totalSentCoins: number;

  @Column({ type: 'boolean', default: true })
  showOnlineStatus: boolean;

  @Column({ type: 'boolean', default: true })
  allowDmFromStrangers: boolean;

  /** When true (and global setting on), strangers must gift before first DM. */
  @Column({ type: 'boolean', default: false })
  dmGiftGateEnabled: boolean;

  /** Optional catalog gift id required to open a DM with this user. */
  @Column({ type: 'uuid', nullable: true })
  dmRequiredGiftId: string | null;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt: Date;
}
