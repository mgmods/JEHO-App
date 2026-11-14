#!/usr/bin/env node
/**
 * Wipe beta economy: coins, diamonds, cosmetics, VIP, game items, history.
 * Accounts, rooms, chats, and follows are kept.
 *
 * Usage:
 *   node scripts/reset-beta-economy.js --dry-run
 *   node scripts/reset-beta-economy.js --confirm
 */
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

const COUNT_QUERIES = [
  ['users', 'SELECT COUNT(*)::int AS n FROM users'],
  ['wallets (coins>0)', 'SELECT COUNT(*)::int AS n FROM wallets WHERE coins > 0'],
  ['wallets (diamonds>0)', 'SELECT COUNT(*)::int AS n FROM wallets WHERE diamonds > 0'],
  ['user_cosmetics', 'SELECT COUNT(*)::int AS n FROM user_cosmetics'],
  ['user_vips', 'SELECT COUNT(*)::int AS n FROM user_vips'],
  ['wallet_transactions', 'SELECT COUNT(*)::int AS n FROM wallet_transactions'],
  ['gift_sends', 'SELECT COUNT(*)::int AS n FROM gift_sends'],
  ['recharge_orders', 'SELECT COUNT(*)::int AS n FROM recharge_orders'],
  ['user_game_items', 'SELECT COUNT(*)::int AS n FROM user_game_items'],
  ['slot_game_sessions', 'SELECT COUNT(*)::int AS n FROM slot_game_sessions'],
];

async function printSnapshot(label) {
  console.log(`\n=== ${label} ===`);
  for (const [name, sql] of COUNT_QUERIES) {
    try {
      const { rows } = await client.query(sql);
      console.log(`  ${name}: ${rows[0].n}`);
    } catch (err) {
      if (name.includes('slot_game') && err.message?.includes('does not exist')) {
        console.log(`  ${name}: (table missing, skipped)`);
      } else {
        throw err;
      }
    }
  }
}

async function main() {
  const confirm = process.argv.includes('--confirm');
  const dryRun = process.argv.includes('--dry-run') || !confirm;

  if (!confirm) {
    console.log('DRY RUN — pass --confirm to execute reset.\n');
  } else {
    console.log('⚠️  PRODUCTION RESET — wiping beta economy...\n');
  }

  await client.connect();
  try {
    await printSnapshot('BEFORE');

    if (dryRun) {
      console.log('\nNo changes made. Re-run with --confirm to apply.');
      return;
    }

    const sql = fs.readFileSync(
      path.join(__dirname, '20260728-reset-beta-economy.sql'),
      'utf8',
    );
    await client.query(sql);
    console.log('\n✅ Beta economy reset applied.');

    await printSnapshot('AFTER');
  } finally {
    await client.end();
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
