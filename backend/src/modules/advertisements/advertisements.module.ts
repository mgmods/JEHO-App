import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { Advertisement } from '../../database/entities/advertisement.entity';
import { User } from '../../database/entities/user.entity';
import { RealtimeModule } from '../realtime/realtime.module';
import { AdvertisementsService } from './advertisements.service';
import { AdvertisementsController } from './advertisements.controller';

@Module({
  imports: [TypeOrmModule.forFeature([Advertisement, User]), RealtimeModule],
  controllers: [AdvertisementsController],
  providers: [AdvertisementsService],
  exports: [AdvertisementsService],
})
export class AdvertisementsModule {}