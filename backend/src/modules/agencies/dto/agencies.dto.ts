import {
  IsString,
  IsOptional,
  IsEnum,
  MaxLength,
  IsNumber,
  IsBoolean,
  IsEmail,
  IsInt,
  IsArray,
  IsUrl,
  Min,
  Max,
  MinLength,
} from 'class-validator';
import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { Transform } from 'class-transformer';
import { AgencyRole } from '../../../database/entities/agency-member.entity';

const trim = ({ value }: { value: unknown }) =>
  typeof value === 'string' ? value.trim() : value;
const optionalTrim = ({ value }: { value: unknown }) => {
  if (typeof value !== 'string') return value;
  const cleaned = value.trim();
  return cleaned || undefined;
};

export class CreateAgencyDto {
  @ApiProperty()
  @IsString()
  @MaxLength(128)
  name: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  description?: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  logoUrl?: string;
}

export class SubmitAgencyApplicationDto {
  @ApiProperty()
  @Transform(trim)
  @IsString()
  @MinLength(3)
  @MaxLength(128)
  proposedName: string;

  @ApiProperty()
  @Transform(trim)
  @IsString()
  @MinLength(20)
  @MaxLength(4000)
  description: string;

  @ApiProperty()
  @Transform(trim)
  @IsString()
  @MinLength(50)
  @MaxLength(10000)
  businessPlan: string;

  @ApiProperty()
  @Transform(trim)
  @IsString()
  @MinLength(2)
  @MaxLength(100)
  country: string;

  @ApiProperty()
  @Transform(trim)
  @IsEmail()
  @MaxLength(254)
  contactEmail: string;

  @ApiProperty()
  @Transform(trim)
  @IsString()
  @MinLength(6)
  @MaxLength(32)
  contactPhone: string;

  @ApiPropertyOptional()
  @IsOptional()
  @Transform(optionalTrim)
  @IsUrl({ require_protocol: true })
  @MaxLength(512)
  socialLink?: string;

  @ApiProperty()
  @Transform(trim)
  @IsString()
  @MinLength(10)
  @MaxLength(4000)
  experience: string;

  @ApiProperty()
  @IsInt()
  @Min(1)
  expectedHostCount: number;

  @ApiProperty({ type: [String] })
  @IsArray()
  @IsUrl({ require_protocol: true }, { each: true })
  documentUrls: string[];

  @ApiProperty({ example: true })
  @IsBoolean()
  termsAccepted: boolean;
}

export class AddMemberDto {
  @ApiProperty({
    description: 'User UUID, publicId (app ID), or username',
    example: '102345',
  })
  @IsString()
  @MaxLength(64)
  userId: string;

  @ApiPropertyOptional({ enum: AgencyRole })
  @IsOptional()
  @IsEnum(AgencyRole)
  role?: AgencyRole;
}

export class UpdateMemberRoleDto {
  @ApiProperty({ enum: AgencyRole })
  @IsEnum(AgencyRole)
  role: AgencyRole;
}

export class UpdateCommissionDto {
  @ApiProperty({ description: 'Agency owner commission percent (0–50)' })
  @IsNumber()
  @Min(0)
  @Max(50)
  commissionPercent: number;
}

export class JoinByCodeDto {
  @ApiProperty({ example: 'A7K2M9QX', description: 'Agency activation code' })
  @Transform(trim)
  @IsString()
  @MinLength(4)
  @MaxLength(16)
  code: string;
}

export class UpdateAgencySettingsDto {
  @ApiPropertyOptional({
    enum: ['welcome', 'elite', 'family', 'spark'],
    description: 'Notification preset sent when a host is approved',
  })
  @IsOptional()
  @IsString()
  @MaxLength(32)
  notificationStyle?: string;

  @ApiPropertyOptional({ description: 'Agency display name (owner only)' })
  @IsOptional()
  @IsString()
  @MaxLength(64)
  name?: string;

  @ApiPropertyOptional({ description: 'Agency welcome / about text' })
  @IsOptional()
  @IsString()
  @MaxLength(500)
  description?: string;

  @ApiPropertyOptional({ description: 'Agency logo / cover URL' })
  @IsOptional()
  @IsString()
  @MaxLength(512)
  logoUrl?: string;
}

export class CreateAgencyPayoutRequestDto {
  @ApiProperty({ description: 'Diamonds to settle as USD cash from agency' })
  @IsInt()
  @Min(100)
  diamonds: number;

  @ApiProperty({ enum: ['paypal', 'bank', 'usdt', 'cash', 'other'] })
  @IsString()
  @MaxLength(32)
  method: string;

  @ApiPropertyOptional({ description: 'Payment account fields (email, IBAN, wallet…)' })
  @IsOptional()
  payoutDetails?: Record<string, unknown>;

  @ApiPropertyOptional({ description: 'Free-text account line if not using structured map' })
  @IsOptional()
  @IsString()
  @MaxLength(500)
  account?: string;
}

export class ReviewAgencyPayoutDto {
  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  @MaxLength(500)
  note?: string;
}
