import { Entity, PrimaryGeneratedColumn, Column, CreateDateColumn, Index, ManyToOne, JoinColumn } from 'typeorm';
import { User } from './user.entity';
import { Gift } from './gift.entity';

@Entity('custom_gift_grants')
@Index('idx_custom_gift_grants_owner_expiry', ['userId', 'expiresAt'])
export class CustomGiftGrant {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ type: 'uuid' })
  userId: string;

  @ManyToOne(() => User, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'userId' })
  user: User;

  @Index({ unique: true })
  @Column({ type: 'uuid' })
  giftId: string;

  @ManyToOne(() => Gift, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'giftId' })
  gift: Gift;

  @Column({ type: 'uuid' })
  grantedBy: string;

  @Column({ type: 'varchar', length: 16 })
  mediaType: 'image' | 'video' | 'gif';

  @Column({ type: 'varchar', length: 512 })
  mediaUrl: string;

  @Column({ type: 'int', nullable: true })
  durationDays: number | null;

  @Column({ type: 'timestamptz' })
  startsAt: Date;

  @Column({ type: 'timestamptz', nullable: true })
  expiresAt: Date | null;

  @Column({ type: 'timestamptz', nullable: true })
  revokedAt: Date | null;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;
}
