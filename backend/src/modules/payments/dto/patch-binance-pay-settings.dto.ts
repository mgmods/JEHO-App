import { ApiPropertyOptional } from '@nestjs/swagger';

import { IsBoolean, IsIn, IsOptional, IsString, MaxLength } from 'class-validator';

import { BINANCE_WALLET_ALLOWED_BASE_URLS } from '../binance-wallet.client';



export class PatchBinancePaySettingsDto {

  @ApiPropertyOptional()

  @IsOptional()

  @IsString()

  @MaxLength(256)

  apiKey?: string;



  @ApiPropertyOptional()

  @IsOptional()

  @IsString()

  @MaxLength(512)

  secretKey?: string;



  @ApiPropertyOptional({ enum: [...BINANCE_WALLET_ALLOWED_BASE_URLS] })

  @IsOptional()

  @IsString()

  @IsIn([...BINANCE_WALLET_ALLOWED_BASE_URLS])

  baseUrl?: string;



  @ApiPropertyOptional({ description: 'Optional label for the exchange account' })

  @IsOptional()

  @IsString()

  @MaxLength(128)

  accountName?: string;



  @ApiPropertyOptional()

  @IsOptional()

  @IsBoolean()

  clearApiKey?: boolean;



  @ApiPropertyOptional()

  @IsOptional()

  @IsBoolean()

  clearSecretKey?: boolean;

}


