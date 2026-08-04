import { Body, Controller, Get, Post } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { CurrentUser } from '../../common/decorators';
import { DiceService } from './dice.service';
import { DiceRollDto } from './dto/dice.dto';

@ApiTags('Dice')
@ApiBearerAuth()
@Controller('games/dice')
export class DiceController {
  constructor(private readonly dice: DiceService) {}

  @Get()
  @ApiOperation({ summary: 'Dice balance + config' })
  state(@CurrentUser('sub') userId: string) {
    return this.dice.getState(userId);
  }

  @Post('roll')
  @ApiOperation({ summary: 'Place bet and roll (server RNG)' })
  roll(@CurrentUser('sub') userId: string, @Body() dto: DiceRollDto) {
    return this.dice.roll(userId, dto, dto.roomId);
  }
}
