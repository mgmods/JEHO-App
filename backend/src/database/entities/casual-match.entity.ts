import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  UpdateDateColumn,
  Index,
} from 'typeorm';

export type CasualGameKind = 'ono' | 'domino' | 'ludo';
export type CasualMatchStatus = 'waiting' | 'playing' | 'finished' | 'cancelled';

@Entity('casual_matches')
export class CasualMatch {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ type: 'varchar', length: 16 })
  kind: CasualGameKind;

  @Index()
  @Column({ type: 'varchar', length: 16, default: 'waiting' })
  status: CasualMatchStatus;

  @Column({ type: 'uuid' })
  player1Id: string;

  @Column({ type: 'uuid', nullable: true })
  player2Id: string | null;

  @Index()
  @Column({ type: 'uuid', nullable: true })
  roomId: string | null;

  /** Whose turn: 1 or 2 */
  @Column({ type: 'int', default: 1 })
  turn: number;

  /** Full HTML game snapshot (host-authority) */
  @Column({ type: 'simple-json', nullable: true })
  state: Record<string, unknown> | null;

  @Column({ type: 'int', default: 0 })
  stateVersion!: number;

  @Column({ type: 'timestamptz', nullable: true })
  lastHeartbeatAt!: Date | null;

  @Column({ type: 'varchar', length: 8, nullable: true })
  winner: string | null;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt: Date;
}
