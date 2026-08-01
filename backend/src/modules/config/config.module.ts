import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { ConfigController } from './config.controller';
import { TranslateController } from './translate.controller';
import { ZegoModule } from '../zego/zego.module';

@Module({
  imports: [TypeOrmModule.forFeature([AppSetting]), ZegoModule],
  controllers: [ConfigController, TranslateController],
})
export class AppConfigModule {}
