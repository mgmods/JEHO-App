(function () {
  'use strict';

  var TABS = [
    { key: 'vip_badge', label: 'الإطارات', hint: 'إطارات وحركات تظهر على صورتك — عاين قبل الشراء' },
    { key: 'entry_effect', label: 'الدخوليات', hint: 'الدخولية عند دخول الغرفة (سيارات / مؤثرات)' },
    { key: 'level_badge', label: 'شارات المستوى', hint: 'شارات المستوى بجانب الاسم' },
    { key: 'room_card', label: 'بطاقات الغرف', hint: 'إطار كرت الغرفة في القائمة الرئيسية' },
    { key: 'room_background', label: 'خلفيات الغرف', hint: 'خلفيات كاملة داخل الروم — طبّقها من إعدادات الغرفة' },
  ];

  var state = {
    apiBase: '',
    token: '',
    avatarUrl: '',
    displayName: '',
    roomId: '',
    bagMode: false,
    type: 'vip_badge',
    items: [],
    owned: {},
    equipped: {},
    expiresAt: {},
    leaseDays: 30,
    selectedId: null,
    busy: false,
    nativeFramePreview: false,
  };

  var el = {
    tabs: document.getElementById('tabs'),
    grid: document.getElementById('grid'),
    empty: document.getElementById('empty'),
    status: document.getElementById('status'),
    heroName: document.getElementById('heroName'),
    heroHint: document.getElementById('heroHint'),
    heroAvatar: document.getElementById('heroAvatar'),
    heroFrame: document.getElementById('heroFrame'),
    heroSvga: document.getElementById('heroSvga'),
    heroBadge: document.getElementById('heroBadge'),
    heroRoomCard: document.getElementById('heroRoomCard'),
    heroRoomCover: document.getElementById('heroRoomCover'),
    heroRoomFrame: document.getElementById('heroRoomFrame'),
    heroBg: document.getElementById('heroBg'),
    heroVideo: document.getElementById('heroVideo'),
    wearStage: document.getElementById('wearStage'),
    btnPrimary: document.getElementById('btnPrimary'),
    btnSecondary: document.getElementById('btnSecondary'),
    btnBag: document.getElementById('btnBag'),
    btnRecharge: document.getElementById('btnRecharge'),
    catalogCount: document.getElementById('catalogCount'),
    catalogCaption: document.getElementById('catalogCaption'),
  };

  function qs(name) {
    try {
      return new URLSearchParams(window.location.search).get(name) || '';
    } catch (e) {
      return '';
    }
  }

  function bridge() {
    return window.MallBridge || null;
  }

  function readBridgeConfig() {
    var b = bridge();
    if (!b) return;
    try {
      if (b.getApiBase) state.apiBase = String(b.getApiBase() || state.apiBase);
      if (b.getToken) state.token = String(b.getToken() || state.token);
      if (b.getAvatarUrl) state.avatarUrl = String(b.getAvatarUrl() || state.avatarUrl);
      if (b.getDisplayName) state.displayName = String(b.getDisplayName() || state.displayName);
      if (b.getRoomId) state.roomId = String(b.getRoomId() || state.roomId);
    } catch (e) { /* ignore */ }
  }

  function absUrl(url) {
    if (!url) return '';
    var u = String(url).trim();
    if (!u) return '';
    if (/^https?:\/\//i.test(u) || u.indexOf('data:') === 0) return u;
    var origin = state.apiBase.replace(/\/api\/v1\/?$/, '') || window.location.origin;
    if (u.charAt(0) !== '/') u = '/' + u;
    return origin + u;
  }

  /** Thumb for grids — keep webp/gif/png as-is (room cards are webp). Only SVGA→PNG sibling. */
  function stillPng(preview, anim) {
    var previewAbs = preview ? absUrl(preview) : '';
    var animAbs = anim ? absUrl(anim) : '';
    var p = String(preview || '').toLowerCase().split('?')[0];
    var a = String(anim || '').toLowerCase().split('?')[0];
    if (/\.(png|jpe?g|webp|gif)$/.test(p)) return previewAbs;
    if (/\.(png|jpe?g|webp|gif)$/.test(a)) return animAbs;
    var base = preview || anim || '';
    if (!base) return '';
    if (/\.(svga|mp4|webm|json|html)(\?|$)/i.test(base)) {
      var png = base.replace(/\.(svga|mp4|webm|json|html)(\?.*)?$/i, '.png$2');
      if (png !== base) return absUrl(png);
    }
    return absUrl(base);
  }

  function wearSrc(item) {
    if (!item) return '';
    var anim = item.animationUrl || '';
    var preview = item.previewUrl || '';
    // Prefer real motion (SVGA/GIF/video) like dashboard wearSrc.
    if (anim && mediaKind(absUrl(anim)) !== 'image') return absUrl(anim);
    if (preview && mediaKind(absUrl(preview)) !== 'image') return absUrl(preview);
    return absUrl(anim || preview || '');
  }

  function mediaKind(url) {
    var u = String(url || '').toLowerCase().split('?')[0];
    if (/\.mp4$|\.webm$|\.mov$/.test(u)) return 'video';
    if (/\.svga$/.test(u)) return 'svga';
    if (/\.gif$/.test(u)) return 'gif';
    if (/\.webp$/.test(u)) return 'gif';
    return 'image';
  }

  function hasNativeFramePreview() {
    try {
      var b = bridge();
      if (b && typeof b.hasNativeFramePreview === 'function' && b.hasNativeFramePreview()) return true;
      if (state.nativeFramePreview) return true;
    } catch (e) { /* ignore */ }
    return false;
  }

  function notifyNativeWear(item) {
    try {
      var b = bridge();
      if (!b || typeof b.previewWear !== 'function') return false;
      if (!item) {
        if (typeof b.clearWear === 'function') b.clearWear();
        return true;
      }
      b.previewWear(
        state.type,
        item.animationUrl || item.previewUrl || '',
        item.previewUrl || ''
      );
      return true;
    } catch (e) {
      return false;
    }
  }

  /** Android WebView: no Blob Worker + no ImageBitmap shim (both break SVGA there). */
  function makeParser() {
    if (!window.SVGA || !SVGA.Parser) return null;
    try {
      return new SVGA.Parser({
        isDisableWebWorker: true,
        isDisableImageBitmapShim: true,
      });
    } catch (e) {
      try { return new SVGA.Parser({ isDisableWebWorker: true }); } catch (e2) { return null; }
    }
  }

  /** In-memory blob cache — scroll back without re-downloading (~1MB SVGA). */
  var svgaBlobCache = Object.create(null);
  var svgaBlobOrder = [];
  var SVGA_BLOB_CACHE_MAX = 14;

  function rememberSvgaBlob(url, blob) {
    if (!url || !blob) return;
    if (!svgaBlobCache[url]) {
      svgaBlobOrder.push(url);
      while (svgaBlobOrder.length > SVGA_BLOB_CACHE_MAX) {
        var old = svgaBlobOrder.shift();
        delete svgaBlobCache[old];
      }
    }
    svgaBlobCache[url] = blob;
  }

  function getSvgaBlob(url) {
    if (svgaBlobCache[url]) return Promise.resolve(svgaBlobCache[url]);
    return fetch(url, { cache: 'force-cache', credentials: 'omit' }).then(function (res) {
      if (!res.ok) throw new Error('SVGA HTTP ' + res.status);
      return res.blob();
    }).then(function (blob) {
      rememberSvgaBlob(url, blob);
      return blob;
    });
  }

  /** Fetch SVGA bytes then play via blob — HTTP + memory cache. */
  function loadSvgaItem(url) {
    var parser = makeParser();
    if (!parser) return Promise.reject(new Error('SVGA missing'));
    return getSvgaBlob(url).then(function (blob) {
      var blobUrl = URL.createObjectURL(blob);
      return parser.load(blobUrl).then(function (videoItem) {
        try { URL.revokeObjectURL(blobUrl); } catch (e) { /* ignore */ }
        return { parser: parser, videoItem: videoItem };
      }, function (err) {
        try { URL.revokeObjectURL(blobUrl); } catch (e2) { /* ignore */ }
        try { parser.destroy && parser.destroy(); } catch (e3) { /* ignore */ }
        throw err;
      });
    });
  }

  function destroyPlayer(player) {
    if (!player) return;
    try { player.stop && player.stop(); } catch (e) { /* ignore */ }
    try { player.clear && player.clear(); } catch (e2) { /* ignore */ }
    try { player.destroy && player.destroy(); } catch (e3) { /* ignore */ }
  }

  var heroSvgaPlayer = null;
  var heroSvgaParser = null;
  var heroSvgaToken = 0;
  var cardSvga = {}; // id -> { player, parser, token }
  var cardObserver = null;
  var cardVideoObserver = null;

  function stopHeroSvga() {
    heroSvgaToken += 1;
    destroyPlayer(heroSvgaPlayer);
    heroSvgaPlayer = null;
    try { heroSvgaParser && heroSvgaParser.destroy && heroSvgaParser.destroy(); } catch (e) {}
    heroSvgaParser = null;
    if (el.heroSvga) {
      el.heroSvga.hidden = true;
      try {
        var ctx = el.heroSvga.getContext('2d');
        if (ctx) ctx.clearRect(0, 0, el.heroSvga.width || 0, el.heroSvga.height || 0);
      } catch (e2) { /* ignore */ }
    }
  }

  function playHeroSvga(url) {
    stopHeroSvga();
    if (!url || !el.heroSvga) return Promise.resolve(false);
    if (!window.SVGA || !SVGA.Player) return Promise.resolve(false);
    var token = ++heroSvgaToken;
    el.heroSvga.hidden = false;
    el.heroSvga.style.display = 'block';
    el.heroFrame.hidden = true;
    // Ensure stage visible even if a previous native-hero class lingered.
    document.body.classList.remove('native-hero');
    return loadSvgaItem(url).then(function (pack) {
      if (token !== heroSvgaToken) {
        try { pack.parser.destroy && pack.parser.destroy(); } catch (e) {}
        return false;
      }
      heroSvgaParser = pack.parser;
      heroSvgaPlayer = new SVGA.Player({
        container: el.heroSvga,
        loop: 0,
        isUseIntersectionObserver: false,
      });
      return heroSvgaPlayer.mount(pack.videoItem).then(function () {
        if (token !== heroSvgaToken) return false;
        heroSvgaPlayer.start();
        return true;
      });
    }).catch(function (err) {
      console.warn('mall hero svga failed', url, err);
      if (token === heroSvgaToken) {
        el.heroSvga.hidden = true;
        el.heroSvga.style.display = '';
      }
      return false;
    });
  }

  function stopCardSvga(id) {
    var slot = cardSvga[id];
    if (!slot) return;
    slot.token += 1;
    destroyPlayer(slot.player);
    try { slot.parser && slot.parser.destroy && slot.parser.destroy(); } catch (e) {}
    delete cardSvga[id];
  }

  function stopAllCardSvga() {
    Object.keys(cardSvga).forEach(stopCardSvga);
    cardSvga = {};
    if (cardObserver) {
      try { cardObserver.disconnect(); } catch (e) {}
      cardObserver = null;
    }
  }

  var cardSvgaQueue = [];
  var cardSvgaActive = 0;
  var CARD_SVGA_MAX = 4;
  var cardPlayTimers = Object.create(null);
  var cardTokens = Object.create(null);

  function playCardSvga(id, canvas, url) {
    if (!id || !canvas || !url) return;
    if (cardSvga[id] && cardSvga[id].url === url && cardSvga[id].player) return;
    stopCardSvga(id);
    if (!window.SVGA || !SVGA.Player) return;
    var token = (cardTokens[id] = (cardTokens[id] || 0) + 1);
    cardSvga[id] = { player: null, parser: null, token: token, url: url };
    function run() {
      if (!canvas.isConnected || cardTokens[id] !== token) {
        if (cardSvga[id] && cardSvga[id].token === token) stopCardSvga(id);
        return;
      }
      cardSvgaActive += 1;
      loadSvgaItem(url).then(function (pack) {
        var slot = cardSvga[id];
        if (!slot || slot.token !== token || cardTokens[id] !== token || !canvas.isConnected) {
          try { pack.parser.destroy && pack.parser.destroy(); } catch (e) {}
          return;
        }
        slot.parser = pack.parser;
        var player = new SVGA.Player({
          container: canvas,
          loop: 0,
          isUseIntersectionObserver: false,
        });
        slot.player = player;
        return player.mount(pack.videoItem).then(function () {
          if (!cardSvga[id] || cardSvga[id].token !== token || cardTokens[id] !== token) {
            destroyPlayer(player);
            return;
          }
          player.start();
          try {
            var parent = canvas.parentNode;
            if (parent) {
              var fallback = parent.querySelector('.still-fallback');
              if (fallback) fallback.style.opacity = '0';
            }
          } catch (e) { /* ignore */ }
        });
      }).catch(function (err) {
        console.warn('mall card svga failed', url, err);
        if (cardTokens[id] === token) stopCardSvga(id);
      }).then(function () {
        cardSvgaActive = Math.max(0, cardSvgaActive - 1);
        pumpCardSvgaQueue();
      });
    }
    if (cardSvgaActive >= CARD_SVGA_MAX) {
      cardSvgaQueue.push(run);
    } else {
      run();
    }
  }

  function pumpCardSvgaQueue() {
    while (cardSvgaActive < CARD_SVGA_MAX && cardSvgaQueue.length) {
      var next = cardSvgaQueue.shift();
      try { next(); } catch (e) { /* ignore */ }
    }
  }

  function scheduleCardSvga(id, canvas, url, play) {
    if (cardPlayTimers[id]) {
      clearTimeout(cardPlayTimers[id]);
      delete cardPlayTimers[id];
    }
    if (!play) {
      cardTokens[id] = (cardTokens[id] || 0) + 1;
      stopCardSvga(id);
      try {
        var parent = canvas && canvas.parentNode;
        if (parent) {
          var fallback = parent.querySelector('.still-fallback');
          if (fallback) fallback.style.opacity = '';
        }
      } catch (e) { /* ignore */ }
      return;
    }
    cardPlayTimers[id] = setTimeout(function () {
      delete cardPlayTimers[id];
      if (canvas && canvas.isConnected) playCardSvga(id, canvas, url);
    }, 90);
  }

  function ensureCardObserver() {
    if (cardObserver || typeof IntersectionObserver === 'undefined') return;
    cardObserver = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        var canvas = entry.target;
        var id = canvas.getAttribute('data-id');
        var url = canvas.getAttribute('data-svga');
        if (!id || !url) return;
        if (entry.isIntersecting && entry.intersectionRatio > 0.08) {
          scheduleCardSvga(id, canvas, url, true);
        } else {
          scheduleCardSvga(id, canvas, url, false);
        }
      });
    }, { root: null, rootMargin: '120px 0px', threshold: [0, 0.08, 0.25] });
  }

  function setStatus(msg, kind) {
    if (!msg) {
      el.status.hidden = true;
      el.status.textContent = '';
      return;
    }
    el.status.hidden = false;
    el.status.className = 'status' + (kind ? ' ' + kind : '');
    el.status.textContent = msg;
  }

  function toastNative(msg) {
    try {
      var b = bridge();
      if (b && b.toast) b.toast(String(msg || ''));
    } catch (e) { /* ignore */ }
  }

  function looksInsufficient(msg) {
    var t = String(msg || '').toLowerCase();
    return t.indexOf('insufficient') >= 0
      || t.indexOf('not enough') >= 0
      || t.indexOf('balance') >= 0
      || t.indexOf('coins') >= 0
      || String(msg || '').indexOf('رصيد') >= 0
      || String(msg || '').indexOf('عملات') >= 0
      || String(msg || '').indexOf('غير كاف') >= 0
      || String(msg || '').indexOf('اشحن') >= 0;
  }

  function openRechargeNative(msg) {
    try {
      var b = bridge();
      if (b && b.openRechargeIfNeeded) {
        b.openRechargeIfNeeded(String(msg || 'رصيد غير كافٍ'));
        return;
      }
      if (b && b.openRecharge) {
        if (msg) toastNative(msg);
        b.openRecharge();
        return;
      }
    } catch (e) { /* ignore */ }
    toastNative(msg || 'رصيد غير كافٍ');
  }

  function authHeaders() {
    var h = { Accept: 'application/json', 'Content-Type': 'application/json' };
    if (state.token) h.Authorization = 'Bearer ' + state.token;
    return h;
  }

  function apiUrl(path) {
    var base = state.apiBase || (window.location.origin + '/api/v1/');
    if (base.charAt(base.length - 1) !== '/') base += '/';
    return base + path.replace(/^\//, '');
  }

  async function api(path, opts) {
    var res = await fetch(apiUrl(path), Object.assign({ headers: authHeaders() }, opts || {}));
    var json = null;
    try { json = await res.json(); } catch (e) { json = null; }
    if (!res.ok) {
      var msg = (json && (json.message || json.error)) || ('HTTP ' + res.status);
      throw new Error(typeof msg === 'string' ? msg : 'فشل الطلب');
    }
    if (json && typeof json === 'object' && 'success' in json) {
      if (!json.success) throw new Error(json.message || 'فشل الطلب');
      return json.data;
    }
    return json;
  }

  function selectedItem() {
    if (!state.selectedId) return null;
    for (var i = 0; i < state.items.length; i++) {
      if (state.items[i].id === state.selectedId) return state.items[i];
    }
    return null;
  }

  function hideAllHeroMedia() {
    stopHeroSvga();
    el.heroFrame.hidden = true;
    el.heroFrame.classList.remove('pulse');
    el.heroBadge.hidden = true;
    el.heroRoomCard.hidden = true;
    el.heroBg.hidden = true;
    el.heroVideo.hidden = true;
    el.heroVideo.controls = false;
    if (el.wearStage) el.wearStage.classList.remove('entry-effect-preview');
    el.heroAvatar.hidden = false;
    try { el.heroVideo.pause(); el.heroVideo.removeAttribute('src'); el.heroVideo.load(); } catch (e) {}
  }

  function showStaticFrame(src, pulse) {
    el.heroFrame.hidden = false;
    el.heroFrame.src = src || '';
    if (pulse) el.heroFrame.classList.add('pulse');
    else el.heroFrame.classList.remove('pulse');
  }

  function updateHero() {
    var item = selectedItem();
    var tab = TABS.find(function (t) { return t.key === state.type; }) || TABS[0];
    el.heroName.textContent = item ? (item.name || item.code || '—') : (state.displayName || 'معاينة');
    el.heroHint.textContent = item ? (tab.hint || '') : 'اختر عنصراً لمعاينته على صورتك';
    el.heroAvatar.src = absUrl(state.avatarUrl) || placeholderAvatar();
    el.heroRoomCover.src = absUrl(state.avatarUrl) || placeholderAvatar();

    // Always play motion in the mall WebView (one selected SVGA). Keep grid on light PNGs.
    document.body.classList.remove('native-hero');
    try {
      var b = bridge();
      if (b && typeof b.clearWear === 'function') b.clearWear();
    } catch (e) { /* ignore */ }

    hideAllHeroMedia();
    if (!item) {
      syncActionButtons(null);
      return;
    }

    var thumb = stillPng(item.previewUrl, item.animationUrl);
    var wear = wearSrc(item);
    var kind = mediaKind(wear);

    if (state.type === 'vip_badge') {
      if (kind === 'svga' && wear) {
        // Show PNG instantly, then swap to SVGA when ready.
        showStaticFrame(thumb || wear, false);
        playHeroSvga(wear).then(function (ok) {
          if (!ok && selectedItem() && selectedItem().id === item.id) {
            showStaticFrame(thumb || wear, false);
          }
        });
      } else if (kind === 'video' && wear) {
        el.heroVideo.hidden = false;
        el.heroVideo.controls = true;
        el.heroVideo.muted = false;
        el.heroVideo.src = wear;
        el.heroVideo.play().catch(function () { /* user can start playback with controls */ });
      } else if (kind === 'gif' && wear) {
        showStaticFrame(wear, false);
      } else {
        showStaticFrame(thumb || wear, false);
      }
    } else if (state.type === 'level_badge') {
      el.heroBadge.hidden = false;
      el.heroBadge.src = thumb || wear;
    } else if (state.type === 'room_card') {
      el.heroAvatar.hidden = true;
      el.heroRoomCard.hidden = false;
      el.heroRoomFrame.src = thumb || wear || absUrl(item.previewUrl) || '';
    } else if (state.type === 'room_background') {
      el.heroAvatar.hidden = true;
      el.heroBg.hidden = false;
      el.heroBg.src = thumb || wear;
    } else if (state.type === 'entry_effect') {
      // Keep the account avatar visible: entrance media is layered over it,
      // so users can judge the effect against their actual profile photo.
      el.heroAvatar.hidden = false;
      if (el.wearStage) el.wearStage.classList.add('entry-effect-preview');
      if (kind === 'video' && wear) {
        el.heroVideo.hidden = false;
        el.heroVideo.controls = false;
        el.heroVideo.muted = true;
        el.heroVideo.defaultMuted = true;
        el.heroVideo.loop = true;
        el.heroVideo.playsInline = true;
        el.heroVideo.setAttribute('aria-label', 'معاينة الدخولية المتحركة');
        el.heroVideo.src = wear;
        var videoPlay = el.heroVideo.play();
        if (videoPlay && videoPlay.catch) videoPlay.catch(function () {
          // Muted playback is attempted automatically; the status stays usable if blocked.
        });
      } else if (kind === 'svga' && wear) {
        playHeroSvga(wear).then(function (ok) {
          if (!ok && selectedItem() && selectedItem().id === item.id) {
            el.heroBg.hidden = false;
            el.heroBg.src = thumb || wear;
          }
        });
      } else {
        el.heroBg.hidden = false;
        el.heroBg.src = (kind === 'gif' ? wear : thumb) || wear;
      }
    }

    syncActionButtons(item);
  }

  function renewPrice(full) {
    var n = Math.max(0, Math.floor(Number(full) || 0));
    return Math.ceil(n / 2);
  }

  function isActiveOwned(id) {
    if (!state.owned[id]) return false;
    var exp = state.expiresAt[id];
    if (!exp) return true; // legacy permanent
    return new Date(exp).getTime() > Date.now();
  }

  function daysLeft(id) {
    var exp = state.expiresAt[id];
    if (!exp) return null;
    var ms = new Date(exp).getTime() - Date.now();
    if (ms <= 0) return 0;
    return Math.max(1, Math.ceil(ms / (24 * 60 * 60 * 1000)));
  }

  function coinMarkup(amount) {
    var n = Math.max(0, Number(amount) || 0);
    return '<span class="coin-price"><span class="coin-price-icon" aria-label="كوينز">🪙</span>' +
      n.toLocaleString('en-US') + '</span>';
  }

  function syncActionButtons(item) {
    el.btnSecondary.hidden = true;
    if (!item) {
      el.btnPrimary.disabled = true;
      el.btnPrimary.textContent = 'ارتدِ';
      return;
    }
    var owned = isActiveOwned(item.id);
    var equipped = !!state.equipped[item.id];
    var left = daysLeft(item.id);
    el.btnPrimary.disabled = state.busy;

    if (state.type === 'room_background') {
      if (!owned) {
        el.btnPrimary.innerHTML = item.coinPrice > 0
          ? ('شراء ' + coinMarkup(item.coinPrice) + ' · 30 يوم')
          : 'احصل مجاناً · 30 يوم';
        el.btnPrimary.className = 'btn btn-buy';
      } else if (state.roomId) {
        el.btnPrimary.textContent = 'طبّق على الروم';
        el.btnPrimary.className = 'btn btn-primary';
      } else {
        el.btnPrimary.textContent = left != null
          ? ('مشتراة · متبقي ' + left + ' يوم')
          : 'مشتراة — من إعدادات الروم';
        el.btnPrimary.className = 'btn btn-owned';
        el.btnPrimary.disabled = true;
      }
      return;
    }

    if (!owned) {
      el.btnPrimary.innerHTML = item.coinPrice > 0
        ? ('شراء ' + coinMarkup(item.coinPrice) + ' · 30 يوم')
        : 'احصل مجاناً · 30 يوم';
      el.btnPrimary.className = 'btn btn-buy';
    } else if (equipped) {
      el.btnPrimary.textContent = left != null ? ('خلع · ' + left + ' يوم') : 'خلع';
      el.btnPrimary.className = 'btn btn-primary';
      el.btnSecondary.hidden = true;
    } else {
      el.btnPrimary.textContent = 'ارتدِ';
      el.btnPrimary.className = 'btn btn-primary';
      el.btnSecondary.hidden = true;
    }
  }

  function placeholderAvatar() {
    return 'data:image/svg+xml,' + encodeURIComponent(
      '<svg xmlns="http://www.w3.org/2000/svg" width="128" height="128">' +
      '<rect width="128" height="128" rx="64" fill="#d6d0c4"/>' +
      '<circle cx="64" cy="52" r="24" fill="#b7aea0"/>' +
      '<ellipse cx="64" cy="108" rx="40" ry="28" fill="#b7aea0"/>' +
      '</svg>'
    );
  }

  function renderTabs() {
    el.tabs.innerHTML = '';
    TABS.forEach(function (t) {
      var btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'tab' + (t.key === state.type ? ' active' : '');
      btn.textContent = t.label;
      btn.addEventListener('click', function () {
        if (state.type === t.key) return;
        state.type = t.key;
        state.selectedId = null;
        renderTabs();
        loadCatalog();
      });
      el.tabs.appendChild(btn);
    });
  }

  function stopAllCardVideo() {
    if (cardVideoObserver) {
      try { cardVideoObserver.disconnect(); } catch (e) { /* ignore */ }
      cardVideoObserver = null;
    }
    try {
      el.grid.querySelectorAll('video.mall-video-preview').forEach(function (video) {
        try { video.pause(); } catch (e) { /* ignore */ }
        try { video.removeAttribute('src'); video.load(); } catch (e2) { /* ignore */ }
      });
    } catch (e3) { /* ignore */ }
  }

  function ensureCardVideoObserver() {
    if (cardVideoObserver || typeof IntersectionObserver === 'undefined') return;
    cardVideoObserver = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        var video = entry.target;
        if (!video || !video.isConnected) return;
        if (entry.isIntersecting && entry.intersectionRatio > 0.08) {
          var play = video.play();
          if (play && typeof play.catch === 'function') play.catch(function () { /* native controls remain available */ });
        } else {
          try { video.pause(); } catch (e) { /* ignore */ }
        }
      });
    }, { root: null, rootMargin: '100px 0px', threshold: [0, 0.08, 0.25] });
  }

  function renderGrid() {
    stopAllCardVideo();
    stopAllCardSvga();
    Object.keys(cardPlayTimers).forEach(function (id) {
      clearTimeout(cardPlayTimers[id]);
      delete cardPlayTimers[id];
    });
    cardSvgaQueue = [];
    el.grid.innerHTML = '';
    var list = state.items.slice();
    if (state.bagMode) {
      list = list.filter(function (it) { return !!state.owned[it.id]; });
    }
    el.empty.hidden = list.length > 0;
    el.empty.textContent = state.bagMode ? 'حقيبتك فارغة في هذا القسم' : 'لا توجد عناصر في هذا القسم';
    if (el.catalogCount) el.catalogCount.textContent = list.length ? (list.length + ' عنصر') : '';
    if (el.catalogCaption) el.catalogCaption.textContent = state.bagMode ? 'العناصر الموجودة في حقيبتك' : 'مختارات هذا القسم';
    syncBagButton();

    var pendingSvga = [];
    var pendingVideos = [];
    list.forEach(function (item) {
      var card = document.createElement('article');
      card.className = 'card' + (item.id === state.selectedId ? ' selected' : '');
      card.addEventListener('click', function () {
        state.selectedId = item.id;
        updateHero();
        var prev = el.grid.querySelector('.card.selected');
        if (prev) prev.classList.remove('selected');
        card.classList.add('selected');
      });

      var thumb = document.createElement('div');
      thumb.className = 'thumb';
      var still = stillPng(item.previewUrl, item.animationUrl);
      var wear = wearSrc(item);
      var kind = mediaKind(wear);
      if (state.type === 'vip_badge') {
        var face = document.createElement('img');
        face.className = 'face';
        face.loading = 'lazy';
        face.decoding = 'async';
        face.src = absUrl(state.avatarUrl) || placeholderAvatar();
        face.alt = '';
        thumb.appendChild(face);
        // PNG paints instantly for fast scroll; SVGA starts when card is on screen.
        if (still) {
          var stillImg = document.createElement('img');
          stillImg.className = 'overlay still-fallback';
          stillImg.loading = 'lazy';
          stillImg.decoding = 'async';
          stillImg.src = still;
          stillImg.alt = '';
          thumb.appendChild(stillImg);
        }
        if (kind === 'svga' && wear) {
          var canvas = document.createElement('canvas');
          canvas.className = 'overlay svga-canvas';
          canvas.width = 200;
          canvas.height = 200;
          canvas.setAttribute('data-id', item.id);
          canvas.setAttribute('data-svga', wear);
          thumb.appendChild(canvas);
          pendingSvga.push(canvas);
        } else if (kind === 'video' && wear) {
          var frameVideo = document.createElement('video');
          frameVideo.className = 'overlay mall-video-preview';
          frameVideo.src = wear;
          frameVideo.muted = true;
          frameVideo.defaultMuted = true;
          frameVideo.loop = true;
          frameVideo.playsInline = true;
          frameVideo.preload = 'none';
          frameVideo.setAttribute('aria-label', (item.name || 'معاينة الإطار'));
          frameVideo.addEventListener('error', function () { frameVideo.classList.add('media-error'); });
          thumb.appendChild(frameVideo);
          pendingVideos.push(frameVideo);
        }
      } else if (kind === 'video' && wear) {
        if (still && still !== wear) {
          var videoFallback = document.createElement('img');
          videoFallback.className = 'overlay still-fallback';
          videoFallback.loading = 'lazy';
          videoFallback.decoding = 'async';
          videoFallback.src = still;
          videoFallback.alt = '';
          thumb.appendChild(videoFallback);
        }
        var mediaVideo = document.createElement('video');
        mediaVideo.className = 'overlay mall-video-preview';
        mediaVideo.src = wear;
        mediaVideo.muted = true;
        mediaVideo.defaultMuted = true;
        mediaVideo.loop = true;
        mediaVideo.playsInline = true;
        mediaVideo.preload = 'none';
        mediaVideo.setAttribute('aria-label', (item.name || 'معاينة الحركة'));
        mediaVideo.addEventListener('error', function () { mediaVideo.classList.add('media-error'); });
        thumb.appendChild(mediaVideo);
        pendingVideos.push(mediaVideo);
      } else if (still) {
        var img = document.createElement('img');
        img.loading = 'lazy';
        img.decoding = 'async';
        img.src = still;
        img.alt = '';
        thumb.appendChild(img);
      }
      card.appendChild(thumb);

      var name = document.createElement('div');
      name.className = 'card-name';
      name.textContent = item.name || item.code || '—';
      card.appendChild(name);

      var price = document.createElement('div');
      price.className = 'card-price';
      if (isActiveOwned(item.id)) {
        var left = daysLeft(item.id);
        price.textContent = state.equipped[item.id]
          ? (left != null ? ('مُرتدى · ' + left + 'ي') : 'مُرتدى')
          : (left != null ? ('مشتراة · ' + left + 'ي') : 'مشتراة');
      } else {
        price.innerHTML = item.coinPrice > 0
          ? (coinMarkup(item.coinPrice) + ' · 30 يوم')
          : 'مجاني · 30 يوم';
      }
      card.appendChild(price);

      var action = document.createElement('button');
      action.type = 'button';
      var owned = isActiveOwned(item.id);
      var equipped = !!state.equipped[item.id];
      if (!owned) {
        action.className = 'btn btn-buy';
        action.innerHTML = item.coinPrice > 0 ? ('شراء ' + coinMarkup(item.coinPrice)) : 'احصل';
      } else if (state.type === 'room_background') {
        action.className = 'btn btn-owned';
        action.textContent = state.roomId ? 'طبّق' : 'مشتراة';
        if (!state.roomId) action.disabled = true;
      } else if (equipped) {
        action.className = 'btn btn-buy';
        action.innerHTML = 'تجديد ' + coinMarkup(renewPrice(item.coinPrice));
      } else {
        action.className = 'btn btn-primary';
        action.textContent = 'ارتدِ';
      }
      action.addEventListener('click', function (ev) {
        ev.stopPropagation();
        state.selectedId = item.id;
        updateHero();
        var prev = el.grid.querySelector('.card.selected');
        if (prev) prev.classList.remove('selected');
        card.classList.add('selected');
        onPrimary();
      });
      card.appendChild(action);
      el.grid.appendChild(card);
    });

    if (pendingSvga.length) {
      ensureCardObserver();
      if (cardObserver) {
        pendingSvga.forEach(function (canvas) { cardObserver.observe(canvas); });
      } else {
        pendingSvga.slice(0, CARD_SVGA_MAX).forEach(function (canvas) {
          playCardSvga(
            canvas.getAttribute('data-id'),
            canvas,
            canvas.getAttribute('data-svga')
          );
        });
      }
    }
    if (pendingVideos.length) {
      ensureCardVideoObserver();
      if (cardVideoObserver) {
        pendingVideos.forEach(function (video) { cardVideoObserver.observe(video); });
      } else {
        pendingVideos.slice(0, 2).forEach(function (video) {
          var play = video.play();
          if (play && typeof play.catch === 'function') play.catch(function () {});
        });
      }
    }
  }

  async function loadInventory() {
    if (!state.token) {
      state.owned = {};
      state.equipped = {};
      state.expiresAt = {};
      return;
    }
    var rows = await api('cosmetics/inventory');
    var owned = {};
    var equipped = {};
    var expiresAt = {};
    var now = Date.now();
    (rows || []).forEach(function (row) {
      var id = row.cosmeticId || (row.cosmetic && row.cosmetic.id);
      if (!id) return;
      var exp = row.expiresAt || null;
      expiresAt[id] = exp;
      var active = !exp || new Date(exp).getTime() > now;
      if (active) {
        owned[id] = true;
        if (row.equipped) equipped[id] = true;
      }
    });
    state.owned = owned;
    state.equipped = equipped;
    state.expiresAt = expiresAt;
  }

  async function loadCatalog() {
    setStatus('جاري التحميل…');
    try {
      await loadInventory();
      var data = await api('cosmetics?type=' + encodeURIComponent(state.type));
      state.items = Array.isArray(data) ? data : [];
      if (!state.selectedId && state.items.length) {
        var prefer = state.items.find(function (it) { return state.equipped[it.id]; })
          || state.items.find(function (it) { return state.owned[it.id]; })
          || state.items[0];
        state.selectedId = prefer ? prefer.id : null;
      }
      setStatus('');
      updateHero();
      renderGrid();
    } catch (err) {
      state.items = [];
      setStatus(err.message || 'تعذر التحميل', 'err');
      updateHero();
      renderGrid();
    }
  }

  async function onPrimary() {
    var item = selectedItem();
    if (!item || state.busy) return;
    if (isActiveOwned(item.id) && state.equipped[item.id] && state.type !== 'room_background') {
      onUnequip();
      return;
    }
    if (!state.token) {
      setStatus('سجّل الدخول أولاً', 'err');
      toastNative('سجّل الدخول أولاً');
      return;
    }
    state.busy = true;
    syncActionButtons(item);
    try {
      var owned = isActiveOwned(item.id);
      if (!owned) {
        var bought = await api('cosmetics/purchase', {
          method: 'POST',
          body: JSON.stringify({ cosmeticId: item.id }),
        });
        state.owned[item.id] = true;
        if (bought && bought.expiresAt) state.expiresAt[item.id] = bought.expiresAt;
        setStatus('تم الشراء لمدة 30 يوماً', 'ok');
        toastNative('تم الشراء · 30 يوم');
        await loadInventory();
      } else if (state.type === 'room_background') {
        if (!state.roomId) {
          setStatus('افتح المول من داخل الروم لتطبيق الخلفية', 'err');
        } else {
          await api('rooms/' + encodeURIComponent(state.roomId) + '/background', {
            method: 'PATCH',
            body: JSON.stringify({
              backgroundUrl: item.animationUrl || item.previewUrl || '',
            }),
          });
          setStatus('تم تطبيق خلفية الروم', 'ok');
          toastNative('تم تطبيق الخلفية');
        }
      } else if (state.equipped[item.id]) {
        await renewItem(item);
      } else {
        await api('cosmetics/equip', {
          method: 'POST',
          body: JSON.stringify({ cosmeticId: item.id }),
        });
        await loadInventory();
        setStatus('تم الارتداء', 'ok');
        toastNative('تم الارتداء');
        try {
          var b = bridge();
          if (b && b.onEquipped) b.onEquipped(item.id, state.type);
        } catch (e) { /* ignore */ }
      }
      updateHero();
      renderGrid();
    } catch (err) {
      var em = (err && err.message) ? err.message : 'فشل العملية';
      setStatus(em, 'err');
      if (looksInsufficient(em)) {
        openRechargeNative(em);
      } else {
        toastNative(em);
      }
    } finally {
      state.busy = false;
      syncActionButtons(selectedItem());
    }
  }

  async function renewItem(item) {
    var res = await api('cosmetics/purchase', {
      method: 'POST',
      body: JSON.stringify({ cosmeticId: item.id }),
    });
    if (res && res.expiresAt) state.expiresAt[item.id] = res.expiresAt;
    state.owned[item.id] = true;
    await loadInventory();
    setStatus('تم التجديد بنصف السعر (+30 يوم)', 'ok');
    toastNative('تم التجديد · +30 يوم');
  }

  async function onSecondary() {
    // Renewals are intentionally unavailable while a lease is active.
    var item = selectedItem();
    if (!item || state.busy || isActiveOwned(item.id)) return;
  }

  async function onUnequip() {
    var item = selectedItem();
    if (!item || state.busy || !state.equipped[item.id]) return;
    state.busy = true;
    try {
      await api('cosmetics/unequip', {
        method: 'POST',
        body: JSON.stringify({ cosmeticId: item.id }),
      });
      await loadInventory();
      setStatus('تمت الإزالة', 'ok');
      toastNative('تمت الإزالة');
      updateHero();
      renderGrid();
    } catch (err) {
      setStatus(err.message || 'فشل الإزالة', 'err');
    } finally {
      state.busy = false;
      syncActionButtons(selectedItem());
    }
  }

  window.MallPage = {
    setConfig: function (cfg) {
      cfg = cfg || {};
      if (cfg.apiBase) state.apiBase = cfg.apiBase;
      if (cfg.token != null) state.token = cfg.token;
      if (cfg.avatarUrl != null) state.avatarUrl = cfg.avatarUrl;
      if (cfg.displayName != null) state.displayName = cfg.displayName;
      if (cfg.roomId != null) state.roomId = cfg.roomId;
      if (cfg.bagMode != null) state.bagMode = !!cfg.bagMode;
      if (cfg.type) state.type = cfg.type;
      if (cfg.nativeFramePreview != null) state.nativeFramePreview = !!cfg.nativeFramePreview;
      renderTabs();
      loadCatalog();
    },
    setBagMode: function (on) {
      state.bagMode = !!on;
      renderGrid();
    },
    reload: function () { loadCatalog(); },
  };

  el.btnPrimary.addEventListener('click', onPrimary);
  el.btnSecondary.addEventListener('click', function () {
    var item = selectedItem();
    if (item && state.equipped[item.id] && state.type !== 'room_background') {
      onUnequip();
    } else {
      onSecondary();
    }
  });

  function syncBagButton() {
    if (!el.btnBag) return;
    el.btnBag.setAttribute('aria-pressed', state.bagMode ? 'true' : 'false');
    el.btnBag.classList.toggle('is-active', !!state.bagMode);
    var label = el.btnBag.querySelector('.action-label');
    if (label) label.textContent = state.bagMode ? 'المتجر' : 'حقيبتي';
    el.btnBag.setAttribute('aria-label', state.bagMode ? 'العودة إلى المتجر' : 'حقيبتي');
  }

  if (el.btnBag) el.btnBag.addEventListener('click', function () {
    state.bagMode = !state.bagMode;
    state.selectedId = null;
    syncBagButton();
    loadCatalog();
  });

  if (el.btnRecharge) el.btnRecharge.addEventListener('click', function () {
    openRechargeNative('افتح صفحة الشحن لإضافة رصيد إلى محفظتك');
    if (!bridge()) setStatus('ميزة الشحن متاحة من داخل التطبيق بعد تسجيل الدخول.', 'err');
  });

  // Bootstrap from query + bridge
  state.type = qs('type') || 'vip_badge';
  state.bagMode = qs('bag') === '1' || qs('bag') === 'true';
  state.roomId = qs('roomId') || '';
  state.apiBase = qs('apiBase') || (window.location.origin + '/api/v1/');
  readBridgeConfig();
  renderTabs();
  updateHero();

  // Android injects config after load; also self-start for browser testing.
  if (state.token || !bridge()) {
    loadCatalog();
  } else {
    setTimeout(function () {
      readBridgeConfig();
      loadCatalog();
    }, 120);
  }
})();
