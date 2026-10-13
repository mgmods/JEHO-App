import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  UpdateDateColumn,
  Index,
} from 'typeorm';

export enum AdminRole {
  SUPER = 'super',
  MODERATOR = 'moderator',
  SUPPORT = 'support',
  FINANCE = 'finance',
}

@Entity('admin_users')
export class AdminUser {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index({ unique: true })
  @Column({ type: 'varchar', length: 64 })
  email: string;

  @Index({ unique: true })
  @Column({ type: 'varchar', length: 48 })
  username: string;

  @Column({ type: 'varchar', length: 255, select: false })
  passwordHash: string;

  @Column({ type: 'enum', enum: AdminRole, default: AdminRole.MODERATOR })
  role: AdminRole;

  /** Optional migration reference only; never used to authorize dashboard access. */
  @Column({ type: 'uuid', nullable: true })
  linkedUserId: string | null;

  @Column({ type: 'varchar', length: 16, default: 'operator' })
  staffRole: string;

  @Column({ type: 'jsonb', nullable: true })
  dashboardPermissions: Record<string, string> | null;

  @Column({ type: 'boolean', default: true })
  isActive: boolean;

  @Column({ type: 'timestamptz', nullable: true })
  lastLoginAt: Date | null;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt: Date;
}
