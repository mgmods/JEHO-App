import { Body, Controller, Get, Param, ParseUUIDPipe, Post, Query } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { CurrentUser } from '../../common/decorators';
import { ContestsService } from './contests.service';

@ApiTags('Contests')
@ApiBearerAuth()
@Controller('contests')
export class ContestsController {
  constructor(private readonly contests: ContestsService) {}

  @Get()
  @ApiOperation({ summary: 'List contests (optional roomId / agencyId scope)' })
  list(
    @CurrentUser('sub') userId: string,
    @Query('roomId') roomId?: string,
    @Query('agencyId') agencyId?: string,
  ) {
    return this.contests.list(userId, roomId, agencyId);
  }

  @Get('room-pk/invitations')
  @ApiOperation({ summary: 'List pending PK invitations for a room owner' })
  roomPkInvitations(
    @CurrentUser('sub') userId: string,
    @Query('roomId') roomId: string,
  ) {
    return this.contests.listRoomPkInvitations(userId, roomId);
  }

  @Post('room-pk')
  @ApiOperation({ summary: 'Create a room-to-room PK invitation from inside the source room' })
  createRoomPk(
    @CurrentUser('sub') userId: string,
    @Body() dto: { roomId: string; targetRoomId: string; durationMinutes?: number; scoringMode?: string },
  ) {
    return this.contests.createRoomPkChallenge(userId, dto);
  }

  @Post(':id/room-pk-response')
  @ApiOperation({ summary: 'Accept or reject a room-to-room PK invitation' })
  respondRoomPk(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
    @Body() dto: { accept: boolean },
  ) {
    return this.contests.respondRoomPkChallenge(userId, id, dto.accept === true);
  }

  @Get(':id')
  @ApiOperation({ summary: 'Contest details + leaderboard' })
  get(@Param('id', ParseUUIDPipe) id: string, @CurrentUser('sub') userId: string) {
    return this.contests.get(id, userId);
  }

  @Post(':id/join')
  @ApiOperation({ summary: 'Join contest' })
  join(@Param('id', ParseUUIDPipe) id: string, @CurrentUser('sub') userId: string) {
    return this.contests.join(userId, id);
  }
}
