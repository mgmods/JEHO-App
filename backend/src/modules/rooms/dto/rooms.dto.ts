import {
  IsString,
  IsOptional,
  IsEnum,
  IsInt,
  Min,
  Max,
  MaxLength,
  IsUUID,
  IsIn,
  IsBoolean,
} from 'class-validator';
import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { RoomType } from '../../../database/entities/room.entity';

export class CreateRoomDto {
  @ApiProperty()
  @IsString()
  @MaxLength(128)
  title: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  description?: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  coverUrl?: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  backgroundUrl?: string;

  @ApiPropertyOptional({ enum: RoomType })
  @IsOptional()
  @IsEnum(RoomType)
  type?: RoomType;

  @ApiPropertyOptional({ default: 11, description: 'Includes host seat 0 and seats 1–10' })
  @IsOptional()
  @IsInt()
  @Min(2)
  @Max(30)
  seatCount?: number;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  topic?: string;

  /** When true, always open/create a personal STANDARD room even if the host is an agency host. */
  @ApiPropertyOptional()
  @IsOptional()
  @IsBoolean()
  preferPersonal?: boolean;

}

export class JoinRoomDto {
  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  password?: string;
}

export class TakeSeatDto {
  @ApiProperty()
  @IsInt()
  @Min(0)
  seatIndex: number;
}

export class KickBanDto {
  @ApiProperty()
  @IsUUID('4', { message: 'معرّف المستخدم غير صالح' })
  userId: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  reason?: string;

  @ApiPropertyOptional({
    description: 'Timed ban duration: 10, 30, or 216000 minutes (not used by kick)',
    enum: [10, 30, 216000],
  })
  @IsOptional()
  @IsInt()
  @Min(1)
  durationMinutes?: number;
}

export class SetMicDto {
  @ApiProperty({ description: 'true = mute, false = unmute' })
  @IsBoolean({ message: 'حالة الكتم غير صالحة' })
  muted: boolean;

  @ApiPropertyOptional({ description: 'Target user (host/mod mute). Omit to mute yourself.' })
  @IsOptional()
  @IsUUID('4', { message: 'معرّف المستخدم غير صالح' })
  userId?: string;
}

export class RoomMusicDto {
  @ApiProperty({ enum: ['load', 'play', 'pause', 'seek', 'stop'] })
  @IsIn(['load', 'play', 'pause', 'seek', 'stop'])
  action: 'load' | 'play' | 'pause' | 'seek' | 'stop';

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  @MaxLength(1024)
  url?: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  @MaxLength(160)
  title?: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  @MaxLength(160)
  artist?: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsInt()
  @Min(0)
  @Max(86_400_000)
  positionMs?: number;
}

export class ModeratorPermissionsDto {
  @ApiPropertyOptional()
  @IsOptional()
  @IsBoolean()
  canManageMusic?: boolean;

  @ApiPropertyOptional()
  @IsOptional()
  @IsBoolean()
  canChangeFrames?: boolean;

  @ApiPropertyOptional()
  @IsOptional()
  @IsBoolean()
  canControlGames?: boolean;

  @ApiPropertyOptional()
  @IsOptional()
  @IsBoolean()
  canMute?: boolean;

  @ApiPropertyOptional()
  @IsOptional()
  @IsBoolean()
  canKick?: boolean;

  @ApiPropertyOptional()
  @IsOptional()
  @IsBoolean()
  canBan?: boolean;

  @ApiPropertyOptional()
  @IsOptional()
  @IsBoolean()
  canManageSeats?: boolean;

  @ApiPropertyOptional()
  @IsOptional()
  @IsBoolean()
  canInvite?: boolean;

  @ApiPropertyOptional()
  @IsOptional()
  @IsBoolean()
  canManageRoom?: boolean;
}

export class SeatInviteDto {
  @ApiProperty()
  @IsUUID()
  userId: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsInt()
  @Min(0)
  seatIndex?: number;
}

export class TaskRoomInviteDto {
  @ApiProperty({ description: 'New male user to invite for 40◆ dwell reward' })
  @IsUUID()
  userId: string;
}

export class SeatInviteResponseDto {
  @ApiProperty()
  @IsBoolean()
  accept: boolean;
}

export class RoomUserReportDto {
  @ApiProperty()
  @IsUUID()
  targetUserId: string;

  @ApiProperty({ enum: ['abuse', 'harassment', 'spam', 'impersonation', 'other'] })
  @IsIn(['abuse', 'harassment', 'spam', 'impersonation', 'other'])
  reason: 'abuse' | 'harassment' | 'spam' | 'impersonation' | 'other';

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  @MaxLength(500)
  description?: string;
}

export class SetCohostDto {
  @ApiProperty()
  @IsUUID()
  userId: string;
}

export class SetRoomBackgroundDto {
  @ApiPropertyOptional({ nullable: true })
  @IsOptional()
  @IsString()
  backgroundUrl?: string | null;
}

export class SetRoomFrameDto {
  @ApiPropertyOptional({ nullable: true })
  @IsOptional()
  @IsString()
  roomCardUrl?: string | null;
}

export class UpdateRoomDto {
  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  @MaxLength(128)
  title?: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  description?: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  coverUrl?: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  backgroundUrl?: string;

}
