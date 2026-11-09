import { Body, Controller, Delete, Get, Param, ParseUUIDPipe, Patch, Post, Query, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { Throttle } from '@nestjs/throttler';
import { CurrentUser } from '../../common/decorators';
import { JwtAuthGuard } from '../../common/guards/jwt-auth.guard';
import { AdminGuard } from '../../common/guards/admin.guard';
import { StoriesService } from './stories.service';

@ApiTags('Stories')
@ApiBearerAuth()
@UseGuards(JwtAuthGuard)
@Controller('stories')
export class StoriesController {
  constructor(private readonly stories: StoriesService) {}
  @Get('config') config() { return this.stories.getConfig(); }
  @Get('feed') feed(@CurrentUser('sub') userId: string) { return this.stories.feed(userId); }
  @Post()
  @Throttle({ default: { limit: 15, ttl: 60_000 } })
  create(@CurrentUser('sub') userId: string, @Body() body: Record<string, unknown>) {
    return this.stories.create(userId, body || {});
  }
  @Post(':id/view')
  @Throttle({ default: { limit: 60, ttl: 60_000 } })
  view(@Param('id', ParseUUIDPipe) id: string, @CurrentUser('sub') userId: string) {
    return this.stories.recordView(id, userId);
  }
  @Delete(':id')
  remove(@Param('id', ParseUUIDPipe) id: string, @CurrentUser('sub') userId: string) {
    return this.stories.remove(id, userId);
  }
}

@ApiTags('Admin Stories')
@ApiBearerAuth()
@UseGuards(JwtAuthGuard, AdminGuard)
@Controller('admin/stories')
export class AdminStoriesController {
  constructor(private readonly stories: StoriesService) {}
  @Get('config') config() { return this.stories.getConfig(); }
  @Patch('config') updateConfig(@Body() body: Record<string, unknown>) {
    return this.stories.adminUpdateConfig((body || {}) as any);
  }
  @Get() list(@Query('limit') limit?: string) { return this.stories.adminList(Number(limit) || 100); }
  @Delete(':id') remove(@Param('id', ParseUUIDPipe) id: string) { return this.stories.adminRemove(id); }
}
