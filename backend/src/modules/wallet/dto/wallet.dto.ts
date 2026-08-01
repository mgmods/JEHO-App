import {
  IsInt,
  IsOptional,
  IsString,
  IsObject,
  IsIn,
  Min,
  MaxLength,
} from 'class-validator';
import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';

export class CreateRechargeDto {
  @ApiProperty({ example: 'coins_70000' })
  @IsString()
  @MaxLength(64)
  sku: string;

  @ApiPropertyOptional({ default: 'USD' })
  @IsOptional()
  @IsString()
  @MaxLength(16)
  currency?: string;

  @ApiProperty({ example: 'stripe' })
  @IsString()
  provider: string;
}

export class ExchangeDto {
  @ApiProperty({ description: 'Diamonds to convert into coins at 60%' })
  @IsInt()
  @Min(1)
  diamonds: number;
}

export class HostTradeDto {
  @ApiProperty({ description: 'Target hostess userId (must be active agency host)' })
  @IsString()
  @MaxLength(64)
  toUserId: string;

  @ApiProperty({ description: 'Diamonds to send from own balance into her trader collection' })
  @IsInt()
  @Min(1)
  diamonds: number;
}

export class WithdrawDto {
  @ApiProperty()
  @IsInt()
  @Min(100)
  diamonds: number;

  @ApiProperty({
    example: 'paypal',
    enum: ['paypal', 'bank', 'usdt', 'agent'],
    description: 'paypal/bank/usdt = self withdraw; agent = via recharge agent',
  })
  @IsString()
  @IsIn(['paypal', 'bank', 'usdt', 'agent'])
  @MaxLength(32)
  method: string;

  @ApiPropertyOptional({
    description: 'Required when method=agent — RechargeAgent.id',
  })
  @IsOptional()
  @IsString()
  @MaxLength(64)
  agentId?: string;

  @ApiProperty()
  @IsObject()
  payoutDetails: Record<string, unknown>;
}
