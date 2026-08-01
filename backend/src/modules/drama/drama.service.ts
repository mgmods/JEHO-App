import {
  BadRequestException,
  Injectable,
  Logger,
  NotFoundException,
  OnModuleInit,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { createHmac } from 'crypto';
import { Repository } from 'typeorm';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { WalletService } from '../wallet/wallet.service';

export const DRAMA_ENABLED_KEY = 'drama.enabled';
export const DRAMA_ADMOB_KEY = 'drama.admob';
export const DRAMA_SOURCE_KEY = 'drama.source';
export const DRAMA_REWARDS_KEY = 'drama.rewards';

export type DramaAdmobConfig = {
  adsEnabled: boolean;
  testMode: boolean;
  appId: string;
  bannerId: string;
  interstitialId: string;
  rewardedId: string;
  rewardedInterval: number;
  freeEpisodesCount: number;
};

export type DramaRewardsConfig = {
  enabled: boolean;
  coinsPerEpisode: number;
  /** Fraction of episode that must be watched before claim (0.5–0.95). */
  watchThreshold: number;
};

/** Proxies Fumo / Dramaxbox PHP catalog (same API as the reference app). */
export type DramaSourceConfig = {
  baseUrl: string;
  packageName: string;
  signature: string;
  integritySecret: string;
};

const DEFAULT_ADMOB: DramaAdmobConfig = {
  adsEnabled: true,
  testMode: false,
  appId: '',
  bannerId: '',
  interstitialId: '',
  rewardedId: '',
  rewardedInterval: 1,
  freeEpisodesCount: 1,
};

const DEFAULT_REWARDS: DramaRewardsConfig = {
  enabled: true,
  coinsPerEpisode: 5,
  watchThreshold: 0.8,
};

/** Defaults match Fumo_Drama access/secure-config.properties */
const DEFAULT_SOURCE: DramaSourceConfig = {
  baseUrl: 'https://dramaxbox.bbs.tr/',
  packageName: 'com.wow.gazar',
  signature:
    '04:B9:9B:63:15:8A:51:D8:45:00:E6:4C:B4:CB:4D:1B:B7:05:D5:D2:F8:41:1B:88:46:D6:8A:D0:0C:B4:A3:8A',
  integritySecret: 'FumoDrama_2026_Native_9rF2kL8sPq4VzX7m',
};

@Injectable()
export class DramaService implements OnModuleInit {
  private readonly log = new Logger(DramaService.name);

  constructor(
    @InjectRepository(AppSetting)
    private readonly settingsRepo: Repository<AppSetting>,
    private readonly wallet: WalletService,
  ) {}

  async onModuleInit() {
    await this.ensureDefaults();
  }

  async ensureDefaults() {
    await this.upsertSettingIfMissing(DRAMA_ENABLED_KEY, 'true');
    await this.upsertSettingIfMissing(
      DRAMA_ADMOB_KEY,
      JSON.stringify(DEFAULT_ADMOB),
    );
    await this.upsertSettingIfMissing(
      DRAMA_SOURCE_KEY,
      JSON.stringify(DEFAULT_SOURCE),
    );
    await this.upsertSettingIfMissing(
      DRAMA_REWARDS_KEY,
      JSON.stringify(DEFAULT_REWARDS),
    );
  }

  private async upsertSettingIfMissing(key: string, value: string) {
    const row = await this.settingsRepo.findOne({ where: { key } });
    if (!row) {
      await this.settingsRepo.save(this.settingsRepo.create({ key, value }));
    }
  }

  private async readJsonSetting<T>(key: string, fallback: T): Promise<T> {
    const row = await this.settingsRepo.findOne({ where: { key } });
    if (!row?.value) return { ...fallback } as T;
    try {
      return { ...(fallback as object), ...JSON.parse(row.value) } as T;
    } catch {
      return { ...fallback } as T;
    }
  }

  async getSourceConfig(): Promise<DramaSourceConfig> {
    return this.readJsonSetting(DRAMA_SOURCE_KEY, DEFAULT_SOURCE);
  }

  async getConfig() {
    const enabledRow = await this.settingsRepo.findOne({
      where: { key: DRAMA_ENABLED_KEY },
    });
    const admob = await this.readJsonSetting(DRAMA_ADMOB_KEY, DEFAULT_ADMOB);
    const rewards = await this.readJsonSetting(
      DRAMA_REWARDS_KEY,
      DEFAULT_REWARDS,
    );
    const source = await this.getSourceConfig();
    const enabled =
      enabledRow?.value == null
        ? true
        : String(enabledRow.value).toLowerCase() !== 'false' &&
          enabledRow.value !== '0';
    return {
      enabled,
      ads: {
        ...admob,
        adsEnabled: !!admob.adsEnabled,
        testMode: false,
        rewardedInterval: Math.max(1, Number(admob.rewardedInterval) || 1),
        freeEpisodesCount: Math.max(0, Number(admob.freeEpisodesCount) || 0),
      },
      rewards: {
        enabled: rewards.enabled !== false,
        coinsPerEpisode: Math.max(0, Number(rewards.coinsPerEpisode) || 0),
        watchThreshold: Math.min(
          0.95,
          Math.max(0.5, Number(rewards.watchThreshold) || 0.8),
        ),
      },
      source: {
        baseUrl: source.baseUrl,
        packageName: source.packageName,
      },
    };
  }

  async adminGetSettings() {
    const cfg = await this.getConfig();
    const source = await this.getSourceConfig();
    return { ...cfg, source };
  }

  async adminPatchSettings(body: {
    enabled?: boolean;
    ads?: Partial<DramaAdmobConfig>;
    rewards?: Partial<DramaRewardsConfig>;
    source?: Partial<DramaSourceConfig>;
  }) {
    if (typeof body.enabled === 'boolean') {
      let row = await this.settingsRepo.findOne({
        where: { key: DRAMA_ENABLED_KEY },
      });
      if (!row) row = this.settingsRepo.create({ key: DRAMA_ENABLED_KEY });
      row.value = body.enabled ? 'true' : 'false';
      await this.settingsRepo.save(row);
    }
    if (body.ads && typeof body.ads === 'object') {
      const current = await this.readJsonSetting(DRAMA_ADMOB_KEY, DEFAULT_ADMOB);
      let row = await this.settingsRepo.findOne({
        where: { key: DRAMA_ADMOB_KEY },
      });
      if (!row) row = this.settingsRepo.create({ key: DRAMA_ADMOB_KEY });
      // Never keep / enable Google test inventory — production units only.
      const merged = { ...current, ...body.ads, testMode: false };
      row.value = JSON.stringify(merged);
      await this.settingsRepo.save(row);
    }
    if (body.rewards && typeof body.rewards === 'object') {
      const current = await this.readJsonSetting(
        DRAMA_REWARDS_KEY,
        DEFAULT_REWARDS,
      );
      let row = await this.settingsRepo.findOne({
        where: { key: DRAMA_REWARDS_KEY },
      });
      if (!row) row = this.settingsRepo.create({ key: DRAMA_REWARDS_KEY });
      row.value = JSON.stringify({ ...current, ...body.rewards });
      await this.settingsRepo.save(row);
    }
    if (body.source && typeof body.source === 'object') {
      const current = await this.getSourceConfig();
      let row = await this.settingsRepo.findOne({
        where: { key: DRAMA_SOURCE_KEY },
      });
      if (!row) row = this.settingsRepo.create({ key: DRAMA_SOURCE_KEY });
      row.value = JSON.stringify({ ...current, ...body.source });
      await this.settingsRepo.save(row);
    }
    return this.adminGetSettings();
  }

  async claimEpisodeReward(userId: string, episodeId: string) {
    await this.assertEnabled();
    const cfg = await this.getConfig();
    if (!cfg.rewards.enabled || cfg.rewards.coinsPerEpisode <= 0) {
      return {
        credited: false,
        coins: 0,
        alreadyClaimed: false,
        balance: 0,
        message: 'rewards_disabled',
      };
    }
    const id = String(episodeId || '').trim();
    // Reject obviously forged ids (empty / oversized / path injection).
    if (!id || id.length > 64 || /[^\w.-]/.test(id)) {
      throw new BadRequestException('Invalid episode');
    }
    // Require a prior view marker so random id spam cannot mint.
    const viewKey = `drama.view.${userId}.${id}`;
    const viewed = await this.settingsRepo.findOne({ where: { key: viewKey } });
    if (!viewed) {
      throw new BadRequestException('Watch the episode before claiming');
    }
    // Daily soft cap: max 20 episode claims / user / day.
    const day = new Date().toISOString().slice(0, 10);
    const capKey = `drama.claim.${userId}.${day}`;
    const capRow = await this.settingsRepo.findOne({ where: { key: capKey } });
    const used = Number(capRow?.value || 0);
    if (used >= 20) {
      return {
        credited: false,
        coins: 0,
        alreadyClaimed: false,
        balance: 0,
        message: 'daily_cap',
      };
    }
    const result = await this.wallet.creditDramaWatchReward(
      userId,
      id,
      cfg.rewards.coinsPerEpisode,
    );
    if (result.credited) {
      if (capRow) {
        capRow.value = String(used + 1);
        await this.settingsRepo.save(capRow);
      } else {
        await this.settingsRepo.save(
          this.settingsRepo.create({ key: capKey, value: '1' }),
        );
      }
    }
    return result;
  }

  private assertEnabled = async () => {
    const cfg = await this.getConfig();
    if (!cfg.enabled) {
      throw new BadRequestException('Drama feature is disabled');
    }
    return cfg;
  };

  /** Same integrity signing as Fumo TamperDetection + security_check.php */
  private async signedFetch(
    pathWithQuery: string,
    init: RequestInit = {},
  ): Promise<any> {
    const source = await this.getSourceConfig();
    const base = source.baseUrl.endsWith('/')
      ? source.baseUrl
      : `${source.baseUrl}/`;
    const url = new URL(
      pathWithQuery.replace(/^\//, ''),
      base,
    );
    const method = (init.method || 'GET').toUpperCase();
    const pathOnly = url.pathname || '/';
    const timestamp = Date.now();
    const payload = `${source.packageName}|${source.signature}|${method}|${pathOnly}|${timestamp}`;
    const integrity = createHmac('sha256', source.integritySecret)
      .update(payload)
      .digest('base64');

    const headers: Record<string, string> = {
      Accept: 'application/json',
      'User-Agent': 'HamsLive/1.0 (Android; DramaProxy)',
      'X-App-Package': source.packageName,
      'X-App-Signature': source.signature,
      'X-Timestamp': String(timestamp),
      'X-App-Integrity': integrity,
      ...(init.headers as Record<string, string> | undefined),
    };

    const res = await fetch(url.toString(), {
      ...init,
      method,
      headers,
    });
    const text = await res.text();
    let json: any = null;
    try {
      json = text ? JSON.parse(text) : null;
    } catch {
      this.log.warn(`Drama remote non-JSON ${res.status} ${url.pathname}`);
    }
    if (!res.ok) {
      throw new BadRequestException(
        json?.message || json?.error || `Drama source HTTP ${res.status}`,
      );
    }
    return json;
  }

  private mapSeries(row: any) {
    const views = Number(
      row.total_views ??
        row.views_count ??
        row.totalViews ??
        row.views ??
        row.view_count ??
        0,
    );
    const likes = Number(
      row.total_likes ??
        row.likes_count ??
        row.totalLikes ??
        row.likes ??
        row.like_count ??
        0,
    );
    return {
      id: String(row.id),
      title: row.title || '',
      description: row.description || '',
      coverUrl: row.imageName || row.coverUrl || null,
      category: row.category_id != null ? String(row.category_id) : null,
      isFeatured: !!row.isFeatured,
      episodeCount: Number(
        row.episode_count ?? row.episodeCount ?? row.episodes_count ?? 0,
      ),
      totalViews: Number.isFinite(views) ? views : 0,
      totalLikes: Number.isFinite(likes) ? likes : 0,
      isLiked: !!row.is_liked,
      firstEpisodeUrl: row.first_episode_url || null,
      isPaid: !!row.is_paid,
    };
  }

  private mapEpisode(row: any, seriesId: string) {
    const views = Number(
      row.view_count ?? row.views_count ?? row.views ?? row.viewCount ?? 0,
    );
    const likes = Number(
      row.like_count ?? row.likes_count ?? row.likes ?? row.likeCount ?? 0,
    );
    return {
      id: String(row.id),
      seriesId,
      title: row.title || '',
      episodeNumber: Number(row.episode_number || 0),
      videoUrl: row.videoUrl || '',
      thumbnailUrl: null as string | null,
      durationSec: 0,
      viewCount: Number.isFinite(views) ? views : 0,
      likeCount: Number.isFinite(likes) ? likes : 0,
      isLiked: !!row.is_liked,
      isLocked: !!row.isLocked,
    };
  }

  async listSeries(_userId?: string) {
    await this.assertEnabled();
    const json = await this.signedFetch('get_series.php');
    if (json?.status && json.status !== 'success') {
      throw new BadRequestException(json.message || 'Failed to load series');
    }
    const list = Array.isArray(json?.data) ? json.data : [];
    return list.map((row: any) => this.mapSeries(row));
  }

  async getSeries(id: string, userId?: string) {
    await this.assertEnabled();
    const seriesId = String(id).trim();
    if (!/^\d+$/.test(seriesId)) {
      throw new BadRequestException('Invalid series id');
    }

    // Catalog row (cover / stats)
    const catalog = await this.signedFetch('get_series.php');
    const all = Array.isArray(catalog?.data) ? catalog.data : [];
    const found = all.find((s: any) => String(s.id) === seriesId);
    if (!found) throw new NotFoundException('Series not found');

    const epJson = await this.signedFetch(
      `get_episodes.php?series_id=${encodeURIComponent(seriesId)}`,
    );
    if (epJson?.status && epJson.status !== 'success') {
      throw new BadRequestException(epJson.message || 'Failed to load episodes');
    }
    const episodes = Array.isArray(epJson?.data) ? epJson.data : [];

    // Prefer current stats from views_likes when possible
    let mapped = this.mapSeries(found);
    try {
      const uid = userId || 'anonymous';
      const stats = await this.signedFetch(
        `views_likes_api.php?series_id=${encodeURIComponent(seriesId)}&uid=${encodeURIComponent(uid)}`,
      );
      if (stats?.status === 'success' || stats?.likes_count != null) {
        mapped = {
          ...mapped,
          totalLikes: Number(stats.likes_count ?? mapped.totalLikes),
          totalViews: Number(stats.views_count ?? mapped.totalViews),
          isLiked: !!stats.is_liked,
        };
      }
    } catch {
      /* keep catalog stats */
    }

    return {
      ...mapped,
      episodes: episodes.map((ep: any) => this.mapEpisode(ep, seriesId)),
    };
  }

  async recordView(
    userId: string,
    targetType: 'series' | 'episode',
    targetId: string,
  ) {
    await this.assertEnabled();
    const uid = userId || 'anonymous';
    const body = new URLSearchParams();
    body.set('action', 'view');
    body.set('uid', uid);
    if (targetType === 'series') body.set('series_id', targetId);
    else body.set('episode_id', targetId);

    const json = await this.signedFetch('views_likes_api.php', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: body.toString(),
    });

    // Local proof for claim-reward (episode only, authenticated users).
    if (targetType === 'episode' && userId) {
      const viewKey = `drama.view.${userId}.${String(targetId).slice(0, 64)}`;
      const existing = await this.settingsRepo.findOne({ where: { key: viewKey } });
      if (!existing) {
        await this.settingsRepo.save(
          this.settingsRepo.create({
            key: viewKey,
            value: String(Date.now()),
          }),
        );
      }
    }

    return {
      views: Number(json?.views_count ?? 0),
      likes: Number(json?.likes_count ?? 0),
    };
  }

  async toggleLike(
    userId: string,
    targetType: 'series' | 'episode',
    targetId: string,
  ) {
    await this.assertEnabled();
    const uid = userId || 'anonymous';

    // Read current liked state
    let isLiked = false;
    try {
      const q =
        targetType === 'series'
          ? `views_likes_api.php?series_id=${encodeURIComponent(targetId)}&uid=${encodeURIComponent(uid)}`
          : `views_likes_api.php?episode_id=${encodeURIComponent(targetId)}&uid=${encodeURIComponent(uid)}`;
      const stats = await this.signedFetch(q);
      isLiked = !!stats?.is_liked;
    } catch {
      isLiked = false;
    }

    const action = isLiked ? 'unlike' : 'like';
    const body = new URLSearchParams();
    body.set('action', action);
    body.set('uid', uid);
    if (targetType === 'series') body.set('series_id', targetId);
    else body.set('episode_id', targetId);

    const json = await this.signedFetch('views_likes_api.php', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: body.toString(),
    });
    return {
      liked: action === 'like',
      likes: Number(json?.likes_count ?? 0),
    };
  }

  // ─── Admin catalog helpers (remote-backed list for dashboard) ───

  async adminListSeries() {
    try {
      // Admin preview should work even if the in-app Drama tab is disabled.
      const json = await this.signedFetch('get_series.php');
      if (json?.status && json.status !== 'success') {
        this.log.warn(`adminListSeries status: ${json.message || json.status}`);
        return [];
      }
      const list = Array.isArray(json?.data) ? json.data : [];
      return list.map((row: any) => this.mapSeries(row));
    } catch (e: any) {
      this.log.warn(`adminListSeries: ${e?.message || e}`);
      return [];
    }
  }

  async adminGetSeries(id: string) {
    const seriesId = String(id).trim();
    if (!/^\d+$/.test(seriesId)) {
      throw new BadRequestException('Invalid series id');
    }
    const catalog = await this.signedFetch('get_series.php');
    const all = Array.isArray(catalog?.data) ? catalog.data : [];
    const found = all.find((s: any) => String(s.id) === seriesId);
    if (!found) throw new NotFoundException('Series not found');

    const epJson = await this.signedFetch(
      `get_episodes.php?series_id=${encodeURIComponent(seriesId)}`,
    );
    if (epJson?.status && epJson.status !== 'success') {
      throw new BadRequestException(epJson.message || 'Failed to load episodes');
    }
    const episodes = Array.isArray(epJson?.data) ? epJson.data : [];
    return {
      ...this.mapSeries(found),
      episodes: episodes.map((ep: any) => this.mapEpisode(ep, seriesId)),
    };
  }

  /** Local CRUD disabled — catalog lives on dramaxbox. */
  async adminCreateSeries(_body: unknown) {
    throw new BadRequestException(
      'المسلسلات تُدار على سيرفر الدراما (dramaxbox). عندنا نعرضها فقط.',
    );
  }
  async adminUpdateSeries(_id: string, _body: unknown) {
    throw new BadRequestException(
      'المسلسلات تُدار على سيرفر الدراما (dramaxbox). عندنا نعرضها فقط.',
    );
  }
  async adminDeleteSeries(_id: string) {
    throw new BadRequestException(
      'المسلسلات تُدار على سيرفر الدراما (dramaxbox). عندنا نعرضها فقط.',
    );
  }
  async adminCreateEpisode(_seriesId: string, _body: unknown) {
    throw new BadRequestException(
      'الحلقات تُدار على سيرفر الدراما (dramaxbox). عندنا نعرضها فقط.',
    );
  }
  async adminUpdateEpisode(_id: string, _body: unknown) {
    throw new BadRequestException(
      'الحلقات تُدار على سيرفر الدراما (dramaxbox). عندنا نعرضها فقط.',
    );
  }
  async adminDeleteEpisode(_id: string) {
    throw new BadRequestException(
      'الحلقات تُدار على سيرفر الدراما (dramaxbox). عندنا نعرضها فقط.',
    );
  }
}
