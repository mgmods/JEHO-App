import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { User } from '../../database/entities/user.entity';
import { RoomChatPenalty } from '../../database/entities/room-chat-penalty.entity';
import { ContentModerationService } from './content-moderation.service';

@Module({
  imports: [TypeOrmModule.forFeature([AppSetting, User, RoomChatPenalty])],
  providers: [ContentModerationService],
  exports: [ContentModerationService],
})
export class ModerationModule {}
