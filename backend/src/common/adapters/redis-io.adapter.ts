import { INestApplicationContext, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { IoAdapter } from '@nestjs/platform-socket.io';
import { createAdapter } from '@socket.io/redis-adapter';
import Redis from 'ioredis';
import { ServerOptions } from 'socket.io';

export class RedisIoAdapter extends IoAdapter {
  private readonly logger = new Logger(RedisIoAdapter.name);
  private pubClient: Redis | null = null;
  private subClient: Redis | null = null;
  private adapterConstructor: ReturnType<typeof createAdapter> | null = null;

  constructor(
    app: INestApplicationContext,
    private readonly config: ConfigService,
  ) {
    super(app);
  }

  async connect(): Promise<boolean> {
    // Redis is optional for the single-instance Render deployment. Do not
    // probe localhost when no Redis service was explicitly configured.
    if (!process.env.REDIS_HOST) {
      this.logger.log('Socket.IO Redis not configured; using single-process adapter');
      return false;
    }

    const options = {
      host: this.config.get<string>('app.redis.host') || 'localhost',
      port: this.config.get<number>('app.redis.port') || 6379,
      password: this.config.get<string>('app.redis.password') || undefined,
      lazyConnect: true,
      connectTimeout: 5_000,
      maxRetriesPerRequest: null,
      enableOfflineQueue: true,
    };
    this.pubClient = new Redis(options);
    this.subClient = this.pubClient.duplicate();
    const onRedisError = (error: unknown) => {
      const message = error instanceof Error ? error.message : String(error);
      if (!message.includes('ECONNREFUSED')) {
        this.logger.warn(`Socket.IO Redis error: ${message}`);
      }
    };
    this.pubClient.on('error', onRedisError);
    this.subClient.on('error', onRedisError);
    try {
      await Promise.all([this.pubClient.connect(), this.subClient.connect()]);
      this.adapterConstructor = createAdapter(this.pubClient, this.subClient);
      this.logger.log('Socket.IO Redis adapter connected');
      return true;
    } catch (error) {
      this.logger.warn(
        `Redis adapter unavailable; using single-process Socket.IO: ${(error as Error).message}`,
      );
      this.pubClient.disconnect();
      this.subClient.disconnect();
      this.pubClient = null;
      this.subClient = null;
      return false;
    }
  }

  createIOServer(port: number, options?: ServerOptions) {
    const server = super.createIOServer(port, options);
    if (this.adapterConstructor) server.adapter(this.adapterConstructor);
    return server;
  }
}
