import { Injectable, Logger, OnModuleInit } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { DataSource, Repository } from 'typeorm';
import { VanityId, VanityIdStatus } from '../../database/entities/vanity-id.entity';

/** Pretty numerical IDs with length-based coins. Seeded when catalog is empty. */
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

  constructor(
    @InjectRepository(VanityId)
    private readonly vanityRepo: Repository<VanityId>,
    private readonly dataSource: DataSource,
  ) {}

  async onModuleInit() {
    try {
      await this.dataSource.query('ALTER TABLE vanity_ids ADD COLUMN IF NOT EXISTS "targetType" VARCHAR(16)');
      await this.dataSource.query('ALTER TABLE vanity_ids ADD COLUMN IF NOT EXISTS "targetId" UUID');
    } catch (e) {
      this.log.warn(`vanity target columns ensure skipped: ${e instanceof Error ? e.message : String(e)}`);
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
  }
}
