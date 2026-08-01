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
import { User } from './user.entity';

/** Follow a host's voice rooms (persistent; survives room recreate). */
@Entity('room_host_follows')
@Unique(['followerId', 'hostId'])
export class RoomHostFollow {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ type: 'uuid' })
  followerId: string;

  @Index()
  @Column({ type: 'uuid' })
  hostId: string;

  @ManyToOne(() => User, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'followerId' })
  follower: User;

  @ManyToOne(() => User, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'hostId' })
  host: User;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;
}
