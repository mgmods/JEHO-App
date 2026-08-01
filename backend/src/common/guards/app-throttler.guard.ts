import { ExecutionContext, Injectable } from '@nestjs/common';
import { ThrottlerGuard, ThrottlerLimitDetail } from '@nestjs/throttler';
import { Request } from 'express';

/**
 * Track limits per authenticated user when possible (Saudi CGNAT shares IPs).
 * Falls back to client IP for anonymous routes.
 */
@Injectable()
export class AppThrottlerGuard extends ThrottlerGuard {
  protected async getTracker(req: Record<string, any>): Promise<string> {
    const userId = req?.user?.sub;
    if (typeof userId === 'string' && userId.length > 0) {
      return `user:${userId}`;
    }
    const ip =
      req?.clientIp ||
      String(req?.headers?.['x-forwarded-for'] || '')
        .split(',')[0]
        .trim() ||
      req?.headers?.['x-real-ip'] ||
      req?.ip ||
      req?.socket?.remoteAddress ||
      'unknown';
    return `ip:${String(ip).replace(/^::ffff:/, '')}`;
  }

  protected async getErrorMessage(
    context: ExecutionContext,
    throttlerLimitDetail: ThrottlerLimitDetail,
  ): Promise<string> {
    void context;
    void throttlerLimitDetail;
    return 'عدد كبير جداً من الطلبات، انتظر لحظة ثم أعد المحاولة';
  }
}
