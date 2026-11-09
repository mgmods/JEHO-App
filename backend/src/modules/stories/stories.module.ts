import { Module } from '@nestjs/common';
import { StoriesController, AdminStoriesController } from './stories.controller';
import { StoriesService } from './stories.service';

@Module({
  controllers: [StoriesController, AdminStoriesController],
  providers: [StoriesService],
  exports: [StoriesService],
})
export class StoriesModule {}
