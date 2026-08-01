import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import {
  AgentRecharge,
  RechargeAgent,
  RechargeAgentApplication,
  RechargeAgentContact,
  RechargeAgentLedger,
} from '../../database/entities/recharge-agent.entity';
import { User } from '../../database/entities/user.entity';
import { Wallet } from '../../database/entities/wallet.entity';
import { WalletTransaction } from '../../database/entities/wallet-transaction.entity';
import { RechargeOrder } from '../../database/entities/recharge-order.entity';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { WithdrawRequest } from '../../database/entities/withdraw-request.entity';
import { GiftSend } from '../../database/entities/gift-send.entity';
import { WalletModule } from '../wallet/wallet.module';
import { PaymentsModule } from '../payments/payments.module';
import { RechargeAgentsController } from './recharge-agents.controller';
import { RechargeAgentsService } from './recharge-agents.service';
import { NotificationsModule } from '../notifications/notifications.module';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      RechargeAgent,
      RechargeAgentApplication,
      RechargeAgentContact,
      RechargeAgentLedger,
      AgentRecharge,
      User,
      Wallet,
      WalletTransaction,
      RechargeOrder,
      AppSetting,
      WithdrawRequest,
      GiftSend,
    ]),
    WalletModule,
    PaymentsModule,
    NotificationsModule,
  ],
  controllers: [RechargeAgentsController],
  providers: [RechargeAgentsService],
  exports: [RechargeAgentsService],
})
export class RechargeAgentsModule {}
