-- Host monthly target: allow restarting stages after completing the full set.
ALTER TABLE host_monthly_progress
  ADD COLUMN IF NOT EXISTS "cyclesCompleted" integer NOT NULL DEFAULT 0;
