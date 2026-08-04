import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  OneToOne,
  JoinColumn,
  CreateDateColumn,
  UpdateDateColumn,
} from 'typeorm';
import { User } from './user.entity';

@Entity('wallets')
export class Wallet {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ type: 'uuid', unique: true })
  userId: string;

  @OneToOne(() => User, (u) => u.wallet, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'userId' })
  user: User;

  @Column({ type: 'bigint', default: 0 })
  coins: number;

  @Column({ type: 'bigint', default: 0 })
  diamonds: number;

  /**
   * Diamonds earned from agency-room gifts (host share + owner commission).
   * Separate from personal-room `diamonds` — own withdraw/payout path.
   */
  @Column({ type: 'bigint', default: 0 })
  agencyDiamonds: number;

  /**
   * Diamonds received from host↔host trades (التاجر).
   * Separate from withdrawable `diamonds` so swaps accumulate as trader collection.
   */
  @Column({ type: 'bigint', default: 0 })
  traderDiamonds: number;

  /** @deprecated Silver retired — folded into `coins` on boot; kept for DB sync safety. */
  @Column({ type: 'bigint', default: 0 })
  silverCoins: number;

  @Column({ type: 'bigint', default: 0 })
  gamePoints: number;

  @Column({ type: 'bigint', default: 0 })
  totalRecharged: number;

  @Column({ type: 'bigint', default: 0 })
  totalWithdrawn: number;

  @Column({ type: 'varchar', length: 8, default: 'USD' })
  currency: string;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt: Date;
}
