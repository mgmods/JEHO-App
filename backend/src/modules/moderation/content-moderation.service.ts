import {
  BadRequestException,
  Injectable,
  Logger,
  OnModuleInit,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { DataSource, Repository } from 'typeorm';
import { existsSync, readFileSync, unlinkSync } from 'fs';
import { extname } from 'path';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { User, UserStatus } from '../../database/entities/user.entity';
import { RoomChatPenalty } from '../../database/entities/room-chat-penalty.entity';

export type ModerationHit = {
  ok: false;
  code: string;
  reason: string;
  action: 'block' | 'mute' | 'kick' | 'ban';
  score?: number;
};

export type ModerationPass = { ok: true };

export type ModerationResult = ModerationHit | ModerationPass;

export type ChatStrikeResolution = {
  strikes: number;
  action: 'mute' | 'kick' | 'ban';
  muteMinutes: number;
  muteUntil: Date | null;
  reason: string;
};

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
    @InjectRepository(RoomChatPenalty)
    private readonly penaltiesRepo: Repository<RoomChatPenalty>,
    private readonly dataSource: DataSource,
  ) {}

  async onModuleInit() {
    const defaults: Array<[string, string, string]> = [
      ['auto_moderation', 'true', 'Enable automated chat/image moderation'],
      ['autoModeration', 'true', 'Enable automated chat/image moderation (dashboard key)'],
      ['chat_promo_filter_enabled', 'true', 'Block promo links in room/DM chat'],
      ['chat_promo_kick_enabled', 'true', 'Escalate room chat violations (mute→kick→ban)'],
      ['chat_promo_mute_first', 'true', 'Mute chat before kick/ban on promo violation'],
      ['chat_promo_mute_minutes_1', '5', 'Chat mute minutes on 1st room strike'],
      ['chat_promo_mute_minutes_2', '10', 'Chat mute minutes on 2nd room strike'],
      ['chat_promo_kick_at_strike', '3', 'Kick from room at this strike count'],
      ['chat_promo_ban_at_strike', '4', 'Ban from room at this strike count'],
      ['chat_promo_ban_minutes', '30', 'Room ban minutes on 4th+ chat strike'],
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
    await this.ensurePenaltyTable();
  }

  private async ensurePenaltyTable() {
    try {
      await this.dataSource.query(`
        CREATE TABLE IF NOT EXISTS room_chat_penalties (
          id uuid PRIMARY KEY,
          "roomId" uuid NOT NULL,
          "userId" uuid NOT NULL,
          "strikeCount" integer NOT NULL DEFAULT 0,
          "chatMutedUntil" TIMESTAMPTZ NULL,
          "createdAt" TIMESTAMPTZ NOT NULL DEFAULT now(),
          "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT now()
        )
      `);
      await this.dataSource.query(`
        CREATE UNIQUE INDEX IF NOT EXISTS "IDX_room_chat_penalties_room_user"
        ON room_chat_penalties ("roomId", "userId")
      `);
    } catch (err) {
      this.log.warn(
        `room_chat_penalties ensure skipped: ${
          err instanceof Error ? err.message : String(err)
        }`,
      );
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
        // Action is resolved per-room via strike ladder in resolveRoomChatStrike.
        const escalate = await this.boolSetting('chat_promo_kick_enabled', true);
        return {
          ok: false,
          code: 'CHAT_PROMO_BLOCKED',
          reason:
            'ممنوع الترويج أو الروابط أو المحتوى المخالف في الدردشة',
          action: escalate ? 'mute' : 'block',
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
   * Block “وكالة / agency” impersonation on personal identity fields
   * (display name, bio, personal room title/description). Does not apply to
   * real agency branding endpoints.
   */
  private readonly agencyImpersonationPatterns = [
    /وكالة|وكاله|وكالات/i,
    /\bagenc(?:y|ies)\b/i,
  ];

  containsAgencyImpersonation(text: string | null | undefined): boolean {
    const raw = String(text || '').trim();
    if (!raw) return false;
    return this.agencyImpersonationPatterns.some((re) => re.test(raw));
  }

  assertNoAgencyImpersonation(text: string | null | undefined, fieldLabel = 'هذا الحقل'): void {
    if (this.containsAgencyImpersonation(text)) {
      throw new BadRequestException({
        code: 'AGENCY_WORD_FORBIDDEN',
        message: `لا يُسمح باستخدام كلمة «وكالة» في ${fieldLabel}. الاسم «وكالة» محجوز للوكالات الرسمية فقط.`,
      });
    }
  }

  async getActiveChatMute(
    roomId: string,
    userId: string,
  ): Promise<{ muted: boolean; until: Date | null; strikes: number }> {
    const row = await this.penaltiesRepo.findOne({ where: { roomId, userId } });
    if (!row) return { muted: false, until: null, strikes: 0 };
    const until = row.chatMutedUntil;
    const active = !!until && until.getTime() > Date.now();
    return {
      muted: active,
      until: active ? until : null,
      strikes: row.strikeCount || 0,
    };
  }

  /**
   * Escalation: 1st mute, 2nd mute, 3rd kick, 4th+ ban.
   * Always increments strikeCount for the room+user pair.
   */
  async resolveRoomChatStrike(
    roomId: string,
    userId: string,
    baseReason: string,
  ): Promise<ChatStrikeResolution> {
    const escalate = await this.boolSetting('chat_promo_kick_enabled', true);
    let row = await this.penaltiesRepo.findOne({ where: { roomId, userId } });
    if (!row) {
      row = this.penaltiesRepo.create({
        roomId,
        userId,
        strikeCount: 0,
        chatMutedUntil: null,
      });
    }
    row.strikeCount = Math.max(0, Number(row.strikeCount || 0)) + 1;
    const strikes = row.strikeCount;
    const kickAt = await this.numSetting('chat_promo_kick_at_strike', 3);
    const banAt = await this.numSetting('chat_promo_ban_at_strike', 4);
    const mute1 = await this.numSetting('chat_promo_mute_minutes_1', 5);
    const mute2 = await this.numSetting('chat_promo_mute_minutes_2', 10);
    const banMinutes = await this.numSetting('chat_promo_ban_minutes', 30);

    let action: 'mute' | 'kick' | 'ban' = 'mute';
    let muteMinutes = strikes <= 1 ? mute1 : mute2;
    if (escalate && strikes >= banAt) {
      action = 'ban';
      muteMinutes = banMinutes;
      row.chatMutedUntil = null;
    } else if (escalate && strikes >= kickAt) {
      action = 'kick';
      muteMinutes = 0;
      row.chatMutedUntil = null;
    } else {
      action = 'mute';
      const until = new Date(Date.now() + Math.max(1, muteMinutes) * 60 * 1000);
      row.chatMutedUntil = until;
    }
    await this.penaltiesRepo.save(row);

    const reason =
      action === 'ban'
        ? `${baseReason} · حظر الغرفة (مخالفة ${strikes})`
        : action === 'kick'
          ? `${baseReason} · طرد من الغرفة (مخالفة ${strikes})`
          : `${baseReason} · كتم الدردشة ${muteMinutes} د (مخالفة ${strikes})`;

    return {
      strikes,
      action,
      muteMinutes: action === 'ban' ? banMinutes : muteMinutes,
      muteUntil: row.chatMutedUntil,
      reason,
    };
  }

  async clearRoomChatPenalty(roomId: string, userId: string) {
    await this.penaltiesRepo.delete({ roomId, userId });
  }

  async listActiveChatMutes(roomId: string) {
    const now = new Date();
    return this.penaltiesRepo
      .createQueryBuilder('p')
      .where('p.roomId = :roomId', { roomId })
      .andWhere('p.chatMutedUntil IS NOT NULL')
      .andWhere('p.chatMutedUntil > :now', { now })
      .orderBy('p.updatedAt', 'DESC')
      .take(100)
      .getMany();
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
    if (score == null) {
      try {
        unlinkSync(storedPath);
      } catch {
        /* ignore */
      }
      throw new BadRequestException({
        code: 'NSFW_SCAN_UNAVAILABLE',
        message: 'تعذر فحص الصورة — ارفع JPG أو PNG',
      });
    }
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
      user.status = UserStatus.SUSPENDED;
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
