import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { PaymentsController } from './payments.controller';
import { PaymentsService } from './payments.service';
import { PaymentSettingsService } from './payment-settings.service';
import { BinanceWalletReconcileTask } from './binance-wallet-reconcile.task';
import { RechargeOrder } from '../../database/entities/recharge-order.entity';
import { PaymentWebhookEvent } from '../../database/entities/payment-webhook-event.entity';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { WalletModule } from '../wallet/wallet.module';

@Module({
  imports: [
    TypeOrmModule.forFeature([RechargeOrder, PaymentWebhookEvent, AppSetting]),
    WalletModule,
  ],
  controllers: [PaymentsController],
  providers: [PaymentsService, PaymentSettingsService, BinanceWalletReconcileTask],
  exports: [PaymentsService, PaymentSettingsService],
})
export class PaymentsModule {}
