import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { Badge } from '../../database/entities/badge.entity';
import { UserBadge } from '../../database/entities/user-badge.entity';
import { User } from '../../database/entities/user.entity';
import { Follow } from '../../database/entities/follow.entity';
import { Wallet } from '../../database/entities/wallet.entity';
import { UserProfile } from '../../database/entities/user-profile.entity';
import { AuthModule } from '../auth/auth.module';
import { NotificationsModule } from '../notifications/notifications.module';
import { BadgesController } from './badges.controller';
import { BadgesService } from './badges.service';

@Module({
  imports: [
    TypeOrmModule.forFeature([Badge, UserBadge, User, Follow, Wallet, UserProfile]),
    // BadgesController uses JwtAuthGuard; AuthModule exports the configured JwtModule/JwtService.
    AuthModule,
    NotificationsModule,
  ],
  controllers: [BadgesController],
  providers: [BadgesService],
  exports: [BadgesService],
})
export class BadgesModule {}
