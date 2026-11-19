import { Injectable, Logger, OnModuleInit } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { DataSource, Repository } from 'typeorm';
import { JEHO_SCHEMA_LOCK_NAMESPACE, JEHO_SCHEMA_LOCK_ID } from '../../database/schema-migration-lock';
import { VanityId, VanityIdStatus } from '../../database/entities/vanity-id.entity';

/** Six-digit numerical IDs with length-based coin prices. Seeded when catalog is empty. */
const SEED_CATALOG: Array<{ publicId: string; priceCoins: number }> = [
  { publicId: '100001', priceCoins: 8000 },
  { publicId: '100008', priceCoins: 9000 },
  { publicId: '111111', priceCoins: 15000 },
  { publicId: '123456', priceCoins: 18000 },
  { publicId: '222222', priceCoins: 12000 },
  { publicId: '333333', priceCoins: 12000 },
  { publicId: '444444', priceCoins: 12000 },
  { publicId: '555555', priceCoins: 15000 },
  { publicId: '666666', priceCoins: 18000 },
  { publicId: '777777', priceCoins: 22000 },
  { publicId: '888888', priceCoins: 28000 },
  { publicId: '999999', priceCoins: 28000 },
  { publicId: '101010', priceCoins: 10000 },
  { publicId: '121212', priceCoins: 10000 },
  { publicId: '131313', priceCoins: 10000 },
  { publicId: '202020', priceCoins: 10000 },
  { publicId: '123321', priceCoins: 15000 },
  { publicId: '112233', priceCoins: 16000 },
  { publicId: '654321', priceCoins: 18000 },
  { publicId: '123123', priceCoins: 14000 },
  { publicId: '456456', priceCoins: 14000 },
  { publicId: '789789', priceCoins: 14000 },
  { publicId: '888000', priceCoins: 16000 },
  { publicId: '999000', priceCoins: 16000 },

];

@Injectable()
export class VanityIdsService implements OnModuleInit {
  private readonly log = new Logger(VanityIdsService.name);
  private schemaRetryTimer: ReturnType<typeof setInterval> | null = null;

  constructor(
    @InjectRepository(VanityId)
    private readonly vanityRepo: Repository<VanityId>,
    private readonly dataSource: DataSource,
  ) {}

  onModuleInit(): void {
    void this.initializeSchemaAndCatalog();
  }

  private async initializeSchemaAndCatalog(): Promise<void> {
    try {
      await this.dataSource.transaction(async (manager) => {
        await manager.query("SET LOCAL lock_timeout = '250ms'");
        await manager.query("SET LOCAL statement_timeout = '8s'");
        const lockRows = await manager.query('SELECT pg_try_advisory_xact_lock($1, $2) AS locked', [JEHO_SCHEMA_LOCK_NAMESPACE, JEHO_SCHEMA_LOCK_ID]);
        if (lockRows?.[0]?.locked !== true) throw new Error('JEHO schema migration lock is busy; retrying later');
        await manager.query('ALTER TABLE vanity_ids ADD COLUMN IF NOT EXISTS "styleIconUrl" VARCHAR(512)');
        await manager.query('ALTER TABLE vanity_ids ADD COLUMN IF NOT EXISTS "styleVipLevel" INTEGER NOT NULL DEFAULT 1');
        await manager.query('ALTER TABLE vanity_ids ADD COLUMN IF NOT EXISTS "targetType" VARCHAR(16)');
        await manager.query('ALTER TABLE vanity_ids ADD COLUMN IF NOT EXISTS "targetId" UUID');
        await manager.query('ALTER TABLE vanity_ids ADD COLUMN IF NOT EXISTS "previousTargetPublicId" VARCHAR(32)');
      });
    } catch (e) {
      this.log.warn(`Vanity ID schema migration deferred: ${e instanceof Error ? e.message : String(e)}`);
      if (!this.schemaRetryTimer) {
        this.schemaRetryTimer = setInterval(() => void this.initializeSchemaAndCatalog(), 30_000);
        this.schemaRetryTimer.unref?.();
      }
      return;
    }
    try {
      let added = 0;
      for (const seed of SEED_CATALOG) {
        const exists = await this.vanityRepo.exist({
          where: { publicId: seed.publicId },
        });
        if (exists) continue;
        try {
          await this.vanityRepo.save(
            this.vanityRepo.create({
              publicId: seed.publicId,
              priceCoins: seed.priceCoins,
              status: VanityIdStatus.AVAILABLE,
            }),
          );
          added++;
        } catch {
          // unique race
        }
      }
      const available = await this.vanityRepo.count({
        where: { status: VanityIdStatus.AVAILABLE },
      });
      this.log.log(
        `Vanity catalog ready: +${added} seeded, ${available} available for sale`,
      );
    } catch (e) {
      this.log.warn(
        `vanity seed skipped: ${e instanceof Error ? e.message : String(e)}`,
      );
    }
    if (this.schemaRetryTimer) clearInterval(this.schemaRetryTimer);
    this.schemaRetryTimer = null;
  }
}
