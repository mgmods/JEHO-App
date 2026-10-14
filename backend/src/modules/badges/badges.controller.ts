import {
  Body, Controller, Get, Param, Post, Patch, UseGuards,
} from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { CurrentUser } from '../../common/decorators';
import { JwtAuthGuard } from '../../common/guards/jwt-auth.guard';
import { AdminGuard } from '../../common/guards/admin.guard';
import { BadgesService } from './badges.service';

@ApiTags('Badges')
@ApiBearerAuth()
@UseGuards(JwtAuthGuard)
@Controller('badges')
export class BadgesController {
  constructor(private readonly badges: BadgesService) {}

  @Get()
  @ApiOperation({ summary: 'List active badges and whether the current user earned them' })
  catalog(@CurrentUser('sub') userId: string) {
    return this.badges.listCatalog(userId);
  }

  @Get('me')
  @ApiOperation({ summary: 'List badges earned by the current user' })
  mine(@CurrentUser('sub') userId: string) {
    return this.badges.listUserBadges(userId);
  }

  @Get('users/:id')
  @ApiOperation({ summary: 'List earned badges for a profile' })
  userBadges(@Param('id') id: string) {
    return this.badges.listUserBadges(id);
  }
}

@ApiTags('Admin Badges')
@ApiBearerAuth()
@UseGuards(JwtAuthGuard, AdminGuard)
@Controller('admin/badges')
export class AdminBadgesController {
  constructor(private readonly badges: BadgesService) {}

  @Get()
  list() {
    return this.badges.adminList();
  }

  @Post()
  create(@Body() body: {
    code: string; name: string; description?: string; imageUrl?: string;
    isActive?: boolean; sortOrder?: number;
    conditionType?: 'account_age_days' | 'messages_sent' | 'followers_count' | 'diamonds_received' | null;
    conditionTarget?: number | null;
  }) {
    return this.badges.saveDefinition(body);
  }

  @Patch(':id')
  update(@Param('id') id: string, @Body() body: {
    name: string; description?: string; imageUrl?: string;
    isActive?: boolean; sortOrder?: number;
    conditionType?: 'account_age_days' | 'messages_sent' | 'followers_count' | 'diamonds_received' | null;
    conditionTarget?: number | null;
  }) {
    return this.badges.saveDefinition(body, id);
  }

  @Post('users/:userId/:badgeId/grant')
  grant(
    @Param('userId') userId: string,
    @Param('badgeId') badgeId: string,
    @CurrentUser('sub') adminId: string,
    @Body('note') note?: string,
  ) {
    return this.badges.grant(userId, badgeId, adminId, note);
  }

  @Post('users/:userId/:badgeId/revoke')
  revoke(@Param('userId') userId: string, @Param('badgeId') badgeId: string) {
    return this.badges.revoke(userId, badgeId);
  }
}
