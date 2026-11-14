import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  UpdateDateColumn,
  Index,
  Unique,
} from 'typeorm';

@Entity('host_monthly_progress')
@Unique(['userId', 'yearMonth'])
export class HostMonthlyProgress {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ type: 'uuid' })
  userId: string;

  /** Period key: monthly `YYYY-MM` or weekly `YYYYWww` (always ≤7 chars for legacy column). */
  @Index()
  @Column({ type: 'varchar', length: 7 })
  yearMonth: string;

  @Column({ type: 'bigint', default: 0 })
  progress: number;

  /** JSON array of claimed stage ids (current cycle only). */
  @Column({ type: 'simple-json', default: [] })
  claimedStageIds: string[];

  /**
   * Stages whose host-target *salary package* was already submitted for payout
   * this cycle (separate from reward claims). Only ONE current stage is shown
   * for withdraw; after salary withdraw, host advances to the next stage.
   */
  @Column({ type: 'simple-json', default: [], nullable: true })
  withdrawnStageIds: string[];

  /** How many full stage sets the host already finished this month. */
  @Column({ type: 'int', default: 0 })
  cyclesCompleted: number;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt: Date;
}
