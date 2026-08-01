import {
  BadRequestException,
  ForbiddenException,
  Injectable,
  NotFoundException,
  OnModuleInit,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { In, Repository } from 'typeorm';
import {
  PlazaEvent,
  PlazaEventStatus,
} from '../../database/entities/plaza-event.entity';
import { PlazaEventSubscription } from '../../database/entities/plaza-event-subscription.entity';
import { Room } from '../../database/entities/room.entity';
import { User } from '../../database/entities/user.entity';
import { Agency } from '../../database/entities/agency.entity';

@Injectable()
export class PlazaEventsService implements OnModuleInit {
  constructor(
    @InjectRepository(PlazaEvent)
    private readonly eventsRepo: Repository<PlazaEvent>,
    @InjectRepository(PlazaEventSubscription)
    private readonly subsRepo: Repository<PlazaEventSubscription>,
    @InjectRepository(Room) private readonly roomsRepo: Repository<Room>,
    @InjectRepository(User) private readonly usersRepo: Repository<User>,
    @InjectRepository(Agency) private readonly agenciesRepo: Repository<Agency>,
  ) {}

  async onModuleInit() {
    await this.refreshStatuses();
  }

  /** Sync upcoming/live/ended from wall clock. */
  async refreshStatuses() {
    const now = new Date();
    await this.eventsRepo
      .createQueryBuilder()
      .update(PlazaEvent)
      .set({ status: 'ended' })
      .where('status != :ended', { ended: 'ended' })
      .andWhere('"endAt" < :now', { now })
      .execute();
    await this.eventsRepo
      .createQueryBuilder()
      .update(PlazaEvent)
      .set({ status: 'live' })
      .where('status = :upcoming', { upcoming: 'upcoming' })
      .andWhere('"startAt" <= :now', { now })
      .andWhere('"endAt" >= :now', { now })
      .execute();
  }

  async list(userId: string, tab: 'square' | 'mine' = 'square') {
    await this.refreshStatuses();
    let events: PlazaEvent[] = [];

    if (tab === 'mine') {
      // Hosted events only — manage create/edit/delete from profile «فعالياتي».
      events = await this.eventsRepo.find({
        where: { hostId: userId },
        order: { startAt: 'DESC' },
        take: 100,
      });
    } else {
      events = await this.eventsRepo.find({
        where: { isPublic: true },
        order: { startAt: 'ASC' },
        take: 80,
      });
      events = events.filter((e) => e.status !== 'ended');
    }

    return { items: await this.mapMany(events, userId) };
  }

  async get(id: string, userId: string) {
    await this.refreshStatuses();
    const event = await this.eventsRepo.findOne({ where: { id } });
    if (!event) throw new NotFoundException('الفعالية غير موجودة');
    return this.mapOne(event, userId);
  }

  async create(
    userId: string,
    body: {
      title?: string;
      description?: string;
      coverUrl?: string;
      tag?: string;
      startAt?: string;
      endAt?: string;
      roomId?: string;
      isPublic?: boolean;
    },
  ) {
    const title = (body.title || '').trim();
    if (!title) throw new BadRequestException('عنوان الفعالية مطلوب');
    const startAt = body.startAt ? new Date(body.startAt) : new Date();
    const endAt = body.endAt
      ? new Date(body.endAt)
      : new Date(startAt.getTime() + 2 * 60 * 60 * 1000);
    if (Number.isNaN(startAt.getTime()) || Number.isNaN(endAt.getTime())) {
      throw new BadRequestException('وقت غير صالح');
    }
    if (endAt <= startAt) {
      throw new BadRequestException('وقت الانتهاء يجب أن يكون بعد البداية');
    }

    let roomId: string | null = body.roomId || null;
    if (roomId) {
      const room = await this.roomsRepo.findOne({ where: { id: roomId } });
      if (!room) throw new NotFoundException('الغرفة غير موجودة');
      if (room.hostId !== userId) {
        throw new ForbiddenException('يمكنك إنشاء فعالية لغرفتك فقط');
      }
    }

    const now = new Date();
    let status: PlazaEventStatus = 'upcoming';
    if (endAt < now) status = 'ended';
    else if (startAt <= now) status = 'live';

    const saved = await this.eventsRepo.save(
      this.eventsRepo.create({
        title: title.slice(0, 128),
        description: body.description?.trim()?.slice(0, 512) || null,
        coverUrl: body.coverUrl?.trim() || null,
        tag: (body.tag || 'party').slice(0, 32),
        status,
        startAt,
        endAt,
        hostId: userId,
        roomId,
        subscribersCount: 0,
        isPublic: body.isPublic !== false,
      }),
    );
    return this.mapOne(saved, userId);
  }

  async subscribe(userId: string, eventId: string) {
    const event = await this.eventsRepo.findOne({ where: { id: eventId } });
    if (!event) throw new NotFoundException('الفعالية غير موجودة');
    if (event.status === 'ended') {
      throw new BadRequestException('انتهت هذه الفعالية');
    }
    const existing = await this.subsRepo.findOne({
      where: { eventId, userId },
    });
    if (existing) return this.mapOne(event, userId);

    await this.subsRepo.save(this.subsRepo.create({ eventId, userId }));
    event.subscribersCount = Math.max(0, event.subscribersCount) + 1;
    await this.eventsRepo.save(event);
    return this.mapOne(event, userId);
  }

  async unsubscribe(userId: string, eventId: string) {
    const event = await this.eventsRepo.findOne({ where: { id: eventId } });
    if (!event) throw new NotFoundException('الفعالية غير موجودة');
    const existing = await this.subsRepo.findOne({
      where: { eventId, userId },
    });
    if (existing) {
      await this.subsRepo.remove(existing);
      event.subscribersCount = Math.max(0, event.subscribersCount - 1);
      await this.eventsRepo.save(event);
    }
    return this.mapOne(event, userId);
  }

  async updateMine(userId: string, id: string, body: Record<string, any>) {
    const event = await this.eventsRepo.findOne({ where: { id } });
    if (!event) throw new NotFoundException('الفعالية غير موجودة');
    if (event.hostId !== userId) {
      throw new ForbiddenException('يمكنك تعديل فعالياتك فقط');
    }
    if (body.title != null) {
      const title = String(body.title).trim();
      if (!title) throw new BadRequestException('عنوان الفعالية مطلوب');
      event.title = title.slice(0, 128);
    }
    if (body.description !== undefined) {
      event.description = body.description
        ? String(body.description).trim().slice(0, 512)
        : null;
    }
    if (body.coverUrl !== undefined) {
      event.coverUrl = body.coverUrl ? String(body.coverUrl) : null;
    }
    if (body.tag != null) event.tag = String(body.tag).slice(0, 32);
    if (body.startAt) event.startAt = new Date(body.startAt);
    if (body.endAt) event.endAt = new Date(body.endAt);
    if (event.endAt <= event.startAt) {
      throw new BadRequestException('وقت الانتهاء يجب أن يكون بعد البداية');
    }
    if (body.roomId !== undefined) {
      const roomId = body.roomId || null;
      if (roomId) {
        const room = await this.roomsRepo.findOne({ where: { id: roomId } });
        if (!room) throw new NotFoundException('الغرفة غير موجودة');
        if (room.hostId !== userId) {
          throw new ForbiddenException('يمكنك ربط غرفتك فقط');
        }
      }
      event.roomId = roomId;
    }
    if (body.isPublic !== undefined) event.isPublic = !!body.isPublic;
    await this.eventsRepo.save(event);
    await this.refreshStatuses();
    return this.mapOne(
      await this.eventsRepo.findOneOrFail({ where: { id } }),
      userId,
    );
  }

  async deleteMine(userId: string, id: string) {
    const event = await this.eventsRepo.findOne({ where: { id } });
    if (!event) throw new NotFoundException('الفعالية غير موجودة');
    if (event.hostId !== userId) {
      throw new ForbiddenException('يمكنك حذف فعالياتك فقط');
    }
    await this.subsRepo.delete({ eventId: id });
    await this.eventsRepo.remove(event);
    return { ok: true };
  }

  async adminList() {
    await this.refreshStatuses();
    const events = await this.eventsRepo.find({
      order: { startAt: 'DESC' },
      take: 200,
    });
    return { items: await this.mapMany(events, null) };
  }

  async adminCreate(body: Record<string, any>) {
    if (!body?.hostId) throw new BadRequestException('hostId مطلوب');
    return this.create(String(body.hostId), body as any);
  }

  async adminUpdate(id: string, body: Record<string, any>) {
    const event = await this.eventsRepo.findOne({ where: { id } });
    if (!event) throw new NotFoundException('الفعالية غير موجودة');
    if (body.title != null) event.title = String(body.title).slice(0, 128);
    if (body.description !== undefined) {
      event.description = body.description
        ? String(body.description).slice(0, 512)
        : null;
    }
    if (body.coverUrl !== undefined) {
      event.coverUrl = body.coverUrl ? String(body.coverUrl) : null;
    }
    if (body.tag != null) event.tag = String(body.tag).slice(0, 32);
    if (body.startAt) event.startAt = new Date(body.startAt);
    if (body.endAt) event.endAt = new Date(body.endAt);
    if (body.roomId !== undefined) event.roomId = body.roomId || null;
    if (body.isPublic !== undefined) event.isPublic = !!body.isPublic;
    if (body.status) event.status = body.status;
    await this.eventsRepo.save(event);
    await this.refreshStatuses();
    return this.mapOne(await this.eventsRepo.findOneOrFail({ where: { id } }), null);
  }

  async adminDelete(id: string) {
    const event = await this.eventsRepo.findOne({ where: { id } });
    if (!event) throw new NotFoundException('الفعالية غير موجودة');
    await this.subsRepo.delete({ eventId: id });
    await this.eventsRepo.remove(event);
    return { ok: true };
  }

  private async mapMany(events: PlazaEvent[], userId: string | null) {
    if (!events.length) return [];
    const hostIds = [...new Set(events.map((e) => e.hostId))];
    const roomIds = [
      ...new Set(events.map((e) => e.roomId).filter(Boolean) as string[]),
    ];
    const hosts = await this.usersRepo.find({ where: { id: In(hostIds) } });
    const hostMap = new Map(hosts.map((h) => [h.id, h]));
    const rooms =
      roomIds.length > 0
        ? await this.roomsRepo.find({ where: { id: In(roomIds) } })
        : [];
    const roomMap = new Map(rooms.map((r) => [r.id, r]));
    const agencyIds = [
      ...new Set(
        rooms
          .map((r) => r.agencyId)
          .filter((id): id is string => !!id && id.trim().length > 0),
      ),
    ];
    const agencies =
      agencyIds.length > 0
        ? await this.agenciesRepo.find({ where: { id: In(agencyIds) } })
        : [];
    const agencyMap = new Map(agencies.map((a) => [a.id, a]));

    let subSet = new Set<string>();
    if (userId) {
      const subs = await this.subsRepo.find({
        where: { userId, eventId: In(events.map((e) => e.id)) },
      });
      subSet = new Set(subs.map((s) => s.eventId));
    }

    return events.map((e) => {
      const room = roomMap.get(e.roomId || '');
      const agency = room?.agencyId ? agencyMap.get(room.agencyId) : undefined;
      return this.toDto(e, hostMap.get(e.hostId), room, agency, userId, subSet);
    });
  }

  private async mapOne(event: PlazaEvent, userId: string | null) {
    const [mapped] = await this.mapMany([event], userId);
    return mapped;
  }

  private toDto(
    e: PlazaEvent,
    host: User | undefined,
    room: Room | undefined,
    agency: Agency | undefined,
    userId: string | null,
    subSet: Set<string>,
  ) {
    const isAgency =
      !!room?.agencyId ||
      (room?.roomKind && String(room.roomKind).toLowerCase() === 'agency');
    return {
      id: e.id,
      title: e.title,
      description: e.description,
      coverUrl: e.coverUrl || room?.coverUrl || host?.avatarUrl || null,
      tag: e.tag,
      status: e.status,
      startAt: e.startAt.toISOString(),
      endAt: e.endAt.toISOString(),
      hostId: e.hostId,
      roomId: e.roomId,
      roomTitle: room?.title || null,
      roomCoverUrl: room?.coverUrl || null,
      roomKind: room?.roomKind || null,
      agencyId: room?.agencyId || null,
      agencyName: agency?.name || null,
      agencyLogoUrl: agency?.logoUrl || null,
      isAgencyRoom: !!isAgency,
      subscribersCount: e.subscribersCount,
      isPublic: e.isPublic,
      subscribed: userId ? subSet.has(e.id) : false,
      isHost: userId ? e.hostId === userId : false,
      host: host
        ? {
            id: host.id,
            username: host.username,
            displayName: host.displayName,
            avatarUrl: host.avatarUrl,
            country: (host as any).country || null,
          }
        : null,
    };
  }
}
