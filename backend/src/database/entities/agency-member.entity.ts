import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  ManyToOne,
  JoinColumn,
  CreateDateColumn,
  UpdateDateColumn,
  Unique,
  Index,
} from 'typeorm';
import { Agency } from './agency.entity';
import { User } from './user.entity';

export enum AgencyRole {
  OWNER = 'owner',
  MANAGER = 'manager',
  HOST = 'host',
  MEMBER = 'member',
}

/** Membership join lifecycle — self-join starts as pending until agency admin accepts. */
export enum AgencyMemberStatus {
  PENDING = 'pending',
  ACTIVE = 'active',
  REJECTED = 'rejected',
}

@Entity('agency_members')
@Unique(['agencyId', 'userId'])
export class AgencyMember {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ type: 'uuid' })
  agencyId: string;

  @ManyToOne(() => Agency, (a) => a.members, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'agencyId' })
  agency: Agency;

  @Index()
  @Column({ type: 'uuid' })
  userId: string;

  @ManyToOne(() => User, (u) => u.agencyMemberships, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'userId' })
  user: User;

  @Column({ type: 'enum', enum: AgencyRole, default: AgencyRole.MEMBER })
  role: AgencyRole;

  @Column({ type: 'bigint', default: 0 })
  diamondsContributed: number;

  @Column({
    type: 'varchar',
    length: 16,
    default: AgencyMemberStatus.ACTIVE,
  })
  status: AgencyMemberStatus;

  @Column({ type: 'boolean', default: true })
  isActive: boolean;

  @CreateDateColumn({ type: 'timestamptz' })
  joinedAt: Date;

  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt: Date;
}
