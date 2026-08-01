import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  ManyToOne,
  JoinColumn,
  CreateDateColumn,
  UpdateDateColumn,
  Unique,
  Index,
} from 'typeorm';
import { Room } from './room.entity';
import { User } from './user.entity';

export enum SeatStatus {
  EMPTY = 'empty',
  OCCUPIED = 'occupied',
  LOCKED = 'locked',
  MUTED = 'muted',
}

@Entity('room_seats')
@Unique(['roomId', 'seatIndex'])
@Index(['roomId', 'userId'], {
  unique: true,
  where: '"userId" IS NOT NULL',
})
export class RoomSeat {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ type: 'uuid' })
  roomId: string;

  @ManyToOne(() => Room, (r) => r.seats, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'roomId' })
  room: Room;

  @Column({ type: 'int' })
  seatIndex: number;

  @Column({ type: 'uuid', nullable: true })
  userId: string | null;

  @ManyToOne(() => User, { onDelete: 'SET NULL', nullable: true })
  @JoinColumn({ name: 'userId' })
  user: User | null;

  @Column({ type: 'enum', enum: SeatStatus, default: SeatStatus.EMPTY })
  status: SeatStatus;

  @Column({ type: 'boolean', default: false })
  isMuted: boolean;

  @Column({ type: 'boolean', default: false })
  isModeratorMuted: boolean;

  @Column({ type: 'boolean', default: false })
  isHostSeat: boolean;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt: Date;
}
