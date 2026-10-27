import { Body, Controller, Get, Post, Delete, Param, Query, BadRequestException } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { CurrentUser, Public } from '../../common/decorators';
import { ParseUUIDPipe } from '@nestjs/common';
import { CpService } from './cp.service';
import { CpType } from '../../database/entities/cp-relationship.entity';

@ApiTags('CP')
@ApiBearerAuth()
@Controller('cp')
export class CpController {
  constructor(private readonly cp: CpService) {}

  @Public()
  @Get('room-effects')
  @ApiOperation({ summary: 'Active relationships and configured visual effects for users seated together in a room' })
  roomSeatEffects(@Query('userIds') userIds: string) {
    return this.cp.roomSeatEffects(userIds || '');
  }

  @Public()
  @Get('users/:id')
  @ApiOperation({ summary: 'Public active CP relationships for a profile; no financial data' })
  profileRelationships(@Param('id', ParseUUIDPipe) userId: string) {
    return this.cp.profileRelationships(userId);
  }

  @Get('catalog')
  @ApiOperation({ summary: 'Enabled CP types and configured gift prices' })
  catalog() {
    return this.cp.catalog();
  }

  @Get('discover')
  @ApiOperation({ summary: 'Real active CP relationships, ranked by points' })
  discover(@Query('type') type?: CpType) {
    return this.cp.discover(type);
  }



  @Get('tasks')
  @ApiOperation({ summary: 'Get daily shared tasks and claim status for the current CP relationship' })
  tasks(@CurrentUser('sub') userId: string) {
    return this.cp.tasks(userId);
  }

  @Post('tasks/:taskId/claim')
  @ApiOperation({ summary: 'Claim a completed CP task reward once per user and day' })
  claimTask(@CurrentUser('sub') userId: string, @Param('taskId') taskId: string) {
    return this.cp.claimTask(userId, taskId);
  }

  @Get('memories')
  @ApiOperation({ summary: 'List memories shared within the current active CP relationship' })
  memories(@CurrentUser('sub') userId: string) {
    return this.cp.memories(userId);
  }

  @Post('memories')
  @ApiOperation({ summary: 'Add a text or image memory to the current active CP relationship' })
  createMemory(
    @CurrentUser('sub') userId: string,
    @Body() body: { text?: string; imageUrl?: string },
  ) {
    return this.cp.createMemory(userId, body);
  }

  @Delete('memories/:id')
  @ApiOperation({ summary: 'Delete a memory created by the current user' })
  deleteMemory(@CurrentUser('sub') userId: string, @Param('id', ParseUUIDPipe) id: string) {
    return this.cp.deleteMemory(userId, id);
  }

  @Get('me')
  me(@CurrentUser('sub') userId: string) {
    return this.cp.forUser(userId);
  }

  @Get('requests')
  requests(@CurrentUser('sub') userId: string) {
    return this.cp.requests(userId);
  }

  @Post('requests')
  @ApiOperation({ summary: 'Send a paid CP initiation gift from a shared voice room' })
  createRequest(
    @CurrentUser('sub') userId: string,
    @Body() body: { recipientId: string; type: CpType; roomId: string; idempotencyKey: string },
  ) {
    return this.cp.createRequest(userId, body);
  }

  @Post('profile-requests')
  @ApiOperation({ summary: 'Send a relationship request directly from a user profile without a room gift' })
  createProfileRequest(
    @CurrentUser('sub') userId: string,
    @Body() body: { recipientId: string; type: CpType; idempotencyKey: string },
  ) {
    return this.cp.createProfileRequest(userId, body);
  }

  @Get('sent-requests')
  sentRequests(@CurrentUser('sub') userId: string) {
    return this.cp.sentRequests(userId);
  }

  @Post('requests/:id/cancel')
  cancelRequest(@CurrentUser('sub') userId: string, @Param('id', ParseUUIDPipe) id: string) {
    return this.cp.cancelRequest(userId, id);
  }

  @Post('requests/:id/respond')
  respond(
    @CurrentUser('sub') userId: string,
    @Param('id', ParseUUIDPipe) id: string,
    @Body('accept') accept: boolean,
  ) {
    if (typeof accept !== 'boolean') throw new BadRequestException('يجب تحديد القبول أو الرفض بشكل صريح');
    return this.cp.respond(userId, id, accept);
  }

  @Post(':id/end')
  end(@CurrentUser('sub') userId: string, @Param('id', ParseUUIDPipe) id: string) {
    return this.cp.end(userId, id);
  }
}
