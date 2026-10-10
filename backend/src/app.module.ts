import { Module } from '@nestjs/common';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { TypeOrmModule } from '@nestjs/typeorm';
import { DataSource } from 'typeorm';
import { ThrottlerModule } from '@nestjs/throttler';
import { APP_GUARD } from '@nestjs/core';
import configuration from './config/configuration';
import { JwtAuthGuard } from './common/guards/jwt-auth.guard';
import { AppThrottlerGuard } from './common/guards/app-throttler.guard';
import { SecurityModule } from './common/security/security.module';
import { SecurityShieldGuard } from './common/security/security-shield.service';
import * as entities from './database/entities';
import { RedisModule } from './common/redis/redis.module';
import { AuthModule } from './modules/auth/auth.module';
import { UsersModule } from './modules/users/users.module';
import { RoomsModule } from './modules/rooms/rooms.module';
import { ZegoModule } from './modules/zego/zego.module';
import { LiveKitModule } from './modules/livekit/livekit.module';
import { ChatModule } from './modules/chat/chat.module';
import { GiftsModule } from './modules/gifts/gifts.module';
import { WalletModule } from './modules/wallet/wallet.module';
import { VipModule } from './modules/vip/vip.module';
import { AgenciesModule } from './modules/agencies/agencies.module';
import { RankingModule } from './modules/ranking/ranking.module';
import { NotificationsModule } from './modules/notifications/notifications.module';
import { PaymentsModule } from './modules/payments/payments.module';
import { AdminModule } from './modules/admin/admin.module';
import { UploadsModule } from './modules/uploads/uploads.module';
import { RealtimeModule } from './modules/realtime/realtime.module';
import { GamesModule } from './modules/games/games.module';
import { CosmeticsModule } from './modules/cosmetics/cosmetics.module';
import { ContestsModule } from './modules/contests/contests.module';
import { PlazaEventsModule } from './modules/plaza-events/plaza-events.module';
import { AppConfigModule } from './modules/config/config.module';
import { TasksModule } from './modules/tasks/tasks.module';
import { LuckyBoxesModule } from './modules/lucky-boxes/lucky-boxes.module';
import { RechargeAgentsModule } from './modules/recharge-agents/recharge-agents.module';
import { DramaModule } from './modules/drama/drama.module';
import { HostTargetModule } from './modules/host-target/host-target.module';
import { InvitesModule } from './modules/invites/invites.module';
import { RoomCupModule } from './modules/room-cup/room-cup.module';
import { PromotionsModule } from './modules/promotions/promotions.module';
import { VanityIdsModule } from './modules/vanity-ids/vanity-ids.module';
import { ModerationModule } from './modules/moderation/moderation.module';
import { EconomyModule } from './modules/economy/economy.module';
import { User } from './database/entities/user.entity';

const entityList = Object.values(entities).filter(
  (e) => typeof e === 'function',
) as Function[];

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      load: [configuration],
      envFilePath: ['.env', '.env.example'],
    }),
    ThrottlerModule.forRootAsync({
      inject: [ConfigService],
      useFactory: (config: ConfigService) => [
        {
          // Single global bucket. Named "strict"/"auth" must NOT be global —
          // they were capping the whole API at 8–12 req/min and blanking the app.
          name: 'default',
          ttl: (config.get<number>('app.throttleTtl') || 60) * 1000,
          limit: config.get<number>('app.throttleLimit') || 400,
        },
      ],
    }),
    TypeOrmModule.forRootAsync({
      inject: [ConfigService],
      useFactory: (config: ConfigService) => ({
        type: 'postgres' as const,
        ...(config.get<string>('app.database.url')
          ? { url: config.get<string>('app.database.url') }
          : {
              host: config.get<string>('app.database.host'),
              port: config.get<number>('app.database.port'),
              username: config.get<string>('app.database.username'),
              password: config.get<string>('app.database.password'),
              database: config.get<string>('app.database.database'),
            }),
        ssl: config.get<boolean>('app.database.ssl')
          ? { rejectUnauthorized: false }
          : undefined,
        schema: 'jeho_own',
        extra: { options: '-c search_path=jeho_own' },
        entities: entityList,
        synchronize: config.get<boolean>('app.database.synchronize'),
        logging: config.get<boolean>('app.database.logging'),
      }),
      dataSourceFactory: async (options) => {
        if (!options || options.type !== 'postgres') {
          throw new Error('JEHO-OWN requires a PostgreSQL database connection.');
        }

        // Create the isolated schema before TypeORM initializes entities.
        // The bootstrap connection never synchronizes tables.
        const bootstrap = new DataSource({
          ...options,
          schema: 'public',
          entities: [],
          synchronize: false,
          extra: {},
        } as any);
        await bootstrap.initialize();
        try {
          await bootstrap.query('CREATE SCHEMA IF NOT EXISTS "jeho_own"');
        } finally {
          await bootstrap.destroy();
        }

        const appDataSource = await new DataSource(options).initialize();

        // Safe startup diagnostics: identify the connected database/schema without logging credentials.
        const [connectionInfo] = await appDataSource.query(
          'SELECT current_database() AS database_name, current_schema() AS schema_name, current_setting(\'search_path\') AS search_path',
        );
        const [{ table_count: tableCount }] = await appDataSource.query(
          'SELECT COUNT(*)::int AS table_count FROM information_schema.tables WHERE table_schema = $1',
          ['jeho_own'],
        );
        console.log(
          `[JEHO-OWN DB] database=${connectionInfo.database_name} schema=${connectionInfo.schema_name} search_path=${connectionInfo.search_path} tables=${tableCount}`,
        );

        return appDataSource;
      },
    }),
    TypeOrmModule.forFeature([User]),
    RedisModule,
    SecurityModule,
    // Registered early — @Global(), so all downstream modules can inject EconomySettingsService.
    EconomyModule,
    AuthModule,
    UsersModule,
    RoomsModule,
    ZegoModule,
    LiveKitModule,
    ChatModule,
    GiftsModule,
    WalletModule,
    VipModule,
    AgenciesModule,
    RankingModule,
    NotificationsModule,
    PaymentsModule,
    AdminModule,
    UploadsModule,
    RealtimeModule,
    GamesModule,
    CosmeticsModule,
    AppConfigModule,
    TasksModule,
    ContestsModule,
    PlazaEventsModule,
    LuckyBoxesModule,
    RechargeAgentsModule,
    DramaModule,
    HostTargetModule,
    InvitesModule,
    RoomCupModule,
    PromotionsModule,
    VanityIdsModule,
    ModerationModule,
  ],
  providers: [
    // JWT first so AppThrottlerGuard can key by user id (not shared carrier IP).
    { provide: APP_GUARD, useClass: JwtAuthGuard },
    { provide: APP_GUARD, useClass: AppThrottlerGuard },
    { provide: APP_GUARD, useClass: SecurityShieldGuard },
  ],
})
export class AppModule {}
