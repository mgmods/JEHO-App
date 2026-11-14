import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  Index,
  Unique,
} from 'typeorm';

/** Tracks per-user likes (series or episode). */
@Entity('drama_likes')
@Unique(['userId', 'targetType', 'targetId'])
export class DramaLike {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ type: 'uuid' })
  userId: string;

  /** series | episode */
  @Column({ type: 'varchar', length: 16 })
  targetType: string;

  @Index()
  @Column({ type: 'uuid' })
  targetId: string;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;
}
