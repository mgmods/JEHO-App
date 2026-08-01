import { Controller, Get } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { CurrentUser } from '../../common/decorators';
import { HostTargetService } from './host-target.service';

@ApiTags('Host target')
@ApiBearerAuth()
@Controller('host-target')
export class HostTargetController {
  constructor(private readonly hostTargetService: HostTargetService) {}

  @Get('me')
  @ApiOperation({ summary: 'Current host monthly target progress and stages' })
  me(@CurrentUser('sub') userId: string) {
    return this.hostTargetService.getMyTarget(userId);
  }
}
