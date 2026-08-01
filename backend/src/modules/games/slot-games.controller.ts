import { Body, Controller, Get, Param, ParseUUIDPipe, Post, Query } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { Throttle } from '@nestjs/throttler';
import { CurrentUser } from '../../common/decorators';
import { SlotGamesService } from './slot-games.service';
import { EndSlotSessionDto, RecordSlotWinDto, StartSlotSessionDto } from './dto/slot-games.dto';

@ApiTags('Slot Games')
@ApiBearerAuth()
@Controller('games/slot')
export class SlotGamesController {
  constructor(private readonly slots: SlotGamesService) {}

  @Post('session')
  @Throttle({ default: { limit: 20, ttl: 60_000 } })
  @ApiOperation({ summary: 'Start a Mikoo/BaiShun slot game session' })
  start(@CurrentUser('sub') userId: string, @Body() dto: StartSlotSessionDto) {
    return this.slots.startSession(userId, dto.gameId, dto.roomId);
  }

  @Post('get-code')
  @Throttle({ default: { limit: 20, ttl: 60_000 } })
  @ApiOperation({ summary: 'Mikoo-compatible game auth code' })
  getCode(@CurrentUser('sub') userId: string, @Body() dto: StartSlotSessionDto) {
    return this.slots.getCode(userId, dto.gameId, dto.roomId);
  }

  @Post('session/:sessionId/heartbeat')
  heartbeat(
    @Param('sessionId', ParseUUIDPipe) sessionId: string,
    @CurrentUser('sub') userId: string,
  ) {
    return this.slots.heartbeat(sessionId, userId);
  }

  @Post('session/:sessionId/end')
  end(
    @Param('sessionId', ParseUUIDPipe) sessionId: string,
    @CurrentUser('sub') userId: string,
    @Body() dto: EndSlotSessionDto,
  ) {
    return this.slots.endSession(sessionId, userId, dto);
  }

  @Post('session/:sessionId/win')
  @Throttle({ default: { limit: 3, ttl: 60_000 } })
  @ApiOperation({ summary: 'DISABLED — client win reports rejected' })
  win(
    @Param('sessionId', ParseUUIDPipe) sessionId: string,
    @CurrentUser('sub') userId: string,
    @Body() dto: RecordSlotWinDto,
  ) {
    return this.slots.recordWin(sessionId, userId, dto.winCoins);
  }

  @Get('rooms/:roomId/active')
  @ApiOperation({ summary: 'Players currently in slot games inside a voice room' })
  active(@Param('roomId', ParseUUIDPipe) roomId: string) {
    return this.slots.activeInRoom(roomId);
  }

  @Get('rooms/:roomId/leaderboard')
  @ApiOperation({ summary: 'Room slot games win leaderboard' })
  leaderboard(
    @Param('roomId', ParseUUIDPipe) roomId: string,
    @Query('limit') limit?: string,
  ) {
    const n = Number(limit);
    return this.slots.roomLeaderboard(roomId, Number.isFinite(n) ? n : 20);
  }
}
