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

async function databaseIsReady() {
  if (!process.env.DATABASE_URL) {
    console.log('[JEHO-OWN startup] DATABASE_URL is not set; skipping URL-based readiness check and allowing seed to use DB_* settings.');
    return false;
  }

  let client;
  try {
    client = new Client({ connectionString: process.env.DATABASE_URL });
    await client.connect();
    const result = await client.query(
      "select exists (select 1 from information_schema.tables where table_schema='jeho_own' and table_name='users') as ready",
    );
    return Boolean(result.rows[0]?.ready);
  } catch (error) {
    console.error('[JEHO-OWN startup] Database readiness check failed:', JSON.stringify({
      name: error?.name,
      code: error?.code,
      message: error?.message,
    }));
    return false;
  } finally {
    if (client) await client.end().catch(() => {});
  }
}

async function runProcess(label, command, args) {
  console.log(`[JEHO-OWN startup] Starting ${label}...`);
  const child = spawn(command, args, {
    stdio: 'inherit',
    env: process.env,
  });

  return new Promise((resolve) => {
    child.once('error', (error) => {
      console.error(`[JEHO-OWN startup] Failed to start ${label}:`, error?.message || error);
      resolve(1);
    });
    child.once('exit', (code, signal) => {
      console.log(`[JEHO-OWN startup] ${label} exited:`, JSON.stringify({
        code,
        signal,
      }));
      resolve(code ?? 1);
    });
  });
}

async function main() {
  logDatabaseConfiguration();

  if (await databaseIsReady()) {
    console.log('[JEHO-OWN startup] Database ready — skipping seed.');
  } else {
    console.log('[JEHO-OWN startup] Database not initialized or URL check unavailable — running seed.');
    const seedCode = await runProcess('database seed', process.execPath, ['dist/database/seed.js']);
    if (seedCode !== 0) {
      console.error('[JEHO-OWN startup] Seed failed; API will not start.');
      process.exit(seedCode);
    }
    console.log('[JEHO-OWN startup] Seed completed successfully.');
  }

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
