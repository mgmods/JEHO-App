import { Controller, Get, Param, Query } from '@nestjs/common';
import { ApiTags, ApiBearerAuth, ApiOperation, ApiQuery } from '@nestjs/swagger';
import { RankingService } from './ranking.service';
import {
  RankingPeriod,
  RankingCategory,
} from '../../database/entities/ranking-snapshot.entity';
import { Public } from '../../common/decorators';

@ApiTags('Ranking')
@ApiBearerAuth()
@Controller('ranking')
export class RankingController {
  constructor(private readonly rankingService: RankingService) {}

  @Public()
  @Get(':period/:category')
  @ApiOperation({ summary: 'Get ranking board' })
  @ApiQuery({ name: 'limit', required: false })
  get(
    @Param('period') period: RankingPeriod,
    @Param('category') category: RankingCategory,
    @Query('limit') limit?: string,
  ) {
    return this.rankingService.getBoard(
      period,
      category,
      limit ? parseInt(limit, 10) : 50,
    );
  }
}
