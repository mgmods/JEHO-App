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
  giftSend: { backgroundUrl: '', shineEnabled: true, frameGlowEnabled: true, durationSeconds: 3 },
  luckyWinNormal: { backgroundUrl: '', shineEnabled: true, frameGlowEnabled: true, durationSeconds: 4, avatarX: 8, avatarY: 50, textX: 25, textY: 50 },
  luckyWinHigh: { backgroundUrl: '', shineEnabled: true, frameGlowEnabled: true, durationSeconds: 5, avatarX: 8, avatarY: 50, textX: 25, textY: 50 },
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
      frameGlowEnabled: parsed.giftSend?.frameGlowEnabled !== false && parsed.frameGlowEnabled !== false,
      durationSeconds: clampSeconds(parsed.giftSend?.durationSeconds, oldDuration),
    };
    const winDefaults = (source: any, fallback: any) => ({
      backgroundUrl: cleanUrl(source?.backgroundUrl, fallback.backgroundUrl),
      shineEnabled: source?.shineEnabled !== false,
      frameGlowEnabled: source?.frameGlowEnabled !== false,
      durationSeconds: clampSeconds(source?.durationSeconds, fallback.durationSeconds),
      avatarX: clampPercent(source?.avatarX, fallback.avatarX),
      avatarY: clampPercent(source?.avatarY, fallback.avatarY),
      textX: clampPercent(source?.textX, fallback.textX),
      textY: clampPercent(source?.textY, fallback.textY),
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
      frameGlowEnabled: typeof body?.giftSend?.frameGlowEnabled === 'boolean' ? body.giftSend.frameGlowEnabled : (typeof body?.frameGlowEnabled === 'boolean' ? body.frameGlowEnabled : oldGift.frameGlowEnabled),
      durationSeconds: clampSeconds(body?.giftSend?.durationSeconds ?? body?.durationSeconds, oldGift.durationSeconds),
    };
    const win = (key: 'luckyWinNormal' | 'luckyWinHigh') => {
      const old = previous[key] || DEFAULT_TOAST_STYLE[key];
      const input = body?.[key] || {};
      return {
        backgroundUrl: cleanUrl(input.backgroundUrl, old.backgroundUrl),
        shineEnabled: typeof input.shineEnabled === 'boolean' ? input.shineEnabled : old.shineEnabled,
        frameGlowEnabled: typeof input.frameGlowEnabled === 'boolean' ? input.frameGlowEnabled : old.frameGlowEnabled,
        durationSeconds: clampSeconds(input.durationSeconds, old.durationSeconds),
        avatarX: clampPercent(input.avatarX, old.avatarX),
        avatarY: clampPercent(input.avatarY, old.avatarY),
        textX: clampPercent(input.textX, old.textX),
        textY: clampPercent(input.textY, old.textY),
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
    if (row) { row.value = JSON.stringify(next); await this.settingsRepo.save(row); }
    else await this.settingsRepo.save(this.settingsRepo.create({
      key: 'toast_manager_config', value: JSON.stringify(next),
      description: 'Dashboard-managed gift send, lucky-win toast and lucky button assets/effects',
    }));
    return next;
  }
}
