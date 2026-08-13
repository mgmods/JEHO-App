import { Global, Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { EconomySettingsService } from './economy-settings.service';

@Global()
@Module({
  imports: [TypeOrmModule.forFeature([AppSetting])],
  providers: [EconomySettingsService],
  exports: [EconomySettingsService],
})
export class EconomyModule {}
