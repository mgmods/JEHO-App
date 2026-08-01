import { Module } from '@nestjs/common';
import { JwtModule } from '@nestjs/jwt';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { TypeOrmModule } from '@nestjs/typeorm';
import { RealtimeGateway } from './realtime.gateway';
import { ChatParticipant } from '../../database/entities/chat-participant.entity';
import { RoomSeat } from '../../database/entities/room-seat.entity';
import { RoomModerator } from '../../database/entities/room-moderator.entity';
import { Room } from '../../database/entities/room.entity';
import { User } from '../../database/entities/user.entity';
import { UserProfile } from '../../database/entities/user-profile.entity';
import { UserVip } from '../../database/entities/user-vip.entity';
import { GiftSend } from '../../database/entities/gift-send.entity';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { RoomBan } from '../../database/entities/room-ban.entity';
import { RoomAccess } from '../../database/entities/room-access.entity';
import { Agency } from '../../database/entities/agency.entity';

@Module({
  imports: [
    JwtModule.registerAsync({
      imports: [ConfigModule],
      inject: [ConfigService],
      useFactory: (config: ConfigService) => ({
        secret: config.get<string>('app.jwt.secret'),
      }),
    }),
    TypeOrmModule.forFeature([
      ChatParticipant,
      RoomSeat,
      RoomModerator,
      Room,
      RoomBan,
      RoomAccess,
      Agency,
      User,
      UserProfile,
      UserVip,
      GiftSend,
      AppSetting,
    ]),
  ],
  providers: [RealtimeGateway],
  exports: [RealtimeGateway],
})
export class RealtimeModule {}
