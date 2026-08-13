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
} from '@nestjs/common';
import { ApiTags, ApiBearerAuth, ApiOperation } from '@nestjs/swagger';
import { RoomsService } from './rooms.service';
import { InternetMusicService } from './internet-music.service';
import { CurrentUser, Public } from '../../common/decorators';
import { PaginationDto } from '../../common/dto/pagination.dto';
import {
  CreateRoomDto,
  JoinRoomDto,
  TakeSeatDto,
  KickBanDto,
  SetMicDto,
  SetCohostDto,
  SetRoomBackgroundDto,
  SetRoomFrameDto,
  UpdateRoomDto,
  RoomMusicDto,
  ModeratorPermissionsDto,
  SeatInviteDto,
  SeatInviteResponseDto,
  TaskRoomInviteDto,
  RoomUserReportDto,
} from './dto/rooms.dto';

@ApiTags('Rooms')
@ApiBearerAuth()
@Controller('rooms')
export class RoomsController {
  constructor(
    private readonly roomsService: RoomsService,
    private readonly internetMusic: InternetMusicService,
  ) {}

  @Post()
  @ApiOperation({ summary: 'Create voice room' })
  create(@CurrentUser('sub') userId: string, @Body() dto: CreateRoomDto) {
    return this.roomsService.create(userId, dto);
  }

  @Public()
  @Get()
  @ApiOperation({ summary: 'List open public rooms' })
  list(@Query() query: PaginationDto) {
    return this.roomsService.list(query);
  }

  @Get('music-library')
  @ApiOperation({ summary: 'List shared room music library' })
  musicLibrary() {
    return this.roomsService.musicLibrary();
  }

  @Get('music-search')
  @ApiOperation({ summary: 'Search internet music (YouTube-like catalog)' })
  musicSearch(@Query('q') q: string) {
    return this.internetMusic.search(q || '');
  }

  @Get('music-resolve')
  @ApiOperation({ summary: 'Resolve internet track to an audio stream URL' })
  musicResolve(@Query('id') id: string) {
    return this.internetMusic.resolve(id || '');
  }

  @Delete('music-library/:trackId')
  @ApiOperation({ summary: 'Remove a track uploaded by the current user' })
  removeMusicTrack(
    @Param('trackId', ParseUUIDPipe) trackId: string,
    @CurrentUser('sub') userId: string,
  ) {
    return this.roomsService.removeMusicTrack(trackId, userId);
  }

  @Get(':id')
  get(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
  ) {
    return this.roomsService.getRoomForViewer(id, userId);
  }

  @Patch(':id')
  @ApiOperation({ summary: 'Update room title/cover (host only)' })
  update(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
    @Body() dto: UpdateRoomDto,
  ) {
    return this.roomsService.update(id, userId, dto);
  }

  @Patch(':id/background')
  @ApiOperation({ summary: 'Set room background (host/mod)' })
  setBackground(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
    @Body() dto: SetRoomBackgroundDto,
  ) {
    return this.roomsService.setBackground(id, userId, dto.backgroundUrl ?? null);
  }

  @Patch(':id/frame')
  @ApiOperation({ summary: 'Set room list frame/card (owner or permitted moderator)' })
  setFrame(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
    @Body() dto: SetRoomFrameDto,
  ) {
    return this.roomsService.setFrame(id, userId, dto.roomCardUrl ?? null);
  }

  @Post(':id/join')
  join(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
    @Body() dto: JoinRoomDto,
  ) {
    return this.roomsService.join(id, userId, dto);
  }

  @Post(':id/follow')
  @ApiOperation({ summary: 'Follow host voice rooms' })
  followRoom(@Param('id', ParseUUIDPipe) id: string, @CurrentUser('sub') userId: string) {
    return this.roomsService.followHostRoom(userId, id);
  }

  @Delete(':id/follow')
  @ApiOperation({ summary: 'Unfollow host voice rooms' })
  unfollowRoom(@Param('id', ParseUUIDPipe) id: string, @CurrentUser('sub') userId: string) {
    return this.roomsService.unfollowHostRoom(userId, id);
  }

  @Get(':id/follow')
  @ApiOperation({ summary: 'Is following host voice rooms' })
  followStatus(@Param('id', ParseUUIDPipe) id: string, @CurrentUser('sub') userId: string) {
    return this.roomsService.isFollowingHostRoom(userId, id);
  }

  @Post(':id/leave')
  leave(@Param('id', ParseUUIDPipe) id: string, @CurrentUser('sub') userId: string) {
    return this.roomsService.leave(id, userId);
  }

  @Post(':id/close')
  @ApiOperation({ summary: 'Host ends the live session (room disappears until reopened)' })
  close(@Param('id', ParseUUIDPipe) id: string, @CurrentUser('sub') userId: string) {
    return this.roomsService.close(id, userId);
  }

  @Get(':id/supporters')
  @ApiOperation({ summary: 'Top three room and agency supporters by gift value' })
  supporters(@Param('id', ParseUUIDPipe) id: string) {
    return this.roomsService.supporters(id);
  }

  @Get(':id/contribute')
  @ApiOperation({
    summary: 'Mikoo room contribution ranks (wealth|charm × day|week|month)',
  })
  contribute(
    @Param('id', ParseUUIDPipe) id: string,
    @Query('type') type?: string,
    @Query('period') period?: string,
    @Query('limit') limit?: string,
  ) {
    const n = Number(limit);
    return this.roomsService.contribute(
      id,
      type || 'wealth',
      Number.isFinite(n) ? n : 50,
      period,
    );
  }

  @Post(':id/reports')
  @ApiOperation({ summary: 'Submit a real moderation report for a room user' })
  reportUser(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
    @Body() dto: RoomUserReportDto,
  ) {
    return this.roomsService.reportUser(id, userId, dto);
  }

  @Get(':id/zego-token')
  @ApiOperation({ summary: 'Issue seat-aware ZEGO token with least privilege' })
  zegoToken(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
  ) {
    return this.roomsService.issueZegoToken(id, userId);
  }

  @Post(':id/music')
  @ApiOperation({ summary: 'Synchronize room music playback' })
  music(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
    @Body() dto: RoomMusicDto,
  ) {
    return this.roomsService.updateMusic(id, userId, dto);
  }

  @Post(':id/seats/take')
  takeSeat(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
    @Body() dto: TakeSeatDto,
  ) {
    return this.roomsService.takeSeat(id, userId, dto);
  }

  @Post(':id/seats/leave')
  leaveSeat(@Param('id', ParseUUIDPipe) id: string, @CurrentUser('sub') userId: string) {
    return this.roomsService.leaveSeat(id, userId);
  }

  @Post(':id/seats/force-leave')
  @ApiOperation({ summary: 'Host/moderator: remove a user from mic (stay in room)' })
  forceLeaveSeat(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
    @Body() dto: KickBanDto,
  ) {
    return this.roomsService.forceLeaveSeatByModerator(id, userId, dto.userId, dto.reason);
  }

  @Post(':id/cohost')
  setCohost(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
    @Body() dto: SetCohostDto,
  ) {
    return this.roomsService.setCohost(id, userId, dto);
  }

  @Post(':id/kick')
  kick(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
    @Body() dto: KickBanDto,
  ) {
    return this.roomsService.kick(id, userId, dto);
  }

  @Post(':id/ban')
  ban(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
    @Body() dto: KickBanDto,
  ) {
    return this.roomsService.ban(id, userId, dto);
  }

  @Get(':id/bans')
  @ApiOperation({ summary: 'List active room bans (host/moderator)' })
  listBans(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
  ) {
    return this.roomsService.listBans(id, userId);
  }

  @Get(':id/bans/:targetUserId')
  banStatus(
    @Param('id', ParseUUIDPipe) id: string,
    @Param('targetUserId', ParseUUIDPipe) targetUserId: string,
    @CurrentUser('sub') userId: string,
  ) {
    return this.roomsService.banStatus(id, userId, targetUserId);
  }

  @Delete(':id/bans/:targetUserId')
  unban(
    @Param('id', ParseUUIDPipe) id: string,
    @Param('targetUserId', ParseUUIDPipe) targetUserId: string,
    @CurrentUser('sub') userId: string,
  ) {
    return this.roomsService.unban(id, userId, targetUserId);
  }

  @Post(':id/moderators/:userId')
  addMod(
    @Param('id', ParseUUIDPipe) id: string,
    @Param('userId', ParseUUIDPipe) modUserId: string,
    @CurrentUser('sub') userId: string,
  ) {
    return this.roomsService.addModerator(id, userId, modUserId);
  }

  @Delete(':id/moderators/:userId')
  @ApiOperation({ summary: 'Remove room moderator / clear cohost if matching' })
  removeMod(
    @Param('id', ParseUUIDPipe) id: string,
    @Param('userId', ParseUUIDPipe) modUserId: string,
    @CurrentUser('sub') userId: string,
  ) {
    return this.roomsService.removeModerator(id, userId, modUserId);
  }

  @Patch(':id/moderators/:userId/permissions')
  @ApiOperation({ summary: 'Owner grants moderator music/frame/game permissions' })
  moderatorPermissions(
    @Param('id', ParseUUIDPipe) id: string,
    @Param('userId', ParseUUIDPipe) modUserId: string,
    @CurrentUser('sub') userId: string,
    @Body() dto: ModeratorPermissionsDto,
  ) {
    return this.roomsService.updateModeratorPermissions(id, userId, modUserId, dto);
  }

  @Post(':id/seat-invites')
  @ApiOperation({ summary: 'Owner/moderator invites an audience member to a mic seat' })
  inviteSeat(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
    @Body() dto: SeatInviteDto,
  ) {
    return this.roomsService.inviteToSeat(id, userId, dto.userId, dto.seatIndex);
  }

  @Post(':id/task-invites')
  @ApiOperation({ summary: 'Agency host invites new male for 40◆ / 2min room dwell reward' })
  inviteTaskGuest(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
    @Body() dto: TaskRoomInviteDto,
  ) {
    return this.roomsService.inviteGuestForTaskReward(id, userId, dto.userId);
  }

  @Post(':id/seat-invites/respond')
  @ApiOperation({ summary: 'Audience member accepts or rejects a mic invitation' })
  respondSeatInvite(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
    @Body() dto: SeatInviteResponseDto,
  ) {
    return this.roomsService.respondToSeatInvite(id, userId, dto.accept);
  }

  @Post(':id/raise-hand')
  @ApiOperation({ summary: 'Request mic/seat access (raise hand)' })
  raiseHand(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
    @Body() body: { raised?: boolean; seatIndex?: number },
  ) {
    return this.roomsService.raiseHand(
      id,
      userId,
      body?.raised !== false,
      body?.seatIndex ?? null,
    );
  }

  @Get(':id/seat-requests')
  @ApiOperation({ summary: 'List pending mic/seat requests (host/mod)' })
  listSeatRequests(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
  ) {
    return this.roomsService.listSeatRequests(id, userId);
  }

  @Post(':id/seat-requests/approve')
  @ApiOperation({ summary: 'Approve guest mic/seat request' })
  approveSeat(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
    @Body() body: { userId: string; seatIndex?: number },
  ) {
    return this.roomsService.approveSeat(id, userId, body.userId, body?.seatIndex ?? null);
  }

  @Post(':id/seat-requests/reject')
  @ApiOperation({ summary: 'Reject guest mic/seat request' })
  rejectSeat(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
    @Body() body: { userId: string },
  ) {
    return this.roomsService.rejectSeat(id, userId, body.userId);
  }

  @Post(':id/gift-sounds')
  @ApiOperation({ summary: 'Enable or mute gift sounds for everyone in the room' })
  setGiftSounds(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
    @Body() body: { enabled?: boolean },
  ) {
    return this.roomsService.setGiftSounds(id, userId, body?.enabled !== false);
  }

  @Post(':id/display-settings')
  @ApiOperation({ summary: 'Mikoo room-more display toggles (chat/charm/banner/effects)' })
  setDisplaySettings(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
    @Body()
    body: {
      chatZoneEnabled?: boolean;
      charmEnabled?: boolean;
      bannerEnabled?: boolean;
      micInteractEnabled?: boolean;
      entryEffectsEnabled?: boolean;
      lowGiftEffectsEnabled?: boolean;
    },
  ) {
    return this.roomsService.setDisplaySettings(id, userId, body || {});
  }

  @Post(':id/chat/clear')
  @ApiOperation({
    summary: 'Wipe public room chat on every client + stamp DB chatClearedAt',
  })
  clearPublicChat(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
  ) {
    return this.roomsService.clearPublicChat(id, userId);
  }

  @Post(':id/chat/auto-clear')
  @ApiOperation({
    summary: 'Set auto wipe interval for public room chat (0/1/5/10 minutes)',
  })
  setChatAutoClear(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
    @Body() body: { minutes?: number },
  ) {
    return this.roomsService.setChatAutoClearMinutes(
      id,
      userId,
      body?.minutes ?? 0,
    );
  }

  @Post(':id/lock')
  @ApiOperation({ summary: 'Lock or unlock room with password' })
  lock(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
    @Body() body: { locked?: boolean; password?: string },
  ) {
    return this.roomsService.setPassword(id, userId, !!body?.locked, body?.password);
  }

  @Post(':id/mic')
  @ApiOperation({ summary: 'Mute or unmute seat mic (self or target)' })
  setMic(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
    @Body() body: SetMicDto,
  ) {
    return this.roomsService.setMic(id, userId, !!body?.muted, body?.userId);
  }

  @Post(':id/seats/lock')
  @ApiOperation({ summary: 'Lock or unlock a seat (host/mod)' })
  lockSeat(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
    @Body() body: { seatIndex: number; locked?: boolean },
  ) {
    return this.roomsService.lockSeat(id, userId, body.seatIndex, body.locked !== false);
  }

  @Post(':id/seats/resize')
  @ApiOperation({ summary: 'Change number of seats (host)' })
  resizeSeats(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') userId: string,
    @Body() body: { seatCount: number },
  ) {
    return this.roomsService.resizeSeats(id, userId, body.seatCount);
  }
}
