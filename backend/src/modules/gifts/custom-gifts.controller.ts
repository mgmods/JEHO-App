import { Body, Controller, Get, Post, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { IsInt, IsOptional, IsString, Max, Min } from 'class-validator';
import { CurrentUser } from '../../common/decorators';
import { JwtAuthGuard } from '../../common/guards/jwt-auth.guard';
import { AdminGuard } from '../../common/guards/admin.guard';
import { CustomGiftsService } from './custom-gifts.service';

class GrantCustomGiftDto {
  @IsString() recipientPublicId: string;
  @IsString() name: string;
  @IsString() mediaUrl: string;
  @IsOptional() @IsString() mediaType?: string;
  @IsOptional() @IsInt() @Min(0) @Max(3650) durationDays?: number | null;
  @IsInt() @Min(1) coinPrice: number;
  @IsInt() @Min(0) diamondValue: number;
}

class LookupCustomGiftRecipientDto { @IsString() publicId: string; }

@ApiTags('Custom Gifts')
@ApiBearerAuth()
@Controller()
export class CustomGiftsController {
  constructor(private readonly service: CustomGiftsService) {}

  @Get('gifts/custom/mine')
  @UseGuards(JwtAuthGuard)
  @ApiOperation({ summary: 'List only the signed-in user’s active custom gifts' })
  mine(@CurrentUser('sub') userId: string) { return this.service.listMine(userId); }

  @Post('admin/custom-gifts/lookup')
  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiOperation({ summary: 'Resolve a custom-gift recipient by public ID' })
  lookup(@Body() dto: LookupCustomGiftRecipientDto) { return this.service.lookupRecipient(dto.publicId); }

  @Post('admin/custom-gifts/send')
  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiOperation({ summary: 'Grant a privately-owned custom gift to one user' })
  send(@Body() dto: GrantCustomGiftDto, @CurrentUser('sub') actorId: string) {
    return this.service.grant(dto, actorId);
  }

  @Get('admin/custom-gifts')
  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiOperation({ summary: 'List recent custom-gift grants for administration' })
  listAdmin() { return this.service.listAdmin(); }
}
