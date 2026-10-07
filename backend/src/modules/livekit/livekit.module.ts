import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { LiveKitSettingsService } from './livekit-settings.service';
import { LiveKitTokenService } from './livekit-token.service';

/** Self-hosted LiveKit (open-source voice RTC alternative to ZEGOCLOUD). */
@Module({
  imports: [TypeOrmModule.forFeature([AppSetting])],
  providers: [LiveKitSettingsService, LiveKitTokenService],
  exports: [LiveKitSettingsService, LiveKitTokenService],
})
export class LiveKitModule {}
