import { BadRequestException, Body, Controller, Get, Param, ParseUUIDPipe, Post, Query, Req } from '@nestjs/common';
import type { Request } from 'express';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { Throttle } from '@nestjs/throttler';
import { CurrentUser } from '../../common/decorators';
import { SlotGamesService } from './slot-games.service';
import { EndSlotSessionDto, RecordSlotWinDto, StartSlotSessionDto } from './dto/slot-games.dto';

const RETIRED_GAME_HOSTS = /(api\.adnova\.bbs\.tr|jieyou\.shop|sruner\.com|zkruner\.com|jeho-own-backend\.mgs\.blitz\.cloud)/i;

function firstForwarded(value: string | string[] | undefined): string {
  return (Array.isArray(value) ? value[0] : value || '').split(',')[0].trim();
}

function currentPublicOrigin(request: Request): string {
  const forwardedHost = firstForwarded(request.headers['x-forwarded-host']);
  const host = (forwardedHost || firstForwarded(request.headers.host)).toLowerCase();
  const forwardedProto = firstForwarded(request.headers['x-forwarded-proto']).toLowerCase();
  const protocol = forwardedProto === 'http' || forwardedProto === 'https'
    ? forwardedProto
    : request.protocol === 'http' ? 'http' : 'https';

  if (host && /^[a-z0-9.-]+(?::[0-9]{1,5})?$/i.test(host) && !RETIRED_GAME_HOSTS.test(host)) {
    return `${protocol}://${host}`;
  }

  const configured = String(process.env.PUBLIC_API_ORIGIN || '').trim().replace(/\/+$/, '');
  if (/^https?:\\/\\//i.test(configured) && !RETIRED_GAME_HOSTS.test(configured)) {
    try {
      return new URL(configured).origin;
    } catch {
      // Fall through to the request host.
    }
  }

  const fallbackHost = String(request.get('host') || '').trim().toLowerCase();
  if (fallbackHost && /^[a-z0-9.-]+(?::[0-9]{1,5})?$/i.test(fallbackHost) && !RETIRED_GAME_HOSTS.test(fallbackHost)) {
    return `${protocol}://${fallbackHost}`;
  }
  throw new BadRequestException('Unable to resolve the current JEHO game server origin');
}

@ApiTags('Slot Games')
@ApiBearerAuth()
@Controller('games/slot')
export class SlotGamesController {
  constructor(private readonly slots: SlotGamesService) {}

  @Post('session')
  @Throttle({ default: { limit: 20, ttl: 60_000 } })
  @ApiOperation({ summary: 'Start a Mikoo/BaiShun slot game session' })
  start(@CurrentUser('sub') userId: string, @Body() dto: StartSlotSessionDto, @Req() request: Request) {
    return this.slots.startSession(userId, dto.gameId, dto.roomId, currentPublicOrigin(request));
  }

  @Post('get-code')
  @Throttle({ default: { limit: 20, ttl: 60_000 } })
  @ApiOperation({ summary: 'Mikoo-compatible game auth code' })
  getCode(@CurrentUser('sub') userId: string, @Body() dto: StartSlotSessionDto, @Req() request: Request) {
    return this.slots.getCode(userId, dto.gameId, dto.roomId, currentPublicOrigin(request));
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
