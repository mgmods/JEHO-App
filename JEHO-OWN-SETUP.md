# JEHO-OWN: independent test copy

This branch is the isolated development track for a JEHO-branded test deployment. The original `main` branch is not part of this work.

## Current separation status

- Android API resolution no longer falls back to the legacy production API host.
- The admin dashboard uses the same-origin `/api/v1` by default and accepts `VITE_API_URL` only when a separate API origin is intentionally configured.
- The backend Docker image builds and serves the dashboard at `/admin/`, while the API remains at `/api/v1`; both use the same deployment host.
- Legacy production environment values, the old signing key material, and the checked-in production database export have been removed from this branch's current tree or replaced with non-secret placeholders.
- This does **not** erase those values from Git history or from the original `main` branch. Treat exposed credentials as compromised and rotate them with the service owner.
- Firebase configuration still points to the previous Firebase project and must be replaced with a new project's configuration before claiming full service isolation.
- A new backend, PostgreSQL database, Redis instance, voice credentials, and dashboard deployment have not been created or connected yet.

## Render deployment (Docker + independent PostgreSQL)

A Render Blueprint is prepared in the repository root at `render.yaml`. It targets only branch `jeho-own` and defines:
- One Docker web service for the NestJS API and Vue dashboard.
- One separate PostgreSQL database restricted to Render's private network.
- A 1 GB persistent disk for user uploads.
- Generated JWT secrets and a first-deploy database seed hook.
- No Redis service at first; the API's Socket.IO adapter falls back to single-process mode if Redis is unavailable. Add Redis only when multi-instance realtime is required.

**Estimated paid beta cost (USD/month, before usage overages):**
- API Docker web service, 0.5 CPU / 512 MB: about $7.
- PostgreSQL, 0.1 CPU / 256 MB: about $6.
- PostgreSQL storage, configured 1 GB: about $0.30.
- Persistent upload disk, 1 GB: about $0.25.
- **Estimated base total: about $13.55/month.** This excludes outbound bandwidth beyond the workspace allowance, extra build minutes, voice-provider fees, taxes, and any optional domain.

The free tier is suitable only for a disposable preview: free web services can sleep after inactivity, and free PostgreSQL databases expire after 30 days. Do not use a free database for real users' long-lived profiles or wallet data.

No Render service has been created by adding this file. Before provisioning, open Render → New → Blueprint, select this repository and branch `jeho-own`, review the resource and cost summary, and only then confirm creation. The first Blueprint setup will ask for a unique admin email, username, and password; use a strong password of at least 16 characters. Never send these credentials in chat.

The database seed runs once after the service's first successful deploy. It initializes the schema and baseline catalog on the new database only. Runtime TypeORM synchronization remains disabled. The dashboard and API share the same HTTPS origin; the app API base will be `https://YOUR-RENDER-SERVICE.onrender.com/api/v1` and the dashboard `https://YOUR-RENDER-SERVICE.onrender.com/admin/`.

Google sign-in and voice are not considered ready merely because the API deploys: configure a new JEHO-OWN Google OAuth client and fresh ZEGO or LiveKit-provider credentials separately. Do not reuse credentials from the old service. Android Firebase configuration must also be replaced before the independent app can be called fully isolated.

## Lowest-cost free beta: Supabase + Render Free

The active `render.yaml` describes a **free Render Docker web service only**; it does not create a Render database or persistent disk. It does not create a Supabase project automatically. The API can use a separate Supabase project's PostgreSQL and private Storage bucket.

### Supabase project isolation
- Create a separate project named `JEHO-OWN` in the selected Supabase organization. Do not reuse the existing `JEHOO` project or any other existing project.
- Use Supabase's **Session pooler** connection string (IPv4 compatible) from Project → Connect → Session pooler. Keep the connection string secret; set `DB_SSL=true` and `DATABASE_URL` in Render. The connection URL contains the database password, so do not commit it or paste it into chat.
- Create a **private** Storage bucket named `jeho-own-uploads`. Set `SUPABASE_URL` and the project's server-side `service_role` key in Render as `SUPABASE_STORAGE_KEY`. Never put this key in the Android app, dashboard JavaScript, or a public client variable.
- Uploaded objects are kept in the private bucket; the API proxies reads via `/uploads/:filename` so the free Render filesystem is not relied on for persistence.
- Keep `DATABASE_URL`, `SUPABASE_STORAGE_KEY`, `ADMIN_EMAIL`, `ADMIN_USERNAME`, and `ADMIN_PASSWORD` as secrets. Do not put real values in GitHub commits.

### Free-tier limits and beta suitability
- Supabase project creation is currently estimated at **$0/month** for the account's Free organization. Free projects have limited database/storage/egress quotas and may pause after inactivity; verify the current quota and any warnings in the dashboard before creating the project.
- Render Free web services sleep after 15 minutes without inbound traffic, can take about a minute to wake, and have an ephemeral filesystem. This setup uses Supabase Storage to avoid losing uploaded files when the service sleeps or redeploys. It is a test beta, not suitable for guaranteed always-on voice rooms or production SLAs.
- The current backend includes Redis-backed application features. The Socket.IO adapter can fall back to single-process mode when Redis is unavailable, but Redis-backed features may still log errors or fail. We must test those paths and either provide a safe beta fallback or decide which features can be disabled before inviting users.
- The APK must be built with `JEHO_OWN_API_URL=https://YOUR-RENDER-SERVICE.onrender.com/api/v1` only after Render returns the real service URL.

### Provisioning sequence (no paid resources)
1. Confirm the Supabase organization for the new project.
2. Create the new Supabase project only after confirming its displayed cost; do not modify the existing `JEHOO` project.
3. Create the private Storage bucket and place its server-side key in Render secrets.
4. Create the Render service from this repository/branch using the free plan and the new Supabase connection string.
5. Check health, seed/schema initialization, upload/download, login, rooms, chat and realtime. Do not call the voice system production-ready until two real devices pass the audio test.

## Dashboard and API connection

The preferred JEHO-OWN beta deployment serves the Vue dashboard and NestJS API from the same backend origin:

- Dashboard: `https://YOUR-JEHO-OWN-HOST/admin/`
- API: `https://YOUR-JEHO-OWN-HOST/api/v1/`
- Health check: `https://YOUR-JEHO-OWN-HOST/healthz`

The Docker build compiles `backend/dashboard` with `VITE_BASE=/admin/` and copies the static build into `backend/public/admin`. The API client defaults to `/api/v1`, so dashboard requests stay on the same origin and do not depend on the old production API. The backend serves the dashboard's SPA routes under `/admin/*`.

For local development, run the dashboard with Vite and its `/api` proxy; `VITE_API_URL=/api/v1` is the same-origin default. Do not set it to the legacy production host.

## Android debug build

Both Android build variants read the API URL from `JEHO_OWN_API_URL` (Gradle project property/environment) or, for local development, the ignored `android/local.properties` file:

```properties
API_BASE_URL=http://10.0.2.2:3000/api/v1
```

Use your computer's LAN IP instead of `10.0.2.2` when testing on a physical phone. For CI APK builds, add the GitHub repository **variable** `JEHO_OWN_API_URL` with the deployed HTTPS API URL ending in `/api/v1` (for example, `https://your-host.example/api/v1`). The workflow intentionally skips APK generation until this variable is set, so it cannot silently produce a test APK pointed at a placeholder or old backend.

## Backend environment

Copy `backend/.env.example` to an untracked `backend/.env` and replace every placeholder before running the backend. The seed script reads only `backend/.env` and refuses to run unless you explicitly set the target database fields plus a unique admin email/username and an admin password of at least 16 characters. Generate new random JWT secrets, and configure new PostgreSQL/Redis credentials and new voice-provider credentials in the hosting provider's secret store. Never use the sample values as production credentials. Do not run migrations or seed scripts against the previous database.

## Deployment checklist

- [ ] Choose a new backend host and create a separate PostgreSQL database and Redis instance.
- [ ] Configure the backend using new credentials; run schema migrations only against the new database.
- [ ] Configure new voice-provider credentials and validate room join/token issuance.
- [ ] Create a separate Firebase project and replace `android/app/google-services.json`.
- [ ] Configure dashboard hosting with `VITE_API_URL` pointing at the new API.
- [ ] Configure Android release builds to use the new API URL and a newly generated signing key.
- [ ] Build and test the APK, login, dashboard permissions, voice rooms, chat, uploads, and notifications.
- [ ] Confirm no requests reach the legacy API before sharing the test build.
