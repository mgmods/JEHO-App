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
    const rawSupabaseUrl = process.env.SUPABASE_URL?.trim() || '';
    this.supabaseUrl = rawSupabaseUrl.endsWith('/') ? rawSupabaseUrl.slice(0, -1) : rawSupabaseUrl;
    // Server-only Storage credential. Prefer the explicit service-role variable,
    // while keeping the existing SUPABASE_STORAGE_KEY name for compatibility.
    this.storageKey =
      process.env.SUPABASE_SECRET_KEY?.trim() ||
      process.env.SUPABASE_SERVICE_ROLE_KEY?.trim() ||
      process.env.SUPABASE_STORAGE_KEY?.trim() ||
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

  private storageObjectUrl(filename: string) {
    return `${this.supabaseUrl}/storage/v1/object/${encodeURIComponent(this.storageBucket)}/${encodeURIComponent(filename)}`;
  }

  async getStoredFile(filename: string): Promise<{ buffer: Buffer; contentType: string } | null> {
    if (!this.storageEnabled()) return null;
    this.assertSafeFilename(filename);
    const response = await fetch(this.storageObjectUrl(filename), {
      headers: { apikey: this.storageKey, Authorization: `Bearer ${this.storageKey}` },
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
      const uploadHeaders = {
        apikey: this.storageKey,
        Authorization: 'Bearer ' + this.storageKey,
        'Content-Type': file.mimetype || 'application/octet-stream',
        'Cache-Control': '3600',
        'x-upsert': 'true',
      };
      const fileBuffer = readFileSync(storedPath);
      // PUT is the most reliable path for an explicitly unique object key;
      // POST remains the fallback for Storage gateways that prefer POST.
      let upload = await fetch(this.storageObjectUrl(storedName), {
        method: 'PUT',
        headers: uploadHeaders,
        body: fileBuffer,
      });

      if (!upload.ok && (upload.status === 400 || upload.status === 409)) {
        upload = await fetch(this.storageObjectUrl(storedName), {
          method: 'POST',
          headers: uploadHeaders,
          body: fileBuffer,
        });
      }

      // A fresh deployment may not have the configured bucket yet. Create it
      // once and retry instead of making profile/room image uploads fail.
      if (!upload.ok && (upload.status === 400 || upload.status === 404)) {
        await this.ensureStorageBucket();
        upload = await fetch(this.storageObjectUrl(storedName), {
          method: 'POST',
          headers: uploadHeaders,
          body: fileBuffer,
        });
      }

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

  private async ensureStorageBucket() {
    const headers = {
      apikey: this.storageKey,
      Authorization: 'Bearer ' + this.storageKey,
    };
    const bucketUrl =
      this.supabaseUrl + '/storage/v1/bucket/' + encodeURIComponent(this.storageBucket);

    // Do not blindly POST-create the bucket on every failed upload. Supabase
    // returns a 400 for some existing/misconfigured bucket states, which used
    // to turn an otherwise valid upload into "bucket setup failed".
    const existing = await fetch(bucketUrl, { headers });
    if (existing.ok) return;

    if (existing.status !== 404) {
      const detail = await this.safeResponseText(existing);
      throw new BadRequestException(
        `Supabase Storage bucket check failed (${existing.status}): ${detail}`,
      );
    }

    const response = await fetch(this.supabaseUrl + '/storage/v1/bucket', {
      method: 'POST',
      headers: { ...headers, 'Content-Type': 'application/json' },
      body: JSON.stringify({
        id: this.storageBucket,
        name: this.storageBucket,
        public: false,
      }),
    });

    // 409 means another request/deployment created it concurrently.
    if (!response.ok && response.status !== 409) {
      const detail = await this.safeResponseText(response);
      throw new BadRequestException(
        `Supabase Storage bucket setup failed (${response.status}): ${detail}`,
      );
    }
  }
  private async deleteStoredFile(filename: string) {
    const response = await fetch(this.storageObjectUrl(filename), {
      method: 'DELETE',
      headers: { apikey: this.storageKey, Authorization: `Bearer ${this.storageKey}` },
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
      out.push(await this.processUploaded(f, userId));
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
