import {
  Controller,
  Get,
  Post,
  Patch,
  Put,
  Delete,
  Body,
  Param,
  Query,
  ParseUUIDPipe,
  UseGuards,
  BadRequestException,
} from '@nestjs/common';
import { ApiTags, ApiBearerAuth, ApiOperation, ApiProperty } from '@nestjs/swagger';
import { IsEmail, IsEnum, IsOptional, IsString, MinLength } from 'class-validator';
import {
  AdminService,
  AdminAdjustWalletDto,
  UpsertGiftDto,
  UpdateUserStatusDto,
  ReviewWithdrawDto,
} from './admin.service';
import { JwtAuthGuard } from '../../common/guards/jwt-auth.guard';
import { AdminGuard } from '../../common/guards/admin.guard';
import { CurrentUser, Public } from '../../common/decorators';
import { PaginationDto } from '../../common/dto/pagination.dto';
import { ReportStatus } from '../../database/entities/report.entity';
import { UserStatus } from '../../database/entities/user.entity';
import { WithdrawStatus } from '../../database/entities/withdraw-request.entity';
import { ContestsService } from '../contests/contests.service';
import { PlazaEventsService } from '../plaza-events/plaza-events.service';
import { GameStoreService } from '../games/game-store.service';
import { CosmeticsService } from '../cosmetics/cosmetics.service';
import { TasksService } from '../tasks/tasks.service';
import { RankingService } from '../ranking/ranking.service';
import {
  RankingPeriod,
  RankingCategory,
} from '../../database/entities/ranking-snapshot.entity';
import { CosmeticType } from '../../database/entities/cosmetic.entity';
import {
  AdminAssignRechargeAgentDto,
  AdminPatchRechargeAgentDto,
  RechargeAgentsService,
} from '../recharge-agents/recharge-agents.service';
import { PaymentSettingsService } from '../payments/payment-settings.service';
import { PaymentsService } from '../payments/payments.service';
import { PatchBinancePaySettingsDto } from '../payments/dto/patch-binance-pay-settings.dto';
import { ZegoSettingsService } from '../zego/zego-settings.service';
import { PatchZegoSettingsDto } from '../zego/dto/patch-zego-settings.dto';
import { LiveKitSettingsService } from '../livekit/livekit-settings.service';
import { PatchLiveKitSettingsDto } from '../livekit/dto/patch-livekit-settings.dto';
import { ImportZegoFromUrlDto } from '../zego/dto/import-zego-from-url.dto';
import { DramaService } from '../drama/drama.service';
import { GameAdsService } from '../games/game-ads.service';
import { IdentityVerificationService } from '../users/identity-verification.service';
import { GenderVerificationStatus } from '../../database/entities/female-identity-verification.entity';
import { SecurityShieldService } from '../../common/security/security-shield.service';
import { AbuseSeverity } from '../../database/entities/abuse-log.entity';

class ResolveReportDto {
  @IsEnum(ReportStatus)
  status: ReportStatus;

  @IsOptional()
  @IsString()
  adminNote?: string;
}

class AdminLoginDto {
  @ApiProperty()
  @IsEmail()
  email!: string;

  @ApiProperty()
  @IsString()
  @MinLength(6)
  password!: string;
}

class UpdateAdminCredentialsDto {
  @ApiProperty({ description: 'Current password confirmation' })
  @IsString()
  @MinLength(6)
  currentPassword!: string;

  @ApiProperty({ required: false, description: 'New login email' })
  @IsOptional()
  @IsEmail()
  newEmail?: string;

  @ApiProperty({ required: false, description: 'New password (min 8)' })
  @IsOptional()
  @IsString()
  @MinLength(8)
  newPassword?: string;
}

@ApiTags('Admin')
@Controller('admin')
export class AdminController {
  constructor(
    private readonly adminService: AdminService,
    private readonly contestsService: ContestsService,
    private readonly plazaEventsService: PlazaEventsService,
    private readonly gameStoreService: GameStoreService,
    private readonly cosmeticsService: CosmeticsService,
    private readonly tasksService: TasksService,
    private readonly rankingService: RankingService,
    private readonly rechargeAgentsService: RechargeAgentsService,
    private readonly paymentSettingsService: PaymentSettingsService,
    private readonly paymentsService: PaymentsService,
    private readonly zegoSettingsService: ZegoSettingsService,
    private readonly liveKitSettingsService: LiveKitSettingsService,
    private readonly dramaService: DramaService,
    private readonly gameAdsService: GameAdsService,
    private readonly identityVerification: IdentityVerificationService,
    private readonly shield: SecurityShieldService,
  ) {}

  // ─── Auth (public) ─────────────────────────────────────────
  @Public()
  @Post('auth/login')
  @ApiOperation({ summary: 'Admin panel login' })
  adminLogin(@Body() dto: AdminLoginDto) {
    return this.adminService.adminLogin(dto.email, dto.password);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('auth/me')
  adminMe(@CurrentUser('sub') userId: string) {
    return this.adminService.adminMe(userId);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Patch('auth/credentials')
  @ApiOperation({ summary: 'Change super-admin email and/or password' })
  updateCredentials(
    @CurrentUser('sub') userId: string,
    @Body() dto: UpdateAdminCredentialsDto,
  ) {
    return this.adminService.updateAdminCredentials(userId, dto);
  }

  // ─── Dashboard ─────────────────────────────────────────────
  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('dashboard')
  dashboard() {
    return this.adminService.dashboard();
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('dashboard/overview')
  overview() {
    return this.adminService.dashboard();
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('dashboard/charts')
  charts() {
    return this.adminService.charts();
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('dashboard/live')
  dashboardLive() {
    return this.adminService.dashboardLive();
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('policy-brochure/snapshot')
  @ApiOperation({
    summary: 'Snapshot for hosts/agencies/supporters policy PDF brochure',
  })
  policyBrochureSnapshot() {
    return this.adminService.policyBrochureSnapshot();
  }

  // ─── Users ─────────────────────────────────────────────────
  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('users')
  listUsers(@Query() query: PaginationDto) {
    return this.adminService.listUsers(query);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('users/maintenance/reconcile-friends')
  reconcileFriends() {
    return this.adminService.reconcileFriendsCounts();
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('users/maintenance/reset-all-baseline')
  @ApiOperation({
    summary: 'Reset ALL non-admin users to baseline (dangerous test wipe)',
  })
  resetAllUsersBaseline(@CurrentUser('sub') adminId: string) {
    return this.adminService.resetAllUsersToBaseline(adminId);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('debug/celebration-toast')
  @ApiOperation({ summary: 'QA: emit global lucky/game celebration toasts' })
  emitCelebrationToast(
    @Body() body: { name?: string; kinds?: Array<'lucky' | 'game'> },
  ) {
    return this.adminService.emitTestCelebrations(body || {});
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('users/:id')
  getUser(@Param('id', ParseUUIDPipe) id: string) {
    return this.adminService.getUser(id);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Patch('users/:id')
  patchUser(@Param('id', ParseUUIDPipe) id: string, @Body() body: Record<string, unknown>) {
    return this.adminService.patchUser(id, body);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Patch('users/:id/status')
  updateUserStatus(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: UpdateUserStatusDto,
  ) {
    return this.adminService.updateUserStatus(id, dto);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('users/:id/ban')
  banUser(@Param('id', ParseUUIDPipe) id: string) {
    return this.adminService.updateUserStatus(id, { status: UserStatus.BANNED });
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('users/:id/unban')
  unbanUser(@Param('id', ParseUUIDPipe) id: string) {
    return this.adminService.updateUserStatus(id, { status: UserStatus.ACTIVE });
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('security/ban-ip')
  @ApiOperation({ summary: 'Temporarily ban an IP (Redis shield)' })
  async banIp(
    @Body() body: { ip: string; hours?: number; reason?: string },
  ) {
    const ip = String(body?.ip || '').trim();
    if (!ip) throw new BadRequestException('ip required');
    const hours = Math.min(168, Math.max(1, Number(body.hours) || 24));
    await this.shield.banIp(ip, hours * 3600, body.reason || 'admin');
    await this.shield.recordAbuse({
      ip,
      action: 'admin_ban_ip',
      severity: AbuseSeverity.CRITICAL,
      details: body.reason || 'admin ban',
    });
    return { ok: true, ip, hours };
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('security/ban-user')
  @ApiOperation({ summary: 'Suspend user + Redis shield ban' })
  async banUserShield(
    @Body() body: { userId: string; hours?: number; reason?: string },
  ) {
    const userId = String(body?.userId || '').trim();
    if (!userId) throw new BadRequestException('userId required');
    const hours = Math.min(720, Math.max(1, Number(body.hours) || 24));
    await this.shield.banUser(userId, hours * 3600, body.reason || 'admin');
    await this.adminService.updateUserStatus(userId, { status: UserStatus.BANNED });
    return { ok: true, userId, hours };
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('users/:id/reset-baseline')
  @ApiOperation({
    summary: 'Reset user to baseline (wallet, VIP, cosmetics, level) — for test wipe',
  })
  resetUserBaseline(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') adminId: string,
  ) {
    return this.adminService.resetUserToBaseline(id, adminId);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Delete('users/:id')
  @ApiOperation({ summary: 'Soft-delete a user account' })
  deleteUser(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') adminId: string,
  ) {
    return this.adminService.deleteUser(id, adminId);
  }

  // ─── Rooms ─────────────────────────────────────────────────
  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('rooms')
  listRooms(@Query() query: PaginationDto) {
    return this.adminService.listRooms(query);
  }

  // ─── Streams (live voice rooms for dashboard) ───────────────
  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('streams')
  listStreams(@Query() query: PaginationDto) {
    return this.adminService.listStreams(query);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('streams/:id')
  getStream(@Param('id', ParseUUIDPipe) id: string) {
    return this.adminService.getStream(id);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('streams/:id/end')
  endStream(@Param('id', ParseUUIDPipe) id: string) {
    return this.adminService.forceEndStream(id, 'admin_end');
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('streams/:id/force-end')
  forceEndStream(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() body?: { reason?: string },
  ) {
    return this.adminService.forceEndStream(id, body?.reason || 'admin_force_end');
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('rooms/:id/close')
  closeRoom(@Param('id', ParseUUIDPipe) id: string) {
    return this.adminService.closeRoom(id);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Delete('rooms/:id')
  deleteRoom(
    @Param('id', ParseUUIDPipe) id: string,
    @Query('force') force?: string,
  ) {
    return this.adminService.deleteRoom(id, {
      allowAgency: force === '1' || force === 'true',
    });
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Patch('rooms/:id')
  updateRoom(
    @Param('id', ParseUUIDPipe) id: string,
    @Body()
    body: {
      accessMode?: string;
      entryFeeCoins?: number;
      title?: string;
      roomKind?: string;
      isSupport?: boolean;
    },
  ) {
    return this.adminService.updateRoom(id, body);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('rooms/:id/customer-service')
  @ApiOperation({ summary: 'Elevate or revoke a room as official customer-service' })
  setRoomCustomerService(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() body: { enabled?: boolean },
  ) {
    return this.adminService.setRoomCustomerService(id, body?.enabled !== false);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Delete('rooms/:id/customer-service')
  @ApiOperation({ summary: 'Revoke customer-service elevation' })
  clearRoomCustomerService(@Param('id', ParseUUIDPipe) id: string) {
    return this.adminService.setRoomCustomerService(id, false);
  }

  // ─── Gifts ─────────────────────────────────────────────────
  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('gifts')
  listGifts() {
    return this.adminService.listGifts();
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('gifts/activate-all')
  activateAllGifts() {
    return this.adminService.activateAllGifts();
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('gifts/rebuild-still-catalog')
  @ApiOperation({
    summary: 'Merge still-image gifts only (never deletes video/flag gifts)',
  })
  rebuildStillGiftCatalog() {
    return this.adminService.rebuildStillGiftCatalog();
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('gifts/import-jeho-pack')
  @ApiOperation({
    summary: 'Upsert JEHO flag + premium video gifts (keeps existing media types)',
  })
  importJehoGiftPack() {
    return this.adminService.importJehoDesignedGifts();
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('gifts')
  createGift(@Body() dto: UpsertGiftDto) {
    return this.adminService.upsertGift(null, dto);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Put('gifts/:id')
  updateGiftPut(@Param('id', ParseUUIDPipe) id: string, @Body() dto: UpsertGiftDto) {
    return this.adminService.upsertGift(id, dto);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Patch('gifts/:id')
  updateGiftPatch(@Param('id', ParseUUIDPipe) id: string, @Body() dto: UpsertGiftDto) {
    return this.adminService.upsertGift(id, dto);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Delete('gifts/:id')
  deleteGift(@Param('id', ParseUUIDPipe) id: string) {
    return this.adminService.deleteGift(id);
  }

  // ─── Gift categories (sheet tabs) ──────────────────────────
  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('gift-categories')
  listGiftCategories() {
    return this.adminService.listGiftCategories();
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('gift-categories')
  createGiftCategory(@Body() dto: Record<string, unknown>) {
    return this.adminService.upsertGiftCategory(null, dto as any);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Patch('gift-categories/:id')
  updateGiftCategory(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: Record<string, unknown>,
  ) {
    return this.adminService.upsertGiftCategory(id, dto as any);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Delete('gift-categories/:id')
  deleteGiftCategory(@Param('id', ParseUUIDPipe) id: string) {
    return this.adminService.deleteGiftCategory(id);
  }

  // ─── Wallet ────────────────────────────────────────────────
  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('wallet/transactions')
  walletTransactions(@Query() query: PaginationDto) {
    return this.adminService.listTransactions(query);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('wallet/withdraws')
  walletWithdraws(@Query() query: PaginationDto) {
    return this.adminService.listWithdraws(query);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('withdraws')
  listWithdraws(@Query() query: PaginationDto) {
    return this.adminService.listWithdraws(query);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('wallet/withdraws/:id/approve')
  approveWithdraw(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') adminId: string,
  ) {
    return this.adminService.reviewWithdraw(id, adminId, {
      status: WithdrawStatus.PAID,
    });
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('wallet/withdraws/:id/reject')
  rejectWithdraw(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') adminId: string,
    @Body() body: { adminNote?: string },
  ) {
    return this.adminService.reviewWithdraw(id, adminId, {
      status: WithdrawStatus.REJECTED,
      adminNote: body?.adminNote,
    });
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Patch('withdraws/:id')
  reviewWithdraw(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') adminId: string,
    @Body() dto: ReviewWithdrawDto,
  ) {
    return this.adminService.reviewWithdraw(id, adminId, dto);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('wallet/recharges')
  recharges(@Query() query: PaginationDto) {
    return this.adminService.listRecharges(query);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('wallet/recharges/:id/complete')
  @ApiOperation({ summary: 'Manually complete a pending recharge (e.g. Sham Cash after WhatsApp proof)' })
  completeRecharge(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() body?: { providerPaymentId?: string; note?: string },
  ) {
    return this.adminService.completeRechargeOrder(id, body?.providerPaymentId, body?.note);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('wallet/recharges/:id/cancel')
  @ApiOperation({ summary: 'Cancel a pending recharge order' })
  cancelRecharge(@Param('id', ParseUUIDPipe) id: string) {
    return this.adminService.cancelRechargeOrder(id);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('wallet/packages')
  walletPackages() {
    return this.adminService.listRechargePackages();
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Put('wallet/packages')
  saveWalletPackages(@Body() body: { items: Array<Record<string, unknown>> }) {
    return this.adminService.saveRechargePackages(body?.items || []);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('wallet/withdraw-packages')
  withdrawPackages() {
    return this.adminService.listWithdrawPackages();
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Put('wallet/withdraw-packages')
  saveWithdrawPackages(@Body() body: { items: Array<Record<string, unknown>> }) {
    return this.adminService.saveWithdrawPackages(body?.items || []);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('wallet/adjust')
  adjustWalletBody(@Body() body: AdminAdjustWalletDto & { userId: string }) {
    return this.adminService.adjustWallet(body.userId, body);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('wallet/:userId/adjust')
  adjustWallet(
    @Param('userId', ParseUUIDPipe) userId: string,
    @Body() dto: AdminAdjustWalletDto,
  ) {
    return this.adminService.adjustWallet(userId, dto);
  }

  // ─── Recharge agents ───────────────────────────────────────
  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('gender-verifications')
  genderVerifications(@Query('status') status?: GenderVerificationStatus) {
    return this.identityVerification.adminList(status);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('gender-verifications/:id/approve')
  approveGenderVerification(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') adminId: string,
    @Body() body: { note?: string },
  ) {
    return this.identityVerification.adminReview(id, 'approve', adminId, body?.note);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('gender-verifications/:id/reject')
  rejectGenderVerification(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') adminId: string,
    @Body() body: { note?: string },
  ) {
    return this.identityVerification.adminReview(id, 'reject', adminId, body?.note);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('recharge-agents/applications')
  rechargeAgentApplications() {
    return this.rechargeAgentsService.adminListApplications();
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('recharge-agents/applications/:id/approve')
  approveRechargeAgentApplication(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') adminId: string,
    @Body() body: { note?: string; floatCoins?: number; commissionBps?: number },
  ) {
    return this.rechargeAgentsService.adminReviewApplication(
      id,
      'approve',
      adminId,
      body?.note,
      {
        floatCoins:
          body?.floatCoins !== undefined ? Number(body.floatCoins) : undefined,
        commissionBps:
          body?.commissionBps !== undefined ? Number(body.commissionBps) : undefined,
      },
    );
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('recharge-agents/applications/:id/reject')
  rejectRechargeAgentApplication(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') adminId: string,
    @Body() body: { note?: string },
  ) {
    return this.rechargeAgentsService.adminReviewApplication(
      id,
      'reject',
      adminId,
      body?.note,
    );
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('recharge-agents')
  rechargeAgents() {
    return this.rechargeAgentsService.adminListAgents();
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('recharge-agents/assign')
  assignRechargeAgent(
    @CurrentUser('sub') adminId: string,
    @Body() body: AdminAssignRechargeAgentDto,
  ) {
    return this.rechargeAgentsService.adminAssign(adminId, body);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Patch('recharge-agents/:id')
  patchRechargeAgent(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') adminId: string,
    @Body() body: AdminPatchRechargeAgentDto,
  ) {
    return this.rechargeAgentsService.adminPatchAgent(id, body, adminId);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('recharge-agents/:id/float')
  adjustRechargeAgentFloat(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') adminId: string,
    @Body() body: { amount: number; note?: string; usdPaid?: number },
  ) {
    return this.rechargeAgentsService.adminAdjustFloat(
      id,
      Number(body?.amount),
      adminId,
      body?.note,
      body?.usdPaid !== undefined ? Number(body.usdPaid) : undefined,
    );
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('recharge-agent-pricing')
  rechargeAgentPricing() {
    return this.rechargeAgentsService.adminGetPricing();
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Patch('recharge-agent-pricing')
  updateRechargeAgentPricing(
    @Body()
    body: {
      membershipFeeUsdt?: number;
      wholesalePer100CoinsUsdt?: number;
      suggestedRetailPer100CoinsUsdt?: number;
      minInitialCoins?: number;
      maxInitialCoins?: number;
    },
  ) {
    return this.rechargeAgentsService.adminUpdatePricing(body);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Delete('recharge-agents/:id')
  deleteRechargeAgent(@Param('id', ParseUUIDPipe) id: string) {
    return this.rechargeAgentsService.adminDeleteAgent(id);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('recharge-agent-contacts')
  listRechargeAgentContacts() {
    return this.rechargeAgentsService.adminListContacts();
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('recharge-agent-contacts')
  upsertRechargeAgentContact(@Body() body: Record<string, unknown>) {
    return this.rechargeAgentsService.adminUpsertContact(body as any);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Delete('recharge-agent-contacts/:id')
  deleteRechargeAgentContact(@Param('id', ParseUUIDPipe) id: string) {
    return this.rechargeAgentsService.adminDeleteContact(id);
  }

  // ─── VIP / Agencies / Reports ──────────────────────────────
  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('vip')
  listVip(@Query() query: PaginationDto) {
    return this.adminService.listVipMembers(query);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('vip/plans')
  vipPlans() {
    return this.adminService.listVipPlans();
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('vip/plans')
  createVipPlan(@Body() body: Record<string, unknown>) {
    return this.adminService.upsertVipPlan(null, body);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Patch('vip/plans/:id')
  updateVipPlan(@Param('id', ParseUUIDPipe) id: string, @Body() body: Record<string, unknown>) {
    return this.adminService.upsertVipPlan(id, body);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Delete('vip/plans/:id')
  deleteVipPlan(@Param('id', ParseUUIDPipe) id: string) {
    return this.adminService.deleteVipPlan(id);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('vip/assign')
  assignVip(@Body() body: { userId: string; planId: string }) {
    return this.adminService.assignVip(body);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('vip/:id/revoke')
  revokeVip(@Param('id', ParseUUIDPipe) id: string) {
    return this.adminService.revokeVip(id);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('agencies/applications')
  agencyApplications(@Query() query: PaginationDto) {
    return this.adminService.listAgencyApplications(query);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('agencies/applications/:id')
  agencyApplication(@Param('id', ParseUUIDPipe) id: string) {
    return this.adminService.getAgencyApplication(id);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('agencies/applications/:id/approve')
  approveAgencyApplication(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') adminId: string,
    @Body() body: { reviewNote?: string },
  ) {
    return this.adminService.reviewAgencyApplication(
      id,
      adminId,
      'approve',
      body?.reviewNote,
    );
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('agencies/applications/:id/reject')
  rejectAgencyApplication(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') adminId: string,
    @Body() body: { reviewNote?: string },
  ) {
    return this.adminService.reviewAgencyApplication(
      id,
      adminId,
      'reject',
      body?.reviewNote,
    );
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('agencies/applications/:id/request-changes')
  requestAgencyApplicationChanges(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') adminId: string,
    @Body() body: { reviewNote?: string },
  ) {
    return this.adminService.reviewAgencyApplication(
      id,
      adminId,
      'request_changes',
      body?.reviewNote,
    );
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('agencies')
  agencies(@Query() query: PaginationDto) {
    return this.adminService.listAgencies(query);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('agencies')
  createAgency(@Body() body: Record<string, unknown>, @CurrentUser('sub') adminId: string) {
    return this.adminService.createAgency({
      ...(body as any),
      ownerId: (body as any).ownerId || adminId,
    });
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('agencies/:id')
  getAgency(@Param('id', ParseUUIDPipe) id: string) {
    return this.adminService.getAgency(id);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Delete('agencies/:id/members/:userId')
  removeAgencyMember(
    @Param('id', ParseUUIDPipe) id: string,
    @Param('userId', ParseUUIDPipe) userId: string,
  ) {
    return this.adminService.removeAgencyMember(id, userId);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Patch('agencies/:id')
  updateAgency(@Param('id', ParseUUIDPipe) id: string, @Body() body: Record<string, unknown>) {
    return this.adminService.updateAgency(id, body as any);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('agencies/:id/grant-exclusives')
  @ApiOperation({
    summary: 'Grant exclusive agency frames/room cards (admin-only, not mall)',
  })
  grantAgencyExclusives(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() body: Record<string, unknown>,
  ) {
    return this.adminService.grantAgencyExclusives(id, body as any);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('agencies/:id/activation-code')
  regenerateAgencyActivationCode(@Param('id', ParseUUIDPipe) id: string) {
    return this.adminService.regenerateAgencyActivationCode(id);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('agencies/:id/approve')
  approveAgency(@Param('id', ParseUUIDPipe) id: string) {
    return this.adminService.approveAgency(id);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('agencies/:id/suspend')
  suspendAgency(@Param('id', ParseUUIDPipe) id: string, @Body() body?: { reason?: string }) {
    return this.adminService.suspendAgency(id, body?.reason);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Delete('agencies/:id')
  deleteAgency(@Param('id', ParseUUIDPipe) id: string) {
    return this.adminService.deleteAgency(id);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('reports')
  listReports(@Query() query: PaginationDto) {
    return this.adminService.listReports(query);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('reports/:id')
  getReport(@Param('id', ParseUUIDPipe) id: string) {
    return this.adminService.getReport(id);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('reports/:id/resolve')
  resolveReportPost(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') adminId: string,
    @Body() body: { adminNote?: string },
  ) {
    return this.adminService.resolveReport(
      id,
      ReportStatus.RESOLVED,
      body?.adminNote,
      adminId,
    );
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('reports/:id/dismiss')
  dismissReport(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') adminId: string,
    @Body() body: { adminNote?: string },
  ) {
    return this.adminService.resolveReport(
      id,
      ReportStatus.REJECTED,
      body?.adminNote,
      adminId,
    );
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Patch('reports/:id')
  resolveReport(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser('sub') adminId: string,
    @Body() dto: ResolveReportDto,
  ) {
    return this.adminService.resolveReport(
      id,
      dto.status,
      dto.adminNote,
      adminId,
    );
  }

  // ─── Notifications / Settings / Logs ───────────────────────
  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('notifications')
  listNotifications(@Query() query: PaginationDto) {
    return this.adminService.listNotifications(query);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('notifications/send')
  sendNotification(
    @Body()
    body: {
      title: string;
      body: string;
      userId?: string;
      audience?: 'all' | 'hosts' | 'vip' | 'user';
      channel?: 'push' | 'in_app' | 'both';
      type?: string;
      message?: string;
    },
  ) {
    return this.adminService.sendNotification(body as any);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('settings')
  getSettings() {
    return this.adminService.getSettings();
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Patch('settings')
  patchSettings(@Body() body: Record<string, string>) {
    return this.adminService.patchSettings(body);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Put('settings/:key')
  setSetting(
    @Param('key') key: string,
    @Body('value') value: string,
    @Body('description') description?: string,
  ) {
    return this.adminService.setSetting(key, value, description);
  }

  // ─── Payment settings ──────────────────────────────────────
  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('payment-settings/binance-pay')
  @ApiOperation({ summary: 'Get masked Binance Exchange wallet settings' })
  getBinancePaySettings() {
    return this.paymentSettingsService.getBinancePayMaskedSettings();
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('payment-settings/binance-pay/reveal')
  @ApiOperation({ summary: 'Reveal Binance Exchange credentials to an authenticated admin' })
  revealBinancePayCredentials() {
    return this.paymentSettingsService.revealBinancePayCredentials();
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Patch('payment-settings/binance-pay')
  @ApiOperation({ summary: 'Update Binance Exchange wallet settings (secrets encrypted at rest)' })
  patchBinancePaySettings(@Body() body: PatchBinancePaySettingsDto) {
    return this.paymentSettingsService.updateBinancePaySettings(body);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('payment-settings/binance-pay/test')
  @ApiOperation({ summary: 'Test Binance Exchange API credentials' })
  testBinancePaySettings() {
    return this.paymentSettingsService.testBinancePayConnection();
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('payment-settings/binance-pay/reconcile')
  @ApiOperation({ summary: 'Reconcile pending Binance wallet deposits against exchange history' })
  reconcileBinanceWalletDeposits() {
    return this.paymentsService.reconcileBinanceWalletDeposits();
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('payment-settings/fourthwall')
  @ApiOperation({ summary: 'Get masked Fourthwall card-payment settings' })
  getFourthwallSettings() {
    return this.paymentsService.getFourthwallAdminSettings();
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('payment-settings/fourthwall/reveal')
  @ApiOperation({ summary: 'Reveal Fourthwall secrets (admin only)' })
  revealFourthwallSettings() {
    return this.paymentsService.revealFourthwallAdminSettings();
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Patch('payment-settings/fourthwall')
  @ApiOperation({ summary: 'Update Fourthwall card-payment settings' })
  patchFourthwallSettings(@Body() body: Record<string, unknown>) {
    return this.paymentsService.updateFourthwallAdminSettings(body as any);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('payment-settings/fourthwall/test')
  @ApiOperation({ summary: 'Test Fourthwall Open API credentials' })
  testFourthwallSettings() {
    return this.paymentsService.testFourthwallConnection();
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('payment-settings/fourthwall/sync-packages')
  @ApiOperation({
    summary: 'Auto-create Fourthwall digital products from wallet coin packages',
  })
  syncFourthwallPackages() {
    return this.paymentsService.syncFourthwallPackages();
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('payment-settings/sham-cash')
  @ApiOperation({ summary: 'Get Sham Cash (Syria) WhatsApp recharge settings' })
  getShamCashSettings() {
    return this.paymentsService.getShamCashAdminSettings();
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Patch('payment-settings/sham-cash')
  @ApiOperation({ summary: 'Update Sham Cash WhatsApp recharge settings' })
  patchShamCashSettings(@Body() body: Record<string, unknown>) {
    return this.paymentsService.updateShamCashAdminSettings(body as any);
  }

  // ─── ZEGOCLOUD settings ────────────────────────────────────
  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('zego-settings')
  @ApiOperation({ summary: 'Get masked ZEGOCLOUD settings' })
  getZegoSettings() {
    return this.zegoSettingsService.getMaskedSettings();
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('zego-settings/reveal')
  @ApiOperation({ summary: 'Reveal ZEGOCLOUD credentials to an authenticated admin' })
  revealZegoCredentials() {
    return this.zegoSettingsService.revealCredentials();
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Patch('zego-settings')
  @ApiOperation({ summary: 'Update ZEGOCLOUD settings (secrets encrypted at rest)' })
  patchZegoSettings(@Body() body: PatchZegoSettingsDto) {
    return this.zegoSettingsService.updateSettings(body);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('zego-settings/import-from-url')
  @ApiOperation({
    summary: 'Probe or import ZEGOCLOUD settings from a remote API URL/domain',
  })
  importZegoFromUrl(@Body() body: ImportZegoFromUrlDto) {
    return this.zegoSettingsService.importFromRemoteUrl(body);
  }

  // ─── LiveKit (free self-hosted voice) + RTC provider switch ───
  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('voice-rtc-settings')
  @ApiOperation({
    summary: 'Masked LiveKit credentials + active voice provider (zego | livekit)',
  })
  getVoiceRtcSettings() {
    return this.liveKitSettingsService.getMaskedSettings();
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('voice-rtc-settings/reveal')
  @ApiOperation({
    summary: 'Reveal LiveKit URL / API key / secret to an authenticated admin',
  })
  revealVoiceRtcCredentials() {
    return this.liveKitSettingsService.revealCredentials();
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Patch('voice-rtc-settings')
  @ApiOperation({
    summary: 'Update LiveKit URL/keys and optionally switch RTC provider',
  })
  patchVoiceRtcSettings(@Body() body: PatchLiveKitSettingsDto) {
    return this.liveKitSettingsService.updateLiveKitSettings(body);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('logs')
  logs(@Query() query: PaginationDto) {
    return this.adminService.listLogs(query);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('system/health')
  @ApiOperation({ summary: 'Server / process health for admin dashboard' })
  systemHealth() {
    return this.adminService.systemHealth();
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('logs/cleanup')
  @ApiOperation({ summary: 'Delete old or all abuse/system logs' })
  cleanupLogs(
    @Body()
    body: {
      olderThanDays?: number;
      unresolvedOnly?: boolean;
      resolvedOnly?: boolean;
    },
  ) {
    return this.adminService.cleanupLogs(body || {});
  }

  // ─── Contests ──────────────────────────────────────────────
  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('contests')
  @ApiOperation({ summary: 'List contests (admin)' })
  adminContests() {
    return this.contestsService.adminList();
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('contests/:id')
  adminContest(@Param('id', ParseUUIDPipe) id: string) {
    return this.contestsService.get(id);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('contests')
  createContest(@Body() body: Record<string, unknown>) {
    return this.contestsService.adminCreate(body as any);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Patch('contests/:id')
  updateContest(@Param('id', ParseUUIDPipe) id: string, @Body() body: Record<string, unknown>) {
    return this.contestsService.adminUpdate(id, body as any);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('contests/:id/end')
  endContest(@Param('id', ParseUUIDPipe) id: string) {
    return this.contestsService.adminEnd(id);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('contests/end-all')
  @ApiOperation({ summary: 'End all active/upcoming contests' })
  endAllContests() {
    return this.contestsService.adminEndAll();
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Delete('contests/:id')
  @ApiOperation({ summary: 'Delete a contest and its entries' })
  deleteContest(@Param('id', ParseUUIDPipe) id: string) {
    return this.contestsService.adminDelete(id);
  }

  // ─── Plaza events (ساحة الفعاليات) ─────────────────────────
  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('events')
  @ApiOperation({ summary: 'List plaza events (admin)' })
  listEvents() {
    return this.plazaEventsService.adminList();
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('events')
  @ApiOperation({ summary: 'Create plaza event (admin)' })
  createEvent(@Body() body: Record<string, any>) {
    return this.plazaEventsService.adminCreate(body || {});
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Patch('events/:id')
  updateEvent(@Param('id', ParseUUIDPipe) id: string, @Body() body: Record<string, any>) {
    return this.plazaEventsService.adminUpdate(id, body || {});
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Delete('events/:id')
  deleteEvent(@Param('id', ParseUUIDPipe) id: string) {
    return this.plazaEventsService.adminDelete(id);
  }

  // ─── Game store ────────────────────────────────────────────
  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('games-store/catalog')
  async gameStoreCatalog(@Query('section') section?: string) {
    return { items: await this.gameStoreService.catalog(section) };
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Put('games-store/catalog')
  saveGameStoreCatalog(@Body() body: { items?: unknown[] } | unknown[]) {
    const items = Array.isArray(body) ? body : (body as any)?.items;
    return this.gameStoreService.saveCatalog(items as any);
  }

  // ─── Tasks ─────────────────────────────────────────────────
  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('tasks')
  async adminTasks() {
    const [items, enabled] = await Promise.all([
      this.tasksService.loadTasks(),
      this.tasksService.isEnabled(),
    ]);
    return { items, total: items.length, enabled };
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Put('tasks')
  async saveTasks(@Body() body: { items?: unknown[]; enabled?: boolean } | unknown[]) {
    if (Array.isArray(body)) {
      return this.tasksService.saveTasks(body as any);
    }
    const payload = (body || {}) as { items?: unknown[]; enabled?: boolean };
    const result: Record<string, unknown> = {};
    if (typeof payload.enabled === 'boolean') {
      result.enabled = await this.tasksService.setEnabled(payload.enabled);
    }
    if (Array.isArray(payload.items)) {
      Object.assign(result, await this.tasksService.saveTasks(payload.items as any));
    }
    if (result.enabled === undefined) {
      result.enabled = await this.tasksService.isEnabled();
    }
    return result;
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Put('tasks/enabled')
  async setTasksEnabled(@Body() body: { enabled?: boolean }) {
    const enabled = await this.tasksService.setEnabled(body?.enabled !== false);
    return { enabled };
  }

  // ─── Cosmetics admin ───────────────────────────────────────
  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('cosmetics')
  adminCosmetics(@Query('type') type?: CosmeticType) {
    return this.cosmeticsService.adminList(type);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('cosmetics')
  createCosmetic(@Body() body: Record<string, unknown>) {
    return this.cosmeticsService.adminCreate(body as any);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Patch('cosmetics/:id')
  updateCosmetic(@Param('id', ParseUUIDPipe) id: string, @Body() body: Record<string, unknown>) {
    return this.cosmeticsService.adminUpdate(id, body as any);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Delete('cosmetics/:id')
  deleteCosmetic(@Param('id', ParseUUIDPipe) id: string) {
    return this.cosmeticsService.adminDelete(id);
  }

  // ─── Ranking ───────────────────────────────────────────────
  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('ranking/:period/:category')
  adminRanking(
    @Param('period') period: string,
    @Param('category') category: string,
    @Query('limit') limit?: string,
  ) {
    const periods = Object.values(RankingPeriod) as string[];
    const categories = Object.values(RankingCategory) as string[];
    if (!periods.includes(period) || !categories.includes(category)) {
      return {
        period,
        category,
        items: [],
        error: `استخدم period=[${periods.join(',')}] و category=[${categories.join(',')}]`,
      };
    }
    return this.rankingService.getBoard(
      period as RankingPeriod,
      category as RankingCategory,
      limit ? parseInt(limit, 10) : 50,
    );
  }

  // ─── Game AdMob ────────────────────────────────────────────
  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('games/ads')
  gameAdsSettings() {
    return this.gameAdsService.adminGetSettings();
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Patch('games/ads')
  patchGameAdsSettings(@Body() body: Record<string, unknown>) {
    return this.gameAdsService.adminPatchSettings(body as any);
  }

  // ─── Drama / short series ──────────────────────────────────
  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('drama/settings')
  dramaSettings() {
    return this.dramaService.adminGetSettings();
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Patch('drama/settings')
  patchDramaSettings(@Body() body: Record<string, unknown>) {
    return this.dramaService.adminPatchSettings(body as any);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('drama/series')
  adminDramaSeries() {
    return this.dramaService.adminListSeries();
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Get('drama/series/:id')
  adminDramaSeriesGet(@Param('id') id: string) {
    return this.dramaService.adminGetSeries(id);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('drama/series')
  adminDramaSeriesCreate(@Body() body: Record<string, unknown>) {
    return this.dramaService.adminCreateSeries(body as any);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Patch('drama/series/:id')
  adminDramaSeriesUpdate(
    @Param('id') id: string,
    @Body() body: Record<string, unknown>,
  ) {
    return this.dramaService.adminUpdateSeries(id, body as any);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Delete('drama/series/:id')
  adminDramaSeriesDelete(@Param('id') id: string) {
    return this.dramaService.adminDeleteSeries(id);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('drama/series/:id/episodes')
  adminDramaEpisodeCreate(
    @Param('id') id: string,
    @Body() body: Record<string, unknown>,
  ) {
    return this.dramaService.adminCreateEpisode(id, body as any);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Patch('drama/episodes/:id')
  adminDramaEpisodeUpdate(
    @Param('id') id: string,
    @Body() body: Record<string, unknown>,
  ) {
    return this.dramaService.adminUpdateEpisode(id, body as any);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Delete('drama/episodes/:id')
  adminDramaEpisodeDelete(@Param('id') id: string) {
    return this.dramaService.adminDeleteEpisode(id);
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @ApiOperation({ summary: 'Bulk action on selected dashboard rows (max 200)' })
  @Post('bulk')
  async bulk(
    @CurrentUser() user: { id?: string; sub?: string },
    @Body()
    body: {
      resource?: string;
      action?: string;
      ids?: string[];
      reason?: string;
      note?: string;
      force?: boolean;
    },
  ) {
    const ids = [
      ...new Set(
        (Array.isArray(body?.ids) ? body.ids : [])
          .map((id) => String(id || '').trim())
          .filter(Boolean),
      ),
    ];
    if (!ids.length) throw new BadRequestException('اختر عنصراً واحداً على الأقل');
    if (ids.length > 200) throw new BadRequestException('الحد الأقصى 200 عنصر');

    const resource = String(body?.resource || '')
      .trim()
      .toLowerCase();
    const action = String(body?.action || '')
      .trim()
      .toLowerCase();
    const reason =
      String(body?.reason || body?.note || '').trim() || 'إجراء جماعي من لوحة التحكم';
    const adminId = String(user?.id || user?.sub || '');
    const key = `${resource}:${action}`;
    const ok: string[] = [];
    const failed: Array<{ id: string; error: string }> = [];

    for (const id of ids) {
      try {
        switch (key) {
          case 'rooms:close':
            // End live + close: forceEnd works for personal and agency rooms
            try {
              await this.adminService.forceEndStream(id, reason || 'admin_close');
            } catch {
              await this.adminService.closeRoom(id);
            }
            break;
          case 'rooms:delete':
            await this.adminService.deleteRoom(id, {
              allowAgency: !!body?.force,
            });
            break;
          case 'rooms:force-end':
          case 'streams:force-end':
            await this.adminService.forceEndStream(id, reason);
            break;
          case 'users:ban':
            await this.adminService.updateUserStatus(id, {
              status: UserStatus.BANNED,
            });
            break;
          case 'users:unban':
            await this.adminService.updateUserStatus(id, {
              status: UserStatus.ACTIVE,
            });
            break;
          case 'users:delete':
            await this.adminService.deleteUser(id, adminId);
            break;
          case 'gifts:delete':
            await this.adminService.deleteGift(id);
            break;
          case 'gifts:activate':
            await this.adminService.upsertGift(id, { isActive: true } as any);
            break;
          case 'gifts:deactivate':
            await this.adminService.upsertGift(id, { isActive: false } as any);
            break;
          case 'cosmetics:delete':
            await this.cosmeticsService.adminDelete(id);
            break;
          case 'cosmetics:activate':
            await this.cosmeticsService.adminUpdate(id, { isActive: true } as any);
            break;
          case 'cosmetics:deactivate':
            await this.cosmeticsService.adminUpdate(id, { isActive: false } as any);
            break;
          case 'agencies:approve':
            await this.adminService.approveAgency(id);
            break;
          case 'agencies:suspend':
            await this.adminService.suspendAgency(id, reason);
            break;
          case 'agencies:delete':
            await this.adminService.deleteAgency(id);
            break;
          case 'agency-applications:approve':
            await this.adminService.reviewAgencyApplication(
              id,
              adminId,
              'approve',
            );
            break;
          case 'agency-applications:reject':
            await this.adminService.reviewAgencyApplication(
              id,
              adminId,
              'reject',
              reason,
            );
            break;
          case 'withdraws:approve':
            await this.adminService.reviewWithdraw(id, adminId, {
              status: WithdrawStatus.PAID,
            });
            break;
          case 'withdraws:reject':
            await this.adminService.reviewWithdraw(id, adminId, {
              status: WithdrawStatus.REJECTED,
              adminNote: reason,
            });
            break;
          case 'recharges:complete':
            await this.adminService.completeRechargeOrder(id, undefined, reason);
            break;
          case 'recharges:cancel':
            await this.adminService.cancelRechargeOrder(id);
            break;
          case 'reports:resolve':
            await this.adminService.resolveReport(
              id,
              ReportStatus.RESOLVED,
              reason,
              adminId,
            );
            break;
          case 'reports:dismiss':
            await this.adminService.resolveReport(
              id,
              ReportStatus.REJECTED,
              reason,
              adminId,
            );
            break;
          case 'contests:end':
            await this.contestsService.adminEnd(id);
            break;
          case 'contests:delete':
            await this.contestsService.adminDelete(id);
            break;
          case 'recharge-agents:suspend':
            await this.rechargeAgentsService.adminPatchAgent(
              id,
              { status: 'suspended' } as any,
              adminId,
            );
            break;
          case 'recharge-agents:activate':
            await this.rechargeAgentsService.adminPatchAgent(
              id,
              { status: 'active' } as any,
              adminId,
            );
            break;
          case 'recharge-agents:delete':
            await this.rechargeAgentsService.adminDeleteAgent(id);
            break;
          case 'recharge-agent-applications:approve':
            await this.rechargeAgentsService.adminReviewApplication(
              id,
              'approve',
              adminId,
              reason,
            );
            break;
          case 'recharge-agent-applications:reject':
            await this.rechargeAgentsService.adminReviewApplication(
              id,
              'reject',
              adminId,
              reason,
            );
            break;
          case 'vip:revoke':
            await this.adminService.revokeVip(id);
            break;
          case 'gender-verifications:approve':
            await this.identityVerification.adminReview(
              id,
              'approve',
              adminId,
              reason,
            );
            break;
          case 'gender-verifications:reject':
            await this.identityVerification.adminReview(
              id,
              'reject',
              adminId,
              reason,
            );
            break;
          default:
            throw new BadRequestException(
              `إجراء غير مدعوم: ${resource}/${action}`,
            );
        }
        ok.push(id);
      } catch (err: any) {
        failed.push({
          id,
          error: String(err?.message || err || 'failed'),
        });
      }
    }

    return {
      resource,
      action,
      ok: ok.length,
      failed: failed.length,
      results: { ok, failed },
    };
  }
}