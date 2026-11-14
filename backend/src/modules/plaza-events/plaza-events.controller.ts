import {
  Body,
  Controller,
  Delete,
  Get,
  Param,
  ParseUUIDPipe,
  Patch,
  Post,
  Query,
} from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { CurrentUser } from '../../common/decorators';
import { PlazaEventsService } from './plaza-events.service';

@ApiTags('Events')
@ApiBearerAuth()
@Controller('events')
export class PlazaEventsController {
  constructor(private readonly events: PlazaEventsService) {}

  @Get()
  @ApiOperation({ summary: 'Event Square (square) or My Events (mine)' })
  list(
    @CurrentUser('sub') userId: string,
    @Query('tab') tab?: string,
  ) {
    const t = tab === 'mine' ? 'mine' : 'square';
    return this.events.list(userId, t);
  }

  @Get(':id')
  @ApiOperation({ summary: 'Event details' })
  get(@Param('id', ParseUUIDPipe) id: string, @CurrentUser('sub') userId: string) {
    return this.events.get(id, userId);
  }

  @Post()
  @ApiOperation({ summary: 'Create room event (host)' })
  create(@CurrentUser('sub') userId: string, @Body() body: Record<string, any>) {
    return this.events.create(userId, body || {});
  }

  @Patch(':id')
  @ApiOperation({ summary: 'Update my event' })
  update(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
    @Body() body: Record<string, any>,
  ) {
    return this.events.updateMine(userId, id, body || {});
  }

  @Delete(':id')
  @ApiOperation({ summary: 'Delete my event' })
  remove(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
  ) {
    return this.events.deleteMine(userId, id);
  }

  @Post(':id/subscribe')
  @ApiOperation({ summary: 'Subscribe / follow event' })
  subscribe(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
  ) {
    return this.events.subscribe(userId, id);
  }

  @Delete(':id/subscribe')
  @ApiOperation({ summary: 'Unsubscribe from event' })
  unsubscribe(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
  ) {
    return this.events.unsubscribe(userId, id);
  }
}
