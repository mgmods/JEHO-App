import {
  Injectable,
  ConflictException,
  UnauthorizedException,
  BadRequestException,
  Logger,
  OnModuleInit,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { JwtService } from '@nestjs/jwt';
import { ConfigService } from '@nestjs/config';
import * as bcrypt from 'bcrypt';
import { createHash, randomInt } from 'crypto';
import { v4 as uuidv4 } from 'uuid';
import { User, UserStatus } from '../../database/entities/user.entity';
import { UserProfile } from '../../database/entities/user-profile.entity';
import { Wallet } from '../../database/entities/wallet.entity';
import { OtpCode, OtpPurpose } from '../../database/entities/otp-code.entity';
import { UserVip } from '../../database/entities/user-vip.entity';
import { AppSetting } from '../../database/entities/app-setting.entity';
import {
  RegisterDto,
  LoginDto,
  SendOtpDto,
  VerifyOtpDto,
  GuestLoginDto,
  SocialLoginDto,
  ChangePasswordDto,
} from './dto/auth.dto';
import { normalizeStaffRole } from '../../common/staff-role';
@Injectable()
export class AuthService implements OnModuleInit {
  private readonly logger = new Logger(AuthService.name);

  constructor(
    @InjectRepository(User) private readonly usersRepo: Repository<User>,
    @InjectRepository(UserProfile) private readonly profilesRepo: Repository<UserProfile>,
    @InjectRepository(Wallet) private readonly walletsRepo: Repository<Wallet>,
    @InjectRepository(OtpCode) private readonly otpRepo: Repository<OtpCode>,
    @InjectRepository(UserVip) private readonly userVipsRepo: Repository<UserVip>,
    @InjectRepository(AppSetting) private readonly settingsRepo: Repository<AppSetting>,
    private readonly jwtService: JwtService,
    private readonly configService: ConfigService,
  ) {}

  async onModuleInit() {
    try {
      await this.usersRepo.query(
        `ALTER TABLE users ADD COLUMN IF NOT EXISTS "publicId" VARCHAR(16)`,
      );
      await this.usersRepo.query(
        `CREATE UNIQUE INDEX IF NOT EXISTS "idx_users_public_id" ON users("publicId")`,
      );
    } catch (err) {
      this.logger.warn(`publicId column ensure failed: ${(err as Error).message}`);
    }
    try {
      await this.ensureAllPublicIds();
    } catch (err) {
      this.logger.warn(`publicId backfill failed: ${(err as Error).message}`);
    }
  }

  async register(dto: RegisterDto) {
    const existing = await this.usersRepo.findOne({
      where: [{ email: dto.email }, { username: dto.username }],
    });
    if (existing) {
      throw new ConflictException('Email or username already registered');
    }

    const passwordHash = await bcrypt.hash(dto.password, 12);
    const user = this.usersRepo.create({
      email: dto.email.toLowerCase(),
      username: dto.username.toLowerCase(),
      publicId: await this.allocatePublicId(),
      passwordHash,
      displayName: dto.displayName || dto.username,
      emailVerified: false,
    });
    await this.usersRepo.save(user);
    await this.bootstrapUser(user);

    return this.issueTokens(user);
  }

  async login(dto: LoginDto) {
    const identifier = dto.identifier.toLowerCase();
    const user = await this.usersRepo
      .createQueryBuilder('u')
      .addSelect('u.passwordHash')
      .where(
        'LOWER(u.email) = :id OR LOWER(u.username) = :id OR u.publicId = :raw',
        { id: identifier, raw: dto.identifier.trim() },
      )
      .getOne();

    if (!user || !user.passwordHash) {
      throw new UnauthorizedException('Invalid credentials');
    }
    if (
      user.status === UserStatus.BANNED ||
      user.status === UserStatus.SUSPENDED ||
      user.status === UserStatus.DELETED
    ) {
      throw new UnauthorizedException('Account is restricted');
    }

    const ok = await bcrypt.compare(dto.password, user.passwordHash);
    if (!ok) throw new UnauthorizedException('Invalid credentials');

    user.lastOnlineAt = new Date();
    await this.usersRepo.save(user);
    return this.issueTokens(user);
  }

  async changePassword(userId: string, dto: ChangePasswordDto) {
    const user = await this.usersRepo
      .createQueryBuilder('u')
      .addSelect('u.passwordHash')
      .where('u.id = :id', { id: userId })
      .getOne();
    if (!user) throw new UnauthorizedException('User not found');
    if (!user.passwordHash) {
      throw new BadRequestException('هذا الحساب بدون كلمة مرور (سجّل عبر Google/هاتف)');
    }
    const ok = await bcrypt.compare(dto.currentPassword, user.passwordHash);
    if (!ok) throw new UnauthorizedException('كلمة المرور الحالية غير صحيحة');
    if (dto.currentPassword === dto.newPassword) {
      throw new BadRequestException('اختر كلمة مرور جديدة مختلفة');
    }
    user.passwordHash = await bcrypt.hash(dto.newPassword, 12);
    await this.usersRepo.save(user);
    return { ok: true };
  }

  private async settingValue(key: string, fallback = ''): Promise<string> {
    const row = await this.settingsRepo.findOne({ where: { key } });
    return row?.value == null ? fallback : String(row.value);
  }

  private async twilioOtpEnabled(): Promise<boolean> {
    const enabled = (await this.settingValue('twilio.verify.enabled', 'false')).trim().toLowerCase();
    const otp = (await this.settingValue('twilio.otp.enabled', 'false')).trim().toLowerCase();
    return ['true', '1', 'yes', 'on'].includes(enabled) && ['true', '1', 'yes', 'on'].includes(otp);
  }

  private async normalizeOtpPhone(phone: string): Promise<string> {
    const raw = String(phone || '').trim();
    if (raw.startsWith('+')) return raw.replace(/[^+\\d]/g, '');
    const country = String(this.configService.get('app.defaultCountryCode') || '').trim().replace(/[^\\d]/g, '');
    return country ? `+${country}${raw.replace(/\\D/g, '')}` : raw.replace(/[^\\d]/g, '');
  }

  private async twilioRequest(path: string, params: Record<string, string>) {
    const accountSid = (await this.settingValue('twilio.account_sid')).trim();
    const authToken = await this.settingValue('twilio.auth_token');
    const serviceSid = (await this.settingValue('twilio.verify_service_sid')).trim();
    if (!accountSid || !authToken || !serviceSid) {
      throw new BadRequestException('إعدادات Twilio Verify غير مكتملة في لوحة التحكم');
    }
    const body = new URLSearchParams(params);
    const response = await fetch(
      `https://verify.twilio.com/v2/Services/${encodeURIComponent(serviceSid)}${path}`,
      {
        method: 'POST',
        headers: {
          Authorization: 'Basic ' + Buffer.from(`${accountSid}:${authToken}`).toString('base64'),
          'Content-Type': 'application/x-www-form-urlencoded',
        },
        body,
      },
    );
    const data = await response.json().catch(() => ({}));
    if (!response.ok) {
      const message = String((data as any)?.message || 'Twilio Verify request failed');
      this.logger.error(`Twilio Verify error ${response.status}: ${message}`);
      throw new BadRequestException('تعذر إرسال/تحقق رمز الهاتف عبر Twilio');
    }
    return data as any;
  }

  async sendOtp(dto: SendOtpDto) {
    const phone = await this.normalizeOtpPhone(dto.phone);
    if (await this.twilioOtpEnabled()) {
      await this.twilioRequest('/Verifications', { To: phone, Channel: 'sms' });
      return { sent: true, expiresIn: 600, provider: 'twilio' };
    }

    const code = String(randomInt(100000, 999999));
    const codeHash = this.hashOtp(code);
    const expiresAt = new Date(Date.now() + 5 * 60 * 1000);

    await this.otpRepo.save(
      this.otpRepo.create({
        target: phone,
        channel: 'sms',
        purpose: OtpPurpose.LOGIN,
        codeHash,
        expiresAt,
      }),
    );

    // Local/dev fallback when Twilio Verify is disabled.
    this.logger.log(`OTP for ${phone}: ${code}`);

    return {
      sent: true,
      expiresIn: 300,
      ...(this.configService.get('app.nodeEnv') !== 'production' ? { debugCode: code } : {}),
    };
  }

  async verifyOtp(dto: VerifyOtpDto) {
    const phone = await this.normalizeOtpPhone(dto.phone);
    if (await this.twilioOtpEnabled()) {
      const result = await this.twilioRequest('/VerificationCheck', {
        To: phone,
        Code: String(dto.code).trim(),
      });
      if (String(result?.status || '').toLowerCase() !== 'approved') {
        throw new BadRequestException('رمز التحقق غير صحيح أو منتهي');
      }
    } else {
      const otp = await this.otpRepo.findOne({
        where: { target: phone, used: false, purpose: OtpPurpose.LOGIN },
        order: { createdAt: 'DESC' },
      });
      if (!otp || otp.expiresAt < new Date()) {
        throw new BadRequestException('OTP expired or not found');
      }
      if (otp.attempts >= 5) {
        throw new BadRequestException('Too many OTP attempts');
      }

      otp.attempts += 1;
      const valid = this.hashOtp(dto.code) === otp.codeHash;
      if (!valid) {
        await this.otpRepo.save(otp);
        throw new BadRequestException('Invalid OTP');
      }

      otp.used = true;
      await this.otpRepo.save(otp);
    }

    let user = await this.usersRepo.findOne({ where: { phone } });
    if (!user) {
      const username =
        dto.username?.toLowerCase() ||
        `user_${phone.replace(/\\D/g, '').slice(-8)}_${randomInt(100, 999)}`;
      user = this.usersRepo.create({
        phone,
        username,
        publicId: await this.allocatePublicId(),
        displayName: username,
        phoneVerified: true,
      });
      await this.usersRepo.save(user);
      await this.bootstrapUser(user);
    } else {
      user.phoneVerified = true;
      user.lastOnlineAt = new Date();
      await this.usersRepo.save(user);
    }

    return this.issueTokens(user);
  }

  async guestLogin(dto: GuestLoginDto) {
    const suffix = uuidv4().slice(0, 8);
    const username = `guest_${suffix}`;
    const user = this.usersRepo.create({
      username,
      publicId: await this.allocatePublicId(),
      displayName: dto.displayName || `Guest ${suffix}`,
      isGuest: true,
    });
    await this.usersRepo.save(user);
    await this.bootstrapUser(user);
    return this.issueTokens(user);
  }

  /**
   * Social login — Google ID tokens are verified with google-auth-library.
   */
  async socialLogin(dto: SocialLoginDto) {
    if (!dto.token || dto.token.length < 10) {
      throw new BadRequestException('Invalid social token');
    }

    let providerUserId = dto.providerUserId;
    let email = dto.email;
    let displayName = dto.displayName;
    let avatarUrl = dto.avatarUrl;

    if (dto.provider === 'google') {
      const verified = await this.verifyGoogleIdToken(dto.token);
      providerUserId = verified.sub;
      email = verified.email || email;
      displayName = verified.name || displayName;
      avatarUrl = verified.picture || avatarUrl;
    } else if (!providerUserId) {
      providerUserId = this.hashOtp(dto.token).slice(0, 32);
    }

    const fieldMap: Record<string, keyof User> = {
      google: 'googleId',
      facebook: 'facebookId',
      apple: 'appleId',
    };
    const field = fieldMap[dto.provider];
    if (!field) throw new BadRequestException('Unsupported provider');

    let user = await this.usersRepo.findOne({ where: { [field]: providerUserId } as any });
    if (!user && email) {
      user = await this.usersRepo.findOne({ where: { email: email.toLowerCase() } });
      if (user) {
        (user as any)[field] = providerUserId;
        if (avatarUrl && !user.avatarUrl) user.avatarUrl = avatarUrl;
        if (displayName && (!user.displayName || user.displayName.startsWith('guest_'))) {
          user.displayName = displayName;
        }
        user.emailVerified = true;
        await this.usersRepo.save(user);
      }
    }

    if (!user) {
      const base =
        (email?.split('@')[0] || `${dto.provider}_${String(providerUserId).slice(0, 8)}`)
          .toLowerCase()
          .replace(/[^a-z0-9_]/g, '')
          .slice(0, 18) || `user_${randomInt(1000, 9999)}`;
      let username = base;
      let n = 0;
      while (await this.usersRepo.findOne({ where: { username } })) {
        n += 1;
        username = `${base}${n}`.slice(0, 24);
      }
      const created = this.usersRepo.create({
        email: email?.toLowerCase() || null,
        username,
        publicId: await this.allocatePublicId(),
        displayName: displayName || username,
        avatarUrl: avatarUrl || null,
        emailVerified: !!email,
        googleId: dto.provider === 'google' ? providerUserId : null,
        facebookId: dto.provider === 'facebook' ? providerUserId : null,
        appleId: dto.provider === 'apple' ? providerUserId : null,
      });
      user = await this.usersRepo.save(created);
      await this.bootstrapUser(user);
    } else {
      if (
        user.status === UserStatus.BANNED ||
        user.status === UserStatus.SUSPENDED ||
        user.status === UserStatus.DELETED
      ) {
        throw new UnauthorizedException('Account is restricted');
      }
      user.lastOnlineAt = new Date();
      await this.usersRepo.save(user);
    }

    return this.issueTokens(user);
  }

  private async verifyGoogleIdToken(idToken: string): Promise<{
    sub: string;
    email?: string;
    name?: string;
    picture?: string;
  }> {
    try {
      // eslint-disable-next-line @typescript-eslint/no-var-requires
      const { OAuth2Client } = require('google-auth-library');
      const primary =
        this.configService.get<string>('app.google.clientId') || '';
      const extras = String(
        this.configService.get<string>('app.google.clientIds') || '',
      )
        .split(',')
        .map((s) => s.trim())
        .filter(Boolean);
      const audiences = Array.from(new Set([primary, ...extras].filter(Boolean)));
      if (!audiences.length) {
        throw new Error('GOOGLE_CLIENT_ID is not configured');
      }
      const client = new OAuth2Client(primary || audiences[0]);
      const ticket = await client.verifyIdToken({
        idToken,
        audience: audiences.length === 1 ? audiences[0] : audiences,
      });
      const payload = ticket.getPayload();
      if (!payload?.sub) throw new Error('missing sub');
      return {
        sub: payload.sub,
        email: payload.email,
        name: payload.name,
        picture: payload.picture,
      };
    } catch (err) {
      this.logger.warn(`Google token verify failed: ${(err as Error).message}`);
      throw new UnauthorizedException('Invalid Google token');
    }
  }

  async refresh(refreshToken: string) {
    try {
      const payload = await this.jwtService.verifyAsync(refreshToken, {
        secret: this.configService.get<string>('app.jwt.refreshSecret'),
      });
      const user = await this.usersRepo
        .createQueryBuilder('u')
        .addSelect('u.refreshTokenHash')
        .where('u.id = :id', { id: payload.sub })
        .getOne();
      if (!user || !user.refreshTokenHash) {
        throw new UnauthorizedException('Invalid refresh token');
      }
      const match = await bcrypt.compare(refreshToken, user.refreshTokenHash);
      if (!match) throw new UnauthorizedException('Invalid refresh token');
      return this.issueTokens(user);
    } catch {
      throw new UnauthorizedException('Invalid or expired refresh token');
    }
  }

  async logout(userId: string) {
    await this.usersRepo.update(userId, { refreshTokenHash: null });
    return { loggedOut: true };
  }

  private async bootstrapUser(user: User) {
    if (!user.publicId) {
      user.publicId = await this.allocatePublicId();
      await this.usersRepo.update(user.id, { publicId: user.publicId });
    }
    await this.profilesRepo.save(
      this.profilesRepo.create({ userId: user.id }),
    );
    await this.walletsRepo.save(
      this.walletsRepo.create({ userId: user.id, coins: 0, diamonds: 0 }),
    );
  }

  /** 8-digit numeric ID, unique & permanent (app-facing). */
  async allocatePublicId(): Promise<string> {
    for (let i = 0; i < 64; i++) {
      const id = String(randomInt(10_000_000, 99_999_999));
      const exists = await this.usersRepo.exist({ where: { publicId: id } });
      if (!exists) return id;
    }
    // Extremely unlikely fallback
    return String(Date.now()).slice(-8);
  }

  /** Backfill numeric publicId for users who still use login/store name as ID. */
  async ensureAllPublicIds(): Promise<{ updated: number }> {
    const missing = await this.usersRepo
      .createQueryBuilder('u')
      .where(`u.publicId IS NULL OR u.publicId = ''`)
      .getMany();
    let updated = 0;
    for (const u of missing) {
      u.publicId = await this.allocatePublicId();
      await this.usersRepo.save(u);
      updated += 1;
    }
    if (updated > 0) {
      this.logger.log(`Allocated publicId for ${updated} existing user(s)`);
    }
    return { updated };
  }

  private async issueTokens(user: User) {
    if (user.status !== UserStatus.ACTIVE) {
      throw new UnauthorizedException('Account is restricted');
    }
    if (!user.publicId) {
      user.publicId = await this.allocatePublicId();
      await this.usersRepo.update(user.id, { publicId: user.publicId });
    }
    const staffRole = normalizeStaffRole(user);
    const payload = {
      sub: user.id,
      username: user.username,
      publicId: user.publicId,
      isAdmin: user.isAdmin || staffRole === 'super',
      staffRole,
      role: user.isAdmin || staffRole === 'super' ? 'admin' : 'user',
      isGuest: user.isGuest,
    };

    const accessToken = await this.jwtService.signAsync(payload, {
      secret: this.configService.get<string>('app.jwt.secret'),
      expiresIn: this.configService.get<string>('app.jwt.expiresIn'),
    });

    const refreshToken = await this.jwtService.signAsync(
      { sub: user.id },
      {
        secret: this.configService.get<string>('app.jwt.refreshSecret'),
        expiresIn: this.configService.get<string>('app.jwt.refreshExpiresIn'),
      },
    );

    const refreshTokenHash = await bcrypt.hash(refreshToken, 10);
    await this.usersRepo.update(user.id, { refreshTokenHash });

    const now = new Date();
    const vip = await this.userVipsRepo.findOne({
      where: { userId: user.id, isActive: true },
      order: { level: 'DESC' },
    });
    const vipLevel =
      vip && (!vip.expiresAt || vip.expiresAt > now) ? Number(vip.level || 0) : 0;

    return {
      accessToken,
      refreshToken,
      tokenType: 'Bearer',
      user: {
        id: user.id,
        email: user.email,
        phone: user.phone,
        username: user.username,
        publicId: user.publicId,
        displayName: user.displayName,
        avatarUrl: user.avatarUrl,
        level: user.level,
        vipLevel,
        isGuest: user.isGuest,
        isAdmin: user.isAdmin || staffRole === 'super',
        staffRole,
      },
    };
  }

  private hashOtp(code: string): string {
    return createHash('sha256').update(code).digest('hex');
  }
}

