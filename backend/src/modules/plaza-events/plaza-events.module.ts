import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { PlazaEventsController } from './plaza-events.controller';
import { PlazaEventsService } from './plaza-events.service';
import { PlazaEvent } from '../../database/entities/plaza-event.entity';
import { PlazaEventSubscription } from '../../database/entities/plaza-event-subscription.entity';
import { Room } from '../../database/entities/room.entity';
import { User } from '../../database/entities/user.entity';
import { Agency } from '../../database/entities/agency.entity';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      PlazaEvent,
      PlazaEventSubscription,
      Room,
      User,
      Agency,
    ]),
  ],
  controllers: [PlazaEventsController],
  providers: [PlazaEventsService],
  exports: [PlazaEventsService],
})
export class PlazaEventsModule {}
