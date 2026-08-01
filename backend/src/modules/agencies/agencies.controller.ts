import {
  Controller,
  Get,
  Post,
  Patch,
  Delete,
  Body,
  Param,
  Query,
  ParseUUIDPipe,
  GoneException,
} from '@nestjs/common';
import { ApiTags, ApiBearerAuth, ApiOperation } from '@nestjs/swagger';
import { AgenciesService } from './agencies.service';
import { CurrentUser, Public } from '../../common/decorators';
import { PaginationDto } from '../../common/dto/pagination.dto';
import {
  CreateAgencyDto,
  AddMemberDto,
  UpdateMemberRoleDto,
  SubmitAgencyApplicationDto,
  JoinByCodeDto,
  UpdateAgencySettingsDto,
  UpdateCommissionDto,
} from './dto/agencies.dto';
import { CreateRoomDto } from '../rooms/dto/rooms.dto';

@ApiTags('Agencies')
@ApiBearerAuth()
@Controller('agencies')
export class AgenciesController {
  constructor(private readonly agenciesService: AgenciesService) {}

  @Post()
  @ApiOperation({ summary: 'Deprecated: submit via /agencies/applications' })
  create(@CurrentUser('sub') userId: string, @Body() dto: CreateAgencyDto) {
    return this.agenciesService.create(userId, dto);
  }

  @Post('purchase')
  @ApiOperation({ summary: 'Deprecated: agency applications are free' })
  purchase(@CurrentUser('sub') userId: string, @Body() dto: CreateAgencyDto) {
    return this.agenciesService.purchase(userId, dto);
  }

  @Public()
  @Get('pricing')
  @ApiOperation({ summary: 'Agency create price + platform cut' })
  pricing() {
    return this.agenciesService.pricing();
  }

  @Post('applications')
  @ApiOperation({ summary: 'Submit a free professional agency application' })
  submitApplication(
    @CurrentUser('sub') userId: string,
    @Body() dto: SubmitAgencyApplicationDto,
  ) {
    return this.agenciesService.submitApplication(userId, dto);
  }

  @Get('applications/mine')
  @ApiOperation({ summary: 'List current user agency applications' })
  myApplications(@CurrentUser('sub') userId: string) {
    return this.agenciesService.myApplications(userId);
  }

  @Get('mine')
  @ApiOperation({ summary: 'Agency owned or managed by current user (+ earnings)' })
  mine(@CurrentUser('sub') userId: string) {
    return this.agenciesService.mine(userId);
  }

  @Public()
  @Get()
  list(@Query() query: PaginationDto) {
    return this.agenciesService.list(query);
  }

  @Public()
  @Get(':id')
  get(@Param('id', ParseUUIDPipe) id: string) {
    return this.agenciesService.get(id);
  }

  @Public()
  @Get(':id/room')
  @ApiOperation({ summary: 'Get or create the permanent agency room' })
  getRoom(@Param('id', ParseUUIDPipe) id: string) {
    return this.agenciesService.getAgencyRoom(id);
  }

  @Post(':id/room/enter')
  @ApiOperation({ summary: 'Enter the permanent agency room (auth required)' })
  enterRoom(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
  ) {
    return this.agenciesService.enterAgencyRoom(id, userId);
  }

  @Post(':id/room/open')
  @ApiOperation({ summary: 'Open or resume the current host permanent agency room' })
  openRoom(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
    @Body() dto: CreateRoomDto,
  ) {
    return this.agenciesService.openAgencyRoom(id, userId, dto);
  }

  @Get(':id/stats')
  stats(@Param('id', ParseUUIDPipe) id: string) {
    return this.agenciesService.stats(id);
  }

  @Get(':id/earnings')
  @ApiOperation({ summary: 'Agency owner earnings breakdown (commission / platform / host)' })
  earnings(@Param('id', ParseUUIDPipe) id: string, @CurrentUser('sub') userId: string) {
    return this.agenciesService.earnings(id, userId);
  }

  @Post(':id/distribute')
  @ApiOperation({ summary: 'Owner distributes diamonds from wallet to an agency member' })
  distribute(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
    @Body() body: { userId: string; diamonds: number },
  ) {
    return this.agenciesService.distributeEarnings(
      id,
      userId,
      body?.userId,
      Number(body?.diamonds) || 0,
    );
  }

  @Post('join-by-code')
  @ApiOperation({ summary: 'Apply to join an agency using its activation code' })
  joinByCode(
    @CurrentUser('sub') userId: string,
    @Body() dto: JoinByCodeDto,
  ) {
    return this.agenciesService.joinByCode(userId, dto.code);
  }

  @Post(':id/join')
  @ApiOperation({ summary: 'Deprecated: hosts must use POST /agencies/join-by-code' })
  join() {
    throw new GoneException({
      code: 'AGENCY_ACTIVATION_CODE_REQUIRED',
      message: 'الانضمام يتم بكود تفعيل الوكالة فقط عبر /agencies/join-by-code',
    });
  }

  @Patch(':id/settings')
  @ApiOperation({ summary: 'Update agency notification style (owner/manager)' })
  updateSettings(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
    @Body() dto: UpdateAgencySettingsDto,
  ) {
    return this.agenciesService.updateSettings(id, userId, dto);
  }

  @Patch(':id/commission')
  @ApiOperation({ summary: 'Update agency commission percent (owner only)' })
  updateCommission(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
    @Body() dto: UpdateCommissionDto,
  ) {
    return this.agenciesService.updateCommission(id, userId, dto);
  }

  @Get(':id/join-requests')
  @ApiOperation({ summary: 'List pending join requests (owner/manager)' })
  joinRequests(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
  ) {
    return this.agenciesService.listJoinRequests(id, userId);
  }

  @Post(':id/join-requests/:userId/approve')
  @ApiOperation({ summary: 'Approve pending join request' })
  approveJoin(
    @Param('id', ParseUUIDPipe) id: string,
    @Param('userId', ParseUUIDPipe) memberUserId: string,
    @CurrentUser('sub') userId: string,
  ) {
    return this.agenciesService.approveJoin(id, userId, memberUserId);
  }

  @Post(':id/join-requests/:userId/reject')
  @ApiOperation({ summary: 'Reject pending join request' })
  rejectJoin(
    @Param('id', ParseUUIDPipe) id: string,
    @Param('userId', ParseUUIDPipe) memberUserId: string,
    @CurrentUser('sub') userId: string,
  ) {
    return this.agenciesService.rejectJoin(id, userId, memberUserId);
  }

  @Post(':id/leave')
  @ApiOperation({ summary: 'Leave agency (non-owners)' })
  leave(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
  ) {
    return this.agenciesService.leaveSelf(id, userId);
  }

  @Delete(':id')
  @ApiOperation({ summary: 'Permanently delete agency (owner only)' })
  deleteOwn(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
  ) {
    return this.agenciesService.deleteOwn(id, userId);
  }

  @Post(':id/members')
  addMember(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
    @Body() dto: AddMemberDto,
  ) {
    return this.agenciesService.addMember(id, userId, dto);
  }

  @Patch(':id/members/:userId')
  updateRole(
    @Param('id', ParseUUIDPipe) id: string,
    @Param('userId') memberUserId: string,
    @CurrentUser('sub') userId: string,
    @Body() dto: UpdateMemberRoleDto,
  ) {
    return this.agenciesService.updateRole(id, userId, memberUserId, dto);
  }

  @Post(':id/members/:userId/suspend')
  @ApiOperation({ summary: 'Suspend agency member (owner/manager)' })
  suspendMember(
    @Param('id', ParseUUIDPipe) id: string,
    @Param('userId') memberUserId: string,
    @CurrentUser('sub') userId: string,
  ) {
    return this.agenciesService.suspendMember(id, userId, memberUserId);
  }

  @Post(':id/members/:userId/unsuspend')
  @ApiOperation({ summary: 'Restore suspended agency member (owner/manager)' })
  unsuspendMember(
    @Param('id', ParseUUIDPipe) id: string,
    @Param('userId') memberUserId: string,
    @CurrentUser('sub') userId: string,
  ) {
    return this.agenciesService.unsuspendMember(id, userId, memberUserId);
  }

  @Delete(':id/members/:userId')
  removeMember(
    @Param('id', ParseUUIDPipe) id: string,
    @Param('userId') memberUserId: string,
    @CurrentUser('sub') userId: string,
  ) {
    return this.agenciesService.removeMember(id, userId, memberUserId);
  }
}
