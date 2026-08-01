import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { Wallet } from '../../database/entities/wallet.entity';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { User } from '../../database/entities/user.entity';
import { AgencyMember } from '../../database/entities/agency-member.entity';
import { TasksController } from './tasks.controller';
import { TasksService } from './tasks.service';
import { RealtimeModule } from '../realtime/realtime.module';
import { CosmeticsModule } from '../cosmetics/cosmetics.module';
import { VipModule } from '../vip/vip.module';

@Module({
  imports: [
    TypeOrmModule.forFeature([Wallet, AppSetting, User, AgencyMember]),
    RealtimeModule,
    CosmeticsModule,
    VipModule,
  ],
  controllers: [TasksController],
  providers: [TasksService],
  exports: [TasksService],
})
export class TasksModule {}
