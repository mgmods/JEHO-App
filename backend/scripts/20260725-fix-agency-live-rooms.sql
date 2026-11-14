-- Fix fake live agency rooms + lock gift split defaults to 20/20/60

-- Platform / agency-owner cut settings
INSERT INTO app_settings (key, value, description, "createdAt", "updatedAt")
VALUES
  ('agency_platform_cut_percent', '20', 'حصة المنصة من هدايا الوكالة %', NOW(), NOW()),
  ('agency_default_commission_percent', '20', 'حصة صاحب الوكالة من هدايا الأعضاء %', NOW(), NOW())
ON CONFLICT (key) DO UPDATE
SET value = EXCLUDED.value,
    description = EXCLUDED.description,
    "updatedAt" = NOW();

-- Existing agencies (including الأميرات) → 20% owner cut
UPDATE agencies SET "commissionPercent" = 20 WHERE "commissionPercent" IS DISTINCT FROM 20;

-- Idle "open" rooms with no live host
UPDATE rooms
SET status = 'closed',
    "activeHostId" = NULL,
    "updatedAt" = NOW()
WHERE status = 'open'
  AND "activeHostId" IS NULL;

-- Agency rooms whose host is no longer an ACTIVE host-capable member
UPDATE rooms r
SET status = 'closed',
    "activeHostId" = NULL,
    "updatedAt" = NOW()
WHERE r."agencyId" IS NOT NULL
  AND (
    NOT EXISTS (
      SELECT 1
      FROM agency_members m
      WHERE m."agencyId" = r."agencyId"
        AND m."userId" = r."hostId"
        AND m."isActive" = true
        AND m.status = 'active'
        AND m.role IN ('owner', 'manager', 'host')
    )
  );
