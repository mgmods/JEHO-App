import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { ChatController } from './chat.controller';
import { ChatService } from './chat.service';
import { ChatConversation } from '../../database/entities/chat-conversation.entity';
import { ChatParticipant } from '../../database/entities/chat-participant.entity';
import { ChatMessage } from '../../database/entities/chat-message.entity';
import { Block } from '../../database/entities/block.entity';
import { UserVip } from '../../database/entities/user-vip.entity';
import { RealtimeModule } from '../realtime/realtime.module';
import { NotificationsModule } from '../notifications/notifications.module';
import { UploadsModule } from '../uploads/uploads.module';
import { TasksModule } from '../tasks/tasks.module';
import { GiftSend } from '../../database/entities/gift-send.entity';
import { UserProfile } from '../../database/entities/user-profile.entity';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { Wallet } from '../../database/entities/wallet.entity';
import { WalletTransaction } from '../../database/entities/wallet-transaction.entity';
import { ModerationModule } from '../moderation/moderation.module';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      ChatConversation,
      ChatParticipant,
      ChatMessage,
      Block,
      UserVip,
      GiftSend,
      UserProfile,
      AppSetting,
      Wallet,
      WalletTransaction,
    ]),
    RealtimeModule,
    NotificationsModule,
    UploadsModule,
    TasksModule,
    ModerationModule,
  ],
  controllers: [ChatController],
  providers: [ChatService],
  exports: [ChatService],
})
export class ChatModule {}
