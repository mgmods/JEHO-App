import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { WalletModule } from '../wallet/wallet.module';
import { DramaService } from './drama.service';
import { DramaController } from './drama.controller';

@Module({
  imports: [TypeOrmModule.forFeature([AppSetting]), WalletModule],
  controllers: [DramaController],
  providers: [DramaService],
  exports: [DramaService],
})
export class DramaModule {}
