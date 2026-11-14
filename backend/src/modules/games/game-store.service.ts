import { BadRequestException, Injectable, NotFoundException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { DataSource, Repository } from 'typeorm';
import { UserGameItem } from '../../database/entities/user-game-item.entity';
import { Wallet } from '../../database/entities/wallet.entity';
import { AppSetting } from '../../database/entities/app-setting.entity';
import {
  WalletTransaction,
  TransactionType,
  CurrencyType,
} from '../../database/entities/wallet-transaction.entity';

export type GameStoreItem = {
  sku: string;
  title: string;
  costPoints: number;
  section: string;
};

const DEFAULT_CATALOG: GameStoreItem[] = [
  { sku: 'dice_gold', title: 'نرد ذهبي', costPoints: 50, section: 'dice' },
  { sku: 'dice_fire', title: 'نرد ناري', costPoints: 120, section: 'dice' },
  { sku: 'dice_diamond', title: 'نرد ماسي', costPoints: 300, section: 'dice' },
  { sku: 'tile_classic', title: 'طاية كلاسيك', costPoints: 40, section: 'tiles' },
  { sku: 'tile_rose', title: 'طاية وردية', costPoints: 90, section: 'tiles' },
  { sku: 'tile_royal', title: 'طاية ملكية', costPoints: 200, section: 'tiles' },
  { sku: 'stamp_heart', title: 'طابع قلب', costPoints: 30, section: 'stamp' },
  { sku: 'stamp_crown', title: 'طابع تاج', costPoints: 80, section: 'stamp' },
  { sku: 'stamp_vip', title: 'طابع VIP', costPoints: 150, section: 'stamp' },
  { sku: 'emoji_laugh', title: 'تعبير ضحك', costPoints: 20, section: 'emoji' },
  { sku: 'emoji_fire', title: 'تعبير نار', costPoints: 40, section: 'emoji' },
  { sku: 'emoji_crown', title: 'تعبير تاج', costPoints: 100, section: 'emoji' },
];

const CATALOG_KEY = 'games_store_catalog';

@Injectable()
export class GameStoreService {
  constructor(
    @InjectRepository(UserGameItem) private readonly itemsRepo: Repository<UserGameItem>,
    @InjectRepository(Wallet) private readonly walletsRepo: Repository<Wallet>,
    @InjectRepository(AppSetting) private readonly settingsRepo: Repository<AppSetting>,
    private readonly dataSource: DataSource,
  ) {}

  async catalog(section?: string): Promise<GameStoreItem[]> {
    const all = await this.loadCatalog();
    if (!section || section === 'store') return all;
    return all.filter((i) => i.section === section);
  }

  async saveCatalog(items: GameStoreItem[]) {
    if (!Array.isArray(items) || !items.length) {
      throw new BadRequestException('الكتالوج فارغ');
    }
    const normalized = items.map((i) => ({
      sku: String(i.sku || '').trim(),
      title: String(i.title || '').trim(),
      costPoints: Math.max(0, Number(i.costPoints || 0)),
      section: String(i.section || 'store').trim() || 'store',
    }));
    if (normalized.some((i) => !i.sku || !i.title)) {
      throw new BadRequestException('كل عنصر يحتاج sku وعنوان');
    }
    const skus = normalized.map((item) => item.sku.toLowerCase());
    if (new Set(skus).size !== skus.length) {
      throw new BadRequestException('لا يمكن تكرار SKU في كتالوج الألعاب');
    }
    let row = await this.settingsRepo.findOne({ where: { key: CATALOG_KEY } });
    if (!row) row = this.settingsRepo.create({ key: CATALOG_KEY, value: '[]' });
    row.value = JSON.stringify(normalized);
    row.description = 'Game store catalog (dashboard + app)';
    await this.settingsRepo.save(row);
    return { items: normalized, total: normalized.length };
  }

  async inventory(userId: string) {
    const rows = await this.itemsRepo.find({ where: { userId }, order: { createdAt: 'DESC' } });
    const catalog = await this.loadCatalog();
    const loadout = await this.getLoadout(userId);
    return rows.map((row) => {
      const meta = catalog.find((c) => c.sku === row.sku);
      const section = meta?.section || this.sectionFromSku(row.sku);
      return {
        id: row.id,
        sku: row.sku,
        title: row.title,
        costPoints: row.costPoints,
        section,
        equipped: !!(section && loadout[section] === row.sku),
        createdAt: row.createdAt,
      };
    });
  }

  async loadout(userId: string) {
    return this.getLoadout(userId);
  }

  async equip(userId: string, sku: string) {
    const owned = await this.itemsRepo.findOne({ where: { userId, sku } });
    if (!owned) throw new BadRequestException('لا تملك هذا العنصر');
    const catalog = await this.loadCatalog();
    const meta = catalog.find((c) => c.sku === sku);
    const section = meta?.section || this.sectionFromSku(sku);
    if (!section) throw new BadRequestException('قسم العنصر غير معروف');
    const loadout = await this.getLoadout(userId);
    loadout[section] = sku;
    await this.saveLoadout(userId, loadout);
    return { ok: true, section, sku, loadout };
  }

  async purchase(userId: string, sku: string) {
    const catalog = await this.loadCatalog();
    const item = catalog.find((i) => i.sku === sku);
    if (!item) throw new NotFoundException('العنصر غير موجود');

    return this.dataSource.transaction(async (manager) => {
      const owned = await manager.findOne(UserGameItem, {
        where: { userId, sku },
        lock: { mode: 'pessimistic_write' },
      });
      if (owned) throw new BadRequestException('تملك هذا العنصر مسبقاً');

      const wallet = await manager.findOne(Wallet, {
        where: { userId },
        lock: { mode: 'pessimistic_write' },
      });
      if (!wallet) throw new BadRequestException('المحفظة غير موجودة');
      const coinsBal = Number(wallet.coins || 0);
      if (coinsBal < item.costPoints) {
        throw new BadRequestException('رصيد الكوينز غير كافٍ');
      }

      wallet.coins = coinsBal - item.costPoints;
      wallet.gamePoints = 0;
      await manager.save(wallet);

      const row = await manager.save(
        manager.create(UserGameItem, {
          userId,
          sku: item.sku,
          title: item.title,
          costPoints: item.costPoints,
        }),
      );

      await manager.save(
        manager.create(WalletTransaction, {
          userId,
          type: TransactionType.EXCHANGE,
          currency: CurrencyType.COINS,
          amount: -item.costPoints,
          balanceAfter: Number(wallet.coins || 0),
          referenceType: 'game_store_purchase',
          referenceId: `${userId}:${item.sku}`,
          description: `Game store purchase ${item.title}`,
          metadata: {
            sku: item.sku,
            costCoins: item.costPoints,
            coinsAfter: Number(wallet.coins),
          },
        }),
      );

      const loadout = await this.getLoadout(userId);
      loadout[item.section] = item.sku;
      await this.saveLoadout(userId, loadout);

      return {
        item: {
          ...row,
          section: item.section,
          equipped: true,
        },
        coins: Number(wallet.coins),
        gamePoints: 0,
        loadout,
      };
    });
  }

  private loadoutKey(userId: string) {
    return `game_loadout_${userId}`;
  }

  private async getLoadout(userId: string): Promise<Record<string, string>> {
    const row = await this.settingsRepo.findOne({ where: { key: this.loadoutKey(userId) } });
    if (!row?.value) return {};
    try {
      const parsed = JSON.parse(row.value);
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
        return parsed as Record<string, string>;
      }
    } catch {
      /* fallthrough */
    }
    return {};
  }

  private async saveLoadout(userId: string, loadout: Record<string, string>) {
    const key = this.loadoutKey(userId);
    let row = await this.settingsRepo.findOne({ where: { key } });
    if (!row) row = this.settingsRepo.create({ key, value: '{}' });
    row.value = JSON.stringify(loadout);
    row.description = 'Equipped game-store cosmetics per section';
    await this.settingsRepo.save(row);
  }

  private sectionFromSku(sku: string): string {
    if (!sku) return 'store';
    if (sku.startsWith('dice_')) return 'dice';
    if (sku.startsWith('tile_')) return 'tiles';
    if (sku.startsWith('stamp_')) return 'stamp';
    if (sku.startsWith('emoji_')) return 'emoji';
    return 'store';
  }

  private async loadCatalog(): Promise<GameStoreItem[]> {
    const row = await this.settingsRepo.findOne({ where: { key: CATALOG_KEY } });
    if (!row?.value) return DEFAULT_CATALOG;
    try {
      const parsed = JSON.parse(row.value);
      if (Array.isArray(parsed) && parsed.length) return parsed as GameStoreItem[];
    } catch {
      /* fallthrough */
    }
    return DEFAULT_CATALOG;
  }
}
