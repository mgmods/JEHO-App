import { IsEmail, IsOptional, IsString, MinLength, MaxLength, Matches } from 'class-validator';
import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';

export class RegisterDto {
  @ApiProperty()
  @IsEmail()
  email: string;

  @ApiProperty({ minLength: 6 })
  @IsString()
  @MinLength(6)
  @MaxLength(72)
  password: string;

  @ApiProperty()
  @IsString()
  @MinLength(3)
  @MaxLength(48)
  @Matches(/^[a-zA-Z0-9_]+$/)
  username: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  @MaxLength(128)
  displayName?: string;
}

export class LoginDto {
  @ApiProperty({ description: 'Email or username' })
  @IsString()
  identifier: string;

  @ApiProperty()
  @IsString()
  @MinLength(6)
  password: string;
}

export class SendOtpDto {
  @ApiProperty({ example: '+15551234567' })
  @IsString()
  phone: string;
}

export class VerifyOtpDto {
  @ApiProperty()
  @IsString()
  phone: string;

  @ApiProperty()
  @IsString()
  @MinLength(4)
  @MaxLength(8)
  code: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  username?: string;
}

export class GuestLoginDto {
  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  deviceId?: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  displayName?: string;
}

export class RefreshTokenDto {
  @ApiProperty()
  @IsString()
  refreshToken: string;
}

export class SocialLoginDto {
  @ApiProperty({ enum: ['google', 'facebook', 'apple'] })
  @IsString()
  provider: 'google' | 'facebook' | 'apple';

  @ApiProperty({ description: 'ID token or access token from social provider' })
  @IsString()
  token: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  email?: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  displayName?: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  avatarUrl?: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  providerUserId?: string;
}

export class ChangePasswordDto {
  @ApiProperty()
  @IsString()
  @MinLength(6)
  currentPassword: string;

  @ApiProperty({ minLength: 6 })
  @IsString()
  @MinLength(6)
  @MaxLength(72)
  newPassword: string;
}
