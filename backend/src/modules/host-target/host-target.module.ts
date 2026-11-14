import { Module, forwardRef } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { HostMonthlyProgress } from '../../database/entities/host-monthly-progress.entity';
import { Wallet } from '../../database/entities/wallet.entity';
import { WalletTransaction } from '../../database/entities/wallet-transaction.entity';
import { Agency } from '../../database/entities/agency.entity';
import { AgencyMember } from '../../database/entities/agency-member.entity';
import { HostTargetService } from './host-target.service';
import { HostTargetController } from './host-target.controller';
import { RealtimeModule } from '../realtime/realtime.module';
import { CosmeticsModule } from '../cosmetics/cosmetics.module';
import { VipModule } from '../vip/vip.module';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      AppSetting,
      HostMonthlyProgress,
      Wallet,
      WalletTransaction,
      Agency,
      AgencyMember,
    ]),
    forwardRef(() => RealtimeModule),
    CosmeticsModule,
    VipModule,
  ],
  controllers: [HostTargetController],
  providers: [HostTargetService],
  exports: [HostTargetService],
})
export class HostTargetModule {}
