import { Module, forwardRef } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { GiftsController } from './gifts.controller';
import { GiftsService } from './gifts.service';
import { Gift } from '../../database/entities/gift.entity';
import { GiftSend } from '../../database/entities/gift-send.entity';
import { Wallet } from '../../database/entities/wallet.entity';
import { WalletTransaction } from '../../database/entities/wallet-transaction.entity';
import { User } from '../../database/entities/user.entity';
import { UserVip } from '../../database/entities/user-vip.entity';
import { UserProfile } from '../../database/entities/user-profile.entity';
import { Agency } from '../../database/entities/agency.entity';
import { AgencyMember } from '../../database/entities/agency-member.entity';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { Room } from '../../database/entities/room.entity';
import { RoomSeat } from '../../database/entities/room-seat.entity';
import { TasksModule } from '../tasks/tasks.module';
import { ContestsModule } from '../contests/contests.module';
import { RealtimeModule } from '../realtime/realtime.module';
import { HostTargetModule } from '../host-target/host-target.module';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      Gift,
      GiftSend,
      Wallet,
      WalletTransaction,
      UserProfile,
      User,
      UserVip,
      Agency,
      AgencyMember,
      AppSetting,
      Room,
      RoomSeat,
    ]),
    TasksModule,
    forwardRef(() => ContestsModule),
    RealtimeModule,
    HostTargetModule,
  ],
  controllers: [GiftsController],
  providers: [GiftsService],
  exports: [GiftsService],
})
export class GiftsModule {}
