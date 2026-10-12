import { Body, Controller, Get, Post, Param, Query } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { CurrentUser, Public } from '../../common/decorators';
import { ParseUUIDPipe } from '@nestjs/common';
import { CpService } from './cp.service';
import { CpType } from '../../database/entities/cp-relationship.entity';

@ApiTags('CP')
@ApiBearerAuth()
@Controller('cp')
export class CpController {
  constructor(private readonly cp: CpService) {}

  @Public()
  @Get('users/:id')
  @ApiOperation({ summary: 'Public active CP relationships for a profile; no financial data' })
  profileRelationships(@Param('id', ParseUUIDPipe) userId: string) {
    return this.cp.profileRelationships(userId);
  }

  @Get('catalog')
  @ApiOperation({ summary: 'Enabled CP types and configured gift prices' })
  catalog() {
    return this.cp.catalog();
  }

  @Get('discover')
  @ApiOperation({ summary: 'Real active CP relationships, ranked by points' })
  discover(@Query('type') type?: CpType) {
    return this.cp.discover(type);
  }

  @Get('me')
  me(@CurrentUser('sub') userId: string) {
    return this.cp.forUser(userId);
  }

  @Get('requests')
  requests(@CurrentUser('sub') userId: string) {
    return this.cp.requests(userId);
  }

  @Post('requests')
  @ApiOperation({ summary: 'Send a paid CP initiation gift from a shared voice room' })
  createRequest(
    @CurrentUser('sub') userId: string,
    @Body() body: { recipientId: string; type: CpType; roomId: string; idempotencyKey: string },
  ) {
    return this.cp.createRequest(userId, body);
  }

  @Get('sent-requests')
  sentRequests(@CurrentUser('sub') userId: string) {
    return this.cp.sentRequests(userId);
  }

  @Post('requests/:id/cancel')
  cancelRequest(@CurrentUser('sub') userId: string, @Param('id') id: string) {
    return this.cp.cancelRequest(userId, id);
  }

  @Post('requests/:id/respond')
  respond(
    @CurrentUser('sub') userId: string,
    @Param('id') id: string,
    @Body('accept') accept: boolean,
  ) {
    return this.cp.respond(userId, id, Boolean(accept));
  }

  @Post(':id/end')
  end(@CurrentUser('sub') userId: string, @Param('id') id: string) {
    return this.cp.end(userId, id);
  }
}
