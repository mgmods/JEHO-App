import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  JoinColumn,
  ManyToOne,
  PrimaryGeneratedColumn,
  UpdateDateColumn,
} from 'typeorm';
import { User } from './user.entity';

export enum AgencyApplicationStatus {
  PENDING = 'pending',
  CHANGES_REQUESTED = 'changes_requested',
  APPROVED = 'approved',
  REJECTED = 'rejected',
}

@Entity('agency_applications')
export class AgencyApplication {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ type: 'uuid' })
  applicantId: string;

  @ManyToOne(() => User, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'applicantId' })
  applicant: User;

  @Column({ type: 'varchar', length: 128 })
  proposedName: string;

  @Column({ type: 'text' })
  description: string;

  @Column({ type: 'text' })
  businessPlan: string;

  @Column({ type: 'varchar', length: 100 })
  country: string;

  @Column({ type: 'varchar', length: 254 })
  contactEmail: string;

  @Column({ type: 'varchar', length: 32 })
  contactPhone: string;

  @Column({ type: 'varchar', length: 512, nullable: true })
  socialLink: string | null;

  @Column({ type: 'text' })
  experience: string;

  @Column({ type: 'int' })
  expectedHostCount: number;

  @Column({ type: 'jsonb', default: () => "'[]'::jsonb" })
  documentUrls: string[];

  @Column({ type: 'boolean', default: false })
  termsAccepted: boolean;

  @Index()
  @Column({
    type: 'varchar',
    length: 24,
    default: AgencyApplicationStatus.PENDING,
  })
  status: AgencyApplicationStatus;

  @Column({ type: 'text', nullable: true })
  reviewNote: string | null;

  @Column({ type: 'uuid', nullable: true })
  reviewedById: string | null;

  @ManyToOne(() => User, { nullable: true, onDelete: 'SET NULL' })
  @JoinColumn({ name: 'reviewedById' })
  reviewedBy: User | null;

  @Column({ type: 'uuid', nullable: true })
  agencyId: string | null;

  /** Coins charged when the application was submitted (0 = legacy free). */
  @Column({ type: 'bigint', default: 0 })
  paidCoins: number;

  @Column({ type: 'varchar', length: 128, nullable: true })
  paymentReferenceId: string | null;

  @Column({ type: 'boolean', default: false })
  paymentRefunded: boolean;

  @Column({ type: 'timestamptz', nullable: true })
  reviewedAt: Date | null;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt: Date;
}
