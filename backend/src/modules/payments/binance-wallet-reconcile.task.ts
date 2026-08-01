import { Injectable, Logger, OnModuleDestroy, OnModuleInit } from '@nestjs/common';
import { PaymentsService } from './payments.service';

@Injectable()
export class BinanceWalletReconcileTask implements OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger(BinanceWalletReconcileTask.name);
  private timer: NodeJS.Timeout | null = null;

  constructor(private readonly paymentsService: PaymentsService) {}

  onModuleInit() {
    this.timer = setInterval(() => {
      this.paymentsService
        .reconcileBinanceWalletDeposits()
        .catch((err) =>
          this.logger.warn(`Scheduled deposit reconcile failed: ${(err as Error).message}`),
        );
    }, 30_000);
    this.logger.log('Binance wallet deposit reconcile interval started (30s)');
  }

  onModuleDestroy() {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }
}
