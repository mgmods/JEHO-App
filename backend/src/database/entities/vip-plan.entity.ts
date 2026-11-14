import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  UpdateDateColumn,
  Index,
} from 'typeorm';

@Entity('vip_plans')
export class VipPlan {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index({ unique: true })
  @Column({ type: 'int' })
  level: number;

  @Column({ type: 'varchar', length: 64 })
  name: string;

  @Column({ type: 'int' })
  coinPriceMonthly: number;

  @Column({ type: 'varchar', length: 512, nullable: true })
  badgeUrl: string | null;

  @Column({ type: 'simple-json' })
  benefits: {
    entryEffect: boolean;
    antiKick: boolean;
    antiMute: boolean;
    hostBadge: boolean;
    exclusiveGifts: boolean;
    flyingComment: boolean;
    roomPriority: number;
    dmLimit: number;
    extra: string[];
  };

  @Column({ type: 'boolean', default: true })
  isActive: boolean;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt: Date;
}
