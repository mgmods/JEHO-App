/**
 * File-backed store for LYVO Cloud (standalone — no JEHO postgres).
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { nanoid } from 'nanoid';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

/** Default sellable voice packages (you edit prices/minutes in Seller panel). */
export function defaultPackages() {
  const now = new Date().toISOString();
  return [
    {
      id: 'pkg_starter',
      name: 'Starter',
      minutes: 5000,
      priceUsd: 9,
      badge: 'Popular small',
      description: '5,000 voice minutes · good for side projects',
      active: true,
      sort: 10,
      createdAt: now,
    },
    {
      id: 'pkg_growth',
      name: 'Growth',
      minutes: 25000,
      priceUsd: 39,
      badge: 'Best value',
      description: '25,000 voice minutes · studios & live rooms',
      active: true,
      sort: 20,
      createdAt: now,
    },
    {
      id: 'pkg_scale',
      name: 'Scale',
      minutes: 100000,
      priceUsd: 99,
      badge: '',
      description: '100,000 voice minutes · production apps',
      active: true,
      sort: 30,
      createdAt: now,
    },
    {
      id: 'pkg_enterprise',
      name: 'Enterprise',
      minutes: 500000,
      priceUsd: 399,
      badge: 'High volume',
      description: '500,000 voice minutes · multi-app teams',
      active: true,
      sort: 40,
      createdAt: now,
    },
  ];
}

function emptyDb() {
  return {
    developers: [],
    projects: [],
    topups: [],
    orders: [],
    packages: defaultPackages(),
    settings: {
      fourthwall: {
        apiUser: '',
        apiPassword: '',
        storefrontToken: '',
        shopDomain: '',
        webhookSecret: '',
      },
    },
    notices: [
      {
        id: 'n1',
        title: 'Card payments via Fourthwall',
        body: 'Same card checkout as JEHO app (fourthwall.com). Seller pastes Open API + storefront token once.',
        at: new Date().toISOString(),
      },
      {
        id: 'n2',
        title: 'Sell voice minutes',
        body: 'Create packages (minutes + price). Buyers pay by card; minutes credit automatically.',
        at: new Date().toISOString(),
      },
    ],
  };
}

export function openStore(dataDir) {
  const dir = path.resolve(dataDir || path.join(__dirname, '..', 'data'));
  fs.mkdirSync(dir, { recursive: true });
  const file = path.join(dir, 'lyvo-db.json');

  function read() {
    if (!fs.existsSync(file)) {
      const empty = emptyDb();
      fs.writeFileSync(file, JSON.stringify(empty, null, 2), 'utf8');
      return empty;
    }
    const db = JSON.parse(fs.readFileSync(file, 'utf8'));
    migrate(db);
    return db;
  }

  function migrate(db) {
    if (!Array.isArray(db.packages) || !db.packages.length) {
      db.packages = defaultPackages();
    }
    if (!Array.isArray(db.orders)) db.orders = [];
    if (!Array.isArray(db.topups)) db.topups = [];
    if (!db.settings) db.settings = {};
    if (!db.settings.fourthwall) {
      db.settings.fourthwall = {
        apiUser: '',
        apiPassword: '',
        storefrontToken: '',
        shopDomain: '',
        webhookSecret: '',
      };
    }
    for (const d of db.developers || []) {
      if (d.paidMinutesRemaining == null) d.paidMinutesRemaining = 0;
      if (d.paidMinutesTotal == null) d.paidMinutesTotal = 0;
      if (d.role == null) d.role = 'developer';
    }
  }

  function write(db) {
    fs.writeFileSync(file, JSON.stringify(db, null, 2), 'utf8');
  }

  function update(mutator) {
    const db = read();
    const out = mutator(db) ?? db;
    write(out);
    return out;
  }

  return { read, write, update, file, dir, emptyDb, defaultPackages };
}
