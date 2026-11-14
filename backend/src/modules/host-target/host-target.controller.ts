import { BadRequestException, Controller, Get, Query } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { CurrentUser } from '../../common/decorators';
import { HostTargetService } from './host-target.service';

@ApiTags('Host target')
@ApiBearerAuth()
@Controller('host-target')
export class HostTargetController {
  constructor(private readonly hostTargetService: HostTargetService) {}

  @Get('me')
  @ApiOperation({
    summary: 'Host target progress (period = weekly or monthly from settings)',
  })
  me(@CurrentUser('sub') userId: string) {
    return this.hostTargetService.getMyTarget(userId);
  }

  @Get('withdraw-options')
  @ApiOperation({
    summary:
      'Target-stage withdraw amounts (host salary / agency agent share) — no wallet packages',
  })
  withdrawOptions(
    @CurrentUser('sub') userId: string,
    @Query('role') role?: string,
    @Query('agencyId') agencyId?: string,
  ) {
    const r = String(role || 'host').toLowerCase();
    if (r !== 'host' && r !== 'agency') {
      throw new BadRequestException('role must be host or agency');
    }
    return this.hostTargetService.getWithdrawOptions(
      userId,
      r as 'host' | 'agency',
      agencyId,
    );
  }
}
