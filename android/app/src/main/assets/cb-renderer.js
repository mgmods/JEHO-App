(function (window, document) {
  'use strict';
  const esc = value => String(value == null ? '' : value).replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
  const safeUrl = value => {
    const url = String(value || '').trim();
    return /^https?:\/\//i.test(url) ? url : '';
  };
  const style = document.createElement('style');
  style.textContent = '.jeho-cb-card{position:relative;overflow:hidden;border-radius:20px;background-size:cover;background-position:center;padding:16px;color:inherit}.jeho-cb-level{display:inline-flex;align-items:center;gap:6px;border-radius:99px;padding:4px 10px;background:#241329cc;color:#fff;border:1px solid #f4d27b;font-weight:700}.jeho-cb-level img{width:24px;height:24px;object-fit:contain}.jeho-cb-title{display:block;font-weight:800;color:var(--cb-name-color,#f7d47b);text-shadow:var(--cb-name-shadow,none)}.jeho-cb-frame{position:absolute;inset:-4%;width:108%;height:108%;object-fit:contain;pointer-events:none}.jeho-cb-duo-gift{position:relative;display:grid;place-items:center;min-height:110px}.jeho-cb-duo-animation{max-width:180px;max-height:180px;object-fit:contain}.jeho-cb-duo-avatars{position:absolute;bottom:4px;display:flex;gap:8px}.jeho-cb-duo-avatars img{width:34px;height:34px;border-radius:50%;border:2px solid #f4d27b;object-fit:cover}.jeho-cb-profile-summary{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap;padding:10px 12px;border-radius:16px;background:rgba(20,12,24,.06)}.jeho-cb-profile-partner{display:flex;align-items:center;gap:10px;min-width:0}.jeho-cb-profile-partner>img{width:46px;height:46px;border-radius:50%;object-fit:cover}.jeho-cb-profile-partner strong,.jeho-cb-profile-partner small{display:block}.jeho-cb-profile-meta{display:flex;align-items:center;gap:8px;flex-wrap:wrap}.jeho-cb-duration{font-size:12px;opacity:.85}';
  if (!document.getElementById('jeho-cb-renderer-style')) { style.id = 'jeho-cb-renderer-style'; document.head.appendChild(style); }
  function presentation(relationship) {
    return relationship && (relationship.presentation || relationship.currentLevelConfig && relationship.currentLevelConfig.presentation) || {};
  }
  function renderLevelBadge(relationship) {
    const p = presentation(relationship), logo = safeUrl(p.levelLogoUrl || p.logoUrl || p.badgeUrl || relationship && relationship.levelBadgeUrl);
    const label = relationship && (relationship.levelName || relationship.profileTitle || p.title) || 'CB ' + (relationship && relationship.level || 1);
    return '<span class="jeho-cb-level">' + (logo ? '<img src="' + esc(logo) + '" alt="">' : '') + '<span>' + esc(label) + '</span></span>';
  }
  function renderCard(relationship, content) {
    const p = presentation(relationship), bg = safeUrl(p.cardBackgroundUrl || relationship && relationship.cardBackgroundUrl);
    const frame = safeUrl(p.frameUrl);
    return '<section class="jeho-cb-card"' + (bg ? ' style="background-image:linear-gradient(#160a1a55,#160a1a77),url(&quot;' + esc(bg) + '&quot;)"' : '') + '>' +
      (frame ? '<img class="jeho-cb-frame" src="' + esc(frame) + '" alt="">' : '') +
      '<div style="position:relative;z-index:1">' + renderLevelBadge(relationship) + (content || '') + '</div></section>';
  }
  function renderName(name, relationship) {
    const p = presentation(relationship), color = String(p.nameColor || p.titleColor || '').trim();
    const shadow = p.nameGlow ? '0 0 8px ' + (color || '#f5ce70') : 'none';
    return '<span class="jeho-cb-title"' + ((color || p.nameGlow) ? ' style="' + (color ? '--cb-name-color:' + esc(color) + ';' : '') + '--cb-name-shadow:' + esc(shadow) + '"' : '') + '>' + esc(name) + '</span>';
  }
  function renderDuoGift(relationship, avatars) {
    const gift = relationship && (relationship.giftPresentation || relationship.currentLevelConfig && relationship.currentLevelConfig.giftPresentation) || {};
    const animation = safeUrl(gift.animationUrl);
    if (!animation) return '';
    const showAvatars = gift.showPartnerAvatars !== false;
    const a = safeUrl(avatars && avatars.a), b = safeUrl(avatars && avatars.b);
    return '<div class="jeho-cb-duo-gift"><img class="jeho-cb-duo-animation" src="' + esc(animation) + '" alt="هدية CB">' +
      (showAvatars ? '<div class="jeho-cb-duo-avatars">' + (a ? '<img src="' + esc(a) + '" alt="">' : '') + (b ? '<img src="' + esc(b) + '" alt="">' : '') + '</div>' : '') +
      '</div>';
  }
  function renderProfileSummary(relationship) {
    if (!relationship) return '';
    const partner = relationship.partner || {};
    const name = relationship.partnerDisplayName || partner.displayName || partner.username || 'الشريك';
    const avatar = safeUrl(relationship.partnerAvatarUrl || partner.avatarUrl);
    const title = relationship.profileTitle || relationship.levelName || '';
    const duration = relationship.durationLabel || (Number.isFinite(Number(relationship.durationDays)) ? Number(relationship.durationDays) + ' يوم' : '');
    return '<section class="jeho-cb-profile-summary">' +
      '<div class="jeho-cb-profile-partner">' + (avatar ? '<img src="' + esc(avatar) + '" alt="">' : '') +
      '<div><strong>' + esc(name) + '</strong>' + (title ? '<small>' + esc(title) + '</small>' : '') + '</div></div>' +
      '<div class="jeho-cb-profile-meta">' + renderLevelBadge(relationship) +
      (duration ? '<span class="jeho-cb-duration">♥ مرتبطان منذ ' + esc(duration) + '</span>' : '') + '</div></section>';
  }
  function apply(root, relationship) {
    if (!root || !relationship) return;
    root.querySelectorAll('[data-cb-level]').forEach(node => { node.innerHTML = renderLevelBadge(relationship); });
    root.querySelectorAll('[data-cb-profile]').forEach(node => { node.innerHTML = renderProfileSummary(relationship); });
    root.querySelectorAll('[data-cb-card]').forEach(node => { node.outerHTML = renderCard(relationship, node.innerHTML); });
    root.querySelectorAll('[data-cb-name]').forEach(node => { node.innerHTML = renderName(node.textContent, relationship); });
    root.querySelectorAll('[data-cb-gift]').forEach(node => { node.innerHTML = renderDuoGift(relationship, { a: node.dataset.cbAvatarA, b: node.dataset.cbAvatarB }); });
  }
  async function load(userId, apiBase, root) {
    if (!userId || !apiBase || typeof window.fetch !== 'function') throw new Error('CB load requires userId and apiBase');
    let base = String(apiBase);
    while (base.endsWith('/')) base = base.slice(0, -1);
    const response = await window.fetch(base + '/cp/users/' + encodeURIComponent(userId));
    if (!response.ok) throw new Error('Unable to load CB profile: ' + response.status);
    const payload = await response.json();
    const rows = payload && payload.data && (payload.data.data || payload.data) || payload;
    const relationship = Array.isArray(rows) ? rows[0] || null : rows;
    if (root && relationship) apply(root, relationship);
    return relationship;
  }
  window.JehoCB = Object.freeze({ version: 2, renderLevelBadge, renderProfileSummary, renderCard, renderName, renderDuoGift, apply, load, presentation });
})(window, document);
