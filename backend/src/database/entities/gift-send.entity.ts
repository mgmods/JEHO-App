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
import { Gift } from './gift.entity';

@Entity('gift_sends')
export class GiftSend {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ type: 'uuid', nullable: true })
  giftId: string | null;

  @ManyToOne(() => Gift, { onDelete: 'SET NULL', nullable: true })
  @JoinColumn({ name: 'giftId' })
  gift: Gift | null;

  @Column({ type: 'varchar', length: 128, nullable: true })
  giftName: string | null;

  @Column({ type: 'varchar', length: 512, nullable: true })
  giftIconUrl: string | null;

  @Index()
  @Column({ type: 'uuid' })
  senderId: string;

  @ManyToOne(() => User, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'senderId' })
  sender: User;

  @Index()
  @Column({ type: 'uuid' })
  receiverId: string;

  @ManyToOne(() => User, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'receiverId' })
  receiver: User;

  @Column({ type: 'uuid', nullable: true })
  roomId: string | null;

  @Column({ type: 'int', default: 1 })
  quantity: number;

  @Column({ type: 'int', default: 1 })
  comboCount: number;

  @Column({ type: 'int' })
  totalCoins: number;

  @Column({ type: 'int', default: 0 })
  diamondsAwarded: number;

  @Column({ type: 'decimal', precision: 10, scale: 2, nullable: true })
  luckyMultiplier: number | null;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;
}
