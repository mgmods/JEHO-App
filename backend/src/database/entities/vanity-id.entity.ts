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

  /** Per-ID appearance, independent of VIP membership. */
  @Column({ type: 'varchar', length: 16, default: 'static' })
  styleMode!: 'none' | 'static' | 'animated' | 'vip';

  /** VIP tier used only when styleMode=vip; existing tiers are VIP1–VIP7. */
  @Column({ type: 'int', default: 1 })
  styleVipLevel!: number;

  @Column({ type: 'varchar', length: 7, default: '#52E8FF' })
  styleColor1!: string;

  @Column({ type: 'varchar', length: 7, default: '#C084FC' })
  styleColor2!: string;

  @Column({ type: 'varchar', length: 7, default: '#FF7AC8' })
  styleColor3!: string;

  @Column({ type: 'boolean', default: true })
  styleGlow!: boolean;

  /** Optional dashboard-uploaded icon shown directly beside this ID. */
  @Column({ type: 'varchar', length: 512, nullable: true })
  styleIconUrl!: string | null;

  @Column({ type: 'uuid', nullable: true })
  ownerUserId!: string | null;

  /** Where the purchased ID is currently equipped; purchase alone does not overwrite account identity. */
  @Column({ type: 'varchar', length: 16, nullable: true })
  targetType!: 'account' | 'room' | 'agency' | null;

  @Column({ type: 'uuid', nullable: true })
  targetId!: string | null;

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

  /** Restore the room/agency ID that was present before this vanity ID was equipped. */
  @Column({ type: 'varchar', length: 32, nullable: true })
  previousTargetPublicId!: string | null;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt!: Date;

  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt!: Date;
}
