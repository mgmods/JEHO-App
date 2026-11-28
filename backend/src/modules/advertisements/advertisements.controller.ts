import { Controller, Get, Post, Patch, Param, Body, Query, UseGuards, BadRequestException } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { CurrentUser } from '../../common/decorators';
import { JwtAuthGuard } from '../../common/guards/jwt-auth.guard';
import { AdminGuard } from '../../common/guards/admin.guard';
import { AdvertisementsService } from './advertisements.service';

@ApiTags('Advertisements')
@Controller('advertisements')
@UseGuards(JwtAuthGuard)
@ApiBearerAuth()
export class AdvertisementsController {
  constructor(private readonly service: AdvertisementsService) {}

  @Get('config') getConfig() { return this.service.getPublicConfig(); }
  @Get('current') current() { return this.service.current(); }
  @Get('remaining') remaining(@CurrentUser('sub') userId: string) { return this.service.remainingForUser(userId); }

  @Post()
  create(@CurrentUser('sub') userId: string, @Body() body: { text?: string; idempotencyKey?: string }) {
    return this.service.create(userId, body?.text || '', body?.idempotencyKey || '');
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @Get('admin/queue')
  adminQueue(@Query('limit') limit?: string, @Query('q') q?: string, @Query('history') history?: string) {
    return this.service.adminQueue(Math.min(200, Math.max(1, Number(limit) || 50)), q || '', history === 'true');
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @Get('admin/stats')
  adminStats(@Query('days') days?: string) {
    return this.service.adminStats(Math.min(31, Math.max(1, Number(days) || 7)));
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @Patch('admin/config')
  patchConfig(@Body() body: Record<string, unknown>) { return this.service.patchConfig(body || {}); }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @Get('admin/user-policy/:userRef')
  adminUserPolicy(@Param('userRef') userRef: string) { return this.service.adminUserPolicy(userRef); }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @Patch('admin/user-policy')
  patchUserPolicy(@Body() body: Record<string, unknown>) { return this.service.patchUserPolicy(body || {}); }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @Patch('admin/vip-policy')
  patchVipPolicy(@Body() body: Record<string, unknown>) { return this.service.patchVipPolicy(body || {}); }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @Get('admin/banned-words')
  adminBannedWords() { return this.service.adminBannedWords(); }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @Patch('admin/banned-words')
  patchBannedWords(@Body() body: { words?: string }) { return this.service.patchBannedWords(String(body?.words || '')); }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @Patch('admin/queue/:id')
  patchQueue(@Param('id') id: string, @Body() body: Record<string, unknown>) {
    const action = String(body?.action || '');
    if (!['remove', 'priority'].includes(action)) throw new BadRequestException('Invalid queue action');
    return this.service.patchQueue(id, body || {});
  }
}