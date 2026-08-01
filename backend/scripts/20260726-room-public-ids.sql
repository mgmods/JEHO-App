-- Stable 8-digit numeric IDs for rooms. UUID remains the internal primary key.
CREATE SEQUENCE IF NOT EXISTS rooms_public_id_seq START WITH 10000000;

ALTER TABLE rooms
  ADD COLUMN IF NOT EXISTS "publicId" VARCHAR(16);

SELECT setval(
  'rooms_public_id_seq',
  GREATEST(
    COALESCE(
      (SELECT MAX("publicId"::bigint) FROM rooms WHERE "publicId" ~ '^[0-9]+$'),
      9999999
    ),
    9999999
  ),
  true
);

UPDATE rooms
SET "publicId" = nextval('rooms_public_id_seq')::text
WHERE "publicId" IS NULL OR trim("publicId") = '';

ALTER TABLE rooms
  ALTER COLUMN "publicId" SET DEFAULT nextval('rooms_public_id_seq')::text,
  ALTER COLUMN "publicId" SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS "idx_rooms_public_id"
  ON rooms("publicId");
