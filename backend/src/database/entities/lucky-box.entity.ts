import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  UpdateDateColumn,
  Index,
} from 'typeorm';

export type LuckyBoxKind = 'free_daily' | 'paid' | 'host_funded' | 'agency_funded';

export interface LuckyBoxReward {
  type: 'coins' | 'diamonds' | 'points';
  amount: number;
  weight: number;
}

@Entity('lucky_boxes')
export class LuckyBox {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index({ unique: true })
  @Column({ type: 'varchar', length: 64 })
  code: string;

  @Column({ type: 'varchar', length: 128 })
  title: string;

  @Column({ type: 'varchar', length: 32, default: 'free_daily' })
  kind: LuckyBoxKind;

  @Column({ type: 'int', default: 0 })
  costCoins: number;

  @Column({ type: 'int', default: 1 })
  dailyLimitPerUser: number;

  @Column({ type: 'boolean', default: true })
  isActive: boolean;

  @Column({ type: 'jsonb', default: '[]' })
  rewardsJson: LuckyBoxReward[];

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt: Date;
}
