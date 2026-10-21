import { Body, Controller, Delete, Get, Param, ParseUUIDPipe, Post, Put, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { CurrentUser } from '../../common/decorators';
import { JwtAuthGuard } from '../../common/guards/jwt-auth.guard';
import { AdminGuard } from '../../common/guards/admin.guard';
import { BadgesService, UpsertBadgeDto } from './badges.service';

@ApiTags('Badges')
@ApiBearerAuth()
@Controller()
export class BadgesController {
  constructor(private readonly service: BadgesService) {}

  @Get('users/me/badges')
  @UseGuards(JwtAuthGuard)
  @ApiOperation({ summary: 'Get active badge catalog and current-user ownership' })
  myBadges(@CurrentUser('sub') userId: string) {
    return this.service.catalogForIdentifier(userId);
  }

  @Get('users/:userId/badges')
  @UseGuards(JwtAuthGuard)
  @ApiOperation({ summary: 'Get badges for a profile user' })
  userBadges(@Param('userId') userId: string) {
    return this.service.catalogForIdentifier(userId);
  }

  @Get('users/:userId/badges/earned')
  @UseGuards(JwtAuthGuard)
  @ApiOperation({ summary: 'Get persisted earned badges for a profile user' })
  earnedBadges(@Param('userId') userId: string) {
    return this.service.earnedForIdentifier(userId);
  }

  @Get('admin/badges')
  @UseGuards(JwtAuthGuard, AdminGuard)
  listAdmin() { return this.service.listAdmin(); }

  @Post('admin/badges')
  @UseGuards(JwtAuthGuard, AdminGuard)
  create(@Body() dto: UpsertBadgeDto) { return this.service.create(dto); }

  @Put('admin/badges/:id')
  @UseGuards(JwtAuthGuard, AdminGuard)
  update(@Param('id', ParseUUIDPipe) id: string, @Body() dto: Partial<UpsertBadgeDto>) {
    return this.service.update(id, dto);
  }

  @Delete('admin/badges/:id')
  @UseGuards(JwtAuthGuard, AdminGuard)
  remove(@Param('id', ParseUUIDPipe) id: string) { return this.service.remove(id); }

  @Post('admin/badges/:id/grant')
  @UseGuards(JwtAuthGuard, AdminGuard)
  grant(@Param('id', ParseUUIDPipe) id: string, @Body() body: { userId: string }, @CurrentUser('sub') adminId: string) {
    return this.service.grant(id, body?.userId, adminId);
  }

  @Delete('admin/badges/:id/users/:userId')
  @UseGuards(JwtAuthGuard, AdminGuard)
  revoke(@Param('id', ParseUUIDPipe) id: string, @Param('userId') userId: string) {
    return this.service.revoke(id, userId);
  }
}
