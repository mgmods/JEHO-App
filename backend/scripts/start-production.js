const { Client } = require('pg');
const { spawn } = require('child_process');

async function databaseIsReady() {
  const client = new Client({ connectionString: process.env.DATABASE_URL });
  try {
    await client.connect();
    const result = await client.query(
      "select exists (select 1 from information_schema.tables where table_schema='jeho_own' and table_name='users') as ready",
    );
    return Boolean(result.rows[0]?.ready);
  } catch (error) {
    console.warn('JEHO-OWN startup database check failed; seed will run:', error?.message || error);
    return false;
  } finally {
    await client.end().catch(() => {});
  }
}

async function main() {
  if (await databaseIsReady()) {
    console.log('JEHO-OWN database ready — skipping expensive boot seed');
  } else {
    console.log('JEHO-OWN database not initialized — running seed');
    const seed = spawn(process.execPath, ['dist/database/seed.js'], {
      stdio: 'inherit',
      env: process.env,
    });
    const code = await new Promise((resolve) => {
      seed.on('exit', (exitCode, signal) => resolve(exitCode ?? 1));
      seed.on('error', () => resolve(1));
    });
    if (code !== 0) process.exit(code);
  }

  const app = spawn(process.execPath, ['dist/main.js'], {
    stdio: 'inherit',
    env: process.env,
  });

  const forward = (signal) => app.kill(signal);
  process.on('SIGTERM', () => forward('SIGTERM'));
  process.on('SIGINT', () => forward('SIGINT'));

  app.on('exit', (code, signal) => {
    process.exit(code ?? (signal ? 1 : 0));
  });
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
