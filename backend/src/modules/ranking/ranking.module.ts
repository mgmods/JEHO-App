import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { RankingController } from './ranking.controller';
import { RankingService } from './ranking.service';
import { RankingSnapshot } from '../../database/entities/ranking-snapshot.entity';
import { Agency } from '../../database/entities/agency.entity';
import { Room } from '../../database/entities/room.entity';
import { GiftSend } from '../../database/entities/gift-send.entity';
import { User } from '../../database/entities/user.entity';
import { Follow } from '../../database/entities/follow.entity';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      RankingSnapshot,
      Agency,
      Room,
      GiftSend,
      User,
      Follow,
    ]),
  ],
  controllers: [RankingController],
  providers: [RankingService],
  exports: [RankingService],
})
export class RankingModule {}
