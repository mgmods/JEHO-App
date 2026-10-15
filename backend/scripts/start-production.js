const { Client } = require('pg');
const { spawn } = require('child_process');

function logDatabaseConfiguration() {
  console.log('[JEHO-OWN startup] Database configuration:', JSON.stringify({
    DATABASE_URL: Boolean(process.env.DATABASE_URL),
    DB_HOST: Boolean(process.env.DB_HOST),
    DB_PORT: Boolean(process.env.DB_PORT),
    DB_USERNAME: Boolean(process.env.DB_USERNAME),
    DB_PASSWORD: Boolean(process.env.DB_PASSWORD),
    DB_DATABASE: Boolean(process.env.DB_DATABASE),
  }));
}

// Production startup is read-only with respect to schema initialization.
// A missing/unreachable database must never trigger seed, reset, or synchronize.
async function assertDatabaseReady() {
  if (!process.env.DATABASE_URL) {
    throw new Error('DATABASE_URL is not set; refusing to seed or reset the production database.');
  }

  const client = new Client({ connectionString: process.env.DATABASE_URL });
  try {
    await client.connect();
    const result = await client.query(
      "select exists (select 1 from information_schema.tables where table_schema='jeho_own' and table_name='users') as ready",
    );
    if (!result.rows[0]?.ready) {
      throw new Error('jeho_own.users is missing; refusing automatic seed/synchronize. Review and apply an explicit migration first.');
    }
    console.log('[JEHO-OWN startup] Database reachable and existing schema detected — skipping seed.');
  } catch (error) {
    console.error('[JEHO-OWN startup] Database readiness check failed:', JSON.stringify({
      name: error?.name,
      code: error?.code,
      message: error?.message,
    }));
    throw error;
  } finally {
    await client.end().catch(() => {});
  }
}

async function main() {
  logDatabaseConfiguration();
  await assertDatabaseReady();

  const app = spawn(process.execPath, ['dist/main.js'], {
    stdio: 'inherit',
    env: process.env,
  });

  const forward = (signal) => app.kill(signal);
  process.on('SIGTERM', () => forward('SIGTERM'));
  process.on('SIGINT', () => forward('SIGINT'));

  app.once('error', (error) => {
    console.error('[JEHO-OWN startup] Failed to start API process:', error?.message || error);
    process.exit(1);
  });
  app.on('exit', (code, signal) => {
    console.error('[JEHO-OWN startup] API process exited:', JSON.stringify({ code, signal }));
    process.exit(code ?? (signal ? 1 : 0));
  });
}

main().catch((error) => {
  console.error('[JEHO-OWN startup] Fatal startup error:', JSON.stringify({
    name: error?.name,
    code: error?.code,
    message: error?.message,
  }));
  process.exit(1);
});
