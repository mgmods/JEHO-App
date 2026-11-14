import {
  BadRequestException,
  Injectable,
  Logger,
  ServiceUnavailableException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';

export type InternetMusicHit = {
  id: string;
  title: string;
  artist: string;
  thumbnailUrl: string | null;
  durationSec: number | null;
  source: 'youtube';
};

export type InternetMusicResolved = {
  id: string;
  title: string;
  artist: string;
  thumbnailUrl: string | null;
  audioUrl: string;
  mimeType: string | null;
  expiresHintSec: number;
  source: 'youtube';
};

type PipedSearchItem = {
  url?: string;
  title?: string;
  thumbnail?: string;
  uploaderName?: string;
  uploader?: string;
  duration?: number;
  type?: string;
};

type PipedStream = {
  url?: string;
  bitrate?: number;
  mimeType?: string;
  quality?: string;
};

type PipedStreamsResponse = {
  title?: string;
  uploader?: string;
  thumbnailUrl?: string;
  audioStreams?: PipedStream[];
};

@Injectable()
export class InternetMusicService {
  private readonly logger = new Logger(InternetMusicService.name);
  private readonly resolveCache = new Map<
    string,
    { at: number; data: InternetMusicResolved }
  >();

  constructor(private readonly configService: ConfigService) {}

  private pipedInstances(): string[] {
    const raw =
      this.configService.get<string>('app.internetMusic.pipedInstances') || '';
    const fromEnv = raw
      .split(',')
      .map((s) => s.trim().replace(/\/$/, ''))
      .filter(Boolean);
    const defaults = [
      'https://api.piped.private.coffee',
      'https://pipedapi.kavin.rocks',
      'https://pipedapi.adminforge.de',
    ];
    return [...fromEnv, ...defaults.filter((d) => !fromEnv.includes(d))];
  }

  async search(query: string): Promise<{ items: InternetMusicHit[] }> {
    const q = (query || '').trim();
    if (q.length < 2) {
      throw new BadRequestException('Search query is too short');
    }
    if (q.length > 80) {
      throw new BadRequestException('Search query is too long');
    }

    try {
      const items = await this.searchInnertube(q);
      if (items.length) return { items };
    } catch (err) {
      this.logger.warn(
        `innertube search failed: ${
          err instanceof Error ? err.message : String(err)
        }`,
      );
    }

    const encoded = encodeURIComponent(q);
    let lastError: unknown;
    for (const base of this.pipedInstances()) {
      try {
        const items = await this.searchPiped(base, encoded, 'music_songs');
        if (items.length) return { items };
        const all = await this.searchPiped(base, encoded, 'all');
        if (all.length) return { items: all };
      } catch (err) {
        lastError = err;
        this.logger.warn(
          `piped search failed via ${base}: ${
            err instanceof Error ? err.message : String(err)
          }`,
        );
      }
    }

    this.logger.error('All internet music search backends failed', lastError);
    throw new ServiceUnavailableException(
      'تعذر البحث حالياً. حاول مرة أخرى بعد قليل',
    );
  }

  async resolve(videoId: string): Promise<InternetMusicResolved> {
    const id = this.normalizeVideoId(videoId);
    if (!id) throw new BadRequestException('Invalid video id');
    const cached = this.resolveCache.get(id);
    if (cached && Date.now() - cached.at < 25 * 60_000) {
      return cached.data;
    }

    try {
      const resolved = await this.resolveInnertube(id);
      this.resolveCache.set(id, { at: Date.now(), data: resolved });
      return resolved;
    } catch (err) {
      this.logger.warn(
        `innertube resolve failed: ${
          err instanceof Error ? err.message : String(err)
        }`,
      );
    }

    let lastError: unknown;
    for (const base of this.pipedInstances()) {
      try {
        const data = await this.fetchJson<PipedStreamsResponse>(
          `${base}/streams/${encodeURIComponent(id)}`,
          15_000,
        );
        const streams = Array.isArray(data?.audioStreams)
          ? [...data.audioStreams]
          : [];
        streams.sort(
          (a, b) => Number(b.bitrate || 0) - Number(a.bitrate || 0),
        );
        const preferred =
          streams.find((s) =>
            /mp4|m4a|aac/i.test(`${s.mimeType || ''} ${s.quality || ''}`),
          ) || streams[0];
        if (!preferred?.url) continue;
        const resolved: InternetMusicResolved = {
          id,
          title: (data.title || 'موسيقى').slice(0, 180),
          artist: (data.uploader || 'يوتيوب').slice(0, 180),
          thumbnailUrl: data.thumbnailUrl || null,
          audioUrl: preferred.url,
          mimeType: preferred.mimeType || null,
          expiresHintSec: 50 * 60,
          source: 'youtube',
        };
        this.resolveCache.set(id, { at: Date.now(), data: resolved });
        return resolved;
      } catch (err) {
        lastError = err;
        this.logger.warn(
          `piped resolve failed via ${base}: ${
            err instanceof Error ? err.message : String(err)
          }`,
        );
      }
    }

    this.logger.error('All internet music resolve backends failed', lastError);
    throw new ServiceUnavailableException(
      'تعذر تشغيل هذه الأغنية حالياً. جرّب أغنية أخرى',
    );
  }

  normalizeVideoId(raw: string | null | undefined): string | null {
    if (!raw) return null;
    const value = raw.trim();
    const ytScheme = value.match(/^yt:\/\/([A-Za-z0-9_-]{6,20})$/i);
    if (ytScheme) return ytScheme[1];
    if (/^[A-Za-z0-9_-]{6,20}$/.test(value)) return value;
    try {
      const u = new URL(value);
      if (u.hostname.includes('youtu.be')) {
        const id = u.pathname.replace(/^\//, '').slice(0, 20);
        return /^[A-Za-z0-9_-]{6,20}$/.test(id) ? id : null;
      }
      if (u.hostname.includes('youtube.com')) {
        const v = u.searchParams.get('v');
        if (v && /^[A-Za-z0-9_-]{6,20}$/.test(v)) return v;
      }
    } catch {
      /* ignore */
    }
    return null;
  }

  private async searchInnertube(query: string): Promise<InternetMusicHit[]> {
    const data = await this.fetchJson<Record<string, unknown>>(
      'https://www.youtube.com/youtubei/v1/search?prettyPrint=false',
      15_000,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'User-Agent':
            'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
          Accept: '*/*',
        },
        body: JSON.stringify({
          context: {
            client: {
              clientName: 'WEB',
              clientVersion: '2.20240411.09.00',
              hl: 'ar',
              gl: 'IQ',
            },
          },
          query,
        }),
      },
    );

    const hits: InternetMusicHit[] = [];
    const seen = new Set<string>();
    this.walkJson(data, (node) => {
      if (!node || typeof node !== 'object' || Array.isArray(node)) return;
      const vr = (node as Record<string, unknown>).videoRenderer as
        | Record<string, unknown>
        | undefined;
      if (!vr) return;
      const id = typeof vr.videoId === 'string' ? vr.videoId : null;
      if (!id || !/^[A-Za-z0-9_-]{6,20}$/.test(id) || seen.has(id)) return;
      const title = this.runsText(vr.title).trim();
      if (!title) return;
      const artist =
        this.runsText(vr.ownerText) ||
        this.runsText(vr.longBylineText) ||
        this.runsText(vr.shortBylineText) ||
        'يوتيوب';
      const thumb = this.bestThumbnail(vr.thumbnail);
      const durationSec =
        this.parseDurationText(this.runsText(vr.lengthText)) ??
        (typeof vr.lengthSeconds === 'string'
          ? Number(vr.lengthSeconds) || null
          : typeof vr.lengthSeconds === 'number'
            ? vr.lengthSeconds
            : null);
      seen.add(id);
      hits.push({
        id,
        title: title.slice(0, 180),
        artist: artist.slice(0, 180),
        thumbnailUrl: thumb,
        durationSec:
          durationSec && durationSec > 0 ? Math.floor(durationSec) : null,
        source: 'youtube',
      });
    });
    return hits.slice(0, 30);
  }

  private async resolveInnertube(
    videoId: string,
  ): Promise<InternetMusicResolved> {
    const data = await this.fetchJson<Record<string, unknown>>(
      'https://www.youtube.com/youtubei/v1/player?prettyPrint=false',
      18_000,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'User-Agent':
            'com.google.android.youtube/20.10.38 (Linux; U; Android 14) gzip',
          Accept: '*/*',
        },
        body: JSON.stringify({
          context: {
            client: {
              clientName: 'ANDROID',
              clientVersion: '20.10.38',
              androidSdkVersion: 34,
              hl: 'en',
              gl: 'US',
            },
          },
          videoId,
          contentCheckOk: true,
          racyCheckOk: true,
        }),
      },
    );

    const status =
      ((data.playabilityStatus as Record<string, unknown> | undefined)
        ?.status as string | undefined) || '';
    if (status && status !== 'OK') {
      throw new Error(`playability ${status}`);
    }
    const streaming = data.streamingData as
      | Record<string, unknown>
      | undefined;
    const formats = [
      ...((streaming?.adaptiveFormats as Record<string, unknown>[]) || []),
      ...((streaming?.formats as Record<string, unknown>[]) || []),
    ];
    const audio = formats
      .filter((f) => {
        const mime = String(f.mimeType || '');
        const url = String(f.url || '');
        return mime.startsWith('audio/') && !!url;
      })
      .sort(
        (a, b) => Number(b.bitrate || b.averageBitrate || 0)
          - Number(a.bitrate || a.averageBitrate || 0),
      );
    const preferred =
      audio.find((f) => /mp4|mp4a|aac/i.test(String(f.mimeType || ''))) ||
      audio[0];
    if (!preferred?.url) {
      throw new Error('no audio stream url');
    }

    const details = data.videoDetails as Record<string, unknown> | undefined;
    const thumbs =
      ((details?.thumbnail as Record<string, unknown> | undefined)
        ?.thumbnails as Record<string, unknown>[]) || [];
    const thumbUrl =
      thumbs.length > 0
        ? String(thumbs[thumbs.length - 1].url || '') || null
        : null;

    return {
      id: videoId,
      title: String(details?.title || 'موسيقى').slice(0, 180),
      artist: String(details?.author || 'يوتيوب').slice(0, 180),
      thumbnailUrl: thumbUrl,
      audioUrl: String(preferred.url),
      mimeType: preferred.mimeType ? String(preferred.mimeType) : null,
      expiresHintSec: 50 * 60,
      source: 'youtube',
    };
  }

  private async searchPiped(
    base: string,
    encodedQuery: string,
    filter: string,
  ): Promise<InternetMusicHit[]> {
    const res = await this.fetchJson<unknown>(
      `${base}/search?q=${encodedQuery}&filter=${filter}`,
      12_000,
    );
    const rows = this.normalizePipedSearchRows(res);
    return rows
      .map((row) => this.mapPipedSearchItem(row))
      .filter((x): x is InternetMusicHit => !!x)
      .slice(0, 30);
  }

  private normalizePipedSearchRows(res: unknown): PipedSearchItem[] {
    if (Array.isArray(res)) return res as PipedSearchItem[];
    if (res && typeof res === 'object') {
      const items = (res as { items?: unknown }).items;
      if (Array.isArray(items)) return items as PipedSearchItem[];
    }
    return [];
  }

  private mapPipedSearchItem(row: PipedSearchItem): InternetMusicHit | null {
    if (!row) return null;
    const type = (row.type || '').toLowerCase();
    if (type && type !== 'stream' && type !== 'video') return null;
    const id = this.idFromWatchUrl(row.url || '');
    if (!id) return null;
    const title = (row.title || '').trim();
    if (!title) return null;
    return {
      id,
      title: title.slice(0, 180),
      artist: ((row.uploaderName || row.uploader || 'يوتيوب') + '').slice(0, 180),
      thumbnailUrl: row.thumbnail || null,
      durationSec:
        typeof row.duration === 'number' && row.duration > 0
          ? Math.floor(row.duration)
          : null,
      source: 'youtube',
    };
  }

  private idFromWatchUrl(url: string): string | null {
    if (!url) return null;
    const m = url.match(/[?&]v=([A-Za-z0-9_-]{6,20})/);
    if (m) return m[1];
    const path = url.match(/\/watch\?v=([A-Za-z0-9_-]{6,20})/);
    if (path) return path[1];
    if (/^[A-Za-z0-9_-]{6,20}$/.test(url)) return url;
    return this.normalizeVideoId(url);
  }

  private runsText(node: unknown): string {
    if (!node || typeof node !== 'object') return '';
    const runs = (node as { runs?: Array<{ text?: string }> }).runs;
    if (!Array.isArray(runs)) return '';
    return runs.map((r) => r?.text || '').join('').trim();
  }

  private bestThumbnail(node: unknown): string | null {
    if (!node || typeof node !== 'object') return null;
    const thumbs = (node as { thumbnails?: Array<{ url?: string }> })
      .thumbnails;
    if (!Array.isArray(thumbs) || !thumbs.length) return null;
    const url = thumbs[thumbs.length - 1]?.url;
    return url ? String(url) : null;
  }

  private parseDurationText(text: string): number | null {
    if (!text) return null;
    const parts = text
      .trim()
      .split(':')
      .map((p) => Number(p));
    if (parts.some((n) => Number.isNaN(n))) return null;
    if (parts.length === 3) return parts[0] * 3600 + parts[1] * 60 + parts[2];
    if (parts.length === 2) return parts[0] * 60 + parts[1];
    if (parts.length === 1) return parts[0];
    return null;
  }

  private walkJson(
    node: unknown,
    visit: (value: Record<string, unknown> | unknown[]) => void,
  ): void {
    if (!node || typeof node !== 'object') return;
    visit(node as Record<string, unknown> | unknown[]);
    if (Array.isArray(node)) {
      for (const child of node) this.walkJson(child, visit);
      return;
    }
    for (const child of Object.values(node as Record<string, unknown>)) {
      this.walkJson(child, visit);
    }
  }

  private async fetchJson<T>(
    url: string,
    timeoutMs: number,
    init?: RequestInit,
  ): Promise<T> {
    const ctrl = new AbortController();
    const timer = setTimeout(() => ctrl.abort(), timeoutMs);
    try {
      const res = await fetch(url, {
        method: init?.method || 'GET',
        signal: ctrl.signal,
        headers: {
          Accept: 'application/json',
          'User-Agent': 'JEHO-CHAT-Music/1.0',
          ...(init?.headers || {}),
        },
        body: init?.body,
      });
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`);
      }
      return (await res.json()) as T;
    } finally {
      clearTimeout(timer);
    }
  }
}
