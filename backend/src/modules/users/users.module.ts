import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { UsersController } from './users.controller';
import { UsersService } from './users.service';
import { User } from '../../database/entities/user.entity';
import { UserProfile } from '../../database/entities/user-profile.entity';
import { Follow } from '../../database/entities/follow.entity';
import { Block } from '../../database/entities/block.entity';
import { Report } from '../../database/entities/report.entity';
import { UserVip } from '../../database/entities/user-vip.entity';
import { SocialRequest } from '../../database/entities/social-request.entity';
import { ProfileVisit } from '../../database/entities/profile-visit.entity';
import { Cosmetic } from '../../database/entities/cosmetic.entity';
import { FemaleIdentityVerification } from '../../database/entities/female-identity-verification.entity';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { RealtimeModule } from '../realtime/realtime.module';
import { NotificationsModule } from '../notifications/notifications.module';
import { TasksModule } from '../tasks/tasks.module';
import { UploadsModule } from '../uploads/uploads.module';
import { IdentityVerificationService } from './identity-verification.service';

@Module({
  imports: [
    RealtimeModule,
    NotificationsModule,
    TasksModule,
    UploadsModule,
    TypeOrmModule.forFeature([
      User,
      UserProfile,
      Follow,
      Block,
      Report,
      UserVip,
      SocialRequest,
      ProfileVisit,
      Cosmetic,
      FemaleIdentityVerification,
      AppSetting,
    ]),
  ],
  controllers: [UsersController],
  providers: [UsersService, IdentityVerificationService],
  exports: [UsersService, IdentityVerificationService],
})
export class UsersModule {}
