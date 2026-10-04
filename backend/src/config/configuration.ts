import { registerAs } from '@nestjs/config';

export default registerAs('app', () => {
  if (process.env.NODE_ENV === 'production') {
    const required = [
      ['JWT_SECRET', process.env.JWT_SECRET],
      ['JWT_REFRESH_SECRET', process.env.JWT_REFRESH_SECRET],
      ['ADMIN_PASSWORD', process.env.ADMIN_PASSWORD],
    ] as const;
    const missing = required
      .filter(([, value]) => !value || value.length < 16)
      .map(([name]) => name);
    if (missing.length) {
      throw new Error(
        `Production secrets are missing or too short: ${missing.join(', ')}`,
      );
    }
    const databaseUrl = process.env.DATABASE_URL?.trim();
    const databasePassword = process.env.DB_PASSWORD;
    if (!databaseUrl && (!databasePassword || databasePassword.length < 16)) {
      throw new Error('Set DATABASE_URL or a DB_PASSWORD of at least 16 characters in production');
    }
    if (databaseUrl && !['postgres://', 'postgresql://'].some((prefix) => databaseUrl.toLowerCase().startsWith(prefix))) {
      throw new Error('DATABASE_URL must be a PostgreSQL connection URL');
    }
    if (process.env.JWT_SECRET === process.env.JWT_REFRESH_SECRET) {
      throw new Error('JWT_SECRET and JWT_REFRESH_SECRET must be different');
    }
  }
  return {
  nodeEnv: process.env.NODE_ENV || 'development',
  port: parseInt(process.env.PORT || '3000', 10),
  apiPrefix: process.env.API_PREFIX || 'api/v1',
  corsOrigins: process.env.CORS_ORIGINS
    ? process.env.CORS_ORIGINS.split(',').map((s) => s.trim()).filter(Boolean)
    : process.env.NODE_ENV === 'production'
      ? []
      : ['*'],
  throttleTtl: parseInt(process.env.THROTTLE_TTL || '60', 10),
  throttleLimit: parseInt(process.env.THROTTLE_LIMIT || '400', 10),
  uploadDir: process.env.UPLOAD_DIR || './uploads',
  uploadMaxSizeMb: parseInt(process.env.UPLOAD_MAX_SIZE_MB || '20', 10),
  internetMusic: {
    pipedInstances: process.env.PIPED_API_INSTANCES || '',
  },
  database: {
    // Render provides a private DATABASE_URL. Individual DB_* values remain for local development.
    url: process.env.DATABASE_URL || '',
    ssl: process.env.DB_SSL === 'true',
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
  /** Self-hosted LiveKit (open source). No Cloud fees when you run Docker on your VPS. */
  livekit: {
    provider: (process.env.VOICE_RTC_PROVIDER || 'zego').toLowerCase(),
    url: process.env.LIVEKIT_URL || '',
    apiKey: process.env.LIVEKIT_API_KEY || '',
    apiSecret: process.env.LIVEKIT_API_SECRET || '',
    tokenTtl: parseInt(process.env.LIVEKIT_TOKEN_TTL || '3600', 10),
  },
  stripe: {
    secretKey: process.env.STRIPE_SECRET_KEY || '',
    webhookSecret: process.env.STRIPE_WEBHOOK_SECRET || '',
  },
  fourthwall: {
    apiUser: process.env.FOURTHWALL_API_USER || '',
    apiPassword: process.env.FOURTHWALL_API_PASSWORD || '',
    storefrontToken: process.env.FOURTHWALL_STOREFRONT_TOKEN || '',
    webhookSecret: process.env.FOURTHWALL_WEBHOOK_SECRET || '',
    shopDomain: process.env.FOURTHWALL_SHOP_DOMAIN || '',
    /** JSON map: { "coins_1000": "variant-uuid", ... } */
    variantMapJson: process.env.FOURTHWALL_VARIANT_MAP || '{}',
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
    // Must match android `applicationId` / Play Console package exactly.
    packageName: process.env.GOOGLE_PLAY_PACKAGE_NAME || 'com.Dramizo.Series',
    /**
     * Optional dedicated service-account JSON for Android Publisher API
     * (Play Console → Users and permissions → API access). NOT Firebase.
     */
    serviceAccountPath:
      process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON ||
      process.env.GOOGLE_PLAY_CREDENTIALS ||
      '',
    /**
     * If true, refuse credit when Publisher API is down/misconfigured.
     * Default false: still credit valid product SKUs with a unique purchaseToken
     * so users who already paid on Play are not left with zero coins.
     */
    requirePublisherApi: process.env.GOOGLE_PLAY_REQUIRE_PUBLISHER === 'true',
  },
  fcm: {
    serverKey: process.env.FCM_SERVER_KEY || '',
    credentialsPath: process.env.FIREBASE_CREDENTIALS_PATH || '',
  },
  google: {
    // Web client ID from Firebase google-services.json (client_type 3).
    // Must match Android requestIdToken(default_web_client_id).
    // Intentionally no legacy-project fallback; configure a JEHO-OWN OAuth client explicitly.
    clientId: process.env.GOOGLE_CLIENT_ID || '',
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
