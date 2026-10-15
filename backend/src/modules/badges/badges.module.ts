import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { User } from '../../database/entities/user.entity';
import { BadgesController, AdminBadgesController } from './badges.controller';
import { BadgesService } from './badges.service';

@Module({
  imports: [
    // JWT authentication is supplied by the app-wide APP_GUARD.
    TypeOrmModule.forFeature([User]),
  ],
  controllers: [BadgesController, AdminBadgesController],
  providers: [BadgesService],
  exports: [BadgesService],
})
export class BadgesModule {}
