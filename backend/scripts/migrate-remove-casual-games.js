/**

 * Removes ONO/Domino/Ludo and resets app_games to wheel + dice + Mikoo slots.

 */

require('dotenv').config();

const { Client } = require('pg');



const ORIGIN = process.env.PUBLIC_API_ORIGIN || 'https://api.adnova.bbs.tr';



const client = new Client({

  host: process.env.DB_HOST || 'localhost',

  port: Number(process.env.DB_PORT || 5432),

  user: process.env.DB_USER || process.env.DB_USERNAME || 'postgres',

  password: process.env.DB_PASSWORD || 'postgres',

  database: process.env.DB_NAME || process.env.DB_DATABASE || 'auralive',

  ssl: process.env.DB_SSL === 'true' ? { rejectUnauthorized: false } : undefined,

});



const MIKOO = [

  ['7updown', '7 Up Down'],

  ['cleopatra-slot', 'Cleopatra Slot'],

  ['cleopatra-slots', 'Cleopatra Slots'],

  ['crash', 'Crash'],

  ['fishing', 'Fishing'],

  ['football-plinko', 'Football Plinko'],

  ['fortune-slot', 'Fortune Slot'],

  ['greedy-box', 'Greedy Box'],

  ['hilo', 'Hilo'],

  ['line-slots', 'Line Slots'],

  ['luck-car', 'Luck Car'],

  ['megaways-slots', 'Megaways Slots'],

  ['olympians', 'Olympians'],

  ['pirate-king', 'Pirate King'],

  ['royal-battle', 'Royal Battle'],

  ['slot777', 'Slot 777'],

  ['sugar-rush', 'Sugar Rush'],

  ['swimsuit-party', 'Swimsuit Party'],

];



function buildCatalog() {

  const out = [

    {

      id: 'lucky-wheel',

      title: 'عجلة الحظ',

      titleEn: 'Lucky Wheel',

      coverUrl: `${ORIGIN}/games/lucky-wheel/seven-77.png`,

      playUrl: `${ORIGIN}/games/lucky-wheel.html`,

      sortOrder: 1,

      mode: 'server_solo',

    },

    {

      id: 'dice',

      title: 'لعبة النرد',

      titleEn: 'Dice',

      coverUrl: `${ORIGIN}/games/dice/cover.png`,

      playUrl: `${ORIGIN}/games/dice.html`,

      sortOrder: 2,

      mode: 'server_solo',

    },

  ];

  let order = 10;

  for (const [id, title] of MIKOO) {

    out.push({

      id,

      title,

      titleEn: title,

      coverUrl: `${ORIGIN}/games/mikoo/covers/${id}.png`,

      playUrl: `${ORIGIN}/games/mikoo/${id}/index.html`,

      sortOrder: order++,

      mode: 'mikoo_slot',

    });

  }

  return out;

}



async function main() {

  await client.connect();

  try {

    await client.query('BEGIN');

    await client.query('DELETE FROM casual_matches').catch(() => undefined);

    await client.query('DROP TABLE IF EXISTS casual_matches').catch(() => undefined);

    const catalog = JSON.stringify(buildCatalog());

    await client.query(

      `INSERT INTO app_settings (key, value, "updatedAt")

       VALUES ('app_games', $1, NOW())

       ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, "updatedAt" = NOW()`,

      [catalog],

    );

    for (const key of ['games.reward.ono.coins', 'games.reward.domino.coins', 'games.reward.ludo.coins']) {

      await client.query('DELETE FROM app_settings WHERE key = $1', [key]);

    }

    await client.query('COMMIT');

    console.log('Casual games removed; catalog reset.');

  } catch (err) {

    await client.query('ROLLBACK');

    throw err;

  } finally {

    await client.end();

  }

}



main().catch((e) => {

  console.error(e);

  process.exit(1);

});


