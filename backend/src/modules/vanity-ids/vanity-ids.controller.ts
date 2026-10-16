import {
  BadRequestException,
  Body,
  Controller,
  Delete,
  Get,
  NotFoundException,
  Param,
  Patch,
  Post,
  Query,
  UseGuards,
} from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { InjectRepository } from '@nestjs/typeorm';
import { IsNull, Repository } from 'typeorm';
import { VanityId, VanityIdStatus } from '../../database/entities/vanity-id.entity';
import { User } from '../../database/entities/user.entity';
import { Wallet } from '../../database/entities/wallet.entity';
import { Room, RoomKind } from '../../database/entities/room.entity';
import { Agency } from '../../database/entities/agency.entity';
import {
  WalletTransaction,
  TransactionType,
  CurrencyType,
} from '../../database/entities/wallet-transaction.entity';
import { CurrentUser, Public } from '../../common/decorators';
import { JwtAuthGuard } from '../../common/guards/jwt-auth.guard';
import { AdminGuard } from '../../common/guards/admin.guard';

const VANITY_LEASE_DAYS = 30;

@ApiTags('Vanity IDs')
@Controller('vanity-ids')
export class VanityIdsController {
  constructor(
    @InjectRepository(VanityId) private readonly vanityRepo: Repository<VanityId>,
    @InjectRepository(User) private readonly usersRepo: Repository<User>,
    @InjectRepository(Wallet) private readonly walletsRepo: Repository<Wallet>,
    @InjectRepository(Room) private readonly roomsRepo: Repository<Room>,
    @InjectRepository(Agency) private readonly agenciesRepo: Repository<Agency>,
    @InjectRepository(WalletTransaction)
    private readonly txRepo: Repository<WalletTransaction>,
  ) {}

  /** Public read-only appearance contract used by all app ID labels. */
  @Public()
  @Get('style/:publicId')
  @ApiOperation({ summary: 'Read vanity ID display style' })
  async publicStyle(@Param('publicId') publicId: string) {
    const normalized = String(publicId || '').trim();
    if (!/^\\d{3,16}$/.test(normalized)) {
      throw new BadRequestException('Invalid public ID');
    }
    const row = await this.vanityRepo.findOne({ where: { publicId: normalized } });
    if (!row) {
      return { publicId: normalized, styleMode: 'static', styleColor1: '#52E8FF', styleColor2: '#C084FC', styleColor3: '#FF7AC8', styleGlow: false };
    }
    return {
      publicId: normalized,
      styleMode: row.styleMode === 'animated' ? 'animated' : 'static',
      styleColor1: row.styleColor1 || '#52E8FF',
      styleColor2: row.styleColor2 || '#C084FC',
      styleColor3: row.styleColor3 || '#FF7AC8',
      styleGlow: Boolean(row.styleGlow),
    };
  }

  @Public()
  @Get()
  @ApiOperation({ summary: 'List available / catalog vanity IDs' })
  async list(@Query('status') status?: string) {
    const where: any = {};
    if (status) where.status = status;
    else where.status = VanityIdStatus.AVAILABLE;
    // Never expose disabled rows to the client catalog unless explicitly requested.
    if (where.status === VanityIdStatus.DISABLED) {
      return { items: [], leaseDays: VANITY_LEASE_DAYS };
    }
    const items = (await this.vanityRepo.find({
      where,
      order: { priceCoins: 'ASC', publicId: 'ASC' },
      take: 200,
    })).filter((i) => /^\d{6}$/.test(i.publicId));
    return {
      items: items.map((i) => ({
        ...i,
        leaseDays: VANITY_LEASE_DAYS,
        renewPriceCoins: Math.max(0, Math.ceil(Number(i.priceCoins || 0) / 2)),
      })),
      leaseDays: VANITY_LEASE_DAYS,
    };
  }

  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth()
  @Get('mine')
  @ApiOperation({ summary: 'Current vanity lease for the signed-in user' })
  async mine(@CurrentUser('sub') userId: string) {
    const row = await this.vanityRepo.findOne({
      where: { ownerUserId: userId, status: VanityIdStatus.OWNED },
      order: { purchasedAt: 'DESC' },
    });
    if (!row) return { item: null, leaseDays: VANITY_LEASE_DAYS, canApplyToAgency: await this.agenciesRepo.exist({ where: { ownerId: userId } }) };
    const active = !row.expiresAt || row.expiresAt > new Date();
    return {
      item: {
        ...row,
        active,
        leaseDays: VANITY_LEASE_DAYS,
        renewPriceCoins: Math.max(0, Math.ceil(Number(row.priceCoins || 0) / 2)),
      },
      leaseDays: VANITY_LEASE_DAYS,
      canApplyToAgency: await this.agenciesRepo.exist({ where: { ownerId: userId } }),
    };
  }

  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth()
  @Post('id/:publicId/reserve')
  @ApiOperation({ summary: 'Reserve a vanity ID for 15 minutes' })
  async reserve(
    @CurrentUser('sub') userId: string,
    @Param('publicId') publicId: string,
  ) {
    if (!/^\d{6}$/.test(publicId)) {
      throw new BadRequestException('الآيدي يجب أن يتكون من 6 أرقام');
    }
    const row = await this.vanityRepo.findOne({ where: { publicId } });
    if (!row) throw new NotFoundException('ID not found');
    if (row.status === VanityIdStatus.DISABLED) {
      throw new BadRequestException('ID is disabled');
    }
    if (row.status === VanityIdStatus.OWNED && row.ownerUserId !== userId) {
      throw new BadRequestException('ID already owned');
    }
    if (
      row.status === VanityIdStatus.RESERVED &&
      row.reservedUntil &&
      row.reservedUntil > new Date() &&
      row.ownerUserId !== userId
    ) {
      throw new BadRequestException('ID is reserved by another user');
    }
    if (row.status === VanityIdStatus.OWNED && row.ownerUserId === userId) {
      return row; // renew path — already owned
    }
    row.status = VanityIdStatus.RESERVED;
    row.ownerUserId = userId;
    row.reservedUntil = new Date(Date.now() + 15 * 60_000);
    await this.vanityRepo.save(row);
    return row;
  }

  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth()
  @Post('id/:publicId/purchase')
  @ApiOperation({ summary: 'Purchase or renew a vanity ID (30-day lease)' })
  async purchase(
    @CurrentUser('sub') userId: string,
    @Param('publicId') publicId: string,
  ) {
    if (!/^\d{6}$/.test(publicId)) {
      throw new BadRequestException('الآيدي يجب أن يتكون من 6 أرقام');
    }
    const row = await this.vanityRepo.findOne({ where: { publicId } });
    if (!row) throw new NotFoundException('ID not found');
    if (row.status === VanityIdStatus.DISABLED) {
      throw new BadRequestException('ID is disabled');
    }
    if (row.status === VanityIdStatus.OWNED && row.ownerUserId !== userId) {
      throw new BadRequestException('ID already owned');
    }
    if (
      row.status === VanityIdStatus.RESERVED &&
      row.ownerUserId &&
      row.ownerUserId !== userId &&
      row.reservedUntil &&
      row.reservedUntil > new Date()
    ) {
      throw new BadRequestException('ID is reserved by another user');
    }
    const now = new Date();
    const fullPrice = Math.max(0, Math.floor(Number(row.priceCoins) || 0));
    const isOwner = row.status === VanityIdStatus.OWNED && row.ownerUserId === userId;
    const activeLease =
      isOwner && row.expiresAt != null && row.expiresAt.getTime() > now.getTime();
    const renew = activeLease;
    const price = renew ? Math.ceil(fullPrice / 2) : fullPrice;

    const wallet = await this.walletsRepo.findOne({ where: { userId } });
    if (price > 0 && (!wallet || Number(wallet.coins) < price)) {
      throw new BadRequestException('Insufficient coins');
    }
    if (price > 0 && wallet) {
      wallet.coins = Number(wallet.coins) - price;
      await this.walletsRepo.save(wallet);
      await this.txRepo.save(
        this.txRepo.create({
          userId,
          type: TransactionType.GIFT_SEND,
          currency: CurrencyType.COINS,
          amount: -price,
          balanceAfter: Number(wallet.coins),
          referenceType: 'vanity_id',
          referenceId: row.id,
          description: renew
            ? `تجديد آي دي مميز ${publicId} (30 يوم)`
            : `شراء آي دي مميز ${publicId} (30 يوم)`,
        }),
      );
    }

    const user = await this.usersRepo.findOne({ where: { id: userId } });
    if (!user) throw new NotFoundException('User not found');

    // Purchase owns the ID but deliberately does not equip it anywhere.
    // The user chooses account, personal room, or (if owner) agency in the apply dialog.

    const baseMs =
      renew && row.expiresAt && row.expiresAt.getTime() > now.getTime()
        ? row.expiresAt.getTime()
        : now.getTime();
    row.expiresAt = new Date(baseMs + VANITY_LEASE_DAYS * 24 * 60 * 60 * 1000);
    row.status = VanityIdStatus.OWNED;
    row.ownerUserId = userId;
    row.purchasedAt = now;
    row.reservedUntil = null;
    await this.vanityRepo.save(row);

    return {
      publicId,
      priceCoins: price,
      fullPriceCoins: fullPrice,
      renew,
      leaseDays: VANITY_LEASE_DAYS,
      expiresAt: row.expiresAt,
      userId,
      targetType: row.targetType || null,
      targetId: row.targetId || null,
      canApplyToAgency: await this.agenciesRepo.exist({ where: { ownerId: userId } }),
    };
  }

  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth()
  @Post('id/:publicId/apply')
  @ApiOperation({ summary: 'Equip a purchased six-digit ID to account, personal room, or owned agency' })
  async apply(
    @CurrentUser('sub') userId: string,
    @Param('publicId') publicId: string,
    @Body() body: { targetType?: string },
  ) {
    if (!/^\d{6}$/.test(publicId)) {
      throw new BadRequestException('الآيدي يجب أن يتكون من 6 أرقام');
    }
    const row = await this.vanityRepo.findOne({ where: { publicId } });
    if (!row) throw new NotFoundException('ID not found');
    if (row.status !== VanityIdStatus.OWNED || row.ownerUserId !== userId) {
      throw new BadRequestException('يجب شراء هذا الآيدي أولاً');
    }
    if (row.expiresAt && row.expiresAt <= new Date()) {
      throw new BadRequestException('انتهت صلاحية هذا الآيدي');
    }
    const targetType = String(body?.targetType || '');
    if (!['account', 'room', 'agency'].includes(targetType)) {
      throw new BadRequestException('وجهة الآيدي غير صحيحة');
    }

    // Move the ID rather than copy it: restore the old target before equipping another.
    // This also repairs legacy rows where the vanity ID was left on both account and room.
    if (row.targetType && row.targetType !== targetType) {
      await this.restoreAppliedTarget(row);
    }
    if (targetType !== 'account') {
      const user = await this.usersRepo.findOne({ where: { id: userId } });
      if (user?.publicId === publicId) {
        user.publicId = row.previousPublicId || user.id.replace(/-/g, '').slice(0, 8);
        await this.usersRepo.save(user);
        row.previousPublicId = null;
      }
    }
    if (targetType === 'account') {
      const user = await this.usersRepo.findOne({ where: { id: userId } });
      if (!user) throw new NotFoundException('User not found');
      const conflict = await this.usersRepo.findOne({ where: { publicId } });
      if (conflict && conflict.id !== userId) throw new BadRequestException('الآيدي مستخدم لحساب آخر');
      row.previousPublicId = user.publicId && user.publicId !== publicId ? user.publicId : row.previousPublicId;
      user.publicId = publicId;
      await this.usersRepo.save(user);
      row.targetType = 'account';
      row.targetId = userId;
    } else if (targetType === 'room') {
      const room = await this.roomsRepo.findOne({ where: { hostId: userId, agencyId: IsNull(), roomKind: RoomKind.STANDARD }, order: { createdAt: 'DESC' } });
      if (!room) throw new BadRequestException('افتح الروم الشخصي أولاً');
      const conflict = await this.roomsRepo.findOne({ where: { publicId } });
      if (conflict && conflict.id !== room.id) throw new BadRequestException('الآيدي مستخدم لروم آخر');
      if (row.targetType !== 'room' || row.targetId !== room.id) {
        row.previousTargetPublicId = room.publicId && room.publicId !== publicId ? room.publicId : null;
      }
      room.publicId = publicId;
      await this.roomsRepo.save(room);
      row.targetType = 'room';
      row.targetId = room.id;
    } else {
      const agency = await this.agenciesRepo.findOne({ where: { ownerId: userId } });
      if (!agency) throw new BadRequestException('هذا الخيار متاح لصاحب الوكالة فقط');
      const conflict = await this.agenciesRepo.findOne({ where: { publicId } });
      if (conflict && conflict.id !== agency.id) throw new BadRequestException('الآيدي مستخدم لوكالة أخرى');
      if (row.targetType !== 'agency' || row.targetId !== agency.id) {
        row.previousTargetPublicId = agency.publicId && agency.publicId !== publicId ? agency.publicId : null;
      }
      agency.publicId = publicId;
      await this.agenciesRepo.save(agency);
      row.targetType = 'agency';
      row.targetId = agency.id;
    }
    await this.vanityRepo.save(row);
    return { success: true, publicId, targetType, targetId: row.targetId };
  }

  // ─── Admin ────────────────────────────────────────────────────────

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('admin')
  @ApiOperation({ summary: 'Create / upsert vanity IDs from dashboard' })
  async adminUpsert(
    @Body()
    body: { publicId: string; priceCoins?: number; status?: string; styleMode?: string; styleColor1?: string; styleColor2?: string; styleColor3?: string; styleGlow?: boolean },
  ) {
    const publicId = String(body.publicId || '').trim();
    if (!/^\d{6}$/.test(publicId)) {
      throw new BadRequestException(
        'الآيدي يجب أن يتكون من 6 أرقام',
      );
    }
    let row = await this.vanityRepo.findOne({ where: { publicId } });
    if (!row) {
      row = this.vanityRepo.create({
        publicId,
        status: VanityIdStatus.AVAILABLE,
        priceCoins: Math.max(0, Math.floor(Number(body.priceCoins) || 0)),
        ...this.parseStyle(body),
      });
    } else {
      if (body.priceCoins != null) {
        row.priceCoins = Math.max(0, Math.floor(Number(body.priceCoins)));
      }
      if (body.status) {
        this.assertAdminStatus(body.status);
        row.status = body.status as VanityIdStatus;
      }
    }
    return this.vanityRepo.save(row);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('admin/all')
  @ApiOperation({ summary: 'List all vanity IDs for dashboard' })
  adminList() {
    return this.vanityRepo.find({ order: { createdAt: 'DESC' }, take: 500 });
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Patch('admin/:id')
  @ApiOperation({ summary: 'Update vanity price / status (dashboard)' })
  async adminPatch(
    @Param('id') id: string,
    @Body() body: { priceCoins?: number; status?: string; styleMode?: string; styleColor1?: string; styleColor2?: string; styleColor3?: string; styleGlow?: boolean },
  ) {
    const row = await this.requireRow(id);
    if (body.priceCoins != null) {
      row.priceCoins = Math.max(0, Math.floor(Number(body.priceCoins)));
    }
    Object.assign(row, this.parseStyle(body));
    if (body.status) {
      this.assertAdminStatus(body.status);
      const next = body.status as VanityIdStatus;
      // Moving away from owned/reserved → reclaim publicId if needed.
      if (
        (row.status === VanityIdStatus.OWNED ||
          row.status === VanityIdStatus.RESERVED) &&
        (next === VanityIdStatus.AVAILABLE || next === VanityIdStatus.DISABLED)
      ) {
        await this.restoreOwnerPublicId(row);
        this.clearLeaseFields(row);
      }
      row.status = next;
    }
    return this.vanityRepo.save(row);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('admin/:id/release')
  @ApiOperation({
    summary: 'Force-release ownership back to market (restore previous publicId)',
  })
  async adminRelease(@Param('id') id: string) {
    const row = await this.requireRow(id);
    await this.restoreOwnerPublicId(row);
    this.clearLeaseFields(row);
    row.status = VanityIdStatus.AVAILABLE;
    return this.vanityRepo.save(row);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('admin/:id/disable')
  @ApiOperation({ summary: 'Disable ID: hide from shop; release owner if any' })
  async adminDisable(@Param('id') id: string) {
    const row = await this.requireRow(id);
    if (
      row.status === VanityIdStatus.OWNED ||
      row.status === VanityIdStatus.RESERVED
    ) {
      await this.restoreOwnerPublicId(row);
      this.clearLeaseFields(row);
    }
    row.status = VanityIdStatus.DISABLED;
    return this.vanityRepo.save(row);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('admin/:id/enable')
  @ApiOperation({ summary: 'Re-enable a disabled vanity ID for sale' })
  async adminEnable(@Param('id') id: string) {
    const row = await this.requireRow(id);
    if (row.status === VanityIdStatus.OWNED) {
      throw new BadRequestException('Cannot enable while owned — release first');
    }
    this.clearLeaseFields(row);
    row.status = VanityIdStatus.AVAILABLE;
    return this.vanityRepo.save(row);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Delete('admin/:id')
  @ApiOperation({ summary: 'Delete vanity ID (releases owner first if needed)' })
  async adminDelete(@Param('id') id: string) {
    const row = await this.requireRow(id);
    if (
      row.status === VanityIdStatus.OWNED ||
      row.status === VanityIdStatus.RESERVED
    ) {
      await this.restoreOwnerPublicId(row);
    }
    await this.vanityRepo.remove(row);
    return { ok: true, id };
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('admin/assign')
  @ApiOperation({ summary: 'Grant a vanity ID to a user for 7, 15, 30 days or permanently' })
  async adminAssign(
    @Body() body: { userId?: string; publicId?: string; days?: number; permanent?: boolean },
  ) {
    const userId = String(body?.userId || '').trim();
    const publicId = String(body?.publicId || '').trim();
    const permanent = body?.permanent === true;
    const days = Number(body?.days || 7);
    if (!userId || !/^[0-9]{3,12}$/.test(publicId)) {
      throw new BadRequestException('userId is required and publicId must contain 3–12 digits');
    }
    if (!permanent && ![7, 15, 30].includes(days)) {
      throw new BadRequestException('days must be 7, 15 or 30, or permanent=true');
    }
    const user = await this.usersRepo.findOne({ where: { id: userId } });
    if (!user) throw new NotFoundException('User not found');
    const taken = await this.usersRepo.findOne({ where: { publicId } });
    if (taken && taken.id !== userId) {
      throw new BadRequestException('This public ID is already used by another account');
    }

    let target = await this.vanityRepo.findOne({ where: { publicId } });
    if (!target) {
      target = this.vanityRepo.create({
        publicId,
        priceCoins: 0,
        status: VanityIdStatus.AVAILABLE,
      });
    }
    if (target.ownerUserId && target.ownerUserId !== userId && target.status === VanityIdStatus.OWNED) {
      throw new BadRequestException('This vanity ID is already owned; release it explicitly before reassigning');
    }
    if (target.ownerUserId && target.ownerUserId !== userId) {
      this.clearLeaseFields(target);
      target.status = VanityIdStatus.AVAILABLE;
      await this.vanityRepo.save(target);
    }

    const current = await this.vanityRepo.findOne({
      where: { ownerUserId: userId, status: VanityIdStatus.OWNED },
    });
    const now = new Date();
    let previousPublicId = user.publicId;
    let baseTime = now.getTime();
    if (current && current.id === target.id) {
      previousPublicId = current.previousPublicId || user.publicId;
      if (!permanent && current.expiresAt && current.expiresAt > now) {
        baseTime = current.expiresAt.getTime();
      }
    } else if (current) {
      previousPublicId = current.previousPublicId || user.publicId;
      await this.restoreOwnerPublicId(current);
      this.clearLeaseFields(current);
      current.status = VanityIdStatus.AVAILABLE;
      await this.vanityRepo.save(current);
      const refreshed = await this.usersRepo.findOne({ where: { id: userId } });
      if (refreshed) user.publicId = refreshed.publicId;
      previousPublicId = user.publicId;
    }

    if (!target.previousPublicId || target.ownerUserId !== userId) {
      target.previousPublicId = previousPublicId && previousPublicId !== publicId
        ? previousPublicId
        : null;
    }
    user.publicId = publicId;
    await this.usersRepo.save(user);
    target.ownerUserId = userId;
    target.status = VanityIdStatus.OWNED;
    target.reservedUntil = null;
    target.purchasedAt = now;
    target.expiresAt = permanent ? null : new Date(baseTime + days * 24 * 60 * 60 * 1000);
    const saved = await this.vanityRepo.save(target);
    return { granted: true, item: saved, permanent, days: permanent ? null : days };
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('admin/:id/revoke')
  @ApiOperation({ summary: 'Revoke a granted vanity ID and restore the previous public ID' })
  async adminRevoke(@Param('id') id: string) {
    const row = await this.requireRow(id);
    await this.restoreOwnerPublicId(row);
    this.clearLeaseFields(row);
    row.status = VanityIdStatus.AVAILABLE;
    await this.vanityRepo.save(row);
    return { revoked: true, id };
  }

  // ─── helpers ──────────────────────────────────────────────────────

  private async requireRow(id: string): Promise<VanityId> {
    const row = await this.vanityRepo.findOne({ where: { id } });
    if (!row) throw new NotFoundException('Vanity ID not found');
    return row;
  }

  private parseStyle(body: {
    styleMode?: string; styleColor1?: string; styleColor2?: string; styleColor3?: string; styleGlow?: boolean;
  }): Partial<VanityId> {
    const result: Partial<VanityId> = {};
    if (body.styleMode != null) {
      if (!['static', 'animated'].includes(body.styleMode)) {
        throw new BadRequestException('styleMode must be static or animated');
      }
      result.styleMode = body.styleMode as 'static' | 'animated';
    }
    for (const key of ['styleColor1', 'styleColor2', 'styleColor3'] as const) {
      const value = body[key];
      if (value != null) {
        if (!/^#[0-9a-fA-F]{6}$/.test(value)) {
          throw new BadRequestException(`${key} must be a hex color such as #52E8FF`);
        }
        result[key] = value.toUpperCase();
      }
    }
    if (body.styleGlow != null) result.styleGlow = Boolean(body.styleGlow);
    return result;
  }

  private assertAdminStatus(status: string) {
    const allowed = new Set(Object.values(VanityIdStatus));
    if (!allowed.has(status as VanityIdStatus)) {
      throw new BadRequestException(`Invalid status: ${status}`);
    }
  }

  private clearLeaseFields(row: VanityId) {
    row.ownerUserId = null;
    row.expiresAt = null;
    row.previousPublicId = null;
    row.previousTargetPublicId = null;
    row.targetType = null;
    row.targetId = null;
    row.purchasedAt = null;
    row.reservedUntil = null;
  }

  /** Restore the previous target identity before this vanity ID is moved or released. */
  private async restoreAppliedTarget(row: VanityId) {
    if (!row.ownerUserId || !row.targetType || !row.targetId) return;
    if (row.targetType === 'account') {
      const user = await this.usersRepo.findOne({ where: { id: row.targetId } });
      if (user?.publicId === row.publicId) {
        user.publicId = row.previousPublicId || user.id.replace(/-/g, '').slice(0, 8);
        await this.usersRepo.save(user);
      }
      row.previousPublicId = null;
    } else if (row.targetType === 'room') {
      const room = await this.roomsRepo.findOne({ where: { id: row.targetId } });
      if (room?.publicId === row.publicId) {
        room.publicId = row.previousTargetPublicId || null;
        await this.roomsRepo.save(room);
      }
      row.previousTargetPublicId = null;
    } else if (row.targetType === 'agency') {
      const agency = await this.agenciesRepo.findOne({ where: { id: row.targetId } });
      if (agency?.publicId === row.publicId) {
        agency.publicId = row.previousTargetPublicId || null;
        await this.agenciesRepo.save(agency);
      }
      row.previousTargetPublicId = null;
    }
    row.targetType = null;
    row.targetId = null;
  }

  /** If the owner currently displays this vanity, restore their previous account ID. */
  private async restoreOwnerPublicId(row: VanityId) {
    await this.restoreAppliedTarget(row);
    if (!row.ownerUserId) return;
    const user = await this.usersRepo.findOne({ where: { id: row.ownerUserId } });
    if (!user) return;
    if (user.publicId === row.publicId) {
      user.publicId = row.previousPublicId || user.id.replace(/-/g, '').slice(0, 8);
      await this.usersRepo.save(user);
    }
  }
}
