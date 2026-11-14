import {

  BadRequestException,

  Injectable,

  NotFoundException,

} from '@nestjs/common';

import { InjectRepository } from '@nestjs/typeorm';

import { Repository } from 'typeorm';

import { User, Gender } from '../../database/entities/user.entity';

import {

  FemaleIdentityVerification,

  GenderVerificationStatus,

} from '../../database/entities/female-identity-verification.entity';

import { AppSetting } from '../../database/entities/app-setting.entity';

import { SubmitGenderVerificationDto } from './dto/users.dto';



export type GenderVerificationState = 'none' | 'pending' | 'approved' | 'rejected';



@Injectable()

export class IdentityVerificationService {

  constructor(

    @InjectRepository(User) private readonly usersRepo: Repository<User>,

    @InjectRepository(FemaleIdentityVerification)

    private readonly verificationsRepo: Repository<FemaleIdentityVerification>,

    @InjectRepository(AppSetting) private readonly settingsRepo: Repository<AppSetting>,

  ) {}



  private async setting(key: string): Promise<string | null> {

    const row = await this.settingsRepo.findOne({ where: { key } });

    return row?.value ?? null;

  }



  private async isAutoAcceptEnabled(): Promise<boolean> {

    const v = await this.setting('gender_verification.auto_accept');

    return v === 'true' || v === '1';

  }



  private async livenessThreshold(): Promise<number> {

    const raw = await this.setting('gender_verification.liveness_threshold');

    const n = raw != null ? Number(raw) : 0.92;

    return Number.isFinite(n) ? Math.min(1, Math.max(0.85, n)) : 0.92;

  }

  private async isFemaleOnlyHostsEnabled(): Promise<boolean> {

    const v = await this.setting('features.female_only_voice_hosts');

    return v === 'true' || v === '1';

  }

  private ageYears(birthday: Date | string | null | undefined): number | null {

    if (!birthday) return null;

    const b = new Date(birthday);

    if (Number.isNaN(b.getTime())) return null;

    const now = new Date();

    let age = now.getFullYear() - b.getFullYear();

    const m = now.getMonth() - b.getMonth();

    if (m < 0 || (m === 0 && now.getDate() < b.getDate())) age--;

    return age;

  }



  async latestForUser(userId: string): Promise<FemaleIdentityVerification | null> {

    return this.verificationsRepo.findOne({

      where: { userId },

      order: { createdAt: 'DESC' },

    });

  }



  statusFrom(user: User, latest: FemaleIdentityVerification | null): GenderVerificationState {

    if (user.genderVerified) return 'approved';

    if (!latest) return 'none';

    return latest.status as GenderVerificationState;

  }



  async getStatus(userId: string) {

    const user = await this.usersRepo.findOne({ where: { id: userId } });

    if (!user) throw new NotFoundException('User not found');

    const featureEnabled = await this.isFemaleOnlyHostsEnabled();

    const latest = await this.latestForUser(userId);

    if (!featureEnabled) {

      return {

        genderVerified: !!user.genderVerified,

        genderVerificationStatus: user.genderVerified ? 'approved' : 'none',

        featureEnabled: false,

        selfieUrl: latest?.selfieUrl ?? null,

        reviewNote: latest?.reviewNote ?? null,

        livenessScore: latest?.livenessScore ?? null,

        livenessPassed: !!latest?.livenessPassed,

        decisionMode: latest?.decisionMode ?? null,

        submittedAt: latest?.createdAt ?? null,

        reviewedAt: latest?.reviewedAt ?? null,

      };

    }

    return {

      genderVerified: !!user.genderVerified,

      genderVerificationStatus: this.statusFrom(user, latest),

      featureEnabled: true,

      selfieUrl: latest?.selfieUrl ?? null,

      reviewNote: latest?.reviewNote ?? null,

      livenessScore: latest?.livenessScore ?? null,

      livenessPassed: !!latest?.livenessPassed,

      decisionMode: latest?.decisionMode ?? null,

      submittedAt: latest?.createdAt ?? null,

      reviewedAt: latest?.reviewedAt ?? null,

    };

  }



  async submit(userId: string, dto: SubmitGenderVerificationDto) {

    if (!(await this.isFemaleOnlyHostsEnabled())) {

      throw new BadRequestException('Hostess identity verification is disabled');

    }

    const url = (dto.selfieUrl || '').trim();

    if (!url) throw new BadRequestException('Selfie image is required');



    const livenessPassed = !!dto.livenessPassed;
    const livenessScore = dto.livenessScore ?? 0;
    const blinkPassed = dto.blinkPassed !== false; // require unless explicitly false
    const trackingStable = dto.faceTrackingStable !== false;
    const femaleConfidence = Number(dto.femaleConfidence ?? 0);

    if (!livenessPassed || livenessScore < 0.5) {
      throw new BadRequestException(
        'فشل التحقق الحي — وجّهي وجهك للمنتصف ثم اغمضي عيناً ثم يميناً ثم يساراً',
      );
    }
    if (!blinkPassed) {
      throw new BadRequestException(
        'فشل فحص الحيوية — اغمضي عيناً ثم افتحيها (صور الهاتف/الورقية مرفوضة)',
      );
    }
    if (!trackingStable) {
      throw new BadRequestException('فشل تتبع الوجه — أبقي نفس الوجه أمام الكاميرا طوال الفحص');
    }

    const user = await this.usersRepo.findOne({ where: { id: userId } });
    if (!user) throw new NotFoundException('User not found');
    if (String(user.gender || '').toLowerCase() !== Gender.FEMALE) {
      throw new BadRequestException('التحقق من الهوية متاح للمضيفات (أنثى) فقط');
    }
    const age = this.ageYears(user.birthday);
    if (age != null && age < 18) {
      throw new BadRequestException('التحقق من الهوية متاح للبالغين (18+) فقط');
    }

    const autoAccept = await this.isAutoAcceptEnabled();
    const threshold = await this.livenessThreshold();
    // Auto-approve only with strong live proof + female confidence (anti photo-of-photo).
    const canAutoApprove =
      autoAccept &&
      livenessPassed &&
      blinkPassed &&
      trackingStable &&
      livenessScore >= threshold &&
      femaleConfidence >= 0.92 &&
      (age == null || age >= 18);



    let row = await this.verificationsRepo.findOne({

      where: { userId, status: GenderVerificationStatus.PENDING },

    });



    const payload: Partial<FemaleIdentityVerification> = {

      selfieUrl: url,

      livenessScore,

      livenessPassed,

      decisionMode: canAutoApprove ? 'auto' : 'manual',

    };



    if (row) {

      Object.assign(row, payload);

    } else {

      row = this.verificationsRepo.create({

        userId,

        ...payload,

        status: GenderVerificationStatus.PENDING,

      });

    }



    if (canAutoApprove) {

      row.status = GenderVerificationStatus.APPROVED;

      row.reviewedAt = new Date();

      row.reviewNote = 'موافقة تلقائية — فحص حيّ (وميض+التفات) ناجح · أنثى بالغة';

      user.genderVerified = true;

    } else {

      row.status = GenderVerificationStatus.PENDING;

      row.reviewedAt = null;

      row.reviewedBy = null;

      row.reviewNote = null;

      user.genderVerified = false;

    }



    await this.verificationsRepo.save(row);

    await this.usersRepo.save(user);

    return this.getStatus(userId);

  }



  async adminList(status?: GenderVerificationStatus) {

    const where = status ? { status } : {};

    const items = await this.verificationsRepo.find({

      where,

      relations: ['user', 'user.profile'],

      order: { createdAt: 'DESC' },

      take: 200,

    });

    return items.map((row) => ({

      id: row.id,

      userId: row.userId,

      selfieUrl: row.selfieUrl,

      status: row.status,

      livenessScore: row.livenessScore,

      livenessPassed: row.livenessPassed,

      decisionMode: row.decisionMode,

      reviewNote: row.reviewNote,

      reviewedBy: row.reviewedBy,

      reviewedAt: row.reviewedAt,

      createdAt: row.createdAt,

      user: row.user

        ? {

            id: row.user.id,

            username: row.user.username,

            displayName: row.user.displayName,

            avatarUrl: row.user.avatarUrl,

            gender: row.user.gender,

            genderVerified: row.user.genderVerified,

            country: row.user.profile?.country ?? null,

          }

        : null,

    }));

  }



  async adminReview(

    id: string,

    action: 'approve' | 'reject',

    adminId: string,

    note?: string,

  ) {

    const row = await this.verificationsRepo.findOne({

      where: { id },

      relations: ['user'],

    });

    if (!row) throw new NotFoundException('Verification not found');

    // Allow reject anytime (even after approve); approve only from pending.
    if (action === 'approve' && row.status !== GenderVerificationStatus.PENDING) {
      throw new BadRequestException('يمكن الموافقة فقط على الطلبات المعلّقة');
    }
    if (
      action === 'reject' &&
      row.status !== GenderVerificationStatus.PENDING &&
      row.status !== GenderVerificationStatus.APPROVED
    ) {
      throw new BadRequestException('لا يمكن رفض هذا الطلب');
    }



    const user = row.user ?? (await this.usersRepo.findOne({ where: { id: row.userId } }));

    if (!user) throw new NotFoundException('User not found');



    row.reviewedBy = adminId;

    row.reviewedAt = new Date();

    row.reviewNote = note?.trim() || null;

    row.decisionMode = 'manual';



    if (action === 'approve') {

      if (String(user.gender || '').toLowerCase() !== Gender.FEMALE) {

        throw new BadRequestException('Cannot approve: user is not registered as female');

      }

      row.status = GenderVerificationStatus.APPROVED;

      user.genderVerified = true;

    } else {

      row.status = GenderVerificationStatus.REJECTED;

      user.genderVerified = false;

    }



    await this.verificationsRepo.save(row);

    await this.usersRepo.save(user);



    return {

      id: row.id,

      status: row.status,

      userId: row.userId,

      genderVerified: user.genderVerified,

    };

  }

}


