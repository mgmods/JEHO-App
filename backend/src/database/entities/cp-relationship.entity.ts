import { Entity, PrimaryGeneratedColumn, Column, CreateDateColumn, UpdateDateColumn, Index } from 'typeorm';

export enum CpType {
  FRIENDSHIP = 'friendship',
  BROTHERHOOD = 'brotherhood',
  LOVE = 'love',
  ENGAGEMENT = 'engagement',
  MARRIAGE = 'marriage',
}

export enum CpRelationshipStatus {
  ACTIVE = 'active',
  ENDED = 'ended',
}

@Entity('cp_relationships')
@Index('idx_cp_relationship_type_status_points', ['type', 'status', 'points'])
@Index('idx_cp_relationship_user_a_status', ['userAId', 'status'])
@Index('idx_cp_relationship_user_b_status', ['userBId', 'status'])
export class CpRelationship {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ type: 'uuid' })
  userAId: string;

  @Column({ type: 'uuid' })
  userBId: string;

  @Column({ type: 'varchar', length: 24 })
  type: CpType;

  @Column({ type: 'varchar', length: 16, default: CpRelationshipStatus.ACTIVE })
  status: CpRelationshipStatus;

  @Column({ type: 'bigint', default: 0 })
  points: string;

  @Column({ type: 'int', default: 1 })
  level: number;

  @Column({ type: 'timestamptz' })
  startedAt: Date;

  @Column({ type: 'timestamptz', nullable: true })
  endedAt: Date | null;

  @Column({ type: 'uuid', nullable: true })
  endedByUserId: string | null;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt: Date;
}
