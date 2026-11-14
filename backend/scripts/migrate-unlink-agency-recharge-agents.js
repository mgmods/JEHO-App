require('dotenv').config();
const fs = require('fs');
const path = require('path');
const { Client } = require('pg');

const client = new Client({
  host: process.env.DB_HOST || 'localhost',
  port: Number(process.env.DB_PORT || 5432),
  user: process.env.DB_USER || process.env.DB_USERNAME || 'postgres',
  password: process.env.DB_PASSWORD || 'postgres',
  database: process.env.DB_NAME || process.env.DB_DATABASE || 'auralive',
  ssl: process.env.DB_SSL === 'true' ? { rejectUnauthorized: false } : undefined,
});

async function main() {
  const sql = fs.readFileSync(
    path.join(__dirname, '20260726-unlink-agency-recharge-agents.sql'),
    'utf8',
  );
  await client.connect();
  try {
    const result = await client.query(sql);
    console.log(
      `Unlinked broadcast-agency auto recharge agents (rowCount=${result.rowCount ?? 0}).`,
    );
  } finally {
    await client.end();
  }
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
