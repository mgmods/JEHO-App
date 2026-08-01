import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { generateToken04 } from './zego-server-assistant';
import { ZegoSettingsService } from './zego-settings.service';
import { createHash, randomBytes } from 'crypto';

/**
 * Issues ZEGOCLOUD Token04 using ServerSecret (server-side only).
 * Credentials prefer dashboard-encrypted settings, then environment fallbacks.
 */
@Injectable()
export class ZegoTokenService {
  private readonly logger = new Logger(ZegoTokenService.name);
  private readonly defaultTtl: number;

  constructor(
    private readonly configService: ConfigService,
    private readonly zegoSettingsService: ZegoSettingsService,
  ) {
    this.defaultTtl = this.configService.get<number>('app.zego.tokenTtl') || 3600;
  }

  async generateToken(
    userId: string,
    roomId?: string,
    ttlSeconds?: number,
    allowPublish = true,
  ): Promise<{
    token: string;
    appId: number;
    userId: string;
    roomId?: string;
    zegoRoomId?: string;
    expireAt: number;
  }> {
    const creds = await this.zegoSettingsService.resolveConfig();
    if (!creds.appId) {
      this.logger.warn('ZEGO_APP_ID not configured');
    }
    if (!creds.serverSecret || creds.serverSecret.length !== 32) {
      this.logger.warn('ZEGO_SERVER_SECRET must be the 32-char ServerSecret from console');
    }

    const effectiveTtl = ttlSeconds || this.defaultTtl;
    const now = Math.floor(Date.now() / 1000);
    const expireAt = now + effectiveTtl;

    let payload = '';
    if (roomId) {
      payload = JSON.stringify({
        room_id: String(roomId),
        privilege: {
          1: 1,
          ...(allowPublish ? { 2: 1 } : {}),
        },
        stream_id_list: allowPublish ? [`${String(userId)}_audio`] : null,
      });
    }

    const token = generateToken04(
      creds.appId,
      String(userId),
      creds.serverSecret || '',
      effectiveTtl,
      payload,
    );

    return {
      token,
      appId: creds.appId,
      userId: String(userId),
      roomId: roomId ? String(roomId) : undefined,
      zegoRoomId: roomId ? String(roomId) : undefined,
      expireAt,
      // Never send AppSign to clients — Token04 is enough for room login.
    };
  }

  async kickUser(
    roomId: string,
    userId: string,
    reason = 'Room moderation',
  ): Promise<boolean> {
    const credentials = await this.zegoSettingsService.resolveConfig();
    if (!credentials.appId || !credentials.serverSecret) {
      this.logger.warn('ZEGO moderation skipped: server credentials are unavailable');
      return false;
    }
    const timestamp = Math.floor(Date.now() / 1000);
    const nonce = randomBytes(8).toString('hex');
    const signature = createHash('md5')
      .update(
        `${credentials.appId}${nonce}${credentials.serverSecret}${timestamp}`,
      )
      .digest('hex');
    const params = new URLSearchParams({
      Action: 'KickoutUser',
      AppId: String(credentials.appId),
      SignatureNonce: nonce,
      Timestamp: String(timestamp),
      Signature: signature,
      SignatureVersion: '2.0',
      RoomId: roomId,
      CustomReason: reason.slice(0, 240),
    });
    params.append('UserId[]', userId);
    try {
      const response = await fetch(`https://rtc-api.zego.im/?${params}`, {
        signal: AbortSignal.timeout(8_000),
      });
      const result = (await response.json()) as {
        Code?: number;
        code?: number;
        Message?: string;
        message?: string;
      };
      const code = Number(result.Code ?? result.code ?? -1);
      if (response.ok && (code === 0 || code === 104 || code === 50002)) {
        return true;
      }
      this.logger.warn(
        `ZEGO KickoutUser failed room=${roomId} user=${userId} code=${code} ${
          result.Message || result.message || ''
        }`,
      );
      return false;
    } catch (error) {
      this.logger.warn(
        `ZEGO KickoutUser request failed: ${(error as Error).message}`,
      );
      return false;
    }
  }

  async getAppSign(): Promise<string> {
    const creds = await this.zegoSettingsService.resolveConfig();
    return creds.appSign || '';
  }
}
