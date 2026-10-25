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
      .addOrderBy('c.updatedAt', 'DESC');

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

    const rows = await qb.getMany();
    if (!rows.length) return paginate([], 0, query.page || 1, query.limit || 20);

    const ids = rows.map((c) => c.id);
    const lastIds = rows.map((c) => c.lastMessageId).filter(Boolean) as string[];

    const lastMessages: ChatMessage[] = lastIds.length
      ? await this.messages.find({ where: lastIds.map((id) => ({ id })), relations: ['sender'] })
      : [];

    const [counts, unread] = await Promise.all([
      this.messages.createQueryBuilder('m')
        .select('m.conversationId', 'conversationId').addSelect('COUNT(*)', 'count')
        .where('m.conversationId IN (:...ids)', { ids }).andWhere('m.isUnsent = false')
        .groupBy('m.conversationId').getRawMany<{ conversationId: string; count: string }>(),
      this.participants.createQueryBuilder('p')
        .select('p.conversationId', 'conversationId').addSelect('COALESCE(SUM(p.unreadCount), 0)', 'unreadCount')
        .where('p.conversationId IN (:...ids)', { ids }).groupBy('p.conversationId')
        .getRawMany<{ conversationId: string; unreadCount: string }>(),
    ]);

    const lastById = new Map<string, ChatMessage>();
    for (const m of lastMessages) lastById.set(m.id, m);
    const countById = new Map<string, number>();
    for (const r of counts) countById.set(r.conversationId, Number(r.count || 0));
    const unreadById = new Map<string, number>();
    for (const r of unread) unreadById.set(r.conversationId, Number(r.unreadCount || 0));

    const mapped = rows.map((c) => {
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
        title: c.type === ConversationType.DIRECT
          ? (participants.map((p) => p.displayName).filter(Boolean).join(' ↔ ') || c.title || 'محادثة مباشرة')
          : (c.title || participants.map((p) => p.displayName).filter(Boolean).join('، ') || 'محادثة جماعية'),
        participants,
        lastMessage: m ? { id: m.id, senderId: m.senderId, type: m.type, content: m.content, media: m.media || null, createdAt: m.createdAt } : null,
        lastMessageAt: c.lastMessageAt,
        messageCount: countById.get(c.id) || 0,
        unreadCount: unreadById.get(c.id) || 0,
        createdAt: c.createdAt, updatedAt: c.updatedAt,
        _sortAt: c.lastMessageAt ? new Date(c.lastMessageAt).getTime() : 0,
      };
    });

    // Legacy duplicate direct threads for the same pair appear as one stable dashboard row.
    const grouped = new Map<string, any>();
    for (const item of mapped) {
      const key = item.type === ConversationType.DIRECT
        ? 'direct:' + item.participants.map((p: any) => p.id).sort().join(':')
        : 'group:' + item.id;
      const prior = grouped.get(key);
      if (!prior) {
        grouped.set(key, { ...item, conversationIds: [item.id] });
        continue;
      }
      prior.conversationIds.push(item.id);
      prior.messageCount += item.messageCount;
      prior.unreadCount += item.unreadCount;
      if (item._sortAt > prior._sortAt) {
        prior.id = item.id;
        prior.lastMessage = item.lastMessage;
        prior.lastMessageAt = item.lastMessageAt;
        prior.updatedAt = item.updatedAt;
        prior._sortAt = item._sortAt;
      }
    }
    const unique = [...grouped.values()].sort((a, b) => b._sortAt - a._sortAt)
      .map(({ _sortAt, ...item }) => item);
    const page = Math.max(1, query.page || 1);
    const limit = Math.min(200, Math.max(1, query.limit || 20));
    return paginate(unique.slice((page - 1) * limit, page * limit), unique.length, page, limit);
  }

  private async relatedDirectConversationIds(id: string): Promise<string[]> {
    const conversation = await this.conversations.findOne({
      where: { id },
      relations: ['participants'],
    });
    if (!conversation) throw new NotFoundException('Conversation not found');
    if (conversation.type !== ConversationType.DIRECT) return [id];
    const userIds = [...new Set((conversation.participants || []).map((p) => p.userId))];
    if (userIds.length !== 2) return [id];
    const matches = await this.conversations.createQueryBuilder('c')
      .innerJoin('c.participants', 'p')
      .where('c.type = :type', { type: ConversationType.DIRECT })
      .andWhere('p.userId IN (:...userIds)', { userIds })
      .groupBy('c.id')
      .having('COUNT(DISTINCT p.userId) = 2')
      .getMany();
    const ids = matches.map((item) => item.id);
    return ids.includes(id) ? ids : [id];
  }

  async get(id: string) {
    const c = await this.conversations.findOne({
      where: { id },
      relations: ['participants', 'participants.user', 'participants.user.profile'],
    });
    if (!c) throw new NotFoundException('Conversation not found');
    const conversationIds = await this.relatedDirectConversationIds(id);

    const [messageCount, unreadRow] = await Promise.all([
      this.messages.createQueryBuilder('m').where('m.conversationId IN (:...conversationIds)', { conversationIds })
        .andWhere('m.isUnsent = false').getCount(),
      this.participants.createQueryBuilder('p')
        .select('COALESCE(SUM(p.unreadCount), 0)', 'unreadCount')
        .where('p.conversationId IN (:...conversationIds)', { conversationIds }).getRawOne<{ unreadCount: string }>(),
    ]);

    return {
      id: c.id, conversationIds, type: c.type,
      title: c.type === ConversationType.DIRECT
        ? ((c.participants || []).map((p) => p.user?.displayName || p.user?.username).filter(Boolean).join(' ↔ ') || c.title || 'محادثة مباشرة')
        : (c.title || 'محادثة جماعية'),
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
    const conversationIds = await this.relatedDirectConversationIds(id);

    const limit = Math.min(Math.max(query.limit || 50, 1), 100);
    const page = query.page || 1;
    const [newestFirst, total] = await this.messages.findAndCount({
      where: conversationIds.flatMap((conversationId) => [{ conversationId, isUnsent: false }]),
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
