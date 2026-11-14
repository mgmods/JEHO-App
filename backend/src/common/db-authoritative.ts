/**
 * Production rule: PostgreSQL (dashboard-managed rows) is the single source of
 * truth for catalogs, prices, packages, gifts, cosmetics, VIP, and promos.
 *
 * Mutating "ensure/seed" paths stay OFF so a backend deploy never resets the
 * live economy back to code defaults.
 *
 * Opt-in only for empty dev installs: BOOT_SEED_CATALOGS=1
 */
export function bootCatalogSeedEnabled(): boolean {
  const raw = String(process.env.BOOT_SEED_CATALOGS || '')
    .trim()
    .toLowerCase();
  return raw === '1' || raw === 'true' || raw === 'yes';
}

/** Inverse helper — default true in production. */
export function isDbAuthoritative(): boolean {
  return !bootCatalogSeedEnabled();
}
