export type CasualPlayRequestIdentity = {
  gameId: string;
  amount: number;
  roomId?: string;
  choice?: string | number;
};

/**
 * Idempotency keys identify immutable play requests. A replay is valid only
 * when the payload matches the original request exactly.
 */
export function isSameCasualPlayRequest(
  metadata: Record<string, unknown>,
  request: CasualPlayRequestIdentity,
): boolean {
  return (
    Number(metadata.amount) === request.amount &&
    String(metadata.gameId || '') === request.gameId &&
    String(metadata.roomId || '') === String(request.roomId || '') &&
    JSON.stringify(metadata.choice ?? null) === JSON.stringify(request.choice ?? null)
  );
}
