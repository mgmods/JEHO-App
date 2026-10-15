import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AuthModule } from '../auth/auth.module';
import { User } from '../../database/entities/user.entity';
import { BadgesController, AdminBadgesController } from './badges.controller';
import { BadgesService } from './badges.service';

@Module({
  imports: [AuthModule, TypeOrmModule.forFeature([User])],
  controllers: [BadgesController, AdminBadgesController],
  providers: [BadgesService],
  exports: [BadgesService],
})
export class BadgesModule {}
