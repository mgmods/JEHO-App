import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { LuckyBoxesController } from './lucky-boxes.controller';
import { LuckyBoxesService } from './lucky-boxes.service';
import { LuckyBox } from '../../database/entities/lucky-box.entity';
import { LuckyBoxOpen } from '../../database/entities/lucky-box-open.entity';
import { LuckyRewardGrant } from '../../database/entities/lucky-reward-grant.entity';
import { Wallet } from '../../database/entities/wallet.entity';
import { AppSetting } from '../../database/entities/app-setting.entity';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      LuckyBox,
      LuckyBoxOpen,
      LuckyRewardGrant,
      Wallet,
      AppSetting,
    ]),
  ],
  controllers: [LuckyBoxesController],
  providers: [LuckyBoxesService],
  exports: [LuckyBoxesService],
})
export class LuckyBoxesModule {}
