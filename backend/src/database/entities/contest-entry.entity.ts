import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  Index,
  Unique,
} from 'typeorm';

@Entity('contest_entries')
@Unique(['contestId', 'userId'])
export class ContestEntry {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ type: 'uuid' })
  contestId: string;

  @Index()
  @Column({ type: 'uuid' })
  userId: string;

  @Column({ type: 'bigint', default: 0 })
  score: string;

  @CreateDateColumn({ type: 'timestamptz' })
  joinedAt: Date;
}
