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

  constructor(private readonly config: ConfigService) {}

  onModuleInit() {
    this.init();
  }

  private init() {
    try {
      // eslint-disable-next-line @typescript-eslint/no-var-requires
      this.admin = require('firebase-admin') as FirebaseAdmin;
      if (this.admin.apps.length) {
        this.ready = true;
        return;
      }
      const configured =
        this.config.get<string>('app.fcm.credentialsPath') ||
        process.env.FIREBASE_CREDENTIALS_PATH ||
        '';
      const candidates = [
        configured,
        path.join(process.cwd(), 'secrets', 'firebase-adminsdk.json'),
        path.join(process.cwd(), 'firebase-adminsdk.json'),
        path.join(__dirname, '..', '..', '..', 'secrets', 'firebase-adminsdk.json'),
      ].filter(Boolean);

      const file = candidates.find((p) => p && fs.existsSync(p));
      if (!file) {
        this.logger.warn('Firebase credentials not found — push disabled');
        return;
      }
      const serviceAccount = JSON.parse(fs.readFileSync(file, 'utf8'));
      this.admin.initializeApp({
        credential: this.admin.credential.cert(serviceAccount),
      });
      this.ready = true;
      this.logger.log(`Firebase Admin ready (${serviceAccount.project_id})`);
    } catch (err) {
      this.logger.warn(`Firebase Admin init failed: ${(err as Error).message}`);
      this.ready = false;
    }
  }

  isReady() {
    return this.ready && !!this.admin;
  }

  async sendToTokens(
    tokens: string[],
    title: string,
    body: string,
    data?: Record<string, unknown>,
  ): Promise<{ success: number; failure: number }> {
    if (!this.isReady() || !tokens.length) return { success: 0, failure: 0 };
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
      return { success: res.successCount, failure: res.failureCount };
    } catch (err) {
      this.logger.warn(`FCM multicast failed: ${(err as Error).message}`);
      return { success: 0, failure: tokens.length };
    }
  }
}
