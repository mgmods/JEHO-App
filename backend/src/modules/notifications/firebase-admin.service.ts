import { Injectable, Logger, OnModuleInit } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import * as fs from 'fs';
import * as path from 'path';

type FirebaseAdmin = typeof import('firebase-admin');

@Injectable()
export class FirebaseAdminService implements OnModuleInit {
  private readonly logger = new Logger(FirebaseAdminService.name);
  private ready = false;
  private admin: FirebaseAdmin | null = null;
  private projectId: string | null = null;
  private initFailure: string | null = null;

  constructor(private readonly config: ConfigService) {}

  onModuleInit() {
    this.init();
  }

  private init() {
    try {
      // eslint-disable-next-line @typescript-eslint/no-var-requires
      this.admin = require('firebase-admin') as FirebaseAdmin;
      if (this.admin.apps.length) {
        this.projectId = String(this.admin.app().options.projectId || '') || null;
        this.ready = true;
        return;
      }
      const configured =
        this.config.get<string>('app.fcm.credentialsPath') ||
        process.env.FIREBASE_CREDENTIALS_PATH ||
        process.env.GOOGLE_APPLICATION_CREDENTIALS ||
        '';
      const candidates = [
        configured,
        path.join(process.cwd(), 'secrets', 'firebase-adminsdk.json'),
        path.join(process.cwd(), 'firebase-adminsdk.json'),
        path.join(__dirname, '..', '..', '..', 'secrets', 'firebase-adminsdk.json'),
      ].filter(Boolean);

      const file = candidates.find((p) => p && fs.existsSync(p));
      const rawJson = (this.config.get<string>('FIREBASE_SERVICE_ACCOUNT_JSON') ||
        process.env.FIREBASE_SERVICE_ACCOUNT_JSON ||
        '').trim();
      const rawBase64 = (this.config.get<string>('FIREBASE_SERVICE_ACCOUNT_JSON_BASE64') ||
        process.env.FIREBASE_SERVICE_ACCOUNT_JSON_BASE64 ||
        '').trim();

      // Render/server deployments can keep the Firebase Admin key as a secret
      // environment variable instead of mounting a credentials file.
      let serviceAccount: Record<string, unknown> | null = null;
      if (rawJson || rawBase64) {
        try {
          const json = rawJson || Buffer.from(rawBase64, 'base64').toString('utf8');
          serviceAccount = JSON.parse(json) as Record<string, unknown>;
        } catch {
          this.initFailure = 'invalid_service_account_json';
          this.logger.error('Firebase service-account JSON/BASE64 is invalid — push disabled');
          return;
        }
      } else if (file) {
        serviceAccount = JSON.parse(fs.readFileSync(file, 'utf8')) as Record<string, unknown>;
      }

      if (!serviceAccount) {
        this.initFailure = 'credentials_missing';
        this.logger.error('Firebase credentials not found (FIREBASE_SERVICE_ACCOUNT_JSON, FIREBASE_SERVICE_ACCOUNT_JSON_BASE64, or FIREBASE_CREDENTIALS_PATH / GOOGLE_APPLICATION_CREDENTIALS) — push disabled');
        return;
      }
      const projectId = String(serviceAccount.project_id || '').trim();
      const clientEmail = String(serviceAccount.client_email || '').trim();
      const privateKey = String(serviceAccount.private_key || '').replace(/\\n/g, '\n').trim();
      if (!projectId || !clientEmail || !privateKey) {
        this.initFailure = 'service_account_fields_missing';
        this.logger.error('Firebase service-account is missing project_id, client_email, or private_key — push disabled');
        return;
      }
      const expectedProjectId = String(
        this.config.get<string>('app.fcm.projectId') ||
        process.env.FIREBASE_PROJECT_ID ||
        'jeho-fe429',
      ).trim();
      if (expectedProjectId && projectId !== expectedProjectId) {
        this.projectId = projectId;
        this.initFailure = 'firebase_project_mismatch';
        this.logger.error(`Firebase project mismatch: app expects ${expectedProjectId}, service account is ${projectId} — push disabled`);
        return;
      }
      serviceAccount.private_key = privateKey;

      this.admin.initializeApp({
        credential: this.admin.credential.cert(serviceAccount as any),
      });
      this.ready = true;
      this.projectId = projectId;
      this.initFailure = null;
      this.logger.log(`Firebase Admin ready (project=${projectId}, serviceAccount=${clientEmail})`);
    } catch (err) {
      this.initFailure = (err as Error).message || 'firebase_admin_init_failed';
      this.logger.error(`Firebase Admin init failed: ${this.initFailure}`);
      this.ready = false;
    }
  }

  isReady() {
    return this.ready && !!this.admin;
  }

  diagnostics() {
    const expectedProjectId = String(
      this.config.get<string>('app.fcm.projectId') ||
      process.env.FIREBASE_PROJECT_ID ||
      'jeho-fe429',
    ).trim();
    return {
      ready: this.isReady(),
      projectId: this.projectId,
      expectedProjectId,
      projectMatches: !!this.projectId && this.projectId === expectedProjectId,
      failureReason: this.initFailure,
    };
  }

  async sendToTokens(
    tokens: string[],
    title: string,
    body: string,
    data?: Record<string, unknown>,
  ): Promise<{ success: number; failure: number; invalidTokens: string[] }> {
    if (!this.isReady()) {
      this.logger.error('FCM send skipped: Firebase Admin is not initialized');
      return { success: 0, failure: tokens.length, invalidTokens: [] };
    }
    if (!tokens.length) return { success: 0, failure: 0, invalidTokens: [] };
    const stringData: Record<string, string> = {};
    if (data) {
      for (const [k, v] of Object.entries(data)) {
        if (v == null) continue;
        stringData[k] = typeof v === 'string' ? v : JSON.stringify(v);
      }
    }
    stringData.title = stringData.title || title;
    stringData.body = stringData.body || body;

    try {
      // Android: data-only so AuraMessagingService always runs and can render
      // custom RemoteViews (avatar + name + message). A top-level `notification`
      // payload would be drawn by the system (and MIUI) and skip custom layouts.
      // iOS still gets an APNs alert.
      const res = await this.admin!.messaging().sendEachForMulticast({
        tokens,
        data: stringData,
        android: {
          priority: 'high',
        },
        apns: {
          payload: {
            aps: {
              alert: { title, body },
              sound: 'default',
              contentAvailable: true,
            },
          },
        },
      });
      const invalidTokens: string[] = [];
      res.responses.forEach((item, index) => {
        if (item.success) return;
        const code = item.error?.code || 'unknown';
        this.logger.warn(`FCM token delivery failed index=${index} code=${code}: ${item.error?.message || 'unknown error'}`);
        if (code === 'messaging/registration-token-not-registered'
            || code === 'messaging/invalid-registration-token') {
          invalidTokens.push(tokens[index]);
        }
      });
      this.logger.log(`FCM multicast result success=${res.successCount} failure=${res.failureCount} tokens=${tokens.length}`);
      return { success: res.successCount, failure: res.failureCount, invalidTokens };
    } catch (err) {
      this.logger.error(`FCM multicast failed: ${(err as Error).message}`);
      return { success: 0, failure: tokens.length, invalidTokens: [] };
    }
  }
}
