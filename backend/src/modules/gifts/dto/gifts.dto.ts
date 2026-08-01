import { IsUUID, IsOptional, IsInt, Min, IsArray, ArrayMinSize } from 'class-validator';
import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';

export class SendGiftDto {
  @ApiProperty()
  @IsUUID()
  giftId: string;

  @ApiProperty()
  @IsUUID()
  receiverId: string;

  @ApiPropertyOptional({ default: 1 })
  @IsOptional()
  @IsInt()
  @Min(1)
  quantity?: number;

  @ApiPropertyOptional()
  @IsOptional()
  @IsUUID()
  roomId?: string;

  @ApiPropertyOptional({ description: 'Continue combo from previous send' })
  @IsOptional()
  @IsInt()
  @Min(1)
  comboCount?: number;
}

/** All-mic send: one charge, host 50% / mics 50% of diamond pool (after platform cut). */
export class SendAllMicGiftDto {
  @ApiProperty()
  @IsUUID()
  giftId: string;

  @ApiProperty({ type: [String] })
  @IsArray()
  @ArrayMinSize(1)
  @IsUUID('all', { each: true })
  receiverIds: string[];

  @ApiPropertyOptional({ default: 1 })
  @IsOptional()
  @IsInt()
  @Min(1)
  quantity?: number;

  @ApiProperty()
  @IsUUID()
  roomId: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsInt()
  @Min(1)
  comboCount?: number;
}
