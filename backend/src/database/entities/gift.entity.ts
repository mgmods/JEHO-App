import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  UpdateDateColumn,
  Index,
} from 'typeorm';

export enum GiftType {
  NORMAL = 'normal',
  LUCKY = 'lucky',
  COMBO = 'combo',
  PREMIUM = 'premium',
}

@Entity('gifts')
export class Gift {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ type: 'varchar', length: 64 })
  name: string;

  @Column({ type: 'varchar', length: 255, nullable: true })
  description: string | null;

  @Column({ type: 'varchar', length: 512 })
  iconUrl: string;

  @Column({ type: 'varchar', length: 512, nullable: true })
  animationUrl: string | null;

  @Column({ type: 'int' })
  coinPrice: number;

  @Column({ type: 'int', default: 0 })
  diamondValue: number;

  @Column({ type: 'enum', enum: GiftType, default: GiftType.NORMAL })
  type: GiftType;

  /** Mikoo-style panel tab: normal/lucky/bag/cp/friend/country/debris/agency/celebrity/premium/combo */
  @Column({ type: 'varchar', length: 32, default: 'normal' })
  category: string;

  @Column({ type: 'boolean', default: true })
  isActive: boolean;

  @Column({ type: 'int', default: 0 })
  sortOrder: number;

  @Column({ type: 'int', nullable: true })
  comboWindowMs: number | null;

  @Column({ type: 'simple-json', nullable: true })
  luckyConfig: {
    mode?: 'multiplier' | 'weighted_coins';
    minMultiplier?: number;
    maxMultiplier?: number;
    winChance?: number;
    outcomes?: { amount: number; weight: number }[];
  } | null;

  /**
   * Optional agency branding on this gift — when set, gift animations/payloads
   * may show the agency logo / name (admin catalog only).
   */
  @Index()
  @Column({ type: 'uuid', nullable: true })
  brandAgencyId: string | null;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt: Date;
}
