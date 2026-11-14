import { Module, forwardRef } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AgenciesController } from './agencies.controller';
import { AgenciesService } from './agencies.service';
import { Agency } from '../../database/entities/agency.entity';
import { AgencyMember } from '../../database/entities/agency-member.entity';
import { GiftSend } from '../../database/entities/gift-send.entity';
import { Wallet } from '../../database/entities/wallet.entity';
import { WalletTransaction } from '../../database/entities/wallet-transaction.entity';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { Room } from '../../database/entities/room.entity';
import { RoomSeat } from '../../database/entities/room-seat.entity';
import { RoomModerator } from '../../database/entities/room-moderator.entity';
import { User } from '../../database/entities/user.entity';
import { AgencyApplication } from '../../database/entities/agency-application.entity';
import { AgencyPayoutRequest } from '../../database/entities/agency-payout-request.entity';
import { Cosmetic } from '../../database/entities/cosmetic.entity';
import { RoomsModule } from '../rooms/rooms.module';
import { NotificationsModule } from '../notifications/notifications.module';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      Agency,
      AgencyMember,
      AgencyApplication,
      AgencyPayoutRequest,
      GiftSend,
      Wallet,
      WalletTransaction,
      AppSetting,
      Room,
      RoomSeat,
      RoomModerator,
      User,
      Cosmetic,
    ]),
    forwardRef(() => RoomsModule),
    NotificationsModule,
  ],
  controllers: [AgenciesController],
  providers: [AgenciesService],
  exports: [AgenciesService],
})
export class AgenciesModule {}
