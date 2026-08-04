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
import { Repository } from 'typeorm';
import { VanityId, VanityIdStatus } from '../../database/entities/vanity-id.entity';
import { User } from '../../database/entities/user.entity';
import { Wallet } from '../../database/entities/wallet.entity';
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
    @InjectRepository(WalletTransaction)
    private readonly txRepo: Repository<WalletTransaction>,
  ) {}

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
    const items = await this.vanityRepo.find({
      where,
      order: { priceCoins: 'ASC', publicId: 'ASC' },
      take: 200,
    });
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
    if (!row) return { item: null, leaseDays: VANITY_LEASE_DAYS };
    const active = !row.expiresAt || row.expiresAt > new Date();
    return {
      item: {
        ...row,
        active,
        leaseDays: VANITY_LEASE_DAYS,
        renewPriceCoins: Math.max(0, Math.ceil(Number(row.priceCoins || 0) / 2)),
      },
      leaseDays: VANITY_LEASE_DAYS,
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
    const taken = await this.usersRepo.findOne({ where: { publicId } });
    if (taken && taken.id !== userId) {
      throw new BadRequestException('This public ID is already in use');
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

    if (!renew) {
      // Capture previous public ID once when first applying this vanity.
      if (!row.previousPublicId || user.publicId !== publicId) {
        row.previousPublicId =
          user.publicId && user.publicId !== publicId
            ? user.publicId
            : row.previousPublicId;
      }
      user.publicId = publicId;
      await this.usersRepo.save(user);
    }

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
    };
  }

  // ─── Admin ────────────────────────────────────────────────────────

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('admin')
  @ApiOperation({ summary: 'Create / upsert vanity IDs from dashboard' })
  async adminUpsert(
    @Body()
    body: { publicId: string; priceCoins?: number; status?: string },
  ) {
    const publicId = String(body.publicId || '').trim();
    if (!/^\d{3,12}$/.test(publicId)) {
      throw new BadRequestException(
        'الآي دي يجب أن يكون أرقاماً فقط من 3 إلى 12 خانة',
      );
    }
    let row = await this.vanityRepo.findOne({ where: { publicId } });
    if (!row) {
      row = this.vanityRepo.create({
        publicId,
        status: VanityIdStatus.AVAILABLE,
        priceCoins: Math.max(0, Math.floor(Number(body.priceCoins) || 0)),
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
    @Body() body: { priceCoins?: number; status?: string },
  ) {
    const row = await this.requireRow(id);
    if (body.priceCoins != null) {
      row.priceCoins = Math.max(0, Math.floor(Number(body.priceCoins)));
    }
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

  // ─── helpers ──────────────────────────────────────────────────────

  private async requireRow(id: string): Promise<VanityId> {
    const row = await this.vanityRepo.findOne({ where: { id } });
    if (!row) throw new NotFoundException('Vanity ID not found');
    return row;
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
    row.purchasedAt = null;
    row.reservedUntil = null;
  }

  /** If the owner currently displays this vanity, restore their previous publicId. */
  private async restoreOwnerPublicId(row: VanityId) {
    if (!row.ownerUserId) return;
    const user = await this.usersRepo.findOne({ where: { id: row.ownerUserId } });
    if (!user) return;
    if (user.publicId === row.publicId) {
      user.publicId =
        row.previousPublicId || user.id.replace(/-/g, '').slice(0, 8);
      await this.usersRepo.save(user);
    }
  }
}
