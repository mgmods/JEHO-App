import { Controller, Get, Query, All } from '@nestjs/common';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import { Public } from '../../../common/decorators';
import { findMikooGame } from '../mikoo-games.catalog';

function origin() {
  const raw = process.env.PUBLIC_API_ORIGIN || 'https://api.adnova.bbs.tr';
  return raw.replace(/\/$/, '');
}

function wsOrigin() {
  return origin().replace(/^https:\/\//i, 'wss://').replace(/^http:\/\//i, 'ws://');
}

/** BaiShun-compatible game_route shim — replaces jieyou.shop. */
@ApiTags('Mikoo Game Route')
@Controller('games/route')
export class BaishunRouteController {
  @Public()
  @Get('get_addr')
  @ApiOperation({ summary: 'BaiShun get_addr — returns JEHO game server URLs' })
  getAddr(
    @Query('game_id') gameId?: string,
    @Query('user_id') userId?: string,
    @Query('app_id') appId?: string,
    @Query('app_channel') appChannel?: string,
  ) {
    const slug = this.resolveSlug(gameId);
    const base = origin();
    const ws = wsOrigin();
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
    const id = String(gameId).trim();
    const byModule = [
      ['1107', 'cleopatra-slot'],
      ['1022', 'fishing'],
      ['1184', 'football-plinko'],
      ['1072', 'hilo'],
      ['1174', 'royal-battle'],
      ['1098', 'slot777'],
      ['1183', 'swimsuit-party'],
    ];
    for (const [mod, slug] of byModule) {
      if (id === mod) return slug;
    }
    const game = findMikooGame(id);
    if (game?.bridge === 'baishun') return game.id;
    return id.toLowerCase();
  }
}
