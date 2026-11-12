import { BadRequestException, Injectable, NotFoundException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { DataSource, IsNull, LessThanOrEqual, MoreThan, Repository } from 'typeorm';
import { Gift, GiftType } from '../../database/entities/gift.entity';
import { CustomGiftGrant } from '../../database/entities/custom-gift-grant.entity';
import { User } from '../../database/entities/user.entity';
import { NotificationsService } from '../notifications/notifications.service';
import { NotificationType } from '../../database/entities/notification.entity';

const MEDIA_EXT: Record<string, 'image' | 'video' | 'gif'> = {
  jpg: 'image', jpeg: 'image', png: 'image', webp: 'image',
  mp4: 'video', webm: 'video', mov: 'video', '3gp': 'video',
  gif: 'gif',
};

@Injectable()
export class CustomGiftsService {
  constructor(
    @InjectRepository(CustomGiftGrant) private readonly grants: Repository<CustomGiftGrant>,
    @InjectRepository(Gift) private readonly gifts: Repository<Gift>,
    @InjectRepository(User) private readonly users: Repository<User>,
    private readonly dataSource: DataSource,
    private readonly notifications: NotificationsService,
  ) {}

  private normalizeMedia(url: string, declared?: string) {
    const value = String(url || '').trim();
    const match = value.match(/^\/uploads\/([a-zA-Z0-9._-]+)$/);
    if (!match || match[1].includes('..')) throw new BadRequestException('ارفع ملف الهدية من جهازك أولاً');
    const ext = match[1].split('.').pop()?.toLowerCase() || '';
    const detected = MEDIA_EXT[ext];
    if (!detected || (declared && declared !== detected)) throw new BadRequestException('نوع ملف الهدية غير مدعوم أو لا يطابق الملف');
    return { url: value, mediaType: detected };
  }

  async lookupRecipient(publicId: string) {
    const key = String(publicId || '').trim();
    if (!key) throw new BadRequestException('أدخل ID المستخدم');
    const user = await this.users.findOne({
      where: [{ publicId: key }, ...( /^[0-9a-f-]{36}$/i.test(key) ? [{ id: key } as any] : [])],
      select: ['id', 'publicId', 'username', 'displayName', 'avatarUrl', 'status'],
    });
    if (!user || user.status !== 'active') throw new NotFoundException('المستخدم غير موجود أو غير نشط');
    return { id: user.id, publicId: user.publicId, username: user.username, displayName: user.displayName, avatarUrl: user.avatarUrl };
  }

  async grant(dto: { recipientPublicId: string; name: string; mediaUrl: string; mediaType?: string; durationDays?: number | null; coinPrice: number; diamondValue: number }, actorId: string) {
    const recipient = await this.lookupRecipient(dto.recipientPublicId);
    const name = String(dto.name || '').trim().slice(0, 64);
    if (!name) throw new BadRequestException('اسم الهدية مطلوب');
    const media = this.normalizeMedia(dto.mediaUrl, dto.mediaType);
    const coinPrice = Math.max(1, Math.floor(Number(dto.coinPrice) || 0));
    const diamondValue = Math.max(0, Math.floor(Number(dto.diamondValue) || 0));
    if (!Number.isSafeInteger(coinPrice) || !Number.isSafeInteger(diamondValue)) throw new BadRequestException('سعر الهدية غير صحيح');
    const rawDays = dto.durationDays === null || dto.durationDays === undefined ? 0 : Number(dto.durationDays);
    if (!Number.isInteger(rawDays) || rawDays < 0 || rawDays > 3650) throw new BadRequestException('مدة الصلاحية غير صحيحة');
    const startsAt = new Date();
    const expiresAt = rawDays === 0 ? null : new Date(startsAt.getTime() + rawDays * 86400000);

    const saved = await this.dataSource.transaction(async manager => {
      const gift = await manager.save(Gift, manager.create(Gift, {
        name,
        description: 'هدية مخصصة من إدارة JEHO',
        iconUrl: media.url,
        animationUrl: media.url,
        coinPrice,
        diamondValue,
        type: GiftType.NORMAL,
        category: 'custom',
        isActive: true,
        sortOrder: 0,
      }));
      const grant = await manager.save(CustomGiftGrant, manager.create(CustomGiftGrant, {
        userId: recipient.id,
        giftId: gift.id,
        grantedBy: actorId,
        mediaType: media.mediaType,
        mediaUrl: media.url,
        durationDays: rawDays || null,
        startsAt,
        expiresAt,
        revokedAt: null,
      }));
      return { gift, grant };
    });

    const startText = startsAt.toLocaleString('en-GB', { timeZone: 'UTC' }) + ' UTC';
    const endText = expiresAt ? expiresAt.toLocaleString('en-GB', { timeZone: 'UTC' }) + ' UTC' : 'مفتوحة';
    const durationText = rawDays === 0 ? 'مفتوحة' : `${rawDays} يوم`;
    await this.notifications.create({
      userId: recipient.id,
      type: NotificationType.CHAT,
      title: '🎁 هدية مخصصة من إدارة JEHO',
      body: `🎁 مبروك! تمت إهداؤك هدية مخصصة من إدارة التطبيق.\n\nاسم الهدية: ${name}\nنوع الهدية: ${media.mediaType === 'video' ? 'فيديو' : media.mediaType === 'gif' ? 'GIF' : 'صورة'}\nتاريخ البدء: ${startText}\nتاريخ الانتهاء: ${endText}\nمدة الصلاحية: ${durationText}\nالسعر: ${coinPrice} كوينز · ${diamondValue} ألماسات\nمقدمة الهدية: إدارة التطبيق\n\n🎉 يمكنك الآن الدخول إلى قسم الهدايا المخصصة واستخدام هديتك وإرسالها داخل الرومات.`,
      data: { adminMessage: true, source: 'admin', customGift: true, customGiftId: saved.grant.id, giftId: saved.gift.id, mediaType: media.mediaType, startsAt: startsAt.toISOString(), expiresAt: expiresAt?.toISOString() || null, durationDays: rawDays || null, mediaUrl: media.url },
      sendPush: true,
    });
    return { success: true, recipient, gift: saved.gift, grant: saved.grant };
  }

  async listMine(userId: string) {
    const now = new Date();
    const rows = await this.grants.find({
      where: [
        { userId, revokedAt: IsNull(), startsAt: LessThanOrEqual(now), expiresAt: IsNull() },
        { userId, revokedAt: IsNull(), startsAt: LessThanOrEqual(now), expiresAt: MoreThan(now) },
      ],
      relations: ['gift'],
      order: { createdAt: 'DESC' },
    });
    return rows.filter(row => row.gift?.isActive).map(row => ({
      ...row.gift,
      // Use the grant's uploaded media as the authoritative art for this recipient.
      iconUrl: row.mediaUrl,
      animationUrl: row.mediaUrl,
      category: 'custom',
      // Keep the admin-configured price and diamond payout from the Gift row.
      coinPrice: Number(row.gift.coinPrice) || 0,
      diamondValue: Number(row.gift.diamondValue) || 0,
      mediaType: row.mediaType,
      startsAt: row.startsAt,
      expiresAt: row.expiresAt,
      durationDays: row.durationDays,
      customGrantId: row.id,
    }));
  }

  async listAdmin() {
    return this.grants.find({ relations: ['user', 'gift'], order: { createdAt: 'DESC' }, take: 100 });
  }

  async assertOwnedAndActive(userId: string, giftId: string) {
    const row = await this.grants.findOne({
      where: [
        { userId, giftId, revokedAt: IsNull(), expiresAt: IsNull() },
        { userId, giftId, revokedAt: IsNull(), expiresAt: MoreThan(new Date()) },
      ],
    });
    if (!row) throw new NotFoundException('هذه الهدية غير متاحة أو انتهت صلاحيتها');
    return row;
  }
}
