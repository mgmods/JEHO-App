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
import { DataSource, In, Repository } from 'typeorm';
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
import { Cosmetic } from '../../database/entities/cosmetic.entity';
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
import {
  AgencyPayoutRequest,
  AgencyPayoutStatus,
} from '../../database/entities/agency-payout-request.entity';
import {
  SubmitAgencyApplicationDto,
  CreateAgencyPayoutRequestDto,
} from './dto/agencies.dto';
import { NotificationsService } from '../notifications/notifications.service';
import { NotificationType } from '../../database/entities/notification.entity';
import {
  agencyJoinNotification,
  generateActivationCode,
  isAgencyNotificationStyle,
  normalizeActivationCode,
} from './agency-activation';
import { AGENCY_CREATE } from '../../common/pricing-catalog';
import { ECONOMY } from '../../common/economy-config';
import { purgeRoomReferencesBeforeDelete } from '../../common/room-delete-sql';
import { clampSharePct } from './agency-gift-split';
import {
  isValidAgencyPublicId,
  normalizeAgencyPublicId,
} from './agency-perks';
import { normalizeStaffRole } from '../../common/staff-role';

const DEFAULT_CREATE_PRICE = AGENCY_CREATE.defaultCoins;
/**
 * Single source of truth for the agency owner's commission: the live economy
 * split (`ECONOMY.defaultGiftSplit.agencyOwnerPercent`). The host keeps the
 * remainder. There is NO separate platform cut at the split — the platform
 * margin is already taken at the coin→diamond mint.
 */
const ownerCommissionDefault = () => ECONOMY.defaultGiftSplit.agencyOwnerPercent;
const DEFAULT_AGENCY_SEAT_COUNT = 11;
const DEFAULT_DIAMOND_USD_RATE = 0.00005;

@Injectable()
export class AgenciesService implements OnModuleInit {
  private readonly logger = new Logger(AgenciesService.name);
  constructor(
    @InjectRepository(Agency) private readonly agenciesRepo: Repository<Agency>,
    @InjectRepository(AgencyMember) private readonly membersRepo: Repository<AgencyMember>,
    @InjectRepository(AgencyApplication)
    private readonly applicationsRepo: Repository<AgencyApplication>,
    @InjectRepository(AgencyPayoutRequest)
    private readonly payoutsRepo: Repository<AgencyPayoutRequest>,
    @InjectRepository(GiftSend) private readonly giftSendsRepo: Repository<GiftSend>,
    @InjectRepository(Wallet) private readonly walletsRepo: Repository<Wallet>,
    @InjectRepository(AppSetting) private readonly settingsRepo: Repository<AppSetting>,
    @InjectRepository(Room) private readonly roomsRepo: Repository<Room>,
    @InjectRepository(RoomSeat) private readonly seatsRepo: Repository<RoomSeat>,
    @InjectRepository(RoomModerator) private readonly modsRepo: Repository<RoomModerator>,
    @InjectRepository(User) private readonly usersRepo: Repository<User>,
    @InjectRepository(Cosmetic) private readonly cosmeticsRepo: Repository<Cosmetic>,
    private readonly dataSource: DataSource,
    @Inject(forwardRef(() => RoomsService))
    private readonly roomsService: RoomsService,
    private readonly notificationsService: NotificationsService,
  ) {}

  private async diamondUsdRate(): Promise<number> {
    const rate = Number(ECONOMY.diamondUsd);
    return rate > 0 ? rate : DEFAULT_DIAMOND_USD_RATE;
  }

  private diamondsToUsd(diamonds: number, rate: number): number {
    const n = Math.max(0, Number(diamonds) || 0) * (rate > 0 ? rate : DEFAULT_DIAMOND_USD_RATE);
    return Math.round(n * 10000) / 10000;
  }

  private withUsdSlice(slice: Record<string, any>, rate: number) {
    if (!slice || typeof slice !== 'object') return slice;
    const commission = Number(slice.ownerCommissionEarned || 0);
    const host = Number(slice.estimatedHostShare || 0);
    const gross = Number(slice.grossGiftsDiamonds || 0);
    return {
      ...slice,
      ownerCommissionUsd: this.diamondsToUsd(commission, rate),
      estimatedHostShareUsd: this.diamondsToUsd(host, rate),
      grossGiftsUsd: this.diamondsToUsd(gross, rate),
    };
  }

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
      await this.dataSource.query(`
        ALTER TABLE agencies
          ADD COLUMN IF NOT EXISTS "publicId" VARCHAR(16),
          ADD COLUMN IF NOT EXISTS "isVerified" BOOLEAN NOT NULL DEFAULT false,
          ADD COLUMN IF NOT EXISTS "verifiedAt" TIMESTAMPTZ,
          ADD COLUMN IF NOT EXISTS "exclusiveFrameCode" VARCHAR(64),
          ADD COLUMN IF NOT EXISTS "exclusiveRoomCardCode" VARCHAR(64),
          ADD COLUMN IF NOT EXISTS "exclusiveFrameUrl" VARCHAR(512)
      `);
      await this.dataSource.query(`
        CREATE UNIQUE INDEX IF NOT EXISTS "IDX_agencies_publicId"
        ON agencies ("publicId") WHERE "publicId" IS NOT NULL
      `);
    } catch (err) {
      this.logger.warn(`ensure agency exclusive columns: ${(err as Error).message}`);
    }
    try {
      await this.dataSource.query(`
        ALTER TABLE gifts
          ADD COLUMN IF NOT EXISTS "brandAgencyId" uuid
      `);
      await this.dataSource.query(`
        CREATE INDEX IF NOT EXISTS "IDX_gifts_brandAgencyId"
        ON gifts ("brandAgencyId") WHERE "brandAgencyId" IS NOT NULL
      `);
    } catch (err) {
      this.logger.warn(`ensure gift brandAgencyId: ${(err as Error).message}`);
    }
    try {
      // Family-card follows (Mikoo guild attention) — independent of membership.
      await this.dataSource.query(`
        CREATE TABLE IF NOT EXISTS agency_follows (
          id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
          "agencyId" uuid NOT NULL REFERENCES agencies(id) ON DELETE CASCADE,
          "userId" uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
          "createdAt" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
          UNIQUE ("agencyId", "userId")
        )
      `);
      await this.dataSource.query(`
        CREATE INDEX IF NOT EXISTS "IDX_agency_follows_agencyId"
        ON agency_follows ("agencyId")
      `);
      await this.dataSource.query(`
        CREATE INDEX IF NOT EXISTS "IDX_agency_follows_userId"
        ON agency_follows ("userId")
      `);
    } catch (err) {
      this.logger.warn(`ensure agency_follows: ${(err as Error).message}`);
    }
    try {
      // Mark catalog agency frames as admin-only exclusive when meta missing.
      await this.dataSource.query(`
        UPDATE cosmetics
        SET meta = COALESCE(meta, '{}'::jsonb) || '{"agencyExclusive":true}'::jsonb
        WHERE (code ILIKE '%agency%' OR code ILIKE '%_agency%')
          AND (meta IS NULL OR meta->>'agencyExclusive' IS NULL OR meta->>'agencyExclusive' = 'false')
      `);
    } catch {
      // meta may be json not jsonb on some DBs — best effort
      try {
        await this.dataSource.query(`
          UPDATE cosmetics
          SET meta = json_build_object('agencyExclusive', true)
          WHERE (code ILIKE '%agency%' OR code ILIKE '%_agency%')
            AND meta IS NULL
        `);
      } catch {
        /* ignore */
      }
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
      await this.ensureSetting(
        AGENCY_CREATE.freeSettingKey,
        'false',
        'فتح الوكالة مجاناً (true) — عند التعطيل لا يُقبل سعر 0 ويُطبَّق الحد الأدنى',
      );
    } catch (err) {
      this.logger.warn(`ensure agency create price setting: ${(err as Error).message}`);
    }
    try {
      // The gift split (owner commission vs host share) is owned entirely by the
      // live ECONOMY config (dashboard → Economy panel). No per-share app_settings
      // rows are seeded here anymore — that old system was fully removed.
      await this.ensureSetting(
        'agency_auto_approve_after_payment',
        'false',
        'موافقة تلقائية على طلب الوكالة بعد الدفع (true/false)',
      );
    } catch (err) {
      this.logger.warn(`ensure agency settings: ${(err as Error).message}`);
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

  /** Short shareable public agency ID (3–12 alnum). Admin-only assignment. */
  async allocateAgencyPublicId(preferred?: string | null): Promise<string> {
    if (preferred && isValidAgencyPublicId(preferred)) {
      const id = normalizeAgencyPublicId(preferred);
      const exists = await this.agenciesRepo.findOne({
        where: { publicId: id },
        select: ['id'],
      });
      if (exists) {
        throw new ConflictException(`معرف الوكالة ${id} مستخدم مسبقاً`);
      }
      return id;
    }
    for (let attempt = 0; attempt < 40; attempt++) {
      // Easy 5-digit display ID (never starts with 0).
      const id = String(10000 + Math.floor(Math.random() * 90000));
      const exists = await this.agenciesRepo.findOne({
        where: { publicId: id },
        select: ['id'],
      });
      if (!exists) return id;
    }
    throw new BadRequestException('تعذّر إنشاء معرف الوكالة — أعد المحاولة');
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
    const freeRaw = String(
      (await this.settingsRepo.findOne({ where: { key: AGENCY_CREATE.freeSettingKey } }))
        ?.value || 'false',
    )
      .trim()
      .toLowerCase();
    const createFree =
      freeRaw === 'true' || freeRaw === '1' || freeRaw === 'yes';

    const raw = await this.getSettingNumber(
      AGENCY_CREATE.settingKey,
      DEFAULT_CREATE_PRICE,
    );
    // Free only when explicitly enabled. Price 0 without the free flag → min paid fee.
    let createPriceCoins: number;
    if (createFree) {
      createPriceCoins = 0;
    } else {
      const paid = Number.isFinite(raw) ? Math.floor(raw) : DEFAULT_CREATE_PRICE;
      createPriceCoins = Math.max(
        AGENCY_CREATE.minCoins,
        Math.min(
          AGENCY_CREATE.maxCoins,
          paid > 0 ? paid : AGENCY_CREATE.minCoins,
        ),
      );
    }
    const defaultCommissionPercent = clampSharePct(
      ownerCommissionDefault(),
      ownerCommissionDefault(),
    );
    // Clean economy: host keeps the remainder of the gift pool; the platform
    // margin is taken at the mint, so there is no split-time platform cut.
    const platformCutPercent = 0;
    const hostSharePercent = clampSharePct(100 - defaultCommissionPercent, 70);
    const platformRevenueDiamonds = 0;
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
      createFree,
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
    let agency = membership?.agency || null;
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
      // Regular hosts are earnings-only; go-live is owner/manager (admin) only.
      (membership!.role === AgencyRole.OWNER || membership!.role === AgencyRole.MANAGER);
    const room = canHostRoom
      ? await this.roomsRepo.findOne({
          where: { agencyId: membership!.agencyId, hostId: userId },
        })
      : null;
    const canViewEarnings =
      activeMembership &&
      agency?.status === AgencyStatus.ACTIVE &&
      (membership!.role === AgencyRole.OWNER || membership!.role === AgencyRole.MANAGER);
    const canManage =
      activeMembership &&
      (membership!.role === AgencyRole.OWNER || membership!.role === AgencyRole.MANAGER);
    const isHostMember =
      activeMembership &&
      agency?.status === AgencyStatus.ACTIVE &&
      (membership!.role === AgencyRole.HOST || membership!.role === AgencyRole.MEMBER);
    /** Performance board for hosts & regular members (not owner/manager agency rollup). */
    const showHostDash =
      activeMembership &&
      agency?.status === AgencyStatus.ACTIVE &&
      (membership!.role === AgencyRole.HOST || membership!.role === AgencyRole.MEMBER);
    if (canManage && agency && !agency.activationCode) {
      agency.activationCode = await this.allocateActivationCode();
      await this.agenciesRepo.save(agency);
    }
    if (agency) {
      agency = await this.ensureAgencyPublicId(agency);
    }
    const agencyPayload = agency
      ? (() => {
          const base = canManage
            ? { ...agency }
            : { ...this.stripSecretFields(agency) };
          return base;
        })()
      : null;
    // Always expose brand logo (logoUrl or room cover face) for manage / room UI.
    if (agencyPayload && agency) {
      const brand = await this.resolveAgencyBrandUrls(agency.id, agency.logoUrl);
      (agencyPayload as any).logoUrl = brand.logoUrl;
      (agencyPayload as any).coverUrl = brand.coverUrl || brand.logoUrl;
    }
    return {
      application: visibleApplication,
      agency: agencyPayload,
      agencyStatus: agency?.status || null,
      membershipStatus: membership?.status || null,
      role: membership?.role || null,
      canHostRoom,
      roomId: room?.id || null,
      earnings: canViewEarnings ? await this.earnings(agency!.id, userId) : null,
      /** Performance board for hosts & regular members. */
      hostDashboard: showHostDash ? await this.hostDashboard(userId) : null,
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
    // Free only when createFree is on (pricing() already zeroed the fee).
    const priceCoins = pricing.createFree
      ? 0
      : Math.max(AGENCY_CREATE.minCoins, Math.floor(Number(pricing.createPriceCoins) || 0));
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
      } else if (!alreadyPaid && priceCoins <= 0) {
        // Free application — mark as settled with 0 coins (not unpaid).
        paidCoins = 0;
        paymentReferenceId = `agency_create_free:${applicantId}:${Date.now()}`;
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

      let activationCode = generateActivationCode();
      for (let attempt = 0; attempt < 12; attempt++) {
        const clash = await manager.findOne(Agency, {
          where: { activationCode },
          select: ['id'],
        });
        if (!clash) break;
        activationCode = generateActivationCode();
      }
      let publicId = String(10000 + Math.floor(Math.random() * 90000));
      for (let attempt = 0; attempt < 40; attempt++) {
        const clash = await manager.findOne(Agency, {
          where: { publicId },
          select: ['id'],
        });
        if (!clash) break;
        publicId = String(10000 + Math.floor(Math.random() * 90000));
      }
      const agency = await manager.save(
        manager.create(Agency, {
          name: application.proposedName,
          description: application.description,
          ownerId: application.applicantId,
          status: AgencyStatus.ACTIVE,
          memberCount: 1,
          totalDiamonds: 0,
          commissionPercent: ownerCommissionDefault(),
          activationCode,
          publicId,
          isVerified: false,
          verifiedAt: null,
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
    const raw = String(query.search || query.q || '').trim();
    // No public directory dump — clients must look up by agency public ID / code only.
    if (raw.length < 3) {
      return paginate([], 0, query.page || 1, query.limit || 20);
    }
    const qUpper = raw.toUpperCase();
    const qb = this.agenciesRepo
      .createQueryBuilder('a')
      .leftJoinAndSelect('a.owner', 'owner')
      .where('a.status = :st', { st: AgencyStatus.ACTIVE })
      .andWhere(
        // Public lookup is by agency publicId only (not broad name listing).
        '(UPPER(TRIM(a.publicId)) = :exact OR a.publicId ILIKE :prefix OR CAST(a.id AS text) = :idExact)',
        {
          exact: qUpper,
          prefix: `${qUpper.replace(/[%_]/g, '')}%`,
          idExact: raw,
        },
      )
      .orderBy('a.totalDiamonds', 'DESC')
      .skip(query.skip)
      .take(Math.min(query.limit || 10, 10));
    const [items, total] = await qb.getManyAndCount();
    const liveByAgency = await this.loadLiveAgencyRooms(items.map((a) => a.id));
    const safe = await Promise.all(
      items.map((a) => this.publicAgencyCard(a, liveByAgency.get(a.id))),
    );
    return paginate(safe, total, query.page || 1, query.limit || 10);
  }

  /** Best live room per agency (open + active host). */
  /** Prefer agency.logoUrl; else latest non-default agency room cover (same brand face). */
  private async resolveAgencyBrandUrls(
    agencyId: string,
    storedLogo?: string | null,
  ): Promise<{ logoUrl: string | null; coverUrl: string | null }> {
    const logo =
      storedLogo && String(storedLogo).trim()
        ? String(storedLogo).trim()
        : null;
    let roomCover: string | null = null;
    try {
      const rows = await this.dataSource.query(
        `SELECT "coverUrl" FROM rooms
         WHERE "agencyId" = $1
           AND "coverUrl" IS NOT NULL
           AND TRIM("coverUrl") <> ''
         ORDER BY "updatedAt" DESC NULLS LAST
         LIMIT 8`,
        [agencyId],
      );
      if (Array.isArray(rows)) {
        for (const row of rows) {
          const raw = row?.coverUrl;
          if (typeof raw !== 'string' || !raw.trim()) continue;
          const c = raw.trim();
          if (this.isGenericRoomCover(c)) continue;
          roomCover = c;
          break;
        }
      }
    } catch {
      /* ignore */
    }
    const brand = logo || roomCover;
    return {
      logoUrl: brand,
      coverUrl: roomCover || logo,
    };
  }

  /** Asset-pack room walls are not the agency profile face. */
  private isGenericRoomCover(url: string): boolean {
    const u = String(url || '').toLowerCase();
    if (!u) return true;
    return (
      u.includes('backgrounds/bg_') ||
      u.includes('/assets/backgrounds') ||
      u.includes('icon_agency') ||
      u.includes('icon_classic_seat')
    );
  }

  private async loadLiveAgencyRooms(agencyIds: string[]) {
    const map = new Map<
      string,
      { id: string; coverUrl: string | null; roomCardUrl: string | null; viewerCount: number }
    >();
    const ids = agencyIds.filter(Boolean);
    if (!ids.length) return map;
    const rooms = await this.roomsRepo
      .createQueryBuilder('r')
      .where('r.agencyId IN (:...ids)', { ids })
      .andWhere('r.activeHostId IS NOT NULL')
      .andWhere('r.status IN (:...st)', { st: [RoomStatus.OPEN, RoomStatus.LOCKED] })
      .orderBy('r.viewerCount', 'DESC')
      .getMany();
    for (const r of rooms) {
      if (!r.agencyId || map.has(r.agencyId)) continue;
      map.set(r.agencyId, {
        id: r.id,
        coverUrl: r.coverUrl,
        roomCardUrl: r.roomCardUrl,
        viewerCount: Number(r.viewerCount || 0),
      });
    }
    return map;
  }

  private async publicAgencyCard(
    agency: Agency,
    live?: {
      id: string;
      coverUrl: string | null;
      roomCardUrl: string | null;
      viewerCount: number;
    } | null,
    brand?: { logoUrl: string | null; coverUrl: string | null } | null,
  ) {
    const base = this.stripSecretFields(agency) as Agency & Record<string, unknown>;
    const liveCover =
      live?.coverUrl && !this.isGenericRoomCover(String(live.coverUrl))
        ? String(live.coverUrl).trim()
        : null;
    const storedLogo =
      agency.logoUrl && String(agency.logoUrl).trim()
        ? String(agency.logoUrl).trim()
        : null;
    // Single brand face: explicit logo → live cover → resolved brand cover → null.
    const logoUrl =
      brand?.logoUrl ||
      storedLogo ||
      liveCover ||
      brand?.coverUrl ||
      null;
    const verified =
      !!agency.isVerified && agency.status === AgencyStatus.ACTIVE;
    const exclusiveFrameUrl = await this.resolveExclusiveFramePreviewUrl(agency);
    return {
      ...base,
      logoUrl,
      publicId: agency.publicId || null,
      isVerified: verified,
      verifiedAt: agency.verifiedAt || null,
      coverUrl: liveCover || brand?.coverUrl || logoUrl,
      // Prefer live room card, then resolved exclusive frame art from catalog code.
      frameUrl: live?.roomCardUrl || exclusiveFrameUrl || null,
      exclusiveFrameCode: agency.exclusiveFrameCode || null,
      exclusiveRoomCardCode: agency.exclusiveRoomCardCode || null,
      exclusiveFrameUrl,
      isLive: !!live,
      openRoomId: live?.id || null,
      liveViewerCount: live ? Number(live.viewerCount || 0) : 0,
      memberCount: Number(agency.memberCount || 0),
    };
  }

  async get(id: string, viewerId?: string) {
    // Do not load full members list — agencies can be huge. Use listMembers instead.
    let agency = await this.agenciesRepo.findOne({
      where: { id },
      relations: ['owner'],
    });
    if (!agency) {
      // Also resolve by numeric public agency ID (not owner user ID).
      agency = await this.agenciesRepo.findOne({
        where: { publicId: String(id || '').trim() },
        relations: ['owner'],
      });
    }
    if (!agency) throw new NotFoundException('Agency not found');
    agency = await this.ensureAgencyPublicId(agency);
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
    const { owner, activationCode: _code, ...safeAgency } = agency as Agency & {
      owner?: User;
    };
    const verified =
      !!agency.isVerified && agency.status === AgencyStatus.ACTIVE;

    // Mikoo family-info card: stats under the agency name.
    const memberIds = (
      await this.membersRepo.find({
        where: {
          agencyId: agency.id,
          status: AgencyMemberStatus.ACTIVE,
          isActive: true,
        },
        select: ['userId'],
      })
    ).map((m) => m.userId);
    const giftsReceivedDiamonds = await this.sumGiftsReceived(memberIds);
    const roomCount = await this.roomsRepo.count({
      where: { agencyId: agency.id },
    });
    const liveRooms = await this.roomsRepo.find({
      where: { agencyId: agency.id },
      select: ['viewerCount', 'status', 'activeHostId'],
    });
    let maxOnline = 0;
    let liveOnline = 0;
    for (const r of liveRooms) {
      const v = Math.max(0, Number(r.viewerCount || 0));
      if (v > maxOnline) maxOnline = v;
      if (r.status === RoomStatus.OPEN && r.activeHostId) {
        liveOnline += v;
      }
    }
    maxOnline = Math.max(maxOnline, liveOnline);
    let followerCount = 0;
    try {
      const row = await this.dataSource.query(
        `SELECT COUNT(*)::int AS c FROM agency_follows WHERE "agencyId" = $1`,
        [agency.id],
      );
      followerCount = Number(row?.[0]?.c || 0);
    } catch {
      followerCount = 0;
    }
    // Also count members as social gravity on the card when follows are empty.
    if (followerCount <= 0) followerCount = Number(agency.memberCount || 0);

    let isFollowing = false;
    if (viewerId) {
      try {
        const row = await this.dataSource.query(
          `SELECT 1 FROM agency_follows WHERE "agencyId" = $1 AND "userId" = $2 LIMIT 1`,
          [agency.id, viewerId],
        );
        isFollowing = Array.isArray(row) && row.length > 0;
      } catch {
        isFollowing = false;
      }
    }

    const totalDiamonds = Number(agency.totalDiamonds || 0);
    const level = this.agencyBannerTier(totalDiamonds);
    const brand = await this.resolveAgencyBrandUrls(agency.id, agency.logoUrl);
    const exclusiveFrameUrl = await this.resolveExclusiveFramePreviewUrl(agency);

    return {
      ...safeAgency,
      publicId: agency.publicId || null,
      isVerified: verified,
      verifiedAt: agency.verifiedAt || null,
      exclusiveFrameCode: agency.exclusiveFrameCode || null,
      exclusiveRoomCardCode: agency.exclusiveRoomCardCode || null,
      exclusiveFrameUrl,
      frameUrl: exclusiveFrameUrl,
      logoUrl: brand.logoUrl,
      coverUrl: brand.coverUrl || brand.logoUrl,
      owner: publicUser(owner),
      members: [] as unknown[],
      memberCount: Number(agency.memberCount || 0),
      // Family card fields (Mikoo guild homepage):
      followerCount,
      isFollowing,
      roomCount,
      maxOnline,
      liveOnline,
      giftsReceivedDiamonds,
      totalDiamonds,
      level,
      medals: level,
    };
  }

  /** Visual banner tier 1–6 (same thresholds as user-card guild strip). */
  private agencyBannerTier(totalDiamonds: number): number {
    const d = Math.max(0, Number(totalDiamonds) || 0);
    if (d >= 5_000_000) return 6;
    if (d >= 1_000_000) return 5;
    if (d >= 200_000) return 4;
    if (d >= 50_000) return 3;
    if (d >= 10_000) return 2;
    return 1;
  }

  /** Admin/catalog preview for agency card overlay — code wins over stale manual URL. */
  private async resolveExclusiveFramePreviewUrl(
    agency: Pick<Agency, 'exclusiveFrameCode' | 'exclusiveFrameUrl'>,
  ): Promise<string | null> {
    const code = agency.exclusiveFrameCode?.trim();
    if (code) {
      try {
        const row = await this.cosmeticsRepo.findOne({
          where: { code, isActive: true },
        });
        const fromCatalog =
          row?.previewUrl?.trim() || row?.animationUrl?.trim() || null;
        if (fromCatalog) return fromCatalog;
      } catch {
        /* fall through */
      }
    }
    const manual = agency.exclusiveFrameUrl?.trim();
    return manual || null;
  }

  /** Catalog preview for agency room-card kenar (home feed). */
  async resolveExclusiveRoomCardPreviewUrl(
    agency: Pick<Agency, 'exclusiveRoomCardCode'>,
  ): Promise<string | null> {
    const code = agency.exclusiveRoomCardCode?.trim();
    if (!code) return null;
    try {
      const row = await this.cosmeticsRepo.findOne({
        where: { code, isActive: true },
      });
      return row?.previewUrl?.trim() || row?.animationUrl?.trim() || null;
    } catch {
      return null;
    }
  }

  /**
   * Agency public GID is independent of owner user publicId.
   * Older rows without a code get one lazily (5-digit, never owner id).
   */
  private async ensureAgencyPublicId(agency: Agency): Promise<Agency> {
    if (agency.publicId && String(agency.publicId).trim()) {
      agency.publicId = String(agency.publicId).trim();
      return agency;
    }
    for (let attempt = 0; attempt < 48; attempt++) {
      const candidate = String(10000 + Math.floor(Math.random() * 90000));
      const clash = await this.agenciesRepo.findOne({
        where: { publicId: candidate },
        select: ['id'],
      });
      if (clash) continue;
      agency.publicId = candidate;
      await this.agenciesRepo.update({ id: agency.id }, { publicId: candidate });
      return agency;
    }
    const fallback = String(Date.now()).slice(-8);
    agency.publicId = fallback;
    await this.agenciesRepo.update({ id: agency.id }, { publicId: fallback });
    return agency;
  }

  async followAgency(agencyId: string, userId: string) {
    let agency = await this.agenciesRepo.findOne({
      where: { id: agencyId },
    });
    if (!agency) {
      agency = await this.agenciesRepo.findOne({
        where: { publicId: String(agencyId || '').trim() },
      });
    }
    if (!agency || agency.status !== AgencyStatus.ACTIVE) {
      throw new NotFoundException('Agency not found');
    }
    agency = await this.ensureAgencyPublicId(agency);
    await this.dataSource.query(
      `INSERT INTO agency_follows (id, "agencyId", "userId")
       VALUES (gen_random_uuid(), $1, $2)
       ON CONFLICT ("agencyId", "userId") DO NOTHING`,
      [agency.id, userId],
    );
    const row = await this.dataSource.query(
      `SELECT COUNT(*)::int AS c FROM agency_follows WHERE "agencyId" = $1`,
      [agency.id],
    );
    return {
      following: true,
      followerCount: Number(row?.[0]?.c || 0),
      agencyId: agency.id,
      publicId: agency.publicId,
    };
  }

  async unfollowAgency(agencyId: string, userId: string) {
    let agency = await this.agenciesRepo.findOne({
      where: { id: agencyId },
      select: ['id'],
    });
    if (!agency) {
      agency = await this.agenciesRepo.findOne({
        where: { publicId: String(agencyId || '').trim() },
        select: ['id'],
      });
    }
    if (!agency) {
      throw new NotFoundException('Agency not found');
    }
    await this.dataSource.query(
      `DELETE FROM agency_follows WHERE "agencyId" = $1 AND "userId" = $2`,
      [agency.id, userId],
    );
    const row = await this.dataSource.query(
      `SELECT COUNT(*)::int AS c FROM agency_follows WHERE "agencyId" = $1`,
      [agency.id],
    );
    return {
      following: false,
      followerCount: Number(row?.[0]?.c || 0),
      agencyId: agency.id,
    };
  }

  /**
   * Search / page agency members. Never dumps the whole roster.
   * Active members require a search query (min 1 char) unless scope=staff.
   */
  async listMembers(
    agencyId: string,
    actorId: string,
    query: { q?: string; page?: number; limit?: number; scope?: string },
  ) {
    await this.assertManager(agencyId, actorId);
    const page = Math.max(1, Number(query.page) || 1);
    const limit = Math.min(40, Math.max(1, Number(query.limit) || 20));
    const skip = (page - 1) * limit;
    const q = String(query.q || '').trim();
    const scope = String(query.scope || 'active').toLowerCase();

    const mapRow = (member: AgencyMember) => ({
      id: member.id,
      agencyId: member.agencyId,
      userId: member.userId,
      role: member.role,
      status: member.status,
      isActive: member.isActive,
      joinedAt: member.joinedAt,
      user: member.user
        ? {
            id: member.user.id,
            publicId: member.user.publicId,
            username: member.user.username,
            displayName: member.user.displayName,
            avatarUrl: member.user.avatarUrl,
            level: member.user.level,
          }
        : null,
    });

    if (scope === 'pending') {
      const [items, total] = await this.membersRepo.findAndCount({
        where: { agencyId, status: AgencyMemberStatus.PENDING },
        relations: ['user'],
        order: { joinedAt: 'DESC' },
        skip,
        take: limit,
      });
      return {
        items: items.map(mapRow),
        total,
        page,
        limit,
      };
    }

    // Staff preview (owner + managers + hosts) without full roster — max one page.
    if (scope === 'staff' && !q) {
      const items = await this.membersRepo
        .createQueryBuilder('m')
        .leftJoinAndSelect('m.user', 'u')
        .where('m.agencyId = :agencyId', { agencyId })
        .andWhere('m.status = :st', { st: AgencyMemberStatus.ACTIVE })
        .andWhere('m.role IN (:...roles)', {
          roles: [AgencyRole.OWNER, AgencyRole.MANAGER, AgencyRole.HOST],
        })
        .orderBy(
          `CASE m.role WHEN 'owner' THEN 0 WHEN 'manager' THEN 1 WHEN 'host' THEN 2 ELSE 3 END`,
          'ASC',
        )
        .addOrderBy('m.joinedAt', 'DESC')
        .take(limit)
        .getMany();
      return {
        items: items.map(mapRow),
        total: items.length,
        page: 1,
        limit,
        scope: 'staff',
      };
    }

    // Active/all members: search required (prevents loading "a million").
    if (q.length < 1) {
      const total = await this.membersRepo.count({
        where: { agencyId, status: AgencyMemberStatus.ACTIVE },
      });
      return {
        items: [],
        total,
        page,
        limit,
        requiresSearch: true,
      };
    }

    const qb = this.membersRepo
      .createQueryBuilder('m')
      .leftJoinAndSelect('m.user', 'u')
      .where('m.agencyId = :agencyId', { agencyId })
      .andWhere('m.status = :st', { st: AgencyMemberStatus.ACTIVE });

    const like = `%${q.replace(/[%_]/g, '')}%`;
    qb.andWhere(
      `(u.username ILIKE :like OR u.displayName ILIKE :like OR CAST(u.publicId AS text) ILIKE :like OR u.id::text = :exact)`,
      { like, exact: q },
    );

    const total = await qb.getCount();
    const items = await qb
      .orderBy('m.joinedAt', 'DESC')
      .skip(skip)
      .take(limit)
      .getMany();

    return {
      items: items.map(mapRow),
      total,
      page,
      limit,
      requiresSearch: false,
    };
  }

  /**
   * Join using the agency's private activation code (chat invite + manual code field).
   * Code only proves the invite — owner/manager must still approve (PENDING).
   */
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
    // Same approval queue as listMembers join-requests (not instant free entry).
    return this.joinSelf(agency.id, userId);
  }

  private async sendJoinApprovedNotice(agency: Agency, memberUserId: string) {
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
          action: 'join_by_code',
        },
        sendPush: true,
      });
    } catch {
      // Join must succeed even if push fails.
    }
  }

  async updateSettings(
    agencyId: string,
    actorId: string,
    dto: {
      notificationStyle?: string;
      name?: string;
      description?: string;
      logoUrl?: string;
    },
  ) {
    await this.assertManager(agencyId, actorId);
    const agency = await this.agenciesRepo.findOne({ where: { id: agencyId } });
    if (!agency) throw new NotFoundException('Agency not found');

    let renamed = false;
    if (dto.notificationStyle != null) {
      const style = String(dto.notificationStyle).trim().toLowerCase();
      if (!isAgencyNotificationStyle(style)) {
        throw new BadRequestException('نمط الإشعار غير مدعوم');
      }
      agency.notificationStyle = style;
    }
    if (dto.name != null) {
      // Renaming the agency brand is owner-only; managers keep notification style.
      await this.assertOwner(agencyId, actorId);
      const next = String(dto.name).trim().replace(/\s+/g, ' ');
      if (next.length < 2) {
        throw new BadRequestException('اسم الوكالة قصير جداً');
      }
      if (next.length > 64) {
        throw new BadRequestException('اسم الوكالة طويل جداً');
      }
      const clash = await this.agenciesRepo
        .createQueryBuilder('agency')
        .where('LOWER(agency.name) = LOWER(:name)', { name: next })
        .andWhere('agency.id != :id', { id: agencyId })
        .getOne();
      if (clash) {
        throw new ConflictException('اسم الوكالة مستخدم مسبقاً');
      }
      agency.name = next;
      renamed = true;
    }
    if (dto.description !== undefined) {
      await this.assertOwner(agencyId, actorId);
      const desc = dto.description == null ? null : String(dto.description).trim();
      agency.description = desc && desc.length > 0 ? desc.slice(0, 500) : null;
    }
    if (dto.logoUrl !== undefined) {
      await this.assertOwner(agencyId, actorId);
      const logo = dto.logoUrl == null ? null : String(dto.logoUrl).trim();
      agency.logoUrl = logo && logo.length > 0 ? logo.slice(0, 512) : null;
    }

    await this.agenciesRepo.save(agency);

    if (renamed || dto.description !== undefined) {
      const rooms = await this.roomsRepo.find({ where: { agencyId } });
      for (const room of rooms) {
        if (!room) continue;
        room.title = agency.name;
        const welcome =
          agency.description && agency.description.trim().length > 0
            ? agency.description.trim()
            : `مرحباً بكم في الغرفة الصوتية · ${agency.name}`;
        room.description = welcome.slice(0, 500);
        if (agency.logoUrl) {
          room.coverUrl = agency.logoUrl;
        }
        await this.roomsRepo.save(room);
      }
    }

    return {
      id: agency.id,
      name: agency.name,
      description: agency.description,
      logoUrl: agency.logoUrl,
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
    // Cap so a spam flood of pending requests cannot crash the admin UI.
    const take = 40;
    const [items, total] = await this.membersRepo.findAndCount({
      where: { agencyId, status: AgencyMemberStatus.PENDING },
      relations: ['user'],
      order: { joinedAt: 'DESC' },
      take,
    });
    return { items, total, limit: take };
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
    const wasHostCapable = [AgencyRole.OWNER, AgencyRole.MANAGER].includes(member.role);
    const willHostCapable = [AgencyRole.OWNER, AgencyRole.MANAGER].includes(dto.role);
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
    const agency = await this.agenciesRepo.findOne({ where: { id: agencyId } });
    if (!agency) throw new NotFoundException('Agency not found');
    const members = await this.membersRepo.find({
      where: { agencyId, status: AgencyMemberStatus.ACTIVE, isActive: true },
      select: ['userId'],
    });
    const memberIds = members.map((m) => m.userId);
    const received = await this.sumGiftsReceived(memberIds);
    return {
      agencyId,
      name: agency.name,
      memberCount: agency.memberCount,
      totalDiamonds: Number(agency.totalDiamonds),
      giftsReceivedDiamonds: received,
      commissionPercent: Number(agency.commissionPercent),
    };
  }

  private periodStarts() {
    const now = new Date();
    const weekStart = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
    const monthStart = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), 1, 0, 0, 0));
    return { now, weekStart, monthStart };
  }

  private async sumGiftsReceived(receiverIds: string[], from?: Date): Promise<number> {
    if (!receiverIds.length) return 0;
    const qb = this.giftSendsRepo
      .createQueryBuilder('g')
      .select('COALESCE(SUM(g.diamondsAwarded), 0)', 'total')
      .where('g.receiverId IN (:...ids)', { ids: receiverIds });
    if (from) qb.andWhere('g.createdAt >= :from', { from });
    const raw = await qb.getRawOne();
    return Number(raw?.total || 0);
  }

  /** Net diamond credits from gift receives (host take-home after splits). */
  private async sumGiftIncome(userId: string, from?: Date): Promise<number> {
    const qb = this.dataSource
      .getRepository(WalletTransaction)
      .createQueryBuilder('t')
      .select('COALESCE(SUM(t.amount), 0)', 'total')
      .where('t.userId = :uid', { uid: userId })
      .andWhere('t.amount > 0')
      .andWhere('t.referenceType IN (:...types)', {
        types: [
          'gift_receive',
          'gift_receive_host',
          'gift_receive_mic',
          'gift_receive_agency',
          'gift_receive_mic_agency',
        ],
      });
    if (from) qb.andWhere('t.createdAt >= :from', { from });
    const raw = await qb.getRawOne();
    return Number(raw?.total || 0);
  }

  private async countGiftsReceived(userId: string, from?: Date): Promise<number> {
    const qb = this.giftSendsRepo
      .createQueryBuilder('g')
      .select('COUNT(*)', 'c')
      .where('g.receiverId = :uid', { uid: userId });
    if (from) qb.andWhere('g.createdAt >= :from', { from });
    const raw = await qb.getRawOne();
    return Number(raw?.c || 0);
  }

  private async fansCount(userId: string): Promise<number> {
    try {
      const row = await this.usersRepo.manager.query(
        `SELECT COALESCE(p."followersCount", 0)::bigint AS c
         FROM users u
         LEFT JOIN user_profiles p ON p."userId" = u.id
         WHERE u.id = $1
         LIMIT 1`,
        [userId],
      );
      return Number(row?.[0]?.c || 0);
    } catch {
      return 0;
    }
  }

  private pctChange(current: number, previous: number): number {
    if (!previous || previous <= 0) return current > 0 ? 100 : 0;
    return Math.round(((current - previous) / previous) * 1000) / 10;
  }

  private async sumOwnerCommission(
    agencyId: string,
    ownerId: string,
    from?: Date,
  ): Promise<number> {
    const qb = this.dataSource
      .getRepository(WalletTransaction)
      .createQueryBuilder('t')
      .select('COALESCE(SUM(t.amount), 0)', 'total')
      .where('t.userId = :ownerId', { ownerId })
      .andWhere('t.referenceType = :rt', { rt: 'agency_commission' })
      .andWhere(
        `(t."referenceId" = :agencyId OR CAST(t.metadata AS TEXT) LIKE :meta)`,
        { agencyId, meta: `%${agencyId}%` },
      );
    if (from) qb.andWhere('t.createdAt >= :from', { from });
    const raw = await qb.getRawOne();
    return Number(raw?.total || 0);
  }

  private estimateSplit(gross: number, commissionPct: number) {
    // CLEAN ECONOMY: gift diamonds split host / agency owner only (no platform
    // cut — the margin was taken at the coin→diamond mint).
    const g = Math.max(0, Math.floor(Number(gross) || 0));
    const pct = Math.min(100, Math.max(0, Number(commissionPct) || 0));
    const agentShare = Math.floor((g * pct) / 100);
    const hostShare = Math.max(0, g - agentShare);
    return {
      estimatedPlatformCut: 0,
      estimatedAgentShare: agentShare,
      estimatedHostShare: hostShare,
    };
  }

  private async periodSlice(
    agencyId: string,
    ownerId: string,
    memberIds: string[],
    commissionPct: number,
    from?: Date,
  ) {
    const [gross, ownerCommissionEarned] = await Promise.all([
      this.sumGiftsReceived(memberIds, from),
      this.sumOwnerCommission(agencyId, ownerId, from),
    ]);
    const split = this.estimateSplit(gross, commissionPct);
    return {
      from: from ? from.toISOString() : null,
      grossGiftsDiamonds: gross,
      ownerCommissionEarned,
      ...split,
    };
  }

  /** Per-host earnings dashboard (week / month / lifetime + board KPIs). */
  async hostDashboard(userId: string) {
    const { weekStart, monthStart, now } = this.periodStarts();
    const prevMonthStart = new Date(
      Date.UTC(now.getUTCFullYear(), now.getUTCMonth() - 1, 1, 0, 0, 0),
    );
    const prevMonthEnd = monthStart;
    const [
      grossWeek,
      grossMonth,
      grossAll,
      grossPrevMonth,
      diamondsWeek,
      diamondsMonth,
      diamondsAll,
      diamondsPrevMonth,
      giftsCountMonth,
      giftsCountPrevMonth,
      fans,
      wallet,
      user,
    ] = await Promise.all([
      this.sumGiftsReceived([userId], weekStart),
      this.sumGiftsReceived([userId], monthStart),
      this.sumGiftsReceived([userId]),
      this.sumGiftsRange(userId, prevMonthStart, prevMonthEnd),
      this.sumGiftIncome(userId, weekStart),
      this.sumGiftIncome(userId, monthStart),
      this.sumGiftIncome(userId),
      this.sumGiftIncomeRange(userId, prevMonthStart, prevMonthEnd),
      this.countGiftsReceived(userId, monthStart),
      this.countGiftsReceivedRange(userId, prevMonthStart, prevMonthEnd),
      this.fansCount(userId),
      this.walletsRepo.findOne({ where: { userId } }),
      this.usersRepo.findOne({ where: { id: userId } }),
    ]);
    const rate = await this.diamondUsdRate();
    const walletDiamonds = Number(wallet?.diamonds || 0);
    const agencyDiamonds = 0; // deprecated dual-pool field, always 0
    return {
      giftsGrossWeek: grossWeek,
      giftsGrossMonth: grossMonth,
      giftsGrossAllTime: grossAll,
      giftsGrossPrevMonth: grossPrevMonth,
      giftsCountMonth,
      giftsCountPrevMonth,
      fansCount: fans,
      level: Number(user?.level || 1),
      diamondsEarnedWeek: diamondsWeek,
      diamondsEarnedMonth: diamondsMonth,
      diamondsEarnedAllTime: diamondsAll,
      diamondsEarnedPrevMonth: diamondsPrevMonth,
      /** USD recognition of host profits (from gift share). */
      usdEarnedWeek: this.diamondsToUsd(diamondsWeek, rate),
      usdEarnedMonth: this.diamondsToUsd(diamondsMonth, rate),
      usdEarnedAllTime: this.diamondsToUsd(diamondsAll, rate),
      usdEarnedPrevMonth: this.diamondsToUsd(diamondsPrevMonth, rate),
      monthUsdDeltaPct: this.pctChange(diamondsMonth, diamondsPrevMonth),
      giftsMonthDeltaPct: this.pctChange(giftsCountMonth, giftsCountPrevMonth),
      /** Personal-room withdrawable pool */
      walletDiamonds,
      walletUsd: this.diamondsToUsd(walletDiamonds, rate),
      personalDiamonds: walletDiamonds,
      personalUsd: this.diamondsToUsd(walletDiamonds, rate),
      /** Agency-room host share — withdraw via platform */
      agencyDiamonds,
      agencyUsd: this.diamondsToUsd(agencyDiamonds, rate),
      diamondUsdRate: rate,
      weekFrom: weekStart.toISOString(),
      monthFrom: monthStart.toISOString(),
    };
  }

  private async sumGiftsRange(userId: string, from: Date, to: Date): Promise<number> {
    const qb = this.giftSendsRepo
      .createQueryBuilder('g')
      .select('COALESCE(SUM(g.diamondsAwarded), 0)', 'total')
      .where('g.receiverId = :uid', { uid: userId })
      .andWhere('g.createdAt >= :from', { from })
      .andWhere('g.createdAt < :to', { to });
    const raw = await qb.getRawOne();
    return Number(raw?.total || 0);
  }

  private async sumGiftIncomeRange(userId: string, from: Date, to: Date): Promise<number> {
    const qb = this.dataSource
      .getRepository(WalletTransaction)
      .createQueryBuilder('t')
      .select('COALESCE(SUM(t.amount), 0)', 'total')
      .where('t.userId = :uid', { uid: userId })
      .andWhere('t.amount > 0')
      .andWhere('t.referenceType IN (:...types)', {
        types: [
          'gift_receive',
          'gift_receive_host',
          'gift_receive_mic',
          'gift_receive_agency',
          'gift_receive_mic_agency',
        ],
      })
      .andWhere('t.createdAt >= :from', { from })
      .andWhere('t.createdAt < :to', { to });
    const raw = await qb.getRawOne();
    return Number(raw?.total || 0);
  }

  private async countGiftsReceivedRange(
    userId: string,
    from: Date,
    to: Date,
  ): Promise<number> {
    const qb = this.giftSendsRepo
      .createQueryBuilder('g')
      .select('COUNT(*)', 'c')
      .where('g.receiverId = :uid', { uid: userId })
      .andWhere('g.createdAt >= :from', { from })
      .andWhere('g.createdAt < :to', { to });
    const raw = await qb.getRawOne();
    return Number(raw?.c || 0);
  }

  /** Detailed earnings for agency owner/manager: commission + period dashboard */
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

    const commissionPct = clampSharePct(
      Number(agency.commissionPercent) || ownerCommissionDefault(),
      ownerCommissionDefault(),
    );
    // Clean economy: host keeps the remainder, no split-time platform cut.
    const platformPct = 0;
    const hostPct = clampSharePct(100 - commissionPct, 70);
    const { weekStart, monthStart } = this.periodStarts();

    const members = await this.membersRepo.find({
      where: { agencyId, isActive: true, status: AgencyMemberStatus.ACTIVE },
      select: ['userId'],
    });
    const memberIds = members.map((m) => m.userId);

    const [week, month, allTime, recentRows] = await Promise.all([
      this.periodSlice(agencyId, agency.ownerId, memberIds, commissionPct, weekStart),
      this.periodSlice(agencyId, agency.ownerId, memberIds, commissionPct, monthStart),
      this.periodSlice(agencyId, agency.ownerId, memberIds, commissionPct),
      this.dataSource.getRepository(WalletTransaction).find({
        where: {
          userId: agency.ownerId,
          referenceType: 'agency_commission',
        },
        order: { createdAt: 'DESC' },
        take: 120,
      }),
    ]);

    const txs = recentRows
      .filter((t) => {
        const metaAgency =
          t.metadata && typeof t.metadata === 'object'
            ? String((t.metadata as Record<string, unknown>).agencyId || '')
            : '';
        return t.referenceId === agencyId || metaAgency === agencyId;
      })
      .slice(0, 30);

    const rate = await this.diamondUsdRate();
    const weekUsd = this.withUsdSlice(week, rate);
    const monthUsd = this.withUsdSlice(month, rate);
    const allUsd = this.withUsdSlice(allTime, rate);
    const ownerWallet = await this.walletsRepo.findOne({
      where: { userId: agency.ownerId },
    });
    // Single unified pool: the owner's commission lives in wallet.diamonds.
    const agencyDiamonds = 0; // deprecated dual-pool field, always 0
    const personalDiamonds = Number(ownerWallet?.diamonds || 0);

    const activeHosts = await this.membersRepo.count({
      where: {
        agencyId,
        isActive: true,
        status: AgencyMemberStatus.ACTIVE,
        role: AgencyRole.HOST,
      },
    });
    const prevMonthStart = new Date(
      Date.UTC(new Date().getUTCFullYear(), new Date().getUTCMonth() - 1, 1, 0, 0, 0),
    );
    const prevOnlyCommission = await this.sumOwnerCommissionBefore(
      agencyId,
      agency.ownerId,
      prevMonthStart,
      monthStart,
    );

    return {
      agencyId,
      name: agency.name,
      ownerId: agency.ownerId,
      commissionPercent: commissionPct,
      platformCutPercent: platformPct,
      hostSharePercent: hostPct,
      memberCount: memberIds.length,
      activeHosts,
      diamondUsdRate: rate,
      /** Back-compat flat totals = all-time */
      totals: {
        grossGiftsDiamonds: allTime.grossGiftsDiamonds,
        agencyTotalDiamonds: Number(agency.totalDiamonds || 0),
        ownerCommissionEarned: allTime.ownerCommissionEarned,
        ownerCommissionUsd: this.diamondsToUsd(allTime.ownerCommissionEarned, rate),
        estimatedHostShare: allTime.estimatedHostShare,
        estimatedHostShareUsd: this.diamondsToUsd(allTime.estimatedHostShare, rate),
        estimatedPlatformCut: allTime.estimatedPlatformCut,
        estimatedAgentShare: allTime.estimatedAgentShare,
      },
      periods: {
        week: weekUsd,
        month: monthUsd,
        allTime: allUsd,
      },
      monthCommissionDeltaPct: this.pctChange(
        month.ownerCommissionEarned,
        prevOnlyCommission,
      ),
      recentCommission: txs.map((t) => ({
        id: t.id,
        diamonds: Number(t.amount || 0),
        amountUsd: this.diamondsToUsd(Number(t.amount || 0), rate),
        description: t.description,
        createdAt: t.createdAt,
      })),
      /** Live withdrawable pools for the owner (never mixed). */
      available: {
        agencyDiamonds,
        agencyUsd: this.diamondsToUsd(agencyDiamonds, rate),
        personalDiamonds,
        personalUsd: this.diamondsToUsd(personalDiamonds, rate),
      },
      explanation: {
        ar: `أرباحك كلها في رصيد ماس واحد. عمولة الوكالة من هدايا أعضائها = ${commissionPct}% وتُضاف مباشرة لرصيدك، والمضيف يأخذ الباقي ${hostPct}%. سعر الماسة ≈ $${rate}.`,
      },
    };
  }

  private async sumOwnerCommissionBefore(
    agencyId: string,
    ownerId: string,
    from: Date,
    to: Date,
  ): Promise<number> {
    const qb = this.dataSource
      .getRepository(WalletTransaction)
      .createQueryBuilder('t')
      .select('COALESCE(SUM(t.amount), 0)', 'total')
      .where('t.userId = :ownerId', { ownerId })
      .andWhere('t.referenceType = :rt', { rt: 'agency_commission' })
      .andWhere(
        `(t."referenceId" = :agencyId OR CAST(t.metadata AS TEXT) LIKE :meta)`,
        { agencyId, meta: `%${agencyId}%` },
      )
      .andWhere('t.createdAt >= :from', { from })
      .andWhere('t.createdAt < :to', { to });
    const raw = await qb.getRawOne();
    return Number(raw?.total || 0);
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
      throw new BadRequestException('اختر مضيفاً آخر في الوكالة');
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
    if (!member) throw new BadRequestException('المستخدم ليس مضيفاً نشطاً في الوكالة');
    if (![AgencyRole.HOST, AgencyRole.MANAGER, AgencyRole.MEMBER].includes(member.role)) {
      throw new BadRequestException('التوزيع للمضيفين فقط');
    }

    const refId = `agency_dist:${agencyId}:${ownerId}:${toUserId}:${Date.now()}`;
    return this.dataSource.transaction(async (manager) => {
      const sender = await manager.findOne(Wallet, {
        where: { userId: ownerId },
        lock: { mode: 'pessimistic_write' },
      });
      const senderAgencyBal = Number(sender?.diamonds || 0);
      if (!sender || senderAgencyBal < diamonds) {
        throw new BadRequestException('رصيد الألماس غير كافٍ للتوزيع');
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
            agencyDiamonds: 0,
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

      // CLEAN ECONOMY: one diamond pool → move diamonds owner → host.
      sender.diamonds = senderAgencyBal - diamonds;
      receiver.diamonds = Number(receiver.diamonds || 0) + diamonds;
      await manager.save(sender);
      await manager.save(receiver);
      await manager.save(
        manager.create(WalletTransaction, {
          userId: ownerId,
          type: TransactionType.EXCHANGE,
          currency: CurrencyType.DIAMONDS,
          amount: -diamonds,
          balanceAfter: Number(sender.diamonds || 0),
          referenceType: 'agency_distribute_send',
          referenceId: refId,
          description: `توزيع أرباح وكالة`,
          metadata: { agencyId, toUserId, diamonds, stream: 'agency' },
        }),
      );
      await manager.save(
        manager.create(WalletTransaction, {
          userId: toUserId,
          type: TransactionType.EXCHANGE,
          currency: CurrencyType.DIAMONDS,
          amount: diamonds,
          balanceAfter: Number(receiver.diamonds || 0),
          referenceType: 'agency_distribute_recv',
          referenceId: refId,
          description: `استلام توزيع من صاحب الوكالة`,
          metadata: { agencyId, fromUserId: ownerId, diamonds, stream: 'agency' },
        }),
      );
      return {
        ok: true,
        diamonds,
        toUserId,
        senderBalance: Number(sender.diamonds || 0),
        receiverBalance: Number(receiver.diamonds || 0),
        stream: 'agency' as const,
      };
    });
  }

  private async assertActiveHost(agencyId: string, userId: string) {
    const member = await this.membersRepo.findOne({
      where: {
        agencyId,
        userId,
        isActive: true,
        status: AgencyMemberStatus.ACTIVE,
      },
    });
    if (!member) throw new ForbiddenException('لست عضواً نشطاً في الوكالة');
    if (![AgencyRole.HOST, AgencyRole.MEMBER, AgencyRole.MANAGER].includes(member.role)) {
      throw new ForbiddenException('طلب السحب متاح للمضيفين');
    }
    return member;
  }

  private async assertOwnerOrManager(agencyId: string, userId: string) {
    const agency = await this.agenciesRepo.findOne({ where: { id: agencyId } });
    if (!agency) throw new NotFoundException('Agency not found');
    if (agency.ownerId === userId) return { agency, role: AgencyRole.OWNER };
    if (await this.isPlatformSuper(userId)) {
      return { agency, role: AgencyRole.OWNER };
    }
    const member = await this.membersRepo.findOne({
      where: {
        agencyId,
        userId,
        isActive: true,
        status: AgencyMemberStatus.ACTIVE,
      },
    });
    if (!member || ![AgencyRole.OWNER, AgencyRole.MANAGER].includes(member.role)) {
      throw new ForbiddenException('صلاحية الوكالة فقط');
    }
    return { agency, role: member.role };
  }

  private mapPayout(row: AgencyPayoutRequest, host?: User | null) {
    return {
      id: row.id,
      agencyId: row.agencyId,
      hostUserId: row.hostUserId,
      diamonds: Number(row.diamonds),
      amountUsd: Number(row.amountUsd),
      diamondUsdRate: Number(row.diamondUsdRate),
      method: row.method,
      payoutDetails: row.payoutDetails || {},
      status: row.status,
      reviewNote: row.reviewNote,
      reviewedById: row.reviewedById,
      createdAt: row.createdAt,
      updatedAt: row.updatedAt,
      host: host
        ? {
            id: host.id,
            username: host.username,
            displayName: host.displayName,
            publicId: (host as any).publicId,
            avatarUrl: host.avatarUrl,
          }
        : null,
    };
  }

  /**
   * Host→agency cash-out closed: hosts withdraw agencyDiamonds via platform
   * (wallet.withdraw, source=agency_host). Owner commission uses agency_commission.
   */
  async createPayoutRequest(
    agencyId: string,
    hostUserId: string,
    dto: CreateAgencyPayoutRequestDto,
  ) {
    void agencyId;
    void hostUserId;
    void dto;
    throw new BadRequestException(
      'تم إيقاف طلب السحب عبر الوكالة. قدّمي سحب أرباح روم الوكالة مباشرةً من التطبيق إلى إدارة المنصة.',
    );
  }

  async listPayoutRequests(agencyId: string, viewerId: string, status?: string) {
    const agency = await this.agenciesRepo.findOne({ where: { id: agencyId } });
    if (!agency) throw new NotFoundException('Agency not found');
    let isStaff = agency.ownerId === viewerId;
    if (!isStaff) {
      const mgr = await this.membersRepo.findOne({
        where: {
          agencyId,
          userId: viewerId,
          isActive: true,
          status: AgencyMemberStatus.ACTIVE,
        },
      });
      isStaff = !!mgr && [AgencyRole.OWNER, AgencyRole.MANAGER].includes(mgr.role);
    }

    const where: any = { agencyId };
    if (!isStaff) {
      await this.assertActiveHost(agencyId, viewerId);
      where.hostUserId = viewerId;
    }
    if (status && ['pending', 'paid', 'rejected', 'cancelled'].includes(status)) {
      where.status = status;
    }

    const rows = await this.payoutsRepo.find({
      where,
      order: { createdAt: 'DESC' },
      take: 80,
    });
    const hostIds = [...new Set(rows.map((r) => r.hostUserId))];
    const hosts = hostIds.length
      ? await this.usersRepo.find({ where: { id: In(hostIds) } })
      : [];
    const hostMap = new Map(hosts.map((u) => [u.id, u]));
    return {
      items: rows.map((r) => this.mapPayout(r, hostMap.get(r.hostUserId) || null)),
      diamondUsdRate: await this.diamondUsdRate(),
    };
  }

  async markPayoutPaid(
    agencyId: string,
    requestId: string,
    reviewerId: string,
    note?: string,
  ) {
    await this.assertOwnerOrManager(agencyId, reviewerId);
    const row = await this.payoutsRepo.findOne({ where: { id: requestId, agencyId } });
    if (!row) throw new NotFoundException('الطلب غير موجود');
    if (row.status !== AgencyPayoutStatus.PENDING) {
      throw new BadRequestException('الطلب ليس معلّقاً');
    }
    row.status = AgencyPayoutStatus.PAID;
    row.reviewedById = reviewerId;
    row.reviewNote = note?.trim() || 'تم التحويل للمضيفة';
    await this.payoutsRepo.save(row);

    try {
      await this.notificationsService.create({
        userId: row.hostUserId,
        type: NotificationType.AGENCY,
        title: 'تم تحويل أرباحك',
        body: `حوّلت الوكالة لك $${Number(row.amountUsd).toFixed(2)}`,
        data: { agencyId, payoutRequestId: row.id },
      });
    } catch {
      /* ignore */
    }
    return this.mapPayout(row);
  }

  private async refundHeldPayout(
    row: AgencyPayoutRequest,
    status: AgencyPayoutStatus,
    reviewerId: string,
    note: string,
  ) {
    const diamonds = Number(row.diamonds);
    const refId = `agency_payout_refund:${row.id}`;
    await this.dataSource.transaction(async (manager) => {
      const wallet = await manager.findOne(Wallet, {
        where: { userId: row.hostUserId },
        lock: { mode: 'pessimistic_write' },
      });
      if (!wallet) throw new NotFoundException('محفظة المضيفة');
      wallet.diamonds = Number(wallet.diamonds || 0) + diamonds;
      await manager.save(wallet);
      await manager.save(
        manager.create(WalletTransaction, {
          userId: row.hostUserId,
          type: TransactionType.REFUND,
          currency: CurrencyType.DIAMONDS,
          amount: diamonds,
          balanceAfter: Number(wallet.diamonds || 0),
          referenceType: 'agency_payout_refund',
          referenceId: refId,
          description: note,
          metadata: { agencyId: row.agencyId, requestId: row.id, stream: 'agency' },
        }),
      );
      row.status = status;
      row.reviewedById = reviewerId;
      row.reviewNote = note;
      await manager.save(row);
    });
    return this.mapPayout(row);
  }

  async rejectPayoutRequest(
    agencyId: string,
    requestId: string,
    reviewerId: string,
    note?: string,
  ) {
    await this.assertOwnerOrManager(agencyId, reviewerId);
    const row = await this.payoutsRepo.findOne({ where: { id: requestId, agencyId } });
    if (!row) throw new NotFoundException('الطلب غير موجود');
    if (row.status !== AgencyPayoutStatus.PENDING) {
      throw new BadRequestException('الطلب ليس معلّقاً');
    }
    const mapped = await this.refundHeldPayout(
      row,
      AgencyPayoutStatus.REJECTED,
      reviewerId,
      note?.trim() || 'مرفوض من الوكالة',
    );
    try {
      await this.notificationsService.create({
        userId: row.hostUserId,
        type: NotificationType.AGENCY,
        title: 'رفض طلب سحب الأرباح',
        body: note?.trim() || 'تم رفض طلب السحب وإرجاع الماس لمحفظتك',
        data: { agencyId, payoutRequestId: row.id },
      });
    } catch {
      /* ignore */
    }
    return mapped;
  }

  async cancelPayoutRequest(agencyId: string, requestId: string, hostUserId: string) {
    const row = await this.payoutsRepo.findOne({ where: { id: requestId, agencyId } });
    if (!row) throw new NotFoundException('الطلب غير موجود');
    if (row.hostUserId !== hostUserId) throw new ForbiddenException();
    if (row.status !== AgencyPayoutStatus.PENDING) {
      throw new BadRequestException('لا يمكن إلغاء هذا الطلب');
    }
    return this.refundHeldPayout(
      row,
      AgencyPayoutStatus.CANCELLED,
      hostUserId,
      'ألغاه المضيف',
    );
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
      ![AgencyRole.OWNER, AgencyRole.MANAGER].includes(membership.role)
    ) {
      throw new ForbiddenException('Active owner or manager membership required to go live');
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
      [AgencyRole.OWNER, AgencyRole.MANAGER].includes(ownMembership.role)
      ? await this.ensurePermanentRoom(agencyId, userId)
      : await this.ensurePermanentRoom(agencyId);
    return this.roomsService.join(room.id, userId, {});
  }

  /** Open/reopen the authenticated admin's own persistent room in this agency. */
  async openAgencyRoom(agencyId: string, userId: string, dto: CreateRoomDto) {
    const superStaff = await this.isPlatformSuper(userId);
    const membership = await this.membersRepo.findOne({
      where: {
        agencyId,
        userId,
        status: AgencyMemberStatus.ACTIVE,
        isActive: true,
      },
      relations: ['agency'],
    });
    const agency =
      membership?.agency ||
      (await this.agenciesRepo.findOne({ where: { id: agencyId } }));
    if (!agency || agency.status !== AgencyStatus.ACTIVE) {
      throw new ForbiddenException('فتح بث الوكالة لمالك الوكالة أو الأدمن فقط');
    }
    if (
      !superStaff &&
      (!membership ||
        ![AgencyRole.OWNER, AgencyRole.MANAGER].includes(membership.role))
    ) {
      throw new ForbiddenException('فتح بث الوكالة لمالك الوكالة أو الأدمن فقط');
    }
    const agencyName = String(agency.name || '').trim() || 'وكالة';
    // Super can open any agency brand room even when not a member (platform SOS / support).
    if (superStaff) {
      const room = await this.ensurePermanentRoom(agencyId);
      room.title = agencyName;
      await this.roomsRepo.save(room);
      return this.roomsService.forceOpenAgencyRoom(room.id, userId);
    }
    // Agency owner/manager: open own agency host room with agency branding.
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
    if (agency.ownerId !== userId && !(await this.isPlatformSuper(userId))) {
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

  private async assertOwner(agencyId: string, actorId: string) {
    const agency = await this.agenciesRepo.findOne({ where: { id: agencyId } });
    if (!agency) throw new NotFoundException('Agency not found');
    if (agency.ownerId === actorId) return;
    if (await this.isPlatformSuper(actorId)) return;
    throw new ForbiddenException('فقط صاحب الوكالة يمكنه تعديل الاسم والوصف');
  }

  private async isPlatformSuper(userId: string): Promise<boolean> {
    if (!userId) return false;
    try {
      const user = await this.usersRepo.findOne({
        where: { id: userId },
        select: ['id', 'isAdmin', 'staffRole'] as any,
      });
      return normalizeStaffRole(user as any) === 'super';
    } catch {
      return false;
    }
  }

  private async assertManager(agencyId: string, actorId: string) {
    if (await this.isPlatformSuper(actorId)) return;
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
