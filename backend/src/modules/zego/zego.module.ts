import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { ZegoTokenService } from './zego-token.service';
import { ZegoSettingsService } from './zego-settings.service';

/** Shared ZEGOCLOUD Token04 + settings (voice rooms). */
@Module({
  imports: [TypeOrmModule.forFeature([AppSetting])],
  providers: [ZegoTokenService, ZegoSettingsService],
  exports: [ZegoTokenService, ZegoSettingsService],
})
export class ZegoModule {}
