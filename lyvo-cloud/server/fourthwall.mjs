/**
 * Minimal Fourthwall Open API + Storefront client for LYVO Cloud.
 * Ported (simplified) from JEHO's working Fourthwall integration — no Nest deps.
 *
 * Storefront listings use digital eBook-style titles (Fourthwall policy);
 * minutes mapping lives only in LYVO_SKU lines in the description.
 */

export class FourthwallClient {
  constructor({ apiUser, apiPassword, storefrontToken, shopDomain }) {
    this.apiUser = String(apiUser || '').trim();
    this.apiPassword = String(apiPassword || '').trim();
    this.storefrontToken = String(storefrontToken || '').trim();
    this.shopDomain = String(shopDomain || '')
      .replace(/^https?:\/\//i, '')
      .replace(/\/+$/, '')
      .trim();
  }

  get configured() {
    return !!(this.apiUser && this.apiPassword && this.storefrontToken && this.shopDomain);
  }

  shopHost() {
    return this.shopDomain;
  }

  checkoutUrlForVariant(variantId, currency = 'USD') {
    return `https://${this.shopHost()}/cart/checkout?products=${encodeURIComponent(variantId)}:1&currency=${encodeURIComponent(currency)}`;
  }

  checkoutUrl(cartId, currency = 'USD') {
    return `https://${this.shopHost()}/cart/checkout?cartId=${encodeURIComponent(cartId)}&currency=${encodeURIComponent(currency)}`;
  }

  cartHasItems(cart) {
    if (!cart) return false;
    const items =
      (Array.isArray(cart.items) && cart.items) ||
      (Array.isArray(cart.offers) && cart.offers) ||
      (Array.isArray(cart.lineItems) && cart.lineItems) ||
      [];
    return items.length > 0;
  }

  async openFetch(url, opts = {}) {
    const auth = Buffer.from(`${this.apiUser}:${this.apiPassword}`).toString('base64');
    const res = await fetch(url, {
      method: opts.method || 'GET',
      headers: {
        Authorization: `Basic ${auth}`,
        Accept: 'application/json',
        ...(opts.body ? { 'Content-Type': 'application/json' } : {}),
        ...(opts.headers || {}),
      },
      body: opts.body,
    });
    const text = await res.text();
    let data = null;
    try {
      data = text ? JSON.parse(text) : null;
    } catch {
      data = { raw: text };
    }
    if (!res.ok) {
      const msg = data?.message || data?.error || text?.slice(0, 200) || res.statusText;
      throw new Error(`Fourthwall ${res.status}: ${msg}`);
    }
    return data;
  }

  async sfFetch(url, opts = {}) {
    const res = await fetch(url, {
      method: opts.method || 'GET',
      headers: {
        Accept: 'application/json',
        ...(opts.body ? { 'Content-Type': 'application/json' } : {}),
      },
      body: opts.body,
    });
    const text = await res.text();
    let data = null;
    try {
      data = text ? JSON.parse(text) : null;
    } catch {
      data = { raw: text };
    }
    if (!res.ok) {
      throw new Error(`Fourthwall SF ${res.status}: ${text?.slice(0, 200)}`);
    }
    return data;
  }

  moneyBody(priceUsd) {
    const value = Math.round(Number(priceUsd) * 100) / 100;
    return { value, currency: 'USD' };
  }

  moneyEquals(a, b) {
    return Math.abs(Number(a || 0) - Number(b || 0)) < 0.05;
  }

  /** Neutral product meta — no "coins/minutes" wording on storefront. */
  catalogMeta(sku, priceUsd, packageName) {
    const series = [
      'Harbor Quiet Fables',
      'Starlight Pocket Reader',
      'Cedar Grove Anthology',
      'Moonlit Trail Stories',
      'Silver Quill Collection',
      'Amber Window Tales',
    ];
    let hash = 0;
    const s = String(sku);
    for (let i = 0; i < s.length; i++) hash = (hash * 31 + s.charCodeAt(i)) >>> 0;
    const titleSeries = series[hash % series.length];
    const edition = Math.max(1, Math.round(Number(priceUsd) * 100) + (hash % 17));
    const name = `${titleSeries} — Digital eBook · Edition ${edition}`;
    const description = [
      `LYVO_SKU:${sku}`,
      `LYVO_PRICE_USD:${Number(priceUsd)}`,
      `LYVO_NAME:${packageName || sku}`,
      `${titleSeries} digital guide / short reader. Edition ${edition}.`,
      'Instant digital delivery. Non-refundable once fulfilled.',
    ].join('\n');
    return { name, description };
  }

  extractVariantId(product) {
    if (!product) return '';
    const variants = product.variants || product.offer?.variants || [];
    if (Array.isArray(variants) && variants[0]?.id) return String(variants[0].id);
    if (product.variantId) return String(product.variantId);
    return '';
  }

  extractPriceUsd(product) {
    if (!product) return 0;
    const candidates = [
      product?.variants?.[0]?.unitPrice?.value,
      product?.variants?.[0]?.unitPrice?.amount,
      product?.variants?.[0]?.price?.value,
      product?.variants?.[0]?.price,
      product?.price?.value,
      product?.price?.amount,
      product?.price,
      product?.unitPrice?.value,
    ];
    for (const c of candidates) {
      const n = Number(c);
      if (Number.isFinite(n) && n > 0) return Math.round(n * 100) / 100;
    }
    return 0;
  }

  async listProducts(size = 100) {
    const data = await this.openFetch(
      `https://api.fourthwall.com/open-api/v1.0/products?size=${size}`,
    );
    if (Array.isArray(data?.results)) return data.results;
    if (Array.isArray(data)) return data;
    return [];
  }

  async getProduct(productId) {
    return this.openFetch(
      `https://api.fourthwall.com/open-api/v1.0/products/${encodeURIComponent(productId)}`,
    );
  }

  async findByLyvoSku(sku) {
    const marker = `lyvo_sku:${String(sku).toLowerCase()}`;
    const products = await this.listProducts(100);
    for (const p of products) {
      const desc = String(p?.description || '').toLowerCase();
      if (!desc.includes(marker)) continue;
      const id = String(p?.id || p?.productId || '');
      const variantId = this.extractVariantId(p);
      if (!id || !variantId) continue;
      return {
        productId: id,
        variantId,
        name: String(p?.name || ''),
        priceUsd: this.extractPriceUsd(p),
      };
    }
    return null;
  }

  async makeProductPurchasable(productId) {
    const id = encodeURIComponent(productId);
    const attempts = [
      {
        url: `https://api.fourthwall.com/open-api/v1.0/products/${id}/availability`,
        method: 'PUT',
        body: JSON.stringify({ status: 'AVAILABLE' }),
      },
      {
        url: `https://api.fourthwall.com/open-api/v1.0/products/${id}/state`,
        method: 'PUT',
        body: JSON.stringify({ status: 'PUBLIC' }),
      },
      {
        url: `https://api.fourthwall.com/open-api/v1.0/products/${id}/state`,
        method: 'PUT',
        body: JSON.stringify({ state: 'PUBLIC' }),
      },
    ];
    for (const a of attempts) {
      try {
        await this.openFetch(a.url, { method: a.method, body: a.body });
      } catch {
        /* best-effort */
      }
    }
  }

  async createDigitalProduct({ name, description, priceUsd }) {
    const money = this.moneyBody(priceUsd);
    const payloads = [
      { type: 'digital', name, description, price: money, publishOnCreate: false },
      {
        type: 'digital',
        name,
        description,
        price: money.value,
        unitPrice: money,
        publishOnCreate: false,
      },
    ];
    let lastErr = null;
    for (const body of payloads) {
      try {
        const created = await this.openFetch(
          'https://api.fourthwall.com/open-api/v1.0/products',
          { method: 'POST', body: JSON.stringify(body) },
        );
        const productId = String(
          created?.productId || created?.id || created?.product?.id || '',
        );
        if (productId) return { productId };
      } catch (e) {
        lastErr = e;
      }
    }
    throw lastErr || new Error('Fourthwall product create failed');
  }

  async ensurePackageProduct({ sku, priceUsd, packageName }) {
    const want = Math.round(Number(priceUsd) * 100) / 100;
    const meta = this.catalogMeta(sku, want, packageName);
    const existing = await this.findByLyvoSku(sku);
    if (existing?.variantId && existing.productId) {
      await this.makeProductPurchasable(existing.productId);
      const live = await this.getProduct(existing.productId).catch(() => null);
      const price = live ? this.extractPriceUsd(live) : existing.priceUsd;
      const variantId = live ? this.extractVariantId(live) || existing.variantId : existing.variantId;
      if (price > 0 && this.moneyEquals(price, want)) {
        return {
          productId: existing.productId,
          variantId,
          name: meta.name,
          priceUsd: want,
        };
      }
    }

    const { productId } = await this.createDigitalProduct({
      name: meta.name,
      description: meta.description,
      priceUsd: want,
    });
    await this.makeProductPurchasable(productId);
    let details = await this.getProduct(productId);
    let variantId = this.extractVariantId(details);
    if (!variantId) {
      await new Promise((r) => setTimeout(r, 1200));
      details = await this.getProduct(productId);
      variantId = this.extractVariantId(details);
    }
    if (!variantId) throw new Error(`Fourthwall product ${productId} has no variant`);
    const got = this.extractPriceUsd(details);
    if (got > 0 && !this.moneyEquals(got, want)) {
      throw new Error(`Fourthwall price mismatch: live=$${got} package=$${want}`);
    }
    return { productId, variantId, name: meta.name, priceUsd: want };
  }

  async createCartWithVariant(variantId, currency = 'USD') {
    const token = encodeURIComponent(this.storefrontToken);
    const createUrl = `https://storefront-api.fourthwall.com/v1/carts?storefront_token=${token}&currency=${encodeURIComponent(currency)}`;
    const created = await this.sfFetch(createUrl, {
      method: 'POST',
      body: JSON.stringify({ currency, items: [{ variantId, quantity: 1 }] }),
    });
    if (created?.id && this.cartHasItems(created)) return created;
    if (created?.id) {
      const addUrl = `https://storefront-api.fourthwall.com/v1/carts/${encodeURIComponent(String(created.id))}/add?storefront_token=${token}&currency=${encodeURIComponent(currency)}`;
      const added = await this.sfFetch(addUrl, {
        method: 'POST',
        body: JSON.stringify({ variantId, quantity: 1 }),
      });
      return added?.id ? added : created;
    }
    throw new Error('Fourthwall cart create failed');
  }

  async testConnection() {
    try {
      const shop = await this.openFetch('https://api.fourthwall.com/open-api/v1.0/shops/current');
      return { ok: true, shop: shop?.name || shop?.domain || 'ok' };
    } catch (e) {
      return { ok: false, error: String(e.message || e) };
    }
  }
}

export function loadFourthwallFromEnvAndSettings(settings = {}) {
  return new FourthwallClient({
    apiUser: settings.apiUser || process.env.FOURTHWALL_API_USER,
    apiPassword: settings.apiPassword || process.env.FOURTHWALL_API_PASSWORD,
    storefrontToken: settings.storefrontToken || process.env.FOURTHWALL_STOREFRONT_TOKEN,
    shopDomain: settings.shopDomain || process.env.FOURTHWALL_SHOP_DOMAIN,
  });
}
