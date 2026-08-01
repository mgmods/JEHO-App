import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { RoomCupController } from './room-cup.controller';
import { GiftSend } from '../../database/entities/gift-send.entity';
import { Room } from '../../database/entities/room.entity';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { Wallet } from '../../database/entities/wallet.entity';
import { WalletTransaction } from '../../database/entities/wallet-transaction.entity';
import { VipPlan } from '../../database/entities/vip-plan.entity';
import { UserVip } from '../../database/entities/user-vip.entity';
import { User } from '../../database/entities/user.entity';
import { AuthModule } from '../auth/auth.module';

@Module({
  imports: [
    AuthModule,
    TypeOrmModule.forFeature([
      GiftSend,
      Room,
      AppSetting,
      Wallet,
      WalletTransaction,
      User,
      VipPlan,
      UserVip,
    ]),
  ],
  controllers: [RoomCupController],
})
export class RoomCupModule {}
