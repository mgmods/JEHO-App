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
import { ChatConversation } from './chat-conversation.entity';
import { User } from './user.entity';

@Entity('chat_participants')
@Unique(['conversationId', 'userId'])
export class ChatParticipant {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ type: 'uuid' })
  conversationId: string;

  @ManyToOne(() => ChatConversation, (c) => c.participants, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'conversationId' })
  conversation: ChatConversation;

  @Index()
  @Column({ type: 'uuid' })
  userId: string;

  @ManyToOne(() => User, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'userId' })
  user: User;

  @Column({ type: 'boolean', default: false })
  isMuted: boolean;

  @Column({ type: 'boolean', default: false })
  isPinned: boolean;

  @Column({ type: 'boolean', default: false })
  isArchived: boolean;

  /** Soft-hide conversation for this user only */
  @Column({ type: 'boolean', default: false })
  isDeleted: boolean;

  @Column({ type: 'timestamptz', nullable: true })
  lastReadAt: Date | null;

  @Column({ type: 'int', default: 0 })
  unreadCount: number;

  @CreateDateColumn({ type: 'timestamptz' })
  joinedAt: Date;

  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt: Date;
}
