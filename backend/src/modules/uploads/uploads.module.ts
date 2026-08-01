import { Module } from '@nestjs/common';
import { UploadsController } from './uploads.controller';
import { UploadsService } from './uploads.service';
import { MediaCleanupService } from './media-cleanup.service';

@Module({
  controllers: [UploadsController],
  providers: [UploadsService, MediaCleanupService],
  exports: [UploadsService, MediaCleanupService],
})
export class UploadsModule {}
