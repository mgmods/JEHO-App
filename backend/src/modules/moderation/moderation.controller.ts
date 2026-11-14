import { Controller, Get } from '@nestjs/common';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import { ModerationService } from './moderation.service';
import { Public } from '../../common/decorators';

@ApiTags('Moderation')
@Controller('moderation')
export class ModerationController {
  constructor(private readonly moderation: ModerationService) {}

  @Public()
  @Get('config')
  @ApiOperation({ summary: 'Moderation config for clients (live NSFW removed)' })
  getConfig() {
    return this.moderation.getConfig();
  }
}
