import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { NotificationsController } from './notifications.controller';
import { NotificationsService } from './notifications.service';
import { FirebaseAdminService } from './firebase-admin.service';
import { Notification } from '../../database/entities/notification.entity';
import { Device } from '../../database/entities/device.entity';
import { User } from '../../database/entities/user.entity';
import { UserVip } from '../../database/entities/user-vip.entity';
import { Room } from '../../database/entities/room.entity';
import { RealtimeModule } from '../realtime/realtime.module';
import { AuthModule } from '../auth/auth.module';

@Module({
  imports: [
    TypeOrmModule.forFeature([Notification, Device, User, UserVip, Room]),
    AuthModule,
    RealtimeModule,
  ],
  controllers: [NotificationsController],
  providers: [NotificationsService, FirebaseAdminService],
  exports: [NotificationsService, FirebaseAdminService],
})
export class NotificationsModule {}
