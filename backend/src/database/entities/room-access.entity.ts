import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  Index,
} from 'typeorm';

export enum RoomAccessGrant {
  SESSION = 'session',
  PERMANENT = 'permanent',
}

@Entity('room_access')
@Index(['roomId', 'userId'], { unique: true })
export class RoomAccess {
  @PrimaryGeneratedColumn('uuid')
  id!: string;

  @Index()
  @Column({ type: 'uuid' })
  roomId!: string;

  @Index()
  @Column({ type: 'uuid' })
  userId!: string;

  @Column({ type: 'varchar', length: 16, default: RoomAccessGrant.SESSION })
  grantType!: RoomAccessGrant;

  @Column({ type: 'int', default: 0 })
  coinsPaid!: number;

  @Index()
  @Column({ type: 'timestamptz', nullable: true })
  expiresAt!: Date | null;

  @CreateDateColumn({ type: 'timestamptz' })
  grantedAt!: Date;
}
