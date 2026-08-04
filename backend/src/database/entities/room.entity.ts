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
import { Agency } from './agency.entity';
import { RoomSeat } from './room-seat.entity';
import { RoomBan } from './room-ban.entity';
import { RoomModerator } from './room-moderator.entity';

export enum RoomType {
  VOICE = 'voice',
  PARTY = 'party',
}

export enum RoomStatus {
  OPEN = 'open',
  LOCKED = 'locked',
  CLOSED = 'closed',
}

export enum RoomAccessMode {
  FREE = 'free',
  PAID = 'paid',
  PERMANENT = 'permanent',
}

export enum RoomKind {
  STANDARD = 'standard',
  AGENCY = 'agency',
  /** Official customer-service / support room (admin-elevated). */
  SUPPORT = 'support',
}

@Entity('rooms')
@Index('uq_room_agency_host', ['agencyId', 'hostId'], {
  unique: true,
  where: '"agencyId" IS NOT NULL',
})
export class Room {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ type: 'varchar', length: 128 })
  title: string;

  @Column({ type: 'text', nullable: true })
  description: string | null;

  @Column({ type: 'varchar', length: 512, nullable: true })
  coverUrl: string | null;

  @Column({ type: 'varchar', length: 512, nullable: true })
  backgroundUrl: string | null;

  @Column({ type: 'uuid', nullable: true })
  backgroundEquippedById: string | null;

  @Column({ type: 'varchar', length: 512, nullable: true })
  roomCardUrl: string | null;

  @Column({ type: 'uuid', nullable: true })
  roomCardEquippedById: string | null;

  @Column({ type: 'varchar', length: 1024, nullable: true })
  musicUrl: string | null;

  @Column({ type: 'varchar', length: 160, nullable: true })
  musicTitle: string | null;

  @Column({ type: 'varchar', length: 160, nullable: true })
  musicArtist: string | null;

  @Column({ type: 'varchar', length: 16, default: 'stopped' })
  musicStatus: 'playing' | 'paused' | 'stopped';

  @Column({ type: 'bigint', default: 0 })
  musicPositionMs: number;

  @Column({ type: 'timestamptz', nullable: true })
  musicStartedAt: Date | null;

  @Column({ type: 'enum', enum: RoomType, default: RoomType.VOICE })
  type: RoomType;

  @Column({ type: 'enum', enum: RoomStatus, default: RoomStatus.OPEN })
  status: RoomStatus;

  @Index()
  @Column({ type: 'uuid' })
  hostId: string;

  @ManyToOne(() => User, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'hostId' })
  host: User;

  @Column({ type: 'uuid', nullable: true })
  cohostId: string | null;

  @Column({ type: 'varchar', length: 128, nullable: true, select: false })
  passwordHash: string | null;

  @Column({ type: 'boolean', default: false })
  hasPassword: boolean;

  @Column({ type: 'int', default: 11 })
  seatCount: number;

  @Column({ type: 'int', default: 0 })
  viewerCount: number;

  @Column({ type: 'varchar', length: 64, nullable: true })
  zegoRoomId: string | null;

  @Column({ type: 'varchar', length: 64, nullable: true })
  topic: string | null;

  @Column({ type: 'simple-array', nullable: true })
  tags: string[] | null;

  @Column({ type: 'boolean', default: true })
  isPublic: boolean;

  @Index()
  @Column({ type: 'uuid', nullable: true })
  agencyId: string | null;

  @ManyToOne(() => Agency, { nullable: true, onDelete: 'SET NULL' })
  @JoinColumn({ name: 'agencyId' })
  agency: Agency | null;

  @Column({ type: 'varchar', length: 16, default: RoomKind.STANDARD })
  roomKind: RoomKind;

  @Column({ type: 'boolean', default: false })
  isPersistent: boolean;

  @Column({ type: 'uuid', nullable: true })
  activeHostId: string | null;

  /** When the current live session started; room top supporters count gifts after this. */
  @Column({ type: 'timestamptz', nullable: true })
  liveSessionStartedAt: Date | null;

  /**
   * Personal rooms only: first moment the live room had zero presence
   * (no seated users, no live sessions, no sockets). Cleared when someone returns.
   * After ~30 minutes the idle sweeper ends the stream permanently.
   */
  @Column({ type: 'timestamptz', nullable: true })
  emptySince: Date | null;

  /** free | paid (per session) | permanent (one-time unlock) */
  @Column({ type: 'varchar', length: 16, default: RoomAccessMode.FREE })
  accessMode: RoomAccessMode;

  @Column({ type: 'int', default: 0 })
  entryFeeCoins: number;

  /** When false, gift SFX/WAV are muted for everyone in this live room. */
  @Column({ type: 'boolean', default: true })
  giftSoundsEnabled: boolean;

  /** Mikoo room-more: public chat / chat zone visible for everyone. */
  @Column({ type: 'boolean', default: true })
  chatZoneEnabled: boolean;

  /** Mikoo room-more: charm chips visible in room chat. */
  @Column({ type: 'boolean', default: true })
  charmEnabled: boolean;

  /** Mikoo room-more: gift / lucky hit banner strip visible. */
  @Column({ type: 'boolean', default: true })
  bannerEnabled: boolean;

  /** Mikoo room-more: seat emoji reactions allowed. */
  @Column({ type: 'boolean', default: true })
  micInteractEnabled: boolean;

  /** Mikoo room-more (effect): entry / VIP ride effects for others. */
  @Column({ type: 'boolean', default: true })
  entryEffectsEnabled: boolean;

  /** Mikoo room-more (effect): play low-value gift animations. */
  @Column({ type: 'boolean', default: true })
  lowGiftEffectsEnabled: boolean;

  /** Season key when this room holds a Room Cup winner badge. */
  @Column({ type: 'varchar', length: 32, nullable: true })
  cupBadgeSeason: string | null;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt: Date;

  @OneToMany(() => RoomSeat, (s) => s.room)
  seats: RoomSeat[];

  @OneToMany(() => RoomBan, (b) => b.room)
  bans: RoomBan[];

  @OneToMany(() => RoomModerator, (m) => m.room)
  moderators: RoomModerator[];
}
