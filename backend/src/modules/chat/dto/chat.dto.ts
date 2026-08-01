import {
  IsString,
  IsOptional,
  IsEnum,
  IsUUID,
  IsObject,
  MaxLength,
} from 'class-validator';
import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { MessageType } from '../../../database/entities/chat-message.entity';

export class StartConversationDto {
  @ApiProperty()
  @IsUUID()
  peerId: string;
}

export class SendMessageDto {
  @ApiPropertyOptional({ enum: MessageType })
  @IsOptional()
  @IsEnum(MessageType)
  type?: MessageType;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  @MaxLength(4000)
  content?: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsObject()
  media?: {
    url: string;
    mimeType: string;
    size: number;
    width?: number;
    height?: number;
    duration?: number;
  };

  @ApiPropertyOptional()
  @IsOptional()
  @IsUUID()
  replyToId?: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsUUID()
  forwardFromId?: string;
}

export class EditMessageDto {
  @ApiProperty()
  @IsString()
  @MaxLength(4000)
  content: string;
}
