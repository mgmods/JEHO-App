import { Injectable, Logger, ServiceUnavailableException } from '@nestjs/common';
import { AccessToken } from 'livekit-server-sdk';
import { LiveKitSettingsService } from './livekit-settings.service';

/**
 * Issues LiveKit room JWTs (self-hosted). Secrets stay server-side.
 * Free open-source media stack — no LiveKit Cloud billing required.
 */
@Injectable()
export class LiveKitTokenService {
  private readonly logger = new Logger(LiveKitTokenService.name);

  constructor(private readonly liveKitSettings: LiveKitSettingsService) {}

  async generateToken(
    userId: string,
    roomName: string,
    ttlSeconds = 3600,
    canPublish = true,
  ): Promise<{
    token: string;
    url: string;
    roomName: string;
    userId: string;
    expireAt: number;
  }> {
    const creds = await this.liveKitSettings.resolveConfig();
    if (!creds.url || !creds.apiKey || !creds.apiSecret) {
      this.logger.error('LiveKit credentials missing (url / apiKey / apiSecret)');
      throw new ServiceUnavailableException(
        'LiveKit is not configured on this server',
      );
    }

    const identity = String(userId);
    const room = String(roomName || identity);
    const effectiveTtl = Math.max(60, Math.min(ttlSeconds || 3600, 86_400));
    const now = Math.floor(Date.now() / 1000);
    const expireAt = now + effectiveTtl;

    const at = new AccessToken(creds.apiKey, creds.apiSecret, {
      identity,
      name: identity,
      ttl: effectiveTtl,
    });
    at.addGrant({
      roomJoin: true,
      room,
      canPublish,
      canSubscribe: true,
      canPublishData: true,
    });

    const token = await at.toJwt();
    return {
      token,
      url: normalizeWsUrl(creds.url),
      roomName: room,
      userId: identity,
      expireAt,
    };
  }
}

/** Ensure client gets wss:// host for LiveKit WS. */
function normalizeWsUrl(raw: string): string {
  const u = String(raw || '').trim();
  if (!u) return u;
  if (u.startsWith('ws://') || u.startsWith('wss://')) return u;
  if (u.startsWith('https://')) return `wss://${u.slice('https://'.length)}`;
  if (u.startsWith('http://')) return `ws://${u.slice('http://'.length)}`;
  return `wss://${u}`;
}
