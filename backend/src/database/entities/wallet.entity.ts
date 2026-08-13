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
   * @deprecated CLEAN ECONOMY: retired. All gift earnings (host share + agency
   * owner commission) now land in the single `diamonds` pool. Column kept at 0
   * for DB-sync safety and historical rows only. Do not write to it.
   */
  @Column({ type: 'bigint', default: 0 })
  agencyDiamonds: number;

  /**
   * @deprecated CLEAN ECONOMY: retired. Host↔host trades fold into `diamonds`.
   * Column kept at 0 for DB-sync safety only. Do not write to it.
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
