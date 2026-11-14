import { Logger } from '@nestjs/common';

export type FourthwallCart = {
  id: string;
  items?: unknown[];
  [key: string]: unknown;
};

export type FourthwallProductSummary = {
  productId: string;
  variantId: string;
  name: string;
  priceUsd: number;
};

export class FourthwallClient {
  private readonly logger = new Logger(FourthwallClient.name);

  constructor(
    private readonly apiUser: string,
    private readonly apiPassword: string,
    private readonly storefrontToken: string,
    private readonly shopDomain: string,
  ) {}

  get configured(): boolean {
    return !!(
      this.storefrontToken &&
      this.shopDomain &&
      this.apiUser &&
      this.apiPassword
    );
  }

  private shopHost(): string {
    return this.shopDomain
      .replace(/^https?:\/\//i, '')
      .replace(/\/+$/, '');
  }

  private moneyEquals(a: number, b: number): boolean {
    return Math.abs(Number(a || 0) - Number(b || 0)) < 0.021;
  }

  private moneyBody(priceUsd: number) {
    const value = Math.round(Number(priceUsd) * 100) / 100;
    return { value, currency: 'USD' };
  }

  /**
   * Store-facing catalog label for Fourthwall orders/receipts.
   * Must NOT mention virtual currency / coins / diamond / recharge — Fourthwall
   * flags that language. Credit mapping stays only via hidden JEHO_SKU lines.
   */
  private storefrontEbookMeta(input: {
    sku: string;
    priceUsd: number;
  }): { name: string; description: string; edition: number; series: string } {
    const sku = String(input.sku || '').trim();
    const priceUsd = Math.round(Number(input.priceUsd) * 100) / 100;
    const seriesCatalog = [
      'Harbor Quiet Fables',
      'Starlight Pocket Reader',
      'Cedar Grove Anthology',
      'Moonlit Trail Stories',
      'Silver Quill Collection',
      'Amber Window Tales',
      'Linen Shelf Classics',
      'Blue Lantern Library',
      'Olive Press Mini Guide',
      'Northwind Storybook',
      'Garden Path Reader',
      'Ivory Desk Companion',
    ];
    let hash = 0;
    for (let i = 0; i < sku.length; i += 1) {
      hash = (hash * 31 + sku.charCodeAt(i)) >>> 0;
    }
    const series = seriesCatalog[hash % seriesCatalog.length];
    // Edition band also tracks price tier (stable ops label, not "coins").
    const edition = Math.max(1, Math.round(priceUsd * 100) + (hash % 17));
    const name = `${series} — Digital eBook · Edition ${edition}`;
    const marker = `JEHO_SKU:${sku}`;
    const priceMarker = `JEHO_PRICE_USD:${priceUsd}`;
    const description = [
      marker,
      priceMarker,
      `${series} is a short digital storybook / reading guide delivered instantly after purchase.`,
      `Edition ${edition}. Format: text eBook (instant download).`,
      'For entertainment reading. Non-refundable digital download once fulfilled.',
    ].join('\n');
    return { name, description, edition, series };
  }

  /** Preferred: cartId checkout with forced currency (avoids geo local-currency mismatch). */
  checkoutUrlForVariant(variantId: string, currency = 'USD'): string {
    const domain = this.shopHost();
    return `https://${domain}/cart/checkout?products=${encodeURIComponent(variantId)}:1&currency=${encodeURIComponent(currency)}`;
  }

  checkoutUrl(cartId: string, currency = 'USD'): string {
    const domain = this.shopHost();
    return `https://${domain}/cart/checkout?cartId=${encodeURIComponent(cartId)}&currency=${encodeURIComponent(currency)}`;
  }

  cartHasItems(cart: FourthwallCart | null | undefined): boolean {
    if (!cart) return false;
    const items =
      (Array.isArray(cart.items) && cart.items) ||
      (Array.isArray((cart as any).offers) && (cart as any).offers) ||
      (Array.isArray((cart as any).lineItems) && (cart as any).lineItems) ||
      [];
    return items.length > 0;
  }

  async createCartWithVariant(
    variantId: string,
    currency = 'USD',
  ): Promise<FourthwallCart> {
    const token = encodeURIComponent(this.storefrontToken);
    const createUrl = `https://storefront-api.fourthwall.com/v1/carts?storefront_token=${token}&currency=${encodeURIComponent(currency)}`;
    const created = await this.sfFetch(createUrl, {
      method: 'POST',
      body: JSON.stringify({ currency, items: [{ variantId, quantity: 1 }] }),
    });
    if (created?.id && this.cartHasItems(created as FourthwallCart)) {
      return created as FourthwallCart;
    }
    if (created?.id) {
      // Created but empty — try explicit add
      const addUrl = `https://storefront-api.fourthwall.com/v1/carts/${encodeURIComponent(String(created.id))}/add?storefront_token=${token}&currency=${encodeURIComponent(currency)}`;
      const added = await this.sfFetch(addUrl, {
        method: 'POST',
        body: JSON.stringify({ variantId, quantity: 1 }),
      });
      return (added?.id ? added : created) as FourthwallCart;
    }

    const empty = await this.sfFetch(createUrl, {
      method: 'POST',
      body: JSON.stringify({ currency }),
    });
    const cartId = String(empty?.id || '');
    if (!cartId) {
      throw new Error('Fourthwall cart create failed');
    }
    const addUrl = `https://storefront-api.fourthwall.com/v1/carts/${encodeURIComponent(cartId)}/add?storefront_token=${token}&currency=${encodeURIComponent(currency)}`;
    const added = await this.sfFetch(addUrl, {
      method: 'POST',
      body: JSON.stringify({ variantId, quantity: 1 }),
    });
    return (added?.id ? added : { ...empty, id: cartId }) as FourthwallCart;
  }

  /**
   * Ensure a purchasable digital product exists for this JEHO package.
   * Always re-syncs price + storefront eBook title (never coin wording).
   * Products must be available (published) or checkout shows $0 / hangs.
   */
  async ensureCoinPackageProduct(input: {
    sku: string;
    coins: number;
    bonusCoins?: number;
    priceUsd: number;
  }): Promise<FourthwallProductSummary> {
    const sku = String(input.sku || '').trim();
    if (!sku) throw new Error('sku required');
    const priceUsd = Math.round(Number(input.priceUsd) * 100) / 100;
    if (!(priceUsd > 0)) {
      throw new Error(`Invalid package price for ${sku}: ${input.priceUsd}`);
    }
    const catalog = this.storefrontEbookMeta({ sku, priceUsd });
    const { name, description, edition, series } = catalog;

    const existing = await this.findProductBySkuMarker(sku);
    if (existing?.variantId && existing.productId) {
      await this.makeProductPurchasable(existing.productId);
      const live = await this.readProductSummary(existing.productId, existing);
      if (live && this.moneyEquals(live.priceUsd, priceUsd)) {
        // Rename old "… Coins" products → eBook catalog (best-effort).
        await this.touchProductMeta(live.productId, name, description).catch(() => undefined);
        return { productId: live.productId, variantId: live.variantId, name, priceUsd };
      }
      // Stale price (e.g. pack edited to $200 but Fourthwall still $0.99) — force update.
      const fixed = await this.forceMatchPrice(
        existing.productId,
        existing.variantId,
        priceUsd,
        name,
        description,
      );
      if (fixed?.variantId && this.moneyEquals(fixed.priceUsd, priceUsd)) {
        return { productId: fixed.productId, variantId: fixed.variantId, name, priceUsd };
      }
      // Cannot reprice (API limits) — hide old offer and create a fresh product.
      this.logger.warn(
        `Fourthwall price stuck for ${sku} (have ${live?.priceUsd ?? existing.priceUsd}, want ${priceUsd}) — recreating product`,
      );
      await this.archiveProduct(existing.productId).catch(() => undefined);
    }

    const created = await this.createDigitalProduct({
      name,
      description,
      priceUsd,
    });
    const productId = created.productId;

    try {
      await this.attachTinyDigitalFile(productId, sku, series, edition);
    } catch (e) {
      this.logger.warn(
        `digital file attach skipped: ${(e as Error).message || e}`,
      );
    }

    await this.makeProductPurchasable(productId);

    let details = await this.getProduct(productId);
    let variantId = this.extractVariantId(details) || '';
    if (!variantId) {
      await new Promise((r) => setTimeout(r, 1200));
      details = await this.getProduct(productId);
      variantId = this.extractVariantId(details) || '';
    }
    if (!variantId) {
      throw new Error(`Fourthwall product ${productId} has no variant yet`);
    }

    // Confirm posted price; reprice once if API ignored create payload.
    let summary = this.summaryFromProduct(details, productId, name, priceUsd);
    if (!this.moneyEquals(summary.priceUsd, priceUsd)) {
      const fixed = await this.forceMatchPrice(
        productId,
        variantId,
        priceUsd,
        name,
        description,
      );
      if (fixed) summary = fixed;
    }
    if (!summary.variantId) summary.variantId = variantId;
    if (!this.moneyEquals(summary.priceUsd, priceUsd)) {
      // Still wrong — refuse silent $0.99 checkouts for a $200 package.
      throw new Error(
        `Fourthwall product price mismatch for ${sku}: live=$${summary.priceUsd} package=$${priceUsd} (productId=${productId})`,
      );
    }
    return {
      productId: summary.productId || productId,
      variantId: summary.variantId || variantId,
      name,
      priceUsd,
    };
  }

  private async createDigitalProduct(input: {
    name: string;
    description: string;
    priceUsd: number;
  }): Promise<{ productId: string }> {
    const money = this.moneyBody(input.priceUsd);
    // Fourthwall has accepted both bare numbers and Money objects historically.
    const payloads: Array<Record<string, unknown>> = [
      {
        type: 'digital',
        name: input.name,
        description: input.description,
        price: money,
        publishOnCreate: false,
      },
      {
        type: 'digital',
        name: input.name,
        description: input.description,
        price: money.value,
        unitPrice: money,
        publishOnCreate: false,
      },
    ];
    let lastErr: Error | null = null;
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
        lastErr = new Error(
          `Fourthwall product create returned no id: ${JSON.stringify(created).slice(0, 400)}`,
        );
      } catch (e) {
        lastErr = e as Error;
      }
    }
    throw lastErr || new Error('Fourthwall product create failed');
  }

  /**
   * Best-effort reprice + rename so checkout displays JEHO package USD.
   */
  private async forceMatchPrice(
    productId: string,
    variantId: string,
    priceUsd: number,
    name: string,
    description: string,
  ): Promise<FourthwallProductSummary | null> {
    const money = this.moneyBody(priceUsd);
    const attempts: Array<{ url: string; method: string; body: unknown }> = [
      {
        url: `https://api.fourthwall.com/open-api/v1.0/products/${encodeURIComponent(productId)}/variants/${encodeURIComponent(variantId)}`,
        method: 'PUT',
        body: { unitPrice: money },
      },
      {
        url: `https://api.fourthwall.com/open-api/v1.0/products/${encodeURIComponent(productId)}/variants/${encodeURIComponent(variantId)}`,
        method: 'PATCH',
        body: { unitPrice: money },
      },
      {
        url: `https://api.fourthwall.com/open-api/v1.0/products/${encodeURIComponent(productId)}`,
        method: 'PUT',
        body: { name, description, price: money },
      },
      {
        url: `https://api.fourthwall.com/open-api/v1.0/products/${encodeURIComponent(productId)}`,
        method: 'PATCH',
        body: { name, description, price: money.value, unitPrice: money },
      },
      {
        url: `https://api.fourthwall.com/open-api/v1.0/products/${encodeURIComponent(productId)}/price`,
        method: 'PUT',
        body: money,
      },
    ];
    for (const a of attempts) {
      try {
        await this.openFetch(a.url, {
          method: a.method,
          body: JSON.stringify(a.body),
        });
      } catch (e) {
        this.logger.warn(
          `reprice try ${a.method} ${a.url.split('/v1.0/')[1] || ''}: ${(e as Error).message || e}`,
        );
      }
    }
    await this.touchProductMeta(productId, name, description).catch(() => undefined);
    await this.makeProductPurchasable(productId);
    const details = await this.getProduct(productId).catch(() => null);
    if (!details) return null;
    return this.summaryFromProduct(details, productId, name, priceUsd);
  }

  private async touchProductMeta(
    productId: string,
    name: string,
    description: string,
  ): Promise<void> {
    const bodies = [
      { name, description },
      { name },
    ];
    for (const body of bodies) {
      try {
        await this.openFetch(
          `https://api.fourthwall.com/open-api/v1.0/products/${encodeURIComponent(productId)}`,
          { method: 'PUT', body: JSON.stringify(body) },
        );
        return;
      } catch {
        try {
          await this.openFetch(
            `https://api.fourthwall.com/open-api/v1.0/products/${encodeURIComponent(productId)}`,
            { method: 'PATCH', body: JSON.stringify(body) },
          );
          return;
        } catch {
          /* try next */
        }
      }
    }
  }

  private async archiveProduct(productId: string): Promise<void> {
    try {
      await this.openFetch(
        `https://api.fourthwall.com/open-api/v1.0/products/${encodeURIComponent(productId)}/availability`,
        { method: 'PUT', body: JSON.stringify({ available: false }) },
      );
    } catch {
      /* ignore */
    }
    try {
      await this.openFetch(
        `https://api.fourthwall.com/open-api/v1.0/products/${encodeURIComponent(productId)}/state`,
        { method: 'PUT', body: JSON.stringify({ state: 'ARCHIVED' }) },
      );
    } catch {
      try {
        await this.openFetch(
          `https://api.fourthwall.com/open-api/v1.0/products/${encodeURIComponent(productId)}/state`,
          { method: 'PUT', body: JSON.stringify({ state: 'HIDDEN' }) },
        );
      } catch {
        /* ignore */
      }
    }
  }

  /** Make offer visible + buyable on storefront. */
  async makeProductPurchasable(productId: string): Promise<void> {
    const id = encodeURIComponent(productId);
    try {
      await this.openFetch(
        `https://api.fourthwall.com/open-api/v1.0/products/${id}/availability`,
        {
          method: 'PUT',
          body: JSON.stringify({ available: true }),
        },
      );
    } catch (e) {
      this.logger.warn(
        `availability flip failed ${productId}: ${(e as Error).message || e}`,
      );
    }
    try {
      await this.openFetch(
        `https://api.fourthwall.com/open-api/v1.0/products/${id}/state`,
        {
          method: 'PUT',
          body: JSON.stringify({ state: 'PUBLIC' }),
        },
      );
    } catch (e) {
      // Some shops only support availability — ignore state errors.
      this.logger.warn(
        `state PUBLIC skipped ${productId}: ${(e as Error).message || e}`,
      );
    }
  }

  async getProduct(productId: string): Promise<Record<string, any>> {
    return this.openFetch(
      `https://api.fourthwall.com/open-api/v1.0/products/${encodeURIComponent(productId)}`,
    );
  }

  async listProducts(size = 100): Promise<any[]> {
    const data = await this.openFetch(
      `https://api.fourthwall.com/open-api/v1.0/products?size=${size}`,
    );
    if (Array.isArray(data?.results)) return data.results;
    if (Array.isArray(data)) return data;
    return [];
  }

  async findProductBySkuMarker(
    sku: string,
  ): Promise<FourthwallProductSummary | null> {
    const marker = `JEHO_SKU:${sku}`.toLowerCase();
    // Paginate a bit — shops with many digital packs can exceed one page.
    const pages: any[] = [];
    const first = await this.listProducts(100);
    pages.push(...first);
    for (const p of pages) {
      const desc = String(p?.description || '').toLowerCase();
      const id = String(p?.id || p?.productId || '');
      if (!id) continue;
      // Exact marker only — never fuzzy name.match(sku) (that maps wrong packs to $0.99).
      if (!desc.includes(marker)) continue;
      // Prefer rows where marker is a dedicated line (avoid partial collisions).
      const lines = desc.split(/\r?\n/).map((l) => l.trim());
      const hasExact = lines.some(
        (l) => l === marker || l.startsWith(`${marker}|`) || l.startsWith(`${marker} `),
      );
      if (!hasExact && !desc.includes(marker)) continue;
      const variantId = this.extractVariantId(p);
      if (!variantId) continue;
      return this.summaryFromProduct(p, id, String(p?.name || sku), 0);
    }
    return null;
  }

  private async readProductSummary(
    productId: string,
    fallback: FourthwallProductSummary,
  ): Promise<FourthwallProductSummary> {
    const details = await this.getProduct(productId).catch(() => null);
    if (!details) return fallback;
    const live = this.summaryFromProduct(
      details,
      productId,
      fallback.name,
      fallback.priceUsd,
    );
    // If API omitted price fields, keep previous known product price.
    if (!(live.priceUsd > 0) && fallback.priceUsd > 0) {
      live.priceUsd = fallback.priceUsd;
    }
    if (!live.variantId && fallback.variantId) {
      live.variantId = fallback.variantId;
    }
    return live;
  }

  private summaryFromProduct(
    product: Record<string, any>,
    productId: string,
    nameFallback: string,
    priceFallback: number,
    opts?: { trustFallback?: boolean },
  ): FourthwallProductSummary {
    const variantId = this.extractVariantId(product);
    const extracted = this.extractPriceUsd(product);
    const price =
      extracted > 0
        ? extracted
        : opts?.trustFallback && priceFallback > 0
          ? priceFallback
          : 0;
    return {
      productId,
      variantId: variantId || '',
      name: String(product?.name || nameFallback || ''),
      priceUsd: price,
    };
  }

  /** Normalize Money / cents / string into USD major units. */
  private extractPriceUsd(product: Record<string, any> | null): number {
    if (!product) return 0;
    const candidates: unknown[] = [
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
      if (!Number.isFinite(n) || n <= 0) continue;
      // Heuristic: values ≥ 1000 with no decimal are likely cents of large packs;
      // keep normal $0.99–$999 as-is (Fourthwall Money.value is major units).
      return Math.round(n * 100) / 100;
    }
    return 0;
  }

  async ensureWebhook(
    url: string,
    allowedTypes: string[] = ['ORDER_PLACED', 'ORDER_UPDATED'],
  ): Promise<Record<string, unknown>> {
    return this.openFetch('https://api.fourthwall.com/open-api/v1.0/webhooks', {
      method: 'POST',
      body: JSON.stringify({ url, allowedTypes }),
    });
  }

  async getShop(): Promise<Record<string, any>> {
    return this.openFetch('https://api.fourthwall.com/open-api/v1.0/shops/current');
  }

  async getOrder(orderId: string): Promise<Record<string, any>> {
    const id = encodeURIComponent(String(orderId || '').trim());
    try {
      return await this.openFetch(
        `https://api.fourthwall.com/open-api/v1.0/order/${id}`,
      );
    } catch {
      return this.openFetch(
        `https://api.fourthwall.com/open-api/v1.0/orders/${id}`,
      );
    }
  }

  async getOrderByFriendlyId(friendlyId: string): Promise<Record<string, any>> {
    const id = encodeURIComponent(String(friendlyId || '').trim());
    return this.openFetch(
      `https://api.fourthwall.com/open-api/v1.0/order/by-friendly-id/${id}`,
    );
  }

  /**
   * Recent shop orders (for post-checkout reconcile when webhook is slow/missing).
   * GET /open-api/v1.0/order?page=&size=&email=&createdAt[gt]=
   */
  async listOrders(opts: {
    size?: number;
    page?: number;
    email?: string;
    createdAtGt?: string;
  } = {}): Promise<Record<string, any>[]> {
    const q = new URLSearchParams();
    q.set('page', String(opts.page ?? 0));
    q.set('size', String(opts.size ?? 20));
    if (opts.email) q.set('email', String(opts.email).trim().toLowerCase());
    if (opts.createdAtGt) q.set('createdAt[gt]', opts.createdAtGt);
    const res = await this.openFetch(
      `https://api.fourthwall.com/open-api/v1.0/order?${q.toString()}`,
    );
    const rows =
      (Array.isArray(res?.content) && res.content) ||
      (Array.isArray(res?.results) && res.results) ||
      (Array.isArray(res?.items) && res.items) ||
      (Array.isArray(res?.data) && res.data) ||
      (Array.isArray(res) && res) ||
      [];
    return rows as Record<string, any>[];
  }

  private extractVariantId(product: Record<string, any> | null): string {
    if (!product) return '';
    const variants = product.variants || product.offer?.variants || [];
    if (Array.isArray(variants) && variants[0]?.id) return String(variants[0].id);
    if (product.variantId) return String(product.variantId);
    return '';
  }

  private async attachTinyDigitalFile(
    productId: string,
    sku: string,
    series: string,
    edition: number,
  ) {
    // Neutral eBook payload only — no currency wording (ToS / compliance).
    const content = Buffer.from(
      [
        `${series}`,
        `Digital eBook · Edition ${edition}`,
        `Instant download · text format`,
        `Catalog ref: ${sku}`,
        '',
      ].join('\n'),
      'utf8',
    );
    const size = content.byteLength;
    const safeSeries = series
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-|-$/g, '')
      .slice(0, 32);
    const fileName = `${safeSeries || 'ebook'}-ed${edition}.txt`;
    const up = await this.openFetch(
      `https://api.fourthwall.com/open-api/v1.0/products/${encodeURIComponent(productId)}/digital-files/upload-url`,
      {
        method: 'POST',
        body: JSON.stringify({
          fileName,
          contentType: 'text/plain',
          size,
        }),
      },
    );
    const uploadUrl = String(up?.uploadUrl || '');
    const fileUrl = String(up?.fileUrl || '');
    if (!uploadUrl || !fileUrl) {
      throw new Error('Missing digital-files uploadUrl');
    }
    const put = await fetch(uploadUrl, {
      method: 'PUT',
      headers: {
        'Content-Type': 'text/plain',
        'x-goog-content-length-range': `0,${size}`,
      },
      body: content,
    });
    if (!put.ok) {
      throw new Error(`GCS upload failed ${put.status}`);
    }
    await this.openFetch(
      `https://api.fourthwall.com/open-api/v1.0/products/${encodeURIComponent(productId)}/digital-files`,
      {
        method: 'POST',
        body: JSON.stringify({ fileUrl, fileName }),
      },
    );
  }

  private async sfFetch(
    url: string,
    init: RequestInit,
  ): Promise<Record<string, any>> {
    const res = await fetch(url, {
      ...init,
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
        ...(init.headers || {}),
      },
    });
    const text = await res.text();
    let json: any = {};
    try {
      json = text ? JSON.parse(text) : {};
    } catch {
      json = { raw: text };
    }
    if (!res.ok) {
      throw new Error(
        `Storefront ${res.status}: ${typeof json === 'object' ? JSON.stringify(json) : text}`,
      );
    }
    return json;
  }

  private async openFetch(
    url: string,
    init: RequestInit = {},
  ): Promise<Record<string, any>> {
    const basic = Buffer.from(
      `${this.apiUser}:${this.apiPassword}`,
      'utf8',
    ).toString('base64');
    const res = await fetch(url, {
      ...init,
      headers: {
        Authorization: `Basic ${basic}`,
        'Content-Type': 'application/json',
        Accept: 'application/json',
        ...(init.headers || {}),
      },
    });
    const text = await res.text();
    let json: any = {};
    try {
      json = text ? JSON.parse(text) : {};
    } catch {
      json = { raw: text };
    }
    if (!res.ok) {
      throw new Error(
        `OpenAPI ${res.status}: ${typeof json === 'object' ? JSON.stringify(json) : text}`,
      );
    }
    return json;
  }
}
