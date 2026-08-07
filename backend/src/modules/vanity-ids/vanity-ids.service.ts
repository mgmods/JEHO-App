import { Injectable, Logger, OnModuleInit } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { VanityId, VanityIdStatus } from '../../database/entities/vanity-id.entity';

/** Pretty numerical IDs with length-based coins. Seeded when catalog is empty. */
const SEED_CATALOG: Array<{ publicId: string; priceCoins: number }> = [
  { publicId: '888', priceCoins: 50000 },
  { publicId: '999', priceCoins: 50000 },
  { publicId: '1111', priceCoins: 25000 },
  { publicId: '2222', priceCoins: 25000 },
  { publicId: '3333', priceCoins: 25000 },
  { publicId: '5555', priceCoins: 25000 },
  { publicId: '6666', priceCoins: 28000 },
  { publicId: '7777', priceCoins: 30000 },
  { publicId: '8888', priceCoins: 35000 },
  { publicId: '9999', priceCoins: 35000 },
  { publicId: '10001', priceCoins: 12000 },
  { publicId: '12345', priceCoins: 10000 },
  { publicId: '5201314', priceCoins: 45000 },
  { publicId: '66666', priceCoins: 18000 },
  { publicId: '88888', priceCoins: 22000 },
  { publicId: '99999', priceCoins: 22000 },
  { publicId: '100000', priceCoins: 8000 },
  { publicId: '111111', priceCoins: 15000 },
  { publicId: '888888', priceCoins: 28000 },
  { publicId: '999999', priceCoins: 28000 },
  { publicId: '1234567', priceCoins: 6000 },
  { publicId: '10000000', priceCoins: 3500 },
  { publicId: '88888888', priceCoins: 18000 },
  { publicId: '520520520', priceCoins: 12000 },
];

@Injectable()
export class VanityIdsService implements OnModuleInit {
  private readonly log = new Logger(VanityIdsService.name);

  constructor(
    @InjectRepository(VanityId)
    private readonly vanityRepo: Repository<VanityId>,
  ) {}

  async onModuleInit() {
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
