import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  Index,
  Unique,
} from 'typeorm';

@Entity('payment_webhook_events')
@Unique('UQ_payment_webhook_event', ['provider', 'eventId'])
export class PaymentWebhookEvent {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ type: 'varchar', length: 32 })
  provider: string;

  @Column({ type: 'varchar', length: 191 })
  eventId: string;

  @Column({ type: 'varchar', length: 64, nullable: true })
  eventType: string | null;

  @Column({ type: 'uuid', nullable: true })
  orderId: string | null;

  @Column({ type: 'simple-json', nullable: true })
  payload: Record<string, unknown> | null;

  @Column({ type: 'boolean', default: false })
  processed: boolean;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;
}
