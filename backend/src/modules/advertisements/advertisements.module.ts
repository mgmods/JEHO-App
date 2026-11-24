import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { Advertisement } from '../../database/entities/advertisement.entity';
import { User } from '../../database/entities/user.entity';
import { RealtimeModule } from '../realtime/realtime.module';
import { AuthModule } from '../auth/auth.module';
import { AdvertisementsService } from './advertisements.service';
import { AdvertisementsController } from './advertisements.controller';

@Module({
  // The controller uses JwtAuthGuard directly. Import AuthModule so this
  // feature module receives the same configured JwtService and guard provider.
  imports: [TypeOrmModule.forFeature([Advertisement, User]), RealtimeModule, AuthModule],
  controllers: [AdvertisementsController],
  providers: [AdvertisementsService],
  exports: [AdvertisementsService],
})
export class AdvertisementsModule {}
