import { Controller, Get, Post, Body, Query } from '@nestjs/common';
import { ApiTags, ApiBearerAuth, ApiOperation } from '@nestjs/swagger';
import { WalletService } from './wallet.service';
import { CurrentUser } from '../../common/decorators';
import { PaginationDto } from '../../common/dto/pagination.dto';
import { CreateRechargeDto, ExchangeDto, WithdrawDto, HostTradeDto } from './dto/wallet.dto';

@ApiTags('Wallet')
@ApiBearerAuth()
@Controller('wallet')
export class WalletController {
  constructor(private readonly walletService: WalletService) {}

  @Get()
  @ApiOperation({ summary: 'Get wallet balances' })
  get(@CurrentUser('sub') userId: string) {
    return this.walletService.getWallet(userId);
  }

  @Get('economy-config')
  @ApiOperation({ summary: 'Diamond value and withdrawal settings managed by admin' })
  economyConfig() {
    return this.walletService.economyConfig();
  }

  @Get('packages')
  @ApiOperation({ summary: 'Recharge packages' })
  packages() {
    return this.walletService.listPackages();
  }

  @Get('withdraw-packages')
  @ApiOperation({ summary: 'Mikoo-style diamond cashout packages with USD labels' })
  withdrawPackages() {
    return this.walletService.listWithdrawPackages();
  }

  @Get('transactions')
  transactions(@CurrentUser('sub') userId: string, @Query() query: PaginationDto) {
    return this.walletService.transactions(userId, query);
  }

  @Post('recharge')
  @ApiOperation({ summary: 'Create recharge order' })
  recharge(@CurrentUser('sub') userId: string, @Body() dto: CreateRechargeDto) {
    return this.walletService.createRecharge(userId, dto);
  }

  @Post('exchange')
  @ApiOperation({ summary: 'Exchange diamonds to coins (60%)' })
  exchange(@CurrentUser('sub') userId: string, @Body() dto: ExchangeDto) {
    return this.walletService.exchange(userId, dto);
  }

  @Post('host-trade')
  @ApiOperation({ summary: 'Agency hostesses swap diamonds → receiver trader collection' })
  hostTrade(@CurrentUser('sub') userId: string, @Body() dto: HostTradeDto) {
    return this.walletService.tradeDiamondsWithHost(userId, dto.toUserId, dto.diamonds);
  }

  @Post('withdraw')
  @ApiOperation({ summary: 'Request diamond withdrawal' })
  withdraw(@CurrentUser('sub') userId: string, @Body() dto: WithdrawDto) {
    return this.walletService.requestWithdraw(userId, dto);
  }

  @Get('withdraws')
  @ApiOperation({ summary: 'List my withdrawal requests' })
  myWithdraws(@CurrentUser('sub') userId: string, @Query() query: PaginationDto) {
    return this.walletService.listWithdraws(userId, query);
  }
}
