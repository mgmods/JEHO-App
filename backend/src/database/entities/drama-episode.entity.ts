import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  UpdateDateColumn,
  Index,
  ManyToOne,
  JoinColumn,
} from 'typeorm';
import { DramaSeries } from './drama-series.entity';

@Entity('drama_episodes')
export class DramaEpisode {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ type: 'uuid' })
  seriesId: string;

  @ManyToOne(() => DramaSeries, (s) => s.episodes, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'seriesId' })
  series?: DramaSeries;

  @Column({ type: 'varchar', length: 200 })
  title: string;

  @Column({ type: 'int', default: 1 })
  episodeNumber: number;

  @Column({ type: 'varchar', length: 1024 })
  videoUrl: string;

  @Column({ type: 'varchar', length: 512, nullable: true })
  thumbnailUrl: string | null;

  @Column({ type: 'int', default: 0 })
  durationSec: number;

  @Column({ type: 'int', default: 0 })
  viewCount: number;

  @Column({ type: 'int', default: 0 })
  likeCount: number;

  @Index()
  @Column({ type: 'boolean', default: true })
  isPublished: boolean;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt: Date;
}
