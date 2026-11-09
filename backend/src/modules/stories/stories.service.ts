import { BadRequestException, ForbiddenException, Injectable, Logger, NotFoundException, OnModuleInit } from '@nestjs/common';
import { DataSource } from 'typeorm';

export type StoriesConfig = {
  enabled: boolean; publishingEnabled: boolean; viewingEnabled: boolean;
  imagesEnabled: boolean; videosEnabled: boolean; emergencyMode: boolean;
  cacheEnabled: boolean; durationHours: number; maxVideoSeconds: number; maxMediaMb: number;
};
const KEY = 'stories.config';
const DEFAULTS: StoriesConfig = {
  enabled: true, publishingEnabled: true, viewingEnabled: true,
  imagesEnabled: true, videosEnabled: true, emergencyMode: false,
  cacheEnabled: true, durationHours: 24, maxVideoSeconds: 30, maxMediaMb: 20,
};

@Injectable()
export class StoriesService implements OnModuleInit {
  private readonly logger = new Logger(StoriesService.name);
  constructor(private readonly db: DataSource) {}

  async onModuleInit() {
    await this.db.query(`
      CREATE TABLE IF NOT EXISTS "jeho_own"."stories" (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "userId" uuid NOT NULL REFERENCES "jeho_own"."users"(id) ON DELETE CASCADE,
        "mediaType" varchar(8) NOT NULL CHECK ("mediaType" IN ('image','video')),
        "mediaUrl" varchar(1024) NOT NULL, caption varchar(300) NOT NULL DEFAULT '',
        visibility varchar(16) NOT NULL DEFAULT 'public' CHECK (visibility IN ('public','followers','private')),
        "viewCount" integer NOT NULL DEFAULT 0, "isActive" boolean NOT NULL DEFAULT true,
        "createdAt" timestamptz NOT NULL DEFAULT now(), "expiresAt" timestamptz NOT NULL
      )`);
    await this.db.query(`
      CREATE TABLE IF NOT EXISTS "jeho_own"."story_views" (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "storyId" uuid NOT NULL REFERENCES "jeho_own"."stories"(id) ON DELETE CASCADE,
        "userId" uuid NOT NULL REFERENCES "jeho_own"."users"(id) ON DELETE CASCADE,
        "viewedAt" timestamptz NOT NULL DEFAULT now(),
        CONSTRAINT "UQ_story_views_story_user" UNIQUE ("storyId","userId")
      )`);
    await this.db.query('CREATE INDEX IF NOT EXISTS "IDX_stories_expiry" ON "jeho_own"."stories" ("expiresAt" DESC) WHERE "isActive" = true');
    await this.db.query('CREATE INDEX IF NOT EXISTS "IDX_stories_user_expiry" ON "jeho_own"."stories" ("userId","expiresAt" DESC)');
    await this.db.query('CREATE INDEX IF NOT EXISTS "IDX_story_views_story" ON "jeho_own"."story_views" ("storyId")');
    await this.db.query('INSERT INTO "jeho_own"."app_settings" (key,value,description) VALUES ($1,$2,$3) ON CONFLICT (key) DO NOTHING',
      [KEY, JSON.stringify(DEFAULTS), 'Stories feature controls']);
  }

  async getConfig(): Promise<StoriesConfig> {
    const [row] = await this.db.query('SELECT value FROM "jeho_own"."app_settings" WHERE key=$1 LIMIT 1',[KEY]);
    let saved: any = {};
    try { saved = row?.value ? JSON.parse(row.value) : {}; } catch {}
    const c = { ...DEFAULTS, ...saved };
    return {
      enabled: c.enabled !== false, publishingEnabled: c.publishingEnabled !== false,
      viewingEnabled: c.viewingEnabled !== false, imagesEnabled: c.imagesEnabled !== false,
      videosEnabled: c.videosEnabled !== false, emergencyMode: c.emergencyMode === true,
      cacheEnabled: c.cacheEnabled !== false,
      durationHours: Math.min(24,Math.max(1,Number(c.durationHours)||24)),
      maxVideoSeconds: Math.min(60,Math.max(5,Number(c.maxVideoSeconds)||30)),
      maxMediaMb: Math.min(50,Math.max(1,Number(c.maxMediaMb)||20)),
    };
  }

  async adminUpdateConfig(patch: Partial<StoriesConfig>) {
    const c = await this.getConfig();
    const next: any = { ...c };
    for (const k of ['enabled','publishingEnabled','viewingEnabled','imagesEnabled','videosEnabled','emergencyMode','cacheEnabled']) {
      if (typeof (patch as any)?.[k] === 'boolean') next[k] = (patch as any)[k];
    }
    const limits: Array<[string, number, number]> = [['durationHours',1,24],['maxVideoSeconds',5,60],['maxMediaMb',1,50]];
    for (const [key,min,max] of limits) {
      if ((patch as any)?.[key] !== undefined) {
        const n = Number((patch as any)[key]);
        if (!Number.isInteger(n) || n < min || n > max) throw new BadRequestException('Invalid stories setting: ' + key);
        next[key] = n;
      }
    }
    await this.db.query(`INSERT INTO "jeho_own"."app_settings" (key,value,description)
      VALUES ($1,$2,$3) ON CONFLICT (key) DO UPDATE SET value=EXCLUDED.value, description=EXCLUDED.description, "updatedAt"=now()`,
      [KEY,JSON.stringify(next),'Stories feature controls']);
    this.logger.log('Stories controls changed: enabled=' + next.enabled + '; emergency=' + next.emergencyMode);
    return next;
  }

  async feed(viewerId: string) {
    const c = await this.getConfig();
    if (!c.enabled || !c.viewingEnabled) return [];
    await this.db.query(`DELETE FROM "jeho_own"."stories" WHERE id IN
      (SELECT id FROM "jeho_own"."stories" WHERE "expiresAt" <= now() ORDER BY "expiresAt" LIMIT 250)`);
    return this.db.query(`SELECT s.id,s."userId",s."mediaType",s."mediaUrl",s.caption,s.visibility,
      s."viewCount",s."createdAt",s."expiresAt",u.username,u."displayName",u."avatarUrl",u."publicId",
      EXISTS(SELECT 1 FROM "jeho_own"."story_views" v WHERE v."storyId"=s.id AND v."userId"=$1) AS "viewedByMe"
      FROM "jeho_own"."stories" s JOIN "jeho_own"."users" u ON u.id=s."userId"
      WHERE s."isActive"=true AND s."expiresAt">now() AND u.status='active'
      AND (s.visibility='public' OR s."userId"=$1 OR (s.visibility='followers' AND EXISTS
        (SELECT 1 FROM "jeho_own"."follows" f WHERE f."followerId"=$1 AND f."followingId"=s."userId")))
      ORDER BY s."createdAt" DESC LIMIT 300`,[viewerId]);
  }

  async create(userId: string, body: any) {
    const c = await this.getConfig();
    if (!c.enabled || !c.publishingEnabled || c.emergencyMode) throw new ForbiddenException('Story publishing is paused');
    const type = String(body?.mediaType || '').toLowerCase();
    if (type !== 'image' && type !== 'video') throw new BadRequestException('Invalid story media type');
    if (type === 'image' && !c.imagesEnabled) throw new ForbiddenException('Story images are disabled');
    if (type === 'video' && !c.videosEnabled) throw new ForbiddenException('Story videos are disabled');
    const url = String(body?.mediaUrl || '').trim();
    // Stories must reference files created by JEHO's authenticated upload pipeline.
    // Reject arbitrary remote URLs to prevent hotlinking, SSRF-style abuse, and
    // untrusted third-party media from being attached to a story.
    const ownedUploadPath = /^\/uploads\/[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\.(jpg|jpeg|png|gif|webp|mp4|mov|webm|3gp)$/i;
    if (!ownedUploadPath.test(url)) throw new BadRequestException('Story media must use a JEHO uploaded file');
    if (Number(body?.sizeBytes || 0) > c.maxMediaMb * 1024 * 1024) throw new BadRequestException('Story file exceeds configured size limit');
    if (type === 'video' && Number(body?.durationSeconds || 0) > c.maxVideoSeconds) throw new BadRequestException('Story video is too long');
    const visibility = ['public','followers','private'].includes(body?.visibility) ? body.visibility : 'public';
    const [count] = await this.db.query('SELECT count(*)::int AS n FROM "jeho_own"."stories" WHERE "userId"=$1 AND "expiresAt">now() AND "isActive"=true',[userId]);
    if (Number(count?.n || 0) >= 20) throw new BadRequestException('Maximum 20 active stories per user');
    const [row] = await this.db.query(`INSERT INTO "jeho_own"."stories" ("userId","mediaType","mediaUrl",caption,visibility,"expiresAt")
      VALUES ($1,$2,$3,$4,$5,now()+($6::int * interval '1 hour'))
      RETURNING id,"userId","mediaType","mediaUrl",caption,visibility,"viewCount","createdAt","expiresAt"`,
      [userId,type,url,String(body?.caption||'').trim().slice(0,300),visibility,c.durationHours]);
    const [user] = await this.db.query('SELECT username,"displayName","avatarUrl","publicId" FROM "jeho_own"."users" WHERE id=$1',[userId]);
    return { ...row, ...user, viewedByMe: false };
  }

  async recordView(id: string, userId: string) {
    const c = await this.getConfig();
    if (!c.enabled || !c.viewingEnabled) throw new ForbiddenException('Stories viewing is disabled');
    const [story] = await this.db.query('SELECT id FROM "jeho_own"."stories" WHERE id=$1 AND "isActive"=true AND "expiresAt">now()',[id]);
    if (!story) throw new NotFoundException('Story not found or expired');
    const inserted = await this.db.query('INSERT INTO "jeho_own"."story_views" ("storyId","userId") VALUES ($1,$2) ON CONFLICT ("storyId","userId") DO NOTHING RETURNING id',[id,userId]);
    if (inserted.length) await this.db.query('UPDATE "jeho_own"."stories" SET "viewCount"="viewCount"+1 WHERE id=$1',[id]);
    return { recorded: true, counted: inserted.length > 0 };
  }

  async remove(id: string, userId: string) {
    const rows = await this.db.query('DELETE FROM "jeho_own"."stories" WHERE id=$1 AND "userId"=$2 RETURNING id',[id,userId]);
    if (!rows.length) throw new NotFoundException('Story not found');
    return { deleted: true };
  }
  async adminList(limit=100) {
    return this.db.query(`SELECT s.id,s."userId",s."mediaType",s."mediaUrl",s.caption,s.visibility,s."viewCount",
      s."createdAt",s."expiresAt",u.username,u."displayName",u."publicId"
      FROM "jeho_own"."stories" s JOIN "jeho_own"."users" u ON u.id=s."userId"
      ORDER BY s."createdAt" DESC LIMIT $1`,[Math.min(300,Math.max(1,Math.trunc(Number(limit)||100)))]);
  }
  async adminRemove(id: string) {
    const rows = await this.db.query('DELETE FROM "jeho_own"."stories" WHERE id=$1 RETURNING id',[id]);
    if (!rows.length) throw new NotFoundException('Story not found');
    return { deleted: true };
  }
}
