import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  Index,
  Unique,
} from 'typeorm';

/** Tracks user likes on series or episodes. */
@Entity('drama_reactions')
@Unique(['userId', 'targetType', 'targetId'])
export class DramaReaction {
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
