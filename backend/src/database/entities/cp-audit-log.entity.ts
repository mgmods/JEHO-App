import { Entity, PrimaryGeneratedColumn, Column, CreateDateColumn, Index } from 'typeorm';

@Entity('cp_audit_logs')
@Index('idx_cp_audit_created_at', ['createdAt'])
export class CpAuditLog {
  @PrimaryGeneratedColumn('uuid') id: string;
  @Column({ type: 'uuid', nullable: true }) adminUserId: string | null;
  @Column({ type: 'varchar', length: 64 }) action: string;
  @Column({ type: 'varchar', length: 64 }) entityType: string;
  @Column({ type: 'varchar', length: 128, nullable: true }) entityId: string | null;
  @Column({ type: 'jsonb', default: {} }) details: Record<string, unknown>;
  @CreateDateColumn({ type: 'timestamptz' }) createdAt: Date;
}
