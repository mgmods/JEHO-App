import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { RoomsController } from './rooms.controller';
import { RoomsService } from './rooms.service';
import { InternetMusicService } from './internet-music.service';
import { Room } from '../../database/entities/room.entity';
import { RoomSeat } from '../../database/entities/room-seat.entity';
import { RoomSeatSignal } from '../../database/entities/room-seat-signal.entity';
import { RoomBan } from '../../database/entities/room-ban.entity';
import { RoomModerator } from '../../database/entities/room-moderator.entity';
import { UserVip } from '../../database/entities/user-vip.entity';
import { User } from '../../database/entities/user.entity';
import { RoomAccess } from '../../database/entities/room-access.entity';
import { RoomHostFollow } from '../../database/entities/room-host-follow.entity';
import { Follow } from '../../database/entities/follow.entity';
import { Wallet } from '../../database/entities/wallet.entity';
import { WalletTransaction } from '../../database/entities/wallet-transaction.entity';
import { Cosmetic } from '../../database/entities/cosmetic.entity';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { ZegoModule } from '../zego/zego.module';
import { RealtimeModule } from '../realtime/realtime.module';
import { TasksModule } from '../tasks/tasks.module';
import { NotificationsModule } from '../notifications/notifications.module';
import { UploadsModule } from '../uploads/uploads.module';
import { ModerationModule } from '../moderation/moderation.module';
import { Agency } from '../../database/entities/agency.entity';
import { AgencyMember } from '../../database/entities/agency-member.entity';
import { Report } from '../../database/entities/report.entity';
import { RoomMusicTrack } from '../../database/entities/room-music-track.entity';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      Room,
      RoomSeat,
      RoomSeatSignal,
      RoomBan,
      RoomModerator,
      UserVip,
      User,
      RoomAccess,
      RoomHostFollow,
      Follow,
      Wallet,
      WalletTransaction,
      Cosmetic,
      AppSetting,
      Agency,
      AgencyMember,
      Report,
      RoomMusicTrack,
    ]),
    ZegoModule,
    RealtimeModule,
    TasksModule,
    NotificationsModule,
    UploadsModule,
    ModerationModule,
  ],
  controllers: [RoomsController],
  providers: [RoomsService, InternetMusicService],
  exports: [RoomsService],
})
export class RoomsModule {}
