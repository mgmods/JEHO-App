-- Ensure withdraw/economy runtime keys exist (admin-controlled from dashboard).
INSERT INTO app_settings (id, key, value, description, "createdAt", "updatedAt")
SELECT gen_random_uuid(), v.key, v.value, v.description, NOW(), NOW()
FROM (VALUES
  ('economy.minWithdrawDiamonds', '10000', 'Withdrawal target / minimum diamonds'),
  ('economy.diamondUsdRate', '0.00005', 'USD value of one diamond (withdraw)'),
  ('economy.diamondCoinRate', '0.55', 'Gold coins granted per diamond exchanged'),
  ('economy.silverToCoinRate', '0.04', 'Gold coins per silver coin'),
  ('economy.minSilverExchange', '4000', 'Minimum silver for exchange'),
  ('economy.coinToSilverRate', '20', 'Silver coins per gold coin')
) AS v(key, value, description)
WHERE NOT EXISTS (
  SELECT 1 FROM app_settings s WHERE s.key = v.key
);
