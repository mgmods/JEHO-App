import { Controller, Get, Post, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { JwtAuthGuard } from '../../common/guards/jwt-auth.guard';
import { AdminGuard } from '../../common/guards/admin.guard';
import { CurrentUser, Public } from '../../common/decorators';
import { PromotionsService } from './promotions.service';

@ApiTags('Promotions')
@Controller('promotions')
export class PromotionsController {
  constructor(private readonly promotions: PromotionsService) {}

  @Public()
  @Get('catalog')
  @ApiOperation({ summary: 'Monthly / agent / supporter / VIP duration offers' })
  catalog() {
    return this.promotions.getCatalog();
  }

  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth()
  @Get('me')
  @ApiOperation({ summary: 'My monthly recharge promo progress' })
  myProgress(@CurrentUser('sub') userId: string) {
    return this.promotions.myProgress(userId);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('admin/expire-vanity')
  @ApiOperation({ summary: 'Expire timed special IDs now' })
  expireVanity() {
    return this.promotions.expireVanityLeases();
  }
}
