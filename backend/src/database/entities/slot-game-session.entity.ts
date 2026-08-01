import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  UpdateDateColumn,
  Index,
} from 'typeorm';

@Entity('slot_game_sessions')
export class SlotGameSession {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ type: 'uuid' })
  userId: string;

  @Index()
  @Column({ type: 'varchar', length: 64 })
  gameId: string;

  @Index()
  @Column({ type: 'uuid', nullable: true })
  roomId: string | null;

  @Column({ type: 'varchar', length: 128 })
  code: string;

  @Column({ type: 'varchar', length: 16, default: 'active' })
  status: 'active' | 'ended';

  @Column({ type: 'int', default: 0 })
  totalBetCoins: number;

  @Column({ type: 'int', default: 0 })
  totalWinCoins: number;

  @Column({ type: 'int', default: 0 })
  spinCount: number;

  @Column({ type: 'timestamptz', nullable: true })
  lastHeartbeatAt: Date | null;

  @Column({ type: 'timestamptz', nullable: true })
  endedAt: Date | null;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt: Date;
}
