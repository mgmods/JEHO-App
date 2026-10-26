import {
  Injectable,
  NotFoundException,
  ForbiddenException,
  BadRequestException,
  Optional,
  OnModuleInit,
  OnModuleDestroy,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { DataSource, Repository } from 'typeorm';
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
import { MediaCleanupService } from '../uploads/media-cleanup.service';
import { TasksService } from '../tasks/tasks.service';
import { GiftSend } from '../../database/entities/gift-send.entity';
import { UserProfile } from '../../database/entities/user-profile.entity';
import { Follow } from '../../database/entities/follow.entity';
import { SocialRequest, SocialRequestStatus, SocialRequestType } from '../../database/entities/social-request.entity';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { levelFromScore, MAX_ECONOMY_LEVEL } from '../../common/pricing-catalog';
import { effectiveVipLevel } from '../../common/vip-progress';
import { Wallet } from '../../database/entities/wallet.entity';
import {
  WalletTransaction,
  TransactionType,
  CurrencyType,
} from '../../database/entities/wallet-transaction.entity';
import { ContentModerationService } from '../moderation/content-moderation.service';

@Injectable()
export class ChatService implements OnModuleInit, OnModuleDestroy {
  private paidRefundTimer?: ReturnType<typeof setInterval>;

  onModuleInit() {
    // Recover expired paid-message refunds after startup and continue periodically.
    void this.processExpiredPaidMessageRefunds();
    this.paidRefundTimer = setInterval(() => {
      void this.processExpiredPaidMessageRefunds();
    }, 60_000);
    this.paidRefundTimer.unref?.();
  }

  onModuleDestroy() {
    if (this.paidRefundTimer) clearInterval(this.paidRefundTimer);
  }
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
    private readonly dataSource: DataSource,
    private readonly mediaCleanup: MediaCleanupService,
    private readonly moderation: ContentModerationService,
    @Optional() private readonly realtime?: RealtimeGateway,
    @Optional() private readonly tasks?: TasksService,
  ) {}

  private directKey(a: string, b: string) {
    return [a, b].sort().join(':');
  }

  /** Enforce the recipient's persisted DM privacy policy on every entry/send. */
  private async assertDirectMessageAllowed(senderId: string, recipientId: string) {
    const profile = await this.profilesRepo.findOne({ where: { userId: recipientId } });
    if (!profile) return; // Preserve access for legacy accounts without a profile row.
    const audience = String(profile.dmAudience || 'everyone').toLowerCase();
    if (profile.dmEnabled === false || audience === 'nobody') {
      throw new ForbiddenException({
        code: 'DM_PRIVACY_CLOSED',
        message: 'هذا المستخدم لا يستقبل رسائل خاصة حالياً',
      });
    }
    if (audience === 'everyone') return;

    const follows = await this.dataSource.query(
      'SELECT 1 FROM follows WHERE "followerId" = $1 AND "followingId" = $2 LIMIT 1',
      [senderId, recipientId],
    );
    if (audience === 'followers') {
      if (Array.isArray(follows) && follows.length) return;
      throw new ForbiddenException({ code: 'DM_PRIVACY_FOLLOWERS', message: 'يمكن لمتابعي هذا المستخدم فقط مراسلته' });
    }
    if (audience === 'friends') {
      const friendship = await this.dataSource.query(
        'SELECT 1 FROM social_requests WHERE type = $3 AND status = $4 AND (("fromUserId" = $1 AND "toUserId" = $2) OR ("fromUserId" = $2 AND "toUserId" = $1)) LIMIT 1',
        [senderId, recipientId, SocialRequestType.FRIEND, SocialRequestStatus.ACCEPTED],
      );
      if (Array.isArray(friendship) && friendship.length && Array.isArray(follows) && follows.length) return;
      throw new ForbiddenException({ code: 'DM_PRIVACY_FRIENDS', message: 'يمكن للأصدقاء الذين تتابعهم فقط مراسلة هذا المستخدم' });
    }
  }

  private async paidMessageConfig() {
    const keys = [
      'paid_messages.enabled', 'paid_messages.free_count', 'paid_messages.price_coins',
      'paid_messages.discount_percent', 'paid_messages.one_way_discount_percent',
      'paid_messages.price_step_coins', 'paid_messages.price_step_every',
      'paid_messages.price_max_coins', 'paid_messages.daily_limit',
      'paid_messages.refund_window_minutes', 'paid_messages.refund_window_hours',
    ];
    const rows = await this.settingsRepo.find({ where: keys.map((key) => ({ key })) });
    const values = new Map(rows.map((row) => [row.key, row.value]));
    const num = (key: string, fallback: number) => {
      const raw = values.get(key);
      const parsed = raw == null || raw === '' ? fallback : Number(raw);
      return Number.isFinite(parsed) ? parsed : fallback;
    };
    const enabled = ['true', '1', 'yes', 'on'].includes(String(values.get('paid_messages.enabled') || '').toLowerCase());
    const freeCount = Math.min(1000, Math.max(0, Math.floor(num('paid_messages.free_count', 3))));
    const basePrice = Math.min(10_000_000, Math.max(0, Math.floor(num('paid_messages.price_coins', 10))));
    const discountPercent = Math.min(100, Math.max(0, num('paid_messages.one_way_discount_percent', num('paid_messages.discount_percent', 25))));
    const priceStepCoins = Math.min(10_000_000, Math.max(0, Math.floor(num('paid_messages.price_step_coins', 0))));
    const priceStepEvery = Math.min(100000, Math.max(1, Math.floor(num('paid_messages.price_step_every', 10))));
    const maxPriceCoins = Math.min(10_000_000, Math.max(basePrice, Math.floor(num('paid_messages.price_max_coins', 10_000_000))));
    const dailyLimit = Math.min(100000, Math.max(0, Math.floor(num('paid_messages.daily_limit', 0))));
    const refundMinutesRaw = values.get('paid_messages.refund_window_minutes');
    const legacyHoursRaw = values.get('paid_messages.refund_window_hours');
    const refundWindowMinutes = refundMinutesRaw != null
      ? Math.min(10080, Math.max(0, num('paid_messages.refund_window_minutes', 30)))
      : legacyHoursRaw != null
        ? Math.min(10080, Math.max(0, num('paid_messages.refund_window_hours', 0) * 60))
        : 30;
    return { enabled, freeCount, basePrice, discountPercent, priceStepCoins, priceStepEvery, maxPriceCoins, dailyLimit, refundWindowMinutes };
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

  private async displayRankMapForUsers(userIds: string[]) {
    const ids = [...new Set((userIds || []).filter(Boolean))];
    const result = new Map<string, { customDisplayRankName: string | null; customDisplayRankImageUrl: string | null; customDisplayRankAnimated: boolean }>();
    if (!ids.length) return result;
    const rows = await this.dataSource.query(
      `SELECT DISTINCT ON (ub."userId")
              ub."userId" AS "userId", b.name AS "name",
              b."imageUrl" AS "imageUrl", b."isAnimated" AS "isAnimated"
         FROM user_badges ub JOIN badges b ON b.id = ub."badgeId"
        WHERE ub."userId" = ANY($1::uuid[])
          AND b.category = 'display_rank' AND b."isActive" = true
          AND (ub."expiresAt" IS NULL OR ub."expiresAt" > NOW())
        ORDER BY ub."userId", ub."grantedAt" DESC, b."sortOrder" ASC`,
      [ids],
    );
    for (const row of rows || []) result.set(String(row.userId), {
      customDisplayRankName: row.name ? String(row.name) : null,
      customDisplayRankImageUrl: row.imageUrl ? String(row.imageUrl) : null,
      customDisplayRankAnimated: row.isAnimated === true || row.isAnimated === 'true',
    });
    return result;
  }

  private mapMessage(message: ChatMessage, vipLevel = 0, displayRank?: any) {
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
            customDisplayRankName: displayRank?.customDisplayRankName ?? null,
            customDisplayRankImageUrl: displayRank?.customDisplayRankImageUrl ?? null,
            customDisplayRankAnimated: displayRank?.customDisplayRankAnimated === true,
            hostBadgeUrl: profile?.hostBadgeUrl ?? null,
            vipBadgeUrl: profile?.vipBadgeUrl ?? null,
            roomCardUrl: profile?.roomCardUrl ?? null,
            level: Number(sender.level || 1),
            vipLevel: Number(vipLevel || sender._vipLevel || 0),
            genderVerified: !!sender.genderVerified,
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
    await this.assertDirectMessageAllowed(userId, dto.peerId);

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
    displayRanks?: Map<string, any>,
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
        lastMessage = this.mapMessage(msg, senderVip, displayRanks?.get(msg.senderId));
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
            customDisplayRankName: displayRanks?.get(peerPart.user.id)?.customDisplayRankName ?? null,
            customDisplayRankImageUrl: displayRanks?.get(peerPart.user.id)?.customDisplayRankImageUrl ?? null,
            customDisplayRankAnimated: displayRanks?.get(peerPart.user.id)?.customDisplayRankAnimated === true,
            hostBadgeUrl: (peerPart.user as any).profile?.hostBadgeUrl ?? null,
            vipBadgeUrl: (peerPart.user as any).profile?.vipBadgeUrl ?? null,
            roomCardUrl: (peerPart.user as any).profile?.roomCardUrl ?? null,
            level: Number((peerPart.user as any).level || 1),
            vipLevel: peerVip,
            ...this.economyStats((peerPart.user as any).profile),
            lastSeenAt: (peerPart.user as any).updatedAt || null,
            showOnlineStatus: showPeerOnline,
            isOnline: peerOnline,
            genderVerified: !!(peerPart.user as any).genderVerified,
          }
        : null,
      myParticipant: my,
    };
  }

  async searchConversations(userId: string, rawQuery: string, query: PaginationDto) {
    const term = String(rawQuery || '').trim().slice(0, 120);
    if (term.length < 2) return paginate([], 0, query.page || 1, query.limit || 20);
    const needle = `%${term.toLocaleLowerCase()}%`;
    const qb = this.partRepo
      .createQueryBuilder('p')
      .innerJoinAndSelect('p.conversation', 'c')
      .leftJoinAndSelect('c.participants', 'parts')
      .leftJoinAndSelect('parts.user', 'u')
      .leftJoinAndSelect('u.profile', 'up')
      .leftJoin('c.participants', 'peerPart', 'peerPart.userId <> :userId', { userId })
      .leftJoin('peerPart.user', 'peer')
      .leftJoin('peer.profile', 'peerProfile')
      .leftJoin('c.messages', 'matchedMessage', 'matchedMessage.isUnsent = false')
      .where('p.userId = :userId', { userId })
      .andWhere('p.isDeleted = false')
      .andWhere('p.isArchived = false')
      .andWhere(
        "(LOWER(COALESCE(c.title, '')) LIKE :needle OR LOWER(COALESCE(peer.username, '')) LIKE :needle OR LOWER(COALESCE("peerProfile"."displayName", '')) LIKE :needle OR LOWER(CAST(peer.id AS text)) LIKE :needle OR LOWER(COALESCE(matchedMessage.content, '')) LIKE :needle)",
        { needle },
      )
      .distinct(true)
      .orderBy('p.isPinned', 'DESC')
      .addOrderBy('c.lastMessageAt', 'DESC', 'NULLS LAST')
      .skip(query.skip)
      .take(Math.min(Math.max(query.limit || 20, 1), 50));

    const [parts, total] = await qb.getManyAndCount();
    const peerIds: string[] = [];
    for (const part of parts) {
      for (const peer of part.conversation?.participants || []) {
        if (peer.userId) peerIds.push(peer.userId);
      }
    }
    const [vipByUser, displayRanks] = await Promise.all([
      this.vipMapForUsers(peerIds), this.displayRankMapForUsers(peerIds),
    ]);
    const items = await Promise.all(
      parts.map((p) => this.decorateConversation(p.conversation, userId, vipByUser, displayRanks)),
    );
    return paginate(items, total, query.page || 1, Math.min(Math.max(query.limit || 20, 1), 50));
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
    const [vipByUser, displayRanks] = await Promise.all([
      this.vipMapForUsers(peerIds), this.displayRankMapForUsers(peerIds),
    ]);
    const items = await Promise.all(
      parts.map((p) => this.decorateConversation(p.conversation, userId, vipByUser, displayRanks)),
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
    const [vipByUser, displayRanks] = await Promise.all([
      this.vipMapForUsers(peerIds), this.displayRankMapForUsers(peerIds),
    ]);
    return this.decorateConversation(conv, userId, vipByUser, displayRanks);
  }

  async send(conversationId: string, senderId: string, dto: SendMessageDto) {
    await this.getConversation(conversationId, senderId);
    const dmConversation = await this.convRepo.findOne({ where: { id: conversationId } });
    if (dmConversation?.type === ConversationType.DIRECT) {
      const participants = await this.partRepo.find({ where: { conversationId } });
      const recipient = participants.find((participant) => participant.userId !== senderId);
      if (recipient?.userId) await this.assertDirectMessageAllowed(senderId, recipient.userId);
    }
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

    if (content && String(content).trim()) {
      await this.moderation.assertCleanText(content);
    }

    // Gift messages themselves satisfy the gate — only block first text/media.
    if (type !== MessageType.GIFT && type !== MessageType.SYSTEM) {
      await this.assertDmGiftGate(conversationId, senderId, type);
    }

    const paidConfig = await this.paidMessageConfig();

    // Serialize sends per conversation so concurrent messages cannot both
    // observe the same free-message quota and bypass the paid-message charge.
    const message = await this.dataSource.transaction(async (manager) => {
      const conversation = await manager.findOne(ChatConversation, {
        where: { id: conversationId },
        lock: { mode: 'pessimistic_write' },
      });
      if (!conversation) throw new NotFoundException('Conversation not found');

      const billable = type !== MessageType.GIFT && type !== MessageType.SYSTEM;
      // The free allowance belongs to the sender account, not each conversation.
      // The dashboard configures the free allowance per direct conversation.
      // Gift/system messages do not consume the allowance.
      const priorBillableMessages = billable
        ? await manager.getRepository(ChatMessage)
            .createQueryBuilder('m')
            .where('m.senderId = :senderId', { senderId })
            .andWhere('m.conversationId = :conversationId', { conversationId })
            .andWhere('m.type NOT IN (:...excludedTypes)', { excludedTypes: [MessageType.GIFT, MessageType.SYSTEM] })
            .getCount()
        : 0;
      const paidCount = billable
        ? await manager.count(WalletTransaction, { where: { userId: senderId, referenceType: 'paid_message' } })
        : 0;
      const todayStart = new Date();
      todayStart.setHours(0, 0, 0, 0);
      const paidToday = billable && paidConfig.dailyLimit > 0
        ? await manager.getRepository(WalletTransaction).createQueryBuilder('tx')
            .where('tx.userId = :senderId', { senderId })
            .andWhere('tx.referenceType = :referenceType', { referenceType: 'paid_message' })
            .andWhere('tx.createdAt >= :todayStart', { todayStart })
            .getCount()
        : 0;
      if (paidConfig.enabled && billable && paidConfig.dailyLimit > 0 && paidToday >= paidConfig.dailyLimit) {
        throw new BadRequestException('وصلت إلى الحد اليومي للرسائل المدفوعة');
      }
      const peers = billable
        ? await manager.find(ChatParticipant, { where: { conversationId } })
        : [];
      const receiver = peers.find((participant) => participant.userId !== senderId);
      const senderFollows = receiver
        ? !!(await manager.findOne(Follow, { where: { followerId: senderId, followingId: receiver.userId } }))
        : false;
      const receiverFollows = receiver
        ? !!(await manager.findOne(Follow, { where: { followerId: receiver.userId, followingId: senderId } }))
        : false;
      const mutualFollow = senderFollows && receiverFollows;
      const oneWayFollow = senderFollows || receiverFollows;
      const tierPrice = Math.min(
        paidConfig.maxPriceCoins,
        paidConfig.basePrice + Math.floor(paidCount / paidConfig.priceStepEvery) * paidConfig.priceStepCoins,
      );
      // Enforce the paid-message setting after the configured free quota even for mutual follows.
      // The one-way follow discount is applied below; mutual follows pay the normal configured price.
      const isPaidMessage =
        paidConfig.enabled && billable &&
        priorBillableMessages >= paidConfig.freeCount && tierPrice > 0;
      const effectivePrice = oneWayFollow
        ? Math.round(tierPrice * (1 - paidConfig.discountPercent / 100))
        : tierPrice;
      const paidCoins = isPaidMessage ? Math.max(0, effectivePrice) : 0;

      const saved = await manager.save(
        manager.create(ChatMessage, {
          conversationId,
          senderId,
          type,
          content,
          media,
          replyToId: dto.replyToId || null,
          forwardedFromId,
        }),
      );

      if (paidCoins > 0) {
        const wallet = await manager.findOne(Wallet, {
          where: { userId: senderId },
          lock: { mode: 'pessimistic_write' },
        });
        if (!wallet || Number(wallet.coins || 0) < paidCoins) {
          throw new BadRequestException('تحتاج إلى ' + paidCoins + ' عملة لإرسال هذه الرسالة المدفوعة');
        }
        wallet.coins = Number(wallet.coins || 0) - paidCoins;
        await manager.save(wallet);
        await manager.save(
          manager.create(WalletTransaction, {
            userId: senderId,
            type: TransactionType.EXCHANGE,
            currency: CurrencyType.COINS,
            amount: -paidCoins,
            balanceAfter: Number(wallet.coins),
            referenceType: 'paid_message',
            referenceId: saved.id,
            description: 'رسالة مدفوعة',
            metadata: {
              conversationId,
              freeMessagesCount: paidConfig.freeCount,
              basePriceCoins: paidConfig.basePrice,
              chargedCoins: paidCoins,
              paidMessageNumber: paidCount + 1,
              discountPercent: oneWayFollow ? paidConfig.discountPercent : 0,
              followStatus: mutualFollow ? 'mutual' : oneWayFollow ? 'one_way' : 'none',
              refundWindowMinutes: paidConfig.refundWindowMinutes,
            },
          }),
        );
      }

      await manager.update(ChatConversation, conversationId, {
        lastMessageId: saved.id,
        lastMessageAt: saved.createdAt,
      });
      return saved;
    });

    const full = await this.msgRepo.findOne({
      where: { id: message.id },
      relations: ['sender', 'sender.profile', 'replyTo'],
    });
    const [senderVipMap, senderRanks] = await Promise.all([this.vipMapForUsers([senderId]), this.displayRankMapForUsers([senderId])]);
    const mapped: any = this.mapMessage(full || message, senderVipMap.get(senderId) ?? 0, senderRanks.get(senderId));
    const paidTransaction = await this.dataSource.getRepository(WalletTransaction).findOne({
      where: { userId: senderId, referenceType: 'paid_message', referenceId: message.id },
    });
    if (paidTransaction) {
      const paidMetadata = (paidTransaction.metadata || {}) as Record<string, unknown>;
      mapped.paidCoins = Math.abs(Number(paidTransaction.amount) || 0);
      mapped.paidStatus = 'pending';
      mapped.replyDeadlineAt = new Date(new Date(message.createdAt).getTime() + Math.max(0, Number(paidMetadata.refundWindowMinutes ?? 30)) * 60_000).toISOString();
    }

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
    const senderIds = items.map((m) => m.senderId);
    const [vipByUser, displayRanks] = await Promise.all([
      this.vipMapForUsers(senderIds), this.displayRankMapForUsers(senderIds),
    ]);
    const readAt = new Date();
    await this.partRepo.update(
      { conversationId, userId },
      { unreadCount: 0, lastReadAt: readAt },
    );
    await this.emitReadReceipt(conversationId, userId, readAt);
    const peers = await this.partRepo.find({ where: { conversationId } });
    const peerPart = peers.find((p) => p.userId !== userId);
    const mappedItems: any[] = items.map((m) => this.mapMessage(m, vipByUser.get(m.senderId) ?? 0, displayRanks.get(m.senderId)));
    const paidIds = items.map((m) => m.id);
    if (paidIds.length) {
      const [paidRows, refundRows] = await Promise.all([
        this.dataSource.getRepository(WalletTransaction).createQueryBuilder('tx')
          .where('tx.referenceType = :type', { type: 'paid_message' })
          .andWhere('tx.referenceId IN (:...ids)', { ids: paidIds }).getMany(),
        this.dataSource.getRepository(WalletTransaction).createQueryBuilder('tx')
          .where('tx.referenceType = :type', { type: 'paid_message_refund' })
          .andWhere('tx.referenceId IN (:...ids)', { ids: paidIds }).getMany(),
      ]);
      const paidById = new Map(paidRows.map((tx) => [tx.referenceId, tx]));
      const refunded = new Set(refundRows.map((tx) => tx.referenceId));
      for (let i = 0; i < items.length; i++) {
        const message = items[i];
        const tx = paidById.get(message.id);
        if (!tx) continue;
        const meta = (tx.metadata || {}) as Record<string, unknown>;
        const windowMinutes = Math.max(0, Number(meta.refundWindowMinutes ?? 30));
        const deadline = new Date(new Date(message.createdAt).getTime() + windowMinutes * 60_000);
        const peer = peers.find((p) => p.userId !== message.senderId);
        let replied = false;
        if (peer) {
          replied = await this.msgRepo.createQueryBuilder('reply')
            .where('reply.conversationId = :conversationId', { conversationId })
            .andWhere('reply.senderId = :peerId', { peerId: peer.userId })
            .andWhere('reply.createdAt > :createdAt', { createdAt: message.createdAt })
            .andWhere('reply.createdAt <= :deadline', { deadline })
            .andWhere('reply.type NOT IN (:...excludedTypes)', { excludedTypes: [MessageType.GIFT, MessageType.SYSTEM] })
            .getExists();
        }
        mappedItems[i].paidCoins = Math.abs(Number(tx.amount) || 0);
        mappedItems[i].replyDeadlineAt = deadline.toISOString();
        mappedItems[i].paidStatus = refunded.has(message.id) ? 'refunded'
          : replied ? 'replied' : Date.now() < deadline.getTime() ? 'pending' : 'expired';
      }
    }
    const pageResult = paginate(mappedItems, total, page, limit);
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
    // Reader's list badge must clear immediately (no full refetch needed).
    this.realtime?.emitToUser(readerId, 'chat:conversation:updated', {
      conversationId,
      unreadCount: 0,
      at: lastReadAt.toISOString(),
    });
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
    const [senderVipMap, senderRanks] = await Promise.all([this.vipMapForUsers([userId]), this.displayRankMapForUsers([userId])]);
    const mapped = this.mapMessage({ ...saved, sender: msg.sender }, senderVipMap.get(userId) ?? 0, senderRanks.get(userId));
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

  /** Automatically refund paid messages that received no reply before the configured deadline. */
  private async processExpiredPaidMessageRefunds() {
    try {
      const now = Date.now();
      const candidates = await this.dataSource.getRepository(WalletTransaction)
        .createQueryBuilder('tx')
        .where('tx.referenceType = :referenceType', { referenceType: 'paid_message' })
        .andWhere('tx.amount < 0')
        .andWhere('tx.createdAt < :cutoff', { cutoff: new Date(now - 60_000) })
        .orderBy('tx.createdAt', 'ASC')
        .take(200)
        .getMany();

      for (const candidate of candidates) {
        try {
          await this.dataSource.transaction(async (manager) => {
            // Lock the original debit so overlapping app instances cannot refund it twice.
            const paidTx = await manager.findOne(WalletTransaction, {
              where: { id: candidate.id },
              lock: { mode: 'pessimistic_write' },
            });
            if (!paidTx || paidTx.referenceType !== 'paid_message' || Number(paidTx.amount) >= 0 || !paidTx.referenceId) return;
            const paidMessageId = paidTx.referenceId;
            const alreadyRefunded = await manager.findOne(WalletTransaction, {
              where: { userId: paidTx.userId, referenceType: 'paid_message_refund', referenceId: paidMessageId },
            });
            if (alreadyRefunded) return;

            const message = await manager.findOne(ChatMessage, { where: { id: paidMessageId } });
            if (!message) return;
            const metadata = (paidTx.metadata || {}) as Record<string, unknown>;
            const configuredMinutes = metadata.refundWindowMinutes !== undefined
              ? Number(metadata.refundWindowMinutes)
              : metadata.refundWindowHours !== undefined
                ? Number(metadata.refundWindowHours) * 60
                : 30;
            const windowMinutes = Math.min(10080, Math.max(0, Number.isFinite(configuredMinutes) ? configuredMinutes : 30));
            if (windowMinutes <= 0) return;
            const deadline = new Date(new Date(message.createdAt).getTime() + windowMinutes * 60_000);
            if (now < deadline.getTime()) return;

            const participants = await manager.find(ChatParticipant, { where: { conversationId: message.conversationId } });
            const receiver = participants.find((participant) => participant.userId !== message.senderId);
            if (!receiver) return;
            const reply = await manager.getRepository(ChatMessage)
              .createQueryBuilder('reply')
              .where('reply.conversationId = :conversationId', { conversationId: message.conversationId })
              .andWhere('reply.senderId = :receiverId', { receiverId: receiver.userId })
              .andWhere('reply.createdAt > :messageCreatedAt', { messageCreatedAt: message.createdAt })
              .andWhere('reply.createdAt <= :deadline', { deadline })
              .andWhere('reply.type NOT IN (:...excludedTypes)', { excludedTypes: [MessageType.SYSTEM, MessageType.GIFT] })
              .getOne();
            if (reply) return;

            const refundCoins = Math.abs(Number(paidTx.amount));
            if (!Number.isFinite(refundCoins) || refundCoins <= 0) return;
            const wallet = await manager.findOne(Wallet, {
              where: { userId: paidTx.userId },
              lock: { mode: 'pessimistic_write' },
            });
            if (!wallet) return;
            wallet.coins = Number(wallet.coins || 0) + refundCoins;
            await manager.save(wallet);
            await manager.save(manager.create(WalletTransaction, {
              userId: paidTx.userId,
              type: TransactionType.REFUND,
              currency: CurrencyType.COINS,
              amount: refundCoins,
              balanceAfter: Number(wallet.coins),
              referenceType: 'paid_message_refund',
              referenceId: message.id,
              description: 'استرداد تلقائي لرسالة لم يتم الرد عليها',
              metadata: { paidTransactionId: paidTx.id, refundWindowMinutes: windowMinutes, reason: 'no_reply_before_deadline' },
            }));
          });
        } catch (error) {
          // A single malformed row must not stop refunds for other messages.
          console.error('[paid-messages] automatic refund failed', candidate.id, error);
        }
      }
    } catch (error) {
      console.error('[paid-messages] refund sweep failed', error);
    }
  }

  private async refundPaidMessageIfEligible(
    manager: any,
    message: ChatMessage,
    userId: string,
  ) {
    if (message.senderId !== userId) return 0;

    const paidTx = await manager.findOne(WalletTransaction, {
      where: {
        userId,
        referenceType: 'paid_message',
        referenceId: message.id,
      },
    });
    if (!paidTx || Number(paidTx.amount) >= 0) return 0;

    const metadata = (paidTx.metadata || {}) as any;
    const refundWindowMinutes = Math.max(
      0,
      metadata.refundWindowMinutes !== undefined
        ? Number(metadata.refundWindowMinutes) || 0
        : (Number(metadata.refundWindowHours) || 0) * 60,
    );
    if (refundWindowMinutes <= 0) return 0;

    const createdAt = new Date(message.createdAt).getTime();
    const ageMs = Date.now() - createdAt;
    if (ageMs < 0 || ageMs > refundWindowMinutes * 60 * 1000) return 0;

    const refundReferenceType = 'paid_message_refund';
    const existingRefund = await manager.findOne(WalletTransaction, {
      where: {
        userId,
        referenceType: refundReferenceType,
        referenceId: message.id,
      },
    });
    if (existingRefund) return 0;

    const refundCoins = Math.abs(Number(paidTx.amount));
    if (!refundCoins) return 0;

    const wallet = await manager.findOne(Wallet, {
      where: { userId },
      lock: { mode: 'pessimistic_write' },
    });
    if (!wallet) return 0;

    wallet.coins = Number(wallet.coins || 0) + refundCoins;
    await manager.save(wallet);
    await manager.save(
      manager.create(WalletTransaction, {
        userId,
        type: TransactionType.REFUND,
        currency: CurrencyType.COINS,
        amount: refundCoins,
        balanceAfter: Number(wallet.coins),
        referenceType: refundReferenceType,
        referenceId: message.id,
        description: 'استرداد رسالة مدفوعة',
        metadata: {
          paidTransactionId: paidTx.id,
          refundWindowMinutes,
        },
      }),
    );
    return refundCoins;
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

      await this.refundPaidMessageIfEligible(em, msg, userId);

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
        const ranks = await this.displayRankMapForUsers([current.senderId]);
        lastMessage = this.mapMessage(current, vip, ranks.get(current.senderId));
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
