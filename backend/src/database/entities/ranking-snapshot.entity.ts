import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  Index,
} from 'typeorm';

export enum RankingPeriod {
  DAILY = 'daily',
  WEEKLY = 'weekly',
  MONTHLY = 'monthly',
}

export enum RankingCategory {
  RICH = 'rich',
  POPULAR = 'popular',
  HOST = 'host',
  AGENCY = 'agency',
  ROOM = 'room',
  GIFTS = 'gifts',
}

@Entity('ranking_snapshots')
@Index(['period', 'category', 'periodKey'])
export class RankingSnapshot {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ type: 'enum', enum: RankingPeriod })
  period: RankingPeriod;

  @Column({ type: 'enum', enum: RankingCategory })
  category: RankingCategory;

  @Column({ type: 'varchar', length: 32 })
  periodKey: string;

  @Column({ type: 'uuid' })
  targetId: string;

  @Column({ type: 'varchar', length: 128, nullable: true })
  targetName: string | null;

  @Column({ type: 'int' })
  rank: number;

  @Column({ type: 'bigint' })
  score: number;

  @Column({ type: 'simple-json', nullable: true })
  meta: Record<string, unknown> | null;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;
}
