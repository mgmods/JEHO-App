import { Body, Controller, Delete, Get, Param, ParseUUIDPipe, Patch, Post, Query, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { CurrentUser } from '../../common/decorators';
import { JwtAuthGuard } from '../../common/guards/jwt-auth.guard';
import { CreateMomentDto, MomentsService } from './moments.service';
import { AdminGuard } from '../../common/guards/admin.guard';

@ApiTags('Moments')
@ApiBearerAuth()
@UseGuards(JwtAuthGuard)
@Controller('moments')
export class MomentsController {
  constructor(private readonly moments: MomentsService) {}

  @Get('feed')
  @ApiOperation({ summary: 'Latest moments visible to the current user' })
  feed(@CurrentUser('sub') userId: string, @Query('page') page?: string, @Query('limit') limit?: string) {
    return this.moments.feed(userId, Number(page) || 1, Number(limit) || 20);
  }

  @Get('users/:userId')
  @ApiOperation({ summary: 'Visible moments for a user profile' })
  userMoments(@Param('userId', ParseUUIDPipe) userId: string, @CurrentUser('sub') viewerId: string) {
    return this.moments.forUser(userId, viewerId);
  }

  @Post()
  @ApiOperation({ summary: 'Publish a moment' })
  create(@CurrentUser('sub') userId: string, @Body() dto: CreateMomentDto) {
    return this.moments.create(userId, dto);
  }

  @Patch(':id')
  @ApiOperation({ summary: 'Edit an owned moment' })
  update(@Param('id', ParseUUIDPipe) id: string, @CurrentUser('sub') userId: string, @Body() dto: CreateMomentDto) {
    return this.moments.update(id, userId, dto);
  }

  @Post(':id/like')
  toggleLike(@Param('id', ParseUUIDPipe) id: string, @CurrentUser('sub') userId: string) {
    return this.moments.toggleLike(id, userId);
  }

  @Get(':id/comments')
  comments(@Param('id', ParseUUIDPipe) id: string, @CurrentUser('sub') userId: string) {
    return this.moments.listComments(id, userId);
  }

  @Post(':id/comments')
  comment(@Param('id', ParseUUIDPipe) id: string, @CurrentUser('sub') userId: string,
          @Body() body: { body?: string; parentCommentId?: string | null }) {
    return this.moments.addComment(id, userId, body?.body, body?.parentCommentId);
  }

  @Post(':id/report')
  @ApiOperation({ summary: 'Report a moment for administrator review' })
  report(@Param('id', ParseUUIDPipe) id: string, @CurrentUser('sub') userId: string, @Body() body: { reason?: string; description?: string }) {
    return this.moments.report(id, userId, body);
  }

  @Delete(':id')
  remove(@Param('id', ParseUUIDPipe) id: string, @CurrentUser('sub') userId: string) {
    return this.moments.remove(id, userId);
  }
}


@ApiTags('Admin Moments')
@ApiBearerAuth()
@UseGuards(JwtAuthGuard, AdminGuard)
@Controller('admin/moments')
export class AdminMomentsController {
  constructor(private readonly moments: MomentsService) {}

  @Get()
  @ApiOperation({ summary: 'List recent moments for dashboard moderation' })
  list(@Query('limit') limit?: string) {
    return this.moments.adminList(Number(limit) || 100);
  }

  @Delete(':id')
  @ApiOperation({ summary: 'Delete a moment and its likes/comments as administrator' })
  remove(@Param('id', ParseUUIDPipe) id: string) {
    return this.moments.adminRemove(id);
  }

  @Delete()
  @ApiOperation({ summary: 'Delete all moments and their likes/comments as administrator' })
  removeAll() {
    return this.moments.adminRemoveAll();
  }
}
