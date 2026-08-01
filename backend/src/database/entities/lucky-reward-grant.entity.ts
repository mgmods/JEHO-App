import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  OneToOne,
  JoinColumn,
  CreateDateColumn,
  Index,
} from 'typeorm';
import { LuckyBoxOpen } from './lucky-box-open.entity';

@Entity('lucky_reward_grants')
export class LuckyRewardGrant {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index({ unique: true })
  @Column({ type: 'uuid' })
  openId: string;

  @OneToOne(() => LuckyBoxOpen, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'openId' })
  open: LuckyBoxOpen;

  @Column({ type: 'uuid' })
  userId: string;

  @Column({ type: 'varchar', length: 32 })
  rewardType: string;

  @Column({ type: 'bigint' })
  amount: number;

  @Column({ type: 'uuid', nullable: true })
  walletTxId: string | null;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;
}
