import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  ManyToOne,
  JoinColumn,
  CreateDateColumn,
  UpdateDateColumn,
  Index,
  Unique,
} from 'typeorm';
import { User } from './user.entity';

export enum SocialRequestType {
  FRIEND = 'friend',
  FOLLOW = 'follow',
  RELATION = 'relation',
  GUARDIAN = 'guardian',
}

export enum SocialRequestStatus {
  PENDING = 'pending',
  ACCEPTED = 'accepted',
  REJECTED = 'rejected',
}

@Entity('social_requests')
@Unique(['fromUserId', 'toUserId', 'type'])
export class SocialRequest {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ type: 'uuid' })
  fromUserId: string;

  @Index()
  @Column({ type: 'uuid' })
  toUserId: string;

  @ManyToOne(() => User, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'fromUserId' })
  fromUser: User;

  @ManyToOne(() => User, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'toUserId' })
  toUser: User;

  @Column({ type: 'varchar', length: 32, default: SocialRequestType.FRIEND })
  type: SocialRequestType;

  @Column({ type: 'varchar', length: 16, default: SocialRequestStatus.PENDING })
  status: SocialRequestStatus;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt: Date;
}
