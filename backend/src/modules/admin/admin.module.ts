import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { JwtModule } from '@nestjs/jwt';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { AdminController } from './admin.controller';
import { AdminService } from './admin.service';
import { User } from '../../database/entities/user.entity';
import { UserProfile } from '../../database/entities/user-profile.entity';
import { Room } from '../../database/entities/room.entity';
import { Gift } from '../../database/entities/gift.entity';
import { Wallet } from '../../database/entities/wallet.entity';
import { RechargeOrder } from '../../database/entities/recharge-order.entity';
import { WithdrawRequest } from '../../database/entities/withdraw-request.entity';
import { Report } from '../../database/entities/report.entity';
import { AdminUser } from '../../database/entities/admin-user.entity';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { AbuseLog } from '../../database/entities/abuse-log.entity';
import { WalletTransaction } from '../../database/entities/wallet-transaction.entity';
import { VipPlan } from '../../database/entities/vip-plan.entity';
import { Agency } from '../../database/entities/agency.entity';
import { AgencyMember } from '../../database/entities/agency-member.entity';
import { Notification } from '../../database/entities/notification.entity';
import { UserVip } from '../../database/entities/user-vip.entity';
import { UserCosmetic } from '../../database/entities/user-cosmetic.entity';
import { UserGameItem } from '../../database/entities/user-game-item.entity';
import { WalletModule } from '../wallet/wallet.module';
import { PaymentsModule } from '../payments/payments.module';
import { ZegoModule } from '../zego/zego.module';
import { RealtimeModule } from '../realtime/realtime.module';
import { NotificationsModule } from '../notifications/notifications.module';
import { ContestsModule } from '../contests/contests.module';
import { PlazaEventsModule } from '../plaza-events/plaza-events.module';
import { GamesModule } from '../games/games.module';
import { CosmeticsModule } from '../cosmetics/cosmetics.module';
import { TasksModule } from '../tasks/tasks.module';
import { RankingModule } from '../ranking/ranking.module';
import { UsersModule } from '../users/users.module';
import { RechargeAgentsModule } from '../recharge-agents/recharge-agents.module';
import { DramaModule } from '../drama/drama.module';
import { AgencyApplication } from '../../database/entities/agency-application.entity';
import { RoomSeat } from '../../database/entities/room-seat.entity';
import { AgenciesModule } from '../agencies/agencies.module';
@Module({
  imports: [
    TypeOrmModule.forFeature([
      User,
      UserProfile,
      Room,
      Gift,
      Wallet,
      RechargeOrder,
      WithdrawRequest,
      Report,
      AdminUser,
      AppSetting,
      AbuseLog,
      WalletTransaction,
      VipPlan,
      UserVip,
      UserCosmetic,
      UserGameItem,
      Agency,
      AgencyMember,
      AgencyApplication,
      RoomSeat,
      Notification,
    ]),
    WalletModule,
    PaymentsModule,
    ZegoModule,
    RealtimeModule,
    NotificationsModule,
    ContestsModule,
    PlazaEventsModule,
    GamesModule,
    CosmeticsModule,
    TasksModule,
    RankingModule,
    UsersModule,
    RechargeAgentsModule,
    DramaModule,
    AgenciesModule,
    JwtModule.registerAsync({
      imports: [ConfigModule],
      inject: [ConfigService],
      useFactory: (config: ConfigService) => ({
        secret: config.get<string>('app.jwt.secret'),
        signOptions: {
          expiresIn: config.get<string>('app.jwt.expiresIn') || '15m',
        },
      }),
    }),
  ],
  controllers: [AdminController],
  providers: [AdminService],
  exports: [AdminService],
})
export class AdminModule {}
