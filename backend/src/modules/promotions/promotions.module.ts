import { Module, forwardRef } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { UserPromoProgress } from '../../database/entities/user-promo-progress.entity';
import { User } from '../../database/entities/user.entity';
import { VanityId } from '../../database/entities/vanity-id.entity';
import { CosmeticsModule } from '../cosmetics/cosmetics.module';
import { AuthModule } from '../auth/auth.module';
import { PromotionsService } from './promotions.service';
import { PromotionsController } from './promotions.controller';

@Module({
  imports: [
    AuthModule,
    TypeOrmModule.forFeature([AppSetting, UserPromoProgress, User, VanityId]),
    forwardRef(() => CosmeticsModule),
  ],
  controllers: [PromotionsController],
  providers: [PromotionsService],
  exports: [PromotionsService],
})
export class PromotionsModule {}
