import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { VipController } from './vip.controller';
import { VipService } from './vip.service';
import { VipPlan } from '../../database/entities/vip-plan.entity';
import { UserVip } from '../../database/entities/user-vip.entity';
import { Wallet } from '../../database/entities/wallet.entity';
import { CosmeticsModule } from '../cosmetics/cosmetics.module';

@Module({
  imports: [
    TypeOrmModule.forFeature([VipPlan, UserVip, Wallet]),
    CosmeticsModule,
  ],
  controllers: [VipController],
  providers: [VipService],
  exports: [VipService],
})
export class VipModule {}
