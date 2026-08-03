import {
  Injectable,
  NotFoundException,
  ForbiddenException,
  BadRequestException,
  Optional,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import {
  ChatConversation,
  ConversationType,
} from '../../database/entities/chat-conversation.entity';
import { ChatParticipant } from '../../database/entities/chat-participant.entity';
import {
  ChatMessage,
  MessageType,
} from '../../database/entities/chat-message.entity';
import { Block } from '../../database/entities/block.entity';
import { UserVip } from '../../database/entities/user-vip.entity';
import { paginate, PaginationDto } from '../../common/dto/pagination.dto';
import { StartConversationDto, SendMessageDto, EditMessageDto } from './dto/chat.dto';
import { RealtimeGateway } from '../realtime/realtime.gateway';
import { NotificationsService } from '../notifications/notifications.service';
import { NotificationType } from '../../database/entities/notification.entity';
import { MediaCleanupService } from '../uploads/media-cleanup.service';
import { TasksService } from '../tasks/tasks.service';
import { GiftSend } from '../../database/entities/gift-send.entity';
import { UserProfile } from '../../database/entities/user-profile.entity';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { levelFromScore, MAX_ECONOMY_LEVEL } from '../../common/pricing-catalog';
import { effectiveVipLevel } from '../../common/vip-progress';
import { ContentModerationService } from '../moderation/content-moderation.service';

@Injectable()
export class ChatService {
  constructor(
    @InjectRepository(ChatConversation)
    private readonly convRepo: Repository<ChatConversation>,
    @InjectRepository(ChatParticipant)
    private readonly partRepo: Repository<ChatParticipant>,
    @InjectRepository(ChatMessage)
    private readonly msgRepo: Repository<ChatMessage>,
    @InjectRepository(Block)
    private readonly blocksRepo: Repository<Block>,
    @InjectRepository(UserVip)
    private readonly userVipsRepo: Repository<UserVip>,
    @InjectRepository(GiftSend)
    private readonly giftSendsRepo: Repository<GiftSend>,
    @InjectRepository(UserProfile)
    private readonly profilesRepo: Repository<UserProfile>,
    @InjectRepository(AppSetting)
    private readonly settingsRepo: Repository<AppSetting>,
    private readonly mediaCleanup: MediaCleanupService,
    private readonly moderation: ContentModerationService,
    @Optional() private readonly realtime?: RealtimeGateway,
    @Optional() private readonly notifications?: NotificationsService,
    @Optional() private readonly tasks?: TasksService,
  ) {}

  private directKey(a: string, b: string) {
    return [a, b].sort().join(':');
  }

  private async isGlobalDmGiftGateEnabled() {
    const row = await this.settingsRepo.findOne({
      where: { key: 'chat.requireGiftToDm' },
    });
    if (!row?.value) return false;
    const v = String(row.value).toLowerCase();
    return v === '1' || v === 'true' || v === 'yes';
  }

  /**
   * First non-gift message to a peer who enabled the gate requires a prior gift.
   */
  private async assertDmGiftGate(
    conversationId: string,
    senderId: string,
    _type: MessageType,
  ) {
    if (!(await this.isGlobalDmGiftGateEnabled())) return;

    const conv = await this.convRepo.findOne({ where: { id: conversationId } });
    if (!conv || conv.type !== ConversationType.DIRECT) return;

    const peers = await this.partRepo.find({ where: { conversationId } });
    const peer = peers.find((p) => p.userId !== senderId);
    if (!peer?.userId) return;

    const profile = await this.profilesRepo.findOne({
      where: { userId: peer.userId },
    });
    if (!profile?.dmGiftGateEnabled) return;

    // Sender already participated in this thread → gate already passed.
    const priorFromSender = await this.msgRepo.count({
      where: { conversationId, senderId },
    });
    if (priorFromSender > 0) return;

    const giftWhere: any = {
      senderId,
      receiverId: peer.userId,
    };
    if (profile.dmRequiredGiftId) {
      giftWhere.giftId = profile.dmRequiredGiftId;
    }
    const gifted = await this.giftSendsRepo.findOne({ where: giftWhere });
    if (gifted) return;

    throw new ForbiddenException({
      code: 'DM_GIFT_REQUIRED',
      message: 'أرسل هدية أولاً لفتح المحادثة مع هذا المستخدم',
      requiredGiftId: profile.dmRequiredGiftId || null,
      peerId: peer.userId,
    });
  }

  private async vipMapForUsers(userIds: string[]) {
    const ids = [...new Set(userIds.filter(Boolean))];
    const map = new Map<string, number>();
    if (!ids.length) return map;
    const rows = await this.userVipsRepo
      .createQueryBuilder('v')
      .select('v.userId', 'userId')
      .addSelect('MAX(v.level)', 'level')
      .where('v.userId IN (:...ids)', { ids })
      .andWhere('v.isActive = true')
      .andWhere('(v.expiresAt IS NULL OR v.expiresAt > NOW())')
      .groupBy('v.userId')
      .getRawMany<{ userId: string; level: string }>();
    for (const row of rows) {
      map.set(row.userId, Number(row.level) || 0);
    }
    const profiles = await this.profilesRepo.find({
      where: ids.map((id) => ({ userId: id })),
      select: ['userId', 'totalSentCoins'],
    });
    for (const p of profiles) {
      const base = map.get(p.userId) || 0;
      map.set(p.userId, effectiveVipLevel(base, Number(p.totalSentCoins || 0)));
    }
    return map;
  }

  /** Display levels for wealth / popularity chips (same as profile API). */
  private economyStats(profile?: UserProfile | null) {
    const received = Math.max(0, Number(profile?.totalReceivedDiamonds ?? 0));
    const sent = Math.max(0, Number(profile?.totalSentCoins ?? 0));
    const popularityLevel = Math.min(MAX_ECONOMY_LEVEL, levelFromScore(received));
    const wealthLevel = Math.min(MAX_ECONOMY_LEVEL, levelFromScore(sent));
    return {
      charmScore: popularityLevel,
      popularityScore: popularityLevel,
      popularityLevel,
      wealthScore: wealthLevel,
      wealthLevel,
      totalReceivedDiamonds: received,
      totalSentCoins: sent,
    };
  }

  private mapMessage(message: ChatMessage, vipLevel = 0) {
    const sender: any = message.sender;
    const profile = sender?.profile;
    const reply = (message as any).replyTo as ChatMessage | undefined;
    return {
      id: message.id,
      conversationId: message.conversationId,
      senderId: message.senderId,
      type: message.type,
      content: message.content,
      media: message.media || null,
      replyToId: message.replyToId,
      replyTo: reply
        ? {
            id: reply.id,
            content: reply.content,
            type: reply.type,
            senderId: reply.senderId,
            media: reply.media || null,
          }
        : null,
      isEdited: message.isEdited,
      createdAt: message.createdAt,
      sender: sender
        ? {
            id: sender.id,
            username: sender.username,
            displayName: sender.displayName,
            avatarUrl: sender.avatarUrl,
            hostBadgeUrl: profile?.hostBadgeUrl ?? null,
            vipBadgeUrl: profile?.vipBadgeUrl ?? null,
            roomCardUrl: profile?.roomCardUrl ?? null,
            level: Number(sender.level || 1),
            vipLevel: Number(vipLevel || sender._vipLevel || 0),
            ...this.economyStats(profile),
          }
        : undefined,
    };
  }

  async startOrGet(userId: string, dto: StartConversationDto) {
    if (userId === dto.peerId) throw new BadRequestException('Cannot chat with yourself');
    const blocked = await this.blocksRepo.findOne({
      where: [
        { blockerId: userId, blockedId: dto.peerId },
        { blockerId: dto.peerId, blockedId: userId },
      ],
    });
    if (blocked) throw new ForbiddenException('Unable to message this user');

    const key = this.directKey(userId, dto.peerId);
    let conv = await this.convRepo.findOne({
      where: { directKey: key },
      relations: ['participants', 'participants.user', 'participants.user.profile'],
    });
    if (conv) {
      // Re-opening a deleted/archived chat restores it for this user
      await this.partRepo.update(
        { conversationId: conv.id, userId },
        { isDeleted: false, isArchived: false },
      );
      const vipByUser = await this.vipMapForUsers([userId, dto.peerId]);
      return this.decorateConversation(conv, userId, vipByUser);
    }

    conv = await this.convRepo.save(
      this.convRepo.create({
        type: ConversationType.DIRECT,
        directKey: key,
      }),
    );
    await this.partRepo.save([
      this.partRepo.create({ conversationId: conv.id, userId }),
      this.partRepo.create({ conversationId: conv.id, userId: dto.peerId }),
    ]);
    return this.getConversation(conv.id, userId);
  }

  private async decorateConversation(
    conv: ChatConversation,
    userId: string,
    vipByUser?: Map<string, number>,
  ) {
    const my = (conv.participants || []).find((p) => p.userId === userId);
    const peerPart = (conv.participants || []).find((p) => p.userId !== userId);
    let lastMessage: ReturnType<ChatService['mapMessage']> | null = null;
    if (conv.lastMessageId) {
      const msg = await this.msgRepo.findOne({
        where: { id: conv.lastMessageId },
        relations: ['sender', 'sender.profile'],
      });
      if (msg && !msg.isUnsent) {
        const senderVip = vipByUser?.get(msg.senderId) ?? 0;
        lastMessage = this.mapMessage(msg, senderVip);
      }
    }
    const peerVip = peerPart?.userId ? vipByUser?.get(peerPart.userId) ?? 0 : 0;
    const showPeerOnline =
      (peerPart?.user as any)?.profile?.showOnlineStatus ?? true;
    const peerOnline =
      !!peerPart?.user &&
      showPeerOnline &&
      !!this.realtime &&
      (await this.realtime.isOnlineGlobal(peerPart.user.id));
    return {
      id: conv.id,
      type: conv.type,
      title: peerPart?.user?.displayName || peerPart?.user?.username || conv.title || null,
      avatarUrl: peerPart?.user?.avatarUrl || null,
      lastMessageId: conv.lastMessageId,
      lastMessageAt: conv.lastMessageAt,
      lastMessage,
      unreadCount: my?.unreadCount ?? 0,
      updatedAt: conv.updatedAt || conv.lastMessageAt,
      peer: peerPart?.user
        ? {
            id: peerPart.user.id,
            username: peerPart.user.username,
            displayName: peerPart.user.displayName,
            avatarUrl: peerPart.user.avatarUrl,
            hostBadgeUrl: (peerPart.user as any).profile?.hostBadgeUrl ?? null,
            vipBadgeUrl: (peerPart.user as any).profile?.vipBadgeUrl ?? null,
            roomCardUrl: (peerPart.user as any).profile?.roomCardUrl ?? null,
            level: Number((peerPart.user as any).level || 1),
            vipLevel: peerVip,
            ...this.economyStats((peerPart.user as any).profile),
            lastSeenAt: (peerPart.user as any).updatedAt || null,
            showOnlineStatus: showPeerOnline,
            isOnline: peerOnline,
          }
        : null,
      myParticipant: my,
    };
  }

  async listConversations(userId: string, query: PaginationDto) {
    const qb = this.partRepo
      .createQueryBuilder('p')
      .innerJoinAndSelect('p.conversation', 'c')
      .leftJoinAndSelect('c.participants', 'parts')
      .leftJoinAndSelect('parts.user', 'u')
      .leftJoinAndSelect('u.profile', 'up')
      .where('p.userId = :userId', { userId })
      .andWhere('p.isDeleted = false')
      .andWhere('p.isArchived = false')
      .orderBy('p.isPinned', 'DESC')
      .addOrderBy('c.lastMessageAt', 'DESC', 'NULLS LAST')
      .skip(query.skip)
      .take(query.limit || 20);

    const [parts, total] = await qb.getManyAndCount();
    const peerIds: string[] = [];
    for (const part of parts) {
      for (const peer of part.conversation?.participants || []) {
        if (peer.userId && peer.userId !== userId) peerIds.push(peer.userId);
        if (part.conversation?.lastMessageId && peer.userId) peerIds.push(peer.userId);
      }
    }
    const vipByUser = await this.vipMapForUsers(peerIds);
    const items = await Promise.all(
      parts.map((p) => this.decorateConversation(p.conversation, userId, vipByUser)),
    );
    return paginate(items, total, query.page || 1, query.limit || 20);
  }

  async getConversation(id: string, userId: string) {
    const part = await this.partRepo.findOne({ where: { conversationId: id, userId } });
    if (!part) throw new ForbiddenException('Not a participant');
    const conv = await this.convRepo.findOne({
      where: { id },
      relations: ['participants', 'participants.user', 'participants.user.profile'],
    });
    if (!conv) throw new NotFoundException('Conversation not found');
    const peerIds = (conv.participants || [])
      .map((p) => p.userId)
      .filter((id): id is string => !!id);
    const vipByUser = await this.vipMapForUsers(peerIds);
    return this.decorateConversation(conv, userId, vipByUser);
  }

  async send(conversationId: string, senderId: string, dto: SendMessageDto) {
    await this.getConversation(conversationId, senderId);
    if (!dto.content && !dto.media && !dto.forwardFromId) {
      throw new BadRequestException('Message content required');
    }

    let content = dto.content || null;
    let media = dto.media || null;
    let type = dto.type || (media ? MessageType.IMAGE : MessageType.TEXT);
    let forwardedFromId: string | null = null;

    if (dto.forwardFromId) {
      const original = await this.msgRepo.findOne({ where: { id: dto.forwardFromId } });
      if (!original || original.isUnsent) throw new NotFoundException('Original message not found');
      content = original.content;
      media = original.media;
      type = original.type;
      forwardedFromId = original.id;
    }

    if (type === MessageType.TEXT || type === MessageType.IMAGE) {
      await this.moderation.assertCleanText(content);
    }

    // Gift messages themselves satisfy the gate — only block first text/media.
    if (type !== MessageType.GIFT && type !== MessageType.SYSTEM) {
      await this.assertDmGiftGate(conversationId, senderId, type);
    }

    const message = await this.msgRepo.save(
      this.msgRepo.create({
        conversationId,
        senderId,
        type,
        content,
        media,
        replyToId: dto.replyToId || null,
        forwardedFromId,
      }),
    );

    await this.convRepo.update(conversationId, {
      lastMessageId: message.id,
      lastMessageAt: message.createdAt,
    });

    const full = await this.msgRepo.findOne({
      where: { id: message.id },
      relations: ['sender', 'sender.profile', 'replyTo'],
    });
    const senderVip = (await this.vipMapForUsers([senderId])).get(senderId) ?? 0;
    const mapped = this.mapMessage(full || message, senderVip);

    const peers = await this.partRepo.find({
      where: { conversationId },
      relations: ['user'],
    });
    for (const p of peers) {
      const viewing =
        p.userId !== senderId &&
        this.realtime &&
        (await this.realtime.isUserInConversation(p.userId, conversationId));

      if (p.userId !== senderId && !viewing) {
        await this.partRepo.increment({ id: p.id }, 'unreadCount', 1);
        p.unreadCount = Number(p.unreadCount || 0) + 1;
      } else if (p.userId !== senderId && viewing) {
        // Actively viewing this thread — mark read instantly for sender ticks.
        const readAt = new Date();
        await this.partRepo.update({ id: p.id }, { unreadCount: 0, lastReadAt: readAt });
        p.unreadCount = 0;
        p.lastReadAt = readAt;
        this.realtime?.emitToUser(senderId, 'chat:read', {
          conversationId,
          userId: p.userId,
          readerId: p.userId,
          lastReadAt: readAt.toISOString(),
          at: readAt.toISOString(),
        });
      }

      this.realtime?.emitToUser(p.userId, 'chat:message', {
        conversationId,
        message: mapped,
        at: new Date().toISOString(),
      });
      this.realtime?.emitToUser(p.userId, 'chat:conversation:updated', {
        conversationId,
        lastMessage: mapped,
        unreadCount: p.userId === senderId ? 0 : Number(p.unreadCount || 0),
        at: new Date().toISOString(),
      });
      if (p.userId !== senderId && this.notifications && !viewing) {
        const preview =
          (content && content.slice(0, 80)) ||
          (type === MessageType.IMAGE
            ? '📷 صورة'
            : type === MessageType.AUDIO
              ? '🎤 رسالة صوتية'
              : type === MessageType.GIFT
                ? '🎁 هدية'
                : 'رسالة جديدة');
        const senderName =
          (full?.sender as any)?.displayName ||
          (full?.sender as any)?.username ||
          'صديق';
        const senderAvatar =
          (full?.sender as any)?.avatarUrl ||
          mapped?.sender?.avatarUrl ||
          null;
        void this.notifications.notifyUser(
          p.userId,
          NotificationType.CHAT,
          senderName,
          preview,
          {
            type: 'chat',
            conversationId,
            messageId: message.id,
            avatarUrl: senderAvatar,
            senderAvatarUrl: senderAvatar,
            senderName,
          },
        );
      }
    }

    // New-male ↔ female host chat task progress (≥2 min rounds).
    try {
      const peerPart = peers.find((p) => p.userId && p.userId !== senderId);
      const senderUser = full?.sender as any;
      const peerUser = peerPart?.user as any;
      if (peerPart?.userId && this.tasks) {
        void this.tasks.trackChatForTasks({
          conversationId,
          senderId,
          peerId: peerPart.userId,
          senderGender: senderUser?.gender,
          peerGender: peerUser?.gender,
          senderCreatedAt: senderUser?.createdAt,
        });
      }
    } catch {
      /* non-fatal */
    }

    return mapped;
  }

  async listMessages(conversationId: string, userId: string, query: PaginationDto) {
    await this.getConversation(conversationId, userId);
    // Newest-first pagination: page 1 must be the latest messages (chat UI),
    // then reverse to chronological ASC for display. ASC+skip was returning
    // only the oldest page and hiding newer DMs after ~20 messages.
    const limit = Math.min(Math.max(query.limit || 50, 1), 100);
    const page = query.page || 1;
    const skip = (page - 1) * limit;
    const [newestFirst, total] = await this.msgRepo.findAndCount({
      where: { conversationId, isUnsent: false },
      relations: ['sender', 'sender.profile', 'replyTo'],
      skip,
      take: limit,
      order: { createdAt: 'DESC' },
    });
    const items = newestFirst.slice().reverse();
    const vipByUser = await this.vipMapForUsers(items.map((m) => m.senderId));
    const readAt = new Date();
    await this.partRepo.update(
      { conversationId, userId },
      { unreadCount: 0, lastReadAt: readAt },
    );
    await this.emitReadReceipt(conversationId, userId, readAt);
    const peers = await this.partRepo.find({ where: { conversationId } });
    const peerPart = peers.find((p) => p.userId !== userId);
    const pageResult = paginate(
      items.map((m) => this.mapMessage(m, vipByUser.get(m.senderId) ?? 0)),
      total,
      page,
      limit,
    );
    return {
      ...pageResult,
      peerLastReadAt: peerPart?.lastReadAt
        ? new Date(peerPart.lastReadAt).toISOString()
        : null,
    };
  }

  private async emitReadReceipt(conversationId: string, readerId: string, lastReadAt: Date) {
    const payload = {
      conversationId,
      userId: readerId,
      readerId,
      lastReadAt: lastReadAt.toISOString(),
      at: lastReadAt.toISOString(),
    };
    const peers = await this.partRepo.find({ where: { conversationId } });
    for (const peer of peers) {
      if (peer.userId === readerId) continue;
      this.realtime?.emitToUser(peer.userId, 'chat:read', payload);
    }
  }

  async edit(messageId: string, userId: string, dto: EditMessageDto) {
    const msg = await this.msgRepo.findOne({ where: { id: messageId }, relations: ['sender', 'sender.profile'] });
    if (!msg) throw new NotFoundException('Message not found');
    if (msg.senderId !== userId) throw new ForbiddenException('Cannot edit others messages');
    if (msg.isUnsent) throw new BadRequestException('Message was unsent');
    await this.moderation.assertCleanText(dto.content);
    msg.content = dto.content;
    msg.isEdited = true;
    msg.editedAt = new Date();
    const saved = await this.msgRepo.save(msg);
    const senderVip = (await this.vipMapForUsers([userId])).get(userId) ?? 0;
    const mapped = this.mapMessage({ ...saved, sender: msg.sender }, senderVip);
    const conversation = await this.convRepo.findOne({ where: { id: msg.conversationId } });
    const touchesPreview = conversation?.lastMessageId === msg.id;
    const payload = {
      conversationId: msg.conversationId,
      message: mapped,
      at: new Date().toISOString(),
    };
    const peers = await this.partRepo.find({ where: { conversationId: msg.conversationId } });
    for (const peer of peers) {
      this.realtime?.emitToUser(peer.userId, 'chat:message:edited', payload);
      if (touchesPreview) {
        this.realtime?.emitToUser(peer.userId, 'chat:conversation:updated', {
          conversationId: msg.conversationId,
          lastMessage: mapped,
          unreadCount: peer.userId === userId ? 0 : peer.unreadCount,
          at: payload.at,
        });
      }
    }
    return mapped;
  }

  async unsend(messageId: string, userId: string) {
    const existing = await this.msgRepo.findOne({
      where: { id: messageId },
      relations: ['sender', 'sender.profile'],
    });
    if (!existing) throw new NotFoundException('Message not found');
    if (existing.senderId !== userId) throw new ForbiddenException('Cannot unsend others messages');
    if (existing.isUnsent) throw new BadRequestException('Message was already unsent');

    const conversationId = existing.conversationId;
    const messageCreatedAt = existing.createdAt;
    const mediaToDelete = existing.media?.url || null;

    await this.msgRepo.manager.transaction(async (em) => {
      const msgRepo = em.getRepository(ChatMessage);
      const convRepo = em.getRepository(ChatConversation);
      const partRepo = em.getRepository(ChatParticipant);

      const msg = await msgRepo.findOne({ where: { id: messageId } });
      if (!msg || msg.isUnsent) return;

      msg.isUnsent = true;
      msg.content = null;
      msg.media = null;
      await msgRepo.save(msg);

      const conversation = await convRepo.findOne({
        where: { id: conversationId },
        lock: { mode: 'pessimistic_write' },
      });
      if (conversation?.lastMessageId === messageId) {
        const previous = await msgRepo.findOne({
          where: { conversationId, isUnsent: false },
          order: { createdAt: 'DESC' },
        });
        conversation.lastMessageId = previous?.id || null;
        conversation.lastMessageAt = previous?.createdAt || null;
        await convRepo.save(conversation);
      }

      const peers = await partRepo.find({ where: { conversationId } });
      for (const peer of peers) {
        if (peer.userId === userId) continue;
        const wasUnread =
          !peer.lastReadAt || peer.lastReadAt.getTime() < messageCreatedAt.getTime();
        if (wasUnread && peer.unreadCount > 0) {
          peer.unreadCount = Math.max(peer.unreadCount - 1, 0);
          await partRepo.save(peer);
        }
      }
    });

    const msg = await this.msgRepo.findOne({
      where: { id: messageId },
      relations: ['sender', 'sender.profile'],
    });
    if (!msg) throw new NotFoundException('Message not found');

    let lastMessage: ReturnType<ChatService['mapMessage']> | null = null;
    const conversation = await this.convRepo.findOne({ where: { id: conversationId } });
    if (conversation?.lastMessageId) {
      const current = await this.msgRepo.findOne({
        where: { id: conversation.lastMessageId, isUnsent: false },
        relations: ['sender', 'sender.profile'],
      });
      if (current) {
        const vip =
          (await this.vipMapForUsers([current.senderId])).get(current.senderId) ?? 0;
        lastMessage = this.mapMessage(current, vip);
      }
    }

    const payload = {
      conversationId,
      messageId,
      at: new Date().toISOString(),
    };
    const peers = await this.partRepo.find({ where: { conversationId } });
    for (const peer of peers) {
      this.realtime?.emitToUser(peer.userId, 'chat:message:unsent', payload);
      this.realtime?.emitToUser(peer.userId, 'chat:conversation:updated', {
        conversationId,
        lastMessage,
        unreadCount: peer.userId === userId ? 0 : peer.unreadCount,
        at: payload.at,
      });
    }

    // Remove attached voice/image files from disk so the server does not keep orphans.
    this.mediaCleanup.deleteUploadUrl(mediaToDelete);

    return {
      id: msg.id,
      conversationId,
      senderId: msg.senderId,
      type: msg.type,
      content: null,
      media: null,
      isEdited: msg.isEdited,
      isUnsent: true,
      createdAt: msg.createdAt,
    };
  }

  async mute(conversationId: string, userId: string, muted: boolean) {
    await this.getConversation(conversationId, userId);
    await this.partRepo.update({ conversationId, userId }, { isMuted: muted });
    return { muted };
  }

  async pin(conversationId: string, userId: string, pinned: boolean) {
    await this.getConversation(conversationId, userId);
    await this.partRepo.update({ conversationId, userId }, { isPinned: pinned });
    return { pinned };
  }

  async archive(conversationId: string, userId: string, archived = true) {
    await this.getConversation(conversationId, userId);
    await this.partRepo.update({ conversationId, userId }, { isArchived: archived });
    this.realtime?.emitToUser(userId, 'chat:conversation:deleted', {
      conversationId,
      byUserId: userId,
      archived: true,
      at: new Date().toISOString(),
    });
    return { archived };
  }

  async deleteForMe(conversationId: string, userId: string) {
    await this.getConversation(conversationId, userId);
    // Delete for everyone in the conversation (both peers).
    const peers = await this.partRepo.find({ where: { conversationId } });
    await this.partRepo.update(
      { conversationId },
      { isDeleted: true, unreadCount: 0, isArchived: false, isPinned: false },
    );
    const at = new Date().toISOString();
    for (const peer of peers) {
      this.realtime?.emitToUser(peer.userId, 'chat:conversation:deleted', {
        conversationId,
        byUserId: userId,
        at,
      });
    }
    return { deleted: true, forEveryone: true };
  }
}
