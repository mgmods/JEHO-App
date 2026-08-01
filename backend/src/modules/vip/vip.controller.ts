import { Controller, Get, Post, Body } from '@nestjs/common';
import { ApiTags, ApiBearerAuth, ApiOperation } from '@nestjs/swagger';
import { VipService, PurchaseVipDto } from './vip.service';
import { CurrentUser, Public } from '../../common/decorators';

@ApiTags('VIP')
@ApiBearerAuth()
@Controller('vip')
export class VipController {
  constructor(private readonly vipService: VipService) {}

  @Public()
  @Get('plans')
  @ApiOperation({ summary: 'List VIP1-100 plans and benefits' })
  plans() {
    return this.vipService.listPlans();
  }

  @Get('me')
  me(@CurrentUser('sub') userId: string) {
    return this.vipService.myVip(userId);
  }

  @Post('purchase')
  purchase(@CurrentUser('sub') userId: string, @Body() dto: PurchaseVipDto) {
    return this.vipService.purchase(userId, dto);
  }
}
