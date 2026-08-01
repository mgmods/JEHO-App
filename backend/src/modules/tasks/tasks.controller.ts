import { Controller, Get, Post, Param, Query } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { CurrentUser } from '../../common/decorators';
import { TasksService } from './tasks.service';

@ApiTags('Tasks')
@ApiBearerAuth()
@Controller('tasks')
export class TasksController {
  constructor(private readonly tasks: TasksService) {}

  @Get('daily')
  @ApiOperation({ summary: 'Daily tasks with claim state for current user' })
  list(
    @CurrentUser('sub') userId: string,
    @Query('roomId') roomId?: string,
    @Query('agencyId') agencyId?: string,
  ) {
    return this.tasks.listForUser(userId, { roomId, agencyId });
  }

  @Post('checkin')
  @ApiOperation({ summary: 'Daily check-in progress (explicit tap)' })
  checkIn(@CurrentUser('sub') userId: string) {
    return this.tasks.checkIn(userId);
  }

  @Post('daily/:id/claim')
  claim(@CurrentUser('sub') userId: string, @Param('id') taskId: string) {
    return this.tasks.claim(userId, taskId);
  }
}
