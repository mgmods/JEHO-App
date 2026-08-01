import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { existsSync, unlinkSync } from 'fs';
import { basename, join } from 'path';

/**
 * Deletes uploaded media files that belong to this server's /uploads tree.
 * Ignores remote URLs and /assets/* catalog files (shared theme assets).
 */
@Injectable()
export class MediaCleanupService {
  private readonly logger = new Logger(MediaCleanupService.name);
  private readonly uploadDir: string;

  constructor(private readonly config: ConfigService) {
    this.uploadDir = this.config.get<string>('app.uploadDir') || './uploads';
  }

  /** Extract filename from `/uploads/xyz.ext` or absolute URL ending in /uploads/. */
  filenameFromUrl(urlOrPath?: string | null): string | null {
    if (!urlOrPath || typeof urlOrPath !== 'string') return null;
    const clean = urlOrPath.split('?')[0].trim();
    if (!clean) return null;
    const marker = '/uploads/';
    const idx = clean.lastIndexOf(marker);
    if (idx < 0) return null;
    const name = clean.slice(idx + marker.length);
    if (!name || name.includes('..') || name.includes('/') || name.includes('\\')) {
      return null;
    }
    return name;
  }

  deleteUploadUrl(urlOrPath?: string | null): boolean {
    const name = this.filenameFromUrl(urlOrPath);
    if (!name) return false;
    const filePath = join(this.uploadDir, name);
    if (!existsSync(filePath)) return false;
    try {
      unlinkSync(filePath);
      return true;
    } catch (err) {
      this.logger.warn(`Failed to delete ${name}: ${(err as Error).message}`);
      return false;
    }
  }

  /** Replace: delete previous upload if the new URL is different. */
  replaceUpload(oldUrl?: string | null, newUrl?: string | null): void {
    const oldName = this.filenameFromUrl(oldUrl);
    const newName = this.filenameFromUrl(newUrl);
    if (!oldName) return;
    if (newName && oldName === newName) return;
    this.deleteUploadUrl(oldUrl);
  }

  deletePublicAssetPath(assetPath?: string | null): boolean {
    if (!assetPath || typeof assetPath !== 'string') return false;
    const clean = assetPath.split('?')[0].trim();
    // Only allow /assets/... under public/assets — not arbitrary paths.
    if (!clean.startsWith('/assets/')) return false;
    const relative = clean.replace(/^\//, '');
    if (relative.includes('..')) return false;
    const cwd = process.cwd();
    const candidates = [
      join(cwd, 'public', relative),
      join(cwd, relative),
    ];
    for (const filePath of candidates) {
      if (existsSync(filePath)) {
        try {
          unlinkSync(filePath);
          return true;
        } catch (err) {
          this.logger.warn(`Failed to delete asset ${filePath}: ${(err as Error).message}`);
        }
      }
    }
    return false;
  }

  safeBasename(pathOrUrl?: string | null): string | null {
    if (!pathOrUrl) return null;
    try {
      return basename(pathOrUrl.split('?')[0]);
    } catch {
      return null;
    }
  }
}
