import { Body, Controller, Get, Post, Req } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { Throttle } from '@nestjs/throttler';
import { CurrentUser } from '../../common/decorators';
import { SecurityShieldService } from '../../common/security/security-shield.service';
import { InvitesService } from './invites.service';
import { IsString, MinLength } from 'class-validator';
import { Request } from 'express';

class BindInviteDto {
  @IsString()
  @MinLength(2)
  code: string;
}

@ApiTags('Invites')
@ApiBearerAuth()
@Controller('invites')
export class InvitesController {
  constructor(
    private readonly invites: InvitesService,
    private readonly shield: SecurityShieldService,
  ) {}

  @Get('me')
  @ApiOperation({ summary: 'My invite code + stats' })
  me(@CurrentUser('sub') userId: string) {
    return this.invites.me(userId);
  }

  @Post('bind')
  @Throttle({ default: { limit: 5, ttl: 60_000 } })
  @ApiOperation({ summary: 'Bind an invite / promo code once' })
  async bind(
    @CurrentUser('sub') userId: string,
    @Body() dto: BindInviteDto,
    @Req() req: Request,
  ) {
    const ip = this.shield.clientIp(req);
    const over = await this.shield.checkBurst(`invite-bind:${ip}`, 8, 3600, {
      ip,
      userId,
      autoBan: true,
    });
    if (over) {
      return { ok: false, message: 'too_many_attempts' };
    }
    return this.invites.bind(userId, dto.code);
  }
}
