import { Body, Controller, Get, Param, Post, Query } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { CurrentUser, Public } from '../../common/decorators';
import {
  ApplyRechargeAgentDto,
  RechargeAgentsService,
  SellRechargeDto,
} from './recharge-agents.service';

@ApiTags('Recharge agents')
@ApiBearerAuth()
@Controller('recharge-agents')
export class RechargeAgentsController {
  constructor(private readonly rechargeAgentsService: RechargeAgentsService) {}

  @Post('apply')
  @ApiOperation({ summary: 'Apply to become a recharge agent' })
  apply(@CurrentUser('sub') userId: string, @Body() dto: ApplyRechargeAgentDto) {
    return this.rechargeAgentsService.apply(userId, dto);
  }

  @Get('config')
  @Public()
  @ApiOperation({ summary: 'Recharge agent membership and USDT pricing' })
  config() {
    return this.rechargeAgentsService.publicConfig();
  }

  @Get('quote')
  @ApiOperation({ summary: 'Calculate wholesale and suggested retail USDT totals' })
  quote(@Query('coins') coins: string) {
    return this.rechargeAgentsService.quoteForCoins(Number(coins));
  }

  @Get('payment-details')
  @ApiOperation({ summary: 'Get USDT deposit address for an agent application' })
  paymentDetails(
    @CurrentUser('sub') userId: string,
    @Query('network') network?: string,
  ) {
    return this.rechargeAgentsService.paymentDetails(userId, network);
  }

  @Post('deposit-order')
  @ApiOperation({ summary: 'Create trackable USDT deposit order for agent membership' })
  createDepositOrder(
    @CurrentUser('sub') userId: string,
    @Body() body: { network?: string; requestedCoins?: number },
  ) {
    return this.rechargeAgentsService.createDepositOrder(
      userId,
      body?.network || 'TRX',
      Number(body?.requestedCoins || 0),
    );
  }

  @Post('card-checkout')
  @ApiOperation({
    summary: 'Create Fourthwall card checkout for agent membership (no wallet credit)',
  })
  createCardCheckout(
    @CurrentUser('sub') userId: string,
    @Body() body: { requestedCoins?: number },
  ) {
    return this.rechargeAgentsService.createCardCheckout(
      userId,
      Number(body?.requestedCoins || 0),
    );
  }

  @Get('resolve-user')
  @ApiOperation({ summary: 'Resolve recipient by publicId / username / UUID' })
  resolveUser(@Query('query') query: string) {
    return this.rechargeAgentsService.resolveRecipient(query);
  }

  @Get('user-overview')
  @ApiOperation({
    summary: 'Agent: user wallet + received gifts (to credit their account)',
  })
  userOverview(
    @CurrentUser('sub') userId: string,
    @Query('query') query: string,
  ) {
    return this.rechargeAgentsService.userOverviewForAgent(userId, query);
  }

  @Get('withdraw-agents')
  @ApiOperation({ summary: 'List active agents for agent-channel withdraw' })
  withdrawAgents() {
    return this.rechargeAgentsService.listAgentsForWithdraw();
  }

  @Get('withdraws')
  @ApiOperation({ summary: 'Agent inbox: withdraw requests assigned to me' })
  myWithdraws(@CurrentUser('sub') userId: string) {
    return this.rechargeAgentsService.listMyWithdraws(userId);
  }

  @Post('withdraws/:id/complete')
  @ApiOperation({ summary: 'Agent marks withdraw as paid' })
  completeWithdraw(
    @CurrentUser('sub') userId: string,
    @Param('id') id: string,
    @Body() body: { note?: string },
  ) {
    return this.rechargeAgentsService.completeWithdraw(userId, id, body?.note);
  }

  @Post('withdraws/:id/reject')
  @ApiOperation({ summary: 'Agent rejects withdraw and refunds diamonds' })
  rejectWithdraw(
    @CurrentUser('sub') userId: string,
    @Param('id') id: string,
    @Body() body: { note?: string },
  ) {
    return this.rechargeAgentsService.rejectWithdraw(userId, id, body?.note);
  }

  @Get('me')
  @ApiOperation({ summary: 'Get my recharge agent status' })
  me(@CurrentUser('sub') userId: string) {
    return this.rechargeAgentsService.myAgent(userId);
  }

  @Get('recharges')
  @ApiOperation({ summary: 'List recharges sold by this agent' })
  recharges(@CurrentUser('sub') userId: string) {
    return this.rechargeAgentsService.listMyRecharges(userId);
  }

  @Get('directory')
  @Public()
  @ApiOperation({ summary: 'Public recharge agent contact directory' })
  directory(@Query('country') country?: string) {
    return this.rechargeAgentsService.publicDirectory(country);
  }

  @Post('sell')
  @ApiOperation({ summary: 'Sell coins to a user from agent float' })
  sell(@CurrentUser('sub') userId: string, @Body() dto: SellRechargeDto) {
    return this.rechargeAgentsService.sellToUser(userId, dto);
  }
}
