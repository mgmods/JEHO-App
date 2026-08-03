import {
  Injectable,
  NotFoundException,
  ForbiddenException,
  ConflictException,
  BadRequestException,
  GoneException,
  Inject,
  forwardRef,
  OnModuleInit,
  Logger,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { DataSource, Repository } from 'typeorm';
import { v4 as uuidv4 } from 'uuid';
import { Agency, AgencyStatus } from '../../database/entities/agency.entity';
import {
  AgencyMember,
  AgencyRole,
  AgencyMemberStatus,
} from '../../database/entities/agency-member.entity';
import { GiftSend } from '../../database/entities/gift-send.entity';
import { Wallet } from '../../database/entities/wallet.entity';
import {
  WalletTransaction,
  TransactionType,
  CurrencyType,
} from '../../database/entities/wallet-transaction.entity';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { Room, RoomKind, RoomStatus, RoomType } from '../../database/entities/room.entity';
import { RoomSeat, SeatStatus } from '../../database/entities/room-seat.entity';
import { RoomModerator, ModeratorRole } from '../../database/entities/room-moderator.entity';
import { paginate, PaginationDto } from '../../common/dto/pagination.dto';
import { CreateAgencyDto, AddMemberDto, UpdateMemberRoleDto } from './dto/agencies.dto';
import { RoomsService } from '../rooms/rooms.service';
import { CreateRoomDto } from '../rooms/dto/rooms.dto';
import { User } from '../../database/entities/user.entity';
import {
  AgencyApplication,
  AgencyApplicationStatus,
} from '../../database/entities/agency-application.entity';
import { SubmitAgencyApplicationDto } from './dto/agencies.dto';
import { NotificationsService } from '../notifications/notifications.service';
import { NotificationType } from '../../database/entities/notification.entity';
import {
  agencyJoinNotification,
  generateActivationCode,
  isAgencyNotificationStyle,
  normalizeActivationCode,
} from './agency-activation';
import { AGENCY_CREATE } from '../../common/pricing-catalog';
import { purgeRoomReferencesBeforeDelete } from '../../common/room-delete-sql';

const DEFAULT_CREATE_PRICE = AGENCY_CREATE.defaultCoins;
const DEFAULT_COMMISSION = 15;
const DEFAULT_PLATFORM_CUT = 30;
const DEFAULT_AGENCY_SEAT_COUNT = 11;

@Injectable()
export class AgenciesService implements OnModuleInit {
  private readonly logger = new Logger(AgenciesService.name);
  constructor(
    @InjectRepository(Agency) private readonly agenciesRepo: Repository<Agency>,
    @InjectRepository(AgencyMember) private readonly membersRepo: Repository<AgencyMember>,
    @InjectRepository(AgencyApplication)
    private readonly applicationsRepo: Repository<AgencyApplication>,
    @InjectRepository(GiftSend) private readonly giftSendsRepo: Repository<GiftSend>,
    @InjectRepository(Wallet) private readonly walletsRepo: Repository<Wallet>,
    @InjectRepository(AppSetting) private readonly settingsRepo: Repository<AppSetting>,
    @InjectRepository(Room) private readonly roomsRepo: Repository<Room>,
    @InjectRepository(RoomSeat) private readonly seatsRepo: Repository<RoomSeat>,
    @InjectRepository(RoomModerator) private readonly modsRepo: Repository<RoomModerator>,
    @InjectRepository(User) private readonly usersRepo: Repository<User>,
    private readonly dataSource: DataSource,
    @Inject(forwardRef(() => RoomsService))
    private readonly roomsService: RoomsService,
    private readonly notificationsService: NotificationsService,
  ) {}

  async onModuleInit() {
    try {
      await this.dataSource.query(`
        ALTER TABLE agency_applications
          ADD COLUMN IF NOT EXISTS "paidCoins" BIGINT NOT NULL DEFAULT 0,
          ADD COLUMN IF NOT EXISTS "paymentReferenceId" VARCHAR(128),
          ADD COLUMN IF NOT EXISTS "paymentRefunded" BOOLEAN NOT NULL DEFAULT false
      `);
    } catch (err) {
      this.logger.warn(`ensure agency application payment columns: ${(err as Error).message}`);
    }
    try {
      const existing = await this.settingsRepo.findOne({
        where: { key: AGENCY_CREATE.settingKey },
      });
      if (!existing) {
        await this.settingsRepo.save(
          this.settingsRepo.create({
            key: AGENCY_CREATE.settingKey,
            value: String(AGENCY_CREATE.defaultCoins),
            description: 'سعر فتح الوكالة بالعملات (مدفوع — يحدده الأدمن)',
          }),
        );
      }
    } catch (err) {
      this.logger.warn(`ensure agency create price setting: ${(err as Error).message}`);
    }
    try {
      await this.ensureSetting(
        'agency_default_commission_percent',
        String(DEFAULT_COMMISSION),
        'حصة صاحب الوكالة من هدايا الأعضاء %',
      );
      await this.ensureSetting(
        'agency_platform_cut_percent',
        String(DEFAULT_PLATFORM_CUT),
        'حصة المنصة من هدايا الوكالة %',
      );
      await this.ensureSetting(
        'agency_host_share_percent',
        String(100 - DEFAULT_COMMISSION - DEFAULT_PLATFORM_CUT),
        'حصة المضيفة من هدايا الوكالة % (المتبقي بعد المنصة وصاحب الوكالة)',
      );
      await this.ensureSetting(
        'agency_auto_approve_after_payment',
        'false',
        'موافقة تلقائية على طلب الوكالة بعد الدفع (true/false)',
      );
      // Raise legacy platform cut (20/25) to safer 30% house default.
      const cut = await this.settingsRepo.findOne({
        where: { key: 'agency_platform_cut_percent' },
      });
      if (cut) {
        const n = Number(String(cut.value).trim());
        if (n === 20 || n === 25) {
          cut.value = String(DEFAULT_PLATFORM_CUT);
          cut.description = 'حصة المنصة من هدايا الوكالة %';
          await this.settingsRepo.save(cut);
        }
      }
      // Soft-migrate default agency commission 20 → 15 when still on legacy default.
      const defComm = await this.settingsRepo.findOne({
        where: { key: 'agency_default_commission_percent' },
      });
      if (defComm && String(defComm.value).trim() === '20') {
        defComm.value = String(DEFAULT_COMMISSION);
        defComm.description = 'حصة صاحب الوكالة من هدايا الأعضاء %';
        await this.settingsRepo.save(defComm);
      }
    } catch (err) {
      this.logger.warn(`ensure agency commission settings: ${(err as Error).message}`);
    }
  }

  private async ensureSetting(key: string, value: string, description: string) {
    const existing = await this.settingsRepo.findOne({ where: { key } });
    if (!existing) {
      await this.settingsRepo.save(
        this.settingsRepo.create({ key, value, description }),
      );
    }
  }

  /** Generate a unique activation code (retries on rare collisions). */
  async allocateActivationCode(): Promise<string> {
    for (let attempt = 0; attempt < 12; attempt++) {
      const code = generateActivationCode();
      const exists = await this.agenciesRepo.findOne({
        where: { activationCode: code },
        select: ['id'],
      });
      if (!exists) return code;
    }
    throw new BadRequestException('تعذّر إنشاء كود التفعيل — أعد المحاولة');
  }

  private stripSecretFields<T extends { activationCode?: string | null }>(agency: T): Omit<T, 'activationCode'> {
    const { activationCode: _code, ...rest } = agency;
    return rest;
  }

  async getSettingNumber(key: string, fallback: number): Promise<number> {
    const row = await this.settingsRepo.findOne({ where: { key } });
    if (!row?.value) return fallback;
    const n = Number(row.value);
    return Number.isFinite(n) ? n : fallback;
  }

  async pricing() {
    const raw = await this.getSettingNumber(
      AGENCY_CREATE.settingKey,
      DEFAULT_CREATE_PRICE,
    );
    const createPriceCoins = Math.max(
      AGENCY_CREATE.minCoins,
      Math.min(AGENCY_CREATE.maxCoins, Math.floor(raw)),
    );
    const defaultCommissionPercent = Math.min(
      50,
      Math.max(0, await this.getSettingNumber('agency_default_commission_percent', DEFAULT_COMMISSION)),
    );
    const platformCutPercent = Math.min(
      50,
      Math.max(0, await this.getSettingNumber('agency_platform_cut_percent', DEFAULT_PLATFORM_CUT)),
    );
    const hostSharePercent = Math.max(0, 100 - defaultCommissionPercent - platformCutPercent);
    const platformRevenueDiamonds = await this.getSettingNumber(
      'platform_gift_revenue_diamonds',
      0,
    );
    const autoRaw = String(
      (await this.settingsRepo.findOne({ where: { key: 'agency_auto_approve_after_payment' } }))
        ?.value || 'false',
    )
      .trim()
      .toLowerCase();
    const autoApproveAfterPayment =
      autoRaw === 'true' || autoRaw === '1' || autoRaw === 'yes';
    return {
      createPriceCoins,
      currency: 'coins',
      isPaid: createPriceCoins > 0,
      defaultCommissionPercent,
      platformCutPercent,
      hostSharePercent,
      autoApproveAfterPayment,
      platformRevenueDiamonds,
    };
  }

  /** Owned or managed agency for the current user (with earnings when allowed). */
  async mine(userId: string) {
    // Prefer active membership; otherwise surface a pending join request so the
    // app can show "awaiting approval" instead of an empty join form.
    let membership = await this.membersRepo.findOne({
      where: {
        userId,
        status: AgencyMemberStatus.ACTIVE,
        isActive: true,
      },
      relations: ['agency'],
      order: { joinedAt: 'DESC' },
    });
    if (!membership) {
      membership = await this.membersRepo.findOne({
        where: { userId, status: AgencyMemberStatus.PENDING },
        relations: ['agency'],
        order: { joinedAt: 'DESC' },
      });
    }
    const application = await this.applicationsRepo.findOne({
      where: { applicantId: userId },
      order: { createdAt: 'DESC' },
    });
    const agency = membership?.agency || null;
    const visibleApplication =
      application?.status === AgencyApplicationStatus.APPROVED &&
      (!agency || !application.agencyId || application.agencyId !== agency.id)
        ? null
        : application;
    const activeMembership =
      !!membership &&
      membership.status === AgencyMemberStatus.ACTIVE &&
      membership.isActive;
    const canHostRoom =
      activeMembership &&
      agency?.status === AgencyStatus.ACTIVE &&
      [AgencyRole.OWNER, AgencyRole.MANAGER, AgencyRole.HOST].includes(
        membership!.role,
      );
    const room = canHostRoom
      ? await this.roomsRepo.findOne({
          where: { agencyId: membership!.agencyId, hostId: userId },
        })
      : null;
    const canViewEarnings =
      canHostRoom &&
      (membership!.role === AgencyRole.OWNER || membership!.role === AgencyRole.MANAGER);
    const canManage =
      activeMembership &&
      (membership!.role === AgencyRole.OWNER || membership!.role === AgencyRole.MANAGER);
    if (canManage && agency && !agency.activationCode) {
      agency.activationCode = await this.allocateActivationCode();
      await this.agenciesRepo.save(agency);
    }
    const agencyPayload = agency
      ? canManage
        ? agency
        : this.stripSecretFields(agency)
      : null;
    return {
      application: visibleApplication,
      agency: agencyPayload,
      agencyStatus: agency?.status || null,
      membershipStatus: membership?.status || null,
      role: membership?.role || null,
      canHostRoom,
      roomId: room?.id || null,
      earnings: canViewEarnings ? await this.earnings(agency!.id, userId) : null,
    };
  }

  async create(ownerId: string, dto: CreateAgencyDto) {
    throw new GoneException({
      code: 'AGENCY_APPLICATION_REQUIRED',
      message:
        'فتح الوكالة مدفوع عبر طلب احترافي — استخدم POST /agencies/applications',
      proposedName: dto.name?.trim() || null,
    });
  }

  async purchase(ownerId: string, dto: CreateAgencyDto) {
    return this.create(ownerId, dto);
  }

  async submitApplication(applicantId: string, dto: SubmitAgencyApplicationDto) {
    if (!dto.termsAccepted) {
      throw new BadRequestException('Terms must be accepted');
    }
    const applicant = await this.usersRepo.findOne({
      where: { id: applicantId },
      relations: ['profile'],
    });
    if (!applicant) throw new NotFoundException('Applicant not found');
    const accountCountry = applicant.profile?.country?.trim();
    const [owned, activeMembership, conflictingName, pending] = await Promise.all([
      this.agenciesRepo.findOne({ where: { ownerId: applicantId } }),
      this.membersRepo.findOne({
        where: { userId: applicantId, isActive: true, status: AgencyMemberStatus.ACTIVE },
      }),
      this.agenciesRepo
        .createQueryBuilder('agency')
        .where('LOWER(agency.name) = LOWER(:name)', { name: dto.proposedName })
        .getOne(),
      this.applicationsRepo.findOne({
        where: [
          { applicantId, status: AgencyApplicationStatus.PENDING },
          { applicantId, status: AgencyApplicationStatus.CHANGES_REQUESTED },
        ],
        order: { createdAt: 'DESC' },
      }),
    ]);
    if (owned || activeMembership) {
      throw new ConflictException('You already own or belong to an agency');
    }
    if (conflictingName) throw new ConflictException('Agency name is already in use');
    if (pending?.status === AgencyApplicationStatus.PENDING) {
      throw new ConflictException('You already have a pending agency application');
    }

    const pricing = await this.pricing();
    const priceCoins = Math.max(0, Number(pricing.createPriceCoins) || 0);
    const alreadyPaid =
      pending?.status === AgencyApplicationStatus.CHANGES_REQUESTED &&
      Number(pending.paidCoins || 0) > 0 &&
      !pending.paymentRefunded;

    return this.dataSource.transaction(async (manager) => {
      let paidCoins = alreadyPaid ? Number(pending!.paidCoins || 0) : 0;
      let paymentReferenceId = alreadyPaid ? pending!.paymentReferenceId : null;

      if (!alreadyPaid && priceCoins > 0) {
        let wallet = await manager.findOne(Wallet, {
          where: { userId: applicantId },
          lock: { mode: 'pessimistic_write' },
        });
        if (!wallet) {
          wallet = await manager.save(
            manager.create(Wallet, {
              userId: applicantId,
              coins: 0,
              diamonds: 0,
              traderDiamonds: 0,
              gamePoints: 0,
            }),
          );
          wallet = await manager.findOne(Wallet, {
            where: { userId: applicantId },
            lock: { mode: 'pessimistic_write' },
          });
        }
        if (!wallet || Number(wallet.coins || 0) < priceCoins) {
          throw new BadRequestException(
            `رصيد العملات غير كافٍ لفتح الوكالة (المطلوب ${priceCoins.toLocaleString()} عملة)`,
          );
        }
        wallet.coins = Number(wallet.coins || 0) - priceCoins;
        await manager.save(wallet);
        paymentReferenceId = `agency_create:${applicantId}:${Date.now()}`;
        paidCoins = priceCoins;
        await manager.save(
          manager.create(WalletTransaction, {
            userId: applicantId,
            type: TransactionType.ADMIN_ADJUST,
            currency: CurrencyType.COINS,
            amount: -priceCoins,
            balanceAfter: Number(wallet.coins || 0),
            referenceType: 'agency_create',
            referenceId: paymentReferenceId,
            description: `رسوم فتح وكالة «${dto.proposedName}»`,
            metadata: { proposedName: dto.proposedName, priceCoins },
          }),
        );
      }

      const values: Partial<AgencyApplication> = {
        ...dto,
        applicantId,
        country: accountCountry || dto.country,
        socialLink: dto.socialLink || null,
        documentUrls: dto.documentUrls || [],
        status: AgencyApplicationStatus.PENDING,
        reviewNote: null,
        reviewedById: null,
        reviewedAt: null,
        paidCoins,
        paymentReferenceId,
        paymentRefunded: false,
      };

      if (pending?.status === AgencyApplicationStatus.CHANGES_REQUESTED) {
        Object.assign(pending, values);
        const saved = await manager.save(pending);
        return saved;
      }
      return manager.save(manager.create(AgencyApplication, values));
    }).then(async (saved) => {
      try {
        const pricingAfter = await this.pricing();
        if (pricingAfter.autoApproveAfterPayment && saved?.id) {
          await this.autoApprovePaidApplication(saved.id, applicantId);
          const refreshed = await this.applicationsRepo.findOne({ where: { id: saved.id } });
          return refreshed || saved;
        }
      } catch (err) {
        this.logger.warn(
          `agency auto-approve failed for ${saved?.id}: ${(err as Error).message}`,
        );
      }
      return saved;
    });
  }

  /**
   * System/admin-style approve after payment when auto-approve is enabled.
   * Leaves the application pending if anything conflicts.
   */
  async autoApprovePaidApplication(applicationId: string, reviewerId: string) {
    const result = await this.dataSource.transaction(async (manager) => {
      const application = await manager.findOne(AgencyApplication, {
        where: { id: applicationId },
        lock: { mode: 'pessimistic_write' },
      });
      if (!application) throw new NotFoundException('Agency application not found');
      if (application.status === AgencyApplicationStatus.APPROVED) {
        return { application, agency: null as Agency | null };
      }
      if (application.status !== AgencyApplicationStatus.PENDING) {
        throw new ConflictException('Only pending applications can be auto-approved');
      }

      const existingAgency = await manager
        .createQueryBuilder(Agency, 'agency')
        .where(
          'agency."ownerId" = :applicantId OR LOWER(agency.name) = LOWER(:name)',
          {
            applicantId: application.applicantId,
            name: application.proposedName,
          },
        )
        .getOne();
      const existingMembership = await manager.findOne(AgencyMember, {
        where: {
          userId: application.applicantId,
          isActive: true,
          status: AgencyMemberStatus.ACTIVE,
        },
      });
      if (existingAgency || existingMembership) {
        throw new ConflictException(
          'Applicant already owns or belongs to an agency, or name is taken',
        );
      }

      const commissionRow = await manager.findOne(AppSetting, {
        where: { key: 'agency_default_commission_percent' },
      });
      let activationCode = generateActivationCode();
      for (let attempt = 0; attempt < 12; attempt++) {
        const clash = await manager.findOne(Agency, {
          where: { activationCode },
          select: ['id'],
        });
        if (!clash) break;
        activationCode = generateActivationCode();
      }
      const agency = await manager.save(
        manager.create(Agency, {
          name: application.proposedName,
          description: application.description,
          ownerId: application.applicantId,
          status: AgencyStatus.ACTIVE,
          memberCount: 1,
          totalDiamonds: 0,
          commissionPercent: Number(commissionRow?.value || DEFAULT_COMMISSION),
          activationCode,
          notificationStyle: 'welcome',
        }),
      );
      await manager.save(
        manager.create(AgencyMember, {
          agencyId: agency.id,
          userId: application.applicantId,
          role: AgencyRole.OWNER,
          status: AgencyMemberStatus.ACTIVE,
          isActive: true,
        }),
      );
      const room = await manager.save(
        manager.create(Room, {
          title: agency.name,
          description: `${agency.name} — persistent agency voice room`,
          type: RoomType.VOICE,
          status: RoomStatus.CLOSED,
          hostId: agency.ownerId,
          activeHostId: null,
          seatCount: DEFAULT_AGENCY_SEAT_COUNT,
          viewerCount: 0,
          isPublic: true,
          roomKind: RoomKind.AGENCY,
          isPersistent: true,
          agencyId: agency.id,
          accessMode: 'free' as any,
          entryFeeCoins: 0,
          hasPassword: false,
          passwordHash: null,
          zegoRoomId: `agency_${uuidv4().replace(/-/g, '').slice(0, 16)}`,
        }),
      );
      const seats: RoomSeat[] = [];
      for (let index = 0; index < DEFAULT_AGENCY_SEAT_COUNT; index++) {
        seats.push(
          manager.create(RoomSeat, {
            roomId: room.id,
            seatIndex: index,
            isHostSeat: index === 0,
            status: SeatStatus.EMPTY,
            userId: null,
          }),
        );
      }
      await manager.save(seats);
      application.status = AgencyApplicationStatus.APPROVED;
      application.agencyId = agency.id;
      application.reviewNote = 'موافقة تلقائية بعد الدفع';
      application.reviewedById = reviewerId;
      application.reviewedAt = new Date();
      await manager.save(application);
      return { application, agency };
    });

    if (result.agency?.activationCode) {
      try {
        await this.notificationsService.create({
          userId: result.agency.ownerId,
          type: NotificationType.AGENCY,
          title: 'تم تفعيل وكالتك',
          body: `وكالتك «${result.agency.name}» نشطة تلقائياً بعد الدفع. كود التفعيل: ${result.agency.activationCode}`,
          data: {
            agencyId: result.agency.id,
            activationCode: result.agency.activationCode,
            officialNews: true,
            autoApproved: true,
          },
          sendPush: true,
        });
      } catch {
        /* ignore */
      }
    }
    return result;
  }

  /** Refund application fee on admin reject (idempotent). */
  async refundApplicationFee(applicationId: string) {
    return this.dataSource.transaction(async (manager) => {
      const application = await manager.findOne(AgencyApplication, {
        where: { id: applicationId },
        lock: { mode: 'pessimistic_write' },
      });
      if (!application) return { refunded: false };
      const paid = Math.max(0, Number(application.paidCoins || 0));
      if (!paid || application.paymentRefunded) return { refunded: false, paid };
      let wallet = await manager.findOne(Wallet, {
        where: { userId: application.applicantId },
        lock: { mode: 'pessimistic_write' },
      });
      if (!wallet) {
        wallet = await manager.save(
          manager.create(Wallet, {
            userId: application.applicantId,
            coins: 0,
            diamonds: 0,
            traderDiamonds: 0,
            gamePoints: 0,
          }),
        );
        wallet = await manager.findOne(Wallet, {
          where: { userId: application.applicantId },
          lock: { mode: 'pessimistic_write' },
        });
      }
      if (!wallet) return { refunded: false, paid };
      wallet.coins = Number(wallet.coins || 0) + paid;
      await manager.save(wallet);
      const refundRef = `agency_create_refund:${application.id}`;
      await manager.save(
        manager.create(WalletTransaction, {
          userId: application.applicantId,
          type: TransactionType.ADMIN_ADJUST,
          currency: CurrencyType.COINS,
          amount: paid,
          balanceAfter: Number(wallet.coins || 0),
          referenceType: 'agency_create_refund',
          referenceId: refundRef,
          description: `استرداد رسوم طلب وكالة «${application.proposedName}»`,
          metadata: {
            applicationId: application.id,
            originalPayment: application.paymentReferenceId,
          },
        }),
      );
      application.paymentRefunded = true;
      await manager.save(application);
      return { refunded: true, paid };
    });
  }

  async myApplications(applicantId: string) {
    const items = await this.applicationsRepo.find({
      where: { applicantId },
      order: { createdAt: 'DESC' },
    });
    return { items, total: items.length };
  }

  async list(query: PaginationDto) {
    const [items, total] = await this.agenciesRepo.findAndCount({
      where: { status: AgencyStatus.ACTIVE },
      relations: ['owner'],
      skip: query.skip,
      take: query.limit || 20,
      order: { totalDiamonds: 'DESC' },
    });
    const safe = items.map((a) => this.stripSecretFields(a));
    return paginate(safe, total, query.page || 1, query.limit || 20);
  }

  async get(id: string) {
    const agency = await this.agenciesRepo.findOne({
      where: { id },
      relations: ['owner', 'members', 'members.user'],
    });
    if (!agency) throw new NotFoundException('Agency not found');
    const publicUser = (user: User | null | undefined) =>
      user
        ? {
            id: user.id,
            publicId: user.publicId,
            username: user.username,
            displayName: user.displayName,
            avatarUrl: user.avatarUrl,
            level: user.level,
          }
        : null;
    const { owner, members, activationCode: _code, ...safeAgency } = agency;
    return {
      ...safeAgency,
      owner: publicUser(owner),
      members: (members || [])
        .filter((member) => member.status === AgencyMemberStatus.ACTIVE)
        .map((member) => ({
          id: member.id,
          agencyId: member.agencyId,
          userId: member.userId,
          role: member.role,
          status: member.status,
          isActive: member.isActive,
          joinedAt: member.joinedAt,
          user: publicUser(member.user),
        })),
    };
  }

  /** Join using the agency's private activation code (distinguishes agencies). */
  async joinByCode(userId: string, rawCode: string) {
    const code = normalizeActivationCode(rawCode);
    if (code.length < 4) {
      throw new BadRequestException('كود التفعيل غير صالح');
    }
    const agency = await this.agenciesRepo.findOne({
      where: { activationCode: code },
    });
    if (!agency) {
      throw new NotFoundException('كود التفعيل غير صحيح');
    }
    return this.joinSelf(agency.id, userId);
  }

  async updateSettings(
    agencyId: string,
    actorId: string,
    dto: { notificationStyle?: string },
  ) {
    await this.assertManager(agencyId, actorId);
    const agency = await this.agenciesRepo.findOne({ where: { id: agencyId } });
    if (!agency) throw new NotFoundException('Agency not found');
    if (dto.notificationStyle != null) {
      const style = String(dto.notificationStyle).trim().toLowerCase();
      if (!isAgencyNotificationStyle(style)) {
        throw new BadRequestException('نمط الإشعار غير مدعوم');
      }
      agency.notificationStyle = style;
    }
    await this.agenciesRepo.save(agency);
    return {
      id: agency.id,
      activationCode: agency.activationCode,
      notificationStyle: agency.notificationStyle,
    };
  }

  /**
   * Commission is set by platform admin (dashboard) only.
   * Agency owners cannot change their cut from the app.
   */
  async updateCommission(
    agencyId: string,
    actorId: string,
    dto: { commissionPercent: number },
  ) {
    void agencyId;
    void actorId;
    void dto;
    throw new ForbiddenException(
      'نسبة العمولة تُحدَّد من إدارة التطبيق فقط ولا يمكن تعديلها من الوكيل',
    );
  }

  /** Soft-suspend member: cannot host; can be restored later. */
  async suspendMember(agencyId: string, actorId: string, memberUserId: string) {
    await this.assertManager(agencyId, actorId);
    const targetUserId = await this.resolveUserRef(memberUserId);
    const member = await this.membersRepo.findOne({
      where: { agencyId, userId: targetUserId },
    });
    if (!member) throw new NotFoundException('Member not found');
    if (member.role === AgencyRole.OWNER) {
      throw new ForbiddenException('Cannot suspend owner');
    }
    if (!member.isActive && member.status === AgencyMemberStatus.ACTIVE) {
      return { suspended: true, already: true };
    }
    const wasActive =
      member.status === AgencyMemberStatus.ACTIVE && member.isActive;
    member.isActive = false;
    member.status = AgencyMemberStatus.ACTIVE;
    await this.membersRepo.save(member);
    await this.removeAgencyModerator(agencyId, targetUserId);
    await this.roomsService.closeAgencyHostRoom(agencyId, targetUserId);
    if (wasActive) {
      await this.agenciesRepo.decrement({ id: agencyId }, 'memberCount', 1);
    }
    try {
      const agency = await this.agenciesRepo.findOne({ where: { id: agencyId } });
      await this.notificationsService.create({
        userId: targetUserId,
        type: NotificationType.AGENCY,
        title: 'تم تعليق عضويتك',
        body: agency
          ? `تم تعليق عضويتك في وكالة «${agency.name}».`
          : 'تم تعليق عضويتك في الوكالة.',
        data: { agencyId, officialNews: true, action: 'member_suspend' },
        sendPush: true,
      });
    } catch {
      // ignore
    }
    return { suspended: true };
  }

  async unsuspendMember(agencyId: string, actorId: string, memberUserId: string) {
    await this.assertManager(agencyId, actorId);
    const targetUserId = await this.resolveUserRef(memberUserId);
    const member = await this.membersRepo.findOne({
      where: { agencyId, userId: targetUserId },
    });
    if (!member) throw new NotFoundException('Member not found');
    if (member.isActive && member.status === AgencyMemberStatus.ACTIVE) {
      return { unsuspended: true, already: true };
    }
    const other = await this.membersRepo.findOne({
      where: { userId: targetUserId, isActive: true, status: AgencyMemberStatus.ACTIVE },
    });
    if (other && other.agencyId !== agencyId) {
      throw new ConflictException('المستخدم منضم لوكالة أخرى نشطة');
    }
    const wasInactive = !member.isActive;
    member.isActive = true;
    member.status = AgencyMemberStatus.ACTIVE;
    await this.membersRepo.save(member);
    if (wasInactive) {
      await this.agenciesRepo.increment({ id: agencyId }, 'memberCount', 1);
    }
    if (member.role === AgencyRole.MANAGER) {
      const rooms = await this.roomsRepo.find({ where: { agencyId } });
      for (const room of rooms) await this.grantAgencyModerators(agencyId, room.id);
    }
    try {
      const agency = await this.agenciesRepo.findOne({ where: { id: agencyId } });
      await this.notificationsService.create({
        userId: targetUserId,
        type: NotificationType.AGENCY,
        title: 'تم رفع التعليق',
        body: agency
          ? `تم إعادة تفعيل عضويتك في وكالة «${agency.name}».`
          : 'تم إعادة تفعيل عضويتك في الوكالة.',
        data: { agencyId, officialNews: true, action: 'member_unsuspend' },
        sendPush: true,
      });
    } catch {
      // ignore
    }
    return { unsuspended: true };
  }

    async joinSelf(agencyId: string, userId: string) {
    const agency = await this.agenciesRepo.findOne({ where: { id: agencyId } });
    if (!agency) throw new NotFoundException('Agency not found');
    if (agency.status !== AgencyStatus.ACTIVE) {
      throw new BadRequestException('Agency is not accepting members');
    }
    const existing = await this.membersRepo.findOne({
      where: { agencyId, userId },
    });
    if (existing) {
      if (existing.status === AgencyMemberStatus.PENDING) {
        return { ...existing, pending: true, message: 'طلبك قيد المراجعة' };
      }
      if (existing.status === AgencyMemberStatus.ACTIVE && existing.isActive) {
        return { ...existing, pending: false, message: 'أنت عضو بالفعل' };
      }
      // Re-apply after reject
      existing.status = AgencyMemberStatus.PENDING;
      existing.isActive = false;
      existing.role = AgencyRole.HOST;
      await this.membersRepo.save(existing);
      await this.notifyManagersOfJoinRequest(agency, userId);
      return { ...existing, pending: true, message: 'تم إرسال طلب الانضمام' };
    }

    const other = await this.membersRepo.findOne({
      where: { userId, isActive: true, status: AgencyMemberStatus.ACTIVE },
    });
    if (other && other.agencyId !== agencyId) {
      throw new ConflictException('أنت منضم لوكالة أخرى — اتركها أولاً');
    }
    const otherPending = await this.membersRepo.findOne({
      where: { userId, status: AgencyMemberStatus.PENDING },
    });
    if (otherPending && otherPending.agencyId !== agencyId) {
      throw new ConflictException('لديك طلب انضمام معلّق لوكالة أخرى');
    }

    const member = await this.membersRepo.save(
      this.membersRepo.create({
        agencyId,
        userId,
        role: AgencyRole.HOST,
        status: AgencyMemberStatus.PENDING,
        isActive: false,
      }),
    );
    // Do not increment memberCount until approved
    await this.notifyManagersOfJoinRequest(agency, userId);
    return { ...member, pending: true, message: 'تم إرسال طلب الانضمام — بانتظار موافقة الإدارة' };
  }

  /** Inbox + FCM to agency owner/managers when someone requests to join. */
  private async notifyManagersOfJoinRequest(agency: Agency, applicantId: string) {
    try {
      const applicant = await this.usersRepo.findOne({ where: { id: applicantId } });
      const who =
        applicant?.displayName?.trim() ||
        applicant?.username?.trim() ||
        'مستخدم';
      const managers = await this.membersRepo.find({
        where: [
          {
            agencyId: agency.id,
            role: AgencyRole.OWNER,
            isActive: true,
            status: AgencyMemberStatus.ACTIVE,
          },
          {
            agencyId: agency.id,
            role: AgencyRole.MANAGER,
            isActive: true,
            status: AgencyMemberStatus.ACTIVE,
          },
        ],
      });
      const targets = new Set<string>();
      if (agency.ownerId) targets.add(agency.ownerId);
      for (const m of managers) {
        if (m.userId) targets.add(m.userId);
      }
      targets.delete(applicantId);

      const title = 'طلب انضمام للوكالة';
      const body = `${who} يطلب الانضمام إلى «${agency.name}».`;
      const data = {
        agencyId: agency.id,
        agencyName: agency.name,
        agencyCode: agency.activationCode || null,
        applicantId,
        action: 'join_request',
        officialNews: true,
      };
      await Promise.all([
        ...[...targets].map((userId) =>
          this.notificationsService
            .create({
              userId,
              type: NotificationType.AGENCY,
              title,
              body,
              data,
              sendPush: true,
            })
            .catch(() => null),
        ),
        // Applicant confirmation in Official News (read-only chat).
        this.notificationsService
          .create({
            userId: applicantId,
            type: NotificationType.AGENCY,
            title: 'تم إرسال طلب الانضمام',
            body: `طلبك للانضمام إلى «${agency.name}» قيد المراجعة.${
              agency.activationCode ? ` رمز الوكالة: ${agency.activationCode}` : ''
            }`,
            data: {
              agencyId: agency.id,
              agencyName: agency.name,
              agencyCode: agency.activationCode || null,
              action: 'join_request_sent',
              officialNews: true,
            },
            sendPush: true,
          })
          .catch(() => null),
      ]);
    } catch (err) {
      this.logger.warn(
        `join-request notify failed: ${(err as Error).message}`,
      );
    }
  }

  async listJoinRequests(agencyId: string, actorId: string) {
    await this.assertManager(agencyId, actorId);
    const items = await this.membersRepo.find({
      where: { agencyId, status: AgencyMemberStatus.PENDING },
      relations: ['user'],
      order: { joinedAt: 'DESC' },
    });
    return { items, total: items.length };
  }

  async approveJoin(agencyId: string, actorId: string, memberUserId: string) {
    await this.assertManager(agencyId, actorId);
    const member = await this.membersRepo.findOne({
      where: { agencyId, userId: memberUserId },
    });
    if (!member) throw new NotFoundException('الطلب غير موجود');
    if (member.status === AgencyMemberStatus.ACTIVE && member.isActive) {
      return member;
    }
    const other = await this.membersRepo.findOne({
      where: { userId: memberUserId, isActive: true, status: AgencyMemberStatus.ACTIVE },
    });
    if (other && other.agencyId !== agencyId) {
      throw new ConflictException('المستخدم منضم لوكالة أخرى');
    }
    const wasPending = member.status === AgencyMemberStatus.PENDING || !member.isActive;
    member.status = AgencyMemberStatus.ACTIVE;
    member.isActive = true;
    await this.membersRepo.save(member);
    if (wasPending) {
      await this.agenciesRepo.increment({ id: agencyId }, 'memberCount', 1);
    }
    const agency = await this.agenciesRepo.findOne({ where: { id: agencyId } });
    if (agency && wasPending) {
      const copy = agencyJoinNotification(agency.notificationStyle, agency.name);
      try {
        await this.notificationsService.create({
          userId: memberUserId,
          type: NotificationType.AGENCY,
          title: copy.title,
          body: copy.body,
          data: {
            agencyId: agency.id,
            agencyName: agency.name,
            notificationStyle: agency.notificationStyle,
            officialNews: true,
            action: 'join_approve',
          },
          sendPush: true,
        });
      } catch {
        // Approval must succeed even if push fails.
      }
    }
    return member;
  }

  async rejectJoin(agencyId: string, actorId: string, memberUserId: string) {
    await this.assertManager(agencyId, actorId);
    const member = await this.membersRepo.findOne({
      where: { agencyId, userId: memberUserId },
    });
    if (!member) throw new NotFoundException('الطلب غير موجود');
    if (member.role === AgencyRole.OWNER) {
      throw new BadRequestException('لا يمكن رفض المالك');
    }
    if (member.status === AgencyMemberStatus.ACTIVE && member.isActive) {
      throw new BadRequestException('العضو مقبول مسبقاً — استخدم الطرد');
    }
    member.status = AgencyMemberStatus.REJECTED;
    member.isActive = false;
    await this.membersRepo.save(member);
    try {
      const agency = await this.agenciesRepo.findOne({ where: { id: agencyId } });
      await this.notificationsService.create({
        userId: memberUserId,
        type: NotificationType.AGENCY,
        title: 'تم رفض طلب الانضمام',
        body: agency
          ? `تم رفض طلب انضمامك لوكالة «${agency.name}».`
          : 'تم رفض طلب انضمامك للوكالة.',
        data: { agencyId, officialNews: true, action: 'join_reject' },
        sendPush: true,
      });
    } catch {
      // ignore
    }
    return { ok: true };
  }

  async addMember(agencyId: string, actorId: string, dto: AddMemberDto) {
    await this.assertManager(agencyId, actorId);
    const targetUserId = await this.resolveUserRef(dto.userId);
    if (targetUserId === actorId) {
      throw new BadRequestException('لا يمكنك إضافة نفسك');
    }
    const role = dto.role || AgencyRole.HOST;
    const existing = await this.membersRepo.findOne({
      where: { agencyId, userId: targetUserId },
    });
    if (existing) throw new ConflictException('Already a member');

    const other = await this.membersRepo.findOne({
      where: { userId: targetUserId, isActive: true },
    });
    if (other && other.agencyId !== agencyId) {
      throw new ConflictException('المستخدم منضم لوكالة أخرى');
    }

    const member = await this.membersRepo.save(
      this.membersRepo.create({
        agencyId,
        userId: targetUserId,
        role,
        status: AgencyMemberStatus.ACTIVE,
        isActive: true,
      }),
    );
    await this.agenciesRepo.increment({ id: agencyId }, 'memberCount', 1);
    return member;
  }

  /** Accept UUID, numeric publicId, or username — same IDs users see in-app. */
  private async resolveUserRef(raw: string): Promise<string> {
    const q = (raw || '').trim();
    if (!q) throw new BadRequestException('معرّف المستخدم مطلوب');
    if (/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(q)) {
      const byId = await this.usersRepo.findOne({ where: { id: q } });
      if (!byId) throw new NotFoundException('المستخدم غير موجود');
      return byId.id;
    }
    const byPublic = await this.usersRepo.findOne({ where: { publicId: q } });
    if (byPublic) return byPublic.id;
    const byUsername = await this.usersRepo.findOne({ where: { username: q } });
    if (byUsername) return byUsername.id;
    throw new NotFoundException('المستخدم غير موجود — استخدم الـ ID الظاهر في الملف الشخصي');
  }

  async updateRole(
    agencyId: string,
    actorId: string,
    memberUserId: string,
    dto: UpdateMemberRoleDto,
  ) {
    await this.assertManager(agencyId, actorId);
    const targetUserId = await this.resolveUserRef(memberUserId);
    const member = await this.membersRepo.findOne({
      where: { agencyId, userId: targetUserId },
    });
    if (!member) throw new NotFoundException('Member not found');
    if (member.role === AgencyRole.OWNER) {
      throw new ForbiddenException('Cannot change owner role');
    }
    if (dto.role === AgencyRole.OWNER) {
      throw new ForbiddenException('Cannot assign owner role');
    }
    // Only the agency owner may promote/demote managers (admins).
    if (
      (dto.role === AgencyRole.MANAGER || member.role === AgencyRole.MANAGER) &&
      !(await this.isAgencyOwner(agencyId, actorId))
    ) {
      throw new ForbiddenException('صاحب الوكالة فقط يرفع أو يزيل أدمن الوكالة');
    }
    const wasHostCapable = [AgencyRole.OWNER, AgencyRole.MANAGER, AgencyRole.HOST].includes(
      member.role,
    );
    const willHostCapable = [AgencyRole.OWNER, AgencyRole.MANAGER, AgencyRole.HOST].includes(
      dto.role,
    );
    const wasManager = member.role === AgencyRole.MANAGER;
    member.role = dto.role;
    const saved = await this.membersRepo.save(member);
    if (wasManager && dto.role !== AgencyRole.MANAGER) {
      await this.removeAgencyModerator(agencyId, targetUserId);
    } else if (dto.role === AgencyRole.MANAGER) {
      const rooms = await this.roomsRepo.find({ where: { agencyId } });
      for (const room of rooms) await this.grantAgencyModerators(agencyId, room.id);
    }
    if (wasHostCapable && !willHostCapable) {
      await this.roomsService.closeAgencyHostRoom(agencyId, targetUserId);
    }
    return saved;
  }

  async leaveSelf(agencyId: string, userId: string) {
    const agency = await this.agenciesRepo.findOne({ where: { id: agencyId } });
    if (!agency) throw new NotFoundException('Agency not found');
    if (agency.ownerId === userId) {
      throw new ForbiddenException('صاحب الوكالة لا يستطيع المغادرة — انقل الملكية أو علّق الوكالة من الداشبورد');
    }
    const member = await this.membersRepo.findOne({
      where: [
        { agencyId, userId, isActive: true },
        { agencyId, userId, status: AgencyMemberStatus.PENDING },
      ],
    });
    if (!member) throw new NotFoundException('لست عضواً في هذه الوكالة');
    if (member.role === AgencyRole.OWNER) {
      throw new ForbiddenException('Cannot leave as owner');
    }
    const wasActive =
      member.status === AgencyMemberStatus.ACTIVE && member.isActive;
    await this.membersRepo.delete(member.id);
    if (!wasActive) {
      // Cancel pending join request — no room/moderator cleanup needed.
      return { left: true, agencyId, cancelledPending: true };
    }
    await this.removeAgencyModerator(agencyId, userId);
    await this.roomsService.closeAgencyHostRoom(agencyId, userId);
    if (Number(agency.memberCount || 0) > 0) {
      await this.agenciesRepo.decrement({ id: agencyId }, 'memberCount', 1);
    }
    return { left: true, agencyId };
  }

  async removeMember(agencyId: string, actorId: string, memberUserId: string) {
    await this.assertManager(agencyId, actorId);
    const targetUserId = await this.resolveUserRef(memberUserId);
    const member = await this.membersRepo.findOne({
      where: { agencyId, userId: targetUserId },
    });
    if (!member) throw new NotFoundException('Member not found');
    if (member.role === AgencyRole.OWNER) {
      throw new ForbiddenException('Cannot remove owner');
    }
    const wasActive =
      member.status === AgencyMemberStatus.ACTIVE && member.isActive;
    await this.membersRepo.delete(member.id);
    await this.removeAgencyModerator(agencyId, targetUserId);
    await this.roomsService.closeAgencyHostRoom(agencyId, targetUserId);
    if (wasActive) {
      await this.agenciesRepo.decrement({ id: agencyId }, 'memberCount', 1);
    }
    return { removed: true };
  }

  async stats(agencyId: string) {
    const agency = await this.get(agencyId);
    const memberIds = (agency.members || []).map((m) => m.userId);
    let received = 0;
    if (memberIds.length) {
      const raw = await this.giftSendsRepo
        .createQueryBuilder('g')
        .select('COALESCE(SUM(g.diamondsAwarded), 0)', 'total')
        .where('g.receiverId IN (:...ids)', { ids: memberIds })
        .getRawOne();
      received = Number(raw?.total || 0);
    }
    return {
      agencyId,
      name: agency.name,
      memberCount: agency.memberCount,
      totalDiamonds: Number(agency.totalDiamonds),
      giftsReceivedDiamonds: received,
      commissionPercent: Number(agency.commissionPercent),
    };
  }

  /** Detailed earnings for agency owner: commission txs + split summary */
  async earnings(agencyId: string, viewerId: string) {
    const agency = await this.agenciesRepo.findOne({ where: { id: agencyId } });
    if (!agency) throw new NotFoundException('Agency not found');
    if (agency.ownerId !== viewerId) {
      const member = await this.membersRepo.findOne({
        where: { agencyId, userId: viewerId, isActive: true },
      });
      if (!member || (member.role !== AgencyRole.OWNER && member.role !== AgencyRole.MANAGER)) {
        throw new ForbiddenException('Owner/manager only');
      }
    }

    const platformPct = await this.getSettingNumber('agency_platform_cut_percent', DEFAULT_PLATFORM_CUT);
    const commissionPct = Number(agency.commissionPercent) || DEFAULT_COMMISSION;
    const platformRevenueAll = await this.getSettingNumber('platform_gift_revenue_diamonds', 0);

    // Commission rows used to key referenceId=agencyId (broken unique constraint);
    // new rows use referenceId=giftSendId and metadata.agencyId.
    const commissionRows = await this.dataSource.getRepository(WalletTransaction).find({
      where: {
        userId: agency.ownerId,
        referenceType: 'agency_commission',
      },
      order: { createdAt: 'DESC' },
      take: 200,
    });
    const txs = commissionRows
      .filter((t) => {
        const metaAgency = t.metadata && typeof t.metadata === 'object'
          ? String((t.metadata as Record<string, unknown>).agencyId || '')
          : '';
        return t.referenceId === agencyId || metaAgency === agencyId;
      })
      .slice(0, 50);

    const ownerCommissionEarned = txs.reduce((s, t) => s + Number(t.amount || 0), 0);
    const stats = await this.stats(agencyId);
    const gross = Number(stats.giftsReceivedDiamonds || 0);
    const estimatedPlatform = Math.floor((gross * platformPct) / 100);
    const estimatedAgent = Math.floor((gross * Math.min(50, commissionPct)) / 100);
    const estimatedHost = Math.max(0, gross - estimatedAgent - estimatedPlatform);

    return {
      agencyId,
      name: agency.name,
      ownerId: agency.ownerId,
      commissionPercent: commissionPct,
      platformCutPercent: platformPct,
      hostSharePercent: Math.max(0, 100 - Math.min(50, commissionPct) - Math.min(50, platformPct)),
      totals: {
        grossGiftsDiamonds: gross,
        agencyTotalDiamonds: Number(agency.totalDiamonds || 0),
        ownerCommissionEarned,
        estimatedHostShare: estimatedHost,
        estimatedPlatformCut: estimatedPlatform,
        estimatedAgentShare: estimatedAgent,
        platformRevenueAllTime: platformRevenueAll,
      },
      recentCommission: txs.map((t) => ({
        id: t.id,
        diamonds: Number(t.amount || 0),
        description: t.description,
        createdAt: t.createdAt,
      })),
      explanation: {
        ar: `عند إرسال هدية لعضو في الوكالة: المنصة ${platformPct}%، صاحب الوكالة ${commissionPct}%، المضيفة ≈ ${Math.max(0, 100 - platformPct - commissionPct)}%. أرباح صاحب الوكالة تظهر هنا مفصّلة. وكيل الشحن منفصل ولا علاقة له بهذه النسب.`,
      },
    };
  }

  /** Owner distributes withdrawable diamonds from their wallet to an active agency member. */
  async distributeEarnings(
    agencyId: string,
    ownerId: string,
    toUserId: string,
    amount: number,
  ) {
    const diamonds = Math.floor(Number(amount) || 0);
    if (diamonds < 1) throw new BadRequestException('أدخل كمية ألماس صحيحة');
    if (!toUserId || toUserId === ownerId) {
      throw new BadRequestException('اختر عضواً آخر في الوكالة');
    }
    const agency = await this.agenciesRepo.findOne({ where: { id: agencyId } });
    if (!agency) throw new NotFoundException('Agency not found');
    if (agency.ownerId !== ownerId) {
      throw new ForbiddenException('صاحب الوكالة فقط يمكنه التوزيع');
    }
    const member = await this.membersRepo.findOne({
      where: {
        agencyId,
        userId: toUserId,
        isActive: true,
        status: AgencyMemberStatus.ACTIVE,
      },
    });
    if (!member) throw new BadRequestException('المستخدم ليس عضواً نشطاً في الوكالة');

    const refId = `agency_dist:${agencyId}:${ownerId}:${toUserId}:${Date.now()}`;
    return this.dataSource.transaction(async (manager) => {
      const sender = await manager.findOne(Wallet, {
        where: { userId: ownerId },
        lock: { mode: 'pessimistic_write' },
      });
      if (!sender || Number(sender.diamonds) < diamonds) {
        throw new BadRequestException('رصيد الماس غير كافٍ للسحب/التوزيع');
      }
      let receiver = await manager.findOne(Wallet, {
        where: { userId: toUserId },
        lock: { mode: 'pessimistic_write' },
      });
      if (!receiver) {
        receiver = await manager.save(
          manager.create(Wallet, {
            userId: toUserId,
            coins: 0,
            diamonds: 0,
            traderDiamonds: 0,
            gamePoints: 0,
          }),
        );
        receiver = await manager.findOne(Wallet, {
          where: { userId: toUserId },
          lock: { mode: 'pessimistic_write' },
        });
      }
      if (!receiver) throw new NotFoundException('محفظة المستلم غير موجودة');

      sender.diamonds = Number(sender.diamonds) - diamonds;
      receiver.diamonds = Number(receiver.diamonds) + diamonds;
      await manager.save(sender);
      await manager.save(receiver);
      await manager.save(
        manager.create(WalletTransaction, {
          userId: ownerId,
          type: TransactionType.EXCHANGE,
          currency: CurrencyType.DIAMONDS,
          amount: -diamonds,
          balanceAfter: Number(sender.diamonds),
          referenceType: 'agency_distribute_send',
          referenceId: refId,
          description: `توزيع أرباح وكالة`,
          metadata: { agencyId, toUserId, diamonds },
        }),
      );
      await manager.save(
        manager.create(WalletTransaction, {
          userId: toUserId,
          type: TransactionType.EXCHANGE,
          currency: CurrencyType.DIAMONDS,
          amount: diamonds,
          balanceAfter: Number(receiver.diamonds),
          referenceType: 'agency_distribute_recv',
          referenceId: refId,
          description: `استلام توزيع من صاحب الوكالة`,
          metadata: { agencyId, fromUserId: ownerId, diamonds },
        }),
      );
      return {
        ok: true,
        diamonds,
        toUserId,
        senderBalance: Number(sender.diamonds),
        receiverBalance: Number(receiver.diamonds),
      };
    });
  }

  /** Creates or returns one permanent public/free room for an eligible agency host. */
  async ensurePermanentRoom(agencyId: string, hostId?: string): Promise<Room> {
    const agency = await this.agenciesRepo.findOne({ where: { id: agencyId } });
    if (!agency) throw new NotFoundException('Agency not found');
    if (agency.status !== AgencyStatus.ACTIVE) {
      throw new ForbiddenException('Agency is suspended or inactive');
    }
    const effectiveHostId = hostId || agency.ownerId;
    const membership = await this.membersRepo.findOne({
      where: {
        agencyId,
        userId: effectiveHostId,
        status: AgencyMemberStatus.ACTIVE,
        isActive: true,
      },
    });
    if (
      !membership ||
      ![AgencyRole.OWNER, AgencyRole.MANAGER, AgencyRole.HOST].includes(membership.role)
    ) {
      throw new ForbiddenException('Active owner, manager, or host membership required');
    }

    const existing = await this.roomsRepo.findOne({
      where: { agencyId, hostId: effectiveHostId },
    });
    if (existing) {
      if (!existing.activeHostId) {
        existing.status = RoomStatus.CLOSED;
      }
      existing.roomKind = RoomKind.AGENCY;
      existing.isPersistent = true;
      existing.isPublic = true;
      existing.hasPassword = false;
      existing.passwordHash = null;
      existing.accessMode = 'free' as any;
      existing.entryFeeCoins = 0;
      // Keep lobby title = agency name (never host personal name).
      existing.title = agency.name;
      await this.roomsRepo.save(existing);
      await this.grantAgencyModerators(agency.id, existing.id);
      return existing;
    }

    const room = this.roomsRepo.create({
      title: agency.name,
      description: `${agency.name} — persistent agency voice room`,
      type: RoomType.VOICE,
      status: RoomStatus.CLOSED,
      hostId: effectiveHostId,
      activeHostId: null,
      seatCount: DEFAULT_AGENCY_SEAT_COUNT,
      isPublic: true,
      isPersistent: true,
      roomKind: RoomKind.AGENCY,
      agencyId: agency.id,
      zegoRoomId: `agency_${uuidv4().replace(/-/g, '').slice(0, 16)}`,
      hasPassword: false,
      passwordHash: null,
      accessMode: 'free' as any,
      entryFeeCoins: 0,
      viewerCount: 0,
    });
    try {
      await this.roomsRepo.save(room);
    } catch (error) {
      const raced = await this.roomsRepo.findOne({
        where: { agencyId, hostId: effectiveHostId },
      });
      if (raced) {
        await this.grantAgencyModerators(agencyId, raced.id);
        return raced;
      }
      throw error;
    }

    const seats: RoomSeat[] = [];
    for (let i = 0; i < DEFAULT_AGENCY_SEAT_COUNT; i++) {
      seats.push(
        this.seatsRepo.create({
          roomId: room.id,
          seatIndex: i,
          isHostSeat: i === 0,
          status: SeatStatus.EMPTY,
          userId: null,
        }),
      );
    }
    await this.seatsRepo.save(seats);

    await this.grantAgencyModerators(agency.id, room.id);

    return room;
  }

  /**
   * Soft staff for agency owner/managers on agency rooms.
   * Full room admin (kick/ban/seats/settings) stays with the room host only;
   * the room owner can still appoint real moderators with custom permissions.
   */
  private async grantAgencyModerators(agencyId: string, roomId: string) {
    const agency = await this.agenciesRepo.findOne({ where: { id: agencyId } });
    const managers = await this.membersRepo.find({
      where: [
        { agencyId, role: AgencyRole.OWNER, isActive: true },
        { agencyId, role: AgencyRole.MANAGER, isActive: true },
      ],
    });
    const managerIds = new Set(managers.map((member) => member.userId));
    if (agency?.ownerId) managerIds.add(agency.ownerId);
    for (const userId of managerIds) {
      const exists = await this.modsRepo.findOne({
        where: { roomId, userId },
      });
      if (!exists) {
        await this.modsRepo.save(
          this.modsRepo.create({
            roomId,
            userId,
            role: ModeratorRole.MOD,
            appointedById: agency?.ownerId || userId,
            canManageMusic: false,
            canChangeFrames: false,
            canControlGames: false,
            canMute: true,
            canKick: false,
            canBan: false,
            canManageSeats: false,
            canInvite: false,
            canManageRoom: false,
          }),
        );
      }
    }
  }

  private async removeAgencyModerator(agencyId: string, userId: string) {
    const rooms = await this.roomsRepo.find({ where: { agencyId } });
    for (const room of rooms) {
      await this.modsRepo.delete({ roomId: room.id, userId });
    }
  }

  async getAgencyRoom(agencyId: string, hostId?: string) {
    let room: Room | null;
    if (hostId) {
      room = await this.ensurePermanentRoom(agencyId, hostId);
    } else {
      const agency = await this.agenciesRepo.findOne({ where: { id: agencyId } });
      if (!agency) throw new NotFoundException('Agency not found');
      if (agency.status !== AgencyStatus.ACTIVE) {
        throw new ForbiddenException('Agency is suspended or inactive');
      }
      room = await this.roomsRepo.findOne({
        where: { agencyId, hostId: agency.ownerId },
      });
      if (!room) throw new NotFoundException('Agency room not found');
    }
    return this.roomsService.getRoom(room.id);
  }

  async enterAgencyRoom(agencyId: string, userId: string) {
    const ownMembership = await this.membersRepo.findOne({
      where: {
        agencyId,
        userId,
        status: AgencyMemberStatus.ACTIVE,
        isActive: true,
      },
    });
    const room = ownMembership &&
      [AgencyRole.OWNER, AgencyRole.MANAGER, AgencyRole.HOST].includes(ownMembership.role)
      ? await this.ensurePermanentRoom(agencyId, userId)
      : await this.ensurePermanentRoom(agencyId);
    return this.roomsService.join(room.id, userId, {});
  }

  /** Open/reopen the authenticated host's own persistent room in this agency. */
  async openAgencyRoom(agencyId: string, userId: string, dto: CreateRoomDto) {
    const membership = await this.membersRepo.findOne({
      where: {
        agencyId,
        userId,
        status: AgencyMemberStatus.ACTIVE,
        isActive: true,
      },
      relations: ['agency'],
    });
    if (
      !membership ||
      membership.agency?.status !== AgencyStatus.ACTIVE ||
      ![AgencyRole.OWNER, AgencyRole.MANAGER, AgencyRole.HOST].includes(membership.role)
    ) {
      throw new ForbiddenException('Active owner, manager, or host membership required');
    }
    const agencyName = String(membership.agency.name || '').trim() || 'وكالة';
    return this.roomsService.create(userId, {
      ...dto,
      title: agencyName,
      preferPersonal: false,
    });
  }

  /**
   * Owner permanently deletes the agency, all members, and agency rooms.
   * Financial history (gift_sends, etc.) is preserved with room/agency refs nulled.
   */
  async deleteOwn(agencyId: string, userId: string) {
    const agency = await this.agenciesRepo.findOne({ where: { id: agencyId } });
    if (!agency) throw new NotFoundException('الوكالة غير موجودة');
    if (agency.ownerId !== userId) {
      throw new ForbiddenException('فقط صاحب الوكالة يمكنه حذفها نهائياً');
    }

    const rooms = await this.roomsRepo.find({ where: { agencyId } });
    const roomIds = rooms.map((room) => room.id);

    for (const room of rooms) {
      try {
        await this.roomsService.endLiveSession(room);
      } catch {
        /* room may already be closed */
      }
    }

    await this.dataSource.transaction(async (manager) => {
      for (const roomId of roomIds) {
        const deletedRoom = rooms.find((room) => room.id === roomId);
        const roomTitle = String(deletedRoom?.title || roomId);
        await purgeRoomReferencesBeforeDelete(manager, roomId, roomTitle);
      }

      await manager.delete(AgencyApplication, { agencyId });
      await manager.query(`UPDATE contests SET "agencyId" = NULL WHERE "agencyId" = $1`, [
        agencyId,
      ]);
      await manager.query(
        `UPDATE lucky_box_opens SET "agencyId" = NULL WHERE "agencyId" = $1`,
        [agencyId],
      );
      if (roomIds.length) {
        await manager.delete(Room, roomIds);
      }
      await manager.delete(AgencyMember, { agencyId });
      await manager.delete(Agency, agencyId);
    });

    void this.notificationsService
      .create({
        userId,
        type: NotificationType.SYSTEM,
        title: 'تم حذف الوكالة',
        body: `تم حذف وكالتك «${agency.name}» مع جميع الأعضاء والغرف نهائياً.`,
        data: { agencyId, event: 'agency_deleted' },
      })
      .catch(() => undefined);

    return { deleted: true, id: agencyId, deletedRoomIds: roomIds };
  }

  private async assertManager(agencyId: string, actorId: string) {
    const member = await this.membersRepo.findOne({
      where: { agencyId, userId: actorId, isActive: true },
    });
    if (
      !member ||
      (member.role !== AgencyRole.OWNER && member.role !== AgencyRole.MANAGER)
    ) {
      throw new ForbiddenException('Manager privileges required');
    }
  }

  private async isAgencyOwner(agencyId: string, actorId: string): Promise<boolean> {
    const agency = await this.agenciesRepo.findOne({ where: { id: agencyId } });
    return !!agency && agency.ownerId === actorId;
  }
}
