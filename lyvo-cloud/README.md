# LYVO Cloud

**Standalone** developer console (like ZEGO console UX) — **not** part of JEHO Nest backend or `/admin`.

| Site | Domain | Port / role |
|------|--------|-------------|
| **LYVO Cloud** (this folder) | `cloud.adastra.bbs.tr` | Node `:3910` |
| LiveKit media | `voice.adastra.bbs.tr` | LiveKit `:7880` |
| JEHO API + admin | `api.adnova.bbs.tr` | Nest `:3000` |

## Brand

- **Company:** LYVO Technologies  
- **Product:** LYVO Cloud  
- Not ZEGO / not JEHO admin  

## Local

```bash
cd lyvo-cloud
npm run install:all
cp .env.example .env
npm run build
npm start
# open http://127.0.0.1:3910
```

Dev UI (proxy API):

```bash
npm run dev:server   # terminal 1
npm run dev:web      # terminal 2 → :5188
```

## Deploy (own site)

```bash
python lyvo-cloud/scripts/deploy_lyvo_cloud.py
```

Then in DNS/Cloudflare: **A** record `cloud` → same VPS as JEHO, proxied or DNS-only.  
Issue SSL certificate for `cloud.adastra.bbs.tr` in the panel (do not share vhost with LiveKit `voice.*`).

## Features

- Register / login (friends can join)
- Create projects → AppID + API Key + Secret
- Free minutes bar + USD balance
- Card top-up via **Stripe keys owned by LYVO** (`STRIPE_*` in `lyvo-cloud/.env` only)
- Token API: `POST /api/v1/token` for third-party apps

## Data

JSON file under `lyvo-cloud/data/` on the server. No JEHO Postgres.
