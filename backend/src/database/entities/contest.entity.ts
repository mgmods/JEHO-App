import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  UpdateDateColumn,
  Index,
} from 'typeorm';

export type ContestStatus = 'upcoming' | 'active' | 'ended';

@Entity('contests')
export class Contest {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ type: 'varchar', length: 128 })
  title: string;

  @Column({ type: 'varchar', length: 512, nullable: true })
  description: string | null;

  /** rich | gifts | popular */
  @Column({ type: 'varchar', length: 32, default: 'rich' })
  category: string;

  @Index()
  @Column({ type: 'varchar', length: 16, default: 'active' })
  status: ContestStatus;

  @Column({ type: 'int', default: 0 })
  entryFeeCoins: number;

  @Column({ type: 'int', default: 0 })
  prizeCoins: number;

  @Column({ type: 'varchar', length: 128, nullable: true })
  prizeLabel: string | null;

  @Column({ type: 'timestamptz' })
  startAt: Date;

  @Column({ type: 'timestamptz' })
  endAt: Date;

  @Column({ type: 'int', default: 0 })
  entrantsCount: number;

  /** global | room | agency */
  @Column({ type: 'varchar', length: 16, default: 'global' })
  scope: string;

  @Index()
  @Column({ type: 'uuid', nullable: true })
  roomId: string | null;

  @Index()
  @Column({ type: 'uuid', nullable: true })
  agencyId: string | null;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt: Date;
}
