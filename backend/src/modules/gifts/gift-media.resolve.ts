/**
 * Resolve gift play media by uploaded type (image / gif-webp / video).
 * Admin uploads always win; Mikoo entry remaps only for legacy HTML placeholders.
 */
export function resolvePlayableGiftAnimation(opts: {
  giftName?: string | null;
  iconUrl?: string | null;
  animationUrl?: string | null;
}): string | null {
  const anim = firstRealMedia(opts.animationUrl);
  if (anim) return anim;

  const icon = firstRealMedia(opts.iconUrl);
  if (icon) {
    const k = mediaKind(icon);
    // Icon-as-show: gif / video / svga / trusted upload image.
    if (
      k === 'video' ||
      k === 'svga' ||
      k === 'gif' ||
      k === 'image' ||
      isTrustedUpload(icon)
    ) {
      return icon;
    }
  }

  // Legacy only: runtime.html with no real files.
  if (isHtmlPlaceholder(opts.animationUrl || '')) {
    const byName = mapByName(opts.giftName || '');
    if (byName) return byName;
    if (!isTrustedUpload(opts.iconUrl || '')) {
      const byIcon = mapByIcon(opts.iconUrl || '');
      if (byIcon) return byIcon;
    }
  }

  const iconRaw = (opts.iconUrl || '').trim();
  if (iconRaw && isTrustedUpload(iconRaw)) {
    const sibling = iconRaw.replace(/\.(png|jpe?g|webp)(\?.*)?$/i, '.mp4$2');
    if (sibling !== iconRaw && mediaKind(sibling) === 'video' && isTrustedUpload(sibling)) {
      return sibling.split('?')[0];
    }
  }

  return firstRealMedia(opts.animationUrl) || firstRealMedia(opts.iconUrl) || null;
}

export type GiftMediaKind = 'image' | 'gif' | 'video' | 'svga' | 'none';

export function mediaKind(url: string): GiftMediaKind {
  if (!url) return 'none';
  const u = url.split('?')[0].toLowerCase();
  if (isHtmlPlaceholder(url)) return 'none';
  if (u.endsWith('.svga')) return 'svga';
  if (u.endsWith('.mp4') || u.endsWith('.webm') || u.endsWith('.mov')) return 'video';
  if (u.endsWith('.gif')) return 'gif';
  if (
    u.endsWith('.webp') ||
    u.endsWith('.png') ||
    u.endsWith('.jpg') ||
    u.endsWith('.jpeg') ||
    u.endsWith('.bmp') ||
    u.endsWith('.avif')
  ) {
    // Animated webp is still delivered as image/gif path client-side (Glide asGif).
    return u.endsWith('.webp') || u.endsWith('.gif') ? 'gif' : 'image';
  }
  if (u.startsWith('http://') || u.startsWith('https://') || u.startsWith('/')) {
    return 'image';
  }
  return 'none';
}

function firstRealMedia(url?: string | null): string | null {
  const raw = (url || '').trim();
  if (!raw || isHtmlPlaceholder(raw)) return null;
  const k = mediaKind(raw);
  return k === 'none' ? null : raw;
}

function isHtmlPlaceholder(url: string): boolean {
  const u = (url || '').toLowerCase();
  return !url || u.includes('runtime.html') || u.endsWith('.html') || u.endsWith('.htm');
}

function isTrustedUpload(url: string): boolean {
  const u = (url || '').toLowerCase();
  return (
    u.includes('/uploads/') ||
    u.includes('/assets/gifts/') ||
    u.includes('/assets/pack/') ||
    u.startsWith('http://') ||
    u.startsWith('https://')
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
  if (/(fireworks|galaxy|مجرة|champagne|شامبانيا|احتفال|فاخر)/i.test(name)) {
    return entry('entry_mikoo_267_winning_the_championship.mp4');
  }
  if (/(planet|كوكب|saturn|earth)/i.test(name)) {
    return entry('entry_mikoo_267_winning_the_championship.mp4');
  }
  if (/(castle|قلعة|diamond|ألماس|ring|خاتم)/i.test(name)) {
    return entry('entry_mikoo_268_royal_family.mp4');
  }
  if (/(heart|قلب|rose|وردة|teddy|دب)/i.test(name)) return entry('entry_mikoo_260_happy_football.mp4');
  if (/(egg|بيضة|بيضه|lucky.?egg)/i.test(name)) return entry('entry_mikoo_260_happy_football.mp4');
  if (/(icecream|آيس|ايس.?كريم|ice.?cream)/i.test(name)) {
    return entry('entry_mikoo_260_happy_football.mp4');
  }
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
  if (!u || isTrustedUpload(iconUrl)) return null;
  if (u.includes('lion')) return entry('entry_mikoo_247_golden_lion_roar.mp4');
  if (u.includes('car')) return entry('entry_mikoo_265_luxury_car_team.mp4');
  if (u.includes('plane') || u.includes('airplane')) return entry('entry_mikoo_179_dubai_golden_airplane.mp4');
  if (u.includes('ball') || u.includes('football')) return entry('entry_mikoo_190_goal.mp4');
  if (u.includes('crown') || u.includes('royal')) return entry('entry_mikoo_268_royal_family.mp4');
  if (u.includes('dragon')) return entry('entry_mikoo_264_majestic_lion_king.mp4');
  if (u.includes('rocket') || u.includes('meteor')) return entry('entry_mikoo_177_glory_kick.mp4');
  if (u.includes('fireworks') || u.includes('galaxy') || u.includes('champagne')
      || u.includes('gift-champagne')) {
    return entry('entry_mikoo_267_winning_the_championship.mp4');
  }
  if (u.includes('planet') || u.includes('saturn') || u.includes('earth')) {
    return entry('entry_mikoo_267_winning_the_championship.mp4');
  }
  return null;
}
