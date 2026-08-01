import {
  Controller,
  Get,
  Post,
  Patch,
  Body,
  Param,
  Query,
  ParseUUIDPipe,
} from '@nestjs/common';
import { ApiTags, ApiBearerAuth, ApiOperation } from '@nestjs/swagger';
import { ChatService } from './chat.service';
import { CurrentUser } from '../../common/decorators';
import { PaginationDto } from '../../common/dto/pagination.dto';
import { StartConversationDto, SendMessageDto, EditMessageDto } from './dto/chat.dto';

@ApiTags('Chat')
@ApiBearerAuth()
@Controller('chat')
export class ChatController {
  constructor(private readonly chatService: ChatService) {}

  @Post('conversations')
  @ApiOperation({ summary: 'Start or get DM conversation' })
  start(@CurrentUser('sub') userId: string, @Body() dto: StartConversationDto) {
    return this.chatService.startOrGet(userId, dto);
  }

  @Get('conversations')
  list(@CurrentUser('sub') userId: string, @Query() query: PaginationDto) {
    return this.chatService.listConversations(userId, query);
  }

  @Get('conversations/:id')
  get(@Param('id', ParseUUIDPipe) id: string, @CurrentUser('sub') userId: string) {
    return this.chatService.getConversation(id, userId);
  }

  @Post('conversations/:id/messages')
  send(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
    @Body() dto: SendMessageDto,
  ) {
    return this.chatService.send(id, userId, dto);
  }

  @Get('conversations/:id/messages')
  messages(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
    @Query() query: PaginationDto,
  ) {
    return this.chatService.listMessages(id, userId, query);
  }

  @Patch('messages/:id')
  edit(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
    @Body() dto: EditMessageDto,
  ) {
    return this.chatService.edit(id, userId, dto);
  }

  @Post('messages/:id/unsend')
  unsend(@Param('id', ParseUUIDPipe) id: string, @CurrentUser('sub') userId: string) {
    return this.chatService.unsend(id, userId);
  }

  @Post('conversations/:id/mute')
  mute(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
    @Body('muted') muted: boolean,
  ) {
    return this.chatService.mute(id, userId, muted !== false);
  }

  @Post('conversations/:id/pin')
  pin(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
    @Body('pinned') pinned: boolean,
  ) {
    return this.chatService.pin(id, userId, pinned !== false);
  }

  @Post('conversations/:id/archive')
  archive(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
    @Body('archived') archived: boolean,
  ) {
    return this.chatService.archive(id, userId, archived !== false);
  }

  @Post('conversations/:id/delete')
  deleteForMe(@Param('id', ParseUUIDPipe) id: string, @CurrentUser('sub') userId: string) {
    return this.chatService.deleteForMe(id, userId);
  }
}
