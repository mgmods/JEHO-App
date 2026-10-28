import { Body, Controller, Get, Patch, Post, Param, UseGuards, BadRequestException } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { IsArray, IsBoolean, IsInt, IsObject, IsOptional, IsString, Max, Min } from 'class-validator';
import { DataSource } from 'typeorm';
import { CpService } from './cp.service';
import { AdminGuard } from '../../common/guards/admin.guard';
import { JwtAuthGuard } from '../../common/guards/jwt-auth.guard';
import { CurrentUser } from '../../common/decorators';
import { CpType } from '../../database/entities/cp-relationship.entity';

class CpTypeConfigDto {
  type?: CpType;
  @IsOptional() @IsInt() @Min(0) @Max(1000000000) priceCoins?: number;
  @IsOptional() @IsString() giftId?: string;
  @IsOptional() @IsInt() @Min(0) @Max(1000000000) giftPoints?: number;
  @IsOptional() @IsInt() @Min(0) @Max(1000000000) giftInteractionPoints?: number;
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
  constructor(private readonly dataSource: DataSource, private readonly cp: CpService) {}

  @Get('config')
  @ApiOperation({ summary: 'Read CP types and admin-configured prices, gifts, levels and rules' })
  async getConfig() {
    const rows = await this.dataSource.query(
      `SELECT key,value FROM app_settings WHERE key LIKE 'cp.%' ORDER BY key`,
    );
    const settings = Object.fromEntries(rows.map((row: { key: string; value: string }) => {
      try { return [row.key, JSON.parse(row.value)]; } catch { return [row.key, row.value]; }
    }));
    const types: Array<Record<string, unknown>> = [];
    for (const type of Object.values(CpType)) {
      const giftId = settings[`cp.gift.${type}`] ?? null;
      const price = Number(settings[`cp.price.${type}`]);
      const [gift] = giftId ? await this.dataSource.query('SELECT id,name,"iconUrl","animationUrl" FROM gifts WHERE id=$1 AND "isActive"=true', [giftId]) : [];
      types.push({
        type,
        priceCoins: Number.isSafeInteger(price) && price > 0 ? price : null,
        giftId: giftId || null,
        gift: gift ? { id: gift.id, name: gift.name, iconUrl: gift.iconUrl, animationUrl: gift.animationUrl } : null,
        giftPoints: settings[`cp.gift_points.${type}`] ?? 0,
        giftInteractionPoints: settings[`cp.points.gift_send.${type}`] ?? 0,
        maxLevel: settings[`cp.max_level.${type}`] ?? null,
        levels: settings[`cp.levels.${type}`] ?? [],
        // CP types are enabled automatically; price and an active gift determine purchase readiness.
        enabled: settings[`cp.enabled.${type}`] !== false && settings[`cp.enabled.${type}`] !== 'false'
          && Number.isSafeInteger(price) && price > 0 && !!gift,
        rules: settings[`cp.rules.${type}`] ?? {},
      });
    }
    let taskConfig: Record<string, any> = {};
    try { taskConfig = JSON.parse(String(settings['cp.tasks.config'] || '{}')); } catch { taskConfig = {}; }
    const tasks = { ...taskConfig };
    tasks.memory_today = { enabled: true, title: 'ذكرى جديدة', description: 'أضيفوا ذكرى مشتركة اليوم', condition: 'memory_count', target: 1, rewardPoints: 10, rewardCosmeticCode: '', ...(tasks.memory_today || {}) };
    tasks.earn_points_today = { enabled: true, title: 'نشاط مشترك', description: 'اجمعوا نقاط العلاقة اليوم', condition: 'points_today', target: 10, rewardPoints: 15, rewardCosmeticCode: '', ...(tasks.earn_points_today || {}) };
    return {
      globallyEnabled: settings['cp.enabled.global'] !== false && settings['cp.enabled.global'] !== 'false',
      tasks,
      types,
      policy: {
        requestExpiryMinutes: settings['cp.policy.request_expiry_minutes'] ?? 30,
        rules: settings['cp.policy.rules'] ?? {},
        fullRefundOnReject: settings['cp.policy.full_refund_on_reject'] ?? true,
        fullRefundOnExpiry: settings['cp.policy.full_refund_on_expiry'] ?? true,
        allowEitherPartyToEnd: settings['cp.policy.allow_either_party_to_end'] ?? true,
      },
      note: 'Prices and levels are intentionally unset until an administrator configures them.',
    };
  }


  @Patch('features')
  @ApiOperation({ summary: 'Enable/disable CP globally and configure the supported daily CP missions' })
  async updateFeatures(
    @CurrentUser('sub') adminId: string,
    @Body() body: {
      globallyEnabled?: boolean;
      tasks?: Record<string, { enabled?: boolean; title?: string; target?: number; rewardPoints?: number; rewardCosmeticCode?: string }>;
    },
  ) {
    let current: Record<string, any> = {};
    if (body.tasks !== undefined) {
      if (!body.tasks || typeof body.tasks !== 'object' || Array.isArray(body.tasks) || Object.keys(body.tasks).length > 100) {
        throw new BadRequestException('إعدادات مهمات CP غير صالحة');
      }
      for (const id of Object.keys(body.tasks)) {
        if (!/^[a-zA-Z0-9_-]{2,64}$/.test(id)) throw new BadRequestException('معرّف مهمة CP غير صالح');
        const task = body.tasks[id];
        if (!task || typeof task !== 'object' || Array.isArray(task)) throw new BadRequestException('بيانات المهمة غير صالحة');
        if (task.enabled !== undefined && typeof task.enabled !== 'boolean') throw new BadRequestException('حالة المهمة غير صالحة');
        if (task.condition !== undefined && !['memory_count', 'points_today'].includes(String(task.condition))) throw new BadRequestException('شرط المهمة غير مدعوم');
        if (task.description !== undefined && (typeof task.description !== 'string' || task.description.length > 240)) throw new BadRequestException('وصف المهمة غير صالح');
        if (task.target !== undefined && (!Number.isSafeInteger(task.target) || task.target < 1 || task.target > 1000000)) throw new BadRequestException('هدف المهمة يجب أن يكون بين 1 و1,000,000');
        if (task.rewardPoints !== undefined && (!Number.isSafeInteger(task.rewardPoints) || task.rewardPoints < 0 || task.rewardPoints > 1000000)) throw new BadRequestException('مكافأة المهمة يجب أن تكون بين 0 و1,000,000 نقطة');
        if (task.title !== undefined && (typeof task.title !== 'string' || !task.title.trim() || task.title.length > 100)) throw new BadRequestException('عنوان المهمة غير صالح');
        if (task.rewardCosmeticCode !== undefined) {
          const code = String(task.rewardCosmeticCode || '').trim();
          if (code && !/^[a-zA-Z0-9_-]{2,64}$/.test(code)) throw new BadRequestException('رمز هدية المهمة غير صالح');
          if (code) {
            const [item] = await this.dataSource.query(
              'SELECT id FROM cosmetics WHERE code=$1 AND code LIKE \'cp_%\' AND "isActive"=true LIMIT 1',
              [code],
            );
            if (!item) throw new BadRequestException('هدية المهمة يجب أن تكون عنصراً مفعّلاً من متجر CP');
          }
          task.rewardCosmeticCode = code;
        }
        current[id] = { ...task, ...(task.title !== undefined ? { title: task.title.trim() } : {}), ...(task.description !== undefined ? { description: task.description.trim() } : {}) };
      }
    }
    const values: Record<string, string> = {};
    if (body.globallyEnabled !== undefined) values['cp.enabled.global'] = String(body.globallyEnabled);
    if (body.tasks !== undefined) values['cp.tasks.config'] = JSON.stringify(current);
    await this.dataSource.transaction(async manager => {
      for (const [key, value] of Object.entries(values)) {
        await manager.query(
          `INSERT INTO app_settings (id,key,value,description,"createdAt","updatedAt")
           VALUES (gen_random_uuid(),$1,$2,$3,now(),now())
           ON CONFLICT (key) DO UPDATE SET value=EXCLUDED.value,description=EXCLUDED.description,"updatedAt"=now()`,
          [key, value, 'CP features and missions configured by admin'],
        );
      }
      await manager.query(
        `INSERT INTO cp_audit_logs ("adminUserId",action,"entityType","entityId",details,"createdAt")
         VALUES ($1,'features_update','cp_features','global',$2::jsonb,now())`,
        [adminId, JSON.stringify({ keys: Object.keys(values), globallyEnabled: body.globallyEnabled ?? null })],
      );
    });
    return this.getConfig();
  }

  @Patch('policy')
  @ApiOperation({ summary: 'Configure CP request expiry and lifecycle policy without weakening full refunds' })
  async updatePolicy(
    @CurrentUser('sub') adminId: string,
    @Body() body: { requestExpiryMinutes?: number; rules?: Record<string, unknown> },
  ) {
    const config: Record<string, string> = {};
    if (body.requestExpiryMinutes !== undefined) {
      if (!Number.isInteger(body.requestExpiryMinutes) || body.requestExpiryMinutes < 5 || body.requestExpiryMinutes > 1440) {
        throw new BadRequestException('مهلة الطلب يجب أن تكون بين 5 دقائق و24 ساعة');
      }
      config['cp.policy.request_expiry_minutes'] = String(body.requestExpiryMinutes);
    }
    if (body.rules !== undefined) config['cp.policy.rules'] = JSON.stringify(body.rules);
    await this.dataSource.transaction(async manager => {
      for (const [key, value] of Object.entries(config)) {
        await manager.query(
          `INSERT INTO app_settings (id,key,value,description,"createdAt","updatedAt")
           VALUES (gen_random_uuid(),$1,$2,$3,now(),now())
           ON CONFLICT (key) DO UPDATE SET value=EXCLUDED.value,description=EXCLUDED.description,"updatedAt"=now()`,
          [key, value, 'CP lifecycle policy configured by admin'],
        );
      }
      await manager.query(
        `INSERT INTO cp_audit_logs ("adminUserId",action,"entityType","entityId",details,"createdAt")
         VALUES ($1,'policy_update','cp_policy','global',$2::jsonb,now())`,
        [adminId, JSON.stringify({ keys: Object.keys(config) })],
      );
    });
    return this.getConfig();
  }

  @Patch('config/:type')
  async setTypeConfig(
    @CurrentUser('sub') adminId: string,
    @Param('type') type: CpType,
    @Body() body: CpTypeConfigDto,
  ) {
    if (!Object.values(CpType).includes(type)) throw new BadRequestException('نوع CP غير صالح');
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
    if (body.giftId !== undefined && body.giftId) {
      const [selectedGift] = await this.dataSource.query(
        'SELECT id FROM gifts WHERE id=$1 AND "isActive"=true LIMIT 1',
        [body.giftId],
      );
      if (!selectedGift) throw new BadRequestException('اختر هدية موجودة ومفعّلة من كتالوج الهدايا');
    }
    const config: Record<string, unknown> = {};
    if (body.priceCoins !== undefined) config[`cp.price.${type}`] = String(body.priceCoins);
    if (body.giftId !== undefined) config[`cp.gift.${type}`] = body.giftId;
    if (body.giftPoints !== undefined) config[`cp.gift_points.${type}`] = String(body.giftPoints);
    if (body.giftInteractionPoints !== undefined) config[`cp.points.gift_send.${type}`] = String(body.giftInteractionPoints);
    if (body.maxLevel !== undefined) config[`cp.max_level.${type}`] = String(body.maxLevel);
    if (body.levels !== undefined) config[`cp.levels.${type}`] = JSON.stringify(body.levels);
    if (body.enabled !== undefined) config[`cp.enabled.${type}`] = String(body.enabled);
    if (body.rules !== undefined) config[`cp.rules.${type}`] = JSON.stringify(body.rules);
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
        [adminId, type, JSON.stringify({ keys: Object.keys(config) })],
      );
    });
    return this.getConfig();
  }


  @Post('relationships/points')
  @ApiOperation({ summary: 'Admin-only point adjustment recorded in the idempotent CP ledger' })
  async adjustRelationshipPoints(
    @CurrentUser('sub') adminId: string,
    @Body() body: { relationshipId: string; eventType: string; referenceType: string; referenceId: string; points: number; metadata?: Record<string, unknown> },
  ) {
    if (!body?.relationshipId || !body.eventType || !body.referenceType || !body.referenceId || !Number.isSafeInteger(body.points) || body.points < 0) {
      throw new BadRequestException('بيانات نقاط العلاقة غير صالحة');
    }
    const result = await this.cp.recordPoints(
      body.relationshipId, body.eventType, body.referenceType, body.referenceId, body.points, body.metadata || {},
    );
    await this.dataSource.query(
      `INSERT INTO cp_audit_logs ("adminUserId",action,"entityType","entityId",details,"createdAt")
       VALUES ($1,'points_adjustment','cp_relationship',$2,$3::jsonb,now())`,
      [adminId, body.relationshipId, JSON.stringify({ eventType: body.eventType, referenceType: body.referenceType, referenceId: body.referenceId, points: body.points, duplicate: (result as any).duplicate })],
    );
    return result;
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
