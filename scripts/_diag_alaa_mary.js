const fs = require('fs');
const { Client } = require('pg');
function loadEnv(file) {
  const keys = {};
  if (!fs.existsSync(file)) return keys;
  for (const line of fs.readFileSync(file, 'utf8').split(/\r?\n/)) {
    const t = line.trim();
    if (!t || t.startsWith('#') || !t.includes('=')) continue;
    const i = t.indexOf('=');
    let k = t.slice(0, i).trim();
    let v = t.slice(i + 1).trim();
    if ((v.startsWith('"') && v.endsWith('"')) || (v.startsWith("'") && v.endsWith("'")))
      v = v.slice(1, -1);
    keys[k] = v;
  }
  return keys;
}
(async () => {
  const keys = loadEnv('/www/wwwroot/api.adnova.bbs.tr/.env');
  const client = new Client({
    host: keys.DB_HOST,
    port: Number(keys.DB_PORT || 5432),
    database: keys.DB_DATABASE,
    user: keys.DB_USERNAME,
    password: keys.DB_PASSWORD,
  });
  await client.connect();
  const ids = ['93249193', '2222', '780c4781-3a91-4dc0-a724-16f9d0fabd95', '279aa46c-7453-4c8a-900d-7d9968ced1b4'];
  const users = await client.query(
    `SELECT id, "publicId", username, "displayName" FROM users
     WHERE "publicId" = ANY($1::text[]) OR id::text = ANY($1::text[])`,
    [ids],
  );
  const uuids = users.rows.map((u) => u.id);
  const wallets = await client.query(
    `SELECT w.*, u."publicId", u."displayName" FROM wallets w
     JOIN users u ON u.id = w."userId" WHERE w."userId" = ANY($1::uuid[])`,
    [uuids],
  );
  const sends = await client.query(
    `SELECT gs.id, gs."createdAt", gs.quantity, gs."totalCoins", gs."diamondsAwarded",
            gs."roomId", g.name AS gift_name,
            s."publicId" AS sender_public, s."displayName" AS sender_name,
            r."publicId" AS receiver_public, r."displayName" AS receiver_name
     FROM gift_sends gs
     LEFT JOIN gifts g ON g.id = gs."giftId"
     LEFT JOIN users s ON s.id = gs."senderId"
     LEFT JOIN users r ON r.id = gs."receiverId"
     WHERE gs."senderId" = ANY($1::uuid[]) OR gs."receiverId" = ANY($1::uuid[])
     ORDER BY gs."createdAt" DESC LIMIT 15`,
    [uuids],
  );
  const room = await client.query(
    `SELECT id, title, kind, "hostId", "activeHostId", "agencyId", status
     FROM rooms WHERE id = 'cc89a7d4-f160-45c9-84fb-e16af3e5f432'`,
  );
  console.log(
    JSON.stringify(
      {
        users: users.rows,
        wallets: wallets.rows.map((w) => ({
          publicId: w.publicId,
          name: w.displayName,
          coins: w.coins,
          diamonds: w.diamonds,
          agencyDiamonds: w.agencyDiamonds,
          traderDiamonds: w.traderDiamonds,
          updatedAt: w.updatedAt,
        })),
        sends: sends.rows,
        room: room.rows[0] || null,
      },
      null,
      2,
    ),
  );
  await client.end();
})().catch((e) => console.log(JSON.stringify({ error: String(e.message || e) })));
