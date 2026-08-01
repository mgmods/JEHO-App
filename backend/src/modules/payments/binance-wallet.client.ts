import * as crypto from 'crypto';

export const BINANCE_WALLET_ALLOWED_BASE_URLS = [
  'https://api.binance.com',
  'https://api.binance.us',
] as const;

export type BinanceWalletBaseUrl = (typeof BINANCE_WALLET_ALLOWED_BASE_URLS)[number];

export interface BinanceApiResult<T = unknown> {
  success: boolean;
  data?: T;
  error?: string;
  code?: number | string;
  raw?: string;
}

export interface BinanceDepositAddress {
  address: string;
  coin: string;
  tag?: string;
  url?: string;
}

export interface BinanceDepositRecord {
  id: string;
  amount: string;
  coin: string;
  network: string;
  status: number;
  address: string;
  addressTag?: string;
  txId: string;
  insertTime: number;
  transferType?: number;
  confirmTimes?: string;
  unlockConfirm?: number;
  walletType?: number;
}

export interface BinanceAccountBalance {
  asset: string;
  free: string;
  locked: string;
}

export class BinanceWalletClient {
  constructor(
    private readonly apiKey: string,
    private readonly secretKey: string,
    private readonly baseUrl: BinanceWalletBaseUrl,
  ) {}

  static assertAllowedBaseUrl(baseUrl: string): BinanceWalletBaseUrl {
    const normalized = baseUrl.replace(/\/+$/, '');
    if (!BINANCE_WALLET_ALLOWED_BASE_URLS.includes(normalized as BinanceWalletBaseUrl)) {
      throw new Error(
        `Invalid Binance API base URL. Allowed: ${BINANCE_WALLET_ALLOWED_BASE_URLS.join(', ')}`,
      );
    }
    return normalized as BinanceWalletBaseUrl;
  }

  private sign(queryString: string): string {
    return crypto.createHmac('sha256', this.secretKey).update(queryString).digest('hex');
  }

  async signedGet<T = unknown>(
    endpoint: string,
    params: Record<string, string | number> = {},
  ): Promise<BinanceApiResult<T>> {
    const signedParams: Record<string, string | number> = {
      ...params,
      timestamp: Date.now(),
      recvWindow: 10000,
    };
    const queryString = new URLSearchParams(
      Object.entries(signedParams).map(([k, v]) => [k, String(v)]),
    ).toString();
    const signature = this.sign(queryString);
    const url = `${this.baseUrl}${endpoint}?${queryString}&signature=${signature}`;
    return this.request<T>('GET', url);
  }

  async signedPost<T = unknown>(
    endpoint: string,
    params: Record<string, string | number> = {},
  ): Promise<BinanceApiResult<T>> {
    const signedParams: Record<string, string | number> = {
      ...params,
      timestamp: Date.now(),
      recvWindow: 10000,
    };
    const queryString = new URLSearchParams(
      Object.entries(signedParams).map(([k, v]) => [k, String(v)]),
    ).toString();
    const signature = this.sign(queryString);
    const url = `${this.baseUrl}${endpoint}?${queryString}&signature=${signature}`;
    return this.request<T>('POST', url);
  }

  private async request<T>(method: 'GET' | 'POST', url: string): Promise<BinanceApiResult<T>> {
    try {
      const response = await fetch(url, {
        method,
        headers: {
          'X-MBX-APIKEY': this.apiKey,
          'Content-Type': 'application/json',
        },
      });
      const raw = await response.text();
      let data: unknown;
      try {
        data = raw ? JSON.parse(raw) : {};
      } catch {
        data = { msg: raw };
      }

      if (!response.ok) {
        const errObj = data as { msg?: string; code?: number | string };
        return {
          success: false,
          error: errObj.msg || `HTTP ${response.status}`,
          code: errObj.code ?? response.status,
          raw,
        };
      }

      return { success: true, data: data as T };
    } catch (err) {
      return {
        success: false,
        error: (err as Error).message || 'Network error',
      };
    }
  }

  getDepositAddress(coin: string, network = ''): Promise<BinanceApiResult<BinanceDepositAddress>> {
    const params: Record<string, string> = { coin };
    if (network) params.network = network;
    return this.signedGet<BinanceDepositAddress>('/sapi/v1/capital/deposit/address', params);
  }

  getDepositHistory(
    coin = '',
    startTime = 0,
    limit = 100,
  ): Promise<BinanceApiResult<BinanceDepositRecord[]>> {
    const params: Record<string, string | number> = { limit };
    if (coin) params.coin = coin;
    if (startTime > 0) params.startTime = startTime;
    return this.signedGet<BinanceDepositRecord[]>('/sapi/v1/capital/deposit/hisrec', params);
  }

  getAccountBalances(): Promise<
    BinanceApiResult<{ balances: BinanceAccountBalance[] }>
  > {
    return this.signedGet<{ balances: BinanceAccountBalance[] }>('/api/v3/account');
  }

  async testConnection(): Promise<{ ok: boolean; message: string }> {
    const account = await this.getAccountBalances();
    if (account.success) {
      return { ok: true, message: 'Exchange API credentials accepted (account readable).' };
    }

    const address = await this.getDepositAddress('USDT', 'TRX');
    if (address.success && address.data?.address) {
      return { ok: true, message: 'Exchange API credentials accepted (deposit address readable).' };
    }

    return {
      ok: false,
      message:
        account.error ||
        address.error ||
        'Could not verify Binance Exchange API credentials.',
    };
  }
}
