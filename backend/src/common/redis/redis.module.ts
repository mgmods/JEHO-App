import { Global, Inject, Injectable, Module, OnModuleDestroy } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import Redis from 'ioredis';

export const REDIS_CLIENT = 'REDIS_CLIENT';

@Injectable()
export class RedisService implements OnModuleDestroy {
  constructor(@Inject(REDIS_CLIENT) private readonly client: Redis) {}

  getClient(): Redis {
    return this.client;
  }

  async get(key: string): Promise<string | null> {
    try { return await this.client.get(key); } catch { return null; }
  }

  async set(key: string, value: string, ttlSeconds?: number): Promise<void> {
    try {
      if (ttlSeconds) await this.client.set(key, value, 'EX', ttlSeconds);
      else await this.client.set(key, value);
    } catch {}
  }

  async del(key: string): Promise<void> {
    try { await this.client.del(key); } catch {}
  }

  async incr(key: string): Promise<number> {
    try { return await this.client.incr(key); } catch { return 1; }
  }

  async expire(key: string, ttlSeconds: number): Promise<void> {
    try { await this.client.expire(key, ttlSeconds); } catch {}
  }

  async onModuleDestroy() {
    try { await this.client.quit(); } catch {}
  }
}

@Global()
@Module({
  providers: [
    {
      provide: REDIS_CLIENT,
      inject: [ConfigService],
      useFactory: (config: ConfigService) => new Redis({
        host: config.get<string>('app.redis.host') || 'localhost',
        port: config.get<number>('app.redis.port') || 6379,
        password: config.get<string>('app.redis.password') || undefined,
        lazyConnect: true,
        maxRetriesPerRequest: 1,
        enableOfflineQueue: false,
      }),
    },
    RedisService,
  ],
  exports: [REDIS_CLIENT, RedisService],
})
export class RedisModule {}
