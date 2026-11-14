import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { Cosmetic } from '../../database/entities/cosmetic.entity';
import { UserCosmetic } from '../../database/entities/user-cosmetic.entity';
import { UserProfile } from '../../database/entities/user-profile.entity';
import { User } from '../../database/entities/user.entity';
import { UserVip } from '../../database/entities/user-vip.entity';
import { Wallet } from '../../database/entities/wallet.entity';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { CosmeticsController } from './cosmetics.controller';
import { CosmeticsService } from './cosmetics.service';
import { UploadsModule } from '../uploads/uploads.module';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      Cosmetic,
      UserCosmetic,
      UserProfile,
      User,
      UserVip,
      Wallet,
      AppSetting,
    ]),
    UploadsModule,
  ],
  controllers: [CosmeticsController],
  providers: [CosmeticsService],
  exports: [CosmeticsService],
})
export class CosmeticsModule {}
