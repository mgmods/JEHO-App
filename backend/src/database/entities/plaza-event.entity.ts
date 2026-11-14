import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  UpdateDateColumn,
  Index,
} from 'typeorm';

export type PlazaEventStatus = 'upcoming' | 'live' | 'ended';

/** Mikoo-style room activity / event plaza item. */
@Entity('plaza_events')
export class PlazaEvent {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ type: 'varchar', length: 128 })
  title: string;

  @Column({ type: 'varchar', length: 512, nullable: true })
  description: string | null;

  @Column({ type: 'varchar', length: 512, nullable: true })
  coverUrl: string | null;

  /** party | game | music | contest | other */
  @Column({ type: 'varchar', length: 32, default: 'party' })
  tag: string;

  @Index()
  @Column({ type: 'varchar', length: 16, default: 'upcoming' })
  status: PlazaEventStatus;

  @Column({ type: 'timestamptz' })
  startAt: Date;

  @Column({ type: 'timestamptz' })
  endAt: Date;

  @Index()
  @Column({ type: 'uuid' })
  hostId: string;

  @Index()
  @Column({ type: 'uuid', nullable: true })
  roomId: string | null;

  @Column({ type: 'int', default: 0 })
  subscribersCount: number;

  /** When true, shown in public Event Square. */
  @Column({ type: 'boolean', default: true })
  isPublic: boolean;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt: Date;
}
