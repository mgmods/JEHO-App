import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  ManyToOne,
  JoinColumn,
  CreateDateColumn,
  Index,
  Unique,
} from 'typeorm';
import { User } from './user.entity';

@Entity('profile_visits')
@Unique(['visitorId', 'profileUserId'])
export class ProfileVisit {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ type: 'uuid' })
  visitorId: string;

  @Index()
  @Column({ type: 'uuid' })
  profileUserId: string;

  @ManyToOne(() => User, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'visitorId' })
  visitor: User;

  @ManyToOne(() => User, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'profileUserId' })
  profileUser: User;

  @Column({ type: 'int', default: 1 })
  visitCount: number;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;

  @Column({ type: 'timestamptz', default: () => 'NOW()' })
  lastVisitedAt: Date;
}
