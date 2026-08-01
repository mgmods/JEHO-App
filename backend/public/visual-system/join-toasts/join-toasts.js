(function () {
  "use strict";

  /** Built-in join toast variants — pure CSS, no PNG assets. */
  const catalog = [
    { id: "normal", name: "عادي", accent: "#5ec8ff", glow: "#1a6a9c" },
    { id: "vip", name: "VIP", accent: "#ffd56a", glow: "#9a6a00" },
    { id: "gold", name: "ذهبي", accent: "#ffb21d", glow: "#a86a00" },
    { id: "diamond", name: "ماسي", accent: "#b7e0ff", glow: "#3d6a9c" },
    { id: "legend", name: "أسطوري", accent: "#ff6a3a", glow: "#8a1d08" },
    { id: "supporter", name: "داعم", accent: "#d39bff", glow: "#5b1d9c" }
  ];
  const byId = new Map(catalog.map((item) => [item.id, item]));
  let activeToast;

  function avatarNode(avatar, username) {
    const el = document.createElement("div");
    el.className = "join-toast__avatar";
    if (avatar) {
      const img = document.createElement("img");
      img.src = avatar;
      img.alt = "";
      el.append(img);
    } else {
      el.textContent = (username || "U").trim().charAt(0).toUpperCase();
    }
    return el;
  }

  function hardStop() {
    if (activeToast) {
      try { activeToast.close?.(true); } catch (_) { /* ignore */ }
      activeToast = undefined;
    }
    document.querySelectorAll(".join-toast").forEach((n) => n.remove());
  }

  function show(options = {}) {
    const {
      variant = "normal",
      username = "ضيف",
      message = "انضم إلى الغرفة",
      avatar = "",
      duration = 4200,
      container = document.body,
      onOpen,
      onClose,
      onStart,
      onComplete
    } = options;

    const item = byId.get(variant) || byId.get("normal");
    hardStop();

    const toast = document.createElement("article");
    toast.className = "join-toast is-animated";
    toast.dataset.variant = item.id;
    toast.style.setProperty("--join-accent", item.accent);
    toast.style.setProperty("--join-glow", item.glow);

    const stage = document.createElement("div");
    stage.className = "join-toast__stage";
    const shine = document.createElement("div");
    shine.className = "join-toast__shine";
    const content = document.createElement("div");
    content.className = "join-toast__content";
    const copy = document.createElement("div");
    copy.className = "join-toast__copy";
    const name = document.createElement("strong");
    name.textContent = username;
    const text = document.createElement("span");
    text.textContent = message;
    copy.append(name, text);
    content.append(copy, avatarNode(avatar, username));
    stage.append(shine, content);
    toast.append(stage);

    let closed = false;
    let finished = false;
    let timer;
    const finish = (notify) => {
      if (finished) return;
      finished = true;
      toast.remove();
      if (activeToast?.element === toast) activeToast = undefined;
      if (notify) {
        onClose?.();
        onComplete?.(item);
      }
    };
    const close = (immediate = false) => {
      if (closed) return;
      closed = true;
      clearTimeout(timer);
      if (immediate) {
        finish(false);
        return;
      }
      toast.classList.add("is-leaving");
      const force = window.setTimeout(() => finish(true), 520);
      toast.addEventListener("animationend", (e) => {
        if (e.target !== toast) return;
        clearTimeout(force);
        finish(true);
      }, { once: true });
    };

    (container || document.body).append(toast);
    requestAnimationFrame(() => toast.classList.add("is-visible"));
    onOpen?.(toast);
    onStart?.(item, toast);
    timer = window.setTimeout(() => close(false), Math.max(1600, duration));
    activeToast = { element: toast, item, close };
    return activeToast;
  }

  window.JoinToasts = {
    items: catalog,
    variants: catalog.map((i) => i.id),
    show,
    stop: hardStop,
    clear: hardStop,
    preload() { /* no assets */ }
  };

  // Compatibility alias — entry effects removed; join toast is the only join banner.
  window.EntryEffects = {
    items: catalog,
    variants: catalog.map((i) => i.id),
    play(opts = {}) {
      return JoinToasts.show({
        ...opts,
        variant: opts.variant || "legend",
        message: opts.title || opts.message || "دخل إلى الغرفة"
      });
    },
    stop: hardStop,
    preload() { /* no assets */ }
  };
})();
