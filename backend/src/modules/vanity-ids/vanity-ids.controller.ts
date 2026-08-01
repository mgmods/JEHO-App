import {
  BadRequestException,
  Body,
  Controller,
  Get,
  NotFoundException,
  Param,
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
    const items = await this.vanityRepo.find({
      where,
      order: { priceCoins: 'ASC', publicId: 'ASC' },
      take: 200,
    });
    return { items };
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
    if (row.status === VanityIdStatus.OWNED) {
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
    row.status = VanityIdStatus.RESERVED;
    row.ownerUserId = userId;
    row.reservedUntil = new Date(Date.now() + 15 * 60_000);
    await this.vanityRepo.save(row);
    return row;
  }

  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth()
  @Post('id/:publicId/purchase')
  @ApiOperation({ summary: 'Purchase a vanity ID and apply it to your account' })
  async purchase(
    @CurrentUser('sub') userId: string,
    @Param('publicId') publicId: string,
  ) {
    const row = await this.vanityRepo.findOne({ where: { publicId } });
    if (!row) throw new NotFoundException('ID not found');
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
    const price = Math.max(0, Number(row.priceCoins) || 0);
    const wallet = await this.walletsRepo.findOne({ where: { userId } });
    if (!wallet || Number(wallet.coins) < price) {
      throw new BadRequestException('Insufficient coins');
    }
    if (price > 0) {
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
          description: `شراء آي دي مميز ${publicId}`,
        }),
      );
    }
    const user = await this.usersRepo.findOne({ where: { id: userId } });
    if (!user) throw new NotFoundException('User not found');
    user.publicId = publicId;
    await this.usersRepo.save(user);
    row.status = VanityIdStatus.OWNED;
    row.ownerUserId = userId;
    row.purchasedAt = new Date();
    row.reservedUntil = null;
    await this.vanityRepo.save(row);
    return { publicId, priceCoins: price, userId };
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('admin')
  @ApiOperation({ summary: 'Create / upsert vanity IDs from dashboard' })
  async adminUpsert(
    @Body()
    body: { publicId: string; priceCoins?: number; status?: string },
  ) {
    const publicId = String(body.publicId || '').trim();
    if (!/^\d{4,12}$/.test(publicId)) {
      throw new BadRequestException('publicId must be 4-12 digits');
    }
    let row = await this.vanityRepo.findOne({ where: { publicId } });
    if (!row) {
      row = this.vanityRepo.create({
        publicId,
        status: VanityIdStatus.AVAILABLE,
        priceCoins: Math.max(0, Math.floor(Number(body.priceCoins) || 0)),
      });
    } else {
      if (body.priceCoins != null) row.priceCoins = Math.max(0, Math.floor(Number(body.priceCoins)));
      if (body.status) row.status = body.status as VanityIdStatus;
    }
    return this.vanityRepo.save(row);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('admin/all')
  adminList() {
    return this.vanityRepo.find({ order: { createdAt: 'DESC' }, take: 500 });
  }
}
