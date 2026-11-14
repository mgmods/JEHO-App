import { Controller, Get, Post, Body, Param, ParseUUIDPipe } from '@nestjs/common';
import { ApiTags, ApiBearerAuth, ApiOperation } from '@nestjs/swagger';
import { GamesService } from './games.service';
import { CurrentUser } from '../../common/decorators';
import { StartGameDto, GameMoveDto } from './dto/games.dto';

@ApiTags('Games')
@ApiBearerAuth()
@Controller('rooms/:roomId/games')
export class GamesController {
  constructor(private readonly gamesService: GamesService) {}

  @Post()
  @ApiOperation({ summary: 'Start a room mini-game (tic_tac_toe)' })
  start(
    @Param('roomId', ParseUUIDPipe) roomId: string,
    @CurrentUser('sub') userId: string,
    @Body() dto: StartGameDto,
  ) {
    return this.gamesService.start(roomId, userId, dto);
  }

  @Post(':gameId/join')
  @ApiOperation({ summary: 'Accept / join a waiting game as player O' })
  join(
    @Param('roomId', ParseUUIDPipe) roomId: string,
    @Param('gameId', ParseUUIDPipe) gameId: string,
    @CurrentUser('sub') userId: string,
  ) {
    return this.gamesService.join(roomId, gameId, userId);
  }

  @Post(':gameId/reject')
  @ApiOperation({ summary: 'Reject a direct XO challenge' })
  reject(
    @Param('roomId', ParseUUIDPipe) roomId: string,
    @Param('gameId', ParseUUIDPipe) gameId: string,
    @CurrentUser('sub') userId: string,
  ) {
    return this.gamesService.reject(roomId, gameId, userId);
  }

  @Post(':gameId/move')
  @ApiOperation({ summary: 'Place a mark on the board (cell 0-8)' })
  move(
    @Param('roomId', ParseUUIDPipe) roomId: string,
    @Param('gameId', ParseUUIDPipe) gameId: string,
    @CurrentUser('sub') userId: string,
    @Body() dto: GameMoveDto,
  ) {
    return this.gamesService.move(roomId, gameId, userId, dto);
  }

  @Get(':gameId')
  @ApiOperation({ summary: 'Get game state' })
  get(
    @Param('roomId', ParseUUIDPipe) roomId: string,
    @Param('gameId', ParseUUIDPipe) gameId: string,
    @CurrentUser('sub') userId: string,
  ) {
    return this.gamesService.get(roomId, gameId, userId);
  }

  @Post(':gameId/cancel')
  @ApiOperation({ summary: 'Cancel a waiting or in-progress game' })
  cancel(
    @Param('roomId', ParseUUIDPipe) roomId: string,
    @Param('gameId', ParseUUIDPipe) gameId: string,
    @CurrentUser('sub') userId: string,
  ) {
    return this.gamesService.cancel(roomId, gameId, userId);
  }
}
