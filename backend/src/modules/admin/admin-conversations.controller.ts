import { Controller, Get, Param, ParseUUIDPipe, Query, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { AdminGuard } from '../../common/guards/admin.guard';
import { JwtAuthGuard } from '../../common/guards/jwt-auth.guard';
import { PaginationDto } from '../../common/dto/pagination.dto';
import { AdminConversationsService } from './admin-conversations.service';

@ApiTags('Admin Conversations')
@Controller('admin/conversations')
@UseGuards(JwtAuthGuard, AdminGuard)
@ApiBearerAuth()
export class AdminConversationsController {
  constructor(private readonly service: AdminConversationsService) {}
  @Get()
  @ApiOperation({ summary: 'List all user conversations, read-only' })
  list(@Query() query: PaginationDto, @Query('from') from?: string, @Query('to') to?: string) {
    return this.service.list(Object.assign(query, { from, to }));
  }
  @Get(':id')
  @ApiOperation({ summary: 'Get one conversation, read-only' })
  get(@Param('id', ParseUUIDPipe) id: string) { return this.service.get(id); }
  @Get(':id/messages')
  @ApiOperation({ summary: 'Get conversation messages without changing read state' })
  messages(@Param('id', ParseUUIDPipe) id: string, @Query() query: PaginationDto) {
    return this.service.messagesFor(id, query);
  }
}
