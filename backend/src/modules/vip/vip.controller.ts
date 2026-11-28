import { Controller, Get, Post, Body } from '@nestjs/common';
import { ApiTags, ApiBearerAuth, ApiOperation } from '@nestjs/swagger';
import { VipService, PurchaseVipDto } from './vip.service';
import { CurrentUser, Public } from '../../common/decorators';
import { IsBoolean } from 'class-validator';
import { ApiProperty } from '@nestjs/swagger';

class SetMysteriousModeDto {
  @ApiProperty({ description: 'Enable anonymous identity in voice rooms' })
  @IsBoolean()
  enabled: boolean;
}

@ApiTags('VIP')
@ApiBearerAuth()
@Controller('vip')
export class VipController {
  constructor(private readonly vipService: VipService) {}

  @Public()
  @Get('plans')
  @ApiOperation({ summary: 'List the existing VIP1-7 plans and benefits' })
  plans() {
    return this.vipService.listPlans();
  }

  @Get('me')
  me(@CurrentUser('sub') userId: string) {
    return this.vipService.myVip(userId);
  }

  @Get('mysterious-mode')
  mysteriousModeStatus(@CurrentUser('sub') userId: string) {
    return this.vipService.mysteriousModeStatus(userId);
  }

  @Post('mysterious-mode')
  setMysteriousMode(
    @CurrentUser('sub') userId: string,
    @Body() dto: SetMysteriousModeDto,
  ) {
    return this.vipService.setMysteriousMode(userId, dto.enabled === true);
  }

  @Post('purchase')
  purchase(@CurrentUser('sub') userId: string, @Body() dto: PurchaseVipDto) {
    return this.vipService.purchase(userId, dto);
  }
}
