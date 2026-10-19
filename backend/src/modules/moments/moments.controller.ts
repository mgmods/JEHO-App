import { Body, Controller, Delete, Get, Param, ParseUUIDPipe, Post, Query, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { CurrentUser } from '../../common/decorators';
import { JwtAuthGuard } from '../../common/guards/jwt-auth.guard';
import { CreateMomentDto, MomentsService } from './moments.service';

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

  @Post(':id/like')
  toggleLike(@Param('id', ParseUUIDPipe) id: string, @CurrentUser('sub') userId: string) {
    return this.moments.toggleLike(id, userId);
  }

  @Get(':id/comments')
  comments(@Param('id', ParseUUIDPipe) id: string, @CurrentUser('sub') userId: string) {
    return this.moments.listComments(id, userId);
  }

  @Post(':id/comments')
  comment(@Param('id', ParseUUIDPipe) id: string, @CurrentUser('sub') userId: string, @Body('body') body: string) {
    return this.moments.addComment(id, userId, body);
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
