/**
 * Seat mic reaction stickers — managed from dashboard (AppSetting `seat_stickers`).
 * Defaults point at `/assets/seat-stickers/eXX.gif|webp` on the API origin.
 */

export type SeatStickerItem = {
  id: string;
  key: string;
  url: string;
  name?: string;
  sortOrder: number;
  isActive: boolean;
  accessType: 'free' | 'paid' | 'vip' | 'cp';
  priceCoins: number;
  audience: 'all' | 'male' | 'female';
  requiredVipLevel: number;
  requiresCp: boolean;
  categoryId: string;
};

export type SeatStickerCategory = {
  id: string; name: string; iconUrl?: string; sortOrder: number; isActive: boolean;
  accessType: 'free' | 'paid' | 'vip' | 'cp'; priceCoins: number; durationDays: number;
  audience: 'all' | 'male' | 'female'; requiredVipLevel: number; requiresCp: boolean;
};

export type SeatStickersConfig = {
  categories: SeatStickerCategory[];
  version: number;
  updatedAt?: string;
  enabled: boolean;
  items: SeatStickerItem[];
};

const DEFAULT_CATEGORIES: SeatStickerCategory[] = [];

function defaultItems(): SeatStickerItem[] {
  // No bundled/legacy stickers: admins create fresh categories and upload assets.
  return [];
}


