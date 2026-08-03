import { Module } from '@nestjs/common';
import { UploadsController } from './uploads.controller';
import { UploadsService } from './uploads.service';
import { MediaCleanupService } from './media-cleanup.service';
import { ModerationModule } from '../moderation/moderation.module';

@Module({
  imports: [ModerationModule],
  controllers: [UploadsController],
  providers: [UploadsService, MediaCleanupService],
  exports: [UploadsService, MediaCleanupService],
})
export class UploadsModule {}
