import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { VanityIdsController } from './vanity-ids.controller';
import { VanityId } from '../../database/entities/vanity-id.entity';
import { User } from '../../database/entities/user.entity';
import { Wallet } from '../../database/entities/wallet.entity';
import { WalletTransaction } from '../../database/entities/wallet-transaction.entity';
import { AuthModule } from '../auth/auth.module';

@Module({
  imports: [
    AuthModule,
    TypeOrmModule.forFeature([VanityId, User, Wallet, WalletTransaction]),
  ],
  controllers: [VanityIdsController],
})
export class VanityIdsModule {}
