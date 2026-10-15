import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { JwtModule } from '@nestjs/jwt';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { AuthModule } from '../auth/auth.module';
import { JwtAuthGuard } from '../../common/guards/jwt-auth.guard';
import { User } from '../../database/entities/user.entity';
import { BadgesController, AdminBadgesController } from './badges.controller';
import { BadgesService } from './badges.service';

@Module({
  imports: [
    AuthModule,
    // Register JWT explicitly in this feature module so route-level JwtAuthGuard
    // can resolve JwtService even if AuthModule exports differ in the deployed build.
    JwtModule.registerAsync({
      imports: [ConfigModule],
      inject: [ConfigService],
      useFactory: (config: ConfigService) => ({
        secret: config.get<string>('app.jwt.secret'),
        signOptions: {
          expiresIn: config.get<string>('app.jwt.expiresIn'),
        },
      }),
    }),
    TypeOrmModule.forFeature([User]),
  ],
  controllers: [BadgesController, AdminBadgesController],
  providers: [BadgesService, JwtAuthGuard],
  exports: [BadgesService],
})
export class BadgesModule {}
