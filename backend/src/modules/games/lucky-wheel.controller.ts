import { Body, Controller, Get, Post } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { CurrentUser } from '../../common/decorators';
import { LuckyWheelService } from './lucky-wheel.service';
import { LuckyWheelSpinDto } from './dto/lucky-wheel.dto';

@ApiTags('Lucky Wheel')
@ApiBearerAuth()
@Controller('games/lucky-wheel')
export class LuckyWheelController {
  constructor(private readonly luckyWheel: LuckyWheelService) {}

  @Get()
  @ApiOperation({ summary: 'Lucky wheel balance + config' })
  state(@CurrentUser('sub') userId: string) {
    return this.luckyWheel.getState(userId);
  }

  @Post('spin')
  @ApiOperation({ summary: 'Place bet and spin (server RNG)' })
  spin(@CurrentUser('sub') userId: string, @Body() dto: LuckyWheelSpinDto) {
    return this.luckyWheel.spin(userId, dto, dto.roomId);
  }
}
