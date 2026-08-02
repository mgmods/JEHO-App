/**
 * Resolve a playable gift animation when DB still has /visual-system/runtime.html.
 * Uses Mikoo entry MP4s already on CDN.
 */
export function resolvePlayableGiftAnimation(opts: {
  giftName?: string | null;
  iconUrl?: string | null;
  animationUrl?: string | null;
}): string | null {
  const anim = (opts.animationUrl || '').trim();
  if (isVideoOrSvga(anim) && !isHtmlPlaceholder(anim)) return anim;

  const byName = mapByName(opts.giftName || '');
  if (byName) return byName;
  const byIcon = mapByIcon(opts.iconUrl || '');
  if (byIcon) return byIcon;

  const icon = (opts.iconUrl || '').trim();
  if (icon) {
    const sibling = icon.replace(/\.(png|jpe?g|webp)(\?.*)?$/i, '.mp4$2');
    if (sibling !== icon && isVideoOrSvga(sibling)) return sibling.split('?')[0];
  }
  return isHtmlPlaceholder(anim) ? null : anim || null;
}

function isHtmlPlaceholder(url: string): boolean {
  const u = url.toLowerCase();
  return !url || u.includes('runtime.html') || u.endsWith('.html') || u.endsWith('.htm');
}

function isVideoOrSvga(url: string): boolean {
  const u = url.split('?')[0].toLowerCase();
  return (
    u.endsWith('.mp4') ||
    u.endsWith('.webm') ||
    u.endsWith('.mov') ||
    u.endsWith('.svga')
  );
}

function entry(file: string): string {
  return `/assets/cosmetics/entries/${file}`;
}

function mapByName(name: string): string | null {
  const n = name.trim().toLowerCase();
  if (!n) return null;
  if (/(lion|أسد|اسد)/i.test(name) || n.includes('lion')) return entry('entry_mikoo_247_golden_lion_roar.mp4');
  if (/(tiger|نمر)/i.test(name)) return entry('entry_mikoo_256_lightning_lion.mp4');
  if (/(wolf|ذئب)/i.test(name)) return entry('entry_mikoo_264_majestic_lion_king.mp4');
  if (/(car|سيارة|سياره)/i.test(name)) return entry('entry_mikoo_265_luxury_car_team.mp4');
  if (/(plane|طائرة|طيارة|طائره)/i.test(name)) return entry('entry_mikoo_179_dubai_golden_airplane.mp4');
  if (/(ball|كرة|كره|football)/i.test(name)) return entry('entry_mikoo_190_goal.mp4');
  if (/(crown|تاج|عرش|throne)/i.test(name)) return entry('entry_mikoo_268_royal_family.mp4');
  if (/(dragon|تنين|phoenix|عنقاء)/i.test(name)) return entry('entry_mikoo_264_majestic_lion_king.mp4');
  if (/(yacht|يخت|train|قطار|bike|دراجة)/i.test(name)) return entry('entry_mikoo_265_luxury_car_team.mp4');
  if (/(rocket|صاروخ|meteor|نيزك)/i.test(name)) return entry('entry_mikoo_177_glory_kick.mp4');
  if (/(fireworks|galaxy|مجرة|champagne|شامبانيا)/i.test(name)) {
    return entry('entry_mikoo_267_winning_the_championship.mp4');
  }
  if (/(castle|قلعة|diamond|ألماس|ring|خاتم)/i.test(name)) {
    return entry('entry_mikoo_268_royal_family.mp4');
  }
  if (/(heart|قلب|rose|وردة|teddy|دب)/i.test(name)) return entry('entry_mikoo_260_happy_football.mp4');
  if (/(guitar|غيتار|microphone|ميكروفون|piano|بيانو|drums|طبول)/i.test(name)) {
    return entry('entry_mikoo_208_shoter_wealth_top3.mp4');
  }
  if (/(unicorn|يونيكورن|elephant|فيل|eagle|نسر|falcon|صقر)/i.test(name)) {
    return entry('entry_mikoo_264_majestic_lion_king.mp4');
  }
  return null;
}

function mapByIcon(iconUrl: string): string | null {
  const u = iconUrl.toLowerCase();
  if (!u) return null;
  if (u.includes('lion')) return entry('entry_mikoo_247_golden_lion_roar.mp4');
  if (u.includes('car')) return entry('entry_mikoo_265_luxury_car_team.mp4');
  if (u.includes('plane') || u.includes('airplane')) return entry('entry_mikoo_179_dubai_golden_airplane.mp4');
  if (u.includes('ball') || u.includes('football')) return entry('entry_mikoo_190_goal.mp4');
  if (u.includes('crown') || u.includes('royal')) return entry('entry_mikoo_268_royal_family.mp4');
  if (u.includes('dragon')) return entry('entry_mikoo_264_majestic_lion_king.mp4');
  if (u.includes('rocket') || u.includes('meteor')) return entry('entry_mikoo_177_glory_kick.mp4');
  if (u.includes('fireworks') || u.includes('galaxy')) {
    return entry('entry_mikoo_267_winning_the_championship.mp4');
  }
  return null;
}
