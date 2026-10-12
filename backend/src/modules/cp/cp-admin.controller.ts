import { Body, Controller, Get, Patch, UseGuards, BadRequestException } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { IsArray, IsBoolean, IsInt, IsObject, IsOptional, IsString, Max, Min } from 'class-validator';
import { DataSource } from 'typeorm';
import { AdminGuard } from '../../common/guards/admin.guard';
import { JwtAuthGuard } from '../../common/guards/jwt-auth.guard';
import { CurrentUser } from '../../common/decorators';
import { CpType } from '../../database/entities/cp-relationship.entity';

class CpTypeConfigDto {
  @IsOptional() @IsInt() @Min(0) @Max(1000000000) priceCoins?: number;
  @IsOptional() @IsString() giftId?: string;
  @IsOptional() @IsInt() @Min(0) @Max(1000000000) giftPoints?: number;
  @IsOptional() @IsInt() @Min(1) @Max(1000) maxLevel?: number;
  @IsOptional() @IsArray() levels?: Array<{ level: number; pointsRequired: number; reward?: string }>;
  @IsOptional() @IsBoolean() enabled?: boolean;
  @IsOptional() @IsObject() rules?: Record<string, unknown>;
}

@ApiTags('Admin CP')
@ApiBearerAuth()
@Controller('admin/cp')
@UseGuards(JwtAuthGuard, AdminGuard)
export class CpAdminController {
  constructor(private readonly dataSource: DataSource) {}

  @Get('config')
  @ApiOperation({ summary: 'Read CP types and admin-configured prices, gifts, levels and rules' })
  async getConfig() {
    const rows = await this.dataSource.query(
      `SELECT key,value FROM app_settings WHERE key LIKE 'cp.%' ORDER BY key`,
    );
    const settings = Object.fromEntries(rows.map((row: { key: string; value: string }) => {
      try { return [row.key, JSON.parse(row.value)]; } catch { return [row.key, row.value]; }
    }));
    return {
      types: Object.values(CpType).map(type => ({
        type,
        priceCoins: settings[`cp.price.${type}`] ?? null,
        giftId: settings[`cp.gift.${type}`] ?? null,
        giftPoints: settings[`cp.gift_points.${type}`] ?? null,
        maxLevel: settings[`cp.max_level.${type}`] ?? null,
        levels: settings[`cp.levels.${type}`] ?? [],
        enabled: settings[`cp.enabled.${type}`] ?? false,
        rules: settings[`cp.rules.${type}`] ?? {},
      })),
      policy: {
        requestExpiryMinutes: settings['cp.policy.request_expiry_minutes'] ?? 30,
        fullRefundOnReject: settings['cp.policy.full_refund_on_reject'] ?? true,
        fullRefundOnExpiry: settings['cp.policy.full_refund_on_expiry'] ?? true,
        allowEitherPartyToEnd: settings['cp.policy.allow_either_party_to_end'] ?? true,
      },
      note: 'Prices and levels are intentionally unset until an administrator configures them.',
    };
  }

  @Patch('config/:type')
  @ApiOperation({ summary: 'Configure one CP type; no default price is created' })
  async updateType(
    @CurrentUser('sub') adminId: string,
    @Body() body: CpTypeConfigDto,
  ) {
    const type = (arguments as any)[0]?.params?.type;
    return { error: 'Use PATCH /admin/cp/config/type with type in the body', suppliedType: type };
  }

  @Patch('config/type')
  async setTypeConfig(
    @CurrentUser('sub') adminId: string,
    @Body() body: CpTypeConfigDto & { type: CpType },
  ) {
    if (!Object.values(CpType).includes(body.type)) throw new BadRequestException('نوع CP غير صالح');
    if (body.priceCoins !== undefined && (!Number.isSafeInteger(body.priceCoins) || body.priceCoins < 0)) {
      throw new BadRequestException('سعر العملات يجب أن يكون عدداً صحيحاً غير سالب؛ الصفر يعطّل الشراء');
    }
    if (body.levels !== undefined) {
      if (!Array.isArray(body.levels) || body.levels.length > 1000) throw new BadRequestException('قائمة المستويات غير صالحة');
      let previous = -1;
      for (const level of body.levels) {
        if (!Number.isInteger(level?.level) || level.level < 1 || !Number.isSafeInteger(level?.pointsRequired) || level.pointsRequired < 0 || level.pointsRequired < previous) {
          throw new BadRequestException('تأكد أن أرقام المستويات والنقاط صحيحة ومرتبة تصاعدياً');
        }
        previous = level.pointsRequired;
      }
    }
    const config: Record<string, unknown> = {};
    if (body.priceCoins !== undefined) config[`cp.price.${body.type}`] = String(body.priceCoins);
    if (body.giftId !== undefined) config[`cp.gift.${body.type}`] = body.giftId;
    if (body.giftPoints !== undefined) config[`cp.gift_points.${body.type}`] = String(body.giftPoints);
    if (body.maxLevel !== undefined) config[`cp.max_level.${body.type}`] = String(body.maxLevel);
    if (body.levels !== undefined) config[`cp.levels.${body.type}`] = JSON.stringify(body.levels);
    if (body.enabled !== undefined) config[`cp.enabled.${body.type}`] = String(body.enabled);
    if (body.rules !== undefined) config[`cp.rules.${body.type}`] = JSON.stringify(body.rules);
    await this.dataSource.transaction(async manager => {
      for (const [key, value] of Object.entries(config)) {
        await manager.query(
          `INSERT INTO app_settings (id,key,value,description,"createdAt","updatedAt")
           VALUES (gen_random_uuid(),$1,$2,$3,now(),now())
           ON CONFLICT (key) DO UPDATE SET value=EXCLUDED.value,description=EXCLUDED.description,"updatedAt"=now()`,
          [key, value, 'CP settings configured through the protected admin API'],
        );
      }
      await manager.query(
        `INSERT INTO cp_audit_logs ("adminUserId",action,"entityType","entityId",details,"createdAt")
         VALUES ($1,'config_update','cp_type',$2,$3::jsonb,now())`,
        [adminId, body.type, JSON.stringify({ keys: Object.keys(config) })],
      );
    });
    return this.getConfig();
  }

  @Get('audit')
  @ApiOperation({ summary: 'Review CP admin changes and lifecycle events' })
  async audit() {
    return this.dataSource.query(
      `SELECT id,"adminUserId",action,"entityType","entityId",details,"createdAt"
       FROM cp_audit_logs ORDER BY "createdAt" DESC LIMIT 200`,
    );
  }

  @Get('requests')
  async requestLog() {
    return this.dataSource.query(
      `SELECT q.id,q.type,q.status,q."fromUserId",a.username AS "fromUsername",
       q."toUserId",b.username AS "toUsername",q."priceCoins",q."roomId",
       q."createdAt",q."expiresAt",q."respondedAt",q."refundedAt"
       FROM cp_requests q JOIN users a ON a.id=q."fromUserId" JOIN users b ON b.id=q."toUserId"
       ORDER BY q."createdAt" DESC LIMIT 200`,
    );
  }
}
