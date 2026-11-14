import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  UpdateDateColumn,
  Index,
} from 'typeorm';

/**
 * Per-room chat moderation state: strike ladder + timed text mute.
 * Separate from room_bans (kick/ban) so hosts can clear mutes from blacklist.
 */
@Entity('room_chat_penalties')
@Index(['roomId', 'userId'], { unique: true })
export class RoomChatPenalty {
  @PrimaryGeneratedColumn('uuid')
  id!: string;

  @Index()
  @Column({ type: 'uuid' })
  roomId!: string;

  @Index()
  @Column({ type: 'uuid' })
  userId!: string;

  @Column({ type: 'int', default: 0 })
  strikeCount!: number;

  /** When set and in the future, user cannot send room chat. */
  @Index()
  @Column({ type: 'timestamptz', nullable: true })
  chatMutedUntil!: Date | null;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt!: Date;

  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt!: Date;
}
