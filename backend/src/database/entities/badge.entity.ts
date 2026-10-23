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

  /** Optional visual styling for display-rank badges only. */
  @Column({ type: 'varchar', length: 16, default: '#D8B35B' })
  displayColor: string;

  @Column({ type: 'varchar', length: 512, nullable: true })
  frameUrl: string | null;

  @Column({ type: 'varchar', length: 512, nullable: true })
  nameplateUrl: string | null;

  @Column({ type: 'varchar', length: 512, nullable: true })
  entryEffectUrl: string | null;

  @Column({ type: 'varchar', length: 32, default: 'manual' })
  awardType: string;

  @Column({ type: 'varchar', length: 64, nullable: true })
  achievementKey: string | null;

  @Column({ type: 'int', default: 0 })
  achievementThreshold: number;

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
