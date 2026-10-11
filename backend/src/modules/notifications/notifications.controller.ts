import {
  Controller,
  Get,
  Post,
  Patch,
  Body,
  Param,
  Query,
  ParseUUIDPipe,
  UseGuards,
  Delete,
} from '@nestjs/common';
import { ApiTags, ApiBearerAuth, ApiOperation } from '@nestjs/swagger';
import { NotificationType } from '../../database/entities/notification.entity';
import {
  NotificationsService,
  CreateNotificationDto,
  AdminPushDto,
} from './notifications.service';
import { CurrentUser } from '../../common/decorators';
import { PaginationDto } from '../../common/dto/pagination.dto';
import { IsOptional, IsString } from 'class-validator';
import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { JwtAuthGuard } from '../../common/guards/jwt-auth.guard';
import { AdminGuard } from '../../common/guards/admin.guard';

class RegisterDeviceDto {
  @ApiProperty()
  @IsString()
  deviceId: string;

  @ApiProperty()
  @IsString()
  platform: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  fcmToken?: string;

  /** Android legacy alias */
  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  token?: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  model?: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  appVersion?: string;
}

@ApiTags('Notifications')
@ApiBearerAuth()
@Controller('notifications')
export class NotificationsController {
  constructor(private readonly notificationsService: NotificationsService) {}

  @Get()
  list(@CurrentUser('sub') userId: string, @Query() query: PaginationDto) {
    return this.notificationsService.list(userId, query);
  }

  @Get('admin-messages')
  @ApiOperation({ summary: 'Super-admin messages inbox, separate from Official News' })
  listAdminMessages(@CurrentUser('sub') userId: string, @Query() query: PaginationDto) {
    return this.notificationsService.listAdminMessages(userId, query);
  }

  @Get('admin-messages/preview')
  adminMessagesPreview(@CurrentUser('sub') userId: string) {
    return this.notificationsService.adminMessagesPreview(userId);
  }

  @Post('admin-messages/read-all')
  markAllAdminMessagesRead(@CurrentUser('sub') userId: string) {
    return this.notificationsService.markAllAdminMessagesRead(userId);
  }

  @Post('admin-messages/send')
  @UseGuards(JwtAuthGuard, AdminGuard)
  sendAdminMessage(@Body() dto: AdminPushDto) {
    return this.notificationsService.adminSend({ ...dto, type: NotificationType.CHAT, channel: 'both' });
  }

  @Get('official')
  @ApiOperation({ summary: 'Official News feed (read-only chat bubbles)' })
  listOfficial(
    @CurrentUser('sub') userId: string,
    @Query() query: PaginationDto,
  ) {
    return this.notificationsService.listOfficial(userId, query);
  }

  @Get('official/preview')
  @ApiOperation({ summary: 'Pinned Official News row preview + unread' })
  officialPreview(@CurrentUser('sub') userId: string) {
    return this.notificationsService.officialPreview(userId);
  }

  @Get('official/unread-count')
  officialUnread(@CurrentUser('sub') userId: string) {
    return this.notificationsService.officialUnreadCount(userId);
  }

  @Post('official/read-all')
  markAllOfficial(@CurrentUser('sub') userId: string) {
    return this.notificationsService.markAllOfficialRead(userId);
  }

  @Get('unread-count')
  unread(@CurrentUser('sub') userId: string) {
    return this.notificationsService.unreadCount(userId);
  }

  @Patch(':id/read')
  markRead(
    @CurrentUser('sub') userId: string,
    @Param('id', ParseUUIDPipe) id: string,
  ) {
    return this.notificationsService.markRead(userId, id);
  }

  @Post('read-all')
  markAll(@CurrentUser('sub') userId: string) {
    return this.notificationsService.markAllRead(userId);
  }

  @Post()
  @ApiOperation({ summary: 'Create notification (internal/admin use)' })
  @UseGuards(JwtAuthGuard, AdminGuard)
  create(@Body() dto: CreateNotificationDto) {
    return this.notificationsService.create(dto);
  }

  @Post('devices')
  registerDevice(@CurrentUser('sub') userId: string, @Body() dto: RegisterDeviceDto) {
    return this.notificationsService.registerDevice(userId, dto);
  }

  @Delete('devices/:deviceId')
  unregisterDevice(
    @CurrentUser('sub') userId: string,
    @Param('deviceId') deviceId: string,
  ) {
    return this.notificationsService.unregisterDevice(userId, deviceId);
  }
}
