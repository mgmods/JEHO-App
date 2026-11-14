const ranks = [
  { title: "الطليعة", en: "VANGUARD", primary: "#91a1b8", secondary: "#f3f7ff", accent: "#536176", fire: "#93dcff" },
  { title: "الحارس", en: "SENTINEL", primary: "#735cff", secondary: "#d8d0ff", accent: "#3b279f", fire: "#9d8cff" },
  { title: "الصقر", en: "FALCON", primary: "#e64d36", secondary: "#ffd1a5", accent: "#8d241e", fire: "#ff7b35" },
  { title: "الزمرد", en: "EMERALD", primary: "#14b88a", secondary: "#baffdf", accent: "#08755f", fire: "#4dffc6" },
  { title: "العاصفة", en: "TEMPEST", primary: "#248dff", secondary: "#c9efff", accent: "#0754b5", fire: "#75dfff" },
  { title: "الشبح", en: "PHANTOM", primary: "#c849ff", secondary: "#f1c7ff", accent: "#732299", fire: "#df76ff" },
  { title: "الشمس", en: "SOLARIS", primary: "#ffb21d", secondary: "#fff0a3", accent: "#bd5f05", fire: "#ff6a18" },
  { title: "التنين", en: "DRAGON", primary: "#ff304f", secondary: "#ffc0b5", accent: "#8f0a25", fire: "#ff4b1f" },
  { title: "السماوي", en: "CELESTIAL", primary: "#33d6e8", secondary: "#e2ffff", accent: "#087d99", fire: "#a5ffff" },
  { title: "الخالد", en: "IMMORTAL", primary: "#f6ce62", secondary: "#fffbe0", accent: "#a76808", fire: "#ff8a20" },
  { title: "الكوبرا", en: "COBRA", primary: "#64e572", secondary: "#e7ffd9", accent: "#167c35", fire: "#a6ff54" },
  { title: "الأسد", en: "LIONHEART", primary: "#ff9f1c", secondary: "#fff0b0", accent: "#9d4208", fire: "#ffcf42" },
  { title: "النسر", en: "EAGLE", primary: "#69b7ff", secondary: "#f0fbff", accent: "#17599c", fire: "#a6e3ff" },
  { title: "الذئب", en: "DIRE WOLF", primary: "#8f9baa", secondary: "#f5fbff", accent: "#344155", fire: "#76d7ff" },
  { title: "العنقاء", en: "PHOENIX", primary: "#ff4b24", secondary: "#fff1a8", accent: "#a30d0d", fire: "#ffb000" },
  { title: "العقرب", en: "SCORPION", primary: "#d4ff2c", secondary: "#f4ffc1", accent: "#577600", fire: "#c8ff00" },
  { title: "الكراكن", en: "KRAKEN", primary: "#9f63ff", secondary: "#ead8ff", accent: "#452080", fire: "#ca8cff" },
  { title: "الكبش", en: "WAR RAM", primary: "#c88952", secondary: "#ffe0b5", accent: "#653519", fire: "#ff9e4a" },
  { title: "الغراب", en: "NIGHT RAVEN", primary: "#5474d8", secondary: "#cbd6ff", accent: "#161c5b", fire: "#748cff" },
  { title: "الإمبراطور", en: "EMPEROR", primary: "#ffd65a", secondary: "#fffde1", accent: "#a85d00", fire: "#ff8b18" },
  { title: "التنين الإمبراطوري", en: "IMPERIAL DRAGON", primary: "#ff3d24", secondary: "#ffd49a", accent: "#700b0b", fire: "#ff5a16" },
  { title: "النمر الأبيض", en: "WHITE TIGER", primary: "#5caeff", secondary: "#f5fbff", accent: "#164f9b", fire: "#85d8ff" },
  { title: "ملك الجليد", en: "POLAR KING", primary: "#7dd8ff", secondary: "#ffffff", accent: "#2174ad", fire: "#b9f2ff" },
  { title: "الميغالودون", en: "MEGALODON", primary: "#16c8ee", secondary: "#c7f8ff", accent: "#075c86", fire: "#31e6ff" },
  { title: "المينوتور", en: "MINOTAUR", primary: "#df641f", secondary: "#ffd0a1", accent: "#5b1c0c", fire: "#ff7a1a" },
  { title: "الأرملة السوداء", en: "BLACK WIDOW", primary: "#f2254f", secondary: "#ffc5d0", accent: "#590719", fire: "#ff335f" },
  { title: "سيد الرعد", en: "THUNDER LORD", primary: "#3284ff", secondary: "#eef8ff", accent: "#143eaa", fire: "#64b9ff" },
  { title: "الشوغن", en: "SHOGUN", primary: "#d92325", secondary: "#ffd58a", accent: "#5d0b0d", fire: "#ff402a" },
  { title: "ملك الظلام", en: "DEMON KING", primary: "#ff253c", secondary: "#ffb2aa", accent: "#54000a", fire: "#ff361c" },
  { title: "الحاكم الكوني", en: "COSMIC SOVEREIGN", primary: "#9d6cff", secondary: "#fff8df", accent: "#4e208d", fire: "#cba2ff" }
];

const imagePresets = [
  { src: "assets/frame-01-bronze-falcon-transparent.png", filter: "none", layout: "rendered" },
  { src: "assets/frame-02-silver-stag-transparent.png", filter: "none", layout: "rendered" },
  { src: "assets/frame-03-crimson-griffin-transparent.png", filter: "none", layout: "rendered" },
  { src: "assets/frame-04-emerald-jaguar-transparent.png", filter: "none", layout: "rendered" },
  { src: "assets/frame-05-sapphire-leviathan-transparent.png", filter: "none", layout: "rendered" },
  { src: "assets/frame-06-amethyst-owl-transparent.png", filter: "none", layout: "rendered" },
  { src: "assets/frame-07-solar-scarab-transparent.png", filter: "none", layout: "rendered" },
  { src: "assets/frame-08-fire-stallion-transparent.png", filter: "none", layout: "rendered" },
  { src: "assets/frame-09-celestial-swan-transparent.png", filter: "none", layout: "rendered" },
  { src: "assets/frame-10-royal-seraph-transparent.png", filter: "none", layout: "rendered" },
  { src: "assets/frame-11-cobra-transparent.png", filter: "none", layout: "rendered" },
  { src: "assets/frame-12-lion-transparent.png", filter: "none", layout: "rendered" },
  { src: "assets/frame-13-eagle-transparent.png", filter: "none", layout: "rendered" },
  { src: "assets/frame-14-wolf-transparent.png", filter: "none", layout: "rendered" },
  { src: "assets/frame-15-phoenix-transparent.png", filter: "none", layout: "rendered" },
  { src: "assets/frame-16-scorpion-transparent.png", filter: "none", layout: "rendered" },
  { src: "assets/frame-17-kraken-transparent.png", filter: "none", layout: "rendered" },
  { src: "assets/frame-18-ram-transparent.png", filter: "none", layout: "rendered" },
  { src: "assets/frame-19-raven-transparent.png", filter: "none", layout: "rendered" },
  { src: "assets/frame-20-emperor-transparent.png", filter: "none", layout: "rendered" },
  { src: "assets/frame-21-dragon-transparent.png", filter: "none", layout: "rendered" },
  { src: "assets/frame-22-tiger-transparent.png", filter: "none", layout: "rendered" },
  { src: "assets/frame-23-polar-bear-transparent.png", filter: "none", layout: "rendered" },
  { src: "assets/frame-24-shark-transparent.png", filter: "none", layout: "rendered" },
  { src: "assets/frame-25-minotaur-transparent.png", filter: "none", layout: "rendered" },
  { src: "assets/frame-26-spider-transparent.png", filter: "none", layout: "rendered" },
  { src: "assets/frame-27-thunder-transparent.png", filter: "none", layout: "rendered" },
  { src: "assets/frame-28-samurai-transparent.png", filter: "none", layout: "rendered" },
  { src: "assets/frame-29-demon-transparent.png", filter: "none", layout: "rendered" },
  { src: "assets/frame-30-cosmic-transparent.png", filter: "none", layout: "rendered" }
];

const frameThemes = [
  "guardian", "serpent", "falcon", "emerald", "storm", "specter", "solar", "dragon", "celestial", "royal",
  "cobra", "lion", "eagle", "wolf", "phoenix", "scorpion", "kraken", "ram", "raven", "emperor"
];

function wingMarkup(side, tier) {
  const right = side === "right";
  const transform = right ? "translate(500 0) scale(-1 1)" : "";
  const extra = tier >= 4
    ? `<path class="wing-feather" d="M205 174 C135 96 78 63 28 67 C75 104 93 144 119 190 C145 214 174 219 207 213 Z"/>
       <path class="wing-line" d="M190 186 C130 127 87 101 45 87"/>`
    : "";
  const upper = tier >= 7
    ? `<path class="wing-feather" d="M212 160 C165 73 126 31 74 17 C101 64 105 108 131 170 Z"/>`
    : "";
  const lower = tier >= 9
    ? `<path class="wing-feather" d="M210 284 C137 324 91 365 65 414 C120 388 170 363 220 326 Z"/>`
    : "";

  return `
    <g transform="${transform}">
      <g class="wing ${right ? "right" : "left"}">
      ${upper}${extra}
      <path class="wing-feather" d="M218 190 C151 126 85 104 20 119 C74 145 102 178 127 223 C157 240 187 238 221 226 Z"/>
      <path class="wing-feather" d="M218 218 C140 183 72 186 18 218 C78 224 118 245 153 278 C178 282 202 272 224 250 Z"/>
      <path class="wing-feather" d="M224 248 C151 238 91 263 50 306 C105 289 146 298 187 318 C207 306 221 286 232 263 Z"/>
      ${lower}
      <path class="wing-line" d="M205 209 C137 170 86 153 42 142"/>
      <path class="wing-line" d="M210 236 C139 216 86 218 44 226"/>
      <path class="wing-line" d="M216 261 C157 263 112 276 79 296"/>
      </g>
    </g>`;
}

function crownMarkup(tier) {
  if (tier <= 2) {
    return `<path class="crown" d="M208 133 L225 91 L250 118 L275 91 L292 133 L279 153 H221 Z"/>`;
  }
  if (tier <= 5) {
    return `<path class="crown" d="M199 139 L209 82 L239 116 L250 63 L261 116 L291 82 L301 139 L281 158 H219 Z"/>
      <circle class="gem" cx="250" cy="104" r="8"/>`;
  }
  if (tier <= 8) {
    return `<path class="crown" d="M190 143 L200 76 L230 111 L250 46 L270 111 L300 76 L310 143 L283 164 H217 Z"/>
      <path class="star" d="M250 61 L257 79 L277 80 L261 92 L266 111 L250 100 L234 111 L239 92 L223 80 L243 79 Z"/>`;
  }
  return `<path class="crown" d="M180 147 L193 68 L228 108 L250 27 L272 108 L307 68 L320 147 L284 170 H216 Z"/>
    <path class="star" d="M250 43 L259 69 L286 70 L264 86 L272 112 L250 96 L228 112 L236 86 L214 70 L241 69 Z"/>
    <circle class="gem" cx="250" cy="82" r="10"/>`;
}

function frameMarkup(tier) {
  const eliteBlades = tier >= 4 ? `
    <path class="frame-blade" d="M154 163 L109 132 L126 190 L92 211 L151 229 L176 200 Z"/>
    <path class="frame-blade" d="M346 163 L391 132 L374 190 L408 211 L349 229 L324 200 Z"/>` : "";
  const royalSpikes = tier >= 7 ? `
    <path class="frame-blade" d="M174 139 L143 72 L203 121 Z"/>
    <path class="frame-blade" d="M326 139 L357 72 L297 121 Z"/>
    <path class="frame-blade" d="M153 311 L99 354 L171 348 Z"/>
    <path class="frame-blade" d="M347 311 L401 354 L329 348 Z"/>` : "";
  const immortalCrest = tier >= 9 ? `
    <path class="frame-spike" d="M139 247 L62 225 L113 269 L64 302 L143 289 Z"/>
    <path class="frame-spike" d="M361 247 L438 225 L387 269 L436 302 L357 289 Z"/>` : "";

  return `
    ${eliteBlades}${royalSpikes}${immortalCrest}
    <path class="frame-shadow" fill-rule="evenodd" d="
      M250 105 L285 117 L318 110 L339 138 L372 147 L378 181 L402 207
      L391 241 L404 274 L382 302 L377 336 L343 347 L319 374 L284 367
      L250 386 L216 367 L181 374 L157 347 L123 336 L118 302 L96 274
      L109 241 L98 207 L122 181 L128 147 L161 138 L182 110 L215 117 Z
      M250 143 A101 101 0 1 0 250 345 A101 101 0 1 0 250 143 Z"/>
    <path class="frame-body" fill-rule="evenodd" d="
      M250 111 L281 124 L313 118 L331 146 L362 156 L365 187 L389 210
      L379 241 L392 272 L371 297 L367 328 L336 338 L314 362 L282 354
      L250 373 L218 354 L186 362 L164 338 L133 328 L129 297 L108 272
      L121 241 L111 210 L135 187 L138 156 L169 146 L187 118 L219 124 Z
      M250 145 A99 99 0 1 0 250 343 A99 99 0 1 0 250 145 Z"/>
    <path class="frame-inlay" d="M159 197 C186 139 226 128 250 128 C274 128 314 139 341 197"/>
    <path class="frame-inlay lower" d="M151 285 C175 340 218 360 250 360 C282 360 325 340 349 285"/>
    <path class="frame-armor" d="M127 198 L154 181 L171 203 L153 239 L120 236 Z"/>
    <path class="frame-armor" d="M373 198 L346 181 L329 203 L347 239 L380 236 Z"/>
    <path class="frame-armor" d="M126 280 L156 273 L176 306 L153 329 L125 314 Z"/>
    <path class="frame-armor" d="M374 280 L344 273 L324 306 L347 329 L375 314 Z"/>
    <path class="frame-highlight" d="M145 184 C171 133 218 112 250 112 C292 112 329 132 355 174"/>
    <path class="frame-highlight lower" d="M126 275 C144 337 198 372 250 373 C302 372 356 337 374 275"/>
    <path class="frame-gem" d="M250 115 L264 129 L250 143 L236 129 Z"/>
    <path class="frame-gem" d="M119 251 L132 264 L119 277 L106 264 Z"/>
    <path class="frame-gem" d="M381 251 L394 264 L381 277 L368 264 Z"/>`;
}

function flameMarkup(tier) {
  const sideFlames = tier >= 5 ? `
    <g class="flame" transform="translate(76 274) rotate(-48)">
      <path class="flame-outer" d="M0 0 C-34 30 -28 71 0 104 C29 69 37 31 0 0Z"/>
      <path class="flame-mid" d="M0 29 C-17 50 -12 75 0 91 C15 70 18 50 0 29Z"/>
    </g>
    <g class="flame" transform="translate(424 274) rotate(48)">
      <path class="flame-outer" d="M0 0 C34 30 28 71 0 104 C-29 69 -37 31 0 0Z"/>
      <path class="flame-mid" d="M0 29 C17 50 12 75 0 91 C-15 70 -18 50 0 29Z"/>
    </g>` : "";
  const inferno = tier >= 8 ? `
    <g class="flame" transform="translate(250 60) rotate(180)">
      <path class="flame-outer" d="M0 0 C-28 37 -23 78 0 111 C25 77 31 38 0 0Z"/>
      <path class="flame-mid" d="M0 27 C-14 49 -11 73 0 91 C14 69 15 49 0 27Z"/>
    </g>` : "";
  return `${sideFlames}${inferno}
    <g class="flame" transform="translate(250 369)">
      <path class="flame-outer" d="M0 0 C-45 42 -35 90 0 126 C37 88 47 43 0 0Z"/>
      <path class="flame-mid" d="M0 23 C-25 52 -20 84 0 108 C23 80 27 52 0 23Z"/>
      <path class="flame-core" d="M0 52 C-11 72 -8 91 0 102 C11 88 12 70 0 52Z"/>
    </g>`;
}

function themeMarkup(theme, level, rank) {
  const artwork = {
    guardian: `<path d="M250 24 L291 57 L280 112 L250 139 L220 112 L209 57 Z"/><path class="theme-cut" d="M250 43 L270 64 L264 98 L250 113 L236 98 L230 64 Z"/>`,
    serpent: `<path class="theme-line heavy" d="M121 335 C64 270 89 142 176 99 C237 69 319 88 357 145 C390 193 375 251 337 282"/><path d="M333 267 L381 248 L366 296 L337 313 L312 286 Z"/><circle class="theme-eye" cx="356" cy="277" r="5"/>`,
    falcon: `<path d="M250 32 C219 47 203 72 208 106 L250 132 L292 106 C297 72 281 47 250 32 Z"/><path class="theme-cut" d="M240 55 L274 67 L247 81 L267 91 L238 105 L221 84 Z"/><circle class="theme-eye" cx="246" cy="72" r="4"/>`,
    emerald: `<path d="M250 24 L284 76 L250 132 L216 76 Z"/><path class="theme-cut" d="M250 42 L267 77 L250 107 L233 77 Z"/><path d="M191 64 L221 91 L203 125 L169 91 Z M309 64 L279 91 L297 125 L331 91 Z"/>`,
    storm: `<path d="M265 20 L210 88 L242 88 L222 145 L292 65 L258 65 Z"/><path class="theme-line" d="M176 102 L135 137 M324 102 L365 137"/>`,
    specter: `<path d="M203 42 L224 66 C239 53 261 53 276 66 L297 42 L290 102 L269 128 L231 128 L210 102 Z"/><path class="theme-cut" d="M224 82 L243 89 L232 103 Z M276 82 L257 89 L268 103 Z"/><path class="theme-line" d="M239 116 L250 107 L261 116"/>`,
    solar: `<path d="M250 23 L263 57 L299 44 L286 80 L322 94 L285 108 L299 145 L263 131 L250 166 L237 131 L201 145 L214 108 L178 94 L215 80 L201 44 L237 57 Z"/><circle class="theme-cut" cx="250" cy="94" r="30"/>`,
    dragon: `<path d="M188 105 L205 45 L236 69 L250 28 L264 69 L295 45 L312 105 L283 136 L217 136 Z"/><path class="theme-cut" d="M216 88 L240 94 L225 108 Z M284 88 L260 94 L275 108 Z"/><path class="theme-line" d="M230 122 L250 110 L270 122"/>`,
    celestial: `<path d="M250 23 L261 57 L297 57 L268 78 L279 113 L250 92 L221 113 L232 78 L203 57 L239 57 Z"/><path class="theme-line" d="M184 64 A66 66 0 0 0 316 64"/>`,
    royal: `<path d="M180 112 L191 47 L226 81 L250 24 L274 81 L309 47 L320 112 L295 139 L205 139 Z"/><circle class="theme-eye" cx="250" cy="91" r="9"/>`,
    cobra: `<path d="M250 33 C197 45 178 85 193 137 L223 159 L250 126 L277 159 L307 137 C322 85 303 45 250 33 Z"/><path class="theme-cut" d="M250 58 C231 63 228 86 238 106 L250 121 L262 106 C272 86 269 63 250 58 Z"/><circle class="theme-eye" cx="238" cy="82" r="4"/><circle class="theme-eye" cx="262" cy="82" r="4"/>`,
    lion: `<path d="M250 25 L278 44 L311 39 L319 72 L342 95 L322 124 L315 158 L280 160 L250 181 L220 160 L185 158 L178 124 L158 95 L181 72 L189 39 L222 44 Z"/><path class="theme-cut" d="M211 72 L232 63 L250 79 L268 63 L289 72 L282 126 L250 153 L218 126 Z"/><path class="theme-line" d="M225 96 L240 101 M275 96 L260 101 M235 127 L250 116 L265 127"/>`,
    eagle: `<path d="M197 52 C231 24 282 31 310 67 L278 72 L319 91 L280 112 L259 145 L215 131 L193 99 Z"/><path class="theme-cut" d="M219 61 C242 47 270 49 287 64 L253 73 L230 94 Z"/><path d="M276 72 L340 89 L294 108 Z"/><circle class="theme-eye" cx="259" cy="66" r="5"/>`,
    wolf: `<path d="M185 38 L224 61 L250 42 L276 61 L315 38 L302 110 L274 158 L250 178 L226 158 L198 110 Z"/><path class="theme-cut" d="M212 80 L239 91 L220 108 Z M288 80 L261 91 L280 108 Z"/><path class="theme-line" d="M230 134 L250 119 L270 134 L250 154 Z"/>`,
    phoenix: `<path d="M250 18 C225 53 226 80 250 101 C274 80 275 53 250 18 Z"/><path d="M242 91 C196 50 159 64 135 103 C177 90 202 112 230 143 Z M258 91 C304 50 341 64 365 103 C323 90 298 112 270 143 Z"/><path d="M250 91 L278 157 L250 139 L222 157 Z"/>`,
    scorpion: `<path class="theme-line heavy" d="M179 126 C123 81 138 35 185 37 C226 39 226 91 197 95 C171 98 160 73 178 60"/><path d="M174 111 L202 127 L191 160 L161 153 L151 126 Z"/><path d="M153 123 L112 104 L124 141 Z M197 126 L238 105 L225 143 Z"/><path d="M172 82 L188 94 L171 108 L155 96 Z"/>`,
    kraken: `<path d="M216 53 C230 28 270 28 284 53 L296 101 L276 129 L224 129 L204 101 Z"/><circle class="theme-eye" cx="250" cy="81" r="10"/><path class="theme-line heavy" d="M219 119 C184 137 187 170 158 181 M239 127 C224 159 238 181 217 201 M261 127 C276 159 262 181 283 201 M281 119 C316 137 313 170 342 181"/>`,
    ram: `<path class="theme-line heavy" d="M226 70 C192 25 139 42 145 91 C149 125 187 129 203 104 C216 84 201 64 181 69 M274 70 C308 25 361 42 355 91 C351 125 313 129 297 104 C284 84 299 64 319 69"/><path d="M216 67 L250 47 L284 67 L276 137 L250 164 L224 137 Z"/><path class="theme-cut" d="M231 88 L244 98 L233 109 Z M269 88 L256 98 L267 109 Z"/>`,
    raven: `<path d="M192 105 C211 44 269 29 312 62 L281 76 L331 91 L288 111 L268 153 L221 139 Z"/><path class="theme-cut" d="M217 86 C240 58 268 55 291 67 L255 78 L230 111 Z"/><path d="M279 76 L354 92 L297 109 Z"/><circle class="theme-eye" cx="263" cy="69" r="5"/>`,
    emperor: `<path d="M164 120 L178 42 L221 83 L250 16 L279 83 L322 42 L336 120 L302 153 L198 153 Z"/><path class="theme-cut" d="M250 41 L264 85 L250 113 L236 85 Z"/><path d="M175 137 L212 152 L195 185 L155 164 Z M325 137 L288 152 L305 185 L345 164 Z"/><circle class="theme-eye" cx="250" cy="91" r="8"/>`
  };
  const id = `animal-metal-${level}`;
  return `
    <svg class="theme-art theme-${theme}" viewBox="0 0 500 500" aria-hidden="true">
      <defs>
        <linearGradient id="${id}" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stop-color="${rank.secondary}"/>
          <stop offset=".38" stop-color="${rank.primary}"/>
          <stop offset=".64" stop-color="${rank.secondary}"/>
          <stop offset="1" stop-color="${rank.accent}"/>
        </linearGradient>
      </defs>
      <g style="--theme-metal:url(#${id})">${artwork[theme]}</g>
    </svg>`;
}

function buildSignal(target, level, options = {}) {
  const rank = ranks[level - 1];
  const preset = imagePresets[level - 1];
  if (!rank || !target) return;
  const assetBase = (options.assetBase || "assets").replace(/\/$/, "");
  const avatar = options.avatar || "";
  const src = preset.src.startsWith("assets/")
    ? `${assetBase}/${preset.src.slice("assets/".length)}`
    : preset.src;

  target.style.setProperty("--primary", rank.primary);
  target.style.setProperty("--secondary", rank.secondary);
  target.style.setProperty("--accent", rank.accent);
  target.style.setProperty("--fire", rank.fire);
  target.style.setProperty("--art-filter", preset.filter);
  target.style.setProperty("--wing-filter", preset.wingFilter || preset.filter);
  target.style.setProperty("--host-frame-image", `url("${src}")`);

  target.innerHTML = `
    <div class="signal is-animated">
      <div class="image-emblem level-${level} ${preset.layout || ""}" role="img" aria-label="إشارة المضيف، المستوى ${level}">
        <div class="energy-halo"></div>
        <div class="fire-plume fire-left"><i></i><i></i><i></i></div>
        <div class="fire-plume fire-right"><i></i><i></i><i></i></div>
        ${avatar ? `<img class="profile-photo" src="${avatar}" alt="">` : `<div class="profile-photo profile-photo--empty"></div>`}
        <img class="frame-art" src="${src}" alt="">
        <img class="wing-art wing-art-left" src="${src}" alt="">
        <img class="wing-art wing-art-right" src="${src}" alt="">
        <div class="frame-glow"></div>
        <div class="shine-sweep"></div>
        <div class="embers" aria-hidden="true">
          <i></i><i></i><i></i><i></i><i></i><i></i><i></i><i></i>
        </div>
      </div>
    </div>`;
}

const hostItems = ranks.map((rank, index) => ({
  level: index + 1,
  id: `level-${index + 1}`,
  name: rank.title,
  en: rank.en,
  file: imagePresets[index].src.replace(/^assets\//, ""),
  primary: rank.primary,
  secondary: rank.secondary,
  accent: rank.accent,
  fire: rank.fire
}));

window.HostFrames = {
  count: hostItems.length,
  items: hostItems,
  render(element, level, options = {}) {
    const resolved = Number(level);
    if (!ranks[resolved - 1]) throw new Error(`Unknown host frame level: ${level}`);
    buildSignal(element, resolved, options);
    return { element, level: resolved, frame: hostItems[resolved - 1] };
  },
  preload(options = {}) {
    const assetBase = (options.assetBase || "assets").replace(/\/$/, "");
    hostItems.forEach((item) => {
      const image = new Image();
      image.src = `${assetBase}/${item.file}`;
    });
  },
  get(level) {
    return hostItems[Number(level) - 1];
  }
};

document.querySelectorAll("[data-level]").forEach((target) => {
  const level = Number(target.dataset.level);
  if (ranks[level - 1]) {
    target.style.setProperty("--primary", ranks[level - 1].primary);
    target.closest(".gallery-item")?.style.setProperty("--primary", ranks[level - 1].primary);
    buildSignal(target, level);
  }
});

const pageSignal = document.querySelector("#signal");
if (pageSignal && !window.HostFramesSkipAuto) {
  const level = Number(document.body.dataset.level || 1);
  const rank = ranks[level - 1];
  if (rank) {
    document.body.style.setProperty("--aura", `${rank.primary}38`);
    document.title = `Host Frame ${level}`;
    buildSignal(pageSignal, level);
  }
}
