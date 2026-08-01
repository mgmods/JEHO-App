import {

  Controller,

  Post,

  Get,

  Body,

  Param,

  Req,

  Headers,

  ParseUUIDPipe,

  RawBodyRequest,

} from '@nestjs/common';

import { ApiTags, ApiBearerAuth, ApiOperation, ApiExcludeEndpoint } from '@nestjs/swagger';

import { Request } from 'express';

import {

  PaymentsService,

  StripeCheckoutDto,

  PaypalCreateDto,

  PlayBillingVerifyDto,

  CryptoOrderDto,

  BinancePayOrderDto,

  BinanceWalletDepositAddressDto,

  BinanceWalletOrderDto,

} from './payments.service';

import { CurrentUser, Public } from '../../common/decorators';



@ApiTags('Payments')

@ApiBearerAuth()

@Controller('payments')

export class PaymentsController {

  constructor(private readonly paymentsService: PaymentsService) {}



  @Post('stripe/checkout')

  @ApiOperation({ summary: 'Create Stripe Checkout session' })

  stripeCheckout(@CurrentUser('sub') userId: string, @Body() dto: StripeCheckoutDto) {

    return this.paymentsService.createStripeCheckout(userId, dto);

  }



  @Public()

  @Post('stripe/webhook')

  @ApiExcludeEndpoint()

  stripeWebhook(

    @Req() req: RawBodyRequest<Request>,

    @Headers('stripe-signature') signature: string,

  ) {

    const raw = req.rawBody || Buffer.from(JSON.stringify(req.body));

    return this.paymentsService.handleStripeWebhook(raw, signature || '');

  }



  @Post('paypal/create')

  paypalCreate(@CurrentUser('sub') userId: string, @Body() dto: PaypalCreateDto) {

    return this.paymentsService.createPaypalOrder(userId, dto);

  }



  @Post('paypal/:id/capture')

  paypalCapture(

    @Param('id', ParseUUIDPipe) _id: string,

    @Body('captureId') captureId: string,

  ) {

    return this.paymentsService.capturePaypal(_id, captureId || '');

  }



  @Post('google-play/verify')

  @ApiOperation({ summary: 'Verify Google Play Billing purchase' })

  playVerify(@CurrentUser('sub') userId: string, @Body() dto: PlayBillingVerifyDto) {

    return this.paymentsService.verifyPlayBilling(userId, dto);

  }



  @Post('crypto/orders')

  cryptoOrder(@CurrentUser('sub') userId: string, @Body() dto: CryptoOrderDto) {

    return this.paymentsService.createCryptoOrder(userId, dto);

  }



  @Post('crypto/:id/confirm')

  cryptoConfirm(

    @Param('id', ParseUUIDPipe) id: string,

    @Body('txHash') txHash: string,

  ) {

    return this.paymentsService.confirmCrypto(id, txHash || '');

  }



  @Post('binance-wallet/deposit-address')

  @ApiOperation({ summary: 'Get USDT deposit address for Binance Exchange wallet' })

  binanceWalletDepositAddress(

    @CurrentUser('sub') userId: string,

    @Body() dto: BinanceWalletDepositAddressDto,

  ) {

    return this.paymentsService.getBinanceWalletDepositAddress(userId, dto);

  }



  @Post('binance-wallet/orders')

  @ApiOperation({ summary: 'Create Binance Exchange wallet USDT deposit recharge order' })

  binanceWalletOrder(@CurrentUser('sub') userId: string, @Body() dto: BinanceWalletOrderDto) {

    return this.paymentsService.createBinanceWalletOrder(userId, dto);

  }



  @Get('binance-wallet/orders/:id')

  @ApiOperation({ summary: 'Get Binance wallet deposit order status (owner only)' })

  binanceWalletStatus(

    @CurrentUser('sub') userId: string,

    @Param('id', ParseUUIDPipe) id: string,

  ) {

    return this.paymentsService.getBinanceWalletOrderStatus(userId, id);

  }



  @Post('binance-pay/orders')

  @ApiOperation({ summary: 'Create Binance wallet recharge order (legacy path)' })

  binancePayOrder(@CurrentUser('sub') userId: string, @Body() dto: BinancePayOrderDto) {

    return this.paymentsService.createBinancePayOrder(userId, dto);

  }



  @Public()

  @Post('binance-pay/webhook')

  @ApiExcludeEndpoint()

  binancePayWebhook(

    @Req() req: RawBodyRequest<Request>,

    @Headers() headers: Record<string, string | string[] | undefined>,

  ) {

    const raw = req.rawBody || Buffer.from(JSON.stringify(req.body ?? {}));

    return this.paymentsService.handleBinancePayWebhook(raw, headers);

  }



  @Get('binance-pay/orders/:id')

  @ApiOperation({ summary: 'Get Binance wallet order status (legacy path)' })

  binancePayStatus(

    @CurrentUser('sub') userId: string,

    @Param('id', ParseUUIDPipe) id: string,

  ) {

    return this.paymentsService.getBinanceOrderStatus(userId, id);

  }

}


