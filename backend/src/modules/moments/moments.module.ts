import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AuthModule } from '../auth/auth.module';
import { User } from '../../database/entities/user.entity';
import { MomentsController } from './moments.controller';
import { MomentsService } from './moments.service';

@Module({
  // JwtAuthGuard injects Repository<User>; repositories are module-scoped in Nest.
  imports: [AuthModule, TypeOrmModule.forFeature([User])],
  controllers: [MomentsController],
  providers: [MomentsService],
  exports: [MomentsService],
})
export class MomentsModule {}
