import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  ManyToOne,
  JoinColumn,
  CreateDateColumn,
  UpdateDateColumn,
  Index,
} from 'typeorm';
import { Room } from './room.entity';
import { User } from './user.entity';

export enum RoomGameType {
  TIC_TAC_TOE = 'tic_tac_toe',
}

export enum RoomGameStatus {
  WAITING = 'waiting',
  PLAYING = 'playing',
  FINISHED = 'finished',
  CANCELLED = 'cancelled',
}

export type RoomGameTurn = 'X' | 'O';
export type RoomGameWinner = 'X' | 'O' | 'draw' | null;

@Entity('room_games')
export class RoomGame {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ type: 'uuid' })
  roomId: string;

  @ManyToOne(() => Room, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'roomId' })
  room: Room;

  @Column({ type: 'enum', enum: RoomGameType, default: RoomGameType.TIC_TAC_TOE })
  type: RoomGameType;

  @Column({ type: 'enum', enum: RoomGameStatus, default: RoomGameStatus.WAITING })
  status: RoomGameStatus;

  @Column({ type: 'uuid', nullable: true })
  playerXId: string | null;

  @ManyToOne(() => User, { onDelete: 'SET NULL', nullable: true })
  @JoinColumn({ name: 'playerXId' })
  playerX: User | null;

  @Column({ type: 'uuid', nullable: true })
  playerOId: string | null;

  @ManyToOne(() => User, { onDelete: 'SET NULL', nullable: true })
  @JoinColumn({ name: 'playerOId' })
  playerO: User | null;

  /** Length 9: 0 empty, 1 = X, 2 = O */
  @Column({ type: 'simple-json' })
  board: number[];

  @Column({ type: 'varchar', length: 1, default: 'X' })
  turn: RoomGameTurn;

  @Column({ type: 'varchar', length: 8, nullable: true })
  winner: RoomGameWinner;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt: Date;
}
