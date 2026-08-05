import { Controller, Get, Post, Body, Query } from '@nestjs/common';
import { ApiTags, ApiBearerAuth, ApiOperation } from '@nestjs/swagger';
import { GiftsService } from './gifts.service';
import { CurrentUser, Public } from '../../common/decorators';
import { PaginationDto } from '../../common/dto/pagination.dto';
import { SendGiftDto, SendAllMicGiftDto } from './dto/gifts.dto';

@ApiTags('Gifts')
@ApiBearerAuth()
@Controller('gifts')
export class GiftsController {
  constructor(private readonly giftsService: GiftsService) {}

  @Public()
  @Get()
  @ApiOperation({ summary: 'Gift catalog' })
  catalog() {
    return this.giftsService.catalog();
  }

  @Public()
  @Get('categories')
  @ApiOperation({ summary: 'Gift sheet tabs / categories' })
  categories() {
    return this.giftsService.listCategories();
  }

  @Post('send')
  @ApiOperation({ summary: 'Send gift with combo/lucky support' })
  send(@CurrentUser('sub') userId: string, @Body() dto: SendGiftDto) {
    return this.giftsService.send(userId, dto);
  }

  @Post('send-all-mic')
  @ApiOperation({
    summary:
      'All-mic gift: one charge, platform cut, then host 50% / mics 50% diamonds',
  })
  sendAllMic(
    @CurrentUser('sub') userId: string,
    @Body() dto: SendAllMicGiftDto,
  ) {
    return this.giftsService.sendAllMic(userId, dto);
  }

  @Get('history')
  history(@CurrentUser('sub') userId: string, @Query() query: PaginationDto) {
    return this.giftsService.history(userId, query);
  }
}
