import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  OneToMany,
  CreateDateColumn,
  UpdateDateColumn,
  Index,
} from 'typeorm';
import { ChatParticipant } from './chat-participant.entity';
import { ChatMessage } from './chat-message.entity';

export enum ConversationType {
  DIRECT = 'direct',
  GROUP = 'group',
}

@Entity('chat_conversations')
export class ChatConversation {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ type: 'enum', enum: ConversationType, default: ConversationType.DIRECT })
  type: ConversationType;

  @Column({ type: 'varchar', length: 128, nullable: true })
  title: string | null;

  @Index({ unique: true })
  @Column({ type: 'varchar', length: 128, nullable: true })
  directKey: string | null;

  @Column({ type: 'uuid', nullable: true })
  lastMessageId: string | null;

  @Column({ type: 'timestamptz', nullable: true })
  lastMessageAt: Date | null;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt: Date;

  @OneToMany(() => ChatParticipant, (p) => p.conversation)
  participants: ChatParticipant[];

  @OneToMany(() => ChatMessage, (m) => m.conversation)
  messages: ChatMessage[];
}
