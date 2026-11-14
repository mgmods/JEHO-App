import { ApiPropertyOptional } from '@nestjs/swagger';
import { IsBoolean, IsOptional, IsString, MaxLength } from 'class-validator';

export class PatchZegoSettingsDto {
  @ApiPropertyOptional({ description: 'ZEGOCLOUD AppID (numeric string)' })
  @IsOptional()
  @IsString()
  @MaxLength(32)
  appId?: string;

  @ApiPropertyOptional({ description: 'ZEGOCLOUD AppSign for Express SDK' })
  @IsOptional()
  @IsString()
  @MaxLength(128)
  appSign?: string;

  @ApiPropertyOptional({ description: '32-char ServerSecret for Token04' })
  @IsOptional()
  @IsString()
  @MaxLength(64)
  serverSecret?: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  @MaxLength(512)
  wsUrl?: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  @MaxLength(512)
  wsUrlBak?: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsBoolean()
  clearAppSign?: boolean;

  @ApiPropertyOptional()
  @IsOptional()
  @IsBoolean()
  clearServerSecret?: boolean;
}
