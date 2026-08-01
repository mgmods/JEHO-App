import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { IsInt, IsOptional, IsString, Min } from 'class-validator';

export class StartSlotSessionDto {
  @ApiProperty({ example: 'slot777' })
  @IsString()
  gameId: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  roomId?: string;
}

export class EndSlotSessionDto {
  @ApiPropertyOptional()
  @IsOptional()
  @IsInt()
  @Min(0)
  betCoins?: number;

  @ApiPropertyOptional()
  @IsOptional()
  @IsInt()
  @Min(0)
  winCoins?: number;

  @ApiPropertyOptional()
  @IsOptional()
  @IsInt()
  @Min(0)
  spins?: number;
}

export class RecordSlotWinDto {
  @ApiProperty()
  @IsInt()
  @Min(1)
  winCoins: number;
}
