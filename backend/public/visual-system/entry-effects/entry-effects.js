(function () {
  "use strict";

  // Legacy Host-signals pack (lion→legend) removed — missing assets.
  // Live entry rides come from Mikoo catalog (entry_mikoo_* / entry-mikoo-*.png).
  const catalog = [];

  const byId = new Map(catalog.map((item) => [item.id, item]));
  let activeEffect;

  function avatarElement(avatar, username) {
    const element = document.createElement("div");
    element.className = "entry-effect__avatar";
    if (avatar) {
      const image = document.createElement("img");
      image.src = avatar;
      image.alt = "";
      element.append(image);
    } else {
      element.textContent = (username || "U").trim().charAt(0).toUpperCase();
    }
    return element;
  }

  function play(options = {}) {
    const {
      variant = "",
      username = "ضيف",
      title = "دخل إلى البث",
      avatar = "",
      duration = 5200,
      assetBase = "assets",
      container = document.body,
      onStart,
      onComplete
    } = options;

    const item = byId.get(variant);
    if (!item) {
      if (typeof onComplete === "function") onComplete();
      return { stop() {} };
    }

    stop();

    const root = document.createElement("div");
    root.className = "entry-effect";
    root.style.setProperty("--entry-accent", item.accent);

    const banner = document.createElement("div");
    banner.className = "entry-effect__banner";

    const art = document.createElement("img");
    art.className = "entry-effect__art";
    art.src = `${assetBase}/${item.file}`;
    art.alt = item.name;

    const copy = document.createElement("div");
    copy.className = "entry-effect__copy";
    const nameEl = document.createElement("div");
    nameEl.className = "entry-effect__name";
    nameEl.textContent = username;
    const titleEl = document.createElement("div");
    titleEl.className = "entry-effect__title";
    titleEl.textContent = title || item.name;
    copy.append(nameEl, titleEl);

    banner.append(avatarElement(avatar, username), art, copy);
    root.append(banner);
    container.append(root);

    activeEffect = { root, timer: null };
    if (typeof onStart === "function") onStart();
    activeEffect.timer = setTimeout(() => {
      stop();
      if (typeof onComplete === "function") onComplete();
    }, duration);

    return { stop };
  }

  function stop() {
    if (!activeEffect) return;
    clearTimeout(activeEffect.timer);
    activeEffect.root.remove();
    activeEffect = null;
  }

  window.HostEntryEffects = { play, stop, catalog };
})();
