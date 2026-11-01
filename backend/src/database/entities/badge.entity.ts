import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  UpdateDateColumn,
  Index,
} from 'typeorm';

/** Admin-managed badge catalogue. Additive-only; independent from VIP and cosmetics. */
@Entity('badges')
@Index('IDX_badges_active_sort', ['isActive', 'sortOrder'])
export class Badge {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ type: 'varchar', length: 80 })
  name: string;

  @Column({ type: 'varchar', length: 500, default: '' })
  description: string;

  @Column({ type: 'varchar', length: 512 })
  imageUrl: string;

  @Column({ type: 'varchar', length: 32, default: 'general' })
  category: string;

  @Column({ type: 'varchar', length: 32, default: 'manual' })
  awardType: string;

  @Column({ type: 'varchar', length: 64, nullable: true })
  achievementKey: string | null;

  @Column({ type: 'int', default: 0 })
  achievementThreshold: number;

  /** Manually curated medal task; visible tasks appear in the existing task center. */
  @Column({ type: 'boolean', default: false })
  manualTaskEnabled: boolean;
  /** visible = shown in task center; hidden = admin-only/manual award. */
  @Column({ type: 'varchar', length: 16, default: 'hidden' })
  manualTaskVisibility: string;
  @Column({ type: 'varchar', length: 120, default: '' })
  manualTaskTitle: string;
  @Column({ type: 'varchar', length: 500, default: '' })
  manualTaskDescription: string;
  @Column({ type: 'int', default: 0 })
  manualTaskDays: number;

  /** Private server-tracked activity condition; never exposed to user task APIs. */
  @Column({ type: 'boolean', default: false })
  hiddenTaskEnabled: boolean;

  @Column({ type: 'varchar', length: 32, nullable: true })
  hiddenTaskEvent: string | null;

  @Column({ type: 'int', default: 1 })
  hiddenTaskTarget: number;

  /** 0 = permanent; otherwise the earned medal expires after this many days. */
  @Column({ type: 'int', default: 0 })
  hiddenTaskDays: number;

  @Column({ type: 'boolean', default: true })
  isActive: boolean;

  @Column({ type: 'boolean', default: false })
  isAnimated: boolean;

  @Column({ type: 'int', default: 0 })
  sortOrder: number;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt: Date;
}
