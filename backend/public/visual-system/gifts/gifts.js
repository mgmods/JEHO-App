(function () {
  "use strict";

  const baseCatalog = [
    ["ball", "كرة أسطورية", "cute", "bounce", "basic"],
    ["car", "سيارة خارقة", "engine", "drive", "legendary"],
    ["castle", "قلعة الجليد", "royal", "rise", "mythic"],
    ["champagne", "احتفال فاخر", "celebration", "burst", "premium"],
    ["crown", "التاج الملكي", "royal", "crown", "legendary"],
    ["diamond", "ألماسة", "sparkle", "crystal", "premium"],
    ["dragon", "التنين", "fire", "dragon", "mythic"],
    ["fireworks", "ألعاب نارية", "celebration", "burst", "premium"],
    ["galaxy", "المجرة", "cosmic", "orbit", "mythic"],
    ["guitar", "غيتار النجوم", "celebration", "swing", "premium"],
    ["heart", "قلب كريستالي", "romance", "heart", "premium"],
    ["icecream", "آيس كريم", "cute", "bounce", "basic"],
    ["lion", "الأسد الملكي", "roar", "run", "legendary"],
    ["lucky-box", "صندوق الحظ", "sparkle", "box", "premium"],
    ["meteor", "النيزك", "impact", "meteor", "legendary"],
    ["plane", "طائرة خاصة", "engine", "fly", "legendary"],
    ["ring", "خاتم الحب", "romance", "crystal", "premium"],
    ["rocket", "الصاروخ", "cosmic", "rocket", "legendary"],
    ["rose", "وردة الحب", "romance", "heart", "basic"],
    ["teddy", "الدب اللطيف", "cute", "bounce", "basic"],
    ["unicorn", "اليونيكورن", "sparkle", "run", "legendary"],
    ["yacht", "اليخت الملكي", "luxury", "sail", "mythic"],
    ["microphone", "ميكروفون النجوم", "celebration", "swing", "premium"],
    ["phoenix", "العنقاء", "fire", "phoenix", "mythic"],
    ["royal-tiger", "النمر الملكي", "roar", "run", "mythic"],
    ["luxury-watch", "ساعة فاخرة", "luxury", "crystal", "legendary"],
    ["treasure-chest", "كنز الجواهر", "luxury", "box", "mythic"],
    ["golden-throne", "العرش الذهبي", "royal", "crown", "mythic"],
    ["dire-wolf", "الذئب الجليدي", "howl", "run", "mythic"],
    ["royal-train", "القطار الملكي", "train", "train", "legendary"],
    ["golden-tractor", "التراكتور الذهبي", "tractor", "tractor", "legendary"],
    ["crystal-piano", "البيانو الكريستالي", "piano", "music", "mythic"],
    ["superbike", "الدراجة الخارقة", "motorcycle", "drive", "legendary"],
    ["royal-drums", "طبول الملوك", "drums", "music", "legendary"],
    ["planet-earth", "كوكب الأرض", "cosmic", "orbit", "mythic"],
    ["planet-saturn", "كوكب زحل", "cosmic", "orbit", "mythic"],
    ["royal-eagle", "النسر الملكي", "eagle", "bird", "mythic"],
    ["royal-elephant", "الفيل الملكي", "elephant", "stomp", "mythic"],
    ["golden-falcon", "الصقر الذهبي", "eagle", "bird", "legendary"],
    ["magic-lamp", "المصباح السحري", "magic", "orbit", "mythic"]
  ].map(([id, name, sound, animation, tier]) => ({
    id, name, sound, animation, tier, file: `gift-${id}.png`
  }));

  const spriteDefinitions = {
    lion: { file: "gift-lion.png", frames: 9, duration: .95 },
    "royal-tiger": { file: "gift-royal-tiger.png", frames: 9, duration: .9 },
    "dire-wolf": { file: "gift-dire-wolf.png", frames: 9, duration: .82 },
    "golden-tractor": { file: "gift-golden-tractor.png", frames: 9, duration: .78 },
    superbike: { file: "gift-superbike.png", frames: 9, duration: .66 },
    car: { file: "gift-car.png", frames: 9, duration: .7 },
    "royal-train": { file: "gift-royal-train.png", frames: 9, duration: .72 },
    dragon: { file: "gift-dragon.png", frames: 9, duration: .88 },
    phoenix: { file: "gift-phoenix.png", frames: 9, duration: .86 },
    "royal-eagle": { file: "gift-royal-eagle.png", frames: 9, duration: .8 },
    "golden-falcon": { file: "gift-golden-falcon.png", frames: 9, duration: .76 },
    "royal-elephant": { file: "gift-royal-elephant.png", frames: 9, duration: .92 },
    unicorn: { file: "gift-unicorn.png", frames: 9, duration: .84 },
    plane: { file: "gift-plane.png", frames: 9, duration: .74 },
    rocket: { file: "gift-rocket.png", frames: 9, duration: .7 }
  };
  Object.entries(spriteDefinitions).forEach(([id, sprite]) => {
    baseCatalog.find((gift) => gift.id === id).sprite = sprite;
  });

  const catalog = baseCatalog.map((gift, index) => ({
    ...gift,
    filter: "brightness(1)",
    order: index + 1
  }));

  const byId = new Map(catalog.map((gift) => [gift.id, gift]));
  let activeGift;

  function particleLayer(count = 24) {
    const layer = document.createElement("div");
    layer.className = "gift-effect__particles";
    for (let index = 0; index < count; index += 1) {
      const particle = document.createElement("i");
      particle.style.setProperty("--i", index);
      particle.style.setProperty("--x", `${8 + (index * 37) % 84}%`);
      particle.style.setProperty("--delay", `${-(index % 9) * .17}s`);
      layer.append(particle);
    }
    return layer;
  }

  function playSound(name, audioBase, volume) {
    if (!name || volume <= 0) return undefined;
    const audio = new Audio(`${audioBase.replace(/\/$/, "")}/${name}.wav`);
    audio.volume = Math.min(1, Math.max(0, volume));
    audio.preload = "auto";
    audio.play().catch(() => {
      // Browsers can reject audio until the user interacts with the page.
    });
    return audio;
  }

  function createMedia(gift, assetBase, spriteBase) {
    if (gift.sprite?.frames) {
      const source = `${spriteBase.replace(/\/$/, "")}/${gift.sprite.file}`;
      const sprite = document.createElement("div");
      sprite.className = "gift-effect__image gift-effect__sprite";
      sprite.style.setProperty("--sprite-image", `url("${source}")`);
      sprite.style.setProperty("--sprite-frames", gift.sprite.frames);
      sprite.style.setProperty("--sprite-steps", gift.sprite.frames - 1);
      sprite.style.setProperty("--sprite-duration", `${gift.sprite.duration || 1}s`);
      return sprite;
    }

    const source = `${assetBase.replace(/\/$/, "")}/${gift.file}`;
    if (/\.(webm|mp4)$/i.test(gift.file)) {
      const video = document.createElement("video");
      video.className = "gift-effect__image";
      video.src = source;
      video.autoplay = true;
      video.loop = true;
      video.muted = true;
      video.playsInline = true;
      return video;
    }

    const image = document.createElement("img");
    image.className = "gift-effect__image";
    image.src = source;
    image.alt = "";
    image.style.setProperty("--gift-skin-filter", gift.filter);
    return image;
  }

  function show(options = {}) {
    const {
      gift: giftId = "diamond",
      sender = "",
      recipient = "",
      quantity = 1,
      duration = 4200,
      volume = .72,
      sound = true,
      showLabel = true,
      assetBase = "assets",
      spriteBase = "sprites",
      audioBase = "audio",
      container = document.body,
      onStart,
      onComplete
    } = options;

    const gift = byId.get(giftId);
    if (!gift) throw new Error(`Unknown gift: ${giftId}`);
    activeGift?.close();

    const overlay = document.createElement("section");
    overlay.className = `gift-effect gift-effect--${gift.tier} gift-animation--${gift.animation}`;
    overlay.dataset.gift = gift.id;
    overlay.setAttribute("aria-label", gift.name);

    const aura = document.createElement("div");
    aura.className = "gift-effect__aura";
    const rays = document.createElement("div");
    rays.className = "gift-effect__rays";
    const visual = document.createElement("div");
    visual.className = "gift-effect__visual";
    visual.append(createMedia(gift, assetBase, spriteBase));
    const shadow = document.createElement("div");
    shadow.className = "gift-effect__shadow";

    overlay.append(aura, rays, shadow, visual, particleLayer(gift.tier === "mythic" ? 34 : 24));

    if (showLabel) {
      const label = document.createElement("div");
      label.className = "gift-effect__label";
      const title = document.createElement("strong");
      title.textContent = quantity > 1 ? `${gift.name} ×${quantity}` : gift.name;
      const detail = document.createElement("span");
      detail.textContent = sender && recipient
        ? `${sender} أرسلها إلى ${recipient}`
        : sender
          ? `هدية من ${sender}`
          : "";
      label.append(title, detail);
      overlay.append(label);
    }

    let closed = false;
    let timer;
    let audio;
    let resolveFinished;
    const finished = new Promise((resolve) => {
      resolveFinished = resolve;
    });

    const close = () => {
      if (closed) return;
      closed = true;
      clearTimeout(timer);
      overlay.classList.add("is-leaving");
      audio?.pause();
      overlay.addEventListener(
        "animationend",
        (event) => {
          if (event.target !== overlay) return;
          overlay.remove();
          if (activeGift?.element === overlay) activeGift = undefined;
          onComplete?.(gift);
          resolveFinished(gift);
        },
        { once: true }
      );
    };

    container.append(overlay);
    requestAnimationFrame(() => overlay.classList.add("is-playing"));
    if (sound) audio = playSound(gift.sound, audioBase, volume);
    timer = window.setTimeout(close, Math.max(1800, duration));
    activeGift = { element: overlay, gift, close, finished };
    onStart?.(gift, overlay);
    return activeGift;
  }

  function preload(options = {}) {
    const assetBase = (options.assetBase || "assets").replace(/\/$/, "");
    const spriteBase = (options.spriteBase || "sprites").replace(/\/$/, "");
    const audioBase = (options.audioBase || "audio").replace(/\/$/, "");
    catalog.forEach((gift) => {
      const image = new Image();
      image.src = gift.sprite
        ? `${spriteBase}/${gift.sprite.file}`
        : `${assetBase}/${gift.file}`;
      const audio = new Audio();
      audio.preload = "auto";
      audio.src = `${audioBase}/${gift.sound}.wav`;
    });
  }

  window.Gifts = {
    items: catalog,
    show,
    preload,
    stop() {
      activeGift?.close();
    }
  };
})();
