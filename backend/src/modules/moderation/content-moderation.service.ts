import {
  BadRequestException,
  Injectable,
  Logger,
  OnModuleInit,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { existsSync, readFileSync, unlinkSync } from 'fs';
import { extname } from 'path';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { User, UserStatus } from '../../database/entities/user.entity';

export type ModerationHit = {
  ok: false;
  code: string;
  reason: string;
  action: 'block' | 'mute' | 'kick' | 'ban';
  score?: number;
};

export type ModerationPass = { ok: true };

export type ModerationResult = ModerationHit | ModerationPass;

@Injectable()
export class ContentModerationService implements OnModuleInit {
  private readonly log = new Logger(ContentModerationService.name);

  /** Built-in promo / link / adult text patterns (Arabic + Latin). */
  private readonly defaultBlocked = [
    // Links & invites
    /https?:\/\/\S+/i,
    /www\.\S+/i,
    /\b(?:t\.me|telegram\.me|wa\.me|bit\.ly|goo\.gl)\b/i,
    /(?:تليجرام|تلغرام|تيليجرام|واتس|واتساب|انستا|انستغرام|سناب)/i,
    /(?:telegram|whatsapp|instagram|snapchat|tiktok|discord)\.?(?:com|me)?/i,
    // App promo
    /(?:حمل|نزّل|نزل)\s*(?:التطبيق|البرنامج|الابلكيشن|الآب)/i,
    /(?:download|install)\s+(?:app|apk)/i,
    /\.apk\b/i,
    /(?:كود\s*دعوة|رابط\s*دعوة|invite\s*code)/i,
    // Adult / sexual
    /(?:سكس|جنس|نيك|زب|كس\b|عري|عارية|سكسى|اباحي|إباحي)/i,
    /(?:porn|xxx|onlyfans|nude|naked|sex\b)/i,
  ];

  constructor(
    @InjectRepository(AppSetting)
    private readonly settingsRepo: Repository<AppSetting>,
    @InjectRepository(User)
    private readonly usersRepo: Repository<User>,
  ) {}

  async onModuleInit() {
    const defaults: Array<[string, string, string]> = [
      ['auto_moderation', 'true', 'Enable automated chat/image moderation'],
      ['autoModeration', 'true', 'Enable automated chat/image moderation (dashboard key)'],
      ['chat_promo_filter_enabled', 'true', 'Block promo links in room/DM chat'],
      ['chat_promo_kick_enabled', 'true', 'Kick from room on promo/link violation'],
      ['chat_promo_mute_first', 'true', 'Mute mic then kick on promo violation'],
      [
        'chat_blocked_extra_keywords',
        '',
        'Extra banned keywords (comma or newline separated)',
      ],
      ['live_nsfw_enabled', 'true', 'Scan uploaded images for nudity'],
      ['live_nsfw_confidence', '0.72', 'NSFW skin-ratio threshold 0-1'],
      ['live_nsfw_warn_strikes', '1', 'Warn after N NSFW strikes'],
      ['live_nsfw_mute_strikes', '2', 'Mute after N NSFW strikes'],
      ['live_nsfw_stream_ban_strikes', '3', 'Temp ban after N NSFW strikes'],
      ['live_nsfw_perm_ban_strikes', '5', 'Permanent account ban after N NSFW strikes'],
    ];
    for (const [key, value, description] of defaults) {
      const existing = await this.settingsRepo.findOne({ where: { key } });
      if (!existing) {
        await this.settingsRepo.save(
          this.settingsRepo.create({ key, value, description }),
        );
      }
    }
  }

  async isAutoModerationOn(): Promise<boolean> {
    const dash = await this.stringSetting('autoModeration', '');
    if (dash !== '') {
      return this.parseBool(dash, true);
    }
    return this.boolSetting('auto_moderation', true);
  }

  private parseBool(raw: string, fallback: boolean): boolean {
    const s = String(raw || '').trim().toLowerCase();
    if (!s) return fallback;
    return s === '1' || s === 'true' || s === 'yes' || s === 'on';
  }

  async clientPolicy() {
    const auto = await this.isAutoModerationOn();
    const promo = await this.boolSetting('chat_promo_filter_enabled', true);
    const nsfw = await this.boolSetting('live_nsfw_enabled', true);
    const extra = await this.stringSetting('chat_blocked_extra_keywords', '');
    return {
      autoModeration: auto,
      chatPromoFilterEnabled: auto && promo,
      chatPromoKickEnabled: await this.boolSetting('chat_promo_kick_enabled', true),
      liveNsfwEnabled: auto && nsfw,
      extraKeywords: extra
        .split(/[\n,]+/)
        .map((s) => s.trim())
        .filter(Boolean),
    };
  }

  async inspectText(text: string | null | undefined): Promise<ModerationResult> {
    if (!(await this.isAutoModerationOn())) return { ok: true };
    if (!(await this.boolSetting('chat_promo_filter_enabled', true))) {
      return { ok: true };
    }
    const raw = String(text || '').trim();
    if (!raw) return { ok: true };

    const patterns = [...this.defaultBlocked];
    const extra = await this.stringSetting('chat_blocked_extra_keywords', '');
    for (const word of extra.split(/[\n,]+/).map((s) => s.trim()).filter(Boolean)) {
      try {
        patterns.push(new RegExp(word.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'i'));
      } catch {
        /* ignore bad pattern */
      }
    }

    for (const re of patterns) {
      if (re.test(raw)) {
        const kick = await this.boolSetting('chat_promo_kick_enabled', true);
        return {
          ok: false,
          code: 'CHAT_PROMO_BLOCKED',
          reason:
            'ممنوع الترويج أو الروابط أو المحتوى المخالف في الدردشة',
          action: kick ? 'kick' : 'block',
        };
      }
    }
    return { ok: true };
  }

  /** Assert text is clean or throw BadRequestException. */
  async assertCleanText(text: string | null | undefined): Promise<void> {
    const result = await this.inspectText(text);
    if (!result.ok) {
      throw new BadRequestException({
        code: result.code,
        message: result.reason,
        action: result.action,
      });
    }
  }

  /**
   * Scan an uploaded image file for likely nudity (skin-ratio heuristic).
   * Deletes the file and throws when blocked.
   */
  async assertCleanImageFile(storedPath: string, mimeType?: string): Promise<void> {
    if (!(await this.isAutoModerationOn())) return;
    if (!(await this.boolSetting('live_nsfw_enabled', true))) return;
    const ext = extname(storedPath || '').toLowerCase();
    const imageExt = ['.jpg', '.jpeg', '.png', '.webp', '.gif'];
    if (!imageExt.includes(ext) && !(mimeType || '').startsWith('image/')) {
      return;
    }
    if (!storedPath || !existsSync(storedPath)) return;

    const threshold = await this.numSetting('live_nsfw_confidence', 0.72);
    const score = await this.estimateNsfwScore(storedPath, ext);
    if (score == null) return;
    if (score < threshold) return;

    try {
      unlinkSync(storedPath);
    } catch {
      /* ignore */
    }
    throw new BadRequestException({
      code: 'NSFW_IMAGE_BLOCKED',
      message: 'تم رفض الصورة: محتوى غير لائق / عري',
      score,
    });
  }

  async recordNsfwStrike(userId: string): Promise<{
    strikes: number;
    action: 'warn' | 'mute' | 'ban_temp' | 'ban_perm';
  }> {
    const user = await this.usersRepo.findOne({ where: { id: userId } });
    if (!user) return { strikes: 0, action: 'warn' };
    user.nsfwStrikeCount = Math.max(0, Number(user.nsfwStrikeCount || 0)) + 1;
    const strikes = user.nsfwStrikeCount;
    const warnAt = await this.numSetting('live_nsfw_warn_strikes', 1);
    const muteAt = await this.numSetting('live_nsfw_mute_strikes', 2);
    const banTempAt = await this.numSetting('live_nsfw_stream_ban_strikes', 3);
    const banPermAt = await this.numSetting('live_nsfw_perm_ban_strikes', 5);

    let action: 'warn' | 'mute' | 'ban_temp' | 'ban_perm' = 'warn';
    if (strikes >= banPermAt) {
      user.status = UserStatus.BANNED;
      action = 'ban_perm';
    } else if (strikes >= banTempAt) {
      action = 'ban_temp';
    } else if (strikes >= muteAt) {
      action = 'mute';
    } else if (strikes >= warnAt) {
      action = 'warn';
    }
    await this.usersRepo.save(user);
    return { strikes, action };
  }

  private async estimateNsfwScore(
    path: string,
    ext: string,
  ): Promise<number | null> {
    try {
      const buf = readFileSync(path);
      let rgba: { data: Buffer; width: number; height: number } | null = null;
      if (ext === '.png') {
        rgba = await this.decodePng(buf);
      } else if (ext === '.jpg' || ext === '.jpeg') {
        rgba = await this.decodeJpeg(buf);
      } else {
        // webp/gif: optional external API only
        return await this.externalNsfwScore(path);
      }
      if (!rgba || rgba.width < 8 || rgba.height < 8) return null;
      return this.skinRatio(rgba.data, rgba.width, rgba.height);
    } catch (err) {
      this.log.warn(`NSFW scan failed: ${(err as Error).message}`);
      return null;
    }
  }

  private skinRatio(data: Buffer, width: number, height: number): number {
    const stepX = Math.max(1, Math.floor(width / 64));
    const stepY = Math.max(1, Math.floor(height / 64));
    let skin = 0;
    let total = 0;
    for (let y = 0; y < height; y += stepY) {
      for (let x = 0; x < width; x += stepX) {
        const i = (y * width + x) * 4;
        const r = data[i];
        const g = data[i + 1];
        const b = data[i + 2];
        total += 1;
        if (this.isSkinPixel(r, g, b)) skin += 1;
      }
    }
    return total > 0 ? skin / total : 0;
  }

  /** YCbCr / RGB skin heuristic (conservative for moderation). */
  private isSkinPixel(r: number, g: number, b: number): boolean {
    const y = 0.299 * r + 0.587 * g + 0.114 * b;
    const cb = 128 - 0.168736 * r - 0.331264 * g + 0.5 * b;
    const cr = 128 + 0.5 * r - 0.418688 * g - 0.081312 * b;
    const ruleYcbcr =
      y > 40 &&
      cb > 77 &&
      cb < 127 &&
      cr > 133 &&
      cr < 173;
    const ruleRgb =
      r > 95 &&
      g > 40 &&
      b > 20 &&
      r > g &&
      r > b &&
      Math.abs(r - g) > 15 &&
      r - b > 15;
    return ruleYcbcr || ruleRgb;
  }

  private async decodePng(
    buf: Buffer,
  ): Promise<{ data: Buffer; width: number; height: number } | null> {
    try {
      // eslint-disable-next-line @typescript-eslint/no-var-requires
      const { PNG } = require('pngjs');
      const png = PNG.sync.read(buf);
      return { data: png.data, width: png.width, height: png.height };
    } catch {
      return null;
    }
  }

  private async decodeJpeg(
    buf: Buffer,
  ): Promise<{ data: Buffer; width: number; height: number } | null> {
    try {
      // eslint-disable-next-line @typescript-eslint/no-var-requires
      const jpeg = require('jpeg-js');
      const decoded = jpeg.decode(buf, { maxMemoryUsageInMB: 64, useTArray: true });
      if (!decoded?.data) return null;
      return {
        data: Buffer.from(decoded.data),
        width: decoded.width,
        height: decoded.height,
      };
    } catch {
      return null;
    }
  }

  private async externalNsfwScore(path: string): Promise<number | null> {
    const api = await this.stringSetting('nsfw_scan_api_url', '');
    if (!api) return null;
    try {
      const res = await fetch(api, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ path }),
      });
      if (!res.ok) return null;
      const json = (await res.json()) as { score?: number; nsfw?: number };
      const score = Number(json.score ?? json.nsfw);
      return Number.isFinite(score) ? score : null;
    } catch {
      return null;
    }
  }

  private async boolSetting(key: string, fallback: boolean): Promise<boolean> {
    const raw = await this.stringSetting(key, fallback ? 'true' : 'false');
    return this.parseBool(raw, fallback);
  }

  private async numSetting(key: string, fallback: number): Promise<number> {
    const raw = await this.stringSetting(key, String(fallback));
    const n = Number(raw);
    return Number.isFinite(n) ? n : fallback;
  }

  private async stringSetting(key: string, fallback: string): Promise<string> {
    const row = await this.settingsRepo.findOne({ where: { key } });
    const v = row?.value;
    return v == null || v === '' ? fallback : String(v);
  }
}
