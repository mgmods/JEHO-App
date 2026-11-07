import { Controller, Get, Query, All, Req } from '@nestjs/common';
import type { Request } from 'express';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import { Public } from '../../../common/decorators';
import { findMikooGame } from '../mikoo-games.catalog';

function firstHeader(value: string | string[] | undefined): string {
  return (Array.isArray(value) ? value[0] : value || '').split(',')[0].trim();
}

/**
 * Resolve the public game origin from the request that actually reached this
 * backend. Cumin/Cloud proxies provide forwarded host/proto; using them keeps
 * every game on the same deployment without baking a server ID or IP into the
 * packaged game or relying on retired provider domains.
 */
function originFromRequest(request: Request): string {
  const forwardedHost = firstHeader(request.headers['x-forwarded-host']);
  const host = (forwardedHost || firstHeader(request.headers.host)).toLowerCase();
  const forwardedProto = firstHeader(request.headers['x-forwarded-proto']).toLowerCase();
  const protocol = forwardedProto === 'http' || forwardedProto === 'https'
    ? forwardedProto
    : request.protocol === 'http' ? 'http' : 'https';

  if (host && /^[a-z0-9.-]+(?::[0-9]{1,5})?$/i.test(host)) {
    return `${protocol}://${host}`;
  }

  // Environment override is only a last resort, and retired hosts are rejected.
  const configured = String(process.env.PUBLIC_API_ORIGIN || '').trim().replace(/\\/+$/, '');
  if (
    /^https?:\/\//i.test(configured) &&
    !/(api\.adnova\.bbs\.tr|jieyou\.shop|sruner\.com|zkruner\.com|jeho-own-backend\.mgs\.blitz\.cloud)/i.test(configured)
  ) {
    return configured;
  }

  const fallbackHost = String(request.get('host') || '').trim();
  if (fallbackHost && /^[a-z0-9.-]+(?::[0-9]{1,5})?$/i.test(fallbackHost)) {
    return `${protocol}://${fallbackHost}`;
  }
  throw new Error('Unable to resolve the public JEHO game origin from the current request');
}

/** BaiShun-compatible route shim — returns URLs on the current JEHO deployment. */
@ApiTags('Mikoo Game Route')
@Controller('games/route')
export class BaishunRouteController {
  @Public()
  @Get('get_addr')
  @ApiOperation({ summary: 'BaiShun get_addr — returns current JEHO game server URLs' })
  getAddr(
    @Req() request: Request,
    @Query('game_id') gameId?: string,
    @Query('user_id') userId?: string,
    @Query('app_id') appId?: string,
    @Query('app_channel') appChannel?: string,
  ) {
    const slug = this.resolveSlug(gameId);
    const base = originFromRequest(request);
    const ws = base.replace(/^https:\/\//i, 'wss://').replace(/^http:\/\//i, 'ws://');
    return {
      code: 200,
      data: {
        http_addr: `${base}/games/route/`,
        ws_addr: `${ws}/games/ws/${slug}`,
      },
      meta: { gameId: slug, userId, appId, appChannel, provider: 'jeho' },
    };
  }

  @Public()
  @Get('update_time')
  @ApiOperation({ summary: 'BaiShun update_time heartbeat' })
  updateTime() {
    return { code: 200, data: {} };
  }

  /** Client analytics sink — formerly jieyou/sruner/zkruner. */
  @Public()
  @All('client_log/dev/add')
  clientLogDev() {
    return { code: 200, data: true };
  }

  @Public()
  @All('client_log/test/add')
  clientLogTest() {
    return { code: 200, data: true };
  }

  @Public()
  @All('client_log/prod/add')
  clientLogProd() {
    return { code: 200, data: true };
  }

  private resolveSlug(gameId?: string) {
    if (!gameId) return 'cleopatra-slot';
    const id = String(gameId).trim().toLowerCase();
    const byModule: Record<string, string> = {
      '1107': 'cleopatra-slot',
      '1022': 'fishing',
      '1184': 'football-plinko',
      '1068': 'greedy-lion',
      '1072': 'hilo',
      '1174': 'royal-battle',
      '1098': 'slot777',
      '1183': 'swimsuit-party',
    };
    if (byModule[id]) return byModule[id];
    const game = findMikooGame(id);
    if (game?.bridge === 'baishun') return game.id;
    if (game) return game.id;
    return id;
  }
}
