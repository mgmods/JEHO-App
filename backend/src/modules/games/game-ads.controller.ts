import { Controller, Get, Post } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { Throttle } from '@nestjs/throttler';
import { CurrentUser } from '../../common/decorators';
import { GameAdsService } from './game-ads.service';

@ApiTags('Game Ads')
@ApiBearerAuth()
@Controller('games/ads')
export class GameAdsController {
  constructor(private readonly ads: GameAdsService) {}

  @Get('config')
  @ApiOperation({ summary: 'AdMob config for in-app games' })
  config() {
    return this.ads.getClientConfig();
  }

  @Post('rewarded/claim')
  @Throttle({ default: { limit: 10, ttl: 60_000 } })
  @ApiOperation({ summary: 'Claim coins after watching a game rewarded ad' })
  claim(@CurrentUser('sub') userId: string) {
    return this.ads.claimRewarded(userId);
  }
}
