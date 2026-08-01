import { EntityManager } from 'typeorm';

/** Run SQL inside a PG transaction; roll back to savepoint on failure (keeps txn alive). */
async function optionalQuery(
  manager: EntityManager,
  sql: string,
  params: unknown[] = [],
): Promise<void> {
  const sp = `opt_${Math.random().toString(36).slice(2, 12)}`;
  await manager.query(`SAVEPOINT "${sp}"`);
  try {
    await manager.query(sql, params);
    await manager.query(`RELEASE SAVEPOINT "${sp}"`);
  } catch {
    await manager.query(`ROLLBACK TO SAVEPOINT "${sp}"`);
  }
}

/** Clear room FK rows before hard-deleting a room (agency delete, admin delete). */
export async function purgeRoomReferencesBeforeDelete(
  manager: EntityManager,
  roomId: string,
  roomTitle: string,
): Promise<void> {
  await manager.query(`UPDATE gift_sends SET "roomId" = NULL WHERE "roomId" = $1`, [roomId]);
  await manager.query(`UPDATE contests SET "roomId" = NULL WHERE "roomId" = $1`, [roomId]);
  await manager.query(`UPDATE lucky_box_opens SET "roomId" = NULL WHERE "roomId" = $1`, [roomId]);
  await manager.query(`DELETE FROM room_access WHERE "roomId" = $1`, [roomId]);
  await optionalQuery(manager, `DELETE FROM room_moderators WHERE "roomId" = $1`, [roomId]);
  await optionalQuery(manager, `DELETE FROM room_seat_signals WHERE "roomId" = $1`, [roomId]);
  await optionalQuery(manager, `DELETE FROM room_bans WHERE "roomId" = $1`, [roomId]);
  await optionalQuery(manager, `DELETE FROM room_games WHERE "roomId" = $1`, [roomId]);
  await manager.query(`DELETE FROM room_seats WHERE "roomId" = $1`, [roomId]);
  await manager.query(
    `UPDATE reports
        SET description = ('deletedRoomTitle=' || $2::text || E'\n' || COALESCE(description, '')),
            "roomId" = NULL
      WHERE "roomId" = $1::uuid`,
    [roomId, roomTitle],
  );
}
