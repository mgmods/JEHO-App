import { IsInt, IsOptional, IsString, Max, MaxLength, Min } from 'class-validator';
import { Type } from 'class-transformer';

export class DiceRollDto {
  @Type(() => Number)
  @IsInt()
  @Min(1)
  amount: number;

  @Type(() => Number)
  @IsInt()
  @Min(2)
  @Max(12)
  number: number;

  @IsOptional()
  @IsString()
  @MaxLength(64)
  roomId?: string;
}
