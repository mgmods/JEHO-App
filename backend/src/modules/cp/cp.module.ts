import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { CpController } from './cp.controller';
import { CpService } from './cp.service';
import { CpRequest } from '../../database/entities/cp-request.entity';
import { CpRelationship } from '../../database/entities/cp-relationship.entity';
import { Wallet } from '../../database/entities/wallet.entity';
import { WalletTransaction } from '../../database/entities/wallet-transaction.entity';
import { Room } from '../../database/entities/room.entity';
import { RoomSeat } from '../../database/entities/room-seat.entity';
import { User } from '../../database/entities/user.entity';
import { AppSetting } from '../../database/entities/app-setting.entity';

@Module({
  imports: [TypeOrmModule.forFeature([CpRequest, CpRelationship, Wallet, WalletTransaction, Room, RoomSeat, User, AppSetting])],
  controllers: [CpController],
  providers: [CpService],
  exports: [CpService],
})
export class CpModule {}
