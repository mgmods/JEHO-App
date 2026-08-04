/**
 * Agency exclusive cosmetics helpers.
 * Exclusive frames / room cards are never sold in the mall — admin grants only.
 */

export function isAgencyExclusiveCosmetic(meta: Record<string, unknown> | null | undefined, code?: string) {
  if (meta && meta.agencyExclusive === true) return true;
  if (meta && String(meta.grant || '').toLowerCase() === 'agency_admin') return true;
  const c = String(code || '').toLowerCase();
  // Catalog frames/entries named for agencies
  return /_agency|agency_exclusive|agency_frame|exclusive_agency/.test(c);
}

export function normalizeAgencyPublicId(raw: string): string {
  return String(raw || '')
    .trim()
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, '')
    .slice(0, 12);
}

export function isValidAgencyPublicId(raw: string): boolean {
  const id = normalizeAgencyPublicId(raw);
  return /^[A-Z0-9]{3,12}$/.test(id);
}
