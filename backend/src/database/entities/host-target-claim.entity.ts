import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  Index,
  CreateDateColumn,
  Unique,
} from 'typeorm';

/** One claim per host / calendar month / stage index. */
@Entity('host_target_claims')
@Unique('uq_host_target_claim', ['userId', 'yearMonth', 'stageIndex'])
export class HostTargetClaim {
  @PrimaryGeneratedColumn('uuid')
  id!: string;

  @Index()
  @Column({ type: 'uuid' })
  userId!: string;

  /** YYYY-MM in UTC */
  @Column({ type: 'varchar', length: 7 })
  yearMonth!: string;

  @Column({ type: 'int' })
  stageIndex!: number;

  @Column({ type: 'bigint', default: 0 })
  rewardCoins!: number;

  @Column({ type: 'bigint', default: 0 })
  rewardDiamonds!: number;

  @CreateDateColumn({ type: 'timestamptz' })
  claimedAt!: Date;
}
