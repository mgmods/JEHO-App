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
