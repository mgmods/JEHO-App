import { Body, Controller, Get, Patch, UseGuards } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { Repository } from 'typeorm';
import { JwtAuthGuard } from '../../common/guards/jwt-auth.guard';
import { AdminGuard } from '../../common/guards/admin.guard';
import { AppSetting } from '../../database/entities/app-setting.entity';

const DEFAULT_TOAST_STYLE = {
  backgroundUrl: '',
  shineEnabled: true,
  frameGlowEnabled: true,
  durationSeconds: 3,
  version: 1,
  giftSend: { backgroundUrl: '', shineEnabled: true, frameGlowEnabled: false, durationSeconds: 3, avatarX: 8, avatarY: 50, textX: 25, textY: 50, bodyX: 25, bodyY: 67, countX: 88, countY: 50, widthDp: 340, heightDp: 100, avatarSizeDp: 44, titleSizeSp: 13, bodySizeSp: 12, countSizeSp: 12 },
  luckyWinNormal: { backgroundUrl: '', shineEnabled: true, frameGlowEnabled: false, durationSeconds: 4, avatarX: 8, avatarY: 42, textX: 25, textY: 42, bodyX: 25, bodyY: 62, countX: 88, countY: 50, widthDp: 340, heightDp: 100, avatarSizeDp: 44, titleSizeSp: 13, bodySizeSp: 12, countSizeSp: 12 },
  luckyWinHigh: { backgroundUrl: '', shineEnabled: true, frameGlowEnabled: false, durationSeconds: 5, avatarX: 8, avatarY: 42, textX: 25, textY: 42, bodyX: 25, bodyY: 62, countX: 88, countY: 50, widthDp: 340, heightDp: 100, avatarSizeDp: 44, titleSizeSp: 13, bodySizeSp: 12, countSizeSp: 12 },
  luckyButton: { iconUrl: '', durationSeconds: 5, pulseEnabled: true },
};

function cleanUrl(value: unknown, fallback = ''): string {
  return typeof value === 'string' ? value.trim().slice(0, 2048) : fallback;
}
function clampSeconds(value: unknown, fallback: number): number {
  return Math.max(1, Math.min(12, Math.round(Number(value) || fallback)));
}
function clampPercent(value: unknown, fallback: number): number {
  const number = Number(value);
  return Number.isFinite(number) ? Math.max(0, Math.min(100, Math.round(number))) : fallback;
}
function clampTextSize(value: unknown, fallback: number): number {
  const number = Number(value);
  return Number.isFinite(number) ? Math.max(8, Math.min(30, Math.round(number))) : fallback;
}

@ApiTags('Admin Toast Manager')
@Controller('admin/toast-manager')
@UseGuards(JwtAuthGuard, AdminGuard)
@ApiBearerAuth()
export class ToastManagerController {
  constructor(@InjectRepository(AppSetting) private readonly settingsRepo: Repository<AppSetting>) {}

  @Get()
  @ApiOperation({ summary: 'Read dashboard-managed gift and lucky-win toast appearance' })
  async getConfig() {
    const row = await this.settingsRepo.findOne({ where: { key: 'toast_manager_config' } });
    let parsed: any = {};
    try { parsed = row?.value ? JSON.parse(row.value) : {}; } catch { parsed = {}; }
    const oldDuration = clampSeconds(parsed.durationSeconds, 3);
    const giftSend = {
      backgroundUrl: cleanUrl(parsed.giftSend?.backgroundUrl, cleanUrl(parsed.backgroundUrl)),
      shineEnabled: parsed.giftSend?.shineEnabled !== false && parsed.shineEnabled !== false,
      frameGlowEnabled: false,
      durationSeconds: clampSeconds(parsed.giftSend?.durationSeconds, oldDuration),
      avatarX: clampPercent(parsed.giftSend?.avatarX, 8),
      avatarY: clampPercent(parsed.giftSend?.avatarY, 50),
      textX: clampPercent(parsed.giftSend?.textX, 25),
      textY: clampPercent(parsed.giftSend?.textY, 50),
      bodyX: clampPercent(parsed.giftSend?.bodyX, 25), bodyY: clampPercent(parsed.giftSend?.bodyY, 67),
      countX: clampPercent(parsed.giftSend?.countX, 88), countY: clampPercent(parsed.giftSend?.countY, 50),
      widthDp: Math.max(220, Math.min(360, Math.round(Number(parsed.giftSend?.widthDp) || 340))),
      heightDp: Math.max(60, Math.min(180, Math.round(Number(parsed.giftSend?.heightDp) || 100))),
      avatarSizeDp: Math.max(20, Math.min(76, Math.round(Number(parsed.giftSend?.avatarSizeDp) || 44))),
      titleSizeSp: clampTextSize(parsed.giftSend?.titleSizeSp, 13),
      bodySizeSp: clampTextSize(parsed.giftSend?.bodySizeSp, 12),
      countSizeSp: clampTextSize(parsed.giftSend?.countSizeSp, 12),
    };
    const winDefaults = (source: any, fallback: any) => ({
      backgroundUrl: cleanUrl(source?.backgroundUrl, fallback.backgroundUrl),
      shineEnabled: source?.shineEnabled !== false,
      frameGlowEnabled: false,
      durationSeconds: clampSeconds(source?.durationSeconds, fallback.durationSeconds),
      avatarX: clampPercent(source?.avatarX, fallback.avatarX),
      avatarY: clampPercent(source?.avatarY, fallback.avatarY),
      textX: clampPercent(source?.textX, fallback.textX),
      textY: clampPercent(source?.textY, fallback.textY),
      bodyX: clampPercent(source?.bodyX, fallback.bodyX ?? 25), bodyY: clampPercent(source?.bodyY, fallback.bodyY ?? 67),
      countX: clampPercent(source?.countX, fallback.countX ?? 88), countY: clampPercent(source?.countY, fallback.countY ?? 50),
      widthDp: Math.max(220, Math.min(360, Math.round(Number(source?.widthDp) || fallback.widthDp || 340))),
      heightDp: Math.max(60, Math.min(180, Math.round(Number(source?.heightDp) || fallback.heightDp || 100))),
      avatarSizeDp: Math.max(20, Math.min(76, Math.round(Number(source?.avatarSizeDp) || fallback.avatarSizeDp || 44))),
      titleSizeSp: clampTextSize(source?.titleSizeSp, fallback.titleSizeSp || 13),
      bodySizeSp: clampTextSize(source?.bodySizeSp, fallback.bodySizeSp || 12),
      countSizeSp: clampTextSize(source?.countSizeSp, fallback.countSizeSp || 12),
    });
    const luckyWinNormal = winDefaults(parsed.luckyWinNormal, DEFAULT_TOAST_STYLE.luckyWinNormal);
    const luckyWinHigh = winDefaults(parsed.luckyWinHigh, DEFAULT_TOAST_STYLE.luckyWinHigh);
    const luckyButton = {
      iconUrl: cleanUrl(parsed.luckyButton?.iconUrl),
      durationSeconds: clampSeconds(parsed.luckyButton?.durationSeconds, 5),
      pulseEnabled: parsed.luckyButton?.pulseEnabled !== false,
    };
    return { ...DEFAULT_TOAST_STYLE, ...parsed, ...giftSend, giftSend, luckyWinNormal, luckyWinHigh, luckyButton,
      version: Math.max(1, Math.round(Number(parsed.version) || 1)) };
  }

  @Patch()
  @ApiOperation({ summary: 'Save dashboard-managed gift and lucky-win toast appearance' })
  async saveConfig(@Body() body: Record<string, any>) {
    const previous = await this.getConfig();
    const oldGift = previous.giftSend || previous;
    const giftSend = {
      backgroundUrl: cleanUrl(body?.giftSend?.backgroundUrl, cleanUrl(body?.backgroundUrl, oldGift.backgroundUrl)),
      shineEnabled: typeof body?.giftSend?.shineEnabled === 'boolean' ? body.giftSend.shineEnabled : (typeof body?.shineEnabled === 'boolean' ? body.shineEnabled : oldGift.shineEnabled),
      frameGlowEnabled: false,
      durationSeconds: clampSeconds(body?.giftSend?.durationSeconds ?? body?.durationSeconds, oldGift.durationSeconds),
      avatarX: clampPercent(body?.giftSend?.avatarX, oldGift.avatarX ?? 8),
      avatarY: clampPercent(body?.giftSend?.avatarY, oldGift.avatarY ?? 50),
      textX: clampPercent(body?.giftSend?.textX, oldGift.textX ?? 25),
      textY: clampPercent(body?.giftSend?.textY, oldGift.textY ?? 50),
      bodyX: clampPercent(body?.giftSend?.bodyX, oldGift.bodyX ?? 25), bodyY: clampPercent(body?.giftSend?.bodyY, oldGift.bodyY ?? 67),
      countX: clampPercent(body?.giftSend?.countX, oldGift.countX ?? 88), countY: clampPercent(body?.giftSend?.countY, oldGift.countY ?? 50),
      widthDp: Math.max(220, Math.min(360, Math.round(Number(body?.giftSend?.widthDp) || oldGift.widthDp || 340))),
      heightDp: Math.max(60, Math.min(180, Math.round(Number(body?.giftSend?.heightDp) || oldGift.heightDp || 100))),
      avatarSizeDp: Math.max(20, Math.min(76, Math.round(Number(body?.giftSend?.avatarSizeDp) || oldGift.avatarSizeDp || 44))),
      titleSizeSp: clampTextSize(body?.giftSend?.titleSizeSp, oldGift.titleSizeSp || 13),
      bodySizeSp: clampTextSize(body?.giftSend?.bodySizeSp, oldGift.bodySizeSp || 12),
      countSizeSp: clampTextSize(body?.giftSend?.countSizeSp, oldGift.countSizeSp || 12),
    };
    const win = (key: 'luckyWinNormal' | 'luckyWinHigh') => {
      const old = previous[key] || DEFAULT_TOAST_STYLE[key];
      const input = body?.[key] || {};
      return {
        backgroundUrl: cleanUrl(input.backgroundUrl, old.backgroundUrl),
        shineEnabled: typeof input.shineEnabled === 'boolean' ? input.shineEnabled : old.shineEnabled,
        frameGlowEnabled: false,
        durationSeconds: clampSeconds(input.durationSeconds, old.durationSeconds),
        avatarX: clampPercent(input.avatarX, old.avatarX),
        avatarY: clampPercent(input.avatarY, old.avatarY),
        textX: clampPercent(input.textX, old.textX),
        textY: clampPercent(input.textY, old.textY),
        bodyX: clampPercent(input.bodyX, old.bodyX ?? 25), bodyY: clampPercent(input.bodyY, old.bodyY ?? 67),
        countX: clampPercent(input.countX, old.countX ?? 88), countY: clampPercent(input.countY, old.countY ?? 50),
        widthDp: Math.max(220, Math.min(360, Math.round(Number(input.widthDp) || old.widthDp || 340))),
        heightDp: Math.max(60, Math.min(180, Math.round(Number(input.heightDp) || old.heightDp || 100))),
        avatarSizeDp: Math.max(20, Math.min(76, Math.round(Number(input.avatarSizeDp) || old.avatarSizeDp || 44))),
        titleSizeSp: clampTextSize(input.titleSizeSp, old.titleSizeSp || 13),
        bodySizeSp: clampTextSize(input.bodySizeSp, old.bodySizeSp || 12),
        countSizeSp: clampTextSize(input.countSizeSp, old.countSizeSp || 12),
      };
    };
    const luckyButton = {
      iconUrl: cleanUrl(body?.luckyButton?.iconUrl, previous.luckyButton?.iconUrl || ''),
      durationSeconds: clampSeconds(body?.luckyButton?.durationSeconds, previous.luckyButton?.durationSeconds || 5),
      pulseEnabled: typeof body?.luckyButton?.pulseEnabled === 'boolean' ? body.luckyButton.pulseEnabled : previous.luckyButton?.pulseEnabled !== false,
    };
    const next = {
      backgroundUrl: giftSend.backgroundUrl,
      shineEnabled: giftSend.shineEnabled,
      frameGlowEnabled: giftSend.frameGlowEnabled,
      durationSeconds: giftSend.durationSeconds,
      giftSend, luckyWinNormal: win('luckyWinNormal'), luckyWinHigh: win('luckyWinHigh'), luckyButton,
      version: previous.version + 1,
    };
    const row = await this.settingsRepo.findOne({ where: { key: 'toast_manager_config' } });
    const serialized = JSON.stringify(next);
    if (row) {
      row.value = serialized;
      await this.settingsRepo.save(row);
    } else {
      await this.settingsRepo.save(this.settingsRepo.create({
        key: 'toast_manager_config', value: serialized,
        description: 'Dashboard-managed gift send, lucky-win toast and lucky button assets/effects',
      }));
    }

    // Never report success from the in-memory request alone: read the durable row back
    // so the dashboard sees exactly what a page refresh will retrieve.
    const persisted = await this.settingsRepo.findOne({ where: { key: 'toast_manager_config' } });
    if (!persisted?.value) {
      throw new Error('Toast settings were not persisted; reload was not confirmed');
    }
    let stored: any;
    try { stored = JSON.parse(persisted.value); } catch {
      throw new Error('Saved toast settings could not be read back from storage');
    }
    const verifyFields = [
      'avatarX', 'avatarY', 'textX', 'textY', 'bodyX', 'bodyY', 'countX', 'countY',
      'widthDp', 'heightDp', 'avatarSizeDp', 'titleSizeSp', 'bodySizeSp', 'countSizeSp',
    ];
    for (const key of ['giftSend', 'luckyWinNormal', 'luckyWinHigh']) {
      for (const field of verifyFields) {
        if (Number(stored?.[key]?.[field]) !== Number(next[key]?.[field])) {
          throw new Error(`Toast setting did not persist: ${key}.${field}`);
        }
      }
    }
    return this.getConfig();
  }
}
