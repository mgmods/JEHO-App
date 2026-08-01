import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { WalletController } from './wallet.controller';
import { WalletService } from './wallet.service';
import { Wallet } from '../../database/entities/wallet.entity';
import { WalletTransaction } from '../../database/entities/wallet-transaction.entity';
import { RechargeOrder } from '../../database/entities/recharge-order.entity';
import { WithdrawRequest } from '../../database/entities/withdraw-request.entity';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { User } from '../../database/entities/user.entity';
import { AgencyMember } from '../../database/entities/agency-member.entity';
import { TasksModule } from '../tasks/tasks.module';
import { NotificationsModule } from '../notifications/notifications.module';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      Wallet,
      WalletTransaction,
      RechargeOrder,
      WithdrawRequest,
      AppSetting,
      User,
      AgencyMember,
    ]),
    TasksModule,
    NotificationsModule,
  ],
  controllers: [WalletController],
  providers: [WalletService],
  exports: [WalletService],
})
export class WalletModule {}