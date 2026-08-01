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
