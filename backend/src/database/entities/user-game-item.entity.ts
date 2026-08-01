import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  Index,
  Unique,
} from 'typeorm';

@Entity('user_game_items')
@Unique(['userId', 'sku'])
export class UserGameItem {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ type: 'uuid' })
  userId: string;

  @Index()
  @Column({ type: 'varchar', length: 64 })
  sku: string;

  @Column({ type: 'varchar', length: 128 })
  title: string;

  @Column({ type: 'int', default: 0 })
  costPoints: number;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;
}
