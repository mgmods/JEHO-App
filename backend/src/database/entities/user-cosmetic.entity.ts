import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  UpdateDateColumn,
  Index,
  ManyToOne,
  JoinColumn,
} from 'typeorm';
import { User } from './user.entity';
import { Cosmetic } from './cosmetic.entity';

@Entity('user_cosmetics')
export class UserCosmetic {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ type: 'uuid' })
  userId: string;

  @ManyToOne(() => User, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'userId' })
  user: User;

  @Index()
  @Column({ type: 'uuid' })
  cosmeticId: string;

  @ManyToOne(() => Cosmetic, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'cosmeticId' })
  cosmetic: Cosmetic;

  @Column({ type: 'boolean', default: false })
  equipped: boolean;

  @Column({ type: 'timestamptz', nullable: true })
  expiresAt: Date | null;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt: Date;
}
