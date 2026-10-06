import {
  Injectable,
  BadRequestException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { extname, join } from 'path';
import { existsSync, mkdirSync, readFileSync, unlinkSync } from 'fs';
import { v4 as uuidv4 } from 'uuid';
import { ContentModerationService } from '../moderation/content-moderation.service';

@Injectable()
export class UploadsService {
  private readonly uploadDir: string;
  private readonly maxSizeMb: number;
  private readonly supabaseUrl: string;
  private readonly storageKey: string;
  private readonly storageBucket: string;

  constructor(
    private readonly configService: ConfigService,
    private readonly moderation: ContentModerationService,
  ) {
    this.uploadDir = this.configService.get<string>('app.uploadDir') || './uploads';
    this.maxSizeMb = this.configService.get<number>('app.uploadMaxSizeMb') || 40;
    const rawSupabaseUrl = process.env.SUPABASE_URL?.trim() || 'https://nxptedmacsdqnehcatpi.supabase.co';
    this.supabaseUrl = rawSupabaseUrl.endsWith('/') ? rawSupabaseUrl.slice(0, -1) : rawSupabaseUrl;
    // Server-only Storage credential. Prefer the explicit service-role variable,
    // while keeping the existing SUPABASE_STORAGE_KEY name for compatibility.
    this.storageKey =
      // Prefer the legacy service-role credential when it is configured because
      // Supabase Storage admin endpoints still support it directly.
      process.env.SUPABASE_SERVICE_ROLE_KEY?.trim() ||
      process.env.SUPABASE_STORAGE_KEY?.trim() ||
      process.env.SUPABASE_SECRET_KEY?.trim() ||
      '';
    this.storageBucket = process.env.SUPABASE_STORAGE_BUCKET || 'jeho-own-uploads';
    const keySource =
      process.env.SUPABASE_SECRET_KEY?.trim()
        ? 'secret'
        : process.env.SUPABASE_SERVICE_ROLE_KEY?.trim()
          ? 'service_role'
          : process.env.SUPABASE_STORAGE_KEY?.trim()
            ? 'storage_key'
            : 'missing';
    console.log(
      `[Uploads] Supabase Storage ${this.supabaseUrl && this.storageKey ? 'configured' : 'NOT configured'}; bucket=${this.storageBucket}; key=${keySource}`,
    );
    if (!existsSync(this.uploadDir)) {
      mkdirSync(this.uploadDir, { recursive: true });
    }
  }

  getMulterOptions() {
    return {
      dest: this.uploadDir,
      limits: { fileSize: this.maxSizeMb * 1024 * 1024 },
      fileFilter: (
        _req: unknown,
        file: Express.Multer.File,
        cb: (error: Error | null, accept: boolean) => void,
      ) => {
        const allowed = /\.(jpg|jpeg|png|gif|webp|svg|mp4|mp3|m4a|aac|wav|pdf|mov|webm|json)$/i;
        if (!allowed.test(extname(file.originalname))) {
          return cb(new BadRequestException('File type not allowed') as any, false);
        }
        cb(null, true);
      },
    };
  }

  buildPublicPath(filename: string) {
    return `/uploads/${filename}`;
  }

  private storageEnabled() {
    return Boolean(this.supabaseUrl && this.storageKey);
  }

  /**
   * Supabase now supports both legacy JWT service-role keys and the newer
   * sb_secret_* server keys. The latter are API keys, not JWTs, so sending
   * them as "Authorization: Bearer ..." makes Storage return:
   * "Invalid Compact JWS". Always send apikey; only send Bearer for a JWT key.
   */
  private storageHeaders(extra: Record<string, string> = {}) {
    const headers: Record<string, string> = {
      apikey: this.storageKey,
      ...extra,
    };
    const parts = this.storageKey.split('.');
    if (parts.length === 3 && parts.every((part) => part.length > 0)) {
      headers.Authorization = 'Bearer ' + this.storageKey;
    }
    return headers;
  }

  private storageObjectUrl(filename: string) {
    return `${this.supabaseUrl}/storage/v1/object/${encodeURIComponent(this.storageBucket)}/${encodeURIComponent(filename)}`;
  }

  private async storageFetch(
    url: string,
    init: RequestInit,
    timeoutMs = 20_000,
  ): Promise<Response> {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    try {
      return await fetch(url, { ...init, signal: controller.signal });
    } catch (err) {
      if (controller.signal.aborted) {
        throw new BadRequestException('Supabase Storage timed out while uploading the image');
      }
      throw err;
    } finally {
      clearTimeout(timer);
    }
  }

  async getStoredFile(filename: string): Promise<{ buffer: Buffer; contentType: string } | null> {
    if (!this.storageEnabled()) return null;
    this.assertSafeFilename(filename);
    // The production upload bucket is public, so reads must use the
    // public object endpoint. This avoids requiring a user/service
    // Authorization header just to display an uploaded image.
    const publicUrl =
      `${this.supabaseUrl}/storage/v1/object/public/${encodeURIComponent(this.storageBucket)}/${encodeURIComponent(filename)}`;
    const response = await this.storageFetch(publicUrl, {
      headers: { Accept: 'application/octet-stream' },
    });
    if (response.status === 404) return null;
    if (!response.ok) throw new Error(`Supabase Storage read failed (${response.status})`);
    return {
      buffer: Buffer.from(await response.arrayBuffer()),
      contentType: response.headers.get('content-type') || 'application/octet-stream',
    };
  }

  private assertSafeFilename(filename: string) {
    if (!filename || filename.includes('..') || filename.includes('/') || filename.includes(String.fromCharCode(92))) {
      throw new BadRequestException('Invalid filename');
    }
  }

  async processUploaded(file: Express.Multer.File, userId?: string): Promise<{
    originalName: string;
    filename: string;
    mimeType: string;
    size: number;
    url: string;
    path: string;
  }> {
    if (!file) throw new BadRequestException('No file uploaded');
    const ext = extname(file.originalname).toLowerCase();
    const storedName = file.filename || `${uuidv4()}${ext}`;
    const storedPath = file.path || join(this.uploadDir, storedName);
    if (
      ['.mp3', '.m4a', '.aac', '.wav'].includes(ext) &&
      !this.hasAudioSignature(storedPath, ext)
    ) {
      if (existsSync(storedPath)) unlinkSync(storedPath);
      throw new BadRequestException('Invalid or unsupported audio file');
    }
    try {
      await this.moderation.assertCleanImageFile(storedPath, file.mimetype);
    } catch (err) {
      if (userId) {
        try {
          await this.moderation.recordNsfwStrike(userId);
        } catch {
          /* ignore strike errors */
        }
      }
      throw err;
    }
    if (this.storageEnabled()) {
      const uploadHeaders = this.storageHeaders({
        'Content-Type': file.mimetype || 'application/octet-stream',
        'Cache-Control': '3600',
        'x-upsert': 'true',
      });
      const fileBuffer = readFileSync(storedPath);
      // Use one bounded PUT. The old PUT→POST→bucket-check→POST chain could
      // keep a dashboard upload open until Render returned 502.
      let upload = await this.storageFetch(this.storageObjectUrl(storedName), {
        method: 'PUT',
        headers: uploadHeaders,
        body: fileBuffer,
      });

      // The production bucket is provisioned in Supabase and must not be
      // checked/created through the Storage admin API during every upload.
      // That admin endpoint requires a different authorization flow and was
      // the source of the misleading "headers must have required property
      // authorization" error. A missing bucket is a deployment/config error,
      // not something to repair inside a user upload request.

      if (!upload.ok) {
        const detail = await this.safeResponseText(upload);
        console.error(
          `[Uploads] Supabase object upload failed status=${upload.status} bucket=${this.storageBucket} file=${storedName} detail=${detail}`,
        );
        if (existsSync(storedPath)) unlinkSync(storedPath);
        throw new BadRequestException(
          `Supabase Storage upload failed (${upload.status}): ${detail}`,
        );
      }
      if (existsSync(storedPath)) unlinkSync(storedPath);
    }
    return {
      originalName: file.originalname,
      filename: storedName,
      mimeType: file.mimetype,
      size: file.size,
      // Serve uploads through our API because the configured Supabase bucket is private.
      // This keeps dashboard previews and Android clients working without /object/public.
      url: this.buildPublicPath(storedName),
      path: this.buildPublicPath(storedName),
    };
  }

  private async deleteStoredFile(filename: string) {
    const response = await this.storageFetch(this.storageObjectUrl(filename), {
      method: 'DELETE',
      headers: this.storageHeaders(),
    });
    if (!response.ok && response.status !== 404) {
      const detail = await this.safeResponseText(response);
      throw new BadRequestException(
        `Supabase Storage delete failed (${response.status}): ${detail}`,
      );
    }
    return { deleted: response.ok };
  }

  private async safeResponseText(response: Response): Promise<string> {
    try {
      const text = (await response.text()).replace(/\s+/g, ' ').trim();
      return text ? text.slice(0, 500) : 'no response body';
    } catch {
      return 'no response body';
    }
  }

  private hasAudioSignature(path: string, ext: string) {
    try {
      const bytes = readFileSync(path).subarray(0, 16);
      if (ext === '.wav') {
        return (
          bytes.subarray(0, 4).toString('ascii') === 'RIFF' &&
          bytes.subarray(8, 12).toString('ascii') === 'WAVE'
        );
      }
      if (ext === '.m4a') {
        return bytes.subarray(4, 8).toString('ascii') === 'ftyp';
      }
      if (ext === '.aac') {
        return bytes.length >= 2 && bytes[0] === 0xff && (bytes[1] & 0xf6) === 0xf0;
      }
      return (
        bytes.subarray(0, 3).toString('ascii') === 'ID3' ||
        (bytes.length >= 2 && bytes[0] === 0xff && (bytes[1] & 0xe0) === 0xe0)
      );
    } catch {
      return false;
    }
  }

  async processMany(files: Express.Multer.File[], userId?: string) {
    const out: Array<{
      originalName: string;
      filename: string;
      mimeType: string;
      size: number;
      url: string;
      path: string;
    }> = [];
    for (const f of files || []) {
      out.push(await this.processUploaded(f as Express.Multer.File, userId));
    }
    return out;
  }

  deleteFilename(filename: string) {
    if (
      !filename ||
      filename.includes('..') ||
      filename.includes('/') ||
      filename.includes('\\')
    ) {
      throw new BadRequestException('Invalid filename');
    }
    this.assertSafeFilename(filename);
    if (this.storageEnabled()) {
      // Supabase Storage accepts DELETE on a single object path.
      return this.deleteStoredFile(filename);
    }
    const filePath = join(this.uploadDir, filename);
    if (!existsSync(filePath)) {
      return { deleted: false };
    }
    unlinkSync(filePath);
    return { deleted: true };
  }
}
