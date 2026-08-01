import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  ManyToOne,
  JoinColumn,
  CreateDateColumn,
  Index,
} from 'typeorm';
import { User } from './user.entity';
import { LuckyBox } from './lucky-box.entity';

@Entity('lucky_box_opens')
@Index('IDX_lucky_open_user_box_day', ['userId', 'boxId', 'dayKey'])
export class LuckyBoxOpen {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ type: 'uuid' })
  boxId: string;

  @ManyToOne(() => LuckyBox, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'boxId' })
  box: LuckyBox;

  @Index()
  @Column({ type: 'uuid' })
  userId: string;

  @ManyToOne(() => User, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'userId' })
  user: User;

  @Column({ type: 'uuid', nullable: true })
  roomId: string | null;

  @Column({ type: 'uuid', nullable: true })
  agencyId: string | null;

  @Column({ type: 'int', default: 0 })
  costPaid: number;

  @Column({ type: 'varchar', length: 32 })
  rewardType: string;

  @Column({ type: 'bigint' })
  rewardAmount: number;

  @Column({ type: 'varchar', length: 10 })
  dayKey: string;

  @Column({ type: 'jsonb', nullable: true })
  metadata: Record<string, unknown> | null;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;
}
