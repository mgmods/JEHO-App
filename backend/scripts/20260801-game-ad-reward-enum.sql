-- Add game_ad_reward to wallet_transactions.type enum (PostgreSQL).
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_enum e
    JOIN pg_type t ON t.oid = e.enumtypid
    WHERE t.typname = 'wallet_transactions_type_enum'
      AND e.enumlabel = 'game_ad_reward'
  ) THEN
    ALTER TYPE wallet_transactions_type_enum ADD VALUE 'game_ad_reward';
  END IF;
END $$;
