import { Body, Controller, Get, Post } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { Type } from 'class-transformer';
import { IsIn, IsNumber, IsOptional, IsString, Max, Min } from 'class-validator';
import { CurrentUser } from '../../common/decorators';
import { CasualBetService } from './casual-bet.service';

class CasualPlayDto {
  @IsString()
  @IsIn([
    'slots',
    'roulette',
    'plinko',
    'crash',
    'rps',
    'hilo',
    'witch',
    'blackjack',
    'poker',
    'tarot',
    'penalty',
    'reaction',
  ])
  gameId!: string;

  @Type(() => Number)
  @IsNumber()
  @Min(50)
  @Max(20_000)
  amount!: number;

  @IsOptional()
  @IsString()
  roomId?: string;

  @IsOptional()
  choice?: string | number;
}

@ApiTags('Games')
@ApiBearerAuth()
@Controller('games/casual')
export class CasualBetController {
  constructor(private readonly casual: CasualBetService) {}

  @Get()
  @ApiOperation({ summary: 'Casual coin games wallet state' })
  state(@CurrentUser('sub') userId: string) {
    return this.casual.state(userId);
  }

  @Post('play')
  @ApiOperation({ summary: 'Play casual game for coins (server RNG)' })
  play(@CurrentUser('sub') userId: string, @Body() dto: CasualPlayDto) {
    return this.casual.play(userId, dto as any);
  }
}
