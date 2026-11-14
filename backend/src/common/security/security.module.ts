import { Global, Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AbuseLog } from '../../database/entities/abuse-log.entity';
import { User } from '../../database/entities/user.entity';
import { SecurityShieldGuard, SecurityShieldService } from './security-shield.service';

@Global()
@Module({
  imports: [TypeOrmModule.forFeature([AbuseLog, User])],
  providers: [SecurityShieldService, SecurityShieldGuard],
  exports: [SecurityShieldService, SecurityShieldGuard],
})
export class SecurityModule {}
