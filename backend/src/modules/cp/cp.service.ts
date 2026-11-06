import { BadRequestException, ConflictException, Injectable, NotFoundException, OnModuleInit, OnModuleDestroy } from '@nestjs/common';
import { DataSource } from 'typeorm';
import { RealtimeGateway } from '../realtime/realtime.gateway';
import { CpType } from '../../database/entities/cp-relationship.entity';
import { CpRequestStatus } from '../../database/entities/cp-request.entity';
import { NotificationType } from '../../database/entities/notification.entity';
import { NotificationsService } from '../notifications/notifications.service';
import { CosmeticsService } from '../cosmetics/cosmetics.service';

const TYPES = new Set<string>(Object.values(CpType));
const EXPIRY_MINUTES = 30;
const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function parseJsonSetting(raw: unknown, fallback: any): any {
  let value: unknown = raw;
  for (let depth = 0; depth < 3 && typeof value === 'string'; depth += 1) {
    const candidate = value.trim();
    if (!candidate) return fallback;
    try { value = JSON.parse(candidate); } catch { return fallback; }
  }
  return value === undefined || value === null ? fallback : value;
}

@Injectable()
export class CpService implements OnModuleInit, OnModuleDestroy {
  private expiryTimer: ReturnType<typeof setInterval> | null = null;

  constructor(
    private readonly dataSource: DataSource,
    private readonly realtime: RealtimeGateway,
    private readonly notifications: NotificationsService,
    private readonly cosmetics: CosmeticsService,
  ) {}

  async storeCatalog() {
    return this.cosmetics.cpStoreCatalog();
  }

  async storeInventory(userId: string) {
    return this.cosmetics.cpStoreInventory(userId);
  }

  async purchaseStoreItem(userId: string, cosmeticId: string) {
    if (!UUID_REGEX.test(String(cosmeticId || ''))) throw new BadRequestException('معرّف عنصر CP غير صالح');
    return this.cosmetics.purchaseCpStoreItem(userId, cosmeticId);
  }

  async equipStoreItem(userId: string, cosmeticId: string) {
    if (!UUID_REGEX.test(String(cosmeticId || ''))) throw new BadRequestException('معرّف عنصر CP غير صالح');
    return this.cosmetics.equipCpStoreItem(userId, cosmeticId);
  }

  async onModuleInit() {
    // Backward-compatible schema adjustment: profile-origin CP requests have no room/payment.
    await this.dataSource.query('ALTER TABLE cp_requests ALTER COLUMN "roomId" DROP NOT NULL').catch(() => undefined);
    await this.dataSource.query('ALTER TABLE cp_requests ALTER COLUMN "priceCoins" DROP NOT NULL').catch(() => undefined);
    await this.dataSource.query('ALTER TABLE cp_requests ADD COLUMN IF NOT EXISTS "cpPoints" bigint NULL').catch(() => undefined);
    await this.dataSource.query('ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS "cpBackgroundUrl" varchar(512)').catch(() => undefined);
    await this.dataSource.query('ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS "cpLogoUrl" varchar(512)').catch(() => undefined);
    // The cosmetics.type column is a PostgreSQL enum. Add CP-only cosmetic
    // types at runtime so existing Cumin databases accept the new store items.
    const cosmeticEnumRows = await this.dataSource.query(
      `SELECT n.nspname, t.typname
       FROM pg_type t
       JOIN pg_attribute a ON a.atttypid=t.oid
       JOIN pg_class c ON c.oid=a.attrelid
       JOIN pg_namespace n ON n.oid=t.typnamespace
       WHERE c.relname='cosmetics' AND a.attname='type' AND t.typtype='e'
       LIMIT 1`,
    ).catch(() => []);
    const cosmeticEnumSchema = String(cosmeticEnumRows?.[0]?.nspname || '').replace(/"/g, '""');
    const cosmeticEnumName = String(cosmeticEnumRows?.[0]?.typname || '').replace(/"/g, '""');
    if (cosmeticEnumSchema && cosmeticEnumName) {
      await this.dataSource.query(`ALTER TYPE "${cosmeticEnumSchema}"."${cosmeticEnumName}" ADD VALUE IF NOT EXISTS 'cp_background'`).catch(() => undefined);
      await this.dataSource.query(`ALTER TYPE "${cosmeticEnumSchema}"."${cosmeticEnumName}" ADD VALUE IF NOT EXISTS 'cp_logo'`).catch(() => undefined);
    }
    await this.dataSource.query(`
      CREATE TABLE IF NOT EXISTS cp_memories (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "relationshipId" uuid NOT NULL REFERENCES cp_relationships(id) ON DELETE CASCADE,
        "userId" uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        text varchar(500) NOT NULL DEFAULT '',
        "imageUrl" varchar(2048),
        "createdAt" timestamptz NOT NULL DEFAULT now()
      )
    `).catch(() => undefined);
    await this.dataSource.query(`
      CREATE TABLE IF NOT EXISTS cp_level_reward_claims (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "relationshipId" uuid NOT NULL REFERENCES cp_relationships(id) ON DELETE CASCADE,
        "userId" uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        level integer NOT NULL,
        "cosmeticCode" varchar(64) NOT NULL,
        status varchar(16) NOT NULL DEFAULT 'pending',
        "createdAt" timestamptz NOT NULL DEFAULT now(),
        "grantedAt" timestamptz,
        UNIQUE ("relationshipId", level, "userId", "cosmeticCode")
      )
    `).catch(() => undefined);
    await this.dataSource.query(`
      CREATE TABLE IF NOT EXISTS cp_level_gift_reward_claims (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "relationshipId" uuid NOT NULL REFERENCES cp_relationships(id) ON DELETE CASCADE,
        "userId" uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        level integer NOT NULL,
        "giftId" uuid NOT NULL REFERENCES gifts(id) ON DELETE CASCADE,
        status varchar(16) NOT NULL DEFAULT 'pending',
        "createdAt" timestamptz NOT NULL DEFAULT now(),
        "grantedAt" timestamptz,
        UNIQUE ("relationshipId", level, "userId", "giftId")
      )
    `).catch(() => undefined);
    await this.dataSource.query(`
      CREATE INDEX IF NOT EXISTS idx_cp_memories_relationship_created
      ON cp_memories ("relationshipId", "createdAt" DESC)
    `).catch(() => undefined);
    await this.migrateUnifiedRelationshipType();
    // Expiry processing only touches CP requests and their wallet ledger rows.
    this.expiryTimer = setInterval(() => {
      void this.expireDueRequests().catch(() => undefined);
    }, 60_000);
    this.expiryTimer.unref?.();
    void this.expireDueRequests().catch(() => undefined);
  }

  onModuleDestroy() {
    if (this.expiryTimer) clearInterval(this.expiryTimer);
    this.expiryTimer = null;
  }

  private async migrateUnifiedRelationshipType() {
    const rows = await this.dataSource.query(
      `SELECT key,value FROM app_settings WHERE key LIKE 'cp.%'`,
    );
    const settings = new Map<string, string>(rows.map((row: any) => [String(row.key), String(row.value ?? '')]));
    const legacyTypes = ['love', 'engagement', 'friendship', 'brotherhood', 'marriage'];
    const suffixes = ['gift', 'price', 'gift_points', 'points.gift_send', 'max_level', 'levels', 'enabled', 'rules'];
    const sourceType = legacyTypes
      .map(type => ({ type, score: suffixes.filter(suffix => settings.has(`cp.${suffix}.${type}`)).length }))
      .sort((a, b) => b.score - a.score)[0]?.type;
    if (sourceType) {
      await this.dataSource.transaction(async manager => {
        for (const suffix of suffixes) {
          const canonicalKey = `cp.${suffix}.relationship`;
          if (settings.has(canonicalKey)) continue;
          const legacyValue = settings.get(`cp.${suffix}.${sourceType}`);
          if (legacyValue === undefined) continue;
          await manager.query(
            `INSERT INTO app_settings (id,key,value,description,"createdAt","updatedAt")
             VALUES (gen_random_uuid(),$1,$2,$3,now(),now())
             ON CONFLICT (key) DO NOTHING`,
            [canonicalKey, legacyValue, 'Migrated from legacy CP relationship type'],
          );
        }
      });
    }
    // Preserve all history, points and requests while removing user-visible subtypes.
    await this.dataSource.query(
      `UPDATE cp_relationships SET type=$1 WHERE type<>$1`,
      [CpType.RELATIONSHIP],
    );
    await this.dataSource.query(
      `UPDATE cp_requests SET type=$1 WHERE type<>$1`,
      [CpType.RELATIONSHIP],
    );
    // Preserve existing earned CP levels. Multi-level progression is configured
    // through cp.levels.relationship; never reset user progress on application boot.
  }

  private async enrichLevelRewards(levels: any[]) {
    const safeLevels = Array.isArray(levels) ? levels : [];
    const giftIds = Array.from(new Set(safeLevels.flatMap(item => {
      const ids = [
        ...(Array.isArray(item?.rewardGiftIds) ? item.rewardGiftIds : []),
        item?.rewardGiftId,
      ];
      return ids.map(id => String(id || '').trim()).filter(id => UUID_REGEX.test(id));
    })));
    const gifts = giftIds.length ? await this.dataSource.query(
      'SELECT id,name,"iconUrl","animationUrl","coinPrice" FROM gifts WHERE id::text = ANY($1::text[]) AND "isActive"=true AND category=$2',
      [giftIds, 'cp'],
    ) : [];
    const giftsById = new Map(gifts.map((gift: any) => [String(gift.id), gift]));
    const cosmeticCodes = Array.from(new Set(safeLevels.flatMap(item => [
      item?.rewardCosmeticCode,
      ...(Array.isArray(item?.rewardCosmeticCodes) ? item.rewardCosmeticCodes : []),
      ...(Array.isArray(item?.benefits) ? item.benefits : []).map((entry: any) => entry?.cosmeticCode || entry?.code),
      ...(Array.isArray(item?.rewards) ? item.rewards : []).map((entry: any) => entry?.cosmeticCode || entry?.code),
    ]).map(code => String(code || '').trim()).filter(code => /^[a-zA-Z0-9_-]{2,64}$/.test(code))));
    const cosmetics = cosmeticCodes.length
      ? (await this.cosmetics.adminListCpStore())
          .filter(item => item.isActive && cosmeticCodes.includes(item.code))
          .map(item => ({ code: item.code, name: item.name, type: item.type, previewUrl: item.previewUrl, animationUrl: item.animationUrl }))
      : [];
    const cosmeticsByCode = new Map(cosmetics.map((item: any) => [String(item.code), item]));
    return safeLevels.map(item => {
      const rewardGiftIds = Array.from(new Set([
        ...(Array.isArray(item?.rewardGiftIds) ? item.rewardGiftIds : []),
        item?.rewardGiftId,
      ].map(id => String(id || '').trim()).filter(id => UUID_REGEX.test(id))));
      const rewardGifts = rewardGiftIds.map(id => giftsById.get(id)).filter(Boolean);
      // Keep the single primary reward separate from the additional reward list.
      // Otherwise changing the primary reward in the dashboard leaves the old code
      // inside rewardCosmeticCodes and silently grants it as an extra reward forever.
      const rewardCosmeticCodes = Array.from(new Set([
        ...(Array.isArray(item?.rewardCosmeticCodes) ? item.rewardCosmeticCodes : []),
        ...(Array.isArray(item?.benefits) ? item.benefits : []).map((entry: any) => entry?.cosmeticCode || entry?.code),
        ...(Array.isArray(item?.rewards) ? item.rewards : []).map((entry: any) => entry?.cosmeticCode || entry?.code),
      ].map(code => String(code || '').trim()).filter(code => /^[a-zA-Z0-9_-]{2,64}$/.test(code))));
      const allCosmeticCodes = Array.from(new Set([
        item?.rewardCosmeticCode,
        ...rewardCosmeticCodes,
      ].map(code => String(code || '').trim()).filter(code => /^[a-zA-Z0-9_-]{2,64}$/.test(code))));
      const rewardCosmetics = allCosmeticCodes.map(code => cosmeticsByCode.get(code)).filter(Boolean);
      return {
        ...item,
        rewardGiftIds,
        rewardGifts,
        rewardCosmeticCodes,
        rewardCosmetics,
        // Backward compatibility for older Android builds that read one gift.
        rewardGift: rewardGifts[0] || giftsById.get(String(item?.rewardGiftId || '')) || null,
      };
    });
  }

  private async grantLevelGiftRewards(
    relationshipId: string,
    level: number,
    userIds: string[],
    levelConfig: Record<string, any> | null,
  ) {
    if (!levelConfig) return;
    const giftIds = Array.from(new Set([
      ...(Array.isArray(levelConfig.rewardGiftIds) ? levelConfig.rewardGiftIds : []),
      levelConfig.rewardGiftId,
      levelConfig.giftId,
    ].map(id => String(id || '').trim()).filter(id => UUID_REGEX.test(id))));
    if (!giftIds.length) return;
    for (const userId of userIds) {
      for (const giftId of giftIds) {
        await this.dataSource.transaction(async manager => {
          const [claim] = await manager.query(
            `INSERT INTO cp_level_gift_reward_claims ("relationshipId","userId",level,"giftId",status)
             VALUES ($1,$2,$3,$4,'pending')
             ON CONFLICT ("relationshipId",level,"userId","giftId") DO NOTHING
             RETURNING id`,
            [relationshipId, userId, level, giftId],
          );
          if (!claim?.id) return;
          const [gift] = await manager.query(
            'SELECT id,name,"iconUrl" FROM gifts WHERE id=$1 AND "isActive"=true AND category=$2 LIMIT 1',
            [giftId, 'cp'],
          );
          if (!gift) {
            await manager.query('DELETE FROM cp_level_gift_reward_claims WHERE id=$1', [claim.id]);
            return;
          }
          // Mirror other free system gift grants (e.g. Support Chest): persist a
          // zero-cost self-delivered gift history row so it appears in the user's bag/history.
          await manager.query(
            `INSERT INTO gift_sends
              ("giftId","giftName","giftIconUrl","senderId","receiverId","roomId",quantity,"comboCount","totalCoins","diamondsAwarded","luckyMultiplier")
             VALUES ($1,$2,$3,$4,$4,NULL,1,1,0,0,NULL)`,
            [gift.id, gift.name, gift.iconUrl || null, userId],
          );
          await manager.query(
            'UPDATE cp_level_gift_reward_claims SET status=$1,"grantedAt"=now() WHERE id=$2',
            ['granted', claim.id],
          );
        });
      }
    }
  }

  private async grantLevelCosmeticRewards(
    relationshipId: string,
    level: number,
    userIds: string[],
    levelConfig: Record<string, any> | null,
  ) {
    if (!levelConfig) return;
    const benefits = [
      ...(Array.isArray(levelConfig.benefits) ? levelConfig.benefits : []),
      ...(Array.isArray(levelConfig.rewards) ? levelConfig.rewards : []),
    ];
    const candidates = [
      ...(typeof levelConfig.rewardCosmeticCode === 'string' ? [{ code: levelConfig.rewardCosmeticCode, days: levelConfig.rewardDays, permanent: levelConfig.rewardPermanent }] : []),
      ...(Array.isArray(levelConfig.rewardCosmeticCodes) ? levelConfig.rewardCosmeticCodes.map((code: string) => ({ code, days: levelConfig.rewardDays, permanent: levelConfig.rewardPermanent })) : []),
      ...benefits.filter((item: any) => item && typeof item === 'object' && typeof (item.cosmeticCode || item.code) === 'string')
        .map((item: any) => ({ code: item.cosmeticCode || item.code, days: item.days, permanent: item.permanent })),
    ];
    const unique = Array.from(new Map(candidates.map(item => [String(item.code).trim(), item])).values())
      .filter(item => /^[a-zA-Z0-9_-]{2,64}$/.test(String(item.code || '').trim()));
    if (!unique.length) return;
    const codes = unique.map(item => String(item.code).trim());
    const available = codes.length
      ? (await this.cosmetics.adminListCpStore())
          .filter(item => item.isActive && codes.includes(item.code))
          .map(item => ({ id: item.id, code: item.code }))
      : [];
    const availableCodes = new Set(available.map((item: any) => String(item.code)));
    for (const userId of userIds) {
      for (const reward of unique) {
        const code = String(reward.code).trim();
        if (!availableCodes.has(code)) continue;
        const [claim] = await this.dataSource.query(
          `INSERT INTO cp_level_reward_claims ("relationshipId","userId",level,"cosmeticCode",status)
           VALUES ($1,$2,$3,$4,'pending')
           ON CONFLICT ("relationshipId",level,"userId","cosmeticCode") DO NOTHING
           RETURNING id`,
          [relationshipId, userId, level, code],
        );
        if (!claim?.id) continue;
        try {
          const days = Math.max(1, Math.min(365, Math.floor(Number(reward.days || levelConfig.rewardDays) || 30)));
          await this.cosmetics.grantTemporary(userId, code, days, { permanent: reward.permanent === true || levelConfig.rewardPermanent === true });
          await this.dataSource.query(
            'UPDATE cp_level_reward_claims SET status=$1,"grantedAt"=now() WHERE id=$2',
            ['granted', claim.id],
          );
        } catch (error) {
          await this.dataSource.query('DELETE FROM cp_level_reward_claims WHERE id=$1', [claim.id]).catch(() => undefined);
          // A cosmetic reward must never roll back the already-earned relationship points.
          console.warn('[CP] level cosmetic reward delivery failed', code, error);
        }
      }

    }
  }

  async catalog() {
    const rows = await this.dataSource.query(
      `SELECT key,value FROM app_settings WHERE key LIKE 'cp.%' ORDER BY key`,
    );
    const settings: Record<string, string> = Object.fromEntries(rows.map((row: any) => [row.key, String(row.value)]));
    const globallyEnabled = settings['cp.enabled.global'] !== 'false';
    const catalog: Array<Record<string, unknown>> = [];
    const giftTabEnabled = globallyEnabled && settings['cp.gift_tab.enabled'] !== 'false';
    for (const type of [CpType.RELATIONSHIP]) {
      const giftId = settings[`cp.gift.${type}`] || null;
      const [gift] = giftId ? await this.dataSource.query(
        'SELECT id,name,"iconUrl","animationUrl","coinPrice" FROM gifts WHERE id=$1 AND "isActive"=true AND category=$2',
        [giftId, 'cp'],
      ) : [];
      // The CP dashboard price is the live source of truth for the CP store.
      // Legacy installations without cp.price.<type> continue using the selected gift's price.
      const configuredPrice = settings[`cp.price.${type}`] === undefined
        ? Number(gift?.coinPrice)
        : Number(settings[`cp.price.${type}`]);
      const giftPrice = Number.isSafeInteger(configuredPrice) && configuredPrice > 0 ? configuredPrice : 0;
      const enabled = globallyEnabled && settings[`cp.enabled.${type}`] !== 'false';
      let rules: Record<string, any> = {};
      try { rules = parseJsonSetting(settings[`cp.rules.${type}`], {}); } catch { rules = {}; }
      const effect = rules?.seatEffect && typeof rules.seatEffect === 'object' ? rules.seatEffect : {};
      let levels: Array<Record<string, any>> = [];
      try {
        const parsedLevels = parseJsonSetting(settings[`cp.levels.${type}`], []);
        if (Array.isArray(parsedLevels)) levels = parsedLevels
          .filter(item => item && Number.isInteger(Number(item.level)) && Number(item.level) >= 1)
          .sort((a, b) => Number(a.level) - Number(b.level));
      } catch { levels = []; }
      levels = await this.enrichLevelRewards(levels);
      catalog.push({
        type, enabled, priceCoins: enabled && giftPrice > 0 ? giftPrice : null,
        maxLevel: levels.length,
        levels,
        gift: gift ? { id: gift.id, name: gift.name, iconUrl: gift.iconUrl, animationUrl: gift.animationUrl, coinPrice: giftPrice } : null,
        giftPoints: Number(settings[`cp.gift_points.${type}`] || 0) || 0,
        seatEffect: {
          enabled: effect.enabled !== false,
          url: typeof effect.url === 'string' ? effect.url : '',
          sizeDp: Math.max(10, Math.min(36, Number(effect.sizeDp) || 18)),
          durationMs: Math.max(500, Math.min(10000, Number(effect.durationMs) || 1800)),
        },
      });
    }
    // Metadata row keeps the setting available even when no CP type is configured.
    catalog.push({ type: '_settings', enabled: false, giftTabEnabled });
    return catalog;
  }

  async leaderboard(period: 'weekly' | 'world' = 'weekly') {
    if (period === 'world') return this.discover();
    if (period !== 'weekly') throw new BadRequestException('فترة ترتيب CP غير صالحة');
    const rows = await this.dataSource.query(
      `WITH weekly_totals AS (
         SELECT r.id, r.type, r.points::text AS points, r.level, r."startedAt",
           COALESCE(SUM(e.points::numeric), 0) AS weekly_points,
           json_build_object('id', a.id, 'username', a.username, 'displayName', a."displayName", 'avatarUrl', a."avatarUrl", 'level', a.level, 'nameColor', pa."nameColor") AS "userA",
           json_build_object('id', b.id, 'username', b.username, 'displayName', b."displayName", 'avatarUrl', b."avatarUrl", 'level', b.level, 'nameColor', pb."nameColor") AS "userB"
         FROM cp_relationships r
         JOIN users a ON a.id = r."userAId"
         JOIN users b ON b.id = r."userBId"
         LEFT JOIN user_profiles pa ON pa."userId" = a.id
         LEFT JOIN user_profiles pb ON pb."userId" = b.id
         LEFT JOIN cp_point_events e ON e."relationshipId" = r.id
           AND e."createdAt" >= date_trunc('week', now())
         WHERE r.status = 'active'
         GROUP BY r.id, a.id, b.id, pa."nameColor", pb."nameColor"
       ), ranked AS (
         SELECT weekly_totals.*,
           ROW_NUMBER() OVER (ORDER BY weekly_points DESC, points::numeric DESC, "startedAt" ASC) AS weekly_rank
         FROM weekly_totals
       )
       SELECT id, type, points, level, "startedAt",
         GREATEST(0, FLOOR(EXTRACT(EPOCH FROM (now() - "startedAt")) / 86400))::int AS "durationDays",
         weekly_points::text AS "weeklyPoints",
         weekly_rank::int AS "weeklyRank",
         (weekly_rank = 1 AND weekly_points > 0) AS "isWeeklyChampion",
         (date_trunc('week', now()) + interval '1 week') AS "championUntil",
         "userA", "userB"
       FROM ranked
       ORDER BY weekly_rank
       LIMIT 100`,
    );
    // The weekly ranking uses the same dashboard-controlled level artwork and
    // names as the worldwide ranking and the rewards catalog.
    const [levelSetting] = await this.dataSource.query(
      'SELECT value FROM app_settings WHERE key=$1',
      [`cp.levels.${CpType.RELATIONSHIP}`],
    );
    const parsedLevels = parseJsonSetting(levelSetting?.value, []);
    const levels = await this.enrichLevelRewards(Array.isArray(parsedLevels) ? parsedLevels : []);
    const levelsByNumber = new Map(levels.map(level => [Number(level.level), level]));
    return rows.map((row: any) => {
      const config = levelsByNumber.get(Number(row.level));
      return {
        ...row,
        levelName: config?.name || config?.title || config?.presentation?.title || null,
        presentation: config?.presentation || null,
      };
    });
  }

  async rewards() {
    const rows = await this.dataSource.query(
      `SELECT key, value FROM app_settings WHERE key LIKE 'cp.levels.%' ORDER BY key`,
    );
    const configs: Array<{ type: string; levels: any[] }> = [];
    for (const row of rows) {
      const type = String(row.key).slice('cp.levels.'.length);
      if (type !== CpType.RELATIONSHIP) continue;
      let levels: any[] = [];
      try {
        const parsed = parseJsonSetting(row.value, []);
        if (Array.isArray(parsed)) levels = parsed
          .filter(item => item && Number.isFinite(Number(item.level)) && Number(item.level) >= 1)
          .sort((a, b) => Number(a.level) - Number(b.level));
      } catch { levels = []; }
      configs.push({ type, levels: await this.enrichLevelRewards(levels) });
    }
    return configs;
  }

  async discover(type?: CpType) {
    if (type && !TYPES.has(type)) throw new BadRequestException('نوع CP غير صالح');
    const where = type ? 'AND r.type = $1' : '';
    const args = type ? [type] : [];
    const rows = await this.dataSource.query(
      `SELECT r.id, r.type, r.points::text AS points, r.level, r."startedAt",
        json_build_object('id', a.id, 'username', a.username, 'displayName', a."displayName", 'avatarUrl', a."avatarUrl", 'level', a.level,
          'vipBadgeUrl', pa."vipBadgeUrl", 'hostBadgeUrl', pa."hostBadgeUrl", 'levelBadgeUrl', pa."levelBadgeUrl", 'nameColor', pa."nameColor") AS "userA",
        json_build_object('id', b.id, 'username', b.username, 'displayName', b."displayName", 'avatarUrl', b."avatarUrl", 'level', b.level,
          'vipBadgeUrl', pb."vipBadgeUrl", 'hostBadgeUrl', pb."hostBadgeUrl", 'levelBadgeUrl', pb."levelBadgeUrl", 'nameColor', pb."nameColor") AS "userB"
       FROM cp_relationships r
       JOIN users a ON a.id = r."userAId" JOIN users b ON b.id = r."userBId"
       LEFT JOIN user_profiles pa ON pa."userId" = a.id
       LEFT JOIN user_profiles pb ON pb."userId" = b.id
       WHERE r.status = 'active' ${where}
       ORDER BY r.points DESC, r."startedAt" ASC LIMIT 100`,
      args,
    );
    const types: string[] = Array.from(new Set<string>(rows.map((row: any) => String(row.type))));
    // Dashboard-managed CP level JSON includes optional presentation, rewards and gift metadata.
    const configs: Record<string, any[]> = {};
    for (const itemType of types) {
      const [setting] = await this.dataSource.query(
        'SELECT value FROM app_settings WHERE key=$1',
        [`cp.levels.${itemType}`],
      );
      try {
        const parsed = parseJsonSetting(setting?.value, []);
        configs[itemType] = await this.enrichLevelRewards(Array.isArray(parsed) ? parsed : []);
      } catch { configs[itemType] = []; }
    }
    return rows.map((row: any) => {
      const points = BigInt(String(row.points || 0));
      const levels = (configs[row.type] || []).slice().sort((a, b) => Number(a.level) - Number(b.level));
      const currentThreshold = levels.filter(item => Number(item.level) <= Number(row.level)).reduce((max, item) => Math.max(max, Number(item.pointsRequired) || 0), 0);
      const next = levels.find(item => Number(item.level) > Number(row.level));
      const nextThreshold = next ? BigInt(Math.max(0, Number(next.pointsRequired) || 0)) : null;
      const span = nextThreshold === null ? 0n : (nextThreshold - BigInt(currentThreshold));
      const progressed = points > BigInt(currentThreshold) ? points - BigInt(currentThreshold) : 0n;
      const progressPercent = span > 0n ? Math.max(0, Math.min(100, Number((progressed * 10000n) / span) / 100)) : 100;
      const startedAt = new Date(row.startedAt);
      const durationDays = Number.isNaN(startedAt.getTime()) ? 0 : Math.max(0, Math.floor((Date.now() - startedAt.getTime()) / 86400000));
      const unlockedLevels = levels.filter(item => Number(item.level) <= Number(row.level));
      const rewardsUnlocked = unlockedLevels
        // A level with only a selected gift/cosmetic is still a reward level and
        // must be returned to the APK even when it has no separate presentation block.
        .filter(item => item.reward || item.rewards || item.benefits || item.presentation ||
          item.giftPresentation || item.rewardGiftId || item.rewardCosmeticCode || item.rewardCosmeticCodes?.length)
        .map(item => ({
          ...item,
          level: Number(item.level),
          reward: item.reward ?? null,
          benefits: item.benefits ?? item.rewards ?? [],
          rewardGift: item.rewardGift ?? null,
          rewardCosmeticCode: item.rewardCosmeticCode ?? null,
          rewardCosmeticCodes: Array.isArray(item.rewardCosmeticCodes) ? item.rewardCosmeticCodes : [],
          presentation: item.presentation ?? null,
          giftPresentation: item.giftPresentation ?? null,
          giftBonusPercent: Number(item.giftBonusPercent || 0) || 0,
        }));
      const currentLevelConfig = levels.find(item => Number(item.level) === Number(row.level)) || null;
      return {
        ...row,
        currentLevelConfig,
        unlockedLevels,
        durationDays,
        nextLevel: next ? Number(next.level) : null,
        nextLevelPoints: nextThreshold === null ? null : nextThreshold.toString(),
        progressPercent,
        rewardsUnlocked,
      };
    });
  }

  async roomSeatEffects(userIdsInput: string) {
    const userIds = Array.from(new Set(
      String(userIdsInput || '').split(',').map((id) => id.trim()).filter((id) => UUID_REGEX.test(id)),
    )).slice(0, 20);
    if (userIds.length < 2) return { relationships: [], effects: {} };

    const rows = await this.dataSource.query(
      `SELECT id, type, "userAId", "userBId", level, points::text AS points
       FROM cp_relationships
       WHERE status = 'active'
         AND "userAId" = ANY($1::uuid[])
         AND "userBId" = ANY($1::uuid[])
       ORDER BY "startedAt" ASC`,
      [userIds],
    );
    if (!rows.length) return { relationships: [], effects: {} };

    const types = Array.from(new Set<string>(rows.map((row: any) => String(row.type))));
    const effects: Record<string, any> = {};
    const levelConfigs: Record<string, any[]> = {};
    for (const type of types) {
      const [setting, levelSetting] = await Promise.all([
        this.dataSource.query('SELECT value FROM app_settings WHERE key=$1', [`cp.rules.${type}`]).then(rows => rows[0]),
        this.dataSource.query('SELECT value FROM app_settings WHERE key=$1', [`cp.levels.${type}`]).then(rows => rows[0]),
      ]);
      let rules: Record<string, any> = {};
      try { rules = parseJsonSetting(setting?.value, {}); } catch { rules = {}; }
      try {
        const parsed = parseJsonSetting(levelSetting?.value, []);
        levelConfigs[type] = await this.enrichLevelRewards(Array.isArray(parsed) ? parsed : []);
      } catch { levelConfigs[type] = []; }
      const effect = rules?.seatEffect && typeof rules.seatEffect === 'object' ? rules.seatEffect : {};
      effects[type] = {
        enabled: effect.enabled !== false,
        url: typeof effect.url === 'string' ? effect.url : '',
        sizeDp: Math.max(24, Math.min(160, Number(effect.sizeDp) || 56)),
        durationMs: Math.max(500, Math.min(10000, Number(effect.durationMs) || 1800)),
      };
    }
    return {
      relationships: rows.map((row: any) => {
        const currentLevelConfig = (levelConfigs[row.type] || []).find(item => Number(item?.level) === Number(row.level)) || null;
        return {
          id: row.id, type: row.type, userAId: row.userAId, userBId: row.userBId,
          points: row.points, level: row.level, currentLevelConfig,
          levelName: currentLevelConfig?.name || currentLevelConfig?.title || currentLevelConfig?.presentation?.title || null,
          presentation: currentLevelConfig?.presentation || null,
          benefits: currentLevelConfig?.benefits || currentLevelConfig?.rewards || [],
          giftPresentation: currentLevelConfig?.giftPresentation || null,
          giftBonusPercent: Math.max(0, Math.min(500, Number(currentLevelConfig?.giftBonusPercent) || 0)),
        };
      }),
      effects,
    };
  }



  async tasks(userId: string) {
    const [globalSetting] = await this.dataSource.query('SELECT value FROM app_settings WHERE key=$1', ['cp.enabled.global']);
    if (String(globalSetting?.value || 'true').toLowerCase() === 'false') return [];
    const [relationship] = await this.dataSource.query(
      `SELECT id,points::text AS points,level,type,"userAId","userBId" FROM cp_relationships
       WHERE status='active' AND ("userAId"=$1 OR "userBId"=$1)
       ORDER BY "startedAt" DESC LIMIT 1`,
      [userId],
    );
    if (!relationship) return [];
    const [dayRow] = await this.dataSource.query(`SELECT to_char(now(),'YYYY-MM-DD') AS day`);
    const day = String(dayRow?.day || new Date().toISOString().slice(0, 10));
    const [memoryCount] = await this.dataSource.query(
      `SELECT COUNT(*)::int AS count FROM cp_memories
       WHERE "relationshipId"=$1 AND "createdAt">=date_trunc('day',now())`,
      [relationship.id],
    );
    const [pointsRow] = await this.dataSource.query(
      `SELECT COALESCE(SUM(points::numeric),0)::text AS points
       FROM cp_point_events WHERE "relationshipId"=$1
         AND "createdAt">=date_trunc('day',now()) AND "eventType"<>'cp_task_reward'`,
      [relationship.id],
    );
    const claimedRows = await this.dataSource.query(
      `SELECT "referenceId" FROM cp_point_events
       WHERE "referenceType"='cp_task_reward' AND "referenceId" LIKE $1`,
      [`${relationship.id}:${userId}:%:${day}`],
    );
    const claimed = new Set(claimedRows.map((row: any) => String(row.referenceId)));
    const pointsToday = Math.max(0, Number(pointsRow?.points) || 0);
    const [giftExchangeCount] = await this.dataSource.query(
      `SELECT COUNT(DISTINCT metadata->>'senderId')::int AS count
       FROM cp_point_events
       WHERE "relationshipId"=$1 AND "createdAt">=date_trunc('day',now())
         AND "eventType"='gift_send'
         AND metadata->>'senderId'=ANY($2::text[])`,
      [relationship.id, [relationship.userAId, relationship.userBId]],
    );
    const [taskConfigRow] = await this.dataSource.query('SELECT value FROM app_settings WHERE key=$1', ['cp.tasks.config']);
    let taskConfig: Record<string, any> = {};
    try { taskConfig = parseJsonSetting(taskConfigRow?.value, {}); } catch { taskConfig = {}; }
    const defaults: Record<string, any> = {
      memory_today: { title: 'ذكرى جديدة', description: 'أضيفوا ذكرى مشتركة اليوم', condition: 'memory_count', target: 1, rewardPoints: 10 },
      earn_points_today: { title: 'نشاط مشترك', description: 'اجمعوا نقاط العلاقة اليوم', condition: 'points_today', target: 10, rewardPoints: 15 },
    };
    const taskIds = new Set([...Object.keys(defaults), ...Object.keys(taskConfig)]);
    const definitions = Array.from(taskIds)
      .filter(id => taskConfig[id]?.enabled !== false)
      .filter(id => /^[a-zA-Z0-9_-]{2,64}$/.test(id))
      .map(id => {
        const base = defaults[id] || { title: 'مهمة CP', description: 'أكملوا شرط المهمة اليوم', condition: 'points_today', target: 10, rewardPoints: 0 };
        const config = taskConfig[id] || {};
        const condition = ['memory_count', 'gift_exchange_today'].includes(String(config.condition || (id === 'memory_today' ? 'memory_count' : 'points_today'))) ? String(config.condition || 'memory_count') : 'points_today';
        const configuredTarget = Math.max(1, Math.min(1000000, Math.floor(Number(config.target ?? base.target) || base.target)));
        const target = condition === 'gift_exchange_today' ? Math.min(2, configuredTarget) : configuredTarget;
        const progressValue = condition === 'memory_count' ? Number(memoryCount?.count) || 0 : condition === 'gift_exchange_today' ? Number(giftExchangeCount?.count) || 0 : pointsToday;
        return {
          id,
          title: String(config.title || base.title).slice(0, 100),
          description: String(config.description || base.description).slice(0, 240),
          condition,
          target,
          rewardPoints: Math.max(0, Math.min(1000000, Math.floor(Number(config.rewardPoints ?? base.rewardPoints) || 0))),
          rewardCosmeticCode: typeof config.rewardCosmeticCode === 'string' ? config.rewardCosmeticCode : '',
          progress: Math.min(target, progressValue),
        };
      });
    const rewardCodes = definitions.map(task => String(task.rewardCosmeticCode || '').trim()).filter(Boolean);
    const rewardRows: Array<{ code: string; name: string; previewUrl: string; type: string }> = rewardCodes.length
      ? (await this.cosmetics.adminListCpStore())
          .filter(item => item.isActive && rewardCodes.includes(item.code))
          .map(item => ({ code: item.code, name: item.name, previewUrl: item.previewUrl, animationUrl: item.animationUrl, type: item.type }))
      : [];
    const rewardByCode = new Map(rewardRows.map(item => [item.code, item]));
    return definitions.map(task => {
      const referenceId = `${relationship.id}:${userId}:${task.id}:${day}`;
      const isClaimed = claimed.has(referenceId);
      const code = String(task.rewardCosmeticCode || '').trim();
      return {
        ...task,
        rewardItem: code ? (rewardByCode.get(code) || null) : null,
        progress: Number(task.progress),
        claimed: isClaimed,
        canClaim: !isClaimed && task.progress >= task.target,
      };
    });
  }

  async claimTask(userId: string, taskId: string) {
    const [globalSetting] = await this.dataSource.query('SELECT value FROM app_settings WHERE key=$1', ['cp.enabled.global']);
    if (String(globalSetting?.value || 'true').toLowerCase() === 'false') {
      throw new BadRequestException('قسم CP متوقف حالياً من لوحة التحكم');
    }
    if (!/^[a-zA-Z0-9_-]{2,64}$/.test(taskId)) throw new BadRequestException('معرّف مهمة CP غير صالح');
    const [taskConfigRow] = await this.dataSource.query('SELECT value FROM app_settings WHERE key=$1', ['cp.tasks.config']);
    let taskConfig: Record<string, any> = {};
    try { taskConfig = parseJsonSetting(taskConfigRow?.value, {}); } catch { taskConfig = {}; }
    const builtInIds = ['memory_today', 'earn_points_today'];
    if (!builtInIds.includes(taskId) && !Object.prototype.hasOwnProperty.call(taskConfig, taskId)) {
      throw new BadRequestException('مهمة CP غير معروفة');
    }
    if (taskConfig[taskId]?.enabled === false) throw new BadRequestException('هذه المهمة متوقفة من لوحة التحكم');
    const baseTask = taskId === 'memory_today'
      ? { condition: 'memory_count', target: 1, rewardPoints: 10 }
      : taskId === 'earn_points_today'
        ? { condition: 'points_today', target: 10, rewardPoints: 15 }
        : { condition: 'points_today', target: 10, rewardPoints: 0 };
    const taskDefinition = { ...baseTask, ...(taskConfig[taskId] || {}) };
    const taskCondition = ['memory_count', 'gift_exchange_today'].includes(String(taskDefinition.condition || (taskId === 'memory_today' ? 'memory_count' : 'points_today')))
      ? String(taskDefinition.condition || 'memory_count') : 'points_today';
    const configuredTarget = Math.max(1, Math.min(1000000, Math.floor(Number(taskDefinition.target) || baseTask.target)));
    const target = taskCondition === 'gift_exchange_today' ? Math.min(2, configuredTarget) : configuredTarget;
    const configuredRewardPoints = Math.max(0, Math.min(1000000, Math.floor(Number(taskDefinition.rewardPoints ?? baseTask.rewardPoints) || 0)));
    const [relationship] = await this.dataSource.query(
      `SELECT id,points::text AS points,level,type,"userAId","userBId" FROM cp_relationships
       WHERE status='active' AND ("userAId"=$1 OR "userBId"=$1)
       ORDER BY "startedAt" DESC LIMIT 1`,
      [userId],
    );
    if (!relationship) throw new BadRequestException('تحتاج إلى علاقة CP نشطة');
    const [dayRow] = await this.dataSource.query(`SELECT to_char(now(),'YYYY-MM-DD') AS day`);
    const day = String(dayRow?.day || new Date().toISOString().slice(0, 10));
    let eligible = false;
    if (taskCondition === 'memory_count') {
      const [row] = await this.dataSource.query(
        'SELECT COUNT(*)::int AS count FROM cp_memories WHERE "relationshipId"=$1 AND "createdAt">=date_trunc($2,now())',
        [relationship.id, 'day'],
      );
      eligible = Number(row?.count) >= target;
    } else if (taskCondition === 'gift_exchange_today') {
      const [row] = await this.dataSource.query(
        `SELECT COUNT(DISTINCT metadata->>'senderId')::int AS count
         FROM cp_point_events
         WHERE "relationshipId"=$1 AND "createdAt">=date_trunc('day',now())
           AND "eventType"='gift_send'
           AND metadata->>'senderId'=ANY($2::text[])`,
        [relationship.id, [relationship.userAId, relationship.userBId]],
      );
      eligible = Number(row?.count) >= target;
    } else {
      const [row] = await this.dataSource.query(
        'SELECT COALESCE(SUM(points::numeric),0)::text AS points FROM cp_point_events WHERE "relationshipId"=$1 AND "createdAt">=date_trunc($2,now()) AND "eventType"<>$3',
        [relationship.id, 'day', 'cp_task_reward'],
      );
      eligible = Number(row?.points) >= target;
    }
    const rewardPoints = configuredRewardPoints;
    if (!eligible) throw new BadRequestException('أكملوا شرط المهمة أولاً');
    const referenceId = `${relationship.id}:${userId}:${taskId}:${day}`;
    const configuredCode = String(taskConfig[taskId]?.rewardCosmeticCode || '').trim();
    const result = await this.recordPoints(
      relationship.id, 'cp_task_reward', 'cp_task_reward', referenceId, rewardPoints,
      { taskId, userId, day, rewardCosmeticCode: configuredCode || null },
    );
    let rewardItem: Record<string, unknown> | null = null;
    if (configuredCode) {
      const cosmetic = (await this.cosmetics.adminListCpStore())
        .find(item => item.isActive && item.code === configuredCode);
      if (cosmetic) {
        const [owned] = await this.dataSource.query(
          'SELECT id FROM user_cosmetics WHERE "userId"=$1 AND "cosmeticId"=$2 LIMIT 1',
          [userId, cosmetic.id],
        );
        // A retry may arrive after the points ledger committed but before the
        // cosmetic grant completed. Re-apply the idempotent permanent grant.
        await this.cosmetics.grantTemporary(userId, configuredCode, 365, { permanent: true });
        rewardItem = {
          code: cosmetic.code, name: cosmetic.name, type: cosmetic.type,
          previewUrl: cosmetic.previewUrl, animationUrl: cosmetic.animationUrl,
          alreadyOwned: !!owned,
        };
      }
    }
    const taskTitle = String(taskDefinition.title || taskId).slice(0, 100);
    const taskDescription = String(taskDefinition.description || '').slice(0, 240);
    if (!(result as any).duplicate) {
      await this.notifications.notifyUser(
        userId,
        NotificationType.SYSTEM,
        'مكافأة CP وصلت إليك!',
        rewardItem?.name
          ? `أكملت مهمة «${taskTitle}» واستلمت ${String(rewardItem.name)}.`
          : `أكملت مهمة «${taskTitle}» واستلمت ${rewardPoints} نقطة CP.`,
        {
          source: 'cp_task_reward', taskId, taskTitle, taskDescription,
          rewardPoints, rewardItem,
          imageUrl: (rewardItem as any)?.animationUrl || (rewardItem as any)?.previewUrl || null,
        },
      ).catch(error => console.warn('[CP] task reward notification delivery failed', taskId, error));
    }
    return {
      taskId, taskTitle, taskDescription, claimed: true,
      duplicate: !!(result as any).duplicate, rewardPoints, rewardItem,
      points: (result as any).points, level: (result as any).level,
    };
  }

  async memories(userId: string) {
    const relationships = await this.dataSource.query(
      `SELECT id FROM cp_relationships
       WHERE status='active' AND ("userAId"=$1 OR "userBId"=$1)
       ORDER BY "startedAt" DESC LIMIT 1`,
      [userId],
    );
    if (!relationships.length) return [];
    return this.dataSource.query(
      `SELECT m.id,m.text,m."imageUrl",m."createdAt",m."userId",
        json_build_object('id',u.id,'username',u.username,'displayName',u."displayName",'avatarUrl',u."avatarUrl") AS author
       FROM cp_memories m JOIN users u ON u.id=m."userId"
       WHERE m."relationshipId"=$1
       ORDER BY m."createdAt" DESC LIMIT 100`,
      [relationships[0].id],
    );
  }

  async createMemory(userId: string, input: { text?: string; imageUrl?: string }) {
    const text = String(input?.text || '').trim();
    const imageUrl = String(input?.imageUrl || '').trim();
    if ((!text && !imageUrl) || text.length > 500 || imageUrl.length > 2048) {
      throw new BadRequestException('أضف نصاً أو صورة، واجعل النص أقل من 500 حرف');
    }
    if (imageUrl && !/^https?:\/\/\S+$/i.test(imageUrl)) {
      throw new BadRequestException('رابط الصورة غير صالح');
    }
    const relationships = await this.dataSource.query(
      `SELECT id FROM cp_relationships
       WHERE status='active' AND ("userAId"=$1 OR "userBId"=$1)
       ORDER BY "startedAt" DESC LIMIT 1`,
      [userId],
    );
    if (!relationships.length) throw new BadRequestException('تحتاج إلى علاقة CP نشطة لإضافة ذكرى');
    const [memory] = await this.dataSource.query(
      `INSERT INTO cp_memories ("relationshipId","userId",text,"imageUrl","createdAt")
       VALUES ($1,$2,$3,$4,now())
       RETURNING id,text,"imageUrl","createdAt","userId"`,
      [relationships[0].id, userId, text, imageUrl || null],
    );
    return memory;
  }

  async deleteMemory(userId: string, memoryId: string) {
    const result = await this.dataSource.query(
      `DELETE FROM cp_memories m USING cp_relationships r
       WHERE m.id=$1 AND m."userId"=$2 AND r.id=m."relationshipId"
         AND r.status='active' AND (r."userAId"=$2 OR r."userBId"=$2)
       RETURNING m.id`,
      [memoryId, userId],
    );
    if (!result.length) throw new NotFoundException('الذكرى غير موجودة أو لا تملك صلاحية حذفها');
    return { id: memoryId, deleted: true };
  }

  async profileRelationships(userId: string) {
    const rows = await this.dataSource.query(
      `SELECT r.id,r.type,r.points::text AS points,r.level,r."startedAt",
       COALESCE((SELECT value::jsonb ->> 'cardBackgroundUrl' FROM app_settings WHERE key = 'cp.rules.' || r.type), '') AS "cardBackgroundUrl",
       json_build_object('id',p.id,'username',p.username,'displayName',p."displayName",'avatarUrl',p."avatarUrl",'level',p.level,'nameColor',pp."nameColor",
         'vipBadgeUrl',pp."vipBadgeUrl",'hostBadgeUrl',pp."hostBadgeUrl",'levelBadgeUrl',pp."levelBadgeUrl",
         'cpBackgroundUrl',CASE WHEN EXISTS (SELECT 1 FROM user_cosmetics ucp JOIN cosmetics ccp ON ccp.id=ucp."cosmeticId" WHERE ucp."userId"=p.id AND ucp.equipped=true AND ccp.type='cp_background' AND (ucp."expiresAt" IS NULL OR ucp."expiresAt">NOW()) AND ccp."previewUrl"=pp."cpBackgroundUrl") THEN pp."cpBackgroundUrl" ELSE NULL END,
         'cpLogoUrl',CASE WHEN EXISTS (SELECT 1 FROM user_cosmetics ucp JOIN cosmetics ccp ON ccp.id=ucp."cosmeticId" WHERE ucp."userId"=p.id AND ucp.equipped=true AND ccp.type='cp_logo' AND (ucp."expiresAt" IS NULL OR ucp."expiresAt">NOW()) AND ccp."previewUrl"=pp."cpLogoUrl") THEN pp."cpLogoUrl" ELSE NULL END) AS partner
       FROM cp_relationships r
       JOIN users p ON p.id=CASE WHEN r."userAId"=$1 THEN r."userBId" ELSE r."userAId" END
       LEFT JOIN user_profiles pp ON pp."userId" = p.id
       WHERE (r."userAId"=$1 OR r."userBId"=$1) AND r.status='active'
       ORDER BY r.points DESC,r."startedAt" ASC`,
      [userId],
    );
    const configs: Record<string, any[]> = {};
    for (const type of Array.from(new Set<string>(rows.map((row: any) => String(row.type))))) {
      const [setting] = await this.dataSource.query(
        'SELECT value FROM app_settings WHERE key=$1',
        [`cp.levels.${type}`],
      );
      try {
        const parsed = parseJsonSetting(setting?.value, []);
        configs[type] = await this.enrichLevelRewards(Array.isArray(parsed) ? parsed : []);
      } catch { configs[type] = []; }
    }
    return rows.map((row: any) => {
      const levels = (configs[row.type] || []).slice().sort((a, b) => Number(a.level) - Number(b.level));
      const currentThreshold = levels.filter(item => Number(item.level) <= Number(row.level)).reduce((max, item) => Math.max(max, Number(item.pointsRequired) || 0), 0);
      const nextLevel = levels.find(item => Number(item.level) > Number(row.level));
      const nextThreshold = nextLevel ? Math.max(0, Number(nextLevel.pointsRequired) || 0) : null;
      const span = nextThreshold === null ? 0 : nextThreshold - currentThreshold;
      const progressed = Math.max(0, Number(row.points || 0) - currentThreshold);
      const progressPercent = span > 0
        ? Math.max(0, Math.min(100, progressed * 100 / span))
        : (levels.length > 0 ? 100 : 0);
      const currentLevelConfig = levels.find(item => Number(item?.level) === Number(row.level)) || null;
      const presentation = currentLevelConfig?.presentation || {};
      const startedAt = new Date(row.startedAt);
      const durationDays = Number.isNaN(startedAt.getTime()) ? 0 : Math.max(0, Math.floor((Date.now() - startedAt.getTime()) / 86400000));
      const durationYears = Math.floor(durationDays / 365);
      const durationMonths = Math.floor((durationDays % 365) / 30);
      const remainingDays = durationDays - durationYears * 365 - durationMonths * 30;
      const durationLabel = durationYears > 0
        ? `${durationYears} سنة ${durationMonths} شهر`
        : durationMonths > 0 ? `${durationMonths} شهر ${remainingDays} يوم` : `${durationDays} يوم`;
      return {
        ...row,
        currentLevelAt: currentThreshold,
        nextLevelAt: nextThreshold ?? 0,
        progressPercent,
        durationDays,
        durationLabel,
        duration: { days: durationDays, years: durationYears, months: durationMonths, remainingDays },
        partnerDisplayName: row.partner?.displayName || row.partner?.username || 'الشريك',
        partnerAvatarUrl: row.partner?.avatarUrl || null,
        currentLevelConfig,
        levelName: currentLevelConfig?.name || currentLevelConfig?.title || presentation?.title || null,
        levelLogoUrl: presentation?.levelLogoUrl || presentation?.logoUrl || currentLevelConfig?.logoUrl || null,
        levelBadgeUrl: presentation?.badgeUrl || null,
        profileTitle: presentation?.profileTitle || presentation?.title || currentLevelConfig?.title || null,
        presentation: currentLevelConfig?.presentation || null,
        benefits: currentLevelConfig?.benefits || currentLevelConfig?.rewards || [],
        giftPresentation: currentLevelConfig?.giftPresentation || null,
        giftBonusPercent: Math.max(0, Math.min(500, Number(currentLevelConfig?.giftBonusPercent) || 0)),
      };
    });
  }

  async forUser(userId: string) {
    // The CP space is the current relationship workspace, not a history list.
    // Ended relationships stay in audit data but are not returned to the UI.
    const rows = await this.dataSource.query(
      `SELECT r.*,
         json_build_object('id', me.id, 'username', me.username, 'displayName', me."displayName",
           'avatarUrl', me."avatarUrl", 'level', me.level, 'nameColor', mp."nameColor",
           'vipBadgeUrl', mp."vipBadgeUrl", 'hostBadgeUrl', mp."hostBadgeUrl", 'levelBadgeUrl', mp."levelBadgeUrl",
           'cpBackgroundUrl', CASE WHEN EXISTS (SELECT 1 FROM user_cosmetics ucp JOIN cosmetics ccp ON ccp.id=ucp."cosmeticId" WHERE ucp."userId"=me.id AND ucp.equipped=true AND ccp.type='cp_background' AND (ucp."expiresAt" IS NULL OR ucp."expiresAt">NOW()) AND ccp."previewUrl"=mp."cpBackgroundUrl") THEN mp."cpBackgroundUrl" ELSE NULL END,
           'cpLogoUrl', CASE WHEN EXISTS (SELECT 1 FROM user_cosmetics ucp JOIN cosmetics ccp ON ccp.id=ucp."cosmeticId" WHERE ucp."userId"=me.id AND ucp.equipped=true AND ccp.type='cp_logo' AND (ucp."expiresAt" IS NULL OR ucp."expiresAt">NOW()) AND ccp."previewUrl"=mp."cpLogoUrl") THEN mp."cpLogoUrl" ELSE NULL END) AS self,
         json_build_object('id', p.id, 'username', p.username, 'displayName', p."displayName",
           'avatarUrl', p."avatarUrl", 'level', p.level, 'nameColor', pp."nameColor",
           'vipBadgeUrl', pp."vipBadgeUrl", 'hostBadgeUrl', pp."hostBadgeUrl", 'levelBadgeUrl', pp."levelBadgeUrl",
           'cpBackgroundUrl', CASE WHEN EXISTS (SELECT 1 FROM user_cosmetics ucp JOIN cosmetics ccp ON ccp.id=ucp."cosmeticId" WHERE ucp."userId"=p.id AND ucp.equipped=true AND ccp.type='cp_background' AND (ucp."expiresAt" IS NULL OR ucp."expiresAt">NOW()) AND ccp."previewUrl"=pp."cpBackgroundUrl") THEN pp."cpBackgroundUrl" ELSE NULL END,
           'cpLogoUrl', CASE WHEN EXISTS (SELECT 1 FROM user_cosmetics ucp JOIN cosmetics ccp ON ccp.id=ucp."cosmeticId" WHERE ucp."userId"=p.id AND ucp.equipped=true AND ccp.type='cp_logo' AND (ucp."expiresAt" IS NULL OR ucp."expiresAt">NOW()) AND ccp."previewUrl"=pp."cpLogoUrl") THEN pp."cpLogoUrl" ELSE NULL END) AS partner
       FROM cp_relationships r
       JOIN users me ON me.id = $1
       JOIN users p ON p.id = CASE WHEN r."userAId" = $1 THEN r."userBId" ELSE r."userAId" END
       LEFT JOIN user_profiles mp ON mp."userId" = me.id
       LEFT JOIN user_profiles pp ON pp."userId" = p.id
       WHERE (r."userAId" = $1 OR r."userBId" = $1) AND r.status = 'active'
       ORDER BY r."startedAt" DESC LIMIT 1`,
      [userId],
    );
    if (!rows.length) return rows;
    const visuals = await this.profileRelationships(userId);
    const byId = new Map(visuals.map((item: any) => [String(item.id), item]));
    return rows.map((row: any) => {
      const visual: any = byId.get(String(row.id)) || {};
      return {
        ...row,
        durationDays: visual.durationDays ?? 0,
        durationLabel: visual.durationLabel || '0 يوم',
        duration: visual.duration || { days: 0, years: 0, months: 0, remainingDays: 0 },
        partnerDisplayName: visual.partnerDisplayName || row.partner?.displayName || row.partner?.username || 'الشريك',
        partnerAvatarUrl: visual.partnerAvatarUrl || row.partner?.avatarUrl || null,
        currentLevelConfig: visual.currentLevelConfig || null,
        currentLevelAt: Number(visual.currentLevelAt || 0),
        nextLevelAt: Number(visual.nextLevelAt || 0),
        progressPercent: Number(visual.progressPercent ?? 100),
        levelName: visual.levelName || null,
        levelLogoUrl: visual.levelLogoUrl || null,
        levelBadgeUrl: visual.levelBadgeUrl || null,
        profileTitle: visual.profileTitle || null,
        presentation: visual.presentation || null,
        benefits: visual.benefits || [],
        giftPresentation: visual.giftPresentation || null,
        giftBonusPercent: visual.giftBonusPercent || 0,
      };
    });
  }


  async createProfileRequest(_userId: string, _input: { recipientId: string; type: CpType; idempotencyKey: string }) {
    throw new BadRequestException('يتم بدء ارتباط CP حصراً بإرسال هدية ارتباط من داخل الروم الصوتي.');
  }

  private cpTypeLabel(type: CpType): string {
    switch (type) {
      case CpType.BROTHERHOOD: return 'الأخوّة';
      case CpType.FRIENDSHIP: return 'الصداقة';
      case CpType.LOVE: return 'الحب';
      case CpType.ENGAGEMENT: return 'الخطوبة';
      case CpType.MARRIAGE: return 'الزواج';
      default: return 'علاقة CP';
    }
  }

  async sentRequests(userId: string) {
    await this.expireDueRequests();
    return this.dataSource.query(
      `SELECT q.*, json_build_object('id',u.id,'username',u.username,'displayName',u."displayName",'avatarUrl',u."avatarUrl") AS recipient
       FROM cp_requests q JOIN users u ON u.id=q."toUserId"
       WHERE q."fromUserId"=$1 AND q.status IN ('pending','accepted','rejected','expired','cancelled')
       ORDER BY q."createdAt" DESC LIMIT 100`,
      [userId],
    );
  }

  async cancelRequest(userId: string, requestId: string) {
    return this.dataSource.transaction(async manager => {
      const [request] = await manager.query('SELECT * FROM cp_requests WHERE id=$1 FOR UPDATE', [requestId]);
      if (!request) throw new NotFoundException('طلب CP غير موجود');
      if (request.fromUserId !== userId) throw new BadRequestException('لا يمكنك إلغاء طلب ليس لك');
      if (request.status !== CpRequestStatus.PENDING) throw new ConflictException('لا يمكن إلغاء هذا الطلب بعد معالجته');
      const expired = new Date(request.expiresAt).getTime() <= Date.now();
      await this.refund(manager, request);
      const status = expired ? 'expired' : 'cancelled';
      await manager.query('UPDATE cp_requests SET status=$2,"respondedAt"=now(),"updatedAt"=now() WHERE id=$1', [requestId, status]);
      return { id: requestId, status, refunded: true };
    });
  }

  async requests(userId: string) {
    await this.expireDueRequests();
    return this.dataSource.query(
      `SELECT q.*, json_build_object('id', u.id, 'username', u.username, 'displayName', u."displayName", 'avatarUrl', u."avatarUrl") AS sender
       FROM cp_requests q JOIN users u ON u.id = q."fromUserId"
       WHERE q."toUserId" = $1 AND q.status = 'pending' ORDER BY q."createdAt" DESC`,
      [userId],
    );
  }

  async createRequest(_userId: string, _input: { recipientId: string; type: CpType; roomId: string; idempotencyKey: string }) {
    throw new BadRequestException('يتم إنشاء ارتباط CP حصراً عند إرسال هدية CP من داخل الروم الصوتي.');
  }

  async respond(userId: string, requestId: string, accept: boolean) {
    const outcome = await this.dataSource.transaction(async manager => {
      const [request] = await manager.query('SELECT * FROM cp_requests WHERE id = $1 FOR UPDATE', [requestId]);
      if (!request) throw new NotFoundException('طلب CP غير موجود');
      if (request.toUserId !== userId) throw new BadRequestException('لا يمكنك الرد على هذا الطلب');
      if (request.status !== CpRequestStatus.PENDING) throw new ConflictException('تمت معالجة هذا الطلب مسبقاً');
      if (new Date(request.expiresAt).getTime() <= Date.now()) {
        await this.refund(manager, request);
        await manager.query(`UPDATE cp_requests SET status='expired',"respondedAt"=now(),"updatedAt"=now() WHERE id=$1`, [requestId]);
        return { result: { id: requestId, status: 'expired', refunded: true }, senderId: request.fromUserId, receiverId: null, type: request.type, relationship: null, accepted: false };
      }
      if (!accept) {
        await this.refund(manager, request);
        await manager.query(`UPDATE cp_requests SET status='rejected',"respondedAt"=now(),"updatedAt"=now() WHERE id=$1`, [requestId]);
        return { result: { id: requestId, status: 'rejected', refunded: true }, senderId: request.fromUserId, receiverId: null, type: request.type, relationship: null, accepted: false };
      }
      await this.lockRelationshipUsers(manager, [request.fromUserId, request.toUserId]);
      const active = await manager.query(
        `SELECT id FROM cp_relationships WHERE status='active'
         AND ("userAId" = ANY($1::uuid[]) OR "userBId" = ANY($1::uuid[]))
         LIMIT 1 FOR UPDATE`,
        [[request.fromUserId, request.toUserId]],
      );
      if (active.length) {
        await this.refund(manager, request);
        await manager.query(`UPDATE cp_requests SET status='rejected',"respondedAt"=now(),"updatedAt"=now() WHERE id=$1`, [requestId]);
        return {
          result: { id: requestId, status: 'rejected', refunded: true, reason: 'user_already_has_active_relationship' },
          senderId: request.fromUserId, receiverId: null, type: request.type, relationship: null, accepted: false,
        };
      }
      const [relationship] = await manager.query(
        `INSERT INTO cp_relationships ("userAId","userBId",type,status,points,level,"startedAt","createdAt","updatedAt")
         VALUES ($1,$2,$3,'active',0,1,now(),now(),now()) RETURNING *`,
        [request.fromUserId, request.toUserId, request.type],
      );
      const [giftPointsSetting] = await manager.query('SELECT value FROM app_settings WHERE key=$1', [`cp.gift_points.${request.type}`]);
      // A catalog CP gift can carry its own point value. NULL keeps legacy
      // CP-type defaults; an explicit zero means the admin intentionally set zero.
      const initialPoints = request.cpPoints !== null && request.cpPoints !== undefined
        ? Number(request.cpPoints)
        : Number(giftPointsSetting?.value);
      if (Number.isSafeInteger(initialPoints) && initialPoints > 0) {
        await manager.query(
          `INSERT INTO cp_point_events ("relationshipId","eventType","referenceType","referenceId",points,metadata,"createdAt")
           VALUES ($1,'initiation_gift','cp_request',$2,$3,$4::jsonb,now())
           ON CONFLICT ("referenceType","referenceId") DO NOTHING`,
          [relationship.id, request.id, String(initialPoints), JSON.stringify({ giftId: request.giftId, giftName: request.giftName })],
        );
        const [levelsSetting] = await manager.query('SELECT value FROM app_settings WHERE key=$1', [`cp.levels.${request.type}`]);
        let computedLevel = 1;
        try {
          const levels = (parseJsonSetting(levelsSetting?.value, []) as Array<{ level: number; pointsRequired: number }>)
            .slice().sort((a, b) => Number(a.level) - Number(b.level));
          for (const item of levels) {
            if (Number(item.pointsRequired) <= initialPoints) computedLevel = Math.max(computedLevel, Number(item.level) || 1);
          }
        } catch { /* keep safe baseline level */ }
        await manager.query('UPDATE cp_relationships SET points=$1,level=$2,"updatedAt"=now() WHERE id=$3', [String(initialPoints), computedLevel, relationship.id]);
        relationship.points = String(initialPoints);
        relationship.level = computedLevel;
      }
      await manager.query(`UPDATE cp_requests SET status='accepted',"respondedAt"=now(),"relationshipId"=$2,"updatedAt"=now() WHERE id=$1`, [requestId, relationship.id]);
      return {
        result: { requestId, status: 'accepted', relationship },
        senderId: request.fromUserId, receiverId: request.toUserId, type: request.type,
        relationship, accepted: true,
      };
    });

    if (outcome.accepted && outcome.relationship) {
      // Deliver every configured level reward already reached at relationship creation,
      // including level 1. This covers badge auto-equipping for the first CP milestone.
      const [levelsSetting] = await this.dataSource.query(
        'SELECT value FROM app_settings WHERE key=$1',
        [`cp.levels.${outcome.type}`],
      );
      let levels: any[] = [];
      try {
        const parsed = parseJsonSetting(levelsSetting?.value, []);
        levels = Array.isArray(parsed) ? parsed : [];
      } catch { levels = []; }
      const reachedLevels = levels
        .filter(item => Number(item?.level) >= 1 && Number(item?.level) <= Number(outcome.relationship.level || 1))
        .sort((a, b) => Number(a.level) - Number(b.level));
      for (const levelConfig of reachedLevels) {
        await this.grantLevelGiftRewards(
          outcome.relationship.id,
          Number(levelConfig.level),
          [outcome.senderId, outcome.receiverId],
          levelConfig,
        ).catch(error => console.warn('[CP] initial level gift delivery failed', levelConfig.level, error));
        await this.grantLevelCosmeticRewards(
          outcome.relationship.id,
          Number(levelConfig.level),
          [outcome.senderId, outcome.receiverId],
          levelConfig,
        ).catch(error => console.warn('[CP] initial level cosmetic reward delivery failed', levelConfig.level, error));
      }
      const currentLevelConfig = reachedLevels.find(item => Number(item.level) === Number(outcome.relationship.level || 1)) || null;
      const levelName = currentLevelConfig?.name || currentLevelConfig?.title || currentLevelConfig?.presentation?.title || null;
      const levelPayload = {
        type: 'cp_level_up',
        relationshipId: outcome.relationship.id,
        level: Number(outcome.relationship.level) || 1,
        previousLevel: 0,
        points: String(outcome.relationship.points || 0),
        levelName,
        presentation: currentLevelConfig?.presentation || null,
        benefits: currentLevelConfig?.benefits || currentLevelConfig?.rewards || [],
      };
      const levelTitle = 'بدأت علاقتكما في CP ❤️';
      const levelBody = levelName ? `بدأت علاقتكما بمستوى ${levelName}` : `بدأت علاقتكما بالمستوى ${levelPayload.level}`;
      for (const memberId of [outcome.senderId, outcome.receiverId].filter(Boolean)) {
        await this.notifications.notifyUser(memberId, NotificationType.SYSTEM, levelTitle, levelBody, levelPayload).catch(() => undefined);
        this.realtime.emitToUser(memberId, 'cp:level-up', { ...levelPayload, title: levelTitle, body: levelBody });
      }
    }

    const responseStatus = String(outcome.result?.status || '');
    const title = responseStatus === 'accepted'
      ? 'تم قبول طلب ارتباط CP'
      : responseStatus === 'expired'
        ? 'انتهت مهلة طلب CP'
        : 'تم رفض طلب CP';
    const body = responseStatus === 'accepted'
      ? 'وافق الطرف الآخر على هدية الارتباط، وبدأت علاقتكما الآن.'
      : responseStatus === 'expired'
        ? 'انتهت مهلة طلب الارتباط وتمت إعادة العملات.'
        : 'لم تتم الموافقة على طلب الارتباط، وتمت إعادة العملات.';
    if (outcome.senderId) {
      const payload = {
        type: 'cp_relationship_response',
        notificationKind: 'cp_relationship_response',
        requestId,
        relationshipType: outcome.type,
        status: responseStatus,
        relationshipId: outcome.relationship?.id || null,
        route: 'cp',
      };
      await this.notifications.notifyUser(
        outcome.senderId, NotificationType.SYSTEM, title, body, payload,
      ).catch(() => undefined);
      this.realtime.emitToUser(outcome.senderId, 'cp:response', { ...payload, title, body });
    }
    return outcome.result;
  }

  async end(userId: string, relationshipId: string) {
    const result = await this.dataSource.query(
      `UPDATE cp_relationships SET status='ended',"endedAt"=now(),"endedByUserId"=$1,"updatedAt"=now()
       WHERE id=$2 AND status='active' AND ($1 = "userAId" OR $1 = "userBId")
       RETURNING id,type,status,"endedAt","userAId","userBId"`,
      [userId, relationshipId],
    );
    if (!result.length) throw new NotFoundException('العلاقة غير موجودة أو منتهية');
    const ended = result[0];
    const payload = {
      type: 'cp_relationship_ended',
      notificationKind: 'cp_relationship_ended',
      relationshipId: ended.id,
      relationshipType: ended.type,
      endedByUserId: userId,
      route: 'cp',
    };
    for (const memberId of [ended.userAId, ended.userBId].filter(Boolean)) {
      await this.notifications.notifyUser(
        memberId, NotificationType.SYSTEM, 'تم إلغاء ارتباط CP',
        memberId === userId ? 'تم إنهاء علاقتك بنجاح.' : 'قام الطرف الآخر بإنهاء علاقة CP.',
        payload,
      ).catch(error => console.warn('[CP] end notification delivery failed', error));
      this.realtime.emitToUser(memberId, 'cp:ended', payload);
    }
    return { id: ended.id, type: ended.type, status: ended.status, endedAt: ended.endedAt };
  }




  /** Create a relationship invitation from an already-committed catalog gift.
   * The gift is charged and delivered by GiftsService exactly once; this request
   * never charges or refunds coins. A linked sender keeps sending it as a normal gift.
   */
  async createInitiationRequestFromGift(
    senderId: string,
    receiverId: string,
    giftId: string,
    giftSendId: string,
    roomId?: string | null,
    giftPoints?: number | null,
  ) {
    if (!senderId || !receiverId || senderId === receiverId || !giftId || !giftSendId) return null;
    // Prefer the legacy explicit gift-to-type mapping; otherwise use the type saved
    // directly on any dashboard gift classified as CP.
    const configured = await this.dataSource.query(
      'SELECT key FROM app_settings WHERE key=$1 AND value=$2 LIMIT 1',
      ['cp.gift.relationship', giftId],
    );
    const [giftConfig] = await this.dataSource.query(
      'SELECT category, \"cpRelationshipType\" FROM gifts WHERE id=$1 AND \"isActive\"=true LIMIT 1',
      [giftId],
    );
    const mappedType = configured.length
      ? String(configured[0].key).slice('cp.gift.'.length)
      : '';
    // A gift may be marked as a CP gift either by its dashboard category or
    // by the explicit legacy cp.gift.<type> mapping. The mapping must remain
    // sufficient on older installations where the category column was never
    // backfilled. Prefer the explicit mapping when present; otherwise use the
    // relationship type stored on the CP gift row.
    const isCpGift = String(giftConfig?.category || '').toLowerCase() === 'cp' || !!mappedType;
    if (!isCpGift) return null;
    // Every CP-classified gift creates the same relationship type. Old gift
    // metadata is ignored for new invitations; existing pairs were migrated above.
    const type = CpType.RELATIONSHIP;
    const [globalSetting] = await this.dataSource.query('SELECT value FROM app_settings WHERE key=$1', ['cp.enabled.global']);
    const [typeSetting] = await this.dataSource.query('SELECT value FROM app_settings WHERE key=$1', [`cp.enabled.${type}`]);
    if (String(globalSetting?.value || 'true').toLowerCase() === 'false' ||
        String(typeSetting?.value || 'true').toLowerCase() === 'false') return null;

    const [configuredGiftPoints] = await this.dataSource.query(
      'SELECT value FROM app_settings WHERE key=$1',
      [`cp.gift_points.${type}`],
    );
    // A missing/zero legacy gift.cpPoints must not suppress the points
    // configured for this relationship type in the dashboard.
    const explicitGiftPoints = Number(giftPoints);
    const effectiveGiftPoints = Number.isSafeInteger(explicitGiftPoints) && explicitGiftPoints > 0
      ? explicitGiftPoints
      : Math.max(0, Math.floor(Number(configuredGiftPoints?.value) || 0));

    const idempotencyKey = `gift-send:${giftSendId}`;
    const outcome = await this.dataSource.transaction(async manager => {
      await this.lockRelationshipUsers(manager, [senderId, receiverId]);
      const [existing] = await manager.query(
        'SELECT * FROM cp_requests WHERE "idempotencyKey"=$1 FOR UPDATE',
        [idempotencyKey],
      );
      if (existing) return { request: existing, created: false };

      // A user may only have one active relationship, regardless of relationship type.
      const [active] = await manager.query(
        `SELECT id FROM cp_relationships WHERE status='active'
         AND ("userAId" = ANY($1::uuid[]) OR "userBId" = ANY($1::uuid[]))
         LIMIT 1 FOR UPDATE`,
        [[senderId, receiverId]],
      );
      if (active) return { request: null, created: false };

      const [pending] = await manager.query(
        `SELECT id FROM cp_requests WHERE status='pending' AND "expiresAt">now()
         AND (("fromUserId"=$1 AND "toUserId"=$2) OR ("fromUserId"=$2 AND "toUserId"=$1))
         LIMIT 1 FOR UPDATE`,
        [senderId, receiverId],
      );
      if (pending) return { request: pending, created: false };

      const [gift] = await manager.query(
        'SELECT id,name,"iconUrl","animationUrl" FROM gifts WHERE id=$1 AND "isActive"=true',
        [giftId],
      );
      if (!gift) return { request: null, created: false };
      const [expiryRow] = await manager.query(
        'SELECT value FROM app_settings WHERE key=$1',
        ['cp.policy.request_expiry_minutes'],
      );
      const configuredExpiry = Number(expiryRow?.value);
      const expiryMinutes = Number.isInteger(configuredExpiry) && configuredExpiry >= 5 && configuredExpiry <= 1440
        ? configuredExpiry : EXPIRY_MINUTES;
      const [request] = await manager.query(
        `INSERT INTO cp_requests
          ("fromUserId","toUserId",type,status,"roomId","priceCoins","giftId","giftName","giftIconUrl","giftAnimationUrl","cpPoints","idempotencyKey","expiresAt","createdAt","updatedAt")
         VALUES ($1,$2,$3,'pending',$4,NULL,$5,$6,$7,$8,$9,$10,now()+($11 || ' minutes')::interval,now(),now())
         RETURNING *`,
        [senderId, receiverId, type, roomId || null, gift.id, gift.name, gift.iconUrl, gift.animationUrl,
          effectiveGiftPoints > 0 ? String(effectiveGiftPoints) : null,
          idempotencyKey, String(expiryMinutes)],
      );
      return { request, created: true };
    });

    if (!outcome.created || !outcome.request) return outcome.request;
    const [sender] = await this.dataSource.query(
      'SELECT id,"displayName",username,"avatarUrl" FROM users WHERE id=$1',
      [senderId],
    );
    const senderName = String(sender?.displayName || sender?.username || 'أحد المستخدمين');
    const payload = {
      type: 'cp_relationship_request',
      notificationKind: 'cp_relationship_request',
      requestId: outcome.request.id,
      relationshipType: type,
      senderId,
      senderName,
      senderAvatarUrl: sender?.avatarUrl || null,
      recipientId: receiverId,
      giftId: outcome.request.giftId,
      giftName: outcome.request.giftName,
      giftIconUrl: outcome.request.giftIconUrl,
      giftAnimationUrl: outcome.request.giftAnimationUrl,
      roomId: roomId || null,
      route: 'cp',
      actions: ['accept', 'reject'],
      requestSource: 'gift_catalog',
    };
    const requestTitle = 'طلب علاقة';
    const requestBody = `أرسل لك ${senderName} طلب علاقة، هل توافق على الارتباط به؟`;
    await this.notifications.notifyUser(
      receiverId,
      NotificationType.SYSTEM,
      requestTitle,
      requestBody,
      payload,
    ).catch(error => console.warn('[CP] gift request notification delivery failed', error));
    this.realtime.emitToUser(receiverId, 'cp:request', {
      ...payload,
      title: requestTitle,
      body: requestBody,
    });
    if (roomId) {
      this.realtime.emitToRoom(roomId, 'room:event', {
        roomId,
        event: 'cp:request',
        payload: { ...payload, title: requestTitle, body: requestBody },
        at: new Date().toISOString(),
      });
    }
    return outcome.request;
  }

  private async lockRelationshipUsers(manager: any, userIds: string[]) {
    for (const id of Array.from(new Set(userIds)).sort()) {
      await manager.query('SELECT pg_advisory_xact_lock(hashtext($1), 1)', [id]);
    }
  }

  /** Award configured points when a committed normal gift is exchanged by active CP partners. */
  async recordGiftInteraction(senderId: string, receiverId: string, giftSendId: string, totalCoins: number, giftPoints?: number) {
    if (!senderId || !receiverId || !giftSendId || senderId === receiverId) return;
    const relationships = await this.dataSource.query(
      `SELECT id,type,level FROM cp_relationships WHERE status='active'
       AND (("userAId"=$1 AND "userBId"=$2) OR ("userAId"=$2 AND "userBId"=$1))`,
      [senderId, receiverId],
    );
    for (const relationship of relationships) {
      const [setting, legacyGiftSetting] = await Promise.all([
        this.dataSource.query(
          'SELECT value FROM app_settings WHERE key=$1',
          [`cp.points.gift_send.${relationship.type}`],
        ).then(rows => rows[0] || null),
        this.dataSource.query(
          'SELECT value FROM app_settings WHERE key=$1',
          [`cp.gift_points.${relationship.type}`],
        ).then(rows => rows[0] || null),
      ]);
      const perGiftPoints = Number(giftPoints);
      // Explicit per-gift points win only when positive. Otherwise fall back
      // first to the interaction-points setting, then to the CP gift-points
      // setting used by older dashboard versions.
      const configuredInteractionPoints = Number(setting?.value);
      const configuredGiftPoints = Number(legacyGiftSetting?.value);
      const basePoints = Number.isSafeInteger(perGiftPoints) && perGiftPoints > 0
        ? perGiftPoints
        : Number.isSafeInteger(configuredInteractionPoints) && configuredInteractionPoints > 0
          ? configuredInteractionPoints
          : configuredGiftPoints;
      if (!Number.isSafeInteger(basePoints) || basePoints <= 0) continue;
      const [levelSetting] = await this.dataSource.query(
        'SELECT value FROM app_settings WHERE key=$1',
        [`cp.levels.${relationship.type}`],
      );
      let levelConfig: any = null;
      try {
        const levelRows = parseJsonSetting(levelSetting?.value, []);
        if (Array.isArray(levelRows)) levelConfig = levelRows.find(item => Number(item?.level) === Number(relationship.level)) || null;
      } catch { levelConfig = null; }
      const bonusPercent = Math.max(0, Math.min(500, Number(levelConfig?.giftBonusPercent) || 0));
      const points = Math.floor(basePoints * (100 + bonusPercent) / 100);
      await this.recordPoints(
        relationship.id, 'gift_send', 'gift_send', `${giftSendId}:${relationship.id}`, points,
        { senderId, receiverId, giftSendId, totalCoins: Math.max(0, Number(totalCoins) || 0), basePoints, bonusPercent, relationshipLevel: Number(relationship.level) || 1 },
      );
    }
  }

  /** Record a server-verified, idempotent points event; never exposed as a user-award endpoint. */
  async recordPoints(
    relationshipId: string,
    eventType: string,
    referenceType: string,
    referenceId: string,
    points: number,
    metadata: Record<string, unknown> = {},
  ) {
    if (!UUID_REGEX.test(relationshipId) || !Number.isSafeInteger(points) || points < 0 || !eventType || !referenceType || !referenceId) {
      throw new BadRequestException('حدث النقاط غير صالح');
    }
    const result = await this.dataSource.transaction(async manager => {
      const [relationship] = await manager.query(
        'SELECT * FROM cp_relationships WHERE id=$1 AND status=\'active\' FOR UPDATE',
        [relationshipId],
      );
      if (!relationship) throw new NotFoundException('العلاقة غير نشطة');
      const inserted = await manager.query(
        `INSERT INTO cp_point_events ("relationshipId","eventType","referenceType","referenceId",points,metadata,"createdAt")
         VALUES ($1,$2,$3,$4,$5,$6::jsonb,now())
         ON CONFLICT ("referenceType","referenceId") DO NOTHING RETURNING id`,
        [relationshipId, eventType, referenceType, referenceId, String(points), JSON.stringify(metadata)],
      );
      if (!inserted.length) return { relationshipId, duplicate: true, points: relationship.points, level: relationship.level, previousLevel: Number(relationship.level) || 1, levelChanged: false };
      const previousLevel = Number(relationship.level) || 1;
      const total = BigInt(String(relationship.points)) + BigInt(points);
      const [levelConfig] = await manager.query('SELECT value FROM app_settings WHERE key=$1', [`cp.levels.${relationship.type}`]);
      let level = 1;
      try {
        const levels = (parseJsonSetting(levelConfig?.value, []) as Array<{ level: number; pointsRequired: number }>)
          .slice().sort((a, b) => Number(a.level) - Number(b.level));
        for (const item of levels) {
          if (Number(item.pointsRequired) <= Number(total)) level = Math.max(level, Number(item.level) || 1);
        }
      } catch { /* malformed admin config never blocks point ledger writes */ }
      // Permanent levels are monotonic: editing thresholds cannot downgrade users.
      level = Math.max(previousLevel, level);
      await manager.query(
        'UPDATE cp_relationships SET points=$1,level=$2,"updatedAt"=now() WHERE id=$3',
        [total.toString(), level, relationshipId],
      );
      return { relationshipId, duplicate: false, points: total.toString(), level, previousLevel, levelChanged: level > previousLevel };
    });
    if (result.levelChanged) {
      const [relationship] = await this.dataSource.query(
        'SELECT type,"userAId","userBId" FROM cp_relationships WHERE id=$1',
        [relationshipId],
      );
      const [levelsSetting] = relationship
        ? await this.dataSource.query('SELECT value FROM app_settings WHERE key=$1', [`cp.levels.${relationship.type}`])
        : [];
      let levels: any[] = [];
      try {
        const parsed = parseJsonSetting(levelsSetting?.value, []);
        levels = Array.isArray(parsed) ? parsed : [];
      } catch { levels = []; }
      const currentLevelConfig = levels.find(item => Number(item?.level) === Number(result.level)) || null;
      try {
        const crossedLevels = levels
          .filter(item => Number(item?.level) > Number(result.previousLevel) && Number(item?.level) <= Number(result.level))
          .sort((a, b) => Number(a.level) - Number(b.level));
        for (const unlockedLevel of crossedLevels) {
          const members = [relationship.userAId, relationship.userBId].filter(Boolean);
          await this.grantLevelGiftRewards(
            relationshipId, Number(unlockedLevel.level), members, unlockedLevel,
          );
          await this.grantLevelCosmeticRewards(
            relationshipId, Number(unlockedLevel.level), members, unlockedLevel,
          );
        }
      } catch (error) {
        // Cosmetic delivery is best-effort and must never suppress the level-up event
        // or make a successful gift transaction appear to have failed.
        console.warn('[CP] level reward processing failed', relationshipId, result.level, error);
      }
      const payload = {
        type: 'cp_level_up', relationshipId, level: result.level,
        previousLevel: result.previousLevel, points: result.points,
        levelName: currentLevelConfig?.name || currentLevelConfig?.title || currentLevelConfig?.presentation?.title || null,
        presentation: currentLevelConfig?.presentation || null,
        benefits: currentLevelConfig?.benefits || currentLevelConfig?.rewards || [],
      };
      const title = 'ارتفع مستوى CP!';
      const body = payload.levelName ? `وصلتما إلى مستوى ${payload.levelName}` : `وصلتما إلى المستوى ${result.level}`;
      for (const memberId of [relationship?.userAId, relationship?.userBId].filter(Boolean)) {
        await this.notifications.notifyUser(memberId, NotificationType.SYSTEM, title, body, payload).catch(() => undefined);
        this.realtime.emitToUser(memberId, 'cp:level-up', { ...payload, title, body });
      }
    }
    return result;
  }

  private async expireDueRequests() {
    const due = await this.dataSource.query(
      `SELECT id FROM cp_requests WHERE status='pending' AND "expiresAt" <= now() ORDER BY "expiresAt" LIMIT 100`,
    );
    for (const row of due) {
      await this.dataSource.transaction(async manager => {
        const [request] = await manager.query('SELECT * FROM cp_requests WHERE id=$1 FOR UPDATE', [row.id]);
        if (!request || request.status !== 'pending' || new Date(request.expiresAt).getTime() > Date.now()) return;
        await this.refund(manager, request);
        await manager.query(`UPDATE cp_requests SET status='expired',"respondedAt"=now(),"updatedAt"=now() WHERE id=$1 AND status='pending'`, [row.id]);
      });
    }
  }

  private async refund(manager: any, request: any) {
    if (request.refundedAt) return;
    const amount = Math.max(0, Number(request.priceCoins) || 0);
    if (amount === 0) {
      await manager.query('UPDATE cp_requests SET "refundedAt"=now(),"updatedAt"=now() WHERE id=$1 AND "refundedAt" IS NULL', [request.id]);
      return;
    }
    const [wallet] = await manager.query('SELECT coins FROM wallets WHERE "userId"=$1 FOR UPDATE', [request.fromUserId]);
    if (!wallet) throw new NotFoundException('محفظة صاحب الطلب غير موجودة');
    const balance = Number(wallet.coins);
    const inserted = await manager.query(
      `INSERT INTO wallet_transactions ("userId",type,currency,amount,"balanceAfter","referenceType","referenceId",description,metadata,"createdAt")
       VALUES ($1,'refund','coins',$2,$3,'cp_request_refund',$4,'استرداد هدية CP',$5,now())
       ON CONFLICT ("userId","referenceType","referenceId") WHERE "referenceType" IS NOT NULL AND "referenceId" IS NOT NULL DO NOTHING
       RETURNING id`,
      [request.fromUserId, amount, balance + amount, request.id, JSON.stringify({ cpRequestId: request.id, reason: request.status })],
    );
    if (inserted.length) {
      await manager.query('UPDATE wallets SET coins=coins+$1,"updatedAt"=now() WHERE "userId"=$2', [amount, request.fromUserId]);
    }
    await manager.query('UPDATE cp_requests SET "refundedAt"=now(),"updatedAt"=now() WHERE id=$1 AND "refundedAt" IS NULL', [request.id]);
  }
}
