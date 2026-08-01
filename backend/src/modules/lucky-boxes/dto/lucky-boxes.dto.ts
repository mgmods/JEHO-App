import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import {
  IsString,
  IsOptional,
  IsUUID,
  IsInt,
  IsBoolean,
  IsArray,
  ValidateNested,
  Min,
  Max,
  IsIn,
} from 'class-validator';
import { Type } from 'class-transformer';

export class LuckyBoxRewardDto {
  @ApiProperty({ enum: ['coins', 'diamonds', 'points'] })
  @IsIn(['coins', 'diamonds', 'points'])
  type: 'coins' | 'diamonds' | 'points';

  @ApiProperty()
  @IsInt()
  @Min(1)
  @Max(1_000_000_000)
  amount: number;

  @ApiProperty()
  @IsInt()
  @Min(1)
  @Max(1_000_000)
  weight: number;
}

export class OpenBoxDto {
  @ApiPropertyOptional()
  @IsOptional()
  @IsUUID()
  roomId?: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsUUID()
  agencyId?: string;
}

export class FundLuckyPoolDto {
  @ApiProperty({ minimum: 1 })
  @IsInt()
  @Min(1)
  amount: number;
}

export class AdminUpsertBoxDto {
  @ApiPropertyOptional()
  @IsOptional()
  @IsUUID()
  id?: string;

  @ApiProperty()
  @IsString()
  code: string;

  @ApiProperty()
  @IsString()
  title: string;

  @ApiProperty({ enum: ['free_daily', 'paid', 'host_funded', 'agency_funded'] })
  @IsIn(['free_daily', 'paid', 'host_funded', 'agency_funded'])
  kind: 'free_daily' | 'paid' | 'host_funded' | 'agency_funded';

  @ApiPropertyOptional({ default: 0 })
  @IsOptional()
  @IsInt()
  @Min(0)
  @Max(1_000_000_000)
  costCoins?: number;

  @ApiPropertyOptional({ default: 1 })
  @IsOptional()
  @IsInt()
  @Min(1)
  @Max(100)
  dailyLimitPerUser?: number;

  @ApiPropertyOptional({ default: true })
  @IsOptional()
  @IsBoolean()
  isActive?: boolean;

  @ApiProperty({ type: [LuckyBoxRewardDto] })
  @IsArray()
  @ValidateNested({ each: true })
  @Type(() => LuckyBoxRewardDto)
  rewardsJson: LuckyBoxRewardDto[];
}
