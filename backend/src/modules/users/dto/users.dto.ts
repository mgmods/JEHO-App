import {
  IsOptional,
  IsString,
  MaxLength,
  IsEnum,
  IsBoolean,
  IsDateString,
  IsArray,
  IsNumber,
} from 'class-validator';
import { Transform } from 'class-transformer';
import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { Gender } from '../../../database/entities/user.entity';
import { ReportTargetType } from '../../../database/entities/report.entity';

const emptyToUndefined = ({ value }: { value: unknown }) =>
  value === '' || value === null ? undefined : value;

export class UpdateProfileDto {
  @ApiPropertyOptional()
  @IsOptional()
  @Transform(emptyToUndefined)
  @IsString()
  @MaxLength(128)
  displayName?: string;

  @ApiPropertyOptional()
  @IsOptional()
  @Transform(emptyToUndefined)
  @IsString()
  @MaxLength(512)
  avatarUrl?: string;

  @ApiPropertyOptional()
  @IsOptional()
  @Transform(emptyToUndefined)
  @IsString()
  bio?: string;

  @ApiPropertyOptional({ enum: Gender })
  @IsOptional()
  @Transform(emptyToUndefined)
  @IsEnum(Gender)
  gender?: Gender;

  @ApiPropertyOptional()
  @IsOptional()
  @Transform(({ value }) => {
    if (value === '' || value == null) return undefined;
    if (typeof value === 'string' && value.length >= 10) return value.slice(0, 10);
    return value;
  })
  @IsDateString()
  birthday?: string;

  @ApiPropertyOptional()
  @IsOptional()
  @Transform(emptyToUndefined)
  @IsString()
  country?: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  city?: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  language?: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsArray()
  interests?: string[];

  @ApiPropertyOptional()
  @IsOptional()
  @IsArray()
  albumUrls?: string[];

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  coverUrl?: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsBoolean()
  showOnlineStatus?: boolean;

  @ApiPropertyOptional()
  @IsOptional()
  @IsBoolean()
  allowDmFromStrangers?: boolean;

  @ApiPropertyOptional()
  @IsOptional()
  @IsBoolean()
  dmGiftGateEnabled?: boolean;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  dmRequiredGiftId?: string | null;
}

export class SubmitGenderVerificationDto {
  @ApiProperty({ description: 'Uploaded selfie URL from POST /uploads' })
  @IsString()
  @MaxLength(512)
  selfieUrl: string;

  @ApiPropertyOptional({ description: 'Liveness check passed (face turns)' })
  @IsOptional()
  @IsBoolean()
  livenessPassed?: boolean;

  @ApiPropertyOptional({ description: 'Liveness quality score 0–1' })
  @IsOptional()
  @IsNumber()
  livenessScore?: number;

  @ApiPropertyOptional({ description: 'Blink/open-eye anti-spoof passed' })
  @IsOptional()
  @IsBoolean()
  blinkPassed?: boolean;

  @ApiPropertyOptional({ description: 'Same face tracking id across yaw steps' })
  @IsOptional()
  @IsBoolean()
  faceTrackingStable?: boolean;

  @ApiPropertyOptional({ description: 'Client female-face confidence 0–1' })
  @IsOptional()
  @IsNumber()
  femaleConfidence?: number;

  @ApiPropertyOptional()
  @IsOptional()
  @IsNumber()
  livenessYawCenter?: number;

  @ApiPropertyOptional()
  @IsOptional()
  @IsNumber()
  livenessYawLeft?: number;

  @ApiPropertyOptional()
  @IsOptional()
  @IsNumber()
  livenessYawRight?: number;
}

export class ReportUserDto {
  @ApiProperty({ enum: ReportTargetType })
  @IsEnum(ReportTargetType)
  targetType: ReportTargetType;

  @ApiProperty()
  @IsString()
  targetId: string;

  @ApiProperty()
  @IsString()
  reason: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  description?: string;
}
