import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AuthModule } from '../auth/auth.module';
import { User } from '../../database/entities/user.entity';
import { StoriesController, AdminStoriesController } from './stories.controller';
import { StoriesService } from './stories.service';

@Module({
  // StoriesController and AdminStoriesController use JwtAuthGuard locally.
  // Import AuthModule to make its exported JwtModule/JwtService available here,
  // and register User so the guard's @InjectRepository(User) resolves in this context.
  imports: [AuthModule, TypeOrmModule.forFeature([User])],
  controllers: [StoriesController, AdminStoriesController],
  providers: [StoriesService],
  exports: [StoriesService],
})
export class StoriesModule {}
