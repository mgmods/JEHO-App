import {
  Controller,
  Get,
  Patch,
  Post,
  Put,
  Delete,
  Body,
  Param,
  Query,
  ParseUUIDPipe,
} from '@nestjs/common';
import { ApiTags, ApiBearerAuth, ApiOperation } from '@nestjs/swagger';
import { UsersService } from './users.service';
import { IdentityVerificationService } from './identity-verification.service';
import { CurrentUser, Public } from '../../common/decorators';
import { PaginationDto } from '../../common/dto/pagination.dto';
import { UpdateProfileDto, ReportUserDto, SubmitGenderVerificationDto } from './dto/users.dto';
import { SocialRequestType } from '../../database/entities/social-request.entity';

@ApiTags('Users')
@ApiBearerAuth()
@Controller('users')
export class UsersController {
  constructor(
    private readonly usersService: UsersService,
    private readonly identityVerification: IdentityVerificationService,
  ) {}

  @Get('me')
  @ApiOperation({ summary: 'Get current user profile' })
  getMe(@CurrentUser('sub') userId: string) {
    return this.usersService.getMe(userId);
  }

  @Get('me/gender-verification')
  @ApiOperation({ summary: 'Female identity verification status' })
  getGenderVerification(@CurrentUser('sub') userId: string) {
    return this.identityVerification.getStatus(userId);
  }

  @Post('me/gender-verification')
  @ApiOperation({ summary: 'Submit selfie for female identity verification' })
  submitGenderVerification(
    @CurrentUser('sub') userId: string,
    @Body() dto: SubmitGenderVerificationDto,
  ) {
    return this.identityVerification.submit(userId, dto);
  }

  @Patch('me')
  @Put('me')
  @ApiOperation({ summary: 'Update current user profile' })
  updateMe(@CurrentUser('sub') userId: string, @Body() dto: UpdateProfileDto) {
    return this.usersService.updateProfile(userId, dto);
  }

  @Get('me/level')
  getLevel(@CurrentUser('sub') userId: string) {
    return this.usersService.getLevelInfo(userId);
  }

  @Get('me/blocks')
  listBlocks(@CurrentUser('sub') userId: string, @Query() query: PaginationDto) {
    return this.usersService.listBlocks(userId, query);
  }

  @Get('me/blocked')
  listBlockedAlias(@CurrentUser('sub') userId: string, @Query() query: PaginationDto) {
    return this.usersService.listBlocks(userId, query);
  }

  @Get('me/friends')
  myFriends(@CurrentUser('sub') userId: string, @Query() query: PaginationDto) {
    return this.usersService.listFriends(userId, query);
  }

  @Get('me/visitors')
  myVisitors(@CurrentUser('sub') userId: string, @Query() query: PaginationDto) {
    return this.usersService.listVisitors(userId, query);
  }

  @Get('me/relations')
  @ApiOperation({ summary: 'Accepted relations / guardians for current user' })
  myRelations(
    @CurrentUser('sub') userId: string,
    @Query('type') type?: SocialRequestType,
  ) {
    return this.usersService.listAcceptedRelations(userId, type);
  }

  @Get('me/cp')
  @ApiOperation({ summary: 'My CP partner, intimacy score and level' })
  myCp(@CurrentUser('sub') userId: string) {
    return this.usersService.myCpStatus(userId);
  }

  @Get('me/requests')
  myRequests(
    @CurrentUser('sub') userId: string,
    @Query('type') type?: SocialRequestType,
  ) {
    return this.usersService.listIncomingRequests(userId, type);
  }

  @Post('me/requests/:id/accept')
  acceptRequest(@CurrentUser('sub') userId: string, @Param('id', ParseUUIDPipe) id: string) {
    return this.usersService.respondSocialRequest(userId, id, true);
  }

  @Post('me/requests/:id/reject')
  rejectRequest(@CurrentUser('sub') userId: string, @Param('id', ParseUUIDPipe) id: string) {
    return this.usersService.respondSocialRequest(userId, id, false);
  }

  @Public()
  @Get('search')
  search(@Query('q') q: string, @Query() query: PaginationDto) {
    return this.usersService.search(q || '', query);
  }

  @Public()
  @Get('username/:username')
  getByUsername(@Param('username') username: string) {
    return this.usersService.getByUsername(username);
  }

  @Public()
  @Get(':id')
  getById(
    @CurrentUser('sub') viewerId: string | undefined,
    @Param('id', ParseUUIDPipe) id: string,
  ) {
    return this.usersService.getById(id, viewerId);
  }

  @Post(':id/visit')
  visit(@CurrentUser('sub') userId: string, @Param('id', ParseUUIDPipe) id: string) {
    return this.usersService.recordVisit(userId, id);
  }

  @Post(':id/request')
  request(
    @CurrentUser('sub') userId: string,
    @Param('id', ParseUUIDPipe) id: string,
    @Body('type') type: SocialRequestType = SocialRequestType.FRIEND,
  ) {
    return this.usersService.createSocialRequest(userId, id, type || SocialRequestType.FRIEND);
  }

  @Post(':id/follow')
  follow(@CurrentUser('sub') userId: string, @Param('id', ParseUUIDPipe) id: string) {
    return this.usersService.follow(userId, id);
  }

  @Delete(':id/follow')
  unfollow(@CurrentUser('sub') userId: string, @Param('id', ParseUUIDPipe) id: string) {
    return this.usersService.unfollow(userId, id);
  }

  @Delete(':id/friend')
  unfriend(@CurrentUser('sub') userId: string, @Param('id', ParseUUIDPipe) id: string) {
    return this.usersService.unfriend(userId, id);
  }

  @Delete(':id/bond')
  @ApiOperation({ summary: 'End accepted relation/guardian/friend bond with peer' })
  endBond(
    @CurrentUser('sub') userId: string,
    @Param('id', ParseUUIDPipe) id: string,
    @Query('type') type?: SocialRequestType,
  ) {
    return this.usersService.endBond(userId, id, type);
  }

  @Post(':id/unfollow')
  unfollowPost(@CurrentUser('sub') userId: string, @Param('id', ParseUUIDPipe) id: string) {
    return this.usersService.unfollow(userId, id);
  }

  @Get(':id/followers')
  followers(@Param('id', ParseUUIDPipe) id: string, @Query() query: PaginationDto) {
    return this.usersService.listFollowers(id, query);
  }

  @Get(':id/following')
  following(@Param('id', ParseUUIDPipe) id: string, @Query() query: PaginationDto) {
    return this.usersService.listFollowing(id, query);
  }

  @Get(':id/friends')
  friends(@Param('id', ParseUUIDPipe) id: string, @Query() query: PaginationDto) {
    return this.usersService.listFriends(id, query);
  }

  @Post(':id/block')
  block(
    @CurrentUser('sub') userId: string,
    @Param('id', ParseUUIDPipe) id: string,
    @Body('reason') reason?: string,
  ) {
    return this.usersService.block(userId, id, reason);
  }

  @Delete(':id/block')
  unblock(@CurrentUser('sub') userId: string, @Param('id', ParseUUIDPipe) id: string) {
    return this.usersService.unblock(userId, id);
  }

  @Post('report')
  report(@CurrentUser('sub') userId: string, @Body() dto: ReportUserDto) {
    return this.usersService.report(userId, dto);
  }
}
