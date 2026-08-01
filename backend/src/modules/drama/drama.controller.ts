import { Controller, Get, Param, Post } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { Throttle } from '@nestjs/throttler';
import { CurrentUser } from '../../common/decorators';
import { DramaService } from './drama.service';

@ApiTags('Drama')
@ApiBearerAuth()
@Controller('drama')
export class DramaController {
  constructor(private readonly drama: DramaService) {}

  @Get('config')
  @ApiOperation({ summary: 'Drama feature flag + AdMob config' })
  config() {
    return this.drama.getConfig();
  }

  @Get('series')
  @ApiOperation({ summary: 'List series from remote drama API' })
  list(@CurrentUser('sub') userId: string) {
    return this.drama.listSeries(userId);
  }

  @Get('series/:id')
  @ApiOperation({ summary: 'Series detail + episodes from remote API' })
  get(@Param('id') id: string, @CurrentUser('sub') userId: string) {
    return this.drama.getSeries(id, userId);
  }

  @Post('series/:id/view')
  recordSeriesView(
    @Param('id') id: string,
    @CurrentUser('sub') userId: string,
  ) {
    return this.drama.recordView(userId, 'series', id);
  }

  @Post('series/:id/like')
  likeSeries(@Param('id') id: string, @CurrentUser('sub') userId: string) {
    return this.drama.toggleLike(userId, 'series', id);
  }

  @Post('episodes/:id/view')
  recordEpisodeView(
    @Param('id') id: string,
    @CurrentUser('sub') userId: string,
  ) {
    return this.drama.recordView(userId, 'episode', id);
  }

  @Post('episodes/:id/like')
  likeEpisode(@Param('id') id: string, @CurrentUser('sub') userId: string) {
    return this.drama.toggleLike(userId, 'episode', id);
  }

  @Post('episodes/:id/claim-reward')
  @Throttle({ default: { limit: 10, ttl: 60_000 } })
  @ApiOperation({ summary: 'Claim coin reward after watching an episode' })
  claimReward(
    @Param('id') id: string,
    @CurrentUser('sub') userId: string,
  ) {
    return this.drama.claimEpisodeReward(userId, id);
  }
}
