import { Body, Controller, Delete, Get, Param, ParseUUIDPipe, Post, Query } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { CurrentUser } from '../../common/decorators';
import { CasualMatchService } from './casual-match.service';
import { CasualActionDto, QueueCasualDto } from './dto/casual.dto';
import { CasualGameKind } from '../../database/entities/casual-match.entity';

@ApiTags('Casual Games')
@ApiBearerAuth()
@Controller('casual-games')
export class CasualGamesController {
  constructor(private readonly casual: CasualMatchService) {}

  @Post('queue')
  @ApiOperation({ summary: 'Search for a real opponent (ONO / Domino / Ludo)' })
  queue(@CurrentUser('sub') userId: string, @Body() dto: QueueCasualDto) {
    return this.casual.enqueue(userId, dto.kind, dto.roomId);
  }

  @Delete('queue')
  @ApiOperation({ summary: 'Cancel matchmaking search' })
  cancel(
    @CurrentUser('sub') userId: string,
    @Query('kind') kind?: CasualGameKind,
  ) {
    return this.casual.cancelQueue(userId, kind);
  }

  @Get('boss/status')
  @ApiOperation({ summary: 'Get the server-authoritative daily boss battle' })
  bossStatus(@CurrentUser('sub') userId: string) {
    return this.casual.getBossStatus(userId);
  }

  @Post('boss/attack')
  @ApiOperation({ summary: 'Use one daily attack against the boss' })
  attackBoss(@CurrentUser('sub') userId: string) {
    return this.casual.attackBoss(userId);
  }

  @Get('rooms/:roomId/leaderboard')
  @ApiOperation({ summary: 'Room-scoped casual games leaderboard' })
  roomLeaderboard(@Param('roomId', ParseUUIDPipe) roomId: string) {
    return this.casual.roomLeaderboard(roomId);
  }

  @Get(':matchId')
  @ApiOperation({ summary: 'Get match state' })
  get(
    @Param('matchId', ParseUUIDPipe) matchId: string,
    @CurrentUser('sub') userId: string,
  ) {
    return this.casual.getMatch(matchId, userId);
  }

  @Post(':matchId/heartbeat')
  heartbeat(
    @Param('matchId', ParseUUIDPipe) matchId: string,
    @CurrentUser('sub') userId: string,
  ) {
    return this.casual.heartbeat(matchId, userId);
  }

  @Post(':matchId/leave')
  leave(
    @Param('matchId', ParseUUIDPipe) matchId: string,
    @CurrentUser('sub') userId: string,
  ) {
    return this.casual.leaveMatch(matchId, userId);
  }

  @Post(':matchId/action')
  @ApiOperation({ summary: 'Push game state or forfeit' })
  action(
    @Param('matchId', ParseUUIDPipe) matchId: string,
    @CurrentUser('sub') userId: string,
    @Body() dto: CasualActionDto,
  ) {
    return this.casual.action(matchId, userId, dto);
  }
}
