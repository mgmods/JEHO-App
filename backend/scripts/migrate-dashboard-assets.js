require('dotenv').config();
const { Client } = require('pg');

const client = new Client({
  host: process.env.DB_HOST || 'localhost',
  port: Number(process.env.DB_PORT || 5432),
  user: process.env.DB_USER || process.env.DB_USERNAME || 'postgres',
  password: process.env.DB_PASSWORD || 'postgres',
  database: process.env.DB_NAME || process.env.DB_DATABASE || 'auralive',
  ssl: process.env.DB_SSL === 'true' ? { rejectUnauthorized: false } : undefined,
});

const sql = `
ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS "vipBadgeUrl" varchar(512);
ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS "levelBadgeUrl" varchar(512);
ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS "hostBadgeUrl" varchar(512);
`;

async function ensureUniqueCosmeticCode() {
  const dupes = await client.query(`
    SELECT code, COUNT(*) AS cnt
    FROM cosmetics
    GROUP BY code
    HAVING COUNT(*) > 1
    LIMIT 5
  `);
  if (dupes.rows.length) {
    console.warn(
      'Skipping unique index on cosmetics.code — duplicate codes found:',
      dupes.rows.map((r) => `${r.code} (${r.cnt})`).join(', '),
    );
    return;
  }
  await client.query(`
    CREATE UNIQUE INDEX IF NOT EXISTS "UQ_cosmetics_code" ON cosmetics (code)
  `);
  console.log('Unique index UQ_cosmetics_code ensured');
}

async function main() {
  await client.connect();
  console.log('Running dashboard assets migration...');
  await client.query(sql);
  console.log('user_profiles badge columns ensured');
  await ensureUniqueCosmeticCode();
  await client.end();
  console.log('Done.');
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
