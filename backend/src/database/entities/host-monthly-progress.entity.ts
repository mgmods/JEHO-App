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

  /** YYYY-MM */
  @Index()
  @Column({ type: 'varchar', length: 7 })
  yearMonth: string;

  @Column({ type: 'bigint', default: 0 })
  progress: number;

  /** JSON array of claimed stage ids (current cycle only). */
  @Column({ type: 'simple-json', default: [] })
  claimedStageIds: string[];

  /** How many full stage sets the host already finished this month. */
  @Column({ type: 'int', default: 0 })
  cyclesCompleted: number;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt: Date;
}
