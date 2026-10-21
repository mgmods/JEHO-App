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
};

@ApiTags('Admin Toast Manager')
@Controller('admin/toast-manager')
@UseGuards(JwtAuthGuard, AdminGuard)
@ApiBearerAuth()
export class ToastManagerController {
  constructor(@InjectRepository(AppSetting) private readonly settingsRepo: Repository<AppSetting>) {}

  @Get()
  @ApiOperation({ summary: 'Read dashboard-managed gift toast appearance' })
  async getConfig() {
    const row = await this.settingsRepo.findOne({ where: { key: 'toast_manager_config' } });
    if (!row?.value) return DEFAULT_TOAST_STYLE;
    try {
      const parsed = JSON.parse(row.value);
      return {
        backgroundUrl: typeof parsed.backgroundUrl === 'string' ? parsed.backgroundUrl : '',
        shineEnabled: parsed.shineEnabled !== false,
        frameGlowEnabled: parsed.frameGlowEnabled !== false,
        durationSeconds: Math.max(2, Math.min(8, Math.round(Number(parsed.durationSeconds) || 3))),
        version: Math.max(1, Math.round(Number(parsed.version) || 1)),
      };
    } catch {
      return DEFAULT_TOAST_STYLE;
    }
  }

  @Patch()
  @ApiOperation({ summary: 'Save dashboard-managed gift toast appearance' })
  async saveConfig(@Body() body: Record<string, unknown>) {
    const previous = await this.getConfig();
    const next = {
      backgroundUrl: typeof body?.backgroundUrl === 'string' ? body.backgroundUrl.trim().slice(0, 2048) : previous.backgroundUrl,
      shineEnabled: typeof body?.shineEnabled === 'boolean' ? body.shineEnabled : previous.shineEnabled,
      frameGlowEnabled: typeof body?.frameGlowEnabled === 'boolean' ? body.frameGlowEnabled : previous.frameGlowEnabled,
      durationSeconds: Math.max(2, Math.min(8, Math.round(Number(body?.durationSeconds) || 3))),
      version: previous.version + 1,
    };
    const row = await this.settingsRepo.findOne({ where: { key: 'toast_manager_config' } });
    if (row) {
      row.value = JSON.stringify(next);
      await this.settingsRepo.save(row);
    } else {
      await this.settingsRepo.save(this.settingsRepo.create({
        key: 'toast_manager_config',
        value: JSON.stringify(next),
        description: 'Dashboard-managed gift toast background and lightweight effects',
      }));
    }
    return next;
  }
}
