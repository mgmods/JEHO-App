/** Agency activation / invite codes + notification presets. */

const CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
const CODE_LENGTH = 8;

export const AGENCY_NOTIFICATION_STYLES = [
  'welcome',
  'elite',
  'family',
  'spark',
] as const;

export type AgencyNotificationStyle = (typeof AGENCY_NOTIFICATION_STYLES)[number];

export function isAgencyNotificationStyle(value: string): value is AgencyNotificationStyle {
  return (AGENCY_NOTIFICATION_STYLES as readonly string[]).includes(value);
}

export function normalizeActivationCode(raw: string): string {
  return String(raw || '')
    .trim()
    .toUpperCase()
    .replace(/[\s\-_.]/g, '');
}

export function generateActivationCode(): string {
  let code = '';
  for (let i = 0; i < CODE_LENGTH; i++) {
    code += CODE_ALPHABET[Math.floor(Math.random() * CODE_ALPHABET.length)];
  }
  return code;
}

export function agencyJoinNotification(
  style: string | null | undefined,
  agencyName: string,
): { title: string; body: string } {
  const name = agencyName?.trim() || 'الوكالة';
  switch (style) {
    case 'elite':
      return {
        title: 'انضمام للنخبة',
        body: `أصبحت جزءاً من نخبة «${name}». تألق معنا!`,
      };
    case 'family':
      return {
        title: 'أسرة الوكالة',
        body: `انضممت لأسرة «${name}». نحن سعداء بك!`,
      };
    case 'spark':
      return {
        title: 'إشراقة جديدة',
        body: `إشراقة جديدة في «${name}» — مرحباً بك!`,
      };
    case 'welcome':
    default:
      return {
        title: 'مرحباً في الوكالة',
        body: `تم قبولك في وكالة «${name}». أهلاً بك في العائلة!`,
      };
  }
}

export function notificationStyleLabel(style: string | null | undefined): string {
  switch (style) {
    case 'elite':
      return 'نخبة';
    case 'family':
      return 'أسرة';
    case 'spark':
      return 'إشراقة';
    case 'welcome':
    default:
      return 'ترحيب';
  }
}
