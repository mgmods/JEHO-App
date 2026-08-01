import { registerAs } from '@nestjs/config';

export default registerAs('app', () => {
  if (process.env.NODE_ENV === 'production') {
    const required = [
      ['JWT_SECRET', process.env.JWT_SECRET],
      ['JWT_REFRESH_SECRET', process.env.JWT_REFRESH_SECRET],
      ['ADMIN_PASSWORD', process.env.ADMIN_PASSWORD],
      ['DB_PASSWORD', process.env.DB_PASSWORD],
    ] as const;
    const missing = required
      .filter(([, value]) => !value || value.length < 16)
      .map(([name]) => name);
    if (missing.length) {
      throw new Error(
        `Production secrets are missing or too short: ${missing.join(', ')}`,
      );
    }
    if (process.env.JWT_SECRET === process.env.JWT_REFRESH_SECRET) {
      throw new Error('JWT_SECRET and JWT_REFRESH_SECRET must be different');
    }
  }
  return {
  nodeEnv: process.env.NODE_ENV || 'development',
  port: parseInt(process.env.PORT || '3000', 10),
  apiPrefix: process.env.API_PREFIX || 'api/v1',
  corsOrigins: (process.env.CORS_ORIGINS || '*').split(',').map((s) => s.trim()),
  throttleTtl: parseInt(process.env.THROTTLE_TTL || '60', 10),
  throttleLimit: parseInt(process.env.THROTTLE_LIMIT || '400', 10),
  uploadDir: process.env.UPLOAD_DIR || './uploads',
  uploadMaxSizeMb: parseInt(process.env.UPLOAD_MAX_SIZE_MB || '20', 10),
  internetMusic: {
    pipedInstances: process.env.PIPED_API_INSTANCES || '',
  },
  database: {
    host: process.env.DB_HOST || 'localhost',
    port: parseInt(process.env.DB_PORT || '5432', 10),
    username: process.env.DB_USERNAME || 'auralive',
    password: process.env.DB_PASSWORD || 'auralive_secret',
    database: process.env.DB_DATABASE || 'auralive',
    synchronize: process.env.DB_SYNCHRONIZE === 'true',
    logging: process.env.DB_LOGGING === 'true',
  },
  redis: {
    host: process.env.REDIS_HOST || 'localhost',
    port: parseInt(process.env.REDIS_PORT || '6379', 10),
    password: process.env.REDIS_PASSWORD || undefined,
  },
  jwt: {
    secret: process.env.JWT_SECRET || 'change-me-jwt-secret-min-32-chars-long',
    expiresIn: process.env.JWT_EXPIRES_IN || '7d',
    refreshSecret:
      process.env.JWT_REFRESH_SECRET || 'change-me-refresh-secret-min-32-chars',
    refreshExpiresIn: process.env.JWT_REFRESH_EXPIRES_IN || '7d',
  },
  zego: {
    appId: parseInt(process.env.ZEGO_APP_ID || '0', 10),
    appSign: process.env.ZEGO_APP_SIGN || '',
    serverSecret: process.env.ZEGO_SERVER_SECRET || '',
    callbackSecret: process.env.ZEGO_CALLBACK_SECRET || '',
    tokenTtl: parseInt(process.env.ZEGO_TOKEN_TTL || '3600', 10),
    wsUrl: process.env.ZEGO_WS_URL || '',
    wsUrlBak: process.env.ZEGO_WS_URL_BAK || '',
  },
  stripe: {
    secretKey: process.env.STRIPE_SECRET_KEY || '',
    webhookSecret: process.env.STRIPE_WEBHOOK_SECRET || '',
  },
  binanceWallet: {
    apiKey: process.env.BINANCE_WALLET_API_KEY || process.env.BINANCE_PAY_API_KEY || '',
    secretKey: process.env.BINANCE_WALLET_SECRET_KEY || process.env.BINANCE_PAY_SECRET_KEY || '',
    baseUrl:
      process.env.BINANCE_WALLET_BASE_URL ||
      process.env.BINANCE_PAY_BASE_URL ||
      'https://api.binance.com',
    accountName: process.env.BINANCE_WALLET_ACCOUNT_NAME || '',
  },
  /** @deprecated legacy env block — use binanceWallet */
  binancePay: {
    apiKey: process.env.BINANCE_WALLET_API_KEY || process.env.BINANCE_PAY_API_KEY || '',
    secretKey: process.env.BINANCE_WALLET_SECRET_KEY || process.env.BINANCE_PAY_SECRET_KEY || '',
    baseUrl:
      process.env.BINANCE_WALLET_BASE_URL ||
      process.env.BINANCE_PAY_BASE_URL ||
      'https://api.binance.com',
  },
  settingsEncryptionKey: process.env.SETTINGS_ENCRYPTION_KEY || '',
  paypal: {
    clientId: process.env.PAYPAL_CLIENT_ID || '',
    clientSecret: process.env.PAYPAL_CLIENT_SECRET || '',
    mode: process.env.PAYPAL_MODE || 'sandbox',
  },
  googlePlay: {
      packageName: process.env.GOOGLE_PLAY_PACKAGE_NAME || 'com.Dramizo.Series',
    // Production is fail-closed. Local/dev can still credit when credentials are absent.
    enforce:
      process.env.GOOGLE_PLAY_VERIFY_ENFORCE === 'true' ||
      process.env.NODE_ENV === 'production',
  },
  fcm: {
    serverKey: process.env.FCM_SERVER_KEY || '',
    credentialsPath: process.env.FIREBASE_CREDENTIALS_PATH || '',
  },
  google: {
    // Web client ID from Firebase google-services.json (client_type 3).
    // Must match Android requestIdToken(default_web_client_id).
    clientId:
      process.env.GOOGLE_CLIENT_ID ||
      '1085967276199-c58dro18a44qocpeme8868sudnqv8kte.apps.googleusercontent.com',
    // Optional comma-separated extra audiences (Android OAuth clients).
    clientIds: process.env.GOOGLE_CLIENT_IDS || '',
  },
  admin: {
    email: process.env.ADMIN_EMAIL || 'admin@auralive.com',
    password: process.env.ADMIN_PASSWORD || 'Admin@AuraLive2024',
    username: process.env.ADMIN_USERNAME || 'auralive_admin',
  },
  };
});
