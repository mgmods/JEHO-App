import { Module } from '@nestjs/common';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { TypeOrmModule } from '@nestjs/typeorm';
import { DataSource } from 'typeorm';
import {
  JEHO_SCHEMA_LOCK_ID,
  JEHO_SCHEMA_LOCK_NAMESPACE,
  tryAcquireJehoSchemaLock,
  releaseJehoSchemaLock,
} from './database/schema-migration-lock';
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
import { StoriesModule } from './modules/stories/stories.module';
import { HostTargetModule } from './modules/host-target/host-target.module';
import { InvitesModule } from './modules/invites/invites.module';
import { RoomCupModule } from './modules/room-cup/room-cup.module';
import { CpModule } from './modules/cp/cp.module';
import { PromotionsModule } from './modules/promotions/promotions.module';
import { VanityIdsModule } from './modules/vanity-ids/vanity-ids.module';
import { ModerationModule } from './modules/moderation/moderation.module';
import { BadgesModule } from './modules/badges/badges.module';
import { MomentsModule } from './modules/moments/moments.module';
import { AdvertisementsModule } from './modules/advertisements/advertisements.module';
import { EconomyModule } from './modules/economy/economy.module';
import { User } from './database/entities/user.entity';
import { AdminUser } from './database/entities/admin-user.entity';
import { ensureBadgeTables } from './database/badges-bootstrap';
import { ensureVanityIdStyleColumns } from './database/vanity-id-style-bootstrap';
import { ensureMomentsTables } from './database/moments-bootstrap';
import { ensureUserProfileColumns } from './database/user-profile-bootstrap';
import { PostgresLockMonitorService } from './database/postgres-lock-monitor.service';

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
        // Keep the pool deliberately small for the 1 GB managed PostgreSQL
        // instance; reserve connections for maintenance/health checks.
        extra: {
          options: '-c search_path=jeho_own,extensions,public -c lock_timeout=3s -c statement_timeout=120s -c idle_in_transaction_session_timeout=120s -c application_name=jeho-own-api',
          max: 5,
          min: 0,
          idleTimeoutMillis: 30000,
          connectionTimeoutMillis: 20000,
          keepAlive: true,
          keepAliveInitialDelayMillis: 10000,
        },
        entities: entityList,
        // JEHO production deployments must never run TypeORM synchronize, regardless of host NODE_ENV/DB_SYNCHRONIZE values.
        // Schema changes are handled only by reviewed additive bootstraps/migrations; this protects live OrioleDB relations.
        synchronize: false,
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
          // Bootstrap is short-lived and must not open an oversized pool.
          extra: {
            options: '-c lock_timeout=3s -c statement_timeout=120s -c idle_in_transaction_session_timeout=120s -c application_name=jeho-own-bootstrap',
            max: 1,
            min: 0,
            idleTimeoutMillis: 10000,
            connectionTimeoutMillis: 20000,
            keepAlive: true,
          },
        } as any);
        const bootstrapStartedAt = Date.now();
        console.log('[JEHO-OWN DB] Opening bounded bootstrap connection...');
        await bootstrap.initialize();
        console.log(`[JEHO-OWN DB] Bootstrap connection opened in ${Date.now() - bootstrapStartedAt}ms.`);
        let bootstrapLockAcquired = false;
        try {
          // Most restarts already have the schema and UUID extension. Check first
          // so every API replica does not contend for the global migration lock
          // when no bootstrap DDL is needed.
          await bootstrap.query("SET lock_timeout = '250ms'");
          await bootstrap.query("SET statement_timeout = '8s'");
          const readBootstrapState = async () => {
            const [state] = await bootstrap.query(`
              SELECT
                EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = 'jeho_own') AS schema_exists,
                EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'uuid-ossp') AS uuid_extension_exists
            `);
            return state;
          };
          let bootstrapState = await readBootstrapState();
          if (!bootstrapState.schema_exists || !bootstrapState.uuid_extension_exists) {
            // Only the first install/missing-extension path needs the shared lock.
            for (let attempt = 0; attempt < 30 && !bootstrapLockAcquired; attempt += 1) {
              const lockRows = await bootstrap.query(
                'SELECT pg_try_advisory_lock($1, $2) AS locked',
                [JEHO_SCHEMA_LOCK_NAMESPACE, JEHO_SCHEMA_LOCK_ID],
              );
              bootstrapLockAcquired = lockRows?.[0]?.locked === true;
              if (!bootstrapLockAcquired) {
                await new Promise((resolve) => setTimeout(resolve, 500));
              }
            }
            if (!bootstrapLockAcquired) {
              throw new Error('[JEHO-OWN DB] Shared schema lock remained busy; refusing unsynchronized startup DDL.');
            }
            // Recheck after acquiring the lock; another replica may have finished
            // the bootstrap between our first catalog check and lock acquisition.
            bootstrapState = await readBootstrapState();
            if (!bootstrapState.uuid_extension_exists) {
              await bootstrap.query('CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public');
            }
            if (!bootstrapState.schema_exists) {
              await bootstrap.query('CREATE SCHEMA IF NOT EXISTS "jeho_own"');
            }
          }
        } finally {
          if (bootstrapLockAcquired) {
            await bootstrap.query(
              'SELECT pg_advisory_unlock($1, $2)',
              [JEHO_SCHEMA_LOCK_NAMESPACE, JEHO_SCHEMA_LOCK_ID],
            ).catch(() => undefined);
          }
          await bootstrap.query('RESET lock_timeout').catch(() => undefined);
          await bootstrap.query('RESET statement_timeout').catch(() => undefined);
          await bootstrap.destroy();
        }

        console.log('[JEHO-OWN DB] Bootstrap connection ready; initializing application schema...');
        const appInitStartedAt = Date.now();
        // Enforce this again at the final DataSource construction boundary.
        // The Nest TypeORM factory must never inherit a host/env override here.
        console.log(`[JEHO-OWN DB] Final DataSource options: synchronize=${String(options.synchronize)}; forcing false.`);
        const appDataSource = await new DataSource({
          ...options,
          synchronize: false,
          extra: {
            ...((options as any).extra || {}),
            max: 5,
            min: 0,
            idleTimeoutMillis: 30000,
            connectionTimeoutMillis: 20000,
            keepAlive: true,
            keepAliveInitialDelayMillis: 10000,
          },
          connectTimeoutMS: 20000,
        } as any).initialize();
        console.log(`[JEHO-OWN DB] Application DataSource initialized in ${Date.now() - appInitStartedAt}ms (synchronize=${String(options.synchronize)}).`);

        // Do not await schema maintenance from TypeORM's DataSource factory.
        // The API must finish Nest initialization and bind HTTP even if an older
        // PostgreSQL transaction holds a relation lock. Each migration below is
        // idempotent/bounded and reports its own failure for a later deploy retry.
        let schemaMaintenanceRunning = false;
        let schemaMaintenanceComplete = false;
        const runSchemaMaintenance = async (): Promise<void> => {
          if (schemaMaintenanceRunning || schemaMaintenanceComplete) return;
          schemaMaintenanceRunning = true;
          let schemaMaintenanceHadFailure = false;
          try {
            // All application-owned additive schema work shares one database-wide
            // advisory lock with CP, agency and VIP migrations. The lock is held on
            // this pinned connection while the individual idempotent DDL runs.
            const schemaLockRunner = appDataSource.createQueryRunner();
            let schemaLockAcquired = false;
            try {
              await schemaLockRunner.connect();
              await schemaLockRunner.query("SET lock_timeout = '250ms'");
              await schemaLockRunner.query("SET statement_timeout = '90s'");
              for (let attempt = 0; attempt < 60 && !schemaLockAcquired; attempt += 1) {
                schemaLockAcquired = await tryAcquireJehoSchemaLock(schemaLockRunner);
                if (!schemaLockAcquired) {
                  await new Promise((resolve) => setTimeout(resolve, 1000));
                }
              }
              if (!schemaLockAcquired) {
                console.warn('[JEHO-OWN DB] Shared schema migration lock remained busy; this pass is deferred.');
              }
            } catch (lockError) {
              console.error('[JEHO-OWN DB] Could not acquire shared schema migration lock.', lockError instanceof Error ? lockError.message : String(lockError));
            }
            if (!schemaLockAcquired) {
              await schemaLockRunner.query('RESET lock_timeout').catch(() => undefined);
              await schemaLockRunner.query('RESET statement_timeout').catch(() => undefined);
              await schemaLockRunner.release().catch(() => undefined);
              return;
            }
            try {
            // Global voice-room advertisements: additive, idempotent schema bootstrap.
            // This runs under the shared JEHO schema advisory lock and never synchronizes existing tables.
            console.log('[JEHO-OWN DB STEP] room_advertisements: start');
            try {
              await schemaLockRunner.query(`
                CREATE TABLE IF NOT EXISTS "jeho_own"."room_advertisements" (
                  "id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                  "senderId" uuid NOT NULL REFERENCES "jeho_own"."users"("id") ON DELETE CASCADE,
                  "senderName" varchar(128) NOT NULL,
                  "senderImageUrl" varchar(512),
                  "text" varchar(500) NOT NULL,
                  "status" varchar(16) NOT NULL DEFAULT 'queued',
                  "queueOrder" bigint NOT NULL DEFAULT 0,
                  "startedAt" timestamptz,
                  "completedAt" timestamptz,
                  "nextEligibleAt" timestamptz,
                  "rejectionCode" varchar(64),
                  "rejectionReason" varchar(255),
                  "idempotencyKey" varchar(128),
                  "displayTemplateId" varchar(64),
                  "displayDurationMs" integer NOT NULL DEFAULT 9000,
                  "createdAt" timestamptz NOT NULL DEFAULT now(),
                  "updatedAt" timestamptz NOT NULL DEFAULT now()
                )
              `);
              await schemaLockRunner.query('CREATE INDEX IF NOT EXISTS "idx_room_ads_queue" ON "jeho_own"."room_advertisements" ("status","queueOrder","createdAt")');
              await schemaLockRunner.query('CREATE INDEX IF NOT EXISTS "idx_room_ads_sender_day" ON "jeho_own"."room_advertisements" ("senderId","createdAt")');
              await schemaLockRunner.query('CREATE UNIQUE INDEX IF NOT EXISTS "uq_room_ads_idempotency" ON "jeho_own"."room_advertisements" ("idempotencyKey") WHERE "idempotencyKey" IS NOT NULL');
              await schemaLockRunner.query(`
                INSERT INTO "jeho_own"."app_settings" ("id","key","value","description","createdAt","updatedAt")
                SELECT gen_random_uuid(),'voice_room_ads_config',
                  '{"enabled":true,"sendButtonEnabled":true,"paused":false,"dailyLimit":1,"maxQueueSize":500,"maxTextLength":180,"resetTimezone":"UTC","displayDurationMs":9000,"gapMs":1800,"entrySpeedPxPerSec":120,"exitSpeedPxPerSec":120,"reducedMotionStatic":true,"template":"premium-v1","position":"safe-bottom","direction":"rtl","background":"#21142d","backgroundImage":"","backgroundOpacity":92,"radiusDp":18,"heightDp":48,"avatarDp":34,"avatarShape":"circle","nameSp":12,"textSp":13,"nameColor":"#FFD98A","textColor":"#FFFFFF","spacingDp":8}',
                  'Voice room advertising live configuration',NOW(),NOW()
                WHERE NOT EXISTS (SELECT 1 FROM "jeho_own"."app_settings" WHERE "key"='voice_room_ads_config')
              `);
              await schemaLockRunner.query(`
                INSERT INTO "jeho_own"."app_settings" ("id","key","value","description","createdAt","updatedAt")
                SELECT gen_random_uuid(),'voice_room_ads_banned_words','',
                  'Voice room advertising banned words, comma-separated',NOW(),NOW()
                WHERE NOT EXISTS (SELECT 1 FROM "jeho_own"."app_settings" WHERE "key"='voice_room_ads_banned_words')
              `);
              console.log('[JEHO-OWN DB STEP] room_advertisements: complete');
            } catch (error) {
              schemaMaintenanceHadFailure = true;
              console.error('[AdvertisementsBootstrap] Could not initialize advertisement tables/settings.', error instanceof Error ? error.message : String(error));
            }

            // Additive support-box video URL column; preserves all existing lucky-box rows.
            console.log('[JEHO-OWN DB STEP] lucky_boxes videoUrl: start');
            try {
              await schemaLockRunner.query(
                'ALTER TABLE "jeho_own"."lucky_boxes" ADD COLUMN IF NOT EXISTS "videoUrl" varchar(1024)',
              );
              console.log('[JEHO-OWN DB STEP] lucky_boxes videoUrl: complete');
            } catch (error) {
              schemaMaintenanceHadFailure = true;
              console.error('[LuckyBoxVideoBootstrap] Could not initialize video URL column.', error instanceof Error ? error.message : String(error));
            }
    
            // Create the optional custom-gift grants table with a short lock bound.
            // Index creation is deliberately not part of API startup: index DDL can wait
            // behind unrelated transactions and must be handled by a reviewed migration.
            console.log('[JEHO-OWN DB STEP] custom_gift_grants: start');
            try {
              console.log('[JEHO-OWN DB STEP] custom_gift_grants table: start');
              await schemaLockRunner.query(`
                CREATE TABLE IF NOT EXISTS "jeho_own"."custom_gift_grants" (
                  "id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                  "userId" uuid NOT NULL REFERENCES "jeho_own"."users"("id") ON DELETE CASCADE,
                  "giftId" uuid NOT NULL UNIQUE REFERENCES "jeho_own"."gifts"("id") ON DELETE CASCADE,
                  "grantedBy" uuid NOT NULL REFERENCES "jeho_own"."users"("id") ON DELETE RESTRICT,
                  "mediaType" varchar(16) NOT NULL CHECK ("mediaType" IN ('image','video','gif')),
                  "mediaUrl" varchar(512) NOT NULL,
                  "durationDays" integer NULL CHECK ("durationDays" IS NULL OR "durationDays" >= 1),
                  "startsAt" timestamptz NOT NULL DEFAULT now(),
                  "expiresAt" timestamptz NULL,
                  "revokedAt" timestamptz NULL,
                  "createdAt" timestamptz NOT NULL DEFAULT now()
                )
              `);
              console.log('[CustomGiftsBootstrap] Private custom-gift grants table is ready; optional indexes deferred to migration.');
              console.log('[JEHO-OWN DB STEP] custom_gift_grants: complete');
            } catch (error) {
              schemaMaintenanceHadFailure = true;
              const dbError = error as { code?: string; message?: string; detail?: string; constraint?: string };
              console.error(
                '[CustomGiftsBootstrap] Optional table bootstrap failed; API startup will continue.',
                JSON.stringify({
                  code: dbError?.code || null,
                  message: dbError?.message || String(error),
                  detail: dbError?.detail || null,
                  constraint: dbError?.constraint || null,
                }),
              );
            }
    
            // Additive support-chest expiry bootstrap. Use one pinned connection and
            // strict lock/statement bounds so DDL can never stall API startup indefinitely.
            // The column and its optional index are separate autocommit statements: if index
            // creation is blocked, the successfully-added nullable column is preserved.
            console.log('[JEHO-OWN DB STEP] gift_sends expiry: start');
            try {
              console.log('[JEHO-OWN DB STEP] gift_sends expiresAt column: start');
              await schemaLockRunner.query(
                'ALTER TABLE "jeho_own"."gift_sends" ADD COLUMN IF NOT EXISTS "expiresAt" timestamptz',
              );
              console.log('[JEHO-OWN DB STEP] gift_sends expiresAt column: complete');
              console.log('[JEHO-OWN DB STEP] gift_sends expiry index: start');
              await schemaLockRunner.query(
                'CREATE INDEX IF NOT EXISTS "IDX_gift_sends_expiresAt" ON "jeho_own"."gift_sends" ("expiresAt")',
              );
              console.log('[JEHO-OWN DB STEP] gift_sends expiry index: complete');
            } catch (error) {
              schemaMaintenanceHadFailure = true;
              const dbError = error as { code?: string; message?: string; detail?: string };
              console.error(
                '[SupportChestExpiryBootstrap] Bounded optional expiry DDL did not complete; continuing startup.',
                JSON.stringify({
                  code: dbError?.code || null,
                  message: dbError?.message || String(error),
                  detail: dbError?.detail || null,
                }),
              );
              try {
                const blockers = await schemaLockRunner.query(`
                  SELECT
                    blocked.pid AS blocked_pid,
                    blocked.usename AS blocked_user,
                    blocked.application_name AS blocked_application,
                    blocked.state AS blocked_state,
                    blocked.wait_event_type,
                    blocked.wait_event,
                    now() - blocked.query_start AS blocked_for,
                    left(blocked.query, 240) AS blocked_query,
                    blocker.pid AS blocker_pid,
                    blocker.usename AS blocker_user,
                    blocker.application_name AS blocker_application,
                    blocker.state AS blocker_state,
                    blocker.wait_event_type AS blocker_wait_event_type,
                    blocker.wait_event AS blocker_wait_event,
                    blocker.xact_start AS blocker_xact_start,
                    now() - blocker.xact_start AS blocker_transaction_age,
                    blocker.backend_xid AS blocker_backend_xid,
                    pg_blocking_pids(blocker.pid) AS blocker_blocked_by,
                    now() - blocker.query_start AS blocker_query_age,
                    left(blocker.query, 240) AS blocker_query
                  FROM pg_stat_activity AS blocked
                  CROSS JOIN LATERAL unnest(pg_blocking_pids(blocked.pid)) AS blocking(pid)
                  JOIN pg_stat_activity AS blocker ON blocker.pid = blocking.pid
                  WHERE blocked.datname = current_database()
                  ORDER BY blocked.query_start
                  LIMIT 20
                `);
                console.error('[SupportChestExpiryBootstrap] PostgreSQL lock blockers:', JSON.stringify(blockers));
              } catch (diagnosticError) {
                console.error('[SupportChestExpiryBootstrap] Lock diagnostics unavailable:', diagnosticError instanceof Error ? diagnosticError.message : String(diagnosticError));
              }
            }
    
    
            // Create only the additive badge tables missing from older deployments.
            // This never touches voice, Redis, user records, or existing badge data.
            console.log('[JEHO-OWN DB STEP] badges bootstrap: start');
            try {
              await ensureBadgeTables(schemaLockRunner);
              console.log('[JEHO-OWN DB STEP] badges bootstrap: complete');
            } catch (error) {
              schemaMaintenanceHadFailure = true;
              console.error(
                '[BadgesBootstrap] Could not initialize badge tables; API startup continues.',
                error instanceof Error ? error.message : String(error),
              );
            }
    
            // CP schema bootstrap is deliberately owned by CpService after Nest has
            // initialized. A PostgreSQL DDL lock must never prevent the HTTP API from
            // binding its port; CP routes are guarded until the additive bootstrap and
            // unified-relationship migration have both completed.
    
            // Additive vanity-ID style columns; existing IDs, purchases and leases are preserved.
            console.log('[JEHO-OWN DB STEP] vanity ID bootstrap: start');
            try {
              await ensureVanityIdStyleColumns(schemaLockRunner);
              console.log('[JEHO-OWN DB STEP] vanity ID bootstrap: complete');
            } catch (error) {
              schemaMaintenanceHadFailure = true;
              console.error('[VanityIdStyleBootstrap] Could not initialize style columns; API startup continues.', error instanceof Error ? error.message : String(error));
            }
    
            // Additive persistent moments tables; existing application data is preserved.
            console.log('[JEHO-OWN DB STEP] moments bootstrap: start');
            try {
              await ensureMomentsTables(schemaLockRunner);
              console.log('[JEHO-OWN DB STEP] moments bootstrap: complete');
            } catch (error) {
              schemaMaintenanceHadFailure = true;
              console.error(
                '[MomentsBootstrap] Could not initialize moments tables; API startup continues.',
                error instanceof Error ? error.message : String(error),
              );
            }
    
            // Repair profile columns missing from older production schemas before serving requests.
            console.log('[JEHO-OWN DB STEP] user profile bootstrap: start');
            try {
              await ensureUserProfileColumns(schemaLockRunner);
              console.log('[JEHO-OWN DB STEP] user profile bootstrap: complete');
            } catch (error) {
              schemaMaintenanceHadFailure = true;
              console.error(
                '[UserProfileBootstrap] Could not initialize profile columns; API startup continues.',
                error instanceof Error ? error.message : String(error),
              );
            }
    
            // Safe startup diagnostics: identify the connected database/schema without logging credentials.
            console.log('[JEHO-OWN DB STEP] final database diagnostics: start');
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
    
            if (schemaMaintenanceHadFailure) {
              console.warn('[JEHO-OWN DB] One or more additive schema steps failed; the safe migration pass will retry in 30 seconds.');
            } else {
              schemaMaintenanceComplete = true;
              console.log('[JEHO-OWN DB STEP] all database bootstraps complete');
            }
            } finally {
              if (schemaLockAcquired) {
                await releaseJehoSchemaLock(schemaLockRunner).catch(() => undefined);
              }
              await schemaLockRunner.query('RESET lock_timeout').catch(() => undefined);
              await schemaLockRunner.query('RESET statement_timeout').catch(() => undefined);
              await schemaLockRunner.release().catch(() => undefined);
            }
    
          } catch (error) {
            console.error('[JEHO-OWN DB] Background additive schema maintenance failed.', error instanceof Error ? error.message : String(error));
          } finally {
            schemaMaintenanceRunning = false;
            if (!schemaMaintenanceComplete) {
              const retry = setTimeout(() => void runSchemaMaintenance(), 30_000);
              retry.unref?.();
            }
          }
        };
        const initialMaintenance = setTimeout(() => void runSchemaMaintenance(), 0);
        initialMaintenance.unref?.();
        return appDataSource;
      },
    }),
    TypeOrmModule.forFeature([User, AdminUser]),
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
    StoriesModule,
    HostTargetModule,
    InvitesModule,
    RoomCupModule,
    CpModule,
    PromotionsModule,
    VanityIdsModule,
    ModerationModule,
    BadgesModule,
    MomentsModule,
    AdvertisementsModule,
  ],
  providers: [
    PostgresLockMonitorService,
    // JWT first so AppThrottlerGuard can key by user id (not shared carrier IP).
    { provide: APP_GUARD, useClass: JwtAuthGuard },
    { provide: APP_GUARD, useClass: AppThrottlerGuard },
    { provide: APP_GUARD, useClass: SecurityShieldGuard },
  ],
})
export class AppModule {}
