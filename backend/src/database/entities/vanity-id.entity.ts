import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  UpdateDateColumn,
  Index,
} from 'typeorm';

export enum VanityIdStatus {
  AVAILABLE = 'available',
  RESERVED = 'reserved',
  OWNED = 'owned',
  /** Hidden from the app catalog; not sold until re-enabled. */
  DISABLED = 'disabled',
}

@Entity('vanity_ids')
export class VanityId {
  @PrimaryGeneratedColumn('uuid')
  id!: string;

  @Index({ unique: true })
  @Column({ type: 'varchar', length: 16 })
  publicId!: string;

  @Column({ type: 'varchar', length: 16, default: VanityIdStatus.AVAILABLE })
  status!: VanityIdStatus;

  @Column({ type: 'int', default: 0 })
  priceCoins!: number;

  @Column({ type: 'uuid', nullable: true })
  ownerUserId!: string | null;

  @Column({ type: 'timestamptz', nullable: true })
  reservedUntil!: Date | null;

  @Column({ type: 'timestamptz', nullable: true })
  purchasedAt!: Date | null;

  /** Timed supporter / promo lease — null = permanent purchase. */
  @Column({ type: 'timestamptz', nullable: true })
  expiresAt!: Date | null;

  /** Restore this publicId on the user when the lease ends. */
  @Column({ type: 'varchar', length: 32, nullable: true })
  previousPublicId!: string | null;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt!: Date;

  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt!: Date;
}
