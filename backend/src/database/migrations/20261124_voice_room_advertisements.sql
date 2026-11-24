CREATE TABLE IF NOT EXISTS "jeho_own"."room_advertisements" (
  "id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "senderId" uuid NOT NULL REFERENCES "jeho_own"."users"("id") ON DELETE CASCADE,
  "senderName" varchar(128) NOT NULL,
  "senderImageUrl" varchar(512),
  "text" varchar(500) NOT NULL,
  "status" varchar(16) NOT NULL DEFAULT 'queued',
  "queueOrder" bigint NOT NULL DEFAULT 0,
  "startedAt" timestamptz,
  "completedAt" timestamptz,
  "nextEligibleAt" timestamptz,
  "rejectionCode" varchar(64),
  "rejectionReason" varchar(255),
  "idempotencyKey" varchar(128),
  "displayTemplateId" varchar(64),
  "displayDurationMs" integer NOT NULL DEFAULT 9000,
  "createdAt" timestamptz NOT NULL DEFAULT now(),
  "updatedAt" timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS "idx_room_ads_queue" ON "jeho_own"."room_advertisements" ("status","queueOrder","createdAt");
CREATE INDEX IF NOT EXISTS "idx_room_ads_sender_day" ON "jeho_own"."room_advertisements" ("senderId","createdAt");
CREATE UNIQUE INDEX IF NOT EXISTS "uq_room_ads_idempotency" ON "jeho_own"."room_advertisements" ("idempotencyKey") WHERE "idempotencyKey" IS NOT NULL;
INSERT INTO "jeho_own"."app_settings" ("id","key","value","description","createdAt","updatedAt")
SELECT gen_random_uuid(),'voice_room_ads_config','{"enabled":true,"sendButtonEnabled":true,"dailyLimit":1,"maxTextLength":180,"resetTimezone":"UTC","displayDurationMs":9000,"gapMs":1800,"entrySpeedPxPerSec":120,"exitSpeedPxPerSec":120,"reducedMotionStatic":true,"template":"premium-v1","position":"safe-bottom","direction":"rtl","background":"linear-gradient(90deg,rgba(20,12,42,.94),rgba(50,24,76,.86))","backgroundImage":"","backgroundOpacity":92,"radiusDp":18,"heightDp":48,"avatarDp":34,"avatarShape":"circle","nameSp":12,"textSp":13,"nameColor":"#FFD98A","textColor":"#FFFFFF","spacingDp":8}'
       ,'Voice room advertising live configuration',NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM "jeho_own"."app_settings" WHERE "key"='voice_room_ads_config');
INSERT INTO "jeho_own"."app_settings" ("id","key","value","description","createdAt","updatedAt")
SELECT gen_random_uuid(),'voice_room_ads_banned_words','', 'Voice room advertising banned words, comma-separated',NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM "jeho_own"."app_settings" WHERE "key"='voice_room_ads_banned_words');