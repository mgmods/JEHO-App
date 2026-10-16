import {
  Injectable,
  Logger,
  OnModuleInit,
  BadRequestException,
  ServiceUnavailableException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { extname, join } from 'path';
import { existsSync, mkdirSync, readFileSync, readdirSync, statSync, unlinkSync } from 'fs';
import { v4 as uuidv4 } from 'uuid';
import { ContentModerationService } from '../moderation/content-moderation.service';

@Injectable()
export class UploadsService implements OnModuleInit {
  private readonly logger = new Logger(UploadsService.name);
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
    const rawSupabaseUrl = process.env.SUPABASE_URL?.trim() || ''; // Never fall back to the legacy JEHOO CHAT storage project.
    this.supabaseUrl = rawSupabaseUrl.endsWith('/') ? rawSupabaseUrl.slice(0, -1) : rawSupabaseUrl;
    this.storageKey =
      process.env.SUPABASE_SERVICE_ROLE_KEY?.trim() ||
      process.env.SUPABASE_STORAGE_KEY?.trim() ||
      process.env.SUPABASE_SECRET_KEY?.trim() ||
      '';
    this.storageBucket = process.env.SUPABASE_STORAGE_BUCKET || 'jeho-own-uploads';
    const keySource =
      process.env.SUPABASE_SERVICE_ROLE_KEY?.trim()
        ? 'service_role'
        : process.env.SUPABASE_SECRET_KEY?.trim()
          ? 'secret'
          : process.env.SUPABASE_STORAGE_KEY?.trim()
            ? 'storage_key'
            : 'missing';
    this.logger.log(
      `Supabase Storage ${this.supabaseUrl && this.storageKey ? 'configured' : 'NOT configured'}; url=${this.supabaseUrl ? 'set' : 'missing'}; bucket=${this.storageBucket}; key=${keySource}`,
    );
    if (!existsSync(this.uploadDir)) mkdirSync(this.uploadDir, { recursive: true });
  }

  async onModuleInit() {
    if (process.env.NODE_ENV !== 'production' || !this.storageEnabled()) return;
    try {
      // Validate the actual bucket/key at startup, not just whether env vars exist.
      const bucketCheck = await this.storageFetch(
        `${this.supabaseUrl}/storage/v1/bucket/${encodeURIComponent(this.storageBucket)}`,
        { headers: this.storageHeaders({ Accept: 'application/json' }) },
      );
      if (!bucketCheck.ok) {
        const detail = await this.safeResponseText(bucketCheck);
        throw new Error(`Supabase bucket check failed (${bucketCheck.status}): ${detail}`);
      }

      // Migrate files from the container's local uploads directory before serving
      // traffic. Keep local copies as a rollback safety net; skip files already remote.
      const names = readdirSync(this.uploadDir);
      let migrated = 0;
      for (const filename of names) {
        if (!filename || filename.includes('..') || filename.includes('/') || filename.includes(String.fromCharCode(92))) continue;
        const localPath = join(this.uploadDir, filename);
        if (!statSync(localPath).isFile()) continue;
        const objectUrl = this.storageObjectUrl(filename);
        const exists = await this.storageFetch(objectUrl, { method: 'HEAD', headers: this.storageHeaders() });
        if (exists.ok) continue;
        if (exists.status !== 404) {
          throw new Error(`Supabase object check failed (${exists.status}) for ${filename}`);
        }
        const uploaded = await this.storageFetch(objectUrl, {
          method: 'POST',
          headers: this.storageHeaders({
            'Content-Type': 'application/octet-stream',
            'Cache-Control': '3600',
            'x-upsert': 'false',
          }),
          body: readFileSync(localPath),
        });
        if (!uploaded.ok) {
          const detail = await this.safeResponseText(uploaded);
          throw new Error(`Supabase migration failed (${uploaded.status}) for ${filename}: ${detail}`);
        }
        migrated += 1;
      }
      this.logger.log(`Durable storage verified; bucket=${this.storageBucket}; local files migrated=${migrated}`);
    } catch (error) {
      this.logger.error(
        `Durable storage startup verification/migration failed; uploads may fail until configuration is corrected: ${error instanceof Error ? error.message : 'unknown error'}`,
      );
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
        const allowed = /\.(jpg|jpeg|png|gif|webp|svg|svga|mp4|mp3|m4a|aac|wav|pdf|mov|webm|json)$/i;
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

  private storageHeaders(extra: Record<string, string> = {}) {
    // Legacy service-role JWTs must be sent as Bearer tokens. New Supabase
    // secret keys (sb_secret_...) are API keys, not JWTs, so sending them as
    // Authorization: Bearer causes Storage to reject them with "Invalid Compact JWS".
    const headers: Record<string, string> = {
      apikey: this.storageKey,
      ...extra,
    };
    if (this.storageKey.startsWith('eyJ')) {
      headers.Authorization = 'Bearer ' + this.storageKey;
    }
    return headers;
  }

  private storageObjectUrl(filename: string) {
    return `${this.supabaseUrl}/storage/v1/object/${encodeURIComponent(this.storageBucket)}/${encodeURIComponent(filename)}`;
  }

  private async storageFetch(url: string, init: RequestInit, timeoutMs = 20_000): Promise<Response> {
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
    this.assertSafeFilename(filename);

    // If Supabase Storage is configured, read from it first. This is the
    // durable path used by ephemeral cloud deployments.
    if (this.storageEnabled()) {
      const objectUrl = this.storageObjectUrl(filename);
      const response = await this.storageFetch(objectUrl, {
        headers: this.storageHeaders({ Accept: 'application/octet-stream' }),
      });
      // During rollout, serve an existing local copy if the object has not
      // yet been copied to Supabase. This avoids breaking links during migration.
      if (response.status !== 404) {
        if (!response.ok) throw new Error(`Supabase Storage read failed (${response.status})`);
        return {
          buffer: Buffer.from(await response.arrayBuffer()),
          contentType: response.headers.get('content-type') || 'application/octet-stream',
        };
      }
    }

    // Dashboard uploads still succeed without Supabase, but previously their
    // /uploads/:filename URLs always returned 404 because this method only
    // checked remote storage. Serve the existing local upload in that case.
    const localPath = join(this.uploadDir, filename);
    if (!existsSync(localPath)) return null;
    const ext = extname(filename).toLowerCase();
    const contentTypes: Record<string, string> = {
      '.jpg': 'image/jpeg',
      '.jpeg': 'image/jpeg',
      '.png': 'image/png',
      '.webp': 'image/webp',
      '.gif': 'image/gif',
      '.svg': 'image/svg+xml',
      '.mp4': 'video/mp4',
      '.webm': 'video/webm',
      '.mov': 'video/quicktime',
      '.svga': 'application/octet-stream',
      '.pdf': 'application/pdf',
      '.json': 'application/json',
    };
    return {
      buffer: readFileSync(localPath),
      contentType: contentTypes[ext] || 'application/octet-stream',
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
    // Production deployments may use ephemeral container filesystems. Never report
    // a successful upload when durable remote storage is not configured.
    if (process.env.NODE_ENV === 'production' && !this.storageEnabled()) {
      if (existsSync(storedPath)) unlinkSync(storedPath);
      throw new ServiceUnavailableException(
        'Durable media storage is not configured. Upload was rejected to prevent media loss after restart.',
      );
    }
    if (
      ['.mp3', '.m4a', '.aac', '.wav'].includes(ext) &&
      !this.hasAudioSignature(storedPath, ext)
    ) {
      if (existsSync(storedPath)) unlinkSync(storedPath);
      throw new BadRequestException('Invalid or unsupported audio file');
    }
    // NSFW image scanning is only valid for raster images. Running the image
    // decoder against MP4/WebM/MOV/SVGA files makes legitimate mall media uploads
    // fail (and must never count a video/SVGA upload as an NSFW strike).
    const rasterImageExtensions = new Set(['.jpg', '.jpeg', '.png', '.gif', '.webp']);
    if (rasterImageExtensions.has(ext)) {
      try {
        await this.moderation.assertCleanImageFile(storedPath, file.mimetype);
      } catch (err) {
        if (userId) {
          try {
            await this.moderation.recordNsfwStrike(userId);
          } catch {}
        }
        // Remove rejected uploads from the container filesystem.
        if (existsSync(storedPath)) unlinkSync(storedPath);
        throw err;
      }
    }
    if (this.storageEnabled()) {
      const uploadHeaders = this.storageHeaders({
        'Content-Type': file.mimetype || 'application/octet-stream',
        'Cache-Control': '3600',
        'x-upsert': 'true',
      });
      const fileBuffer = readFileSync(storedPath);
      const upload = await this.storageFetch(this.storageObjectUrl(storedName), {
        method: 'POST',
        headers: uploadHeaders,
        body: fileBuffer,
      });
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
      if (ext === '.m4a') return bytes.subarray(4, 8).toString('ascii') === 'ftyp';
      if (ext === '.aac') return bytes.length >= 2 && (bytes[0] === 0xff) && (bytes[1] & 0xf6) === 0xf0;
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
    for (const f of files || []) out.push(await this.processUploaded(f as Express.Multer.File, userId));
    return out;
  }

  deleteFilename(filename: string) {
    if (!filename || filename.includes('..') || filename.includes('/') || filename.includes('\\')) {
      throw new BadRequestException('Invalid filename');
    }
    this.assertSafeFilename(filename);
    if (this.storageEnabled()) return this.deleteStoredFile(filename);
    const filePath = join(this.uploadDir, filename);
    if (!existsSync(filePath)) return { deleted: false };
    unlinkSync(filePath);
    return { deleted: true };
  }
}
