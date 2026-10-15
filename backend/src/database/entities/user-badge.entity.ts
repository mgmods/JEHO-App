import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  ManyToOne,
  JoinColumn,
  Index,
} from 'typeorm';
import { Badge } from './badge.entity';
import { User } from './user.entity';

@Entity('user_badges')
@Index('UQ_user_badges_user_badge', ['userId', 'badgeId'], { unique: true })
@Index('IDX_user_badges_user', ['userId'])
export class UserBadge {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ type: 'uuid' })
  userId: string;

  @ManyToOne(() => User, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'userId' })
  user: User;

  @Column({ type: 'uuid' })
  badgeId: string;

  @ManyToOne(() => Badge, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'badgeId' })
  badge: Badge;

  @Column({ type: 'uuid', nullable: true })
  grantedByAdminId: string | null;

  @Column({ type: 'varchar', length: 32, default: 'manual' })
  source: string;

  @CreateDateColumn({ type: 'timestamptz' })
  grantedAt: Date;
}
