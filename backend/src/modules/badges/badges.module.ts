import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AuthModule } from '../auth/auth.module';
import { JwtAuthGuard } from '../../common/guards/jwt-auth.guard';
import { User } from '../../database/entities/user.entity';
import { BadgesController, AdminBadgesController } from './badges.controller';
import { BadgesService } from './badges.service';

@Module({
  imports: [AuthModule, TypeOrmModule.forFeature([User])],
  controllers: [BadgesController, AdminBadgesController],
  providers: [BadgesService, JwtAuthGuard],
  exports: [BadgesService],
})
export class BadgesModule {}
