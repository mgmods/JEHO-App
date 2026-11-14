let roomPkEnabledCache: { value: boolean; checkedAt: number } | null = null;

export function readCachedRoomPkEnabled(now = Date.now()): boolean | null {
  if (!roomPkEnabledCache || now - roomPkEnabledCache.checkedAt >= 1500) return null;
  return roomPkEnabledCache.value;
}

export function cacheRoomPkEnabled(value: boolean, now = Date.now()): void {
  roomPkEnabledCache = { value, checkedAt: now };
}

/** Called as soon as the dashboard changes the global switch. */
export function invalidateRoomPkEnabledCache(): void {
  roomPkEnabledCache = null;
}
