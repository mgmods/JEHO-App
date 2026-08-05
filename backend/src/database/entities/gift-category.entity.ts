import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  UpdateDateColumn,
  Index,
} from 'typeorm';

/** Admin-managed gift panel tabs (shown in Android gift sheet). */
@Entity('gift_categories')
export class GiftCategory {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  /** Stable key used on gift.category (normal, lucky, bag, …). */
  @Index({ unique: true })
  @Column({ type: 'varchar', length: 32 })
  key: string;

  @Column({ type: 'varchar', length: 64 })
  labelAr: string;

  @Column({ type: 'varchar', length: 64, nullable: true })
  labelEn: string | null;

  @Column({ type: 'int', default: 0 })
  sortOrder: number;

  @Column({ type: 'boolean', default: true })
  isActive: boolean;

  @Column({ type: 'varchar', length: 512, nullable: true })
  iconUrl: string | null;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt: Date;
}
