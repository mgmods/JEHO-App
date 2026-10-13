import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { User } from '../../database/entities/user.entity';
import { Room } from '../../database/entities/room.entity';
import { Agency } from '../../database/entities/agency.entity';
import { ConfigController } from './config.controller';
import { TranslateController } from './translate.controller';
import { ZegoModule } from '../zego/zego.module';
import { LiveKitModule } from '../livekit/livekit.module';
import { ModerationModule } from '../moderation/moderation.module';

@Module({
  imports: [
    TypeOrmModule.forFeature([AppSetting, User, Room, Agency]),
    ZegoModule,
    LiveKitModule,
    ModerationModule,
  ],
  controllers: [ConfigController, TranslateController],
})
export class AppConfigModule {}
