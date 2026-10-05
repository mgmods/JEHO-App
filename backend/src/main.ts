import { NestFactory } from '@nestjs/core';
import { ValidationPipe } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';
import helmet from 'helmet';
import { readFile } from 'fs';
import { join } from 'path';
import { NestExpressApplication } from '@nestjs/platform-express';
import { AppModule } from './app.module';
import { AllExceptionsFilter } from './common/filters/all-exceptions.filter';
import { TransformInterceptor } from './common/interceptors/transform.interceptor';
import { RedisIoAdapter } from './common/adapters/redis-io.adapter';
import { MikooGatewayService } from './modules/games/mikoo-gateway/mikoo-gateway.service';
import { UploadsService } from './modules/uploads/uploads.service';

async function bootstrap() {
  const app = await NestFactory.create<NestExpressApplication>(AppModule, {
    rawBody: true,
  });

  const configService = app.get(ConfigService);
  const port = configService.get<number>('app.port') || 3000;
  const apiPrefix = configService.get<string>('app.apiPrefix') || 'api/v1';
  const corsOrigins = configService.get<string[]>('app.corsOrigins') || ['*'];
  const redisIoAdapter = new RedisIoAdapter(app, configService);
  await redisIoAdapter.connect();
  app.useWebSocketAdapter(redisIoAdapter);

  app.use(
    helmet({
      // Game HTML pages use inline <script>/onclick — default CSP breaks them (yellow ring only).
      contentSecurityPolicy: false,
      crossOriginResourcePolicy: { policy: 'cross-origin' },
    }),
  );
  app.enableCors({
    origin: corsOrigins.includes('*') ? true : corsOrigins,
    credentials: true,
  });

  app.setGlobalPrefix(apiPrefix, {
    exclude: [
      'games/route/get_addr',
      'games/route/update_time',
      'games/route/client_log/dev/add',
      'games/route/client_log/test/add',
      'games/route/client_log/prod/add',
      // BaiShun legacy path (no s) — some packages still request /game_route/*
      'game_route/get_addr',
      'game_route/update_time',
      'game_route/client_log/dev/add',
      'game_route/client_log/test/add',
      'game_route/client_log/prod/add',
    ],
  });
  // Remap historical BaiShun /game_route → /games/route before routing.
  const expressForRoute = app.getHttpAdapter().getInstance();
  expressForRoute.use((req: { url?: string; originalUrl?: string }, _res: unknown, next: () => void) => {
    const raw = String(req.url || '');
    if (raw.startsWith('/game_route') || raw.startsWith('/game-route')) {
      req.url = raw.replace(/^\/game-?route/i, '/games/route');
    }
    next();
  });
  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      transform: true,
      forbidNonWhitelisted: true,
      transformOptions: { enableImplicitConversion: true },
    }),
  );
  app.useGlobalFilters(new AllExceptionsFilter());
  app.useGlobalInterceptors(new TransformInterceptor());

  const uploadDir = configService.get<string>('app.uploadDir') || './uploads';
  const noStoreHeaders = (res: { setHeader: (k: string, v: string) => void }) => {
    res.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate, max-age=0');
    res.setHeader('Pragma', 'no-cache');
    res.setHeader('Expires', '0');
  };
  app.useStaticAssets(join(process.cwd(), uploadDir), {
    prefix: '/uploads/',
    etag: false,
    lastModified: false,
    setHeaders: noStoreHeaders,
  });

  const expressAppEarly = app.getHttpAdapter().getInstance();

  // Backward-compatible public upload URL. New uploads are stored in Supabase and the
  // upload service removes the ephemeral local copy, so /uploads/:filename must fall
  // through to Storage instead of returning the local static 404. This also keeps old
  // profile/room/splash URLs working without requiring an APK update.
  const uploadsService = app.get(UploadsService);
  expressAppEarly.get('/uploads/:filename', async (req: { params: { filename?: string } }, res: {
    setHeader: (k: string, v: string) => void;
    status: (n: number) => { send: (body: string | Buffer) => void };
  }) => {
    try {
      const filename = String(req.params?.filename || '');
      const stored = await uploadsService.getStoredFile(filename);
      if (!stored) return res.status(404).send('File not found');
      res.setHeader('Content-Type', stored.contentType);
      res.setHeader('Cache-Control', 'public, max-age=3600');
      return res.status(200).send(stored.buffer);
    } catch {
      return res.status(502).send('Storage unavailable');
    }
  });

  // BaiShun HTML: inject JEHO host rewrite before static serve (no jieyou/sruner/zkruner).
  const forceLocalTag =
    '<script src="/games/mikoo/_jeho/jeho-force-local.js"></script>';
  expressAppEarly.use((req: { method?: string; path?: string; url?: string }, res: {
    setHeader: (k: string, v: string) => void;
    status: (n: number) => { end: (b: string) => void };
    sendFile: (p: string, opts: { root?: string }, cb?: (err?: Error) => void) => void;
  }, next: () => void) => {
    const pathName = String(req.path || req.url || '').split('?')[0];
    if (
      req.method === 'GET' &&
      /^\/games\/mikoo\/(slot777|cleopatra-slot|football-plinko|fishing|hilo|royal-battle|swimsuit-party|greedy-lion)\/?(index\.html)?$/i.test(
        pathName,
      )
    ) {
      const parts = pathName.replace(/\/+/g, '/').split('/');
      const slug = parts[3];
      const htmlPath = join(process.cwd(), 'public', 'games', 'mikoo', slug, 'index.html');
      readFile(htmlPath, 'utf8', (err, html) => {
        if (err || !html) return next();
        let out = html;
        if (!out.includes('jeho-force-local.js')) {
          out = out.replace(/<head([^>]*)>/i, `<head$1>\n    ${forceLocalTag}`);
        }
        res.setHeader('Content-Type', 'text/html; charset=utf-8');
        noStoreHeaders(res);
        res.status(200).end(out);
      });
      return;
    }
    next();
  });

  // Serve the Vue admin dashboard from the same origin as the API.
  // Static files (including /admin/assets/*) continue through to static middleware below.
  expressAppEarly.use((req: { method?: string; path?: string; url?: string }, res: {
    setHeader: (k: string, v: string) => void;
    status: (n: number) => { send: (body: string) => void };
  }, next: () => void) => {
    const pathName = String(req.path || req.url || '').split('?')[0];
    if (
      req.method === 'GET' &&
      (pathName === '/admin' || pathName.startsWith('/admin/')) &&
      !/\.[^/]+$/.test(pathName)
    ) {
      readFile(join(process.cwd(), 'public', 'admin', 'index.html'), 'utf8', (err, html) => {
        if (err || !html) return next();
        res.setHeader('Content-Type', 'text/html; charset=utf-8');
        noStoreHeaders(res);
        res.status(200).send(html);
      });
      return;
    }
    next();
  });

  app.useStaticAssets(join(process.cwd(), 'public'), {
    prefix: '/',
    etag: false,
    lastModified: false,
    // Required so Google can fetch /.well-known/assetlinks.json
    dotfiles: 'allow',
    setHeaders: (res, filePath) => {
      noStoreHeaders(res);
      if (String(filePath).endsWith('assetlinks.json') || String(filePath).endsWith('jeho-force-local.js')) {
        if (String(filePath).endsWith('assetlinks.json')) {
          res.setHeader('Content-Type', 'application/json; charset=utf-8');
        }
      }
    },
  });

  const swaggerConfig = new DocumentBuilder()
    .setTitle('JEHO CHAT API')
    .setDescription('JEHO CHAT voice-room platform backend')
    .setVersion('1.0')
    .addBearerAuth()
    .build();
  const document = SwaggerModule.createDocument(app, swaggerConfig);
  SwaggerModule.setup('api/docs', app, document);

  // Invite deep links: if the app is not installed, send user to Play with referrer.
  // When installed, Android App Links open SplashActivity instead of hitting this.
  const expressApp = app.getHttpAdapter().getInstance();
  const playPackage = 'com.Dramizo.Series';
  const inviteToPlay = (code: string) => {
    const clean = String(code || '')
      .trim()
      .replace(/[/?#].*$/, '')
      .slice(0, 64);
    const referrer = encodeURIComponent(
      `utm_source=invite&utm_medium=share&utm_content=${clean}`,
    );
    return `https://play.google.com/store/apps/details?id=${playPackage}&referrer=${referrer}`;
  };
  expressApp.get('/open/invite/:code', (req: { params: { code?: string } }, res: {
    setHeader: (k: string, v: string) => void;
    redirect: (status: number, url: string) => void;
  }) => {
    const play = inviteToPlay(req.params?.code || '');
    res.setHeader('Cache-Control', 'no-store');
    res.redirect(302, play);
  });
  expressApp.get('/open/invite', (_req: unknown, res: {
    setHeader: (k: string, v: string) => void;
    redirect: (status: number, url: string) => void;
  }) => {
    res.setHeader('Cache-Control', 'no-store');
    res.redirect(302, `https://play.google.com/store/apps/details?id=${playPackage}`);
  });

  // BaiShun game_route — raw JSON (games expect { code: 200, data: {...} } without API wrapper)
  
  const roomToPlay = (roomId: string) => {
    const clean = String(roomId || '')
      .trim()
      .replace(/[/?#].*$/, '')
      .slice(0, 64);
    const referrer = encodeURIComponent(
      `utm_source=room&utm_medium=share&utm_content=${clean}`,
    );
    return `https://play.google.com/store/apps/details?id=${playPackage}&referrer=${referrer}`;
  };
  expressApp.get('/open/room/:id', (req: { params: { id?: string } }, res: {
    setHeader: (k: string, v: string) => void;
    redirect: (status: number, url: string) => void;
  }) => {
    res.setHeader('Cache-Control', 'no-store');
    res.redirect(302, roomToPlay(req.params?.id || ''));
  });
  expressApp.get('/open/room', (_req: unknown, res: {
    setHeader: (k: string, v: string) => void;
    redirect: (status: number, url: string) => void;
  }) => {
    res.setHeader('Cache-Control', 'no-store');
    res.redirect(302, `https://play.google.com/store/apps/details?id=${playPackage}`);
  });

const baishunModuleMap: Record<string, string> = {
    '1107': 'cleopatra-slot',
    '1022': 'fishing',
    '1184': 'football-plinko',
    '1068': 'greedy-lion',
    '1072': 'hilo',
    '1174': 'royal-battle',
    '1098': 'slot777',
    '1183': 'swimsuit-party',
  };
  const configuredPublicApiOrigin = (process.env.PUBLIC_API_ORIGIN || '').replace(/\/$/, '');
  const baishunGetAddr = (req: {
    query: Record<string, string | undefined>;
    protocol?: string;
    headers?: Record<string, string | string[] | undefined>;
  }, res: {
    setHeader: (k: string, v: string) => void;
    json: (body: unknown) => void;
    status: (n: number) => { json: (body: unknown) => void };
  }) => {
    const rawId = String(req.query?.game_id || '1107').trim().toLowerCase();
    const slug = baishunModuleMap[rawId] || rawId;
    const hostHeader = req.headers?.['x-forwarded-host'] || req.headers?.host || '';
    const host = String(Array.isArray(hostHeader) ? hostHeader[0] : hostHeader)
      .split(',')[0]
      .trim();
    const protoHeader = req.headers?.['x-forwarded-proto'] || req.protocol || 'https';
    const protocol = String(Array.isArray(protoHeader) ? protoHeader[0] : protoHeader)
      .split(',')[0]
      .trim()
      .toLowerCase();
    const requestOrigin =
      host && /^[a-z0-9.-]+(?::[0-9]{1,5})?$/i.test(host) && ['http', 'https'].includes(protocol)
        ? `${protocol}://${host}`
        : '';
    const apiOrigin = configuredPublicApiOrigin || requestOrigin;
    if (!apiOrigin) {
      res.setHeader('Cache-Control', 'no-store');
      res.status(503).json({ code: 503, message: 'Public API origin is not configured' });
      return;
    }
    const wsOrigin = apiOrigin.replace(/^https:\/\//i, 'wss://').replace(/^http:\/\//i, 'ws://');
    res.setHeader('Cache-Control', 'no-store');
    res.json({
      code: 200,
      data: {
        http_addr: `${apiOrigin}/games/route/`,
        ws_addr: `${wsOrigin}/games/ws/${slug}`,
      },
    });
  };
  expressApp.get('/games/route/get_addr', baishunGetAddr);
  expressApp.get('/game_route/get_addr', baishunGetAddr);
  expressApp.get('/game-route/get_addr', baishunGetAddr);
  const baishunUpdateTime = (_req: unknown, res: {
    json: (body: unknown) => void;
  }) => {
    res.json({ code: 200, data: {} });
  };
  expressApp.get('/games/route/update_time', baishunUpdateTime);
  expressApp.get('/game_route/update_time', baishunUpdateTime);
  expressApp.get('/game-route/update_time', baishunUpdateTime);
  const clientLogOk = (_req: unknown, res: { status: (n: number) => { json: (b: unknown) => void } }) => {
    res.status(200).json({ code: 200, data: true });
  };
  for (const env of ['dev', 'test', 'prod'] as const) {
    for (const base of ['/games/route', '/game_route', '/game-route'] as const) {
      (expressApp as { all: (path: string, handler: typeof clientLogOk) => void }).all(
        `${base}/client_log/${env}/add`,
        clientLogOk,
      );
    }
  }

  // Simple deployment health probe (does not expose configuration or secrets).
  expressApp.get('/healthz', (_req: unknown, res: {
    status: (n: number) => { json: (body: unknown) => void };
  }) => {
    res.status(200).json({ ok: true, service: 'jeho-own-api' });
  });

  await app.listen(port);
  const gateway = app.get(MikooGatewayService);
  gateway.attach(app.getHttpServer());
  // eslint-disable-next-line no-console
  console.log(`JEHO CHAT API running on http://localhost:${port}/${apiPrefix}`);
  // eslint-disable-next-line no-console
  console.log(`Swagger docs at http://localhost:${port}/api/docs`);
}

bootstrap();
