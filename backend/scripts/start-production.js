const { Client } = require('pg');
const { spawn } = require('child_process');
const { inspectJehoSchemaForProvisioning } = require('./jeho-empty-schema-guard');

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

// Destructive reset is opt-in and runs only once. It drops only the app-owned
// schema, never the PostgreSQL database, public schema, Redis, or voice config.
// A marker inside the recreated schema prevents repeated resets on later restarts.
async function resetSchemaOnceIfRequested() {
  if (process.env.JEHO_OWN_RESET_ONCE !== 'true') return false;
  if (process.env.NODE_ENV === 'production') {
    throw new Error('JEHO_OWN_RESET_ONCE is blocked in production to protect live data.');
  }
  if (!process.env.DATABASE_URL) {
    throw new Error('JEHO_OWN_RESET_ONCE requires DATABASE_URL; refusing to guess which database to reset.');
  }

  const client = new Client({ connectionString: process.env.DATABASE_URL, connectionTimeoutMillis: 8000, query_timeout: 10000 });
  try {
    await client.connect();
    const marker = await client.query(
      "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='jeho_own' AND table_name='__reset_once_marker') AS done",
    );
    if (marker.rows[0]?.done) {
      console.log('[JEHO-OWN startup] One-time schema reset already completed; preserving schema.');
      return false;
    }

    console.warn('[JEHO-OWN startup] JEHO_OWN_RESET_ONCE=true: dropping only schema jeho_own before fresh seed.');
    await client.query('DROP SCHEMA IF EXISTS "jeho_own" CASCADE');
    return true;
  } finally {
    await client.end().catch(() => {});
  }
}

async function markResetCompleted() {
  const client = new Client({ connectionString: process.env.DATABASE_URL, connectionTimeoutMillis: 8000, query_timeout: 10000 });
  try {
    await client.connect();
    await client.query('CREATE SCHEMA IF NOT EXISTS "jeho_own"');
    await client.query('CREATE TABLE IF NOT EXISTS "jeho_own"."__reset_once_marker" (id integer PRIMARY KEY)');
    await client.query('INSERT INTO "jeho_own"."__reset_once_marker" (id) VALUES (1) ON CONFLICT (id) DO NOTHING');
    console.log('[JEHO-OWN startup] One-time schema reset marked complete.');
  } finally {
    await client.end().catch(() => {});
  }
}


// Explicit, non-destructive first-install provisioning.
// It runs only when JEHO_OWN_PROVISION_EMPTY=true and only against a schema
// proven empty while holding the same global lock used by all runtime migrations.
async function provisionEmptyProductionSchemaIfRequested() {
  if (process.env.JEHO_OWN_PROVISION_EMPTY !== 'true') return false;
  if (process.env.NODE_ENV !== 'production') {
    throw new Error('JEHO_OWN_PROVISION_EMPTY is reserved for an explicit production first install.');
  }

  const databaseUrl = process.env.DATABASE_URL?.trim();
  const connection = databaseUrl
    ? { connectionString: databaseUrl, ssl: process.env.DB_SSL === 'true' ? { rejectUnauthorized: false } : undefined }
    : {
        host: process.env.DB_HOST?.trim(),
        port: Number(process.env.DB_PORT || 5432),
        user: process.env.DB_USERNAME?.trim(),
        password: process.env.DB_PASSWORD,
        database: process.env.DB_DATABASE?.trim(),
        ssl: process.env.DB_SSL === 'true' ? { rejectUnauthorized: false } : undefined,
      };
  if (!databaseUrl && !(connection.host && connection.user && connection.password && connection.database)) {
    throw new Error('JEHO_OWN_PROVISION_EMPTY requires complete database connection settings.');
  }

  const client = new Client({ ...connection, connectionTimeoutMillis: 8000, query_timeout: 15000 });
  let lockAcquired = false;
  try {
    await client.connect();
    const lock = await client.query(
      'SELECT pg_try_advisory_lock($1, $2) AS locked',
      [741902611, 99],
    );
    lockAcquired = lock.rows[0]?.locked === true;
    if (!lockAcquired) {
      throw new Error('Another JEHO schema migration/provisioning task is active; first-install provisioning was not started.');
    }

    await client.query('CREATE SCHEMA IF NOT EXISTS "jeho_own"');
    const schemaState = await inspectJehoSchemaForProvisioning(client);

    if (!schemaState.markerExists) {
      if (!schemaState.isEmpty) {
        throw new Error(
          `Refusing first-install provisioning: jeho_own is not empty (relations=${schemaState.relations}, customTypes=${schemaState.customTypes}, routines=${schemaState.routines}) and has no JEHO provisioning marker. Existing/partial data is preserved.`,
        );
      }
      await client.query(`
        CREATE TABLE "jeho_own"."__jeho_provision_state" (
          id integer PRIMARY KEY CHECK (id = 1),
          status varchar(16) NOT NULL CHECK (status IN ('running','complete')),
          started_at timestamptz NOT NULL DEFAULT now(),
          completed_at timestamptz
        )
      `);
      await client.query(
        "INSERT INTO \"jeho_own\".\"__jeho_provision_state\" (id,status) VALUES (1,'running')",
      );
      console.log('[JEHO-OWN startup] Verified empty app schema; created first-install marker.');
    } else {
      if (schemaState.status === 'complete') {
        console.log('[JEHO-OWN startup] First-install provisioning already complete; no schema changes requested.');
        return false;
      }
      if (schemaState.status !== 'running') {
        throw new Error('JEHO provisioning marker is invalid; refusing automatic schema changes.');
      }
      console.warn('[JEHO-OWN startup] Resuming an interrupted, explicitly marked first-install seed.');
    }

    const seedCode = await runProcess('explicit empty-schema provisioning seed', process.execPath, ['dist/database/seed.js']);
    if (seedCode !== 0) {
      throw new Error('First-install seed failed. The schema remains marked running; API startup is blocked and a retry can safely resume this initial seed.');
    }
    await client.query(
      'UPDATE "jeho_own"."__jeho_provision_state" SET status = \'complete\', completed_at = now() WHERE id = 1',
    );
    console.log('[JEHO-OWN startup] First-install provisioning completed and marked complete.');
    return true;
  } finally {
    if (lockAcquired) {
      await client.query('SELECT pg_advisory_unlock($1, $2)', [741902611, 99]).catch(() => undefined);
    }
    await client.end().catch(() => {});
  }
}

async function terminateStaleJehoSessions() {
  const databaseUrl = process.env.DATABASE_URL?.trim();
  const connection = databaseUrl
    ? { connectionString: databaseUrl, ssl: process.env.DB_SSL === 'true' ? { rejectUnauthorized: false } : undefined }
    : {
        host: process.env.DB_HOST?.trim(),
        port: Number(process.env.DB_PORT || 5432),
        user: process.env.DB_USERNAME?.trim(),
        password: process.env.DB_PASSWORD,
        database: process.env.DB_DATABASE?.trim(),
        ssl: process.env.DB_SSL === 'true' ? { rejectUnauthorized: false } : undefined,
      };
  if (!databaseUrl && !(connection.host && connection.user && connection.password && connection.database)) {
    throw new Error('JEHO startup cannot clean stale sessions: incomplete database connection settings.');
  }

  const client = new Client({
    ...connection,
    connectionTimeoutMillis: 8000,
    query_timeout: 5000,
    application_name: 'jeho-own-session-recovery',
  });
  try {
    await client.connect();
    // Recover only JEHO's own abandoned application/bootstrap sessions. This is
    // intentionally scoped by application_name and current database/user; it
    // never touches unrelated PostgreSQL services.
    const result = await client.query(`
      SELECT
        activity.pid,
        activity.application_name,
        activity.state,
        pg_terminate_backend(activity.pid) AS terminated
      FROM pg_stat_activity AS activity
      WHERE activity.pid <> pg_backend_pid()
        AND activity.datname = current_database()
        AND activity.usename = current_user
        AND activity.application_name IN ('jeho-own-api', 'jeho-own-bootstrap')
        -- Never terminate active sessions: during rolling deploys another
        -- healthy JEHO replica may still serve requests or apply a migration.
        -- Recover only a long-idle holder of JEHO's exact shared migration lock.
        AND activity.state = 'idle'
        AND activity.xact_start IS NULL
        AND activity.state_change < now() - interval '2 minutes'
        AND EXISTS (
          SELECT 1
          FROM pg_locks AS held_lock
          WHERE held_lock.pid = activity.pid
            AND held_lock.locktype = 'advisory'
            AND held_lock.granted
            AND held_lock.classid = 741902611
            AND held_lock.objid = 99
            AND held_lock.objsubid = 2
        )
    `);
    const terminatedRows = result.rows.filter((row) => row.terminated === true);
    if (terminatedRows.length) {
      console.warn('[JEHO-OWN startup] Terminated stale JEHO PostgreSQL session(s) before application startup:', JSON.stringify(
        terminatedRows.map(({ pid, application_name, state }) => ({ pid, application_name, state })),
      ));
    } else {
      console.log('[JEHO-OWN startup] No stale JEHO PostgreSQL sessions or JEHO advisory-lock holders found.');
    }
  } finally {
    await client.end().catch(() => {});
  }
}

async function databaseIsReady() {
  const databaseUrl = process.env.DATABASE_URL?.trim();
  const dbHost = process.env.DB_HOST?.trim();
  const dbUsername = process.env.DB_USERNAME?.trim();
  const dbPassword = process.env.DB_PASSWORD;
  const dbDatabase = process.env.DB_DATABASE?.trim();

  // Support both connection styles used by seed.ts and TypeORM. Previously,
  // when only DB_* variables were configured, this returned false on every
  // cold start and re-ran the full seed before opening the API port.
  if (!databaseUrl && !(dbHost && dbUsername && dbPassword && dbDatabase)) {
    console.error('[JEHO-OWN startup] Database connection settings are incomplete; refusing automatic seed.');
    return null;
  }

  let client;
  try {
    const connection = databaseUrl
      ? { connectionString: databaseUrl, ssl: process.env.DB_SSL === 'true' ? { rejectUnauthorized: false } : undefined }
      : {
          host: dbHost,
          port: Number(process.env.DB_PORT || 5432),
          user: dbUsername,
          password: dbPassword,
          database: dbDatabase,
          ssl: process.env.DB_SSL === 'true' ? { rejectUnauthorized: false } : undefined,
        };
    client = new Client({ ...connection, connectionTimeoutMillis: 8000, query_timeout: 10000 });
    await client.connect();
    const schemaState = await inspectJehoSchemaForProvisioning(client);
    if (schemaState.markerExists && schemaState.status !== 'complete') {
      console.error('[JEHO-OWN startup] First-install provisioning is incomplete; refusing to start API.');
      return null;
    }
    const result = await client.query(
      "select exists (select 1 from information_schema.tables where table_schema='jeho_own' and table_name='users') as ready",
    );
    const ready = Boolean(result.rows[0]?.ready);
    console.log(`[JEHO-OWN startup] Database readiness check: ${ready ? 'ready; skipping seed' : 'schema not initialized; seed required'}.`);
    return ready;
  } catch (error) {
    console.error('[JEHO-OWN startup] Database readiness check failed:', JSON.stringify({
      name: error?.name,
      code: error?.code,
      message: error?.message,
    }));
    // null means the DB is unreachable. Do not mistake an outage for an empty database.
    return null;
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

  await terminateStaleJehoSessions();
  await provisionEmptyProductionSchemaIfRequested();
  const resetWasRequested = await resetSchemaOnceIfRequested();
  if (resetWasRequested) {
    const seedCode = await runProcess('fresh database seed', process.execPath, ['dist/database/seed.js']);
    if (seedCode !== 0) {
      console.error('[JEHO-OWN startup] Fresh seed failed; API will not start.');
      process.exit(seedCode);
    }
    await markResetCompleted();
    console.log('[JEHO-OWN startup] Fresh seed completed successfully.');
  } else {
    const ready = await databaseIsReady();
    if (ready === null) {
      console.error('[JEHO-OWN startup] Database is unreachable; refusing to run seed or start against an unverified database.');
      process.exit(1);
    }
    if (ready) {
      console.log('[JEHO-OWN startup] Database ready — skipping seed.');
    } else if (process.env.NODE_ENV === 'production') {
      console.error('[JEHO-OWN startup] Production schema is not initialized; refusing automatic seed to protect existing data.');
      process.exit(1);
    } else {
      console.log('[JEHO-OWN startup] Development database is not initialized — running seed.');
      const seedCode = await runProcess('database seed', process.execPath, ['dist/database/seed.js']);
      if (seedCode !== 0) {
        console.error('[JEHO-OWN startup] Seed failed; API will not start.');
        process.exit(seedCode);
      }
      console.log('[JEHO-OWN startup] Seed completed successfully.');
    }
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
