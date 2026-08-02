import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  UpdateDateColumn,
  Index,
} from 'typeorm';

/** Tracks calendar-month recharge USD + claimed promo rewards per user. */
@Entity('user_promo_progress')
@Index(['userId', 'monthKey'], { unique: true })
export class UserPromoProgress {
  @PrimaryGeneratedColumn('uuid')
  id!: string;

  @Column({ type: 'uuid' })
  userId!: string;

  /** UTC YYYY-MM */
  @Column({ type: 'varchar', length: 7 })
  monthKey!: string;

  @Column({ type: 'decimal', precision: 12, scale: 2, default: 0 })
  usdSpent!: number;

  /** Claimed offer ids this month (monthly + supporter). */
  @Column({ type: 'simple-json', default: [] })
  claimedOfferIds!: string[];

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt!: Date;

  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt!: Date;
}
