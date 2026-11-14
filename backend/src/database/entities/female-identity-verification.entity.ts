import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  ManyToOne,
  JoinColumn,
  CreateDateColumn,
  UpdateDateColumn,
  Index,
} from 'typeorm';
import { User } from './user.entity';

export enum GenderVerificationStatus {
  PENDING = 'pending',
  APPROVED = 'approved',
  REJECTED = 'rejected',
}

/** Selfie submitted by female users for manual admin review. */
@Entity('female_identity_verifications')
export class FemaleIdentityVerification {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ type: 'uuid' })
  userId: string;

  @ManyToOne(() => User, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'userId' })
  user: User;

  @Column({ type: 'varchar', length: 512 })
  selfieUrl: string;

  /** Client liveness scan quality 0–1 (head turn left/center/right). */
  @Column({ type: 'float', nullable: true })
  livenessScore: number | null;

  @Column({ type: 'boolean', default: false })
  livenessPassed: boolean;

  /** auto = approved by system; manual = admin review queue. */
  @Column({ type: 'varchar', length: 16, nullable: true })
  decisionMode: string | null;

  @Column({
    type: 'enum',
    enum: GenderVerificationStatus,
    default: GenderVerificationStatus.PENDING,
  })
  status: GenderVerificationStatus;

  @Column({ type: 'text', nullable: true })
  reviewNote: string | null;

  @Column({ type: 'uuid', nullable: true })
  reviewedBy: string | null;

  @Column({ type: 'timestamptz', nullable: true })
  reviewedAt: Date | null;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt: Date;
}
