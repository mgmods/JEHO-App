import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  Index,
} from 'typeorm';

export enum AbuseSeverity {
  LOW = 'low',
  MEDIUM = 'medium',
  HIGH = 'high',
  CRITICAL = 'critical',
}

@Entity('abuse_logs')
export class AbuseLog {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ type: 'uuid', nullable: true })
  userId: string | null;

  @Column({ type: 'varchar', length: 64 })
  action: string;

  @Column({ type: 'varchar', length: 64, nullable: true })
  targetType: string | null;

  @Column({ type: 'uuid', nullable: true })
  targetId: string | null;

  @Column({ type: 'enum', enum: AbuseSeverity, default: AbuseSeverity.LOW })
  severity: AbuseSeverity;

  @Column({ type: 'text', nullable: true })
  details: string | null;

  @Column({ type: 'varchar', length: 64, nullable: true })
  ipAddress: string | null;

  @Column({ type: 'boolean', default: false })
  resolved: boolean;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;
}
