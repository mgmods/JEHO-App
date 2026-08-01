import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  UpdateDateColumn,
  Index,
} from 'typeorm';

export enum CosmeticType {
  ENTRY_EFFECT = 'entry_effect',
  JOIN_TOAST = 'join_toast',
  ROOM_CARD = 'room_card',
  ROOM_BACKGROUND = 'room_background',
  LEVEL_BADGE = 'level_badge',
  VIP_BADGE = 'vip_badge',
  HOST_BADGE = 'host_badge',
}

@Entity('cosmetics')
export class Cosmetic {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ type: 'enum', enum: CosmeticType })
  type: CosmeticType;

  @Index({ unique: true })
  @Column({ type: 'varchar', length: 64 })
  code: string;

  @Column({ type: 'varchar', length: 128 })
  name: string;

  @Column({ type: 'varchar', length: 255, nullable: true })
  description: string | null;

  @Column({ type: 'varchar', length: 512 })
  previewUrl: string;

  @Column({ type: 'varchar', length: 512, nullable: true })
  animationUrl: string | null;

  @Column({ type: 'int', default: 0 })
  coinPrice: number;

  @Column({ type: 'int', default: 0 })
  minVipLevel: number;

  @Column({ type: 'int', default: 0 })
  minUserLevel: number;

  @Column({ type: 'boolean', default: true })
  isActive: boolean;

  @Column({ type: 'int', default: 0 })
  sortOrder: number;

  @Column({ type: 'simple-json', nullable: true })
  meta: Record<string, unknown> | null;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt: Date;
}
