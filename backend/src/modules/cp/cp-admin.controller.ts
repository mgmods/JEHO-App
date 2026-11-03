import { Body, Controller, Get, Patch, Post, Param, UseGuards, BadRequestException, InternalServerErrorException } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { IsArray, IsBoolean, IsInt, IsObject, IsOptional, IsString, Max, Min } from 'class-validator';
import { DataSource } from 'typeorm';
import { CpService } from './cp.service';
import { AdminGuard } from '../../common/guards/admin.guard';
import { JwtAuthGuard } from '../../common/guards/jwt-auth.guard';
import { CurrentUser } from '../../common/decorators';
import { CpType } from '../../database/entities/cp-relationship.entity';
import { RealtimeGateway } from '../realtime/realtime.gateway';

function canonicalJson(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(item => canonicalJson(item)).join(',')}]`;
  if (value && typeof value === 'object') {
    const record = value as Record<string, unknown>;
    return `{${Object.keys(record).sort().map(key => `${JSON.stringify(key)}:${canonicalJson(record[key])}`).join(',')}}`;
  }
  return JSON.stringify(value) ?? 'null';
}

class CpTypeConfigDto {
  type?: CpType;
  @IsOptional() @IsInt() @Min(0) @Max(1000000000) priceCoins?: number;
  @IsOptional() @IsString() giftId?: string;
  @IsOptional() @IsInt() @Min(0) @Max(1000000000) giftPoints?: number;
  @IsOptional() @IsInt() @Min(0) @Max(1000000000) giftInteractionPoints?: number;
  @IsOptional() @IsInt() @Min(1) @Max(1000) maxLevel?: number;
  @IsOptional() @IsArray() levels?: Array<Record<string, any>>;
  @IsOptional() @IsBoolean() enabled?: boolean;
  @IsOptional() @IsObject() rules?: Record<string, unknown>;
}

@ApiTags('Admin CP')
@ApiBearerAuth()
@Controller('admin/cp')
@UseGuards(JwtAuthGuard, AdminGuard)
export class CpAdminController {
  constructor(private readonly dataSource: DataSource, private readonly cp: CpService, private readonly realtime: RealtimeGateway) {}

  @Get('config')
  @ApiOperation({ summary: 'Read CP types and admin-configured prices, gifts, levels and rules' })
  async getConfig() {
    const rows = await this.dataSource.query(
      `SELECT key,value FROM app_settings WHERE key LIKE 'cp.%' ORDER BY key`,
    );
    // app_settings.value is TEXT. Older CP writers and migration paths have
    // occasionally persisted JSON values that were JSON-encoded more than once.
    // Decode only the setting's outer value (never nested user strings) so legacy
    // rows such as a JSON-encoded JSON array are read as the same array the editor sent.
    const decodeSettingValue = (raw: unknown): unknown => {
      let value = raw;
      for (let depth = 0; depth < 3 && typeof value === 'string'; depth += 1) {
        const candidate = value.trim();
        if (!candidate) break;
        try {
          const decoded = JSON.parse(candidate);
          if (decoded === value) break;
          value = decoded;
        } catch {
          break;
        }
      }
      return value;
    };
    const settings = Object.fromEntries(rows.map((row: { key: string; value: unknown }) =>
      [row.key, decodeSettingValue(row.value)],
    ));
    const types: Array<Record<string, unknown>> = [];
    for (const type of [CpType.RELATIONSHIP]) {
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
    try {
      const configured = settings['cp.tasks.config'];
      taskConfig = configured && typeof configured === 'object' && !Array.isArray(configured)
        ? configured
        : JSON.parse(String(configured || '{}'));
    } catch { taskConfig = {}; }
    const tasks = { ...taskConfig };
    tasks.memory_today = { enabled: true, title: 'ذكرى جديدة', description: 'أضيفوا ذكرى مشتركة اليوم', condition: 'memory_count', target: 1, rewardPoints: 10, rewardCosmeticCode: '', ...(tasks.memory_today || {}) };
    tasks.earn_points_today = { enabled: true, title: 'نشاط مشترك', description: 'اجمعوا نقاط العلاقة اليوم', condition: 'points_today', target: 10, rewardPoints: 15, rewardCosmeticCode: '', ...(tasks.earn_points_today || {}) };
    return {
      globallyEnabled: settings['cp.enabled.global'] !== false && settings['cp.enabled.global'] !== 'false',
      giftTabEnabled: settings['cp.gift_tab.enabled'] !== false && settings['cp.gift_tab.enabled'] !== 'false',
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
      giftTabEnabled?: boolean;
      tasks?: Record<string, { enabled?: boolean; title?: string; description?: string; condition?: string; target?: number; rewardPoints?: number; rewardCosmeticCode?: string }>;
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
        if (task.condition !== undefined && !['memory_count', 'points_today', 'gift_exchange_today'].includes(String(task.condition))) throw new BadRequestException('شرط المهمة غير مدعوم');
        if (task.description !== undefined && (typeof task.description !== 'string' || task.description.length > 240)) throw new BadRequestException('وصف المهمة غير صالح');
        if (task.target !== undefined && (!Number.isSafeInteger(task.target) || task.target < 1 || task.target > 1000000)) throw new BadRequestException('هدف المهمة يجب أن يكون بين 1 و1,000,000');
        if (task.rewardPoints !== undefined && (!Number.isSafeInteger(task.rewardPoints) || task.rewardPoints < 0 || task.rewardPoints > 1000000)) throw new BadRequestException('مكافأة المهمة يجب أن تكون بين 0 و1,000,000 نقطة');
        if (task.title !== undefined && (typeof task.title !== 'string' || !task.title.trim() || task.title.length > 100)) throw new BadRequestException('عنوان المهمة غير صالح');
        if (task.rewardCosmeticCode !== undefined) {
          const code = String(task.rewardCosmeticCode || '').trim();
          if (code && !/^[a-zA-Z0-9_-]{2,64}$/.test(code)) throw new BadRequestException('رمز هدية المهمة غير صالح');
          if (code) {
            const [item] = await this.dataSource.query(
              'SELECT id FROM cosmetics WHERE code=$1 AND "isActive"=true LIMIT 1',
              [code],
            );
            if (!item) throw new BadRequestException('مكافأة المهمة يجب أن تكون عنصراً تجميلياً مفعّلاً');
          }
          task.rewardCosmeticCode = code;
        }
        current[id] = { ...task, ...(task.title !== undefined ? { title: task.title.trim() } : {}), ...(task.description !== undefined ? { description: task.description.trim() } : {}) };
      }
    }
    const values: Record<string, string> = {};
    if (body.globallyEnabled !== undefined) values['cp.enabled.global'] = String(body.globallyEnabled);
    if (body.giftTabEnabled !== undefined) values['cp.gift_tab.enabled'] = String(body.giftTabEnabled);
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
    const saved = await this.getConfig();
    this.realtime.emitToAll('cp:config-updated', { scope: 'cp', updatedAt: new Date().toISOString() });
    return saved;
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
    const saved = await this.getConfig();
    this.realtime.emitToAll('cp:config-updated', { scope: 'cp', updatedAt: new Date().toISOString() });
    return saved;
  }

  @Patch('config/:type')
  async setTypeConfig(
    @CurrentUser('sub') adminId: string,
    @Param('type') type: CpType,
    @Body() body: CpTypeConfigDto,
  ) {
    if (type !== CpType.RELATIONSHIP) throw new BadRequestException('نظام CP يعتمد نوع ارتباط واحداً فقط');
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
        if (level.name !== undefined && (typeof level.name !== 'string' || level.name.length > 100)) {
          throw new BadRequestException('اسم مستوى CP غير صالح');
        }
        if (level.description !== undefined && (typeof level.description !== 'string' || level.description.length > 500)) {
          throw new BadRequestException('تفاصيل مستوى CP غير صالحة؛ الحد الأقصى 500 حرف');
        }
        if (level.giftBonusPercent !== undefined &&
            (!Number.isFinite(Number(level.giftBonusPercent)) || Number(level.giftBonusPercent) < 0 || Number(level.giftBonusPercent) > 500)) {
          throw new BadRequestException('نسبة نقاط هدايا المستوى يجب أن تكون بين 0 و500');
        }
        if (level.rewardCosmeticCode !== undefined && level.rewardCosmeticCode !== '') {
          if (typeof level.rewardCosmeticCode !== 'string' || !/^[a-zA-Z0-9_-]{2,64}$/.test(level.rewardCosmeticCode)) {
            throw new BadRequestException('رمز المكافأة التجميلية لمستوى CP غير صالح');
          }
          const [rewardCosmetic] = await this.dataSource.query(
            'SELECT id FROM cosmetics WHERE code=$1 AND "isActive"=true LIMIT 1',
            [level.rewardCosmeticCode],
          );
          if (!rewardCosmetic) throw new BadRequestException('اختر مكافأة تجميلية نشطة من كتالوج المتجر');
        }
        if (level.rewardCosmeticCodes !== undefined) {
          if (!Array.isArray(level.rewardCosmeticCodes) || level.rewardCosmeticCodes.length > 30) {
            throw new BadRequestException('قائمة المكافآت التجميلية للمستوى غير صالحة');
          }
          const codes = Array.from(new Set(level.rewardCosmeticCodes.map((code: any) => String(code || '').trim()).filter(Boolean)));
          if (codes.some((code: string) => !/^[a-zA-Z0-9_-]{2,64}$/.test(code))) {
            throw new BadRequestException('أحد رموز المكافآت التجميلية للمستوى غير صالح');
          }
          if (codes.length) {
            const activeItems = await this.dataSource.query(
              'SELECT code FROM cosmetics WHERE code=ANY($1::text[]) AND "isActive"=true',
              [codes],
            );
            if (activeItems.length !== codes.length) {
              throw new BadRequestException('كل المكافآت التجميلية للمستوى يجب أن تكون عناصر نشطة');
            }
          }
        }
        const extraRewards = [
          ...(Array.isArray(level.benefits) ? level.benefits : []),
          ...(Array.isArray(level.rewards) ? level.rewards : []),
        ];
        if (extraRewards.length > 30) throw new BadRequestException('لا يمكن إضافة أكثر من 30 مكافأة للمستوى');
        for (const reward of extraRewards) {
          const code = String(reward?.cosmeticCode || reward?.code || '').trim();
          if (!code) continue;
          if (!/^[a-zA-Z0-9_-]{2,64}$/.test(code)) throw new BadRequestException('رمز إحدى مكافآت المستوى غير صالح');
          const [activeReward] = await this.dataSource.query(
            'SELECT id FROM cosmetics WHERE code=$1 AND "isActive"=true LIMIT 1',
            [code],
          );
          if (!activeReward) throw new BadRequestException('كل مكافآت المستوى يجب أن تكون عناصر تجميلية نشطة');
        }
        if (level.rewardDays !== undefined && (!Number.isInteger(Number(level.rewardDays)) || Number(level.rewardDays) < 1 || Number(level.rewardDays) > 365)) {
          throw new BadRequestException('مدة مكافأة مستوى CP يجب أن تكون بين يوم و365 يوماً');
        }
        if (level.rewardPermanent !== undefined && typeof level.rewardPermanent !== 'boolean') {
          throw new BadRequestException('خيار ديمومة مكافأة مستوى CP غير صالح');
        }
        const rewardGiftIds = Array.from(new Set([
          ...(Array.isArray(level.rewardGiftIds) ? level.rewardGiftIds : []),
          level.rewardGiftId,
        ].map((id: any) => String(id || '').trim()).filter(Boolean)));
        if (rewardGiftIds.length > 30 || rewardGiftIds.some((id: string) =>
            !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(id))) {
          throw new BadRequestException('قائمة هدايا مكافأة المستوى غير صالحة');
        }
        if (rewardGiftIds.length) {
          const activeGifts = await this.dataSource.query(
            'SELECT id FROM gifts WHERE id=ANY($1::uuid[]) AND "isActive"=true',
            [rewardGiftIds],
          );
          if (activeGifts.length !== rewardGiftIds.length) {
            throw new BadRequestException('كل هدايا مكافأة المستوى يجب أن تكون هدايا نشطة من الكتالوج');
          }
        }
        if (level.giftPresentation !== undefined) {
          if (!level.giftPresentation || typeof level.giftPresentation !== 'object' || Array.isArray(level.giftPresentation)) {
            throw new BadRequestException('إعدادات هدية مستوى CP غير صالحة');
          }
          const animationUrl = level.giftPresentation.animationUrl;
          if (animationUrl !== undefined && animationUrl !== '' &&
              (typeof animationUrl !== 'string' || animationUrl.length > 2048 ||
               !(animationUrl.startsWith('https://') || animationUrl.startsWith('http://')))) {
            throw new BadRequestException('رابط أنيميشن هدية CP غير صالح');
          }
          if (level.giftPresentation.showPartnerAvatars !== undefined &&
              typeof level.giftPresentation.showPartnerAvatars !== 'boolean') {
            throw new BadRequestException('خيار صور الشريكين في هدية CP غير صالح');
          }
        }
        if (level.seatEffect !== undefined) {
          const effect = level.seatEffect;
          if (!effect || typeof effect !== 'object' || Array.isArray(effect)) {
            throw new BadRequestException('إعدادات مؤثر مقاعد مستوى CP غير صالحة');
          }
          if (effect.enabled !== undefined && typeof effect.enabled !== 'boolean') {
            throw new BadRequestException('حالة مؤثر مقاعد المستوى غير صالحة');
          }
          if (effect.url !== undefined && effect.url !== '' &&
              (typeof effect.url !== 'string' || effect.url.length > 2048 ||
               !(effect.url.startsWith('https://') || effect.url.startsWith('http://')))) {
            throw new BadRequestException('رابط مؤثر مقاعد المستوى غير صالح');
          }
          // Keep the API validation aligned with the dashboard's 10–36 dp control.
          // A valid value from the admin UI must never be rejected on save.
          if (effect.sizeDp !== undefined &&
              (!Number.isInteger(Number(effect.sizeDp)) || Number(effect.sizeDp) < 10 || Number(effect.sizeDp) > 36)) {
            throw new BadRequestException('حجم مؤثر مقاعد المستوى يجب أن يكون بين 10 و36');
          }
          if (effect.durationMs !== undefined &&
              (!Number.isInteger(Number(effect.durationMs)) || Number(effect.durationMs) < 500 || Number(effect.durationMs) > 10000)) {
            throw new BadRequestException('مدة مؤثر مقاعد المستوى يجب أن تكون بين 500 و10000 مللي ثانية');
          }
        }
        if (level.presentation !== undefined) {
          if (!level.presentation || typeof level.presentation !== 'object' || Array.isArray(level.presentation)) {
            throw new BadRequestException('إعدادات عرض مستوى CP غير صالحة');
          }
          for (const field of ['frameUrl', 'levelLogoUrl', 'logoUrl', 'badgeUrl', 'cardBackgroundUrl']) {
            const url = level.presentation[field];
            if (url !== undefined && url !== '' &&
                (typeof url !== 'string' || url.length > 2048 || !(url.startsWith('https://') || url.startsWith('http://')))) {
              throw new BadRequestException('رابط صورة مستوى CP غير صالح: ' + field);
            }
          }
          if (level.presentation.nameColor !== undefined &&
              (typeof level.presentation.nameColor !== 'string' || !/^#[0-9a-f]{6}$/i.test(level.presentation.nameColor))) {
            throw new BadRequestException('لون اسم مستوى CP غير صالح');
          }
          if (level.presentation.nameGlow !== undefined && typeof level.presentation.nameGlow !== 'boolean') {
            throw new BadRequestException('خيار توهج اسم مستوى CP غير صالح');
          }
        }
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
      const entries = Object.entries(config);
      if (entries.length) {
        // Persist all CP fields with one database round-trip. The previous
        // per-key loop made a single Save wait on several sequential writes.
        await manager.query(
          `INSERT INTO app_settings (id,key,value,description,"createdAt","updatedAt")
           SELECT gen_random_uuid(), item.key, item.value, $3, now(), now()
           FROM unnest($1::text[], $2::text[]) AS item(key, value)
           WHERE true
           ON CONFLICT (key) DO UPDATE
             SET value=EXCLUDED.value, description=EXCLUDED.description, "updatedAt"=now()`,
          [entries.map(([key]) => key), entries.map(([, value]) => String(value)), 'CP settings configured through the protected admin API'],
        );
      }
      await manager.query(
        `INSERT INTO cp_audit_logs ("adminUserId",action,"entityType","entityId",details,"createdAt")
         VALUES ($1,'config_update','cp_type',$2,$3::jsonb,now())`,
        [adminId, type, JSON.stringify({ keys: Object.keys(config) })],
      );
    });

    // Read the persisted JSON back from app_settings before reporting success.
    // This catches silent schema/serialization mismatches that otherwise make the
    // dashboard reload an older level configuration after a successful-looking save.
    const savedConfig = await this.getConfig();
    const savedType = savedConfig.types.find((entry: Record<string, any>) => entry.type === type);
    const mismatches: string[] = [];
    if (body.levels !== undefined &&
        (!savedType || !Array.isArray(savedType.levels) ||
         canonicalJson(savedType.levels) !== canonicalJson(body.levels))) {
      mismatches.push('المستويات والصور والمكافآت');
    }
    if (body.giftId !== undefined &&
        String(savedType?.giftId || (savedType?.gift as Record<string, any> | undefined)?.id || '') !== String(body.giftId || '')) {
      mismatches.push('هدية CP');
    }
    if (body.giftPoints !== undefined &&
        Number(savedType?.giftPoints || 0) !== Number(body.giftPoints)) {
      mismatches.push('نقاط الهدية');
    }
    if (body.giftInteractionPoints !== undefined &&
        Number(savedType?.giftInteractionPoints || 0) !== Number(body.giftInteractionPoints)) {
      mismatches.push('نقاط التفاعل');
    }
    if (body.maxLevel !== undefined &&
        Number(savedType?.maxLevel || 0) !== Number(body.maxLevel)) {
      mismatches.push('الحد الأعلى للمستوى');
    }
    if (body.priceCoins !== undefined) {
      const expectedPrice = body.priceCoins > 0 ? body.priceCoins : null;
      if ((savedType?.priceCoins ?? null) !== expectedPrice) mismatches.push('السعر');
    }
    if (body.rules !== undefined &&
        canonicalJson(savedType?.rules || {}) !== canonicalJson(body.rules)) {
      mismatches.push('التصميم والمؤثرات');
    }
    if (mismatches.length) {
      throw new InternalServerErrorException(
        'تعذر تأكيد حفظ إعدادات CP بعد إعادة قراءتها من قاعدة البيانات: ' + mismatches.join('، ') + '.',
      );
    }
    this.realtime.emitToAll('cp:config-updated', { scope: 'cp', updatedAt: new Date().toISOString() });
    return savedConfig;
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
