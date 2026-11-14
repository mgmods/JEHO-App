import { Body, Controller, Get, Post, Query } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { IsString } from 'class-validator';
import { ApiProperty } from '@nestjs/swagger';
import { CurrentUser } from '../../common/decorators';
import { GameStoreService } from './game-store.service';

class PurchaseGameItemDto {
  @ApiProperty()
  @IsString()
  sku: string;
}

class EquipGameItemDto {
  @ApiProperty()
  @IsString()
  sku: string;
}

@ApiTags('Game Store')
@ApiBearerAuth()
@Controller('games-store')
export class GameStoreController {
  constructor(private readonly store: GameStoreService) {}

  @Get('catalog')
  @ApiOperation({ summary: 'Game store catalog (points)' })
  async catalog(@Query('section') section?: string) {
    return { items: await this.store.catalog(section) };
  }

  @Get('inventory')
  @ApiOperation({ summary: 'Owned game items' })
  inventory(@CurrentUser('sub') userId: string) {
    return this.store.inventory(userId);
  }

  @Get('loadout')
  @ApiOperation({ summary: 'Equipped game cosmetics by section' })
  loadout(@CurrentUser('sub') userId: string) {
    return this.store.loadout(userId);
  }

  @Post('purchase')
  @ApiOperation({ summary: 'Buy game item with game points' })
  purchase(@CurrentUser('sub') userId: string, @Body() dto: PurchaseGameItemDto) {
    return this.store.purchase(userId, dto.sku);
  }

  @Post('equip')
  @ApiOperation({ summary: 'Equip owned game cosmetic' })
  equip(@CurrentUser('sub') userId: string, @Body() dto: EquipGameItemDto) {
    return this.store.equip(userId, dto.sku);
  }
}
