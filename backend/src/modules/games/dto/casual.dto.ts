import { IsIn, IsObject, IsOptional, IsString, IsUUID } from 'class-validator';
import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';

export class QueueCasualDto {
  @ApiProperty({ enum: ['ono', 'domino', 'ludo'] })
  @IsIn(['ono', 'domino', 'ludo'])
  kind: 'ono' | 'domino' | 'ludo';

  @ApiPropertyOptional({ description: 'Restrict matchmaking to this room' })
  @IsOptional()
  @IsUUID()
  roomId?: string;
}

export class CasualActionDto {
  @ApiProperty({ description: 'action type: state | forfeit' })
  @IsString()
  action: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsObject()
  payload?: Record<string, unknown>;
}
