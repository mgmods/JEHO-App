-- AuraLive PostgreSQL schema
-- Brand: AuraLive | Matches TypeORM entities in backend/src/database/entities

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Enums
DO $$ BEGIN
  CREATE TYPE user_status AS ENUM ('active', 'banned', 'deleted', 'suspended');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
  CREATE TYPE gender AS ENUM ('male', 'female', 'other', 'unspecified');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
  CREATE TYPE otp_purpose AS ENUM ('login', 'register', 'reset_password', 'verify_phone', 'verify_email');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
  CREATE TYPE report_target_type AS ENUM ('user', 'room', 'message', 'gift');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
  CREATE TYPE report_status AS ENUM ('pending', 'reviewing', 'resolved', 'rejected');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
  CREATE TYPE room_type AS ENUM ('voice', 'party');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
  CREATE TYPE room_status AS ENUM ('open', 'locked', 'closed');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
  CREATE TYPE seat_status AS ENUM ('empty', 'occupied', 'locked', 'muted');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
  CREATE TYPE moderator_role AS ENUM ('mod', 'admin');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
  CREATE TYPE gift_type AS ENUM ('normal', 'lucky', 'combo', 'premium');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
  CREATE TYPE transaction_type AS ENUM ('recharge', 'gift_send', 'gift_receive', 'exchange', 'withdraw', 'refund', 'admin_adjust', 'lucky_reward');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
  CREATE TYPE currency_type AS ENUM ('coins', 'diamonds');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
  CREATE TYPE recharge_status AS ENUM ('pending', 'completed', 'failed', 'cancelled', 'refunded');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
  CREATE TYPE payment_provider AS ENUM ('stripe', 'paypal', 'google_play', 'apple', 'crypto', 'admin');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
  CREATE TYPE withdraw_status AS ENUM ('pending', 'approved', 'rejected', 'paid', 'cancelled');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
  CREATE TYPE agency_status AS ENUM ('pending', 'active', 'suspended', 'rejected');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
  CREATE TYPE agency_role AS ENUM ('owner', 'manager', 'host', 'member');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
  CREATE TYPE agency_application_status AS ENUM ('pending', 'changes_requested', 'approved', 'rejected');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
  CREATE TYPE ranking_period AS ENUM ('daily', 'weekly', 'monthly');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
  CREATE TYPE ranking_category AS ENUM ('rich', 'popular', 'host', 'agency', 'room', 'gifts');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
  CREATE TYPE conversation_type AS ENUM ('direct', 'group');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
  CREATE TYPE message_type AS ENUM ('text', 'image', 'video', 'audio', 'file', 'gift', 'system');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
  CREATE TYPE notification_type AS ENUM ('system', 'follow', 'gift', 'room', 'chat', 'vip', 'agency', 'wallet');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
  CREATE TYPE admin_role AS ENUM ('super', 'moderator', 'support', 'finance');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
  CREATE TYPE abuse_severity AS ENUM ('low', 'medium', 'high', 'critical');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE TABLE IF NOT EXISTS users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email VARCHAR(64) UNIQUE,
  phone VARCHAR(32) UNIQUE,
  username VARCHAR(48) NOT NULL UNIQUE,
  "publicId" VARCHAR(16) UNIQUE,
  "passwordHash" VARCHAR(255),
  "displayName" VARCHAR(128) NOT NULL DEFAULT '',
  "avatarUrl" VARCHAR(512),
  gender gender NOT NULL DEFAULT 'unspecified',
  birthday DATE,
  status user_status NOT NULL DEFAULT 'active',
  "isGuest" BOOLEAN NOT NULL DEFAULT FALSE,
  "isAdmin" BOOLEAN NOT NULL DEFAULT FALSE,
  "emailVerified" BOOLEAN NOT NULL DEFAULT FALSE,
  "phoneVerified" BOOLEAN NOT NULL DEFAULT FALSE,
  "googleId" VARCHAR(64),
  "facebookId" VARCHAR(64),
  "appleId" VARCHAR(64),
  level INT NOT NULL DEFAULT 1,
  experience BIGINT NOT NULL DEFAULT 0,
  "refreshTokenHash" VARCHAR(512),
  "lastOnlineAt" TIMESTAMPTZ,
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_phone ON users(phone);
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_public_id ON users("publicId");

CREATE TABLE IF NOT EXISTS user_profiles (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  "userId" UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
  bio TEXT,
  country VARCHAR(128),
  city VARCHAR(128),
  language VARCHAR(16),
  interests TEXT,
  "coverUrl" VARCHAR(512),
  "followersCount" INT NOT NULL DEFAULT 0,
  "followingCount" INT NOT NULL DEFAULT 0,
  "friendsCount" INT NOT NULL DEFAULT 0,
  "totalReceivedDiamonds" BIGINT NOT NULL DEFAULT 0,
  "totalSentCoins" BIGINT NOT NULL DEFAULT 0,
  "showOnlineStatus" BOOLEAN NOT NULL DEFAULT TRUE,
  "allowDmFromStrangers" BOOLEAN NOT NULL DEFAULT TRUE,
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS devices (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  "userId" UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  "deviceId" VARCHAR(128) NOT NULL,
  platform VARCHAR(32) NOT NULL,
  model VARCHAR(64),
  "fcmToken" VARCHAR(512),
  "appVersion" VARCHAR(64),
  "isActive" BOOLEAN NOT NULL DEFAULT TRUE,
  "lastSeenAt" TIMESTAMPTZ,
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_devices_user ON devices("userId");

CREATE TABLE IF NOT EXISTS otp_codes (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  target VARCHAR(64) NOT NULL,
  channel VARCHAR(16) NOT NULL,
  purpose otp_purpose NOT NULL,
  "codeHash" VARCHAR(128) NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  used BOOLEAN NOT NULL DEFAULT FALSE,
  "expiresAt" TIMESTAMPTZ NOT NULL,
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_otp_target ON otp_codes(target);

CREATE TABLE IF NOT EXISTS follows (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  "followerId" UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  "followingId" UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE ("followerId", "followingId")
);
CREATE INDEX IF NOT EXISTS idx_follows_follower ON follows("followerId");
CREATE INDEX IF NOT EXISTS idx_follows_following ON follows("followingId");

CREATE TABLE IF NOT EXISTS blocks (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  "blockerId" UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  "blockedId" UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  reason VARCHAR(255),
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE ("blockerId", "blockedId")
);
CREATE INDEX IF NOT EXISTS idx_blocks_blocker ON blocks("blockerId");
CREATE INDEX IF NOT EXISTS idx_blocks_blocked ON blocks("blockedId");

CREATE TABLE IF NOT EXISTS reports (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  "reporterId" UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  "targetType" report_target_type NOT NULL,
  "targetId" UUID NOT NULL,
  reason VARCHAR(64) NOT NULL,
  description TEXT,
  "evidenceUrls" TEXT,
  status report_status NOT NULL DEFAULT 'pending',
  "handledById" UUID,
  "adminNote" TEXT,
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_reports_reporter ON reports("reporterId");
CREATE INDEX IF NOT EXISTS idx_reports_target ON reports("targetId");

CREATE TABLE IF NOT EXISTS rooms (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  title VARCHAR(128) NOT NULL,
  description TEXT,
  "coverUrl" VARCHAR(512),
  type room_type NOT NULL DEFAULT 'voice',
  status room_status NOT NULL DEFAULT 'open',
  "hostId" UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  "cohostId" UUID,
  "passwordHash" VARCHAR(128),
  "hasPassword" BOOLEAN NOT NULL DEFAULT FALSE,
  "seatCount" INT NOT NULL DEFAULT 8,
  "viewerCount" INT NOT NULL DEFAULT 0,
  "zegoRoomId" VARCHAR(64),
  topic VARCHAR(64),
  tags TEXT,
  "isPublic" BOOLEAN NOT NULL DEFAULT TRUE,
  "agencyId" UUID,
  "roomKind" VARCHAR(16) NOT NULL DEFAULT 'standard',
  "isPersistent" BOOLEAN NOT NULL DEFAULT FALSE,
  "activeHostId" UUID,
  "accessMode" VARCHAR(16) NOT NULL DEFAULT 'free',
  "entryFeeCoins" INT NOT NULL DEFAULT 0,
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_rooms_host ON rooms("hostId");
CREATE INDEX IF NOT EXISTS idx_rooms_title ON rooms(title);
CREATE UNIQUE INDEX IF NOT EXISTS uq_room_agency_host
  ON rooms("agencyId", "hostId") WHERE "agencyId" IS NOT NULL;

CREATE TABLE IF NOT EXISTS room_seats (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  "roomId" UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
  "seatIndex" INT NOT NULL,
  "userId" UUID REFERENCES users(id) ON DELETE SET NULL,
  status seat_status NOT NULL DEFAULT 'empty',
  "isMuted" BOOLEAN NOT NULL DEFAULT FALSE,
  "isHostSeat" BOOLEAN NOT NULL DEFAULT FALSE,
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE ("roomId", "seatIndex")
);
CREATE INDEX IF NOT EXISTS idx_room_seats_room ON room_seats("roomId");

CREATE TABLE IF NOT EXISTS room_bans (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  "roomId" UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
  "userId" UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  "bannedById" UUID NOT NULL,
  reason VARCHAR(255),
  "expiresAt" TIMESTAMPTZ,
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE ("roomId", "userId")
);

CREATE TABLE IF NOT EXISTS room_moderators (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  "roomId" UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
  "userId" UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role moderator_role NOT NULL DEFAULT 'mod',
  "appointedById" UUID NOT NULL,
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE ("roomId", "userId")
);

CREATE TABLE IF NOT EXISTS gifts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name VARCHAR(64) NOT NULL,
  description VARCHAR(255),
  "iconUrl" VARCHAR(512) NOT NULL,
  "animationUrl" VARCHAR(512),
  "coinPrice" INT NOT NULL,
  "diamondValue" INT NOT NULL DEFAULT 0,
  type gift_type NOT NULL DEFAULT 'normal',
  "isActive" BOOLEAN NOT NULL DEFAULT TRUE,
  "sortOrder" INT NOT NULL DEFAULT 0,
  "comboWindowMs" INT,
  "luckyConfig" JSONB,
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS gift_sends (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  "giftId" UUID NOT NULL REFERENCES gifts(id) ON DELETE CASCADE,
  "senderId" UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  "receiverId" UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  "roomId" UUID,
  quantity INT NOT NULL DEFAULT 1,
  "comboCount" INT NOT NULL DEFAULT 1,
  "totalCoins" INT NOT NULL,
  "diamondsAwarded" INT NOT NULL DEFAULT 0,
  "luckyMultiplier" DECIMAL(10,2),
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_gift_sends_sender ON gift_sends("senderId");
CREATE INDEX IF NOT EXISTS idx_gift_sends_receiver ON gift_sends("receiverId");

CREATE TABLE IF NOT EXISTS wallets (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  "userId" UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
  coins BIGINT NOT NULL DEFAULT 0,
  diamonds BIGINT NOT NULL DEFAULT 0,
  "totalRecharged" BIGINT NOT NULL DEFAULT 0,
  "totalWithdrawn" BIGINT NOT NULL DEFAULT 0,
  currency VARCHAR(8) NOT NULL DEFAULT 'USD',
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS wallet_transactions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  "userId" UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  type transaction_type NOT NULL,
  currency currency_type NOT NULL,
  amount BIGINT NOT NULL,
  "balanceAfter" BIGINT NOT NULL,
  "referenceType" VARCHAR(255),
  "referenceId" UUID,
  description VARCHAR(255),
  metadata JSONB,
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_wallet_tx_user ON wallet_transactions("userId");

CREATE TABLE IF NOT EXISTS recharge_orders (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  "userId" UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  coins INT NOT NULL,
  "amountFiat" DECIMAL(12,2) NOT NULL,
  currency VARCHAR(8) NOT NULL DEFAULT 'USD',
  provider payment_provider NOT NULL,
  status recharge_status NOT NULL DEFAULT 'pending',
  "providerOrderId" VARCHAR(255),
  "providerPaymentId" VARCHAR(255),
  "providerPayload" JSONB,
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_recharge_user ON recharge_orders("userId");

CREATE TABLE IF NOT EXISTS withdraw_requests (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  "userId" UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  diamonds BIGINT NOT NULL,
  "amountFiat" DECIMAL(12,2) NOT NULL,
  currency VARCHAR(8) NOT NULL DEFAULT 'USD',
  method VARCHAR(32) NOT NULL,
  "agentId" UUID,
  "payoutDetails" JSONB NOT NULL,
  status withdraw_status NOT NULL DEFAULT 'pending',
  "reviewedById" UUID,
  "adminNote" TEXT,
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS vip_plans (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  level INT NOT NULL UNIQUE,
  name VARCHAR(64) NOT NULL,
  "coinPriceMonthly" INT NOT NULL,
  "badgeUrl" VARCHAR(512),
  benefits JSONB NOT NULL,
  "isActive" BOOLEAN NOT NULL DEFAULT TRUE,
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS user_vips (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  "userId" UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  "vipPlanId" UUID NOT NULL REFERENCES vip_plans(id) ON DELETE CASCADE,
  level INT NOT NULL,
  "startsAt" TIMESTAMPTZ NOT NULL,
  "expiresAt" TIMESTAMPTZ NOT NULL,
  "isActive" BOOLEAN NOT NULL DEFAULT TRUE,
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_user_vips_user ON user_vips("userId");

CREATE TABLE IF NOT EXISTS agencies (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name VARCHAR(128) NOT NULL UNIQUE,
  description TEXT,
  "logoUrl" VARCHAR(512),
  "ownerId" UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  status agency_status NOT NULL DEFAULT 'pending',
  "memberCount" INT NOT NULL DEFAULT 0,
  "totalDiamonds" BIGINT NOT NULL DEFAULT 0,
  "commissionPercent" DECIMAL(5,2) NOT NULL DEFAULT 10,
  "activationCode" VARCHAR(16) UNIQUE,
  "notificationStyle" VARCHAR(32) NOT NULL DEFAULT 'welcome',
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS agency_members (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  "agencyId" UUID NOT NULL REFERENCES agencies(id) ON DELETE CASCADE,
  "userId" UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role agency_role NOT NULL DEFAULT 'member',
  "diamondsContributed" BIGINT NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL DEFAULT 'active',
  "isActive" BOOLEAN NOT NULL DEFAULT TRUE,
  "joinedAt" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE ("agencyId", "userId")
);

DO $$ BEGIN
  ALTER TABLE rooms
    ADD CONSTRAINT fk_rooms_agency
    FOREIGN KEY ("agencyId") REFERENCES agencies(id) ON DELETE SET NULL;
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE TABLE IF NOT EXISTS agency_applications (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  "applicantId" UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  "proposedName" VARCHAR(128) NOT NULL,
  description TEXT NOT NULL,
  "businessPlan" TEXT NOT NULL,
  country VARCHAR(100) NOT NULL,
  "contactEmail" VARCHAR(254) NOT NULL,
  "contactPhone" VARCHAR(32) NOT NULL,
  "socialLink" VARCHAR(512),
  experience TEXT NOT NULL,
  "expectedHostCount" INT NOT NULL,
  "documentUrls" JSONB NOT NULL DEFAULT '[]'::jsonb,
  "termsAccepted" BOOLEAN NOT NULL DEFAULT FALSE,
  status agency_application_status NOT NULL DEFAULT 'pending',
  "reviewNote" TEXT,
  "reviewedById" UUID REFERENCES users(id) ON DELETE SET NULL,
  "agencyId" UUID REFERENCES agencies(id) ON DELETE SET NULL,
  "reviewedAt" TIMESTAMPTZ,
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_agency_applications_applicant
  ON agency_applications("applicantId");
CREATE INDEX IF NOT EXISTS idx_agency_applications_status
  ON agency_applications(status);
CREATE UNIQUE INDEX IF NOT EXISTS uq_agency_application_pending_applicant
  ON agency_applications("applicantId") WHERE status = 'pending';

CREATE TABLE IF NOT EXISTS ranking_snapshots (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  period ranking_period NOT NULL,
  category ranking_category NOT NULL,
  "periodKey" VARCHAR(32) NOT NULL,
  "targetId" UUID NOT NULL,
  "targetName" VARCHAR(128),
  rank INT NOT NULL,
  score BIGINT NOT NULL,
  meta JSONB,
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_ranking_lookup ON ranking_snapshots(period, category, "periodKey");

CREATE TABLE IF NOT EXISTS chat_conversations (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  type conversation_type NOT NULL DEFAULT 'direct',
  title VARCHAR(128),
  "directKey" VARCHAR(128) UNIQUE,
  "lastMessageId" UUID,
  "lastMessageAt" TIMESTAMPTZ,
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS chat_participants (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  "conversationId" UUID NOT NULL REFERENCES chat_conversations(id) ON DELETE CASCADE,
  "userId" UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  "isMuted" BOOLEAN NOT NULL DEFAULT FALSE,
  "isPinned" BOOLEAN NOT NULL DEFAULT FALSE,
  "lastReadAt" TIMESTAMPTZ,
  "unreadCount" INT NOT NULL DEFAULT 0,
  "joinedAt" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE ("conversationId", "userId")
);

CREATE TABLE IF NOT EXISTS chat_messages (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  "conversationId" UUID NOT NULL REFERENCES chat_conversations(id) ON DELETE CASCADE,
  "senderId" UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  type message_type NOT NULL DEFAULT 'text',
  content TEXT,
  media JSONB,
  "replyToId" UUID REFERENCES chat_messages(id) ON DELETE SET NULL,
  "forwardedFromId" UUID,
  "isEdited" BOOLEAN NOT NULL DEFAULT FALSE,
  "isUnsent" BOOLEAN NOT NULL DEFAULT FALSE,
  "editedAt" TIMESTAMPTZ,
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_chat_msg_conv ON chat_messages("conversationId");

CREATE TABLE IF NOT EXISTS notifications (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  "userId" UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  type notification_type NOT NULL,
  title VARCHAR(128) NOT NULL,
  body TEXT NOT NULL,
  data JSONB,
  "isRead" BOOLEAN NOT NULL DEFAULT FALSE,
  "fcmSent" BOOLEAN NOT NULL DEFAULT FALSE,
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_notif_user ON notifications("userId");

CREATE TABLE IF NOT EXISTS admin_users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email VARCHAR(64) NOT NULL UNIQUE,
  username VARCHAR(48) NOT NULL UNIQUE,
  "passwordHash" VARCHAR(255) NOT NULL,
  role admin_role NOT NULL DEFAULT 'moderator',
  "linkedUserId" UUID,
  "isActive" BOOLEAN NOT NULL DEFAULT TRUE,
  "lastLoginAt" TIMESTAMPTZ,
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS app_settings (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  key VARCHAR(128) NOT NULL UNIQUE,
  value TEXT NOT NULL,
  description VARCHAR(255),
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS abuse_logs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  "userId" UUID,
  action VARCHAR(64) NOT NULL,
  "targetType" VARCHAR(64),
  "targetId" UUID,
  severity abuse_severity NOT NULL DEFAULT 'low',
  details TEXT,
  "ipAddress" VARCHAR(64),
  resolved BOOLEAN NOT NULL DEFAULT FALSE,
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_abuse_user ON abuse_logs("userId");
