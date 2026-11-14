import { IsBoolean, IsIn, IsOptional, IsString, MaxLength } from 'class-validator';

export class PatchLiveKitSettingsDto {
  @IsOptional()
  @IsString()
  @MaxLength(512)
  url?: string;

  @IsOptional()
  @IsString()
  @MaxLength(256)
  apiKey?: string;

  @IsOptional()
  @IsString()
  @MaxLength(512)
  apiSecret?: string;

  @IsOptional()
  @IsBoolean()
  clearApiSecret?: boolean;

  /** zego = paid cloud · livekit = free self-hosted open source */
  @IsOptional()
  @IsIn(['zego', 'livekit'])
  provider?: 'zego' | 'livekit';
}
