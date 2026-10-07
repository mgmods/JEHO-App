import { Injectable, NotFoundException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { ChatConversation, ConversationType } from '../../database/entities/chat-conversation.entity';
import { ChatParticipant } from '../../database/entities/chat-participant.entity';
import { ChatMessage } from '../../database/entities/chat-message.entity';
import { PaginationDto, paginate } from '../../common/dto/pagination.dto';

@Injectable()
export class AdminConversationsService {
  constructor(
    @InjectRepository(ChatConversation) private readonly conversations: Repository<ChatConversation>,
    @InjectRepository(ChatParticipant) private readonly participants: Repository<ChatParticipant>,
    @InjectRepository(ChatMessage) private readonly messages: Repository<ChatMessage>,
  ) {}

  async list(query: PaginationDto & { from?: string; to?: string }) {
    const qb = this.conversations.createQueryBuilder('c')
      .leftJoinAndSelect('c.participants', 'p')
      .leftJoinAndSelect('p.user', 'u')
      .leftJoinAndSelect('u.profile', 'profile')
      .orderBy('c.lastMessageAt', 'DESC', 'NULLS LAST')
      .addOrderBy('c.updatedAt', 'DESC')
      .skip(query.skip).take(query.limit || 20);

    const search = String(query.search || query.q || '').trim();
    if (search) {
      const s = `%${search}%`;
      qb.andWhere(
        '(u.displayName ILIKE :s OR u.username ILIKE :s OR CAST(u.id AS text) ILIKE :s OR CAST(u.publicId AS text) ILIKE :s)',
        { s },
      );
    }

    const type = String(query.type || '').trim().toLowerCase();
    if (type && Object.values(ConversationType).includes(type as ConversationType)) {
      qb.andWhere('c.type = :type', { type });
    }

    const from = String(query.from || '').trim();
    if (from) {
      const d = new Date(from);
      if (!Number.isNaN(d.getTime())) qb.andWhere('c.lastMessageAt >= :from', { from: d });
    }
    const to = String(query.to || '').trim();
    if (to) {
      const d = new Date(to);
      if (!Number.isNaN(d.getTime())) qb.andWhere('c.lastMessageAt <= :to', { to: d });
    }

    const [rows, total] = await qb.getManyAndCount();
    if (!rows.length) return paginate([], total, query.page || 1, query.limit || 20);

    const ids = rows.map((c) => c.id);
    const lastIds = rows.map((c) => c.lastMessageId).filter(Boolean) as string[];

    const [lastMessages, counts, unread] = await Promise.all([
      lastIds.length ? this.messages.find({ where: lastIds.map((id) => ({ id })), relations: ['sender'] }) : [],
      this.messages.createQueryBuilder('m')
        .select('m.conversationId', 'conversationId').addSelect('COUNT(*)', 'count')
        .where('m.conversationId IN (:...ids)', { ids }).andWhere('m.isUnsent = false')
        .groupBy('m.conversationId').getRawMany<{ conversationId: string; count: string }>(),
      this.participants.createQueryBuilder('p')
        .select('p.conversationId', 'conversationId').addSelect('COALESCE(SUM(p.unreadCount), 0)', 'unreadCount')
        .where('p.conversationId IN (:...ids)', { ids }).groupBy('p.conversationId')
        .getRawMany<{ conversationId: string; unreadCount: string }>(),
    ]);

    const lastById = new Map(lastMessages.map((m) => [m.id, m]));
    const countById = new Map(counts.map((r) => [r.conversationId, Number(r.count || 0)]));
    const unreadById = new Map(unread.map((r) => [r.conversationId, Number(r.unreadCount || 0)]));

    return paginate(rows.map((c) => {
      const participants = (c.participants || []).map((p) => ({
        id: p.userId,
        username: p.user?.username || null,
        displayName: p.user?.displayName || p.user?.username || null,
        publicId: p.user?.publicId || null,
        avatarUrl: p.user?.avatarUrl || null,
      }));
      const m = c.lastMessageId ? lastById.get(c.lastMessageId) : null;
      return {
        id: c.id, type: c.type,
        title: c.title || participants.map((p) => p.displayName).filter(Boolean).join('، ') || 'محادثة',
        participants,
        lastMessage: m ? { id: m.id, senderId: m.senderId, type: m.type, content: m.content, media: m.media || null, createdAt: m.createdAt } : null,
        lastMessageAt: c.lastMessageAt,
        messageCount: countById.get(c.id) || 0,
        unreadCount: unreadById.get(c.id) || 0,
        createdAt: c.createdAt, updatedAt: c.updatedAt,
      };
    }), total, query.page || 1, query.limit || 20);
  }

  async get(id: string) {
    const c = await this.conversations.findOne({
      where: { id },
      relations: ['participants', 'participants.user', 'participants.user.profile'],
    });
    if (!c) throw new NotFoundException('Conversation not found');

    const [messageCount, unreadRow] = await Promise.all([
      this.messages.count({ where: { conversationId: id, isUnsent: false } }),
      this.participants.createQueryBuilder('p')
        .select('COALESCE(SUM(p.unreadCount), 0)', 'unreadCount')
        .where('p.conversationId = :id', { id }).getRawOne<{ unreadCount: string }>(),
    ]);

    return {
      id: c.id, type: c.type, title: c.title,
      participants: (c.participants || []).map((p) => ({
        id: p.userId, username: p.user?.username || null,
        displayName: p.user?.displayName || p.user?.username || null,
        publicId: p.user?.publicId || null, avatarUrl: p.user?.avatarUrl || null,
        unreadCount: Number(p.unreadCount || 0), lastReadAt: p.lastReadAt,
      })),
      lastMessageAt: c.lastMessageAt, messageCount,
      unreadCount: Number(unreadRow?.unreadCount || 0),
      createdAt: c.createdAt, updatedAt: c.updatedAt,
    };
  }

  async messagesFor(id: string, query: PaginationDto) {
    const exists = await this.conversations.exists({ where: { id } });
    if (!exists) throw new NotFoundException('Conversation not found');

    const limit = Math.min(Math.max(query.limit || 50, 1), 100);
    const page = query.page || 1;
    const [newestFirst, total] = await this.messages.findAndCount({
      where: { conversationId: id, isUnsent: false },
      relations: ['sender', 'replyTo'],
      skip: (page - 1) * limit,
      take: limit,
      order: { createdAt: 'DESC' },
    });

    // This is deliberately separate from ChatService.listMessages(): no unread
    // update, no lastReadAt update and no realtime read receipt.
    return paginate(newestFirst.slice().reverse().map((m) => ({
      id: m.id, conversationId: m.conversationId, senderId: m.senderId,
      type: m.type, content: m.content, media: m.media || null,
      replyToId: m.replyToId,
      replyTo: m.replyTo ? {
        id: m.replyTo.id, senderId: m.replyTo.senderId, type: m.replyTo.type,
        content: m.replyTo.content, media: m.replyTo.media || null,
      } : null,
      forwardedFromId: m.forwardedFromId, isEdited: m.isEdited, isUnsent: m.isUnsent,
      createdAt: m.createdAt, updatedAt: m.updatedAt,
      sender: m.sender ? {
        id: m.sender.id, username: m.sender.username,
        displayName: m.sender.displayName, publicId: m.sender.publicId,
        avatarUrl: m.sender.avatarUrl,
      } : null,
    })), total, page, limit);
  }
}
