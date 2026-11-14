(function (global) {
  'use strict';

  function resolveAsset(rel) {
    try {
      return new URL(rel, document.baseURI || location.href).href;
    } catch (e) {
      return rel;
    }
  }

  const AudioKit = {
    ctx: null,
    unlocked: false,
    buffers: {},
    ensure() {
      if (!this.ctx) {
        const AC = window.AudioContext || window.webkitAudioContext;
        if (!AC) return null;
        this.ctx = new AC();
      }
      if (this.ctx.state === 'suspended') this.ctx.resume().catch(() => {});
      this.unlocked = true;
      return this.ctx;
    },
    async load(name, rel) {
      try {
        const ctx = this.ensure();
        if (!ctx) return;
        const res = await fetch(resolveAsset(rel));
        const buf = await res.arrayBuffer();
        this.buffers[name] = await ctx.decodeAudioData(buf.slice(0));
      } catch (e) { /* ignore offline */ }
    },
    play(name, opts) {
      const ctx = this.ensure();
      if (!ctx) return;
      const buf = this.buffers[name];
      if (buf) {
        const src = ctx.createBufferSource();
        const gain = ctx.createGain();
        gain.gain.value = (opts && opts.volume != null) ? opts.volume : 0.55;
        src.buffer = buf;
        src.connect(gain);
        gain.connect(ctx.destination);
        src.start(0);
        return;
      }
      // synth fallback
      const o = ctx.createOscillator();
      const g = ctx.createGain();
      o.type = 'triangle';
      o.frequency.value = name === 'lose' ? 180 : name === 'win' ? 660 : 440;
      g.gain.value = 0.0001;
      o.connect(g); g.connect(ctx.destination);
      const t = ctx.currentTime;
      g.gain.exponentialRampToValueAtTime(0.2, t + 0.01);
      g.gain.exponentialRampToValueAtTime(0.0001, t + 0.18);
      o.start(t); o.stop(t + 0.2);
    },
  };

  async function preloadSfx() {
    const base = 'sfx/';
    // relative from /games/*.html → common is sibling; sfx is sibling
    const map = {
      click: '../games/sfx/click.wav',
      spin: '../games/sfx/spin.wav',
      win: '../games/sfx/win.wav',
      lose: '../games/sfx/lose.wav',
      dice: '../games/sfx/dice.wav',
      chip: '../games/sfx/chip.wav',
      coin: '../games/sfx/coin.wav',
    };
    // From /games/foo.html paths should be ./sfx/
    const local = {
      click: './sfx/click.wav',
      spin: './sfx/spin.wav',
      win: './sfx/win.wav',
      lose: './sfx/lose.wav',
      dice: './sfx/dice.wav',
      chip: './sfx/chip.wav',
      coin: './sfx/coin.wav',
      magic: './sfx/magic.wav',
      whoosh: './sfx/whoosh.wav',
      pop: './sfx/pop.wav',
      hit: './sfx/hit.wav',
      tick: './sfx/tick.wav',
    };
    await Promise.all(Object.keys(local).map((k) => AudioKit.load(k, local[k])));
  }

  function qs(sel) { return document.querySelector(sel); }
  function setMsg(el, text, kind) {
    if (!el) return;
    el.textContent = text || '';
    el.classList.remove('win', 'lose');
    if (kind) el.classList.add(kind);
  }

  function unlockOnGesture() {
    const once = () => { AudioKit.ensure(); preloadSfx(); window.removeEventListener('pointerdown', once); };
    window.addEventListener('pointerdown', once, { once: true });
  }

  const STAMPS = { stamp_heart: '💖', stamp_crown: '👑', stamp_vip: '⭐' };
  const EMOJIS = { emoji_laugh: '😂', emoji_fire: '🔥', emoji_crown: '👑' };
  function applyAuraCosmetics(c) {
    c = c || window.__AURA_COSMETICS__ || {};
    const stamp = qs('#stampBadge');
    const emoji = qs('#emojiBadge');
    if (stamp) {
      const t = STAMPS[c.stamp] || '';
      stamp.textContent = t; stamp.classList.toggle('on', !!t);
    }
    if (emoji) {
      const t = EMOJIS[c.emoji] || '';
      emoji.textContent = t; emoji.classList.toggle('on', !!t);
    }
  }
  window.applyAuraCosmetics = applyAuraCosmetics;

  // Bridge helpers for Android WebView
  function postNative(type, payload) {
    try {
      if (window.AuraNative && typeof window.AuraNative.postMessage === 'function') {
        window.AuraNative.postMessage(JSON.stringify({ type, ...(payload || {}) }));
      }
    } catch (e) {}
  }

  function apiBase() {
    try {
      const q = new URLSearchParams(location.search || '');
      let base = (q.get('apiBase') || '').replace(/\/$/, '');
      if (!base) {
        if (location.protocol === 'http:' || location.protocol === 'https:') base = location.origin;
        else base = 'https://api.adnova.bbs.tr';
      }
      return base.replace(/\/api\/v1\/?$/, '');
    } catch (e) {
      return 'https://api.adnova.bbs.tr';
    }
  }

  function authToken() {
    try {
      const q = new URLSearchParams(location.search || '');
      return q.get('token') || q.get('accessToken') || window.__AURA_TOKEN__ || '';
    } catch (e) { return ''; }
  }

  function roomId() {
    try {
      return new URLSearchParams(location.search || '').get('roomId') || '';
    } catch (e) { return ''; }
  }

  async function api(path, opts) {
    const base = apiBase();
    const url = path.startsWith('http') ? path : (base + '/api/v1' + path);
    const headers = Object.assign({ 'Content-Type': 'application/json' }, (opts && opts.headers) || {});
    const tok = authToken();
    if (tok) headers.Authorization = 'Bearer ' + tok;
    const res = await fetch(url, Object.assign({}, opts || {}, { headers }));
    const json = await res.json().catch(() => ({}));
    if (!res.ok || json.success === false) {
      const msg = (json && (json.message || json.error)) || ('HTTP ' + res.status);
      throw new Error(typeof msg === 'string' ? msg : JSON.stringify(msg));
    }
    return json.data !== undefined ? json.data : json;
  }

  global.AuraGame = {
    Audio: AudioKit,
    preloadSfx,
    unlockOnGesture,
    setMsg,
    qs,
    postNative,
    applyAuraCosmetics,
    api,
    apiBase,
    authToken,
    roomId,
    randInt(a, b) { return a + Math.floor(Math.random() * (b - a + 1)); },
  };

  unlockOnGesture();
  if (window.__AURA_COSMETICS__) applyAuraCosmetics(window.__AURA_COSMETICS__);
})(window);
