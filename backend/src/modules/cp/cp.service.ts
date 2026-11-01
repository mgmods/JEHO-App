import { BadRequestException, ConflictException, Injectable, NotFoundException, OnModuleInit, OnModuleDestroy } from '@nestjs/common';
import { DataSource } from 'typeorm';
import { RealtimeGateway } from '../realtime/realtime.gateway';
import { CpType } from '../../database/entities/cp-relationship.entity';
import { CpRequestStatus } from '../../database/entities/cp-request.entity';
import { NotificationType } from '../../database/entities/notification.entity';
import { NotificationsService } from '../notifications/notifications.service';

const TYPES = new Set<string>(Object.values(CpType));
const EXPIRY_MINUTES = 30;
const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

@Injectable()
export class CpService implements OnModuleInit, OnModuleDestroy {
  private expiryTimer: ReturnType<typeof setInterval> | null = null;

  constructor(
    private readonly dataSource: DataSource,
    private readonly realtime: RealtimeGateway,
    private readonly notifications: NotificationsService,
  ) {}

  async onModuleInit() {
    // Backward-compatible schema adjustment: profile-origin CP requests have no room/payment.
    await this.dataSource.query('ALTER TABLE cp_requests ALTER COLUMN "roomId" DROP NOT NULL').catch(() => undefined);
    await this.dataSource.query('ALTER TABLE cp_requests ALTER COLUMN "priceCoins" DROP NOT NULL').catch(() => undefined);
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
      CREATE INDEX IF NOT EXISTS idx_cp_memories_relationship_created
      ON cp_memories ("relationshipId", "createdAt" DESC)
    `).catch(() => undefined);
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


  private async enrichLevelRewards(levels: any[]) {
    const giftIds = Array.from(new Set(
      (Array.isArray(levels) ? levels : [])
        .map(item => String(item?.rewardGiftId || '').trim())
        .filter(id => UUID_REGEX.test(id)),
    ));
    if (!giftIds.length) return levels;
    const gifts = await this.dataSource.query(
      'SELECT id,name,"iconUrl","animationUrl","coinPrice" FROM gifts WHERE id::text = ANY($1::text[]) AND "isActive"=true',
      [giftIds],
    );
    const byId = new Map(gifts.map((gift: any) => [String(gift.id), gift]));
    return levels.map(item => ({
      ...item,
      rewardGift: byId.get(String(item?.rewardGiftId || '')) || null,
    }));
  }

  async catalog() {
    const rows = await this.dataSource.query(
      `SELECT key,value FROM app_settings WHERE key LIKE 'cp.%' ORDER BY key`,
    );
    const settings: Record<string, string> = Object.fromEntries(rows.map((row: any) => [row.key, String(row.value)]));
    const globallyEnabled = settings['cp.enabled.global'] !== 'false';
    const catalog: Array<Record<string, unknown>> = [];
    for (const type of Object.values(CpType)) {
      const giftId = settings[`cp.gift.${type}`] || null;
      const [gift] = giftId ? await this.dataSource.query(
        'SELECT id,name,"iconUrl","animationUrl","coinPrice" FROM gifts WHERE id=$1 AND "isActive"=true',
        [giftId],
      ) : [];
      // The CP dashboard price is the live source of truth for the CP store.
      // Legacy installations without cp.price.<type> continue using the selected gift's price.
      const configuredPrice = settings[`cp.price.${type}`] === undefined
        ? Number(gift?.coinPrice)
        : Number(settings[`cp.price.${type}`]);
      const giftPrice = Number.isSafeInteger(configuredPrice) && configuredPrice > 0 ? configuredPrice : 0;
      const enabled = globallyEnabled && settings[`cp.enabled.${type}`] !== 'false'
        && !!gift && giftPrice > 0;
      let rules: Record<string, any> = {};
      try { rules = JSON.parse(settings[`cp.rules.${type}`] || '{}'); } catch { rules = {}; }
      const effect = rules?.seatEffect && typeof rules.seatEffect === 'object' ? rules.seatEffect : {};
      let levels: Array<Record<string, any>> = [];
      try {
        const parsedLevels = JSON.parse(settings[`cp.levels.${type}`] || '[]');
        if (Array.isArray(parsedLevels)) levels = parsedLevels
          .filter(item => item && Number.isInteger(Number(item.level)) && Number(item.level) >= 1)
          .sort((a, b) => Number(a.level) - Number(b.level));
      } catch { levels = []; }
      levels = await this.enrichLevelRewards(levels);
      catalog.push({
        type, enabled, priceCoins: enabled ? giftPrice : null,
        maxLevel: Number(settings[`cp.max_level.${type}`] || 0) || null,
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
    return catalog;
  }

  async leaderboard(period: 'weekly' | 'world' = 'weekly') {
    if (period === 'world') return this.discover();
    if (period !== 'weekly') throw new BadRequestException('فترة ترتيب CP غير صالحة');
    return this.dataSource.query(
      `SELECT r.id, r.type, r.points::text AS points, r.level, r."startedAt",
        COALESCE(SUM(e.points::numeric), 0)::text AS "weeklyPoints",
        json_build_object('id', a.id, 'username', a.username, 'displayName', a."displayName", 'avatarUrl', a."avatarUrl", 'level', a.level) AS "userA",
        json_build_object('id', b.id, 'username', b.username, 'displayName', b."displayName", 'avatarUrl', b."avatarUrl", 'level', b.level) AS "userB"
       FROM cp_relationships r
       JOIN users a ON a.id = r."userAId"
       JOIN users b ON b.id = r."userBId"
       LEFT JOIN cp_point_events e ON e."relationshipId" = r.id
         AND e."createdAt" >= date_trunc('week', now())
       WHERE r.status = 'active'
       GROUP BY r.id, a.id, b.id
       ORDER BY SUM(e.points::numeric) DESC NULLS LAST, r.points DESC, r."startedAt" ASC
       LIMIT 100`,
    );
  }

  async rewards() {
    const rows = await this.dataSource.query(
      `SELECT key, value FROM app_settings WHERE key LIKE 'cp.levels.%' ORDER BY key`,
    );
    const configs: Array<{ type: string; levels: any[] }> = [];
    for (const row of rows) {
      const type = String(row.key).slice('cp.levels.'.length);
      if (!TYPES.has(type)) continue;
      let levels: any[] = [];
      try {
        const parsed = JSON.parse(String(row.value || '[]'));
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
          'vipBadgeUrl', pa."vipBadgeUrl", 'hostBadgeUrl', pa."hostBadgeUrl", 'levelBadgeUrl', pa."levelBadgeUrl") AS "userA",
        json_build_object('id', b.id, 'username', b.username, 'displayName', b."displayName", 'avatarUrl', b."avatarUrl", 'level', b.level,
          'vipBadgeUrl', pb."vipBadgeUrl", 'hostBadgeUrl', pb."hostBadgeUrl", 'levelBadgeUrl', pb."levelBadgeUrl") AS "userB"
       FROM cp_relationships r
       JOIN users a ON a.id = r."userAId" JOIN users b ON b.id = r."userBId"
       LEFT JOIN user_profiles pa ON pa."userId" = a.id
       LEFT JOIN user_profiles pb ON pb."userId" = b.id
       WHERE r.status = 'active' ${where}
       ORDER BY r.points DESC, r."startedAt" ASC LIMIT 100`,
      args,
    );
    const types: string[] = Array.from(new Set<string>(rows.map((row: any) => String(row.type))));
    const configs: Record<string, Array<{ level: number; pointsRequired: number; reward?: string }>> = {};
    for (const itemType of types) {
      const [setting] = await this.dataSource.query(
        'SELECT value FROM app_settings WHERE key=$1',
        [`cp.levels.${itemType}`],
      );
      try {
        const parsed = JSON.parse(setting?.value || '[]');
        configs[itemType] = await this.enrichLevelRewards(Array.isArray(parsed) ? parsed : []);
      } catch { configs[itemType] = []; }
    }
    return rows.map((row: any) => {
      const points = BigInt(String(row.points || 0));
      const levels = (configs[row.type] || []).slice().sort((a, b) => Number(a.pointsRequired) - Number(b.pointsRequired));
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
        .filter(item => item.reward || item.rewards || item.benefits || item.presentation || item.giftPresentation)
        .map(item => ({
          ...item,
          level: Number(item.level),
          reward: item.reward ?? null,
          benefits: item.benefits ?? item.rewards ?? [],
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
      try { rules = JSON.parse(String(setting?.value || '{}')); } catch { rules = {}; }
      try {
        const parsed = JSON.parse(String(levelSetting?.value || '[]'));
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
      `SELECT id,points::text AS points,level,type FROM cp_relationships
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
      'SELECT COUNT(*)::int AS count FROM cp_point_events WHERE "relationshipId"=$1 AND "createdAt">=date_trunc($2,now()) AND "eventType"=$3',
      [relationship.id, 'day', 'gift_send'],
    );
    const [taskConfigRow] = await this.dataSource.query('SELECT value FROM app_settings WHERE key=$1', ['cp.tasks.config']);
    let taskConfig: Record<string, any> = {};
    try { taskConfig = JSON.parse(taskConfigRow?.value || '{}'); } catch { taskConfig = {}; }
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
        const target = Math.max(1, Math.min(1000000, Math.floor(Number(config.target ?? base.target) || base.target)));
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
      ? await this.dataSource.query(
          'SELECT code,name,"previewUrl",type FROM cosmetics WHERE code = ANY($1::text[]) AND code LIKE \'cp_%\' AND "isActive"=true',
          [rewardCodes],
        )
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
    try { taskConfig = JSON.parse(taskConfigRow?.value || '{}'); } catch { taskConfig = {}; }
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
    const target = Math.max(1, Math.min(1000000, Math.floor(Number(taskDefinition.target) || baseTask.target)));
    const configuredRewardPoints = Math.max(0, Math.min(1000000, Math.floor(Number(taskDefinition.rewardPoints ?? baseTask.rewardPoints) || 0)));
    const [relationship] = await this.dataSource.query(
      `SELECT id,points::text AS points,level,type FROM cp_relationships
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
        'SELECT COUNT(*)::int AS count FROM cp_point_events WHERE "relationshipId"=$1 AND "createdAt">=date_trunc($2,now()) AND "eventType"=$3',
        [relationship.id, 'day', 'gift_send'],
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
    if (!(result as any).duplicate && configuredCode) {
      const [cosmetic] = await this.dataSource.query(
        'SELECT id,code,name,type,"previewUrl" FROM cosmetics WHERE code=$1 AND "isActive"=true AND code LIKE \'cp_%\' LIMIT 1',
        [configuredCode],
      );
      if (cosmetic) {
        const [owned] = await this.dataSource.query(
          'SELECT id FROM user_cosmetics WHERE "userId"=$1 AND "cosmeticId"=$2 LIMIT 1',
          [userId, cosmetic.id],
        );
        if (!owned) {
          await this.dataSource.query(
            'INSERT INTO user_cosmetics (id,"userId","cosmeticId",equipped,"createdAt","updatedAt") VALUES (gen_random_uuid(),$1,$2,false,now(),now())',
            [userId, cosmetic.id],
          );
        }
        rewardItem = { code: cosmetic.code, name: cosmetic.name, type: cosmetic.type, previewUrl: cosmetic.previewUrl, alreadyOwned: !!owned };
      }
    }
    return { taskId, claimed: true, duplicate: !!(result as any).duplicate, rewardPoints, rewardItem, points: (result as any).points, level: (result as any).level };
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
       json_build_object('id',p.id,'username',p.username,'displayName',p."displayName",'avatarUrl',p."avatarUrl",'level',p.level,
         'vipBadgeUrl',pp."vipBadgeUrl",'hostBadgeUrl',pp."hostBadgeUrl",'levelBadgeUrl',pp."levelBadgeUrl") AS partner
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
        const parsed = JSON.parse(setting?.value || '[]');
        configs[type] = await this.enrichLevelRewards(Array.isArray(parsed) ? parsed : []);
      } catch { configs[type] = []; }
    }
    return rows.map((row: any) => {
      const currentLevelConfig = (configs[row.type] || []).find(item => Number(item?.level) === Number(row.level)) || null;
      const presentation = currentLevelConfig?.presentation || {};
      return {
        ...row,
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
           'avatarUrl', me."avatarUrl", 'level', me.level,
           'vipBadgeUrl', mp."vipBadgeUrl", 'hostBadgeUrl', mp."hostBadgeUrl", 'levelBadgeUrl', mp."levelBadgeUrl") AS self,
         json_build_object('id', p.id, 'username', p.username, 'displayName', p."displayName",
           'avatarUrl', p."avatarUrl", 'level', p.level,
           'vipBadgeUrl', pp."vipBadgeUrl", 'hostBadgeUrl', pp."hostBadgeUrl", 'levelBadgeUrl', pp."levelBadgeUrl") AS partner
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
        currentLevelConfig: visual.currentLevelConfig || null,
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


  async createProfileRequest(userId: string, input: { recipientId: string; type: CpType; idempotencyKey: string }) {
    const recipientId = String(input?.recipientId || '');
    const key = String(input?.idempotencyKey || '').trim();
    const type = input?.type;
    if (!UUID_REGEX.test(recipientId) || !key || key.length > 128 || !TYPES.has(type)) {
      throw new BadRequestException('بيانات هدية CP غير مكتملة');
    }
    if (recipientId === userId) throw new BadRequestException('لا يمكنك إرسال هدية ارتباط إلى حسابك');
    const [typeEnabled] = await this.dataSource.query('SELECT value FROM app_settings WHERE key=$1', [`cp.enabled.${type}`]);
    if (String(typeEnabled?.value || 'true').toLowerCase() === 'false') {
      throw new BadRequestException('هذا النوع من علاقات CP متوقف حالياً من لوحة التحكم');
    }
    const outcome = await this.dataSource.transaction(async manager => {
      await this.lockRelationshipUsers(manager, [userId, recipientId]);
      const existing = await manager.query('SELECT * FROM cp_requests WHERE "idempotencyKey"=$1 FOR UPDATE', [key]);
      if (existing.length) {
        if (existing[0].fromUserId !== userId || existing[0].toUserId !== recipientId || existing[0].type !== type) {
          throw new ConflictException('مفتاح العملية مستخدم لطلب آخر');
        }
        return { request: existing[0], created: false };
      }
      const [recipient] = await manager.query('SELECT id FROM users WHERE id=$1 AND status=$2', [recipientId, 'active']);
      if (!recipient) throw new NotFoundException('المستخدم المستهدف غير موجود أو غير نشط');
      const [active] = await manager.query(
        `SELECT id FROM cp_relationships WHERE status='active'
         AND ("userAId" = ANY($1::uuid[]) OR "userBId" = ANY($1::uuid[]))
         LIMIT 1 FOR UPDATE`,
        [[userId, recipientId]],
      );
      if (active) throw new ConflictException('أحد الحسابين مرتبط بالفعل؛ لا يمكن إنشاء ارتباط آخر');
      const [pending] = await manager.query(
        `SELECT id FROM cp_requests WHERE
         (("fromUserId"=$1 AND "toUserId"=$2) OR ("fromUserId"=$2 AND "toUserId"=$1))
         AND type=$3 AND status='pending' AND "expiresAt">now() LIMIT 1 FOR UPDATE`,
        [userId, recipientId, type],
      );
      if (pending) throw new ConflictException('يوجد طلب هدية ارتباط معلّق بينكما');
      const [priceRow] = await manager.query('SELECT value FROM app_settings WHERE key=$1', [`cp.price.${type}`]);
      const price = Number(priceRow?.value);
      if (!Number.isSafeInteger(price) || price <= 0) throw new BadRequestException('سعر هدية هذا النوع غير محدد من لوحة التحكم');
      const [giftSetting] = await manager.query('SELECT value FROM app_settings WHERE key=$1', [`cp.gift.${type}`]);
      const [gift] = giftSetting?.value
        ? await manager.query('SELECT id,name,"iconUrl","animationUrl" FROM gifts WHERE id=$1 AND "isActive"=true', [giftSetting.value])
        : [];
      if (!gift) throw new BadRequestException('يجب تحديد هدية فعلية لهذا النوع من لوحة التحكم');
      const [wallet] = await manager.query('SELECT id, coins FROM wallets WHERE "userId"=$1 FOR UPDATE', [userId]);
      if (!wallet) throw new NotFoundException('المحفظة غير موجودة');
      const balance = Number(wallet.coins);
      if (balance < price) throw new BadRequestException('رصيد العملات غير كافٍ لإرسال هدية CP');
      const [expiryRow] = await manager.query('SELECT value FROM app_settings WHERE key=$1', ['cp.policy.request_expiry_minutes']);
      const configuredExpiry = Number(expiryRow?.value);
      const expiryMinutes = Number.isInteger(configuredExpiry) && configuredExpiry >= 5 && configuredExpiry <= 1440
        ? configuredExpiry : EXPIRY_MINUTES;
      const [request] = await manager.query(
        `INSERT INTO cp_requests ("fromUserId","toUserId",type,status,"roomId","priceCoins","giftId","giftName","giftIconUrl","giftAnimationUrl","idempotencyKey","expiresAt","createdAt","updatedAt")
         VALUES ($1,$2,$3,'pending',NULL,$4,$5,$6,$7,$8,$9,now()+($10 || ' minutes')::interval,now(),now()) RETURNING *`,
        [userId, recipientId, type, String(price), gift.id, gift.name, gift.iconUrl, gift.animationUrl, key, String(expiryMinutes)],
      );
      await manager.query('UPDATE wallets SET coins=coins-$1,"updatedAt"=now() WHERE "userId"=$2', [price, userId]);
      await manager.query(
        `INSERT INTO wallet_transactions ("userId",type,currency,amount,"balanceAfter","referenceType","referenceId",description,metadata,"createdAt")
         VALUES ($1,'gift_send','coins',$2,$3,'cp_request',$4,$5,$6,now())`,
        [userId, -price, balance - price, request.id, `هدية بدء CP - ${type}`, JSON.stringify({ type, recipientId, giftId: gift.id, giftName: gift.name, source: 'cp_gift_page' })],
      );
      return { request, created: true };
    });
    if (outcome.created) {
      const [sender] = await this.dataSource.query('SELECT "displayName", username FROM users WHERE id=$1', [userId]);
      const senderName = String(sender?.displayName || sender?.username || 'أحد المستخدمين');
      await this.notifications.notifyUser(
        recipientId, NotificationType.SYSTEM, 'طلب ارتباط جديد',
        `أرسل لك ${senderName} هدية ${this.cpTypeLabel(type)}. وافق على الطلب لبدء العلاقة أو ارفضه.`,
        { type: 'cp_relationship_request', notificationKind: 'cp_relationship_request', requestId: outcome.request.id,
          relationshipType: type, senderId: userId, recipientId, route: 'cp', actions: ['accept', 'reject'], requestSource: 'cp_gift_page',
          giftId: outcome.request.giftId, giftName: outcome.request.giftName, giftIconUrl: outcome.request.giftIconUrl,
          priceCoins: outcome.request.priceCoins },
      );
      this.realtime.emitToUser(recipientId, 'cp:request', {
        requestId: outcome.request.id, senderId: userId, senderName, relationshipType: type,
        title: 'هدية وطلب ارتباط جديد', body: `أرسل لك ${senderName} هدية ${this.cpTypeLabel(type)}.`,
        actions: ['accept', 'reject'],
      });
    }
    return outcome.request;
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

  async createRequest(userId: string, input: { recipientId: string; type: CpType; roomId: string; idempotencyKey: string }) {
    const recipientId = String(input?.recipientId || '');
    const roomId = String(input?.roomId || '');
    const key = String(input?.idempotencyKey || '').trim();
    const type = input?.type;
    if (!UUID_REGEX.test(recipientId) || !UUID_REGEX.test(roomId) || !key || key.length > 128 || !TYPES.has(type)) {
      throw new BadRequestException('بيانات طلب CP غير مكتملة');
    }
    if (recipientId === userId) throw new BadRequestException('لا يمكنك إنشاء CP مع حسابك');
    const [globalEnabled] = await this.dataSource.query('SELECT value FROM app_settings WHERE key = $1', ['cp.enabled.global']);
    if (String(globalEnabled?.value || 'true').toLowerCase() === 'false') {
      throw new BadRequestException('قسم CP متوقف حالياً من لوحة التحكم');
    }
    const [typeEnabled] = await this.dataSource.query('SELECT value FROM app_settings WHERE key = $1', [`cp.enabled.${type}`]);
    if (String(typeEnabled?.value || 'true').toLowerCase() === 'false') {
      throw new BadRequestException('هذا النوع من علاقات CP متوقف حالياً من لوحة التحكم');
    }
    const outcome = await this.dataSource.transaction(async manager => {
      await this.lockRelationshipUsers(manager, [userId, recipientId]);
      const existing = await manager.query('SELECT * FROM cp_requests WHERE "idempotencyKey" = $1 FOR UPDATE', [key]);
      if (existing.length) {
        if (existing[0].fromUserId !== userId || existing[0].toUserId !== recipientId || existing[0].type !== type || existing[0].roomId !== roomId) {
          throw new ConflictException('مفتاح العملية مستخدم لطلب آخر');
        }
        return { request: existing[0], created: false };
      }
      const [room] = await manager.query('SELECT id, type, status, "hostId" FROM rooms WHERE id = $1 FOR SHARE', [roomId]);
      if (!room || room.type !== 'voice' || room.status === 'closed') {
        throw new BadRequestException('يجب اختيار غرفة صوتية فعالة');
      }
      // Validate current room seating from the database; do not change the voice/realtime gateway.
      const presentRows = await manager.query(
        `SELECT "userId" FROM room_seats
         WHERE "roomId" = $1 AND "userId" = ANY($2::uuid[])
           AND status IN ('occupied','muted')`,
        [roomId, [userId, recipientId]],
      );
      const presentIds = new Set(presentRows.map((row: any) => String(row.userId)));
      if (!presentIds.has(userId) || !presentIds.has(recipientId)) {
        throw new BadRequestException('يجب أن يكون الطرفان جالسين داخل الغرفة نفسها عند إرسال طلب CP');
      }
      const [expiryRow] = await manager.query('SELECT value FROM app_settings WHERE key = $1', ['cp.policy.request_expiry_minutes']);
      const configuredExpiry = Number(expiryRow?.value);
      const expiryMinutes = Number.isInteger(configuredExpiry) && configuredExpiry >= 5 && configuredExpiry <= 1440
        ? configuredExpiry : EXPIRY_MINUTES;
      const [giftSetting] = await manager.query('SELECT value FROM app_settings WHERE key=$1', [`cp.gift.${type}`]);
      const [gift] = giftSetting?.value
        ? await manager.query('SELECT id,name,"iconUrl","animationUrl","coinPrice","isActive" FROM gifts WHERE id=$1', [giftSetting.value])
        : [];
      const [priceSetting] = await manager.query('SELECT value FROM app_settings WHERE key=$1', [`cp.price.${type}`]);
      // Match the CP store catalog exactly; use gift catalog price only for legacy config.
      const configuredPrice = priceSetting?.value === undefined
        ? Number(gift?.coinPrice)
        : Number(priceSetting.value);
      const price = Number.isSafeInteger(configuredPrice) && configuredPrice > 0 ? configuredPrice : 0;
      if (!gift || gift.isActive !== true) throw new BadRequestException('يجب تحديد هدية فعلية ومفعّلة لهذا النوع من لوحة التحكم');
      if (price <= 0) {
        throw new BadRequestException('سعر هدية CP غير مفعّل. حدّد سعراً موجباً من لوحة CP');
      }
      const [wallet] = await manager.query('SELECT id, coins FROM wallets WHERE "userId" = $1 FOR UPDATE', [userId]);
      if (!wallet) throw new NotFoundException('المحفظة غير موجودة');
      const balance = Number(wallet.coins);
      if (balance < price) throw new BadRequestException('رصيد العملات غير كافٍ');
      // Prevent repeated taps/network retries from charging the sender multiple times
      // for the same active or still-pending relationship type.
      const [activeRelationship] = await manager.query(
        `SELECT id FROM cp_relationships WHERE status='active'
         AND ("userAId" = ANY($1::uuid[]) OR "userBId" = ANY($1::uuid[]))
         LIMIT 1 FOR UPDATE`,
        [[userId, recipientId]],
      );
      if (activeRelationship) throw new ConflictException('يجب إنهاء العلاقة الحالية قبل إنشاء ارتباط جديد');
      const [pendingRequest] = await manager.query(
        `SELECT id FROM cp_requests WHERE
           (("fromUserId"=$1 AND "toUserId"=$2) OR ("fromUserId"=$2 AND "toUserId"=$1))
           AND type=$3 AND status='pending' AND "expiresAt" > now() LIMIT 1 FOR UPDATE`,
        [userId, recipientId, type],
      );
      if (pendingRequest) throw new ConflictException('يوجد طلب CP معلّق بينكما لهذا النوع؛ عالجوا الطلب الحالي أولاً');
      const [user] = await manager.query('SELECT id FROM users WHERE id = $1 AND status = $2', [recipientId, 'active']);
      if (!user) throw new NotFoundException('المستخدم المستهدف غير موجود أو غير نشط');
      const [request] = await manager.query(
        `INSERT INTO cp_requests ("fromUserId","toUserId",type,status,"roomId","priceCoins","giftId","giftName","giftIconUrl","giftAnimationUrl","idempotencyKey","expiresAt","createdAt","updatedAt")
         VALUES ($1,$2,$3,'pending',$4,$5,$6,$7,$8,$9,$10,now() + ($11 || ' minutes')::interval,now(),now()) RETURNING *`,
        [userId, recipientId, type, roomId, String(price), gift.id, gift.name, gift.iconUrl, gift.animationUrl, key, String(expiryMinutes)],
      );
      await manager.query('UPDATE wallets SET coins = coins - $1, "updatedAt" = now() WHERE "userId" = $2', [price, userId]);
      await manager.query(
        `INSERT INTO wallet_transactions ("userId",type,currency,amount,"balanceAfter","referenceType","referenceId",description,metadata,"createdAt")
         VALUES ($1,'gift_send','coins',$2,$3,'cp_request',$4,$5,$6,now())`,
        [userId, -price, balance - price, request.id, `هدية بدء CP - ${type}`, JSON.stringify({ type, recipientId, roomId, giftId: gift.id, giftName: gift.name })],
      );
      return { request, created: true };
    });
    if (outcome.created) {
      const [sender] = await this.dataSource.query(
        'SELECT "displayName", username FROM users WHERE id=$1',
        [userId],
      );
      const senderName = String(sender?.displayName || sender?.username || 'أحد المستخدمين');
      await this.notifications.notifyUser(
        recipientId,
        NotificationType.SYSTEM,
        'طلب ارتباط جديد',
        `أرسل لك ${senderName} طلب ${this.cpTypeLabel(type)}. افتح مساحة CP لقبول الطلب أو رفضه.`,
        {
          type: 'cp_relationship_request',
          notificationKind: 'cp_relationship_request',
          requestId: outcome.request.id,
          relationshipType: type,
          senderId: userId,
          recipientId,
          route: 'cp',
          actions: ['accept', 'reject'],
          requestSource: 'room_gift',
        },
      );
      this.realtime.emitToUser(recipientId, 'cp:request', {
        requestId: outcome.request.id,
        senderId: userId,
        senderName,
        relationshipType: type,
        title: 'طلب ارتباط جديد',
        body: `أرسل لك ${senderName} طلب ${this.cpTypeLabel(type)}.`,
        actions: ['accept', 'reject'],
      });
      this.realtime.emitToRoom(roomId, 'room:event', {
        roomId,
        event: 'cp:request',
        payload: {
          requestId: outcome.request.id,
          targetUserId: recipientId,
          senderId: userId,
          senderName,
          relationshipType: type,
          giftId: outcome.request.giftId,
          giftName: outcome.request.giftName,
          giftIconUrl: outcome.request.giftIconUrl,
          title: 'هدية ارتباط',
          body: `أرسل لك ${senderName} ${outcome.request.giftName || 'هدية ارتباط'}؛ وافق أو ارفض لإكمال الطلب.`,
          actions: ['accept', 'reject'],
        },
        at: new Date().toISOString(),
      });
    }
    return outcome.request;
  }

  async respond(userId: string, requestId: string, accept: boolean) {
    return this.dataSource.transaction(async manager => {
      const [request] = await manager.query('SELECT * FROM cp_requests WHERE id = $1 FOR UPDATE', [requestId]);
      if (!request) throw new NotFoundException('طلب CP غير موجود');
      if (request.toUserId !== userId) throw new BadRequestException('لا يمكنك الرد على هذا الطلب');
      if (request.status !== CpRequestStatus.PENDING) throw new ConflictException('تمت معالجة هذا الطلب مسبقاً');
      if (new Date(request.expiresAt).getTime() <= Date.now()) {
        await this.refund(manager, request);
        await manager.query(`UPDATE cp_requests SET status='expired',"respondedAt"=now(),"updatedAt"=now() WHERE id=$1`, [requestId]);
        return { id: requestId, status: 'expired', refunded: true };
      }
      if (!accept) {
        await this.refund(manager, request);
        await manager.query(`UPDATE cp_requests SET status='rejected',"respondedAt"=now(),"updatedAt"=now() WHERE id=$1`, [requestId]);
        return { id: requestId, status: 'rejected', refunded: true };
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
        return { id: requestId, status: 'rejected', refunded: true, reason: 'user_already_has_active_relationship' };
      }
      const [relationship] = await manager.query(
        `INSERT INTO cp_relationships ("userAId","userBId",type,status,points,level,"startedAt","createdAt","updatedAt")
         VALUES ($1,$2,$3,'active',0,1,now(),now(),now()) RETURNING *`,
        [request.fromUserId, request.toUserId, request.type],
      );
      const [giftPointsSetting] = await manager.query('SELECT value FROM app_settings WHERE key=$1', [`cp.gift_points.${request.type}`]);
      const initialPoints = Number(giftPointsSetting?.value);
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
          const levels = JSON.parse(levelsSetting?.value || '[]') as Array<{ level: number; pointsRequired: number }>;
          for (const item of levels) if (Number(item.pointsRequired) <= initialPoints) computedLevel = Math.max(computedLevel, Number(item.level) || 1);
        } catch { /* keep safe baseline level */ }
        const [maxSetting] = await manager.query('SELECT value FROM app_settings WHERE key=$1', [`cp.max_level.${request.type}`]);
        const configuredMax = Number(maxSetting?.value);
        if (Number.isInteger(configuredMax) && configuredMax > 0) computedLevel = Math.min(computedLevel, configuredMax);
        await manager.query('UPDATE cp_relationships SET points=$1,level=$2,"updatedAt"=now() WHERE id=$3', [String(initialPoints), computedLevel, relationship.id]);
        relationship.points = String(initialPoints);
        relationship.level = computedLevel;
      }
      await manager.query(`UPDATE cp_requests SET status='accepted',"respondedAt"=now(),"relationshipId"=$2,"updatedAt"=now() WHERE id=$1`, [requestId, relationship.id]);
      return { requestId, status: 'accepted', relationship };
    });
  }

  async end(userId: string, relationshipId: string) {
    const result = await this.dataSource.query(
      `UPDATE cp_relationships SET status='ended',"endedAt"=now(),"endedByUserId"=$1,"updatedAt"=now()
       WHERE id=$2 AND status='active' AND ($1 = "userAId" OR $1 = "userBId") RETURNING id,type,status,"endedAt"`,
      [userId, relationshipId],
    );
    if (!result.length) throw new NotFoundException('العلاقة غير موجودة أو منتهية');
    return result[0];
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
  ) {
    if (!senderId || !receiverId || senderId === receiverId || !giftId || !giftSendId) return null;
    const configured = await this.dataSource.query(
      'SELECT key FROM app_settings WHERE key LIKE $1 AND value=$2 ORDER BY key LIMIT 1',
      ['cp.gift.%', giftId],
    );
    if (!configured.length) return null;
    const type = String(configured[0].key).slice('cp.gift.'.length) as CpType;
    if (!TYPES.has(type)) return null;
    const [globalSetting] = await this.dataSource.query('SELECT value FROM app_settings WHERE key=$1', ['cp.enabled.global']);
    const [typeSetting] = await this.dataSource.query('SELECT value FROM app_settings WHERE key=$1', [`cp.enabled.${type}`]);
    if (String(globalSetting?.value || 'true').toLowerCase() === 'false' ||
        String(typeSetting?.value || 'true').toLowerCase() === 'false') return null;

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
          ("fromUserId","toUserId",type,status,"roomId","priceCoins","giftId","giftName","giftIconUrl","giftAnimationUrl","idempotencyKey","expiresAt","createdAt","updatedAt")
         VALUES ($1,$2,$3,'pending',$4,NULL,$5,$6,$7,$8,$9,now()+($10 || ' minutes')::interval,now(),now())
         RETURNING *`,
        [senderId, receiverId, type, roomId || null, gift.id, gift.name, gift.iconUrl, gift.animationUrl, idempotencyKey, String(expiryMinutes)],
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
    await this.notifications.notifyUser(
      receiverId,
      NotificationType.SYSTEM,
      'طلب ارتباط جديد',
      `أرسل لك ${senderName} هدية ${this.cpTypeLabel(type)}. وافق أو ارفض الطلب.`,
      payload,
    );
    this.realtime.emitToUser(receiverId, 'cp:request', {
      ...payload,
      title: 'هدية وطلب ارتباط جديد',
      body: `أرسل لك ${senderName} هدية ${this.cpTypeLabel(type)}.`,
    });
    if (roomId) {
      this.realtime.emitToRoom(roomId, 'room:event', {
        roomId,
        event: 'cp:request',
        payload: { ...payload, title: 'هدية ارتباط', body: `أرسل ${senderName} هدية ارتباط.` },
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
  async recordGiftInteraction(senderId: string, receiverId: string, giftSendId: string, totalCoins: number) {
    if (!senderId || !receiverId || !giftSendId || senderId === receiverId) return;
    const relationships = await this.dataSource.query(
      `SELECT id,type,level FROM cp_relationships WHERE status='active'
       AND (("userAId"=$1 AND "userBId"=$2) OR ("userAId"=$2 AND "userBId"=$1))`,
      [senderId, receiverId],
    );
    for (const relationship of relationships) {
      const [setting] = await this.dataSource.query(
        'SELECT value FROM app_settings WHERE key=$1',
        [`cp.points.gift_send.${relationship.type}`],
      );
      const basePoints = Number(setting?.value);
      if (!Number.isSafeInteger(basePoints) || basePoints <= 0) continue;
      const [levelSetting] = await this.dataSource.query(
        'SELECT value FROM app_settings WHERE key=$1',
        [`cp.levels.${relationship.type}`],
      );
      let levelConfig: any = null;
      try {
        const levelRows = JSON.parse(levelSetting?.value || '[]');
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
        const levels = JSON.parse(levelConfig?.value || '[]') as Array<{ level: number; pointsRequired: number }>;
        for (const item of levels) if (Number(item.pointsRequired) <= Number(total)) level = Math.max(level, Number(item.level) || 1);
      } catch { /* malformed admin config never blocks point ledger writes */ }
      const [maxRow] = await manager.query('SELECT value FROM app_settings WHERE key=$1', [`cp.max_level.${relationship.type}`]);
      const maxLevel = Number(maxRow?.value);
      if (Number.isInteger(maxLevel) && maxLevel > 0) level = Math.min(level, maxLevel);
      // Permanent relationship levels are monotonic: changing dashboard thresholds
      // or the weekly leaderboard can never lower an already-earned level.
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
      let currentLevelConfig: any = null;
      try {
        const levels = JSON.parse(levelsSetting?.value || '[]');
        currentLevelConfig = Array.isArray(levels) ? levels.find(item => Number(item?.level) === Number(result.level)) || null : null;
      } catch { currentLevelConfig = null; }
      const payload = {
        type: 'cp_level_up', relationshipId, level: result.level,
        previousLevel: result.previousLevel, points: result.points,
        levelName: currentLevelConfig?.name || currentLevelConfig?.title || currentLevelConfig?.presentation?.title || null,
        presentation: currentLevelConfig?.presentation || null,
        benefits: currentLevelConfig?.benefits || currentLevelConfig?.rewards || [],
      };
      const title = 'ارتفع مستوى CB!';
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
