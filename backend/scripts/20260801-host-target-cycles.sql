-- Host target progress (salary ladder) — production columns.
-- Safe to re-run.
ALTER TABLE host_monthly_progress
  ADD COLUMN IF NOT EXISTS "cyclesCompleted" integer NOT NULL DEFAULT 0;

ALTER TABLE host_monthly_progress
  ADD COLUMN IF NOT EXISTS "withdrawnStageIds" text;

UPDATE host_monthly_progress
SET "withdrawnStageIds" = '[]'
WHERE "withdrawnStageIds" IS NULL;

