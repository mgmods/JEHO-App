/**
 * LYVO Cloud API — standalone console for selling voice minutes + projects.
 * Separate from JEHO Nest backend.
 */
import 'dotenv/config';
import express from 'express';
import cors from 'cors';
import path from 'path';
import { fileURLToPath } from 'url';
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import { nanoid } from 'nanoid';
import crypto from 'crypto';
import { openStore } from './store.mjs';
import { loadFourthwallFromEnvAndSettings } from './fourthwall.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PORT = Number(process.env.PORT || 3910);
const JWT_SECRET = process.env.JWT_SECRET || 'lyvo-dev-insecure';
const PUBLIC_URL = (process.env.PUBLIC_URL || `http://127.0.0.1:${PORT}`).replace(/\/$/, '');
const DATA_DIR = process.env.DATA_DIR || path.join(__dirname, '..', 'data');
const OWNER_EMAIL = (process.env.BOOTSTRAP_EMAIL || 'owner@lyvo.cloud').toLowerCase();

const store = openStore(DATA_DIR);
const app = express();
app.use(cors({ origin: true, credentials: true }));

function round2(n) {
  return Math.round(Number(n) * 100) / 100;
}

function isSeller(dev) {
  if (!dev) return false;
  if (dev.role === 'seller' || dev.role === 'owner') return true;
  return String(dev.email || '').toLowerCase() === OWNER_EMAIL;
}

function minutesSnapshot(d) {
  const freeTotal = Number(d.freeMinutesTotal ?? 10000);
  const freeUsed = Number(d.freeMinutesUsed ?? 0);
  const freeRem = Math.max(0, freeTotal - freeUsed);
  const paidRem = Math.max(0, Number(d.paidMinutesRemaining ?? 0));
  const paidTotal = Math.max(0, Number(d.paidMinutesTotal ?? 0));
  return {
    freeTotal,
    freeUsed,
    freeRemaining: freeRem,
    paidRemaining: paidRem,
    paidTotal,
    totalRemaining: freeRem + paidRem,
    trialExpiresAt: d.trialExpiresAt || null,
  };
}

function publicDev(d) {
  const m = minutesSnapshot(d);
  const admin = isSeller(d);
  return {
    id: d.id,
    email: d.email,
    displayName: d.displayName,
    balanceUsd: Number(d.balanceUsd || 0),
    role: admin ? 'owner' : 'developer',
    isSeller: admin,
    isAdmin: admin,
    freeMinutesTotal: m.freeTotal,
    freeMinutesUsed: m.freeUsed,
    paidMinutesRemaining: m.paidRemaining,
    paidMinutesTotal: m.paidTotal,
    totalMinutesRemaining: m.totalRemaining,
    trialExpiresAt: m.trialExpiresAt,
    status: d.status,
    createdAt: d.createdAt,
  };
}

function publicPackage(p) {
  return {
    id: p.id,
    name: p.name,
    minutes: Number(p.minutes),
    priceUsd: Number(p.priceUsd),
    badge: p.badge || '',
    description: p.description || '',
    active: p.active !== false,
    sort: p.sort ?? 100,
    perThousandUsd:
      p.minutes > 0 ? round2((Number(p.priceUsd) * 1000) / Number(p.minutes)) : null,
    createdAt: p.createdAt,
    updatedAt: p.updatedAt || null,
  };
}

function publicProject(p, { revealSecret = false } = {}) {
  return {
    id: p.id,
    name: p.name,
    projectCode: p.projectCode,
    appId: p.appId,
    apiKey: p.apiKey,
    apiSecret: revealSecret ? p.apiSecret : undefined,
    apiSecretConfigured: Boolean(p.apiSecret),
    status: p.status,
    roomPrefix: p.roomPrefix,
    voiceMinutesUsed: p.voiceMinutesUsed || 0,
    description: p.description || '',
    createdAt: p.createdAt,
    updatedAt: p.updatedAt,
  };
}

function publicOrder(o) {
  return {
    id: o.id,
    type: o.type || 'package',
    packageId: o.packageId || null,
    packageName: o.packageName || null,
    minutes: o.minutes || 0,
    amountUsd: Number(o.amountUsd || 0),
    status: o.status,
    mode: o.mode || o.provider || 'stripe',
    createdAt: o.createdAt,
    paidAt: o.paidAt || null,
  };
}

function creditPackage(developerId, pkg, orderId) {
  store.update((db) => {
    const order = db.orders.find((t) => t.id === orderId);
    if (order && order.status === 'pending') {
      order.status = 'paid';
      order.paidAt = new Date().toISOString();
    }
    const dev = db.developers.find((d) => d.id === developerId);
    if (dev) {
      const mins = Number(pkg.minutes || order?.minutes || 0);
      dev.paidMinutesRemaining = Math.max(0, Number(dev.paidMinutesRemaining || 0)) + mins;
      dev.paidMinutesTotal = Math.max(0, Number(dev.paidMinutesTotal || 0)) + mins;
    }
    return db;
  });
}

function fourthwallSettings() {
  const db = store.read();
  return db.settings?.fourthwall || {};
}

function fwClient() {
  return loadFourthwallFromEnvAndSettings(fourthwallSettings());
}

function maskSecret(s) {
  const t = String(s || '');
  if (!t) return '';
  if (t.length <= 6) return '••••';
  return `…${t.slice(-4)}`;
}

function creditFourthwallPayload(data) {
  const blob = JSON.stringify(data || {});
  const skuMatch = blob.match(/LYVO_SKU:([a-zA-Z0-9_\-]+)/i);
  const sku = skuMatch ? skuMatch[1] : '';
  const claimMatch = blob.match(/LYVO-[A-Z0-9]{6,14}/i);
  const claimCode = claimMatch ? claimMatch[0].toUpperCase() : '';
  const cartId = String(
    data?.cartId || data?.cart?.id || data?.checkout?.cartId || '',
  ).trim();
  const checkoutId = String(data?.checkoutId || data?.checkout?.id || '').trim();
  const fwOrderId = String(data?.id || data?.orderId || '').trim();

  const db = store.read();
  let order =
    (claimCode && db.orders.find((o) => String(o.claimCode || '').toUpperCase() === claimCode)) ||
    (cartId && db.orders.find((o) => o.providerOrderId === cartId || o.cartId === cartId)) ||
    (checkoutId &&
      db.orders.find(
        (o) =>
          o.checkoutId === checkoutId ||
          o.providerPayload?.checkoutId === checkoutId,
      )) ||
    (fwOrderId &&
      db.orders.find(
        (o) => o.fwOrderId === fwOrderId || o.providerPaymentId === fwOrderId,
      )) ||
    null;

  if (!order && sku) {
    // Latest pending for this package
    order = [...db.orders]
      .filter((o) => o.status === 'pending' && (o.packageId === sku || o.sku === sku))
      .sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt)))[0];
  }

  if (!order || order.status === 'paid') return { credited: false, reason: 'no_pending' };

  const pkg = {
    minutes: order.minutes,
    id: order.packageId,
  };
  store.update((d) => {
    const o = d.orders.find((x) => x.id === order.id);
    if (o) {
      o.fwOrderId = fwOrderId || o.fwOrderId;
      o.providerPaymentId = fwOrderId || o.providerPaymentId;
      o.provider = 'fourthwall';
    }
    return d;
  });
  creditPackage(order.developerId, pkg, order.id);
  return { credited: true, orderId: order.id };
}

async function fulfillStripeSession(session) {
  const type = session.metadata?.lyvoType;
  const developerId = session.metadata?.lyvoDeveloperId;
  if (!developerId) return;

  if (type === 'package') {
    const orderId = session.metadata?.lyvoOrderId;
    const packageId = session.metadata?.lyvoPackageId;
    if (!orderId) return;
    const db = store.read();
    const order = db.orders.find((o) => o.id === orderId);
    const pkg = db.packages.find((p) => p.id === packageId) || {
      minutes: order?.minutes,
      name: order?.packageName,
    };
    if (order && order.status === 'pending') creditPackage(developerId, pkg, orderId);
    return;
  }

  // wallet top-up
  const topupId = session.metadata?.lyvoTopupId;
  if (!topupId) return;
  store.update((db) => {
    const order = db.topups.find((t) => t.id === topupId);
    if (order && order.status === 'pending') {
      order.status = 'paid';
      order.paidAt = new Date().toISOString();
      const dev = db.developers.find((d) => d.id === developerId);
      if (dev) {
        dev.balanceUsd = round2(Number(dev.balanceUsd || 0) + Number(order.amountUsd));
      }
    }
    return db;
  });
}

// Stripe webhook raw body
app.post(
  '/api/billing/stripe-webhook',
  express.raw({ type: 'application/json' }),
  async (req, res) => {
    const secret = process.env.STRIPE_WEBHOOK_SECRET;
    const key = process.env.STRIPE_SECRET_KEY;
    if (!secret || !key) return res.status(503).json({ error: 'stripe_not_configured' });
    try {
      const Stripe = (await import('stripe')).default;
      const stripe = new Stripe(key);
      const event = stripe.webhooks.constructEvent(
        req.body,
        req.headers['stripe-signature'],
        secret,
      );
      if (event.type === 'checkout.session.completed') {
        await fulfillStripeSession(event.data.object);
      }
      res.json({ received: true });
    } catch (e) {
      res.status(400).json({ error: String(e.message || e) });
    }
  },
);

// Fourthwall card webhook (same family as JEHO app)
app.post(
  '/api/billing/fourthwall/webhook',
  express.raw({ type: 'application/json' }),
  (req, res) => {
    try {
      const secret =
        fourthwallSettings().webhookSecret || process.env.FOURTHWALL_WEBHOOK_SECRET || '';
      const raw = Buffer.isBuffer(req.body) ? req.body : Buffer.from(String(req.body || ''));
      if (secret) {
        const expected = crypto.createHmac('sha256', secret).update(raw).digest('base64');
        const given = String(req.headers['x-fourthwall-signature'] || req.headers['x-webhook-signature'] || '').trim();
        if (!given || given !== expected) {
          // Some shops use different sig schemes — still try parse if soft mode
          if (process.env.FOURTHWALL_STRICT_SIG === '1') {
            return res.status(401).json({ error: 'invalid_signature' });
          }
        }
      }
      let event;
      try {
        event = JSON.parse(raw.toString('utf8'));
      } catch {
        return res.status(400).json({ error: 'invalid_json' });
      }
      const type = String(event?.type || '');
      if (type !== 'ORDER_PLACED' && type !== 'ORDER_UPDATED') {
        return res.json({ received: true, type, credited: false });
      }
      let data = event?.data || {};
      if (data?.order && typeof data.order === 'object') {
        data = { ...data.order, checkoutId: data.order.checkoutId || data.checkoutId };
      }
      const result = creditFourthwallPayload(data);
      res.json({ received: true, type, ...result });
    } catch (e) {
      res.status(500).json({ error: String(e.message || e) });
    }
  },
);

app.use(express.json({ limit: '1mb' }));

function signToken(dev) {
  return jwt.sign(
    { sub: dev.id, email: dev.email, typ: 'lyvo' },
    JWT_SECRET,
    { expiresIn: '14d' },
  );
}

function auth(req, res, next) {
  const h = req.headers.authorization || '';
  const token = h.startsWith('Bearer ') ? h.slice(7) : null;
  if (!token) return res.status(401).json({ error: 'unauthorized' });
  try {
    const payload = jwt.verify(token, JWT_SECRET);
    if (payload.typ !== 'lyvo') return res.status(401).json({ error: 'unauthorized' });
    const db = store.read();
    const dev = db.developers.find((d) => d.id === payload.sub);
    if (!dev || dev.status === 'suspended') return res.status(401).json({ error: 'unauthorized' });
    req.developer = { ...dev, passwordHash: undefined };
    next();
  } catch {
    return res.status(401).json({ error: 'unauthorized' });
  }
}

function requireSeller(req, res, next) {
  if (!isSeller(req.developer)) return res.status(403).json({ error: 'seller_only' });
  next();
}

function bootstrap() {
  store.update((db) => {
    if (!db.packages?.length) db.packages = store.defaultPackages();
    if (!db.orders) db.orders = [];
    if (db.developers.length) {
      // Ensure bootstrap email is seller
      for (const d of db.developers) {
        if (String(d.email).toLowerCase() === OWNER_EMAIL) {
          d.role = 'owner';
        }
        if (d.paidMinutesRemaining == null) d.paidMinutesRemaining = 0;
        if (d.paidMinutesTotal == null) d.paidMinutesTotal = 0;
      }
      return db;
    }
    const email = OWNER_EMAIL;
    const password = process.env.BOOTSTRAP_PASSWORD || 'ChangeMeNow!';
    const name = process.env.BOOTSTRAP_NAME || 'LYVO Owner';
    const trial = new Date();
    trial.setFullYear(trial.getFullYear() + 1);
    db.developers.push({
      id: nanoid(),
      email,
      displayName: name,
      passwordHash: bcrypt.hashSync(password, 10),
      balanceUsd: 0,
      freeMinutesTotal: 10000,
      freeMinutesUsed: 0,
      paidMinutesRemaining: 0,
      paidMinutesTotal: 0,
      role: 'owner',
      trialExpiresAt: trial.toISOString(),
      status: 'active',
      createdAt: new Date().toISOString(),
    });
    return db;
  });
}
bootstrap();

// ── Auth ──
app.post('/api/auth/register', (req, res) => {
  const email = String(req.body?.email || '').trim().toLowerCase();
  const password = String(req.body?.password || '');
  const displayName = String(req.body?.displayName || '').trim() || email.split('@')[0];
  if (!email.includes('@') || password.length < 8) {
    return res.status(400).json({ error: 'invalid_email_or_password' });
  }
  let created;
  try {
    store.update((db) => {
      if (db.developers.some((d) => d.email === email)) {
        const err = new Error('email_taken');
        err.code = 'email_taken';
        throw err;
      }
      const trial = new Date();
      trial.setFullYear(trial.getFullYear() + 1);
      created = {
        id: nanoid(),
        email,
        displayName,
        passwordHash: bcrypt.hashSync(password, 10),
        balanceUsd: 0,
        freeMinutesTotal: 10000,
        freeMinutesUsed: 0,
        paidMinutesRemaining: 0,
        paidMinutesTotal: 0,
        role: email === OWNER_EMAIL ? 'owner' : 'developer',
        trialExpiresAt: trial.toISOString(),
        status: 'active',
        createdAt: new Date().toISOString(),
      };
      db.developers.push(created);
      return db;
    });
  } catch (e) {
    if (e.code === 'email_taken') return res.status(409).json({ error: 'email_taken' });
    throw e;
  }
  res.json({ token: signToken(created), developer: publicDev(created) });
});

app.post('/api/auth/login', (req, res) => {
  const email = String(req.body?.email || '').trim().toLowerCase();
  const password = String(req.body?.password || '');
  const db = store.read();
  const dev = db.developers.find((d) => d.email === email);
  if (!dev || !bcrypt.compareSync(password, dev.passwordHash)) {
    return res.status(401).json({ error: 'invalid_credentials' });
  }
  store.update((d) => {
    const row = d.developers.find((x) => x.id === dev.id);
    if (row) {
      row.lastLoginAt = new Date().toISOString();
      if (email === OWNER_EMAIL) row.role = 'owner';
    }
    return d;
  });
  const fresh = store.read().developers.find((d) => d.id === dev.id);
  res.json({ token: signToken(fresh), developer: publicDev(fresh) });
});

app.get('/api/auth/me', auth, (req, res) => {
  res.json({ developer: publicDev(req.developer) });
});

// ── Dashboard ──
app.get('/api/dashboard', auth, (req, res) => {
  const db = store.read();
  const projects = db.projects.filter((p) => p.ownerId === req.developer.id);
  const m = minutesSnapshot(req.developer);
  const packages = (db.packages || [])
    .filter((p) => p.active !== false)
    .sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0))
    .map(publicPackage);
  const myOrders = (db.orders || [])
    .filter((o) => o.developerId === req.developer.id)
    .sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt)))
    .slice(0, 20)
    .map(publicOrder);

  let sales = null;
  if (isSeller(req.developer)) {
    const paid = (db.orders || []).filter((o) => o.status === 'paid');
    sales = {
      ordersPaid: paid.length,
      revenueUsd: round2(paid.reduce((s, o) => s + Number(o.amountUsd || 0), 0)),
      minutesSold: paid.reduce((s, o) => s + Number(o.minutes || 0), 0),
      developers: db.developers.length,
      projects: db.projects.length,
    };
  }

  res.json({
    brand: {
      name: 'LYVO Cloud',
      tagline: 'Sell realtime voice minutes. Ship apps faster.',
      company: 'LYVO Technologies',
    },
    developer: publicDev(req.developer),
    projects: projects.map((p) => publicProject(p)),
    freeMinutes: {
      total: m.freeTotal,
      used: m.freeUsed,
      remaining: m.freeRemaining,
      expiresAt: m.trialExpiresAt,
      product: 'Voice Call trial',
    },
    paidMinutes: {
      remaining: m.paidRemaining,
      totalPurchased: m.paidTotal,
    },
    totalMinutesRemaining: m.totalRemaining,
    balanceUsd: Number(req.developer.balanceUsd || 0),
    packages,
    orders: myOrders,
    sales,
    paymentProvider: fwClient().configured ? 'fourthwall' : (process.env.STRIPE_SECRET_KEY ? 'stripe' : 'simulated'),
    fourthwallConfigured: fwClient().configured,
    notices: db.notices || [],
    resources: {
      docs: `${PUBLIC_URL}/sdk`,
      apiDocs: `${PUBLIC_URL}/api/docs-lite`,
      livekitUrl: process.env.LIVEKIT_URL || 'wss://voice.adastra.bbs.tr',
      tokenUrl: `${PUBLIC_URL}/api/v1/token`,
      /** Always present so clients prioritize wss first */
      primaryTransport: 'wss',
    },
  });
});

app.get('/api/docs-lite', (_req, res) => {
  res.type('html').send(`<!doctype html><html><head><meta charset="utf-8"><title>LYVO API</title>
<style>body{font-family:system-ui;max-width:720px;margin:40px auto;padding:0 16px;line-height:1.5;color:#0f172a}
code{background:#f1f5f9;padding:2px 6px;border-radius:4px} pre{background:#0f172a;color:#e2e8f0;padding:14px;border-radius:10px;overflow:auto}
a{color:#2563eb}</style></head><body>
<h1>LYVO Cloud API</h1>
<p><a href="/">← Console</a></p>
<ul>
<li><code>POST /api/auth/register</code></li>
<li><code>POST /api/auth/login</code></li>
<li><code>GET /api/dashboard</code> Bearer</li>
<li><code>GET /api/packages</code> public catalog</li>
<li><code>POST /api/billing/buy-package</code> { packageId } — pay minutes pack</li>
<li><code>POST /api/billing/checkout</code> { amountUsd } — USD wallet (optional)</li>
<li><code>POST /api/seller/packages</code> seller only — create pack</li>
<li><code>POST /api/v1/token</code> appId+keys+room+identity</li>
</ul>
</body></html>`);
});

// ── Packages (catalog) ──
app.get('/api/packages', (req, res) => {
  const db = store.read();
  const all = req.query.all === '1';
  let list = db.packages || [];
  if (!all) list = list.filter((p) => p.active !== false);
  list = [...list].sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0));
  res.json({ packages: list.map(publicPackage) });
});

// Seller: manage packages you sell
app.get('/api/seller/packages', auth, requireSeller, (_req, res) => {
  const db = store.read();
  const list = [...(db.packages || [])].sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0));
  res.json({ packages: list.map(publicPackage) });
});

app.post('/api/seller/packages', auth, requireSeller, (req, res) => {
  const name = String(req.body?.name || '').trim();
  const minutes = Math.floor(Number(req.body?.minutes || 0));
  const priceUsd = round2(Number(req.body?.priceUsd || 0));
  if (!name || minutes < 100 || priceUsd <= 0) {
    return res.status(400).json({ error: 'name_minutes_price_required' });
  }
  const pkg = {
    id: 'pkg_' + nanoid(10),
    name,
    minutes,
    priceUsd,
    badge: String(req.body?.badge || '').slice(0, 40),
    description: String(req.body?.description || '').slice(0, 240),
    active: req.body?.active !== false,
    sort: Number(req.body?.sort ?? 50),
    createdAt: new Date().toISOString(),
  };
  store.update((db) => {
    db.packages.push(pkg);
    return db;
  });
  res.status(201).json({ package: publicPackage(pkg) });
});

app.patch('/api/seller/packages/:id', auth, requireSeller, (req, res) => {
  let updated = null;
  store.update((db) => {
    const row = db.packages.find((p) => p.id === req.params.id);
    if (!row) return db;
    if (req.body?.name) row.name = String(req.body.name).trim().slice(0, 80);
    if (req.body?.minutes != null) row.minutes = Math.max(100, Math.floor(Number(req.body.minutes)));
    if (req.body?.priceUsd != null) row.priceUsd = round2(Math.max(0.5, Number(req.body.priceUsd)));
    if (req.body?.badge !== undefined) row.badge = String(req.body.badge || '').slice(0, 40);
    if (req.body?.description !== undefined) row.description = String(req.body.description || '').slice(0, 240);
    if (req.body?.active !== undefined) row.active = Boolean(req.body.active);
    if (req.body?.sort !== undefined) row.sort = Number(req.body.sort);
    row.updatedAt = new Date().toISOString();
    updated = row;
    return db;
  });
  if (!updated) return res.status(404).json({ error: 'not_found' });
  res.json({ package: publicPackage(updated) });
});

app.delete('/api/seller/packages/:id', auth, requireSeller, (req, res) => {
  store.update((db) => {
    db.packages = (db.packages || []).filter((p) => p.id !== req.params.id);
    return db;
  });
  res.json({ ok: true });
});

app.get('/api/seller/sales', auth, requireSeller, (_req, res) => {
  const db = store.read();
  const paid = (db.orders || []).filter((o) => o.status === 'paid');
  res.json({
    orders: paid
      .sort((a, b) => String(b.paidAt || b.createdAt).localeCompare(String(a.paidAt || a.createdAt)))
      .slice(0, 100)
      .map(publicOrder),
    summary: {
      ordersPaid: paid.length,
      revenueUsd: round2(paid.reduce((s, o) => s + Number(o.amountUsd || 0), 0)),
      minutesSold: paid.reduce((s, o) => s + Number(o.minutes || 0), 0),
    },
  });
});

// Buy a minutes package — primarily Fourthwall (card), same as JEHO app
app.post('/api/billing/buy-package', auth, async (req, res) => {
  const packageId = String(req.body?.packageId || '');
  const db = store.read();
  const pkg = (db.packages || []).find((p) => p.id === packageId && p.active !== false);
  if (!pkg) return res.status(404).json({ error: 'package_not_found' });

  const claimCode = ('LYVO-' + nanoid(8)).toUpperCase().replace(/[^A-Z0-9\-]/g, '');
  const order = {
    id: nanoid(),
    type: 'package',
    developerId: req.developer.id,
    packageId: pkg.id,
    sku: pkg.id,
    packageName: pkg.name,
    minutes: Number(pkg.minutes),
    amountUsd: round2(pkg.priceUsd),
    status: 'pending',
    provider: 'fourthwall',
    claimCode,
    createdAt: new Date().toISOString(),
  };
  store.update((d) => {
    // Cancel older pending package checkouts for same user
    for (const o of d.orders) {
      if (
        o.developerId === req.developer.id &&
        o.status === 'pending' &&
        o.type === 'package'
      ) {
        o.status = 'cancelled';
      }
    }
    d.orders.push(order);
    return d;
  });

  const client = fwClient();
  if (client.configured) {
    try {
      const product = await client.ensurePackageProduct({
        sku: pkg.id,
        priceUsd: Number(pkg.priceUsd),
        packageName: pkg.name,
      });
      const currency = 'USD';
      const cart = await client.createCartWithVariant(product.variantId, currency);
      const checkoutUrl = client.cartHasItems(cart)
        ? client.checkoutUrl(cart.id, currency)
        : client.checkoutUrlForVariant(product.variantId, currency);
      store.update((d) => {
        const o = d.orders.find((x) => x.id === order.id);
        if (o) {
          o.provider = 'fourthwall';
          o.providerOrderId = cart.id;
          o.cartId = cart.id;
          o.variantId = product.variantId;
          o.productId = product.productId;
          o.providerPayload = {
            cartId: cart.id,
            variantId: product.variantId,
            productId: product.productId,
            checkoutUrl,
            claimCode,
          };
        }
        // cache on package
        const row = d.packages.find((p) => p.id === pkg.id);
        if (row) {
          row.fourthwallProductId = product.productId;
          row.fourthwallVariantId = product.variantId;
        }
        return d;
      });
      return res.json({
        mode: 'fourthwall',
        provider: 'fourthwall',
        checkoutUrl,
        cartId: cart.id,
        orderId: order.id,
        claimCode,
        message: 'Pay with card on Fourthwall checkout (same as JEHO app).',
      });
    } catch (e) {
      store.update((d) => {
        const o = d.orders.find((x) => x.id === order.id);
        if (o) {
          o.status = 'failed';
          o.providerPayload = { error: String(e.message || e) };
        }
        return d;
      });
      return res.status(502).json({
        error: 'fourthwall_checkout_failed',
        message: String(e.message || e),
      });
    }
  }

  // Fallback Stripe (optional)
  const stripeKey = process.env.STRIPE_SECRET_KEY;
  if (stripeKey) {
    const Stripe = (await import('stripe')).default;
    const stripe = new Stripe(stripeKey);
    const session = await stripe.checkout.sessions.create({
      mode: 'payment',
      success_url: `${PUBLIC_URL}/packages?billing=success`,
      cancel_url: `${PUBLIC_URL}/packages?billing=cancel`,
      line_items: [
        {
          quantity: 1,
          price_data: {
            currency: 'usd',
            unit_amount: Math.round(Number(pkg.priceUsd) * 100),
            product_data: {
              name: `LYVO ${pkg.name} — ${pkg.minutes.toLocaleString()} min`,
              description: pkg.description || 'Voice minutes package',
            },
          },
        },
      ],
      metadata: {
        lyvoType: 'package',
        lyvoOrderId: order.id,
        lyvoPackageId: pkg.id,
        lyvoDeveloperId: req.developer.id,
        minutes: String(pkg.minutes),
      },
    });
    store.update((d) => {
      const o = d.orders.find((x) => x.id === order.id);
      if (o) {
        o.provider = 'stripe';
        o.providerOrderId = session.id;
        o.providerPayload = { url: session.url };
      }
      return d;
    });
    return res.json({
      mode: 'stripe',
      checkoutUrl: session.url,
      sessionId: session.id,
      orderId: order.id,
    });
  }

  // Dev simulation only when neither Fourthwall nor Stripe
  creditPackage(req.developer.id, pkg, order.id);
  const fresh = store.read().developers.find((d) => d.id === req.developer.id);
  return res.json({
    mode: 'simulated',
    message: `تم شراء ${pkg.minutes.toLocaleString()} دقيقة (تجريبي — أضف Fourthwall في Seller).`,
    order: publicOrder(store.read().orders.find((o) => o.id === order.id)),
    developer: publicDev(fresh),
  });
});

// Order status (after returning from Fourthwall card page)
app.get('/api/billing/orders/:id', auth, (req, res) => {
  const db = store.read();
  const o = db.orders.find(
    (x) => x.id === req.params.id && x.developerId === req.developer.id,
  );
  if (!o) return res.status(404).json({ error: 'not_found' });
  res.json({ order: publicOrder(o) });
});

// Seller: Fourthwall credentials (card via fourthwall.com)
app.get('/api/seller/payments', auth, requireSeller, (_req, res) => {
  const s = fourthwallSettings();
  const envShop = process.env.FOURTHWALL_SHOP_DOMAIN || '';
  res.json({
    provider: 'fourthwall',
    webhookUrl: `${PUBLIC_URL}/api/billing/fourthwall/webhook`,
    configured: fwClient().configured,
    fourthwall: {
      apiUser: s.apiUser || process.env.FOURTHWALL_API_USER || '',
      apiUserConfigured: !!(s.apiUser || process.env.FOURTHWALL_API_USER),
      apiPasswordConfigured: !!(s.apiPassword || process.env.FOURTHWALL_API_PASSWORD),
      storefrontTokenConfigured: !!(s.storefrontToken || process.env.FOURTHWALL_STOREFRONT_TOKEN),
      webhookSecretConfigured: !!(s.webhookSecret || process.env.FOURTHWALL_WEBHOOK_SECRET),
      shopDomain: s.shopDomain || envShop || '',
      apiPasswordHint: maskSecret(s.apiPassword || process.env.FOURTHWALL_API_PASSWORD),
      storefrontTokenHint: maskSecret(s.storefrontToken || process.env.FOURTHWALL_STOREFRONT_TOKEN),
    },
  });
});

app.patch('/api/seller/payments', auth, requireSeller, (req, res) => {
  store.update((db) => {
    if (!db.settings) db.settings = {};
    if (!db.settings.fourthwall) db.settings.fourthwall = {};
    const f = db.settings.fourthwall;
    const b = req.body || {};
    if (b.apiUser !== undefined) f.apiUser = String(b.apiUser || '').trim();
    if (b.apiPassword !== undefined && String(b.apiPassword).trim()) {
      f.apiPassword = String(b.apiPassword).trim();
    }
    if (b.clearApiPassword) f.apiPassword = '';
    if (b.storefrontToken !== undefined && String(b.storefrontToken).trim()) {
      f.storefrontToken = String(b.storefrontToken).trim();
    }
    if (b.clearStorefrontToken) f.storefrontToken = '';
    if (b.shopDomain !== undefined) {
      f.shopDomain = String(b.shopDomain || '')
        .replace(/^https?:\/\//i, '')
        .replace(/\/+$/, '')
        .trim();
    }
    if (b.webhookSecret !== undefined && String(b.webhookSecret).trim()) {
      f.webhookSecret = String(b.webhookSecret).trim();
    }
    if (b.clearWebhookSecret) f.webhookSecret = '';
    return db;
  });
  res.json({ ok: true, configured: fwClient().configured });
});

app.post('/api/seller/payments/test', auth, requireSeller, async (_req, res) => {
  const client = fwClient();
  if (!client.configured) {
    return res.status(400).json({ ok: false, error: 'not_configured' });
  }
  const result = await client.testConnection();
  res.status(result.ok ? 200 : 400).json(result);
});

// Optional USD wallet top-up (Stripe only if needed)
app.post('/api/billing/checkout', auth, async (req, res) => {
  const amountUsd = Number(req.body?.amountUsd || 0);
  if (!Number.isFinite(amountUsd) || amountUsd < 5 || amountUsd > 5000) {
    return res.status(400).json({ error: 'amount_must_be_5_to_5000' });
  }
  const key = process.env.STRIPE_SECRET_KEY;
  const order = {
    id: nanoid(),
    developerId: req.developer.id,
    amountUsd: round2(amountUsd),
    status: 'pending',
    provider: 'stripe',
    createdAt: new Date().toISOString(),
  };
  store.update((db) => {
    db.topups.push(order);
    return db;
  });

  if (!key) {
    store.update((db) => {
      const o = db.topups.find((t) => t.id === order.id);
      if (o) o.status = 'paid';
      const dev = db.developers.find((d) => d.id === req.developer.id);
      if (dev) dev.balanceUsd = round2(Number(dev.balanceUsd || 0) + amountUsd);
      return db;
    });
    return res.json({
      mode: 'simulated',
      message: 'Stripe not configured — wallet credited for testing.',
      balanceUsd: publicDev(store.read().developers.find((d) => d.id === req.developer.id)).balanceUsd,
      orderId: order.id,
    });
  }

  const Stripe = (await import('stripe')).default;
  const stripe = new Stripe(key);
  const session = await stripe.checkout.sessions.create({
    mode: 'payment',
    success_url: `${PUBLIC_URL}/billing?billing=success`,
    cancel_url: `${PUBLIC_URL}/billing?billing=cancel`,
    line_items: [
      {
        quantity: 1,
        price_data: {
          currency: 'usd',
          unit_amount: Math.round(amountUsd * 100),
          product_data: {
            name: 'LYVO Cloud balance top-up',
            description: `Add $${amountUsd.toFixed(2)} wallet balance`,
          },
        },
      },
    ],
    metadata: {
      lyvoType: 'topup',
      lyvoTopupId: order.id,
      lyvoDeveloperId: req.developer.id,
    },
  });
  store.update((db) => {
    const o = db.topups.find((t) => t.id === order.id);
    if (o) {
      o.providerOrderId = session.id;
      o.providerPayload = { url: session.url };
    }
    return db;
  });
  res.json({ mode: 'stripe', checkoutUrl: session.url, sessionId: session.id, orderId: order.id });
});

// ── Projects ──
app.get('/api/projects', auth, (req, res) => {
  const db = store.read();
  const projects = db.projects.filter((p) => p.ownerId === req.developer.id);
  res.json({ projects: projects.map((p) => publicProject(p)) });
});

app.post('/api/projects', auth, (req, res) => {
  const name = String(req.body?.name || '').trim();
  if (!name || name.length < 2) return res.status(400).json({ error: 'name_required' });
  const description = String(req.body?.description || '').trim();
  const code = ('LV' + nanoid(8)).toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 12);
  const appId = String(1_200_000_000 + Math.floor(Math.random() * 700_000_000));
  const apiKey = 'LVK' + crypto.randomBytes(12).toString('hex');
  const apiSecret = 'LVS' + crypto.randomBytes(24).toString('hex');
  const project = {
    id: nanoid(),
    ownerId: req.developer.id,
    name,
    description,
    projectCode: code,
    appId,
    apiKey,
    apiSecret,
    status: 'trial',
    roomPrefix: `lyvo_${code.toLowerCase()}`,
    voiceMinutesUsed: 0,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  };
  store.update((db) => {
    db.projects.push(project);
    return db;
  });
  res.status(201).json({ project: publicProject(project, { revealSecret: true }), secretOnce: true });
});

app.get('/api/projects/:id', auth, (req, res) => {
  const db = store.read();
  const p = db.projects.find((x) => x.id === req.params.id && x.ownerId === req.developer.id);
  if (!p) return res.status(404).json({ error: 'not_found' });
  res.json({ project: publicProject(p) });
});

app.get('/api/projects/:id/reveal-secret', auth, (req, res) => {
  const db = store.read();
  const p = db.projects.find((x) => x.id === req.params.id && x.ownerId === req.developer.id);
  if (!p) return res.status(404).json({ error: 'not_found' });
  res.json({ apiSecret: p.apiSecret });
});

app.patch('/api/projects/:id', auth, (req, res) => {
  const db = store.read();
  const p = db.projects.find((x) => x.id === req.params.id && x.ownerId === req.developer.id);
  if (!p) return res.status(404).json({ error: 'not_found' });
  store.update((d) => {
    const row = d.projects.find((x) => x.id === p.id);
    if (req.body?.name) row.name = String(req.body.name).trim().slice(0, 80);
    if (req.body?.description !== undefined) row.description = String(req.body.description || '');
    if (['active', 'suspended', 'trial'].includes(req.body?.status)) row.status = req.body.status;
    row.updatedAt = new Date().toISOString();
    return d;
  });
  const next = store.read().projects.find((x) => x.id === p.id);
  res.json({ project: publicProject(next) });
});

app.delete('/api/projects/:id', auth, (req, res) => {
  store.update((db) => {
    db.projects = db.projects.filter(
      (x) => !(x.id === req.params.id && x.ownerId === req.developer.id),
    );
    return db;
  });
  res.json({ ok: true });
});

// Token for client apps
app.post('/api/v1/token', async (req, res) => {
  const { appId, apiKey, apiSecret, room, identity } = req.body || {};
  const db = store.read();
  const project = db.projects.find(
    (p) =>
      String(p.appId) === String(appId) &&
      p.apiKey === apiKey &&
      p.apiSecret === apiSecret,
  );
  if (!project) return res.status(401).json({ error: 'invalid_credentials' });
  if (project.status === 'suspended') return res.status(403).json({ error: 'project_suspended' });

  const owner = db.developers.find((d) => d.id === project.ownerId);
  if (owner) {
    const m = minutesSnapshot(owner);
    if (m.totalRemaining <= 0) {
      return res.status(402).json({ error: 'no_minutes_left', message: 'Buy a minutes package' });
    }
  }

  const roomName = `${project.roomPrefix}_${String(room || 'lobby').replace(/[^a-zA-Z0-9_-]/g, '_')}`;
  const userId = String(identity || 'user').slice(0, 64);
  const lkKey = process.env.LIVEKIT_API_KEY;
  const lkSecret = process.env.LIVEKIT_API_SECRET;
  const lkUrl = process.env.LIVEKIT_URL || 'wss://voice.adastra.bbs.tr';

  if (!lkKey || !lkSecret) {
    return res.json({
      provider: 'lyvo',
      appId: project.appId,
      room: roomName,
      identity: userId,
      livekitUrl: lkUrl,
      token: null,
      message: 'Set LIVEKIT_API_KEY/SECRET on LYVO for real tokens.',
    });
  }

  const now = Math.floor(Date.now() / 1000);
  const header = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64url');
  const payload = Buffer.from(
    JSON.stringify({
      iss: lkKey,
      sub: userId,
      nbf: now,
      exp: now + 3600,
      video: {
        roomJoin: true,
        room: roomName,
        canPublish: true,
        canSubscribe: true,
      },
    }),
  ).toString('base64url');
  const data = `${header}.${payload}`;
  const sig = crypto.createHmac('sha256', lkSecret).update(data).digest('base64url');

  res.json({
    provider: 'livekit',
    appId: project.appId,
    room: roomName,
    identity: userId,
    livekitUrl: lkUrl,
    token: `${data}.${sig}`,
  });
});

const webDist = path.join(__dirname, '..', 'web', 'dist');
app.use(express.static(webDist));
app.get('*', (req, res, next) => {
  if (req.path.startsWith('/api')) return next();
  res.sendFile(path.join(webDist, 'index.html'), (err) => {
    if (err) res.status(404).type('text').send('LYVO Cloud UI not built. Run: npm run build');
  });
});

app.listen(PORT, process.env.HOST || '0.0.0.0', () => {
  console.log(`LYVO Cloud listening on ${PORT}  public=${PUBLIC_URL}`);
});
