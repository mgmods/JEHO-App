import {
  Controller,
  Get,
  Post,
  Delete,
  Body,
  Param,
  Query,
  ParseUUIDPipe,
  UseGuards,
} from '@nestjs/common';
import { ApiTags, ApiBearerAuth, ApiOperation } from '@nestjs/swagger';
import { LuckyBoxesService } from './lucky-boxes.service';
import { CurrentUser } from '../../common/decorators';
import { AdminGuard } from '../../common/guards/admin.guard';
import { PaginationDto } from '../../common/dto/pagination.dto';
import {
  OpenBoxDto,
  AdminUpsertBoxDto,
  FundLuckyPoolDto,
} from './dto/lucky-boxes.dto';

@ApiTags('Lucky Boxes')
@ApiBearerAuth()
@Controller('lucky-boxes')
export class LuckyBoxesController {
  constructor(private readonly luckyBoxesService: LuckyBoxesService) {}

  @Get()
  @ApiOperation({ summary: 'List active lucky boxes with remaining opens today' })
  listActive(@CurrentUser('sub') userId: string) {
    return this.luckyBoxesService.listActive(userId);
  }

  @Post(':id/open')
  @ApiOperation({ summary: 'Open a lucky box (server-authoritative RNG)' })
  open(
    @CurrentUser('sub') userId: string,
    @Param('id', ParseUUIDPipe) boxId: string,
    @Body() dto: OpenBoxDto,
  ) {
    return this.luckyBoxesService.open(userId, boxId, dto);
  }

  @Post('fund/room/:roomId')
  @ApiOperation({ summary: 'Room host funds host-sponsored lucky boxes' })
  fundRoom(
    @CurrentUser('sub') userId: string,
    @Param('roomId', ParseUUIDPipe) roomId: string,
    @Body() dto: FundLuckyPoolDto,
  ) {
    return this.luckyBoxesService.fundRoom(userId, roomId, dto.amount);
  }

  @Post('fund/agency/:agencyId')
  @ApiOperation({ summary: 'Agency owner/manager funds agency lucky boxes' })
  fundAgency(
    @CurrentUser('sub') userId: string,
    @Param('agencyId', ParseUUIDPipe) agencyId: string,
    @Body() dto: FundLuckyPoolDto,
  ) {
    return this.luckyBoxesService.fundAgency(userId, agencyId, dto.amount);
  }

  // ─── Admin endpoints ────────────────────────────────────

  @Get('admin/boxes')
  @UseGuards(AdminGuard)
  @ApiOperation({ summary: 'Admin: list all lucky boxes' })
  adminListBoxes() {
    return this.luckyBoxesService.adminListBoxes();
  }

  @Post('admin/boxes')
  @UseGuards(AdminGuard)
  @ApiOperation({ summary: 'Admin: create or update a lucky box' })
  adminUpsertBox(@Body() dto: AdminUpsertBoxDto) {
    return this.luckyBoxesService.adminUpsertBox(dto);
  }

  @Delete('admin/boxes/:id')
  @UseGuards(AdminGuard)
  @ApiOperation({ summary: 'Admin: delete a lucky box' })
  adminDeleteBox(@Param('id', ParseUUIDPipe) id: string) {
    return this.luckyBoxesService.adminDeleteBox(id);
  }

  @Get('admin/opens')
  @UseGuards(AdminGuard)
  @ApiOperation({ summary: 'Admin: list all box opens (paginated)' })
  adminListOpens(@Query() query: PaginationDto) {
    return this.luckyBoxesService.adminListOpens(query);
  }
}
