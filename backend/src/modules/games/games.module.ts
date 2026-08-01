import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { GamesController } from './games.controller';
import { GamesService } from './games.service';
import { GameStoreController } from './game-store.controller';
import { GameStoreService } from './game-store.service';
import { SlotGamesController } from './slot-games.controller';
import { SlotGamesService } from './slot-games.service';
import { MikooGatewayService } from './mikoo-gateway/mikoo-gateway.service';
import { MikooSessionService } from './mikoo-gateway/mikoo-session.service';
import { BaishunWsHandler } from './mikoo-gateway/baishun-ws.handler';
import { BaishunRouteController } from './mikoo-gateway/baishun-route.controller';
import { MikooEconomyNotifyService } from './mikoo-gateway/mikoo-economy-notify.service';
import { RoomGame } from '../../database/entities/room-game.entity';
import { SlotGameSession } from '../../database/entities/slot-game-session.entity';
import { UserGameItem } from '../../database/entities/user-game-item.entity';
import { Wallet } from '../../database/entities/wallet.entity';
import { Room } from '../../database/entities/room.entity';
import { User } from '../../database/entities/user.entity';
import { UserProfile } from '../../database/entities/user-profile.entity';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { RoomModerator } from '../../database/entities/room-moderator.entity';
import { RoomAccess } from '../../database/entities/room-access.entity';
import { RoomBan } from '../../database/entities/room-ban.entity';
import { RoomGameAccessService } from './room-game-access.service';
import { GameAdsService } from './game-ads.service';
import { GameAdsController } from './game-ads.controller';
import { RealtimeModule } from '../realtime/realtime.module';
import { TasksModule } from '../tasks/tasks.module';
import { WalletModule } from '../wallet/wallet.module';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      RoomGame,
      SlotGameSession,
      UserGameItem,
      Wallet,
      Room,
      User,
      UserProfile,
      AppSetting,
      RoomModerator,
      RoomAccess,
      RoomBan,
    ]),
    RealtimeModule,
    TasksModule,
    WalletModule,
  ],
  controllers: [
    GamesController,
    GameStoreController,
    SlotGamesController,
    BaishunRouteController,
    GameAdsController,
  ],
  providers: [
    GamesService,
    GameStoreService,
    SlotGamesService,
    MikooGatewayService,
    MikooSessionService,
    BaishunWsHandler,
    MikooEconomyNotifyService,
    RoomGameAccessService,
    GameAdsService,
  ],
  exports: [
    GamesService,
    GameStoreService,
    SlotGamesService,
    MikooGatewayService,
    RoomGameAccessService,
    GameAdsService,
  ],
})
export class GamesModule {}
