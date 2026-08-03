import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { IsBoolean, IsOptional, IsString, MaxLength } from 'class-validator';

export class ImportZegoFromUrlDto {
  @ApiProperty({
    description:
      'Full config URL or API domain (e.g. https://api.example.com or https://api.example.com/api/v1/config/zego)',
  })
  @IsString()
  @MaxLength(1024)
  url!: string;

  @ApiPropertyOptional({
    description: 'Optional Bearer token when the remote endpoint requires admin auth',
  })
  @IsOptional()
  @IsString()
  @MaxLength(4096)
  bearerToken?: string;

  @ApiPropertyOptional({
    description: 'When true, save discovered credentials. When false/omitted, probe only.',
  })
  @IsOptional()
  @IsBoolean()
  apply?: boolean;
}
