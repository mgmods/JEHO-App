import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  ManyToOne,
  JoinColumn,
  CreateDateColumn,
  Unique,
  Index,
} from 'typeorm';
import { Room } from './room.entity';
import { User } from './user.entity';

export enum ModeratorRole {
  MOD = 'mod',
  ADMIN = 'admin',
}

@Entity('room_moderators')
@Unique(['roomId', 'userId'])
export class RoomModerator {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ type: 'uuid' })
  roomId: string;

  @ManyToOne(() => Room, (r) => r.moderators, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'roomId' })
  room: Room;

  @Index()
  @Column({ type: 'uuid' })
  userId: string;

  @ManyToOne(() => User, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'userId' })
  user: User;

  @Column({ type: 'enum', enum: ModeratorRole, default: ModeratorRole.MOD })
  role: ModeratorRole;

  @Column({ type: 'boolean', default: true })
  canManageMusic: boolean;

  @Column({ type: 'boolean', default: true })
  canChangeFrames: boolean;

  @Column({ type: 'boolean', default: true })
  canControlGames: boolean;

  @Column({ type: 'boolean', default: true })
  canMute: boolean;

  @Column({ type: 'boolean', default: true })
  canKick: boolean;

  @Column({ type: 'boolean', default: true })
  canBan: boolean;

  @Column({ type: 'boolean', default: true })
  canManageSeats: boolean;

  @Column({ type: 'boolean', default: true })
  canInvite: boolean;

  @Column({ type: 'boolean', default: true })
  canManageRoom: boolean;

  @Column({ type: 'uuid' })
  appointedById: string;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;
}
