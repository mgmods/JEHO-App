import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { VanityIdsController } from './vanity-ids.controller';
import { VanityIdsService } from './vanity-ids.service';
import { VanityId } from '../../database/entities/vanity-id.entity';
import { User } from '../../database/entities/user.entity';
import { Wallet } from '../../database/entities/wallet.entity';
import { WalletTransaction } from '../../database/entities/wallet-transaction.entity';
import { Room } from '../../database/entities/room.entity';
import { Agency } from '../../database/entities/agency.entity';
import { AdminUser } from '../../database/entities/admin-user.entity';
import { AuthModule } from '../auth/auth.module';
import { JwtAuthGuard } from '../../common/guards/jwt-auth.guard';
import { RealtimeModule } from '../realtime/realtime.module';

@Module({
  imports: [
    AuthModule,
    RealtimeModule,
    TypeOrmModule.forFeature([VanityId, User, Wallet, WalletTransaction, Room, Agency, AdminUser]),
  ],
  controllers: [VanityIdsController],
  providers: [VanityIdsService, JwtAuthGuard],
})
export class VanityIdsModule {}
