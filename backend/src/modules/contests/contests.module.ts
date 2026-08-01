import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { ContestsController } from './contests.controller';
import { ContestsService } from './contests.service';
import { Contest } from '../../database/entities/contest.entity';
import { ContestEntry } from '../../database/entities/contest-entry.entity';
import { Wallet } from '../../database/entities/wallet.entity';
import { User } from '../../database/entities/user.entity';
import { WalletTransaction } from '../../database/entities/wallet-transaction.entity';
import { RealtimeModule } from '../realtime/realtime.module';
import { CosmeticsModule } from '../cosmetics/cosmetics.module';

@Module({
  imports: [
    TypeOrmModule.forFeature([Contest, ContestEntry, Wallet, User, WalletTransaction]),
    RealtimeModule,
    CosmeticsModule,
  ],
  controllers: [ContestsController],
  providers: [ContestsService],
  exports: [ContestsService],
})
export class ContestsModule {}
