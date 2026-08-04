import { IsEnum, IsInt, IsOptional, IsUUID, Max, Min } from 'class-validator';
import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { RoomGameType } from '../../../database/entities/room-game.entity';

export class StartGameDto {
  @ApiProperty({ enum: RoomGameType, default: RoomGameType.TIC_TAC_TOE })
  @IsEnum(RoomGameType)
  type: RoomGameType;

  @ApiPropertyOptional({ description: 'Opponent user id; omit to wait for join' })
  @IsOptional()
  @IsUUID()
  opponentId?: string;
}

export class GameMoveDto {
  @ApiProperty({ minimum: 0, maximum: 8, description: 'Board cell index 0-8' })
  @IsInt()
  @Min(0)
  @Max(8)
  cell: number;
}
