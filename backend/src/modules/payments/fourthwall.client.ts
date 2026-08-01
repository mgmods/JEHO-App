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
   * Products must be available (published) or checkout shows $0 / hangs.
   */
  async ensureCoinPackageProduct(input: {
    sku: string;
    coins: number;
    bonusCoins?: number;
    priceUsd: number;
  }): Promise<FourthwallProductSummary> {
    const sku = String(input.sku || '').trim();
    const coins = Math.max(0, Number(input.coins) || 0);
    const bonus = Math.max(0, Number(input.bonusCoins) || 0);
    const priceUsd = Math.round(Number(input.priceUsd) * 100) / 100;
    const marker = `JEHO_SKU:${sku}`;
    const name = `JEHO ${coins.toLocaleString('en-US')} Coins`;

    const existing = await this.findProductBySkuMarker(sku);
    if (existing?.variantId && existing.productId) {
      await this.makeProductPurchasable(existing.productId);
      // Refresh variant after availability flip
      const details = await this.getProduct(existing.productId).catch(() => null);
      const variantId =
        this.extractVariantId(details) || existing.variantId;
      return {
        productId: existing.productId,
        variantId,
        name: existing.name || name,
        priceUsd: existing.priceUsd || priceUsd,
      };
    }

    const created = await this.openFetch(
      'https://api.fourthwall.com/open-api/v1.0/products',
      {
        method: 'POST',
        body: JSON.stringify({
          type: 'digital',
          name,
          description: `${marker}\nJEHO Chat in-app coin recharge.\nCoins: ${coins}${bonus ? ` +${bonus} bonus` : ''}\nPrice: $${priceUsd}`,
          price: priceUsd,
          publishOnCreate: false,
        }),
      },
    );
    const productId = String(
      created?.productId || created?.id || created?.product?.id || '',
    );
    if (!productId) {
      throw new Error(
        `Fourthwall product create returned no id: ${JSON.stringify(created).slice(0, 400)}`,
      );
    }

    try {
      await this.attachTinyDigitalFile(productId, sku, coins);
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
    return { productId, variantId, name, priceUsd };
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
    const products = await this.listProducts(100);
    for (const p of products) {
      const desc = String(p?.description || '').toLowerCase();
      const name = String(p?.name || '');
      const id = String(p?.id || p?.productId || '');
      if (!id) continue;
      if (desc.includes(marker) || name.toLowerCase().includes(sku.toLowerCase())) {
        const variantId = this.extractVariantId(p);
        if (!variantId) continue;
        const price =
          Number(p?.variants?.[0]?.unitPrice?.value) ||
          Number(p?.price?.value) ||
          Number(p?.price) ||
          0;
        return {
          productId: id,
          variantId,
          name: name || sku,
          priceUsd: price,
        };
      }
    }
    return null;
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
    coins: number,
  ) {
    const content = Buffer.from(
      `JEHO Chat coin recharge\nSKU=${sku}\nCOINS=${coins}\n`,
      'utf8',
    );
    const size = content.byteLength;
    const fileName = `jeho-${sku}.txt`;
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
