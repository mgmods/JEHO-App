import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { User } from '../../database/entities/user.entity';
import { AuthModule } from '../auth/auth.module';
import { BadgesController, AdminBadgesController } from './badges.controller';
import { BadgesService } from './badges.service';

@Module({
  imports: [
    // Badge controllers use JwtAuthGuard directly, so the module must import
    // AuthModule to make its exported JwtModule/JwtService available here.
    AuthModule,
    TypeOrmModule.forFeature([User]),
  ],
  controllers: [BadgesController, AdminBadgesController],
  providers: [BadgesService],
  exports: [BadgesService],
})
export class BadgesModule {}
