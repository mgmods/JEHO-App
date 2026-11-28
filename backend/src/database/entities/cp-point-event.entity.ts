import { Entity, PrimaryGeneratedColumn, Column, CreateDateColumn, Index } from 'typeorm';

@Entity('cp_point_events')
@Index('uq_cp_point_event_reference', ['referenceType', 'referenceId'], { unique: true })
@Index('idx_cp_point_events_relationship_created', ['relationshipId', 'createdAt'])
export class CpPointEvent {
  @PrimaryGeneratedColumn('uuid') id: string;
  @Column({ type: 'uuid' }) relationshipId: string;
  @Column({ type: 'varchar', length: 64 }) eventType: string;
  @Column({ type: 'varchar', length: 128 }) referenceType: string;
  @Column({ type: 'varchar', length: 255 }) referenceId: string;
  @Column({ type: 'bigint' }) points: string;
  @Column({ type: 'jsonb', default: {} }) metadata: Record<string, unknown>;
  @CreateDateColumn({ type: 'timestamptz' }) createdAt: Date;
}
