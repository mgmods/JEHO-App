import { Body, Controller, Get, Post, Query } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { IsEnum, IsOptional, IsUUID } from 'class-validator';
import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { CosmeticsService } from './cosmetics.service';
import { CurrentUser, Public } from '../../common/decorators';
import { CosmeticType } from '../../database/entities/cosmetic.entity';

class PurchaseCosmeticDto {
  @ApiProperty()
  @IsUUID()
  cosmeticId: string;
}

class EquipCosmeticDto {
  @ApiProperty()
  @IsUUID()
  cosmeticId: string;
}

class UnequipCosmeticDto {
  @ApiProperty()
  @IsUUID()
  cosmeticId: string;
}

class CatalogQueryDto {
  @ApiPropertyOptional({ enum: CosmeticType })
  @IsOptional()
  @IsEnum(CosmeticType)
  type?: CosmeticType;
}

@ApiTags('Cosmetics')
@ApiBearerAuth()
@Controller('cosmetics')
export class CosmeticsController {
  constructor(private readonly cosmeticsService: CosmeticsService) {}

  @Public()
  @Get()
  @ApiOperation({ summary: 'Host signals, entry effects, badges and room cosmetics catalog' })
  catalog(@Query() query: CatalogQueryDto) {
    return this.cosmeticsService.catalog(query.type);
  }

  @Get('inventory')
  inventory(@CurrentUser('sub') userId: string) {
    return this.cosmeticsService.inventory(userId);
  }

  @Post('purchase')
  purchase(@CurrentUser('sub') userId: string, @Body() dto: PurchaseCosmeticDto) {
    return this.cosmeticsService.purchase(userId, dto.cosmeticId);
  }

  @Post('equip')
  equip(@CurrentUser('sub') userId: string, @Body() dto: EquipCosmeticDto) {
    return this.cosmeticsService.equip(userId, dto.cosmeticId);
  }

  @Post('unequip')
  @ApiOperation({ summary: 'Remove equipped cosmetic of this type (clear wear)' })
  unequip(@CurrentUser('sub') userId: string, @Body() dto: UnequipCosmeticDto) {
    return this.cosmeticsService.unequip(userId, dto.cosmeticId);
  }
}
