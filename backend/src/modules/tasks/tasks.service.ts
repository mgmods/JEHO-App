import { BadRequestException, Injectable, Optional } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { DataSource, EntityManager, In, Repository } from 'typeorm';
import { Wallet } from '../../database/entities/wallet.entity';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { User } from '../../database/entities/user.entity';
import {
  AgencyMember,
  AgencyMemberStatus,
  AgencyRole,
} from '../../database/entities/agency-member.entity';
import { Agency, AgencyStatus } from '../../database/entities/agency.entity';
import {
  WalletTransaction,
  TransactionType,
  CurrencyType,
} from '../../database/entities/wallet-transaction.entity';
import { RealtimeGateway } from '../realtime/realtime.gateway';
import { CosmeticsService } from '../cosmetics/cosmetics.service';
import { VipService } from '../vip/vip.service';
import {
  HOST_NEW_USER_CHAT,
  HOST_ROOM_INVITE_REWARD,
} from '../../common/pricing-catalog';

export type TaskScope = 'global' | 'room' | 'agency';
export type TaskProgressContext = {
  roomId?: string;
  agencyId?: string;
};

export type TaskAudience = 'all' | 'host';

export type DailyTask = {
  id: string;
  title: string;
  rewardPoints: number;
  rewardSilver: number;
  /** Diamonds credited to the claiming user (usually the new male). Never for host audience. */
  rewardDiamonds?: number;
  /** Diamonds credited to each female host peer for completed chat rounds. Always forced to 0. */
  rewardHostDiamonds?: number;
  /** Optional temporary cosmetic (frame / entry…) code. */
  rewardCosmeticCode?: string;
  rewardCosmeticDays?: number;
  /** Optional temporary VIP level. */
  rewardVipLevel?: number;
  rewardVipDays?: number;
  type: string;
  /** Who sees/claims this task. host = female users only. */
  audience?: TaskAudience;
  scope?: TaskScope;
  roomId?: string | null;
  agencyId?: string | null;
  /** Min active chat seconds to count one round (default 120). */
  minConversationSeconds?: number;
  /** Only males whose account was created within this many days (default 30). */
  newUserDays?: number;
};

export const DEFAULT_TASKS: DailyTask[] = [
  { id: 't1', title: 'ادخل غرفة صوتية اليوم', rewardPoints: 100, rewardSilver: 50, type: 'rooms_1', audience: 'all' },
  { id: 't2', title: 'أرسل هدية واحدة', rewardPoints: 80, rewardSilver: 0, type: 'gift_1', audience: 'all' },
  { id: 't3', title: 'تابع مستخدمين اثنين', rewardPoints: 60, rewardSilver: 20, type: 'follow_2', audience: 'all' },
  { id: 't4', title: 'اجلس على الميكروفون 5 مرات', rewardPoints: 120, rewardSilver: 0, type: 'mic_5', audience: 'all' },
  { id: 't5', title: 'سجّل حضورك اليومي من مركز المهام', rewardPoints: 40, rewardSilver: 40, type: 'checkin', audience: 'all' },
  { id: 't6', title: 'اشحن أو بدّل عملات', rewardPoints: 200, rewardSilver: 0, type: 'recharge', audience: 'all' },
  {
    id: 't7',
    title: 'تحدّث مع 10 مضيفات (5–10 رسائل خلال 3 دقائق لكل جولة)',
    rewardPoints: 150,
    rewardSilver: 0,
    rewardDiamonds: 0,
    rewardHostDiamonds: 0,
    type: 'chat_10',
    audience: 'all',
    minConversationSeconds: HOST_NEW_USER_CHAT.maxWindowSeconds,
    newUserDays: HOST_NEW_USER_CHAT.newUserDays,
  },
  { id: 't8', title: 'ادخل 3 غرف صوتية', rewardPoints: 140, rewardSilver: 40, type: 'rooms_3', audience: 'all' },
  { id: 't9', title: 'أرسل 3 هدايا', rewardPoints: 160, rewardSilver: 30, type: 'gift_3', audience: 'all' },
  { id: 't10', title: 'أرسل 5 هدايا', rewardPoints: 220, rewardSilver: 50, type: 'gift_5', audience: 'all' },
  { id: 't11', title: 'تابع 5 مستخدمين', rewardPoints: 100, rewardSilver: 30, type: 'follow_5', audience: 'all' },
  { id: 't12', title: 'اجلس على الميكروفون مرة', rewardPoints: 50, rewardSilver: 15, type: 'mic_1', audience: 'all' },
  { id: 't13', title: 'اجلس على الميكروفون 3 مرات', rewardPoints: 90, rewardSilver: 25, type: 'mic_3', audience: 'all' },
  { id: 't14', title: 'اجلس على الميكروفون 10 مرات', rewardPoints: 200, rewardSilver: 60, type: 'mic_10', audience: 'all' },
  {
    id: 't15',
    title: 'تحدّث مع 5 مضيفات (جولة دردشة كاملة)',
    rewardPoints: 100,
    rewardSilver: 20,
    type: 'chat_5',
    audience: 'all',
    minConversationSeconds: HOST_NEW_USER_CHAT.maxWindowSeconds,
    newUserDays: HOST_NEW_USER_CHAT.newUserDays,
  },
  { id: 't16', title: 'العب لعبة واحدة', rewardPoints: 80, rewardSilver: 20, type: 'game_1', audience: 'all' },
  { id: 't17', title: 'العب 3 ألعاب', rewardPoints: 150, rewardSilver: 40, type: 'game_3', audience: 'all' },
  { id: 't18', title: 'اربح جولة لعبة', rewardPoints: 120, rewardSilver: 35, type: 'win_1', audience: 'all' },
  { id: 't19', title: 'اربح 3 جولات ألعاب', rewardPoints: 220, rewardSilver: 70, type: 'win_3', audience: 'all' },
  { id: 't20', title: 'ادخل 5 غرف صوتية', rewardPoints: 180, rewardSilver: 50, type: 'rooms_5', audience: 'all' },
];

/** Host-only daily tasks — points/silver only (no cashout diamonds). */
export const DEFAULT_HOST_TASKS: DailyTask[] = [
  {
    id: 'h1',
    title: 'افتتاح غرفة صوتية اليوم',
    rewardPoints: 120,
    rewardSilver: 40,
    rewardDiamonds: 0,
    type: 'host_room_1',
    audience: 'host',
  },
  {
    id: 'h2',
    title: 'اجلسي على الميكروفون 3 مرات',
    rewardPoints: 100,
    rewardSilver: 30,
    rewardDiamonds: 0,
    type: 'host_mic_3',
    audience: 'host',
  },
  {
    id: 'h3',
    title: 'استقبلي 3 هدايا',
    rewardPoints: 150,
    rewardSilver: 50,
    rewardDiamonds: 0,
    type: 'host_gift_3',
    audience: 'host',
  },
  {
    id: 'h4',
    title: 'أكملي 5 جولات دردشة مع مستخدمين جدد (ذكور)',
    rewardPoints: 180,
    rewardSilver: 60,
    rewardDiamonds: 0,
    type: 'host_chat_5',
    audience: 'host',
    minConversationSeconds: HOST_NEW_USER_CHAT.maxWindowSeconds,
    newUserDays: HOST_NEW_USER_CHAT.newUserDays,
  },
  {
    id: 'h4b',
    title: 'أكملي 10 جولات دردشة مع مستخدمين جدد (ذكور)',
    rewardPoints: 280,
    rewardSilver: 90,
    rewardDiamonds: 0,
    type: 'host_chat_10',
    audience: 'host',
    minConversationSeconds: HOST_NEW_USER_CHAT.maxWindowSeconds,
    newUserDays: HOST_NEW_USER_CHAT.newUserDays,
  },
  {
    id: 'h5',
    title: 'حضور يومي للمضيفة',
    rewardPoints: 60,
    rewardSilver: 40,
    rewardDiamonds: 0,
    type: 'host_checkin',
    audience: 'host',
  },
  {
    id: 'h6',
    title: 'ادخلي غرفة صوتية مرتين',
    rewardPoints: 80,
    rewardSilver: 20,
    rewardDiamonds: 0,
    type: 'host_visit_2',
    audience: 'host',
  },
  {
    id: 'h7',
    title: 'استقبلي هدية واحدة على الأقل',
    rewardPoints: 90,
    rewardSilver: 25,
    rewardDiamonds: 0,
    type: 'host_gift_1',
    audience: 'host',
  },
  {
    id: 'h8',
    title: 'اجلسي على الميكروفون مرة',
    rewardPoints: 50,
    rewardSilver: 15,
    rewardDiamonds: 0,
    type: 'host_mic_1',
    audience: 'host',
  },
  {
    id: 'h9',
    title: `ادعِي 3 مستخدمين جدد لغرفتك (+${HOST_ROOM_INVITE_REWARD.diamonds}◆ لكل دعوة مكتملة)`,
    rewardPoints: 100,
    rewardSilver: 30,
    rewardDiamonds: 0,
    type: 'host_invite_3',
    audience: 'host',
  },
  {
    id: 'h10',
    title: 'افتحي غرفتك 3 مرات اليوم',
    rewardPoints: 160,
    rewardSilver: 50,
    rewardDiamonds: 0,
    type: 'host_room_3',
    audience: 'host',
  },
  {
    id: 'h11',
    title: 'اجلسي على الميكروفون 5 مرات',
    rewardPoints: 140,
    rewardSilver: 40,
    rewardDiamonds: 0,
    type: 'host_mic_5',
    audience: 'host',
  },
  {
    id: 'h12',
    title: 'اجلسي على الميكروفون 10 مرات',
    rewardPoints: 220,
    rewardSilver: 70,
    rewardDiamonds: 0,
    type: 'host_mic_10',
    audience: 'host',
  },
  {
    id: 'h13',
    title: 'استقبلي 5 هدايا',
    rewardPoints: 200,
    rewardSilver: 60,
    rewardDiamonds: 0,
    type: 'host_gift_5',
    audience: 'host',
  },
  {
    id: 'h14',
    title: 'استقبلي 10 هدايا',
    rewardPoints: 320,
    rewardSilver: 100,
    rewardDiamonds: 0,
    type: 'host_gift_10',
    audience: 'host',
  },
  {
    id: 'h15',
    title: 'أكملي 15 جولة دردشة مع ذكور جدد',
    rewardPoints: 360,
    rewardSilver: 120,
    rewardDiamonds: 0,
    type: 'host_chat_15',
    audience: 'host',
    minConversationSeconds: HOST_NEW_USER_CHAT.maxWindowSeconds,
    newUserDays: HOST_NEW_USER_CHAT.newUserDays,
  },
  {
    id: 'h16',
    title: 'أكملي 20 جولة دردشة مع ذكور جدد',
    rewardPoints: 450,
    rewardSilver: 150,
    rewardDiamonds: 0,
    type: 'host_chat_20',
    audience: 'host',
    minConversationSeconds: HOST_NEW_USER_CHAT.maxWindowSeconds,
    newUserDays: HOST_NEW_USER_CHAT.newUserDays,
  },
  {
    id: 'h17',
    title: 'ادخلي غرف صوتية 5 مرات',
    rewardPoints: 130,
    rewardSilver: 35,
    rewardDiamonds: 0,
    type: 'host_visit_5',
    audience: 'host',
  },
  {
    id: 'h18',
    title: 'ادعِي مستخدماً جديداً واحداً لغرفتك',
    rewardPoints: 70,
    rewardSilver: 20,
    rewardDiamonds: 0,
    type: 'host_invite_1',
    audience: 'host',
  },
  {
    id: 'h19',
    title: 'ادعِي 5 مستخدمين جدد لغرفتك',
    rewardPoints: 180,
    rewardSilver: 55,
    rewardDiamonds: 0,
    type: 'host_invite_5',
    audience: 'host',
  },
  {
    id: 'h20',
    title: 'ادعِي 10 مستخدمين جدد لغرفتك',
    rewardPoints: 300,
    rewardSilver: 90,
    rewardDiamonds: 0,
    type: 'host_invite_10',
    audience: 'host',
  },
  {
    id: 'h21',
    title: 'العبي لعبة داخل الروم',
    rewardPoints: 90,
    rewardSilver: 25,
    rewardDiamonds: 0,
    type: 'game_1',
    audience: 'host',
  },
  {
    id: 'h22',
    title: 'العبي 3 ألعاب داخل الروم',
    rewardPoints: 160,
    rewardSilver: 45,
    rewardDiamonds: 0,
    type: 'game_3',
    audience: 'host',
  },
  {
    id: 'h23',
    title: 'اربحي جولة لعبة',
    rewardPoints: 110,
    rewardSilver: 30,
    rewardDiamonds: 0,
    type: 'win_1',
    audience: 'host',
  },
];

const TASKS_KEY = 'daily_tasks';
const LEVEL_THRESHOLDS = [
  0, 100, 300, 600, 1000, 1500, 2200, 3000, 4000, 5500, 7500, 10000, 13000, 17000, 22000, 28000,
  35000, 45000, 60000, 80000, 100000,
];

@Injectable()
export class TasksService {
  constructor(
    @InjectRepository(Wallet) private readonly wallets: Repository<Wallet>,
    @InjectRepository(AppSetting) private readonly settings: Repository<AppSetting>,
    @InjectRepository(User) private readonly users: Repository<User>,
    @InjectRepository(AgencyMember) private readonly members: Repository<AgencyMember>,
    private readonly dataSource: DataSource,
    @Optional() private readonly realtime?: RealtimeGateway,
    @Optional() private readonly cosmetics?: CosmeticsService,
    @Optional() private readonly vip?: VipService,
  ) {}

  /** Active agency OWNER/MANAGER/HOST only — agency itself must be ACTIVE. */
  async isActiveAgencyHost(userId: string): Promise<boolean> {
    if (!userId) return false;
    const member = await this.members.findOne({
      where: {
        userId,
        isActive: true,
        status: AgencyMemberStatus.ACTIVE,
        role: In([AgencyRole.OWNER, AgencyRole.MANAGER, AgencyRole.HOST]),
      },
      relations: ['agency'],
    });
    return !!member && member.agency?.status === AgencyStatus.ACTIVE;
  }

  async loadTasks(): Promise<DailyTask[]> {
    const row = await this.settings.findOne({ where: { key: TASKS_KEY } });
    let tasks: DailyTask[] = [...DEFAULT_TASKS, ...DEFAULT_HOST_TASKS];
    if (row?.value) {
      try {
        const parsed = JSON.parse(row.value);
        if (Array.isArray(parsed) && parsed.length) tasks = parsed as DailyTask[];
      } catch {
        /* keep defaults */
      }
    }
    // Ensure chat_10 daily task exists even if dashboard saved an older list.
    if (!tasks.some((t) => this.familyOf(t.type) === 'chat' && (t.audience || 'all') !== 'host')) {
      const def = DEFAULT_TASKS.find((t) => t.id === 't7');
      if (def) tasks = [...tasks, def];
    }
    // Merge any missing default tasks (audience + host) so new catalog entries appear.
    const byId = new Set(tasks.map((t) => t.id));
    for (const dt of [...DEFAULT_TASKS, ...DEFAULT_HOST_TASKS]) {
      if (!byId.has(dt.id)) {
        tasks.push(dt);
        byId.add(dt.id);
      }
    }
    // Hard stop: no free cashout diamonds from any task (host or audience).
    return tasks.map((t) => {
      const audience: TaskAudience = t.audience === 'host' ? 'host' : 'all';
      const title =
        t.id === 'h9'
          ? `ادعِي 3 مستخدمين جدد لغرفتك (+${HOST_ROOM_INVITE_REWARD.diamonds}◆ لكل دعوة مكتملة)`
          : t.title;
      return {
        ...t,
        title,
        audience,
        rewardHostDiamonds: 0,
        rewardDiamonds: 0,
      };
    });
  }

  async saveTasks(tasks: DailyTask[]) {
    if (!Array.isArray(tasks) || !tasks.length) {
      throw new BadRequestException('قائمة المهام فارغة');
    }
    const normalized = tasks.map((t, i) => {
      const scope = (['global', 'room', 'agency'].includes(String(t.scope))
        ? t.scope
        : 'global') as TaskScope;
      const audience: TaskAudience = t.audience === 'host' ? 'host' : 'all';
      return {
        id: String(t.id || `t${i + 1}`).trim(),
        title: String(t.title || '').trim(),
        rewardPoints: Math.max(0, Number(t.rewardPoints || 0)),
        rewardSilver: Math.max(0, Number(t.rewardSilver || 0)),
        rewardDiamonds: 0,
        rewardHostDiamonds: 0,
        rewardCosmeticCode: String(t.rewardCosmeticCode || '').trim() || undefined,
        rewardCosmeticDays: Math.max(
          0,
          Math.floor(Number(t.rewardCosmeticDays) || 0),
        ) || undefined,
        rewardVipLevel: Math.max(0, Math.floor(Number(t.rewardVipLevel) || 0)) || undefined,
        rewardVipDays: Math.max(0, Math.floor(Number(t.rewardVipDays) || 0)) || undefined,
        type: String(t.type || 'custom').trim() || 'custom',
        audience,
        scope,
        roomId: scope === 'room' ? t.roomId || null : null,
        agencyId: scope === 'agency' ? t.agencyId || null : null,
        minConversationSeconds: Math.max(
          0,
          Number(t.minConversationSeconds != null ? t.minConversationSeconds : 120),
        ),
        newUserDays: Math.max(1, Number(t.newUserDays != null ? t.newUserDays : 30)),
      };
    });
    if (normalized.some((t) => !t.id || !t.title)) {
      throw new BadRequestException('كل مهمة تحتاج id وعنوان');
    }
    let row = await this.settings.findOne({ where: { key: TASKS_KEY } });
    if (!row) row = this.settings.create({ key: TASKS_KEY, value: '[]' });
    row.value = JSON.stringify(normalized);
    row.description = 'Daily tasks (dashboard + app)';
    await this.settings.save(row);
    return { items: normalized, total: normalized.length };
  }

  /** Parse target count from type suffix: rooms_1 → 1, gift_2 → 2, checkin → 1 */
  targetFor(type: string): number {
    const m = String(type || '').match(/_(\d+)$/);
    if (m) return Math.max(1, parseInt(m[1], 10));
    return 1;
  }

  /** Event family: rooms | gift | follow | mic | checkin | recharge | host_* | custom */
  familyOf(type: string): string {
    const t = String(type || 'custom').toLowerCase();
    if (t.startsWith('host_room')) return 'host_room';
    if (t.startsWith('host_mic')) return 'host_mic';
    if (t.startsWith('host_gift')) return 'host_gift';
    if (t.startsWith('host_chat')) return 'host_chat';
    if (t.startsWith('host_checkin')) return 'host_checkin';
    if (t.startsWith('host_visit') || t.startsWith('host_rooms')) return 'host_visit';
    if (t.startsWith('host_invite')) return 'host_invite';
    if (t.startsWith('host_agency')) return 'host_agency';
    if (t.startsWith('rooms')) return 'rooms';
    if (t.startsWith('gift')) return 'gift';
    if (t.startsWith('follow')) return 'follow';
    if (t.startsWith('mic')) return 'mic';
    if (t.startsWith('chat') || t.startsWith('message') || t.startsWith('dm')) return 'chat';
    if (t.startsWith('checkin') || t === 'check_in') return 'checkin';
    if (t.startsWith('recharge') || t.startsWith('exchange')) return 'recharge';
    if (t.startsWith('game')) return 'game';
    if (t.startsWith('win')) return 'win';
    return 'custom';
  }

  private dayKey() {
    const d = new Date();
    return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}-${String(d.getUTCDate()).padStart(2, '0')}`;
  }

  private claimKey(userId: string) {
    return `task_claims_${userId}_${this.dayKey()}`;
  }

  private progressKey(userId: string) {
    return `task_progress_${userId}_${this.dayKey()}`;
  }

  private async readClaims(userId: string): Promise<string[]> {
    const row = await this.settings.findOne({ where: { key: this.claimKey(userId) } });
    if (!row?.value) return [];
    try {
      const parsed = JSON.parse(row.value);
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }

  private async writeClaims(userId: string, ids: string[]) {
    const key = this.claimKey(userId);
    let row = await this.settings.findOne({ where: { key } });
    if (!row) row = this.settings.create({ key, value: '[]' });
    row.value = JSON.stringify(ids);
    await this.settings.save(row);
  }

  private async readProgress(userId: string): Promise<Record<string, number>> {
    const row = await this.settings.findOne({ where: { key: this.progressKey(userId) } });
    if (!row?.value) return {};
    try {
      const parsed = JSON.parse(row.value);
      return parsed && typeof parsed === 'object' ? parsed : {};
    } catch {
      return {};
    }
  }

  private async writeProgress(userId: string, map: Record<string, number>) {
    const key = this.progressKey(userId);
    let row = await this.settings.findOne({ where: { key } });
    if (!row) row = this.settings.create({ key, value: '{}' });
    row.value = JSON.stringify(map);
    await this.settings.save(row);
  }

  /**
   * Record real activity toward matching daily tasks.
   * event: rooms | gift | follow | mic | checkin | recharge
   */
  async recordProgress(
    userId: string,
    event: string,
    amount = 1,
    context: TaskProgressContext = {},
  ) {
    if (!userId || !event || amount <= 0) return;
    const family = this.familyOf(event);
    const tasks = await this.loadTasks();
    const matching = tasks.filter(
      (t) => this.familyOf(t.type) === family && this.matchesScope(t, context),
    );
    if (!matching.length) return;

    const updated = await this.dataSource.transaction(async (manager) => {
      const progressKey = this.progressKey(userId);
      const claimKey = this.claimKey(userId);
      await manager.query('SELECT pg_advisory_xact_lock(hashtext($1))', [
        `task-progress:${userId}:${this.dayKey()}`,
      ]);
      let progressRow = await manager.findOne(AppSetting, {
        where: { key: progressKey },
      });
      const claimRow = await manager.findOne(AppSetting, {
        where: { key: claimKey },
      });
      let progress: Record<string, number> = {};
      let claimed: string[] = [];
      try {
        progress = progressRow?.value ? JSON.parse(progressRow.value) : {};
      } catch {
        progress = {};
      }
      try {
        claimed = claimRow?.value ? JSON.parse(claimRow.value) : [];
      } catch {
        claimed = [];
      }

      const changes: Array<{ taskId: string; current: number; target: number }> = [];
      for (const task of matching) {
        if (claimed.includes(task.id)) continue;
        const target = this.targetFor(task.type);
        const current = Number(progress[task.id] || 0);
        const next = Math.min(target, current + amount);
        if (next !== current) {
          progress[task.id] = next;
          changes.push({ taskId: task.id, current: next, target });
        }
      }
      if (!changes.length) return changes;
      if (!progressRow) {
        progressRow = manager.create(AppSetting, {
          key: progressKey,
          value: '{}',
          description: `Daily task progress for ${userId}`,
        });
      }
      progressRow.value = JSON.stringify(progress);
      await manager.save(progressRow);
      return changes;
    });

    if (updated.length) {
      this.realtime?.emitToUser(userId, 'task:progress', {
        event,
        context,
        changes: updated,
        at: new Date().toISOString(),
      });
    }
  }

  async listForUser(userId: string, context: TaskProgressContext = {}) {
    const user = await this.users.findOne({ where: { id: userId } });
    const isFemale = String(user?.gender || '').toLowerCase() === 'female';
    const isMale = String(user?.gender || '').toLowerCase() === 'male';
    const isAgencyHost = isFemale ? await this.isActiveAgencyHost(userId) : false;
    let isNewMale = false;
    if (isMale && user?.createdAt) {
      const ageDays =
        (Date.now() - new Date(user.createdAt).getTime()) / (24 * 60 * 60 * 1000);
      isNewMale = ageDays <= HOST_NEW_USER_CHAT.newUserDays;
    }
    const tasks = (await this.loadTasks()).filter((task) => {
      if (!this.matchesScope(task, context)) return false;
      const audience = task.audience || 'all';
      if (audience === 'host') return isAgencyHost;
      // Male↔hostess chat tasks are for new male accounts only.
      if (this.familyOf(task.type) === 'chat') return isNewMale;
      return true;
    });
    const claimed = await this.readClaims(userId);
    const progress = await this.readProgress(userId);
    return tasks.map((t) => {
      const target = this.targetFor(t.type);
      const current = Math.min(target, Number(progress[t.id] || 0));
      const done = claimed.includes(t.id);
      const claimable = !done && current >= target;
      return {
        ...t,
        target,
        current,
        claimed: done,
        claimable,
        progressLabel: `${current}/${target}`,
        section: (t.audience || 'all') === 'host' ? 'host' : 'daily',
        requiresAgency: (t.audience || 'all') === 'host',
      };
    });
  }

  private matchesScope(task: DailyTask, context: TaskProgressContext) {
    const scope = task.scope || 'global';
    if (scope === 'global') return true;
    if (scope === 'room') {
      return !!context.roomId && (!task.roomId || task.roomId === context.roomId);
    }
    return (
      !!context.agencyId &&
      (!task.agencyId || task.agencyId === context.agencyId)
    );
  }

  /** Explicit daily check-in (must tap — not auto on open). */
  async checkIn(userId: string) {
    await this.recordProgress(userId, 'checkin', 1);
    await this.recordProgress(userId, 'host_checkin', 1);
    return { ok: true, tasks: await this.listForUser(userId) };
  }

  async claim(userId: string, taskId: string) {
    const tasks = await this.loadTasks();
    const task = tasks.find((t) => t.id === taskId);
    if (!task) throw new BadRequestException('المهمة غير موجودة');
    if ((task.audience || 'all') === 'host') {
      if (!(await this.isActiveAgencyHost(userId))) {
        throw new BadRequestException('يجب أن تكوني مسجّلة بوكالة نشطة لاستلام مهام المضيفة');
      }
    }
    const progress = await this.readProgress(userId);
    const target = this.targetFor(task.type);
    const current = Number(progress[task.id] || 0);
    if (current < target) {
      throw new BadRequestException(
        `أكمل المهمة أولاً (${current}/${target}) — ادخل غرفة / أرسل هدية / تابع حسب نوع المهمة`,
      );
    }

    const day = this.dayKey();
    const claimRef = `task_claim:${day}:${taskId}`;

    const result = await this.dataSource.transaction(async (manager) => {
      await manager.query('SELECT pg_advisory_xact_lock(hashtext($1))', [
        `task-claim:${userId}:${day}:${taskId}`,
      ]);
      const existingTx = await manager.findOne(WalletTransaction, {
        where: {
          userId,
          referenceType: 'task_claim',
          referenceId: claimRef,
        },
        lock: { mode: 'pessimistic_write' },
      });
      if (existingTx) {
        throw new BadRequestException('تم استلام هذه المهمة مسبقاً اليوم');
      }

      const claimKey = this.claimKey(userId);
      let claimRow = await manager.findOne(AppSetting, {
        where: { key: claimKey },
        lock: { mode: 'pessimistic_write' },
      });
      let claimed: string[] = [];
      if (claimRow?.value) {
        try {
          const parsed = JSON.parse(claimRow.value);
          claimed = Array.isArray(parsed) ? parsed : [];
        } catch {
          claimed = [];
        }
      }
      if (claimed.includes(taskId)) {
        throw new BadRequestException('تم استلام هذه المهمة مسبقاً اليوم');
      }

      let wallet = await manager.findOne(Wallet, {
        where: { userId },
        lock: { mode: 'pessimistic_write' },
      });
      if (!wallet) {
        wallet = manager.create(Wallet, {
          userId,
          coins: 0,
          diamonds: 0,
          gamePoints: 0,
        });
        wallet = await manager.save(wallet);
        wallet = await manager.findOne(Wallet, {
          where: { userId },
          lock: { mode: 'pessimistic_write' },
        });
      }
      if (!wallet) throw new BadRequestException('المحفظة غير موجودة');

      wallet.coins =
        Number(wallet.coins || 0) +
        Number(task.rewardPoints || 0) +
        Number(task.rewardSilver || 0);
      wallet.gamePoints = 0;
      const claimDiamonds = 0; // No free cashout diamonds from task claims.
      await manager.save(wallet);

      // Host peer diamonds disabled (economy safety).
      const hostDiamonds = 0;
      const hostPeersPaid: string[] = [];
      if (hostDiamonds > 0 && this.familyOf(task.type) === 'chat') {
        const peers = await this.readChatPeers(userId);
        const alreadyPaid = await this.readChatPeersPaid(userId);
        for (const peerId of peers) {
          if (!peerId || peerId === userId || alreadyPaid.includes(peerId)) continue;
          const ok = await this.creditHostChatDiamonds(manager, {
            peerId,
            hostDiamonds,
            fromUserId: userId,
            taskId,
            taskTitle: task.title,
            day,
            referenceId: `${claimRef}:${peerId}`,
          });
          if (ok) {
            hostPeersPaid.push(peerId);
            alreadyPaid.push(peerId);
          }
        }
        if (hostPeersPaid.length) await this.writeChatPeersPaid(userId, alreadyPaid);
      }

      const xpGain = Math.max(10, Math.floor(task.rewardPoints / 2));
      const user = await manager.findOne(User, {
        where: { id: userId },
        lock: { mode: 'pessimistic_write' },
      });
      let levelInfo: { level: number; experience: number; xpGain: number } | null = null;
      if (user) {
        user.experience = Number(user.experience || 0) + xpGain;
        let level = 1;
        for (let i = 0; i < LEVEL_THRESHOLDS.length; i++) {
          if (Number(user.experience) >= LEVEL_THRESHOLDS[i]) level = i + 1;
        }
        user.level = Math.min(level, 50);
        await manager.save(user);
        levelInfo = {
          level: user.level,
          experience: Number(user.experience),
          xpGain,
        };
      }

      claimed.push(taskId);
      if (!claimRow) {
        claimRow = manager.create(AppSetting, {
          key: claimKey,
          value: '[]',
          description: `Daily task claims for ${userId}`,
        });
      }
      claimRow.value = JSON.stringify(claimed);
      await manager.save(claimRow);

      await manager.save(
        manager.create(WalletTransaction, {
          userId,
          type: TransactionType.LUCKY_REWARD,
          currency: CurrencyType.COINS,
          amount: Number(task.rewardPoints || 0) + Number(task.rewardSilver || 0),
          balanceAfter: Number(wallet.coins || 0),
          referenceType: 'task_claim',
          referenceId: claimRef,
          description: `Task claim ${task.title}`,
          metadata: {
            taskId,
            rewardCoins:
              Number(task.rewardPoints || 0) + Number(task.rewardSilver || 0),
            rewardPoints: task.rewardPoints,
            rewardSilver: task.rewardSilver,
            rewardDiamonds: claimDiamonds,
            rewardHostDiamonds: hostDiamonds,
            hostPeersPaid,
            day,
          },
        }),
      );

      return {
        claimed: true,
        task,
        wallet,
        level: levelInfo,
        hostPeersPaid,
        message: 'تم استلام المكافأة',
      };
    });

    const cosmeticCode = String(task.rewardCosmeticCode || '').trim();
    if (cosmeticCode && this.cosmetics) {
      try {
        await this.cosmetics.grantTemporary(
          userId,
          cosmeticCode,
          Number(task.rewardCosmeticDays) || 2,
        );
      } catch {
        /* best-effort */
      }
    }
    const vipLevel = Math.max(0, Math.floor(Number(task.rewardVipLevel) || 0));
    if (vipLevel > 0 && this.vip) {
      try {
        await this.vip.grantTemporaryVip(
          userId,
          vipLevel,
          Number(task.rewardVipDays) || 2,
        );
      } catch {
        /* best-effort */
      }
    }
    return result;
  }

  private chatPeersKey(userId: string) {
    return `chat_task_peers_${userId}_${this.dayKey()}`;
  }

  private chatPeersPaidKey(userId: string) {
    return `chat_task_paid_${userId}_${this.dayKey()}`;
  }

  private chatSessionKey(conversationId: string) {
    return `chat_task_session_${conversationId}_${this.dayKey()}`;
  }

  async readChatPeers(userId: string): Promise<string[]> {
    const row = await this.settings.findOne({ where: { key: this.chatPeersKey(userId) } });
    if (!row?.value) return [];
    try {
      const parsed = JSON.parse(row.value);
      return Array.isArray(parsed) ? parsed.map(String) : [];
    } catch {
      return [];
    }
  }

  private async writeChatPeers(userId: string, peers: string[]) {
    const key = this.chatPeersKey(userId);
    let row = await this.settings.findOne({ where: { key } });
    if (!row) row = this.settings.create({ key, value: '[]' });
    row.value = JSON.stringify(peers);
    await this.settings.save(row);
  }

  async readChatPeersPaid(userId: string): Promise<string[]> {
    const row = await this.settings.findOne({ where: { key: this.chatPeersPaidKey(userId) } });
    if (!row?.value) return [];
    try {
      const parsed = JSON.parse(row.value);
      return Array.isArray(parsed) ? parsed.map(String) : [];
    } catch {
      return [];
    }
  }

  private async writeChatPeersPaid(userId: string, peers: string[]) {
    const key = this.chatPeersPaidKey(userId);
    let row = await this.settings.findOne({ where: { key } });
    if (!row) row = this.settings.create({ key, value: '[]' });
    row.value = JSON.stringify(peers);
    await this.settings.save(row);
  }

  private async creditHostChatDiamonds(
    manager: EntityManager,
    opts: {
      peerId: string;
      hostDiamonds: number;
      fromUserId: string;
      taskId: string;
      taskTitle: string;
      day: string;
      referenceId: string;
    },
  ): Promise<boolean> {
    const { peerId, hostDiamonds, fromUserId, taskId, taskTitle, day, referenceId } = opts;
    if (!peerId || peerId === fromUserId || hostDiamonds <= 0) return false;
    let hostWallet = await manager.findOne(Wallet, {
      where: { userId: peerId },
      lock: { mode: 'pessimistic_write' },
    });
    if (!hostWallet) {
      hostWallet = manager.create(Wallet, {
        userId: peerId,
        coins: 0,
        diamonds: 0,
        gamePoints: 0,
      });
      hostWallet = await manager.save(hostWallet);
      hostWallet = await manager.findOne(Wallet, {
        where: { userId: peerId },
        lock: { mode: 'pessimistic_write' },
      });
    }
    if (!hostWallet) return false;
    hostWallet.diamonds = Number(hostWallet.diamonds || 0) + hostDiamonds;
    await manager.save(hostWallet);
    await manager.save(
      manager.create(WalletTransaction, {
        userId: peerId,
        type: TransactionType.LUCKY_REWARD,
        currency: CurrencyType.DIAMONDS,
        amount: hostDiamonds,
        balanceAfter: Number(hostWallet.diamonds || 0),
        referenceType: 'task_host_chat',
        referenceId,
        description: `مكافأة محادثة مع مستخدم جديد — ${taskTitle}`,
        metadata: { taskId, fromUserId, day },
      }),
    );
    this.realtime?.emitToUser(peerId, 'wallet:updated', {
      diamonds: Number(hostWallet.diamonds || 0),
      reason: 'task_host_chat',
    });
    return true;
  }

  /**
   * Track male↔female DM for daily chat tasks.
   * Counts one round per agency-hostess peer/day when both sides exchange messages
   * (5–10 total) within a 3-minute window. Eligible: newly registered males only.
   * Chat-task claims stay points/silver; room-invite dwell grants diamonds from
   * HOST_ROOM_INVITE_REWARD (agency hosts).
   */
  async trackChatForTasks(params: {
    conversationId: string;
    senderId: string;
    peerId: string;
    senderGender?: string | null;
    peerGender?: string | null;
    senderCreatedAt?: Date | string | null;
  }) {
    const { conversationId, senderId, peerId } = params;
    if (!conversationId || !senderId || !peerId || senderId === peerId) return;

    const sg = String(params.senderGender || '').toLowerCase();
    const pg = String(params.peerGender || '').toLowerCase();
    let maleId: string | null = null;
    let femaleId: string | null = null;
    let maleCreatedAt = params.senderCreatedAt;
    if (sg === 'male' && pg === 'female') {
      maleId = senderId;
      femaleId = peerId;
      maleCreatedAt = params.senderCreatedAt;
    } else if (sg === 'female' && pg === 'male') {
      maleId = peerId;
      femaleId = senderId;
      maleCreatedAt = null;
    } else {
      return;
    }

    if (!(await this.isActiveAgencyHost(femaleId!))) return;

    const tasks = (await this.loadTasks()).filter((t) => this.familyOf(t.type) === 'chat');
    if (!tasks.length) return;
    const maxWindowSec = Math.max(
      60,
      ...tasks.map((t) =>
        Number(t.minConversationSeconds || HOST_NEW_USER_CHAT.maxWindowSeconds),
      ),
      HOST_NEW_USER_CHAT.maxWindowSeconds,
    );
    const minMessages = HOST_NEW_USER_CHAT.minMessages;
    const newUserDays = Math.max(
      1,
      ...tasks.map((t) => Number(t.newUserDays || HOST_NEW_USER_CHAT.newUserDays)),
      HOST_NEW_USER_CHAT.newUserDays,
    );

    if (!maleCreatedAt) {
      const male = await this.users.findOne({ where: { id: maleId! } });
      maleCreatedAt = male?.createdAt || null;
    }
    if (!maleCreatedAt) return;
    const createdMs = new Date(maleCreatedAt).getTime();
    if (!Number.isFinite(createdMs)) return;
    const ageDays = (Date.now() - createdMs) / (24 * 60 * 60 * 1000);
    if (ageDays > newUserDays) return;

    const sessionKey = this.chatSessionKey(conversationId);
    let row = await this.settings.findOne({ where: { key: sessionKey } });
    let session: {
      startedAt: number;
      maleSent: boolean;
      femaleSent: boolean;
      maleMsgCount: number;
      femaleMsgCount: number;
      messageCount: number;
      completed?: boolean;
      expired?: boolean;
      maleId: string;
      femaleId: string;
    } = {
      startedAt: Date.now(),
      maleSent: false,
      femaleSent: false,
      maleMsgCount: 0,
      femaleMsgCount: 0,
      messageCount: 0,
      maleId: maleId!,
      femaleId: femaleId!,
    };
    if (row?.value) {
      try {
        session = { ...session, ...JSON.parse(row.value) };
      } catch {
        /* keep default */
      }
    }
    if (session.completed || session.expired) return;

    if (!session.startedAt) session.startedAt = Date.now();
    const elapsedSec = Math.floor((Date.now() - Number(session.startedAt)) / 1000);
    if (elapsedSec > maxWindowSec) {
      session.expired = true;
      if (!row) row = this.settings.create({ key: sessionKey, value: '{}' });
      row.value = JSON.stringify(session);
      await this.settings.save(row);
      return;
    }

    if (senderId === maleId) {
      session.maleSent = true;
      session.maleMsgCount = Math.min(
        HOST_NEW_USER_CHAT.maxMessages,
        Number(session.maleMsgCount || 0) + 1,
      );
    }
    if (senderId === femaleId) {
      session.femaleSent = true;
      session.femaleMsgCount = Math.min(
        HOST_NEW_USER_CHAT.maxMessages,
        Number(session.femaleMsgCount || 0) + 1,
      );
    }
    session.messageCount = Math.min(
      HOST_NEW_USER_CHAT.maxMessages,
      Number(session.maleMsgCount || 0) + Number(session.femaleMsgCount || 0),
    );

    // Both sides must exchange messages; total 5–10 within the 3-minute window.
    const ready =
      session.maleSent &&
      session.femaleSent &&
      Number(session.maleMsgCount || 0) >= 1 &&
      Number(session.femaleMsgCount || 0) >= 1 &&
      session.messageCount >= minMessages &&
      session.messageCount <= HOST_NEW_USER_CHAT.maxMessages &&
      elapsedSec <= maxWindowSec;

    if (!row) row = this.settings.create({ key: sessionKey, value: '{}' });
    if (ready) {
      session.completed = true;
      row.value = JSON.stringify(session);
      await this.settings.save(row);

      const peers = await this.readChatPeers(maleId!);
      if (!peers.includes(femaleId!)) {
        peers.push(femaleId!);
        await this.writeChatPeers(maleId!, peers);
        await this.recordProgress(maleId!, 'chat', 1);
        if (femaleId) {
          await this.recordProgress(femaleId, 'host_chat', 1);
        }
      }
      return;
    }

    row.value = JSON.stringify(session);
    await this.settings.save(row);
  }

  private roomInviteKey(hostId: string, guestId: string) {
    return `host_room_invite_${hostId}_${guestId}_${this.dayKey()}`;
  }

  private roomInviteCountKey(hostId: string) {
    return `host_room_invite_count_${hostId}_${this.dayKey()}`;
  }

  private roomDwellKey(roomId: string, hostId: string, guestId: string) {
    return `host_room_dwell_${roomId}_${hostId}_${guestId}_${this.dayKey()}`;
  }

  /**
   * Agency hostess invites a new male into her room for the 40◆ dwell reward.
   */
  async inviteGuestForRoomReward(params: {
    roomId: string;
    hostId: string;
    guestId: string;
    guestAlreadyInRoom?: boolean;
  }) {
    const { roomId, hostId, guestId } = params;
    if (!roomId || !hostId || !guestId || hostId === guestId) {
      throw new BadRequestException('دعوة غير صالحة');
    }
    if (!(await this.isActiveAgencyHost(hostId))) {
      throw new BadRequestException('يجب أن تكوني مسجّلة بوكالة نشطة');
    }
    const guest = await this.users.findOne({ where: { id: guestId } });
    if (!guest || String(guest.gender || '').toLowerCase() !== 'male') {
      throw new BadRequestException('المهمة مع حسابات الذكور الجدد فقط');
    }
    const ageDays =
      (Date.now() - new Date(guest.createdAt).getTime()) / (24 * 60 * 60 * 1000);
    if (ageDays > HOST_NEW_USER_CHAT.newUserDays) {
      throw new BadRequestException('المستخدم ليس ضمن فترة المستخدمين الجدد');
    }

    const inviteKey = this.roomInviteKey(hostId, guestId);
    let inviteRow = await this.settings.findOne({ where: { key: inviteKey } });
    let invite: {
      roomId: string;
      hostId: string;
      guestId: string;
      rewarded?: boolean;
      invitedAt: number;
    } = {
      roomId,
      hostId,
      guestId,
      invitedAt: Date.now(),
    };
    if (inviteRow?.value) {
      try {
        invite = { ...invite, ...JSON.parse(inviteRow.value) };
      } catch {
        /* keep */
      }
    }
    if (invite.rewarded) {
      throw new BadRequestException('تم استلام مكافأة هذا المستخدم اليوم — غداً مجدداً');
    }
    invite.roomId = roomId;
    invite.hostId = hostId;
    invite.guestId = guestId;
    invite.invitedAt = Date.now();
    if (!inviteRow) inviteRow = this.settings.create({ key: inviteKey, value: '{}' });
    inviteRow.value = JSON.stringify(invite);
    await this.settings.save(inviteRow);

    this.realtime?.emitToUser(guestId, 'room:event', {
      roomId,
      event: 'room:task_invited',
      payload: {
        roomId,
        hostId,
        guestId,
        rewardDiamonds: HOST_ROOM_INVITE_REWARD.diamonds,
        dwellSeconds: HOST_ROOM_INVITE_REWARD.dwellSeconds,
      },
      at: new Date().toISOString(),
    });

    if (params.guestAlreadyInRoom) {
      await this.onGuestJoinedForInviteReward({ roomId, hostId, guestId });
      setTimeout(() => {
        void this.tryCompleteRoomInviteReward({
          roomId,
          hostId,
          guestId,
        }).catch(() => undefined);
      }, (HOST_ROOM_INVITE_REWARD.dwellSeconds + 2) * 1000);
    }

    return {
      invited: true,
      roomId,
      guestId,
      rewardDiamonds: HOST_ROOM_INVITE_REWARD.diamonds,
      dwellSeconds: HOST_ROOM_INVITE_REWARD.dwellSeconds,
      message:
        HOST_ROOM_INVITE_REWARD.diamonds > 0
          ? `ادعيت المستخدم — عند بقائه ${HOST_ROOM_INVITE_REWARD.dwellSeconds} ثانية تحصلين على ${HOST_ROOM_INVITE_REWARD.diamonds} ماسة`
          : `ادعيت المستخدم — عند بقائه ${HOST_ROOM_INVITE_REWARD.dwellSeconds} ثانية تُحتسب الدعوة للمهام`,
    };
  }

  /** Start/refresh dwell timer when invited guest joins host room. */
  async onGuestJoinedForInviteReward(params: {
    roomId: string;
    hostId: string;
    guestId: string;
  }) {
    const { roomId, hostId, guestId } = params;
    if (!roomId || !hostId || !guestId || hostId === guestId) return null;
    const inviteKey = this.roomInviteKey(hostId, guestId);
    const inviteRow = await this.settings.findOne({ where: { key: inviteKey } });
    if (!inviteRow?.value) return null;
    let invite: { roomId?: string; rewarded?: boolean };
    try {
      invite = JSON.parse(inviteRow.value);
    } catch {
      return null;
    }
    if (invite.rewarded) return null;
    if (invite.roomId && invite.roomId !== roomId) return null;

    const dwellKey = this.roomDwellKey(roomId, hostId, guestId);
    let dwellRow = await this.settings.findOne({ where: { key: dwellKey } });
    const dwell = {
      roomId,
      hostId,
      guestId,
      joinedAt: Date.now(),
      completed: false,
    };
    if (!dwellRow) dwellRow = this.settings.create({ key: dwellKey, value: '{}' });
    else {
      try {
        const prev = JSON.parse(dwellRow.value);
        if (prev?.completed) return null;
      } catch {
        /* reset */
      }
    }
    dwellRow.value = JSON.stringify(dwell);
    await this.settings.save(dwellRow);
    return dwell;
  }

  /**
   * Called periodically or on leave: if guest stayed ≥ dwellSeconds (≤2min) credit 40◆.
   */
  async tryCompleteRoomInviteReward(params: {
    roomId: string;
    hostId: string;
    guestId: string;
    forceCheck?: boolean;
  }) {
    const { roomId, hostId, guestId } = params;
    if (!roomId || !hostId || !guestId) return { rewarded: false };
    const dwellKey = this.roomDwellKey(roomId, hostId, guestId);
    const dwellRow = await this.settings.findOne({ where: { key: dwellKey } });
    if (!dwellRow?.value) return { rewarded: false };
    let dwell: {
      joinedAt: number;
      completed?: boolean;
      hostId: string;
      guestId: string;
    };
    try {
      dwell = JSON.parse(dwellRow.value);
    } catch {
      return { rewarded: false };
    }
    if (dwell.completed) return { rewarded: false };

    const elapsed = Math.floor((Date.now() - Number(dwell.joinedAt)) / 1000);
    const need = HOST_ROOM_INVITE_REWARD.dwellSeconds;
    if (elapsed < need) {
      return { rewarded: false, elapsedSec: elapsed, needSec: need };
    }
    // Cap: reward only if completed within 2 minutes + small grace (already joinedAt based).
    if (elapsed > need + 30 && !params.forceCheck) {
      // Still allow if they stayed longer — requirement is "up to 2 min" meaning
      // they don't need more than 2 min; staying longer still counts once.
    }

    const inviteKey = this.roomInviteKey(hostId, guestId);
    const countKey = this.roomInviteCountKey(hostId);
    const day = this.dayKey();
    const referenceId = `host_room_invite:${day}:${hostId}:${guestId}`;

    const result = await this.dataSource.transaction(async (manager) => {
      await manager.query('SELECT pg_advisory_xact_lock(hashtext($1))', [
        `host-room-invite:${hostId}:${guestId}:${day}`,
      ]);
      const existingTx = await manager.findOne(WalletTransaction, {
        where: {
          userId: hostId,
          referenceType: 'host_room_invite',
          referenceId,
        },
      });
      if (existingTx) return { rewarded: false as const, reason: 'already' };

      let countRow = await manager.findOne(AppSetting, {
        where: { key: countKey },
        lock: { mode: 'pessimistic_write' },
      });
      const count = Math.max(0, Number(countRow?.value || 0));
      if (count >= HOST_ROOM_INVITE_REWARD.maxRewardsPerHostPerDay) {
        return { rewarded: false as const, reason: 'daily_cap' };
      }

      if (!(await this.isActiveAgencyHost(hostId))) {
        return { rewarded: false as const, reason: 'not_agency' };
      }

      let wallet = await manager.findOne(Wallet, {
        where: { userId: hostId },
        lock: { mode: 'pessimistic_write' },
      });
      if (!wallet) {
        wallet = await manager.save(
          manager.create(Wallet, {
            userId: hostId,
            coins: 0,
            diamonds: 0,
            traderDiamonds: 0,
            gamePoints: 0,
          }),
        );
        wallet = await manager.findOne(Wallet, {
          where: { userId: hostId },
          lock: { mode: 'pessimistic_write' },
        });
      }
      if (!wallet) return { rewarded: false as const, reason: 'wallet' };

      const diamonds = Math.max(0, Number(HOST_ROOM_INVITE_REWARD.diamonds) || 0);
      // Platform safety: no free cashout diamonds from room invites when ratio is zeroed.
      if (diamonds > 0) {
        wallet.diamonds = Number(wallet.diamonds || 0) + diamonds;
        await manager.save(wallet);
        await manager.save(
          manager.create(WalletTransaction, {
            userId: hostId,
            type: TransactionType.LUCKY_REWARD,
            currency: CurrencyType.DIAMONDS,
            amount: diamonds,
            balanceAfter: Number(wallet.diamonds || 0),
            referenceType: 'host_room_invite',
            referenceId,
            description: `مكافأة دعوة مستخدم جديد للغرفة (${diamonds} ماسة)`,
            metadata: { roomId, guestId, day, dwellSeconds: need, diamonds },
          }),
        );
      }

      if (!countRow) {
        countRow = manager.create(AppSetting, {
          key: countKey,
          value: '0',
          description: `Host room invite rewards ${hostId}`,
        });
      }
      countRow.value = String(count + 1);
      await manager.save(countRow);

      let inviteRow = await manager.findOne(AppSetting, { where: { key: inviteKey } });
      if (inviteRow) {
        try {
          const inv = JSON.parse(inviteRow.value || '{}');
          inv.rewarded = true;
          inviteRow.value = JSON.stringify(inv);
          await manager.save(inviteRow);
        } catch {
          /* ignore */
        }
      }

      const dwellLocked = await manager.findOne(AppSetting, {
        where: { key: dwellKey },
        lock: { mode: 'pessimistic_write' },
      });
      if (dwellLocked) {
        dwell.completed = true;
        dwellLocked.value = JSON.stringify(dwell);
        await manager.save(dwellLocked);
      }

      return {
        rewarded: true as const,
        diamonds,
        walletDiamonds: Number(wallet.diamonds),
      };
    });

    if (result.rewarded) {
      await this.recordProgress(hostId, 'host_invite', 1);
      if (Number(result.diamonds || 0) > 0) {
        this.realtime?.emitToUser(hostId, 'wallet:updated', {
          diamonds: result.walletDiamonds,
          reason: 'host_room_invite',
          amount: result.diamonds,
        });
      }
      this.realtime?.emitToUser(hostId, 'task:progress', {
        event: 'host_invite',
        diamonds: result.diamonds,
        at: new Date().toISOString(),
      });
    }
    return result;
  }

  /** Resolve active room host for invite dwell (owner or activeHost). */
  async maybeTrackRoomJoinForInvite(params: {
    roomId: string;
    guestId: string;
    roomHostId?: string | null;
    activeHostId?: string | null;
  }) {
    const hostId = params.activeHostId || params.roomHostId;
    if (!hostId || hostId === params.guestId) return;
    await this.onGuestJoinedForInviteReward({
      roomId: params.roomId,
      hostId,
      guestId: params.guestId,
    });
  }

  async maybeCompleteInviteOnLeave(params: {
    roomId: string;
    guestId: string;
    roomHostId?: string | null;
    activeHostId?: string | null;
  }) {
    const hostIds = Array.from(
      new Set([params.activeHostId, params.roomHostId].filter(Boolean) as string[]),
    );
    for (const hostId of hostIds) {
      if (hostId === params.guestId) continue;
      await this.tryCompleteRoomInviteReward({
        roomId: params.roomId,
        hostId,
        guestId: params.guestId,
        forceCheck: true,
      });
    }
  }
}
