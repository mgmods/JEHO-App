import { execFile } from 'child_process';
import * as fs from 'fs/promises';
import * as path from 'path';
import { promisify } from 'util';

const execFileAsync = promisify(execFile);

/** Live JEHO / Adastra sites — never delete these document roots. */
const KEEP_WWWROOT = new Set([
  'api.adnova.bbs.tr',
  'chats.adnova.bbs.tr',
  'cloud.adastra.bbs.tr',
  'Voice.adastra.bbs.tr',
  'voice.adastra.bbs.tr',
  'java_node_ssl',
  'server-landing',
  'default',
]);

/** PM2 apps that must stay running. */
const KEEP_PM2 = new Set(['auralive-api', 'lyvo-cloud']);

export type JunkCleanupResult = {
  actions: string[];
  freedBytes: number;
  stoppedPm2: string[];
};

async function run(cmd: string, args: string[], timeoutMs = 60_000): Promise<{ stdout: string; stderr: string; code: number }> {
  try {
    const { stdout, stderr } = await execFileAsync(cmd, args, {
      timeout: timeoutMs,
      maxBuffer: 2 * 1024 * 1024,
      windowsHide: true,
    });
    return { stdout: String(stdout || ''), stderr: String(stderr || ''), code: 0 };
  } catch (e: any) {
    return {
      stdout: String(e?.stdout || ''),
      stderr: String(e?.stderr || e?.message || ''),
      code: typeof e?.code === 'number' ? e.code : 1,
    };
  }
}

async function dirSize(p: string): Promise<number> {
  try {
    const st = await fs.stat(p);
    if (st.isFile()) return st.size;
    if (!st.isDirectory()) return 0;
    const kids = await fs.readdir(p, { withFileTypes: true });
    let sum = 0;
    for (const k of kids) {
      sum += await dirSize(path.join(p, k.name));
    }
    return sum;
  } catch {
    return 0;
  }
}

async function emptyDir(dir: string): Promise<number> {
  let freed = 0;
  try {
    const kids = await fs.readdir(dir, { withFileTypes: true });
    for (const k of kids) {
      const full = path.join(dir, k.name);
      freed += await dirSize(full);
      await fs.rm(full, { recursive: true, force: true });
    }
  } catch {
    /* missing is fine */
  }
  return freed;
}

async function truncateFile(file: string): Promise<number> {
  try {
    const st = await fs.stat(file);
    if (!st.isFile() || st.size <= 0) return 0;
    await fs.truncate(file, 0);
    return st.size;
  } catch {
    return 0;
  }
}

async function truncateGlobDir(dir: string, ext: string[]): Promise<number> {
  let freed = 0;
  try {
    const kids = await fs.readdir(dir, { withFileTypes: true });
    for (const k of kids) {
      if (!k.isFile()) continue;
      const lower = k.name.toLowerCase();
      if (!ext.some((e) => lower.endsWith(e))) continue;
      freed += await truncateFile(path.join(dir, k.name));
    }
  } catch {
    /* ignore */
  }
  return freed;
}

async function deleteRotatedLogs(root: string): Promise<number> {
  let freed = 0;
  try {
    const kids = await fs.readdir(root, { withFileTypes: true });
    for (const k of kids) {
      const full = path.join(root, k.name);
      if (k.isDirectory()) {
        if (k.name === 'journal') continue;
        freed += await deleteRotatedLogs(full);
        continue;
      }
      if (!k.isFile()) continue;
      const n = k.name;
      if (n.endsWith('.gz') || /\.\d+$/.test(n) || n.endsWith('.old')) {
        try {
          const st = await fs.stat(full);
          await fs.unlink(full);
          freed += st.size;
        } catch {
          /* ignore */
        }
      }
    }
  } catch {
    /* ignore */
  }
  return freed;
}

/**
 * Safe host junk cleanup: recycle bin, OS/PM2 logs, caches.
 * Never touches live site roots, PostgreSQL/MySQL data, or keep-listed PM2 apps.
 */
export async function cleanupServerJunk(): Promise<JunkCleanupResult> {
  const actions: string[] = [];
  const stoppedPm2: string[] = [];
  let freedBytes = 0;

  const recycle = await emptyDir('/.Recycle_bin');
  if (recycle > 0) {
    freedBytes += recycle;
    actions.push('recycle_bin');
  }
  freedBytes += await emptyDir('/www/.Recycle_bin');

  freedBytes += await truncateGlobDir('/root/.pm2/logs', ['.log']);
  freedBytes += await truncateGlobDir('/www/wwwlogs', ['.log']);
  actions.push('logs_truncated');

  freedBytes += await deleteRotatedLogs('/var/log');
  for (const f of [
    '/var/log/syslog',
    '/var/log/auth.log',
    '/var/log/kern.log',
    '/var/log/btmp',
  ]) {
    freedBytes += await truncateFile(f);
  }

  const journal = await run('journalctl', ['--vacuum-size=200M'], 90_000);
  if (journal.code === 0) actions.push('journal_vacuum');

  await run('apt-get', ['clean'], 60_000);
  try {
    const crashKids = await fs.readdir('/var/crash');
    for (const n of crashKids) {
      const full = path.join('/var/crash', n);
      freedBytes += await dirSize(full);
      await fs.rm(full, { recursive: true, force: true });
    }
    if (crashKids.length) actions.push('crash_cleared');
  } catch {
    /* ignore */
  }

  const npmCache = '/root/.npm/_cacache';
  const npmSize = await dirSize(npmCache);
  if (npmSize > 0) {
    await fs.rm(npmCache, { recursive: true, force: true });
    freedBytes += npmSize;
    actions.push('npm_cache');
  }
  await fs.rm('/root/.npm/_logs', { recursive: true, force: true }).catch(() => undefined);
  await fs.rm('/root/.cache/pip', { recursive: true, force: true }).catch(() => undefined);

  // Extra wwwroot folders only — never the keep list.
  try {
    const sites = await fs.readdir('/www/wwwroot', { withFileTypes: true });
    for (const s of sites) {
      if (!s.isDirectory()) continue;
      if (KEEP_WWWROOT.has(s.name)) continue;
      const full = path.join('/www/wwwroot', s.name);
      const sz = await dirSize(full);
      await fs.rm(full, { recursive: true, force: true });
      freedBytes += sz;
      actions.push(`removed_wwwroot:${s.name}`);
    }
  } catch {
    /* ignore */
  }

  const pm2 = await run('pm2', ['jlist'], 20_000);
  if (pm2.code === 0 && pm2.stdout.trim()) {
    try {
      const list = JSON.parse(pm2.stdout) as Array<{ name?: string }>;
      for (const app of list) {
        const name = String(app?.name || '').trim();
        if (!name || KEEP_PM2.has(name)) continue;
        await run('pm2', ['delete', name], 20_000);
        stoppedPm2.push(name);
      }
      if (stoppedPm2.length) {
        await run('pm2', ['save', '--force'], 15_000);
        actions.push('pm2_extras_stopped');
      }
    } catch {
      /* ignore malformed jlist */
    }
  }

  return { actions, freedBytes, stoppedPm2 };
}
