import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  JoinColumn,
  ManyToOne,
  PrimaryGeneratedColumn,
  Unique,
} from 'typeorm';
import { Room } from './room.entity';
import { User } from './user.entity';

export enum RoomSeatSignalKind {
  REQUEST = 'request',
  INVITE = 'invite',
}

@Entity('room_seat_signals')
@Unique(['roomId', 'userId', 'kind'])
export class RoomSeatSignal {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ type: 'uuid' })
  roomId: string;

  @ManyToOne(() => Room, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'roomId' })
  room: Room;

  @Index()
  @Column({ type: 'uuid' })
  userId: string;

  @ManyToOne(() => User, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'userId' })
  user: User;

  @Index()
  @Column({ type: 'varchar', length: 16 })
  kind: RoomSeatSignalKind;

  @Column({ type: 'int', nullable: true })
  seatIndex: number | null;

  @Column({ type: 'varchar', length: 128, nullable: true })
  displayName: string | null;

  @Column({ type: 'uuid', nullable: true })
  createdById: string | null;

  @Column({ type: 'timestamptz' })
  expiresAt: Date;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;
}
