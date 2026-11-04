(function (window, document) {
  'use strict';
  const esc = value => String(value == null ? '' : value).replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
  const safeUrl = value => {
    const url = String(value || '').trim();
    return /^https?:\/\//i.test(url) ? url : '';
  };
  const style = document.createElement('style');
  style.textContent = '.jeho-cp-card{position:relative;overflow:hidden;border-radius:20px;background-size:cover;background-position:center;padding:16px;color:inherit}.jeho-cp-level{display:inline-flex;align-items:center;gap:6px;border-radius:99px;padding:4px 10px;background:#241329cc;color:#fff;border:1px solid #f4d27b;font-weight:700}.jeho-cp-level img{width:24px;height:24px;object-fit:contain}.jeho-cp-title{display:block;font-weight:800;color:var(--cp-name-color,#f7d47b);text-shadow:var(--cp-name-shadow,none)}.jeho-cp-frame{position:absolute;inset:-4%;width:108%;height:108%;object-fit:contain;pointer-events:none}.jeho-cp-duo-gift{position:relative;display:grid;place-items:center;min-height:110px}.jeho-cp-duo-animation{max-width:180px;max-height:180px;object-fit:contain}.jeho-cp-duo-avatars{position:absolute;bottom:4px;display:flex;gap:8px}.jeho-cp-duo-avatars img{width:34px;height:34px;border-radius:50%;border:2px solid #f4d27b;object-fit:cover}.jeho-cp-profile-summary{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap;padding:10px 12px;border-radius:16px;background:rgba(20,12,24,.06)}.jeho-cp-profile-partner{display:flex;align-items:center;gap:10px;min-width:0}.jeho-cp-profile-partner>img{width:46px;height:46px;border-radius:50%;object-fit:cover}.jeho-cp-profile-partner strong,.jeho-cp-profile-partner small{display:block}.jeho-cp-profile-meta{display:flex;align-items:center;gap:8px;flex-wrap:wrap}.jeho-cp-duration{font-size:12px;opacity:.85}';
  if (!document.getElementById('jeho-cp-renderer-style')) { style.id = 'jeho-cp-renderer-style'; document.head.appendChild(style); }
  function presentation(relationship) {
    return relationship && (relationship.presentation || relationship.currentLevelConfig && relationship.currentLevelConfig.presentation) || {};
  }
  function renderLevelBadge(relationship) {
    const p = presentation(relationship), logo = safeUrl(p.levelLogoUrl || p.logoUrl || p.badgeUrl || relationship && relationship.levelBadgeUrl);
    const label = relationship && (relationship.levelName || relationship.profileTitle || p.title) || 'CP ' + (relationship && relationship.level || 1);
    return '<span class="jeho-cp-level">' + (logo ? '<img src="' + esc(logo) + '" alt="">' : '') + '<span>' + esc(label) + '</span></span>';
  }
  function renderCard(relationship, content) {
    const p = presentation(relationship), bg = safeUrl(p.cardBackgroundUrl || relationship && relationship.cardBackgroundUrl);
    const frame = safeUrl(p.frameUrl);
    return '<section class="jeho-cp-card"' + (bg ? ' style="background-image:linear-gradient(#160a1a55,#160a1a77),url(&quot;' + esc(bg) + '&quot;)"' : '') + '>' +
      (frame ? '<img class="jeho-cp-frame" src="' + esc(frame) + '" alt="">' : '') +
      '<div style="position:relative;z-index:1">' + renderLevelBadge(relationship) + (content || '') + '</div></section>';
  }
  function renderName(name, relationship) {
    const p = presentation(relationship), color = String(p.nameColor || p.titleColor || '').trim();
    const shadow = p.nameGlow ? '0 0 8px ' + (color || '#f5ce70') : 'none';
    return '<span class="jeho-cp-title"' + ((color || p.nameGlow) ? ' style="' + (color ? '--cp-name-color:' + esc(color) + ';' : '') + '--cp-name-shadow:' + esc(shadow) + '"' : '') + '>' + esc(name) + '</span>';
  }
  function renderDuoGift(relationship, avatars) {
    const gift = relationship && (relationship.giftPresentation || relationship.currentLevelConfig && relationship.currentLevelConfig.giftPresentation) || {};
    const animation = safeUrl(gift.animationUrl);
    if (!animation) return '';
    const showAvatars = gift.showPartnerAvatars !== false;
    const a = safeUrl(avatars && avatars.a), b = safeUrl(avatars && avatars.b);
    return '<div class="jeho-cp-duo-gift"><img class="jeho-cp-duo-animation" src="' + esc(animation) + '" alt="هدية CP">' +
      (showAvatars ? '<div class="jeho-cp-duo-avatars">' + (a ? '<img src="' + esc(a) + '" alt="">' : '') + (b ? '<img src="' + esc(b) + '" alt="">' : '') + '</div>' : '') +
      '</div>';
  }
  function renderProfileSummary(relationship) {
    if (!relationship) return '';
    const partner = relationship.partner || {};
    const name = relationship.partnerDisplayName || partner.displayName || partner.username || 'الشريك';
    const avatar = safeUrl(relationship.partnerAvatarUrl || partner.avatarUrl);
    const title = relationship.profileTitle || relationship.levelName || '';
    const duration = relationship.durationLabel || (Number.isFinite(Number(relationship.durationDays)) ? Number(relationship.durationDays) + ' يوم' : '');
    const self = relationship.self || {};
    const presentation = relationship.presentation || {};
    const background = safeUrl(self.cpBackgroundUrl || partner.cpBackgroundUrl || presentation.cardBackgroundUrl);
    const logo = safeUrl(self.cpLogoUrl || partner.cpLogoUrl || presentation.levelLogoUrl || presentation.logoUrl);
    const backgroundStyle = background ? 'background-image:linear-gradient(120deg,#1d0715bb,#1d071588),url("' + esc(background) + '");background-size:cover;background-position:center;' : '';
    return '<section class="jeho-cp-profile-summary" style="' + backgroundStyle + '">' +
      (logo ? '<img src="' + esc(logo) + '" alt="شعار CP" style="width:48px;height:48px;object-fit:contain;border-radius:14px;background:#ffffff22;padding:5px;margin-bottom:8px">' : '') +
      '<div class="jeho-cp-profile-partner">' + (avatar ? '<img src="' + esc(avatar) + '" alt="">' : '') +
      '<div><strong>' + esc(name) + '</strong>' + (title ? '<small>' + esc(title) + '</small>' : '') + '</div></div>' +
      '<div class="jeho-cp-profile-meta">' + renderLevelBadge(relationship) +
      (duration ? '<span class="jeho-cp-duration">♥ مرتبطان منذ ' + esc(duration) + '</span>' : '') + '</div></section>';
  }
  function apply(root, relationship) {
    if (!root || !relationship) return;
    root.querySelectorAll('[data-cp-level]').forEach(node => { node.innerHTML = renderLevelBadge(relationship); });
    root.querySelectorAll('[data-cp-profile]').forEach(node => { node.innerHTML = renderProfileSummary(relationship); });
    root.querySelectorAll('[data-cp-card]').forEach(node => { node.outerHTML = renderCard(relationship, node.innerHTML); });
    root.querySelectorAll('[data-cp-name]').forEach(node => { node.innerHTML = renderName(node.textContent, relationship); });
    root.querySelectorAll('[data-cp-gift]').forEach(node => { node.innerHTML = renderDuoGift(relationship, { a: node.dataset.cpAvatarA, b: node.dataset.cpAvatarB }); });
  }
  async function load(userId, apiBase, root) {
    if (!userId || !apiBase || typeof window.fetch !== 'function') throw new Error('CP load requires userId and apiBase');
    let base = String(apiBase);
    while (base.endsWith('/')) base = base.slice(0, -1);
    const response = await window.fetch(base + '/cp/users/' + encodeURIComponent(userId));
    if (!response.ok) throw new Error('Unable to load CP profile: ' + response.status);
    const payload = await response.json();
    const rows = payload && payload.data && (payload.data.data || payload.data) || payload;
    const relationship = Array.isArray(rows) ? rows[0] || null : rows;
    if (root && relationship) apply(root, relationship);
    return relationship;
  }
  window.JehoCPRenderer = Object.freeze({ version: 2, renderLevelBadge, renderProfileSummary, renderCard, renderName, renderDuoGift, apply, load, presentation });
})(window, document);
