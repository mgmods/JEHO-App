# JEHO-OWN: independent test copy

This branch is the isolated development track for a JEHO-branded test deployment. The original `main` branch is not part of this work.

## Current separation status

- Android API resolution no longer falls back to the legacy production API host.
- The admin dashboard defaults to the local API and accepts `VITE_API_URL`.
- Legacy production environment values, the old signing key material, and the checked-in production database export have been removed from this branch's current tree or replaced with non-secret placeholders.
- This does **not** erase those values from Git history or from the original `main` branch. Treat exposed credentials as compromised and rotate them with the service owner.
- Firebase configuration still points to the previous Firebase project and must be replaced with a new project's configuration before claiming full service isolation.
- A new backend, PostgreSQL database, Redis instance, voice credentials, and dashboard deployment have not been created or connected yet.

## Local dashboard

Create an untracked `backend/dashboard/.env.local` file containing the API URL for the new backend:

```dotenv
VITE_API_URL=http://localhost:3000/api/v1
VITE_BASE=/admin/
```

For a hosted deployment, set `VITE_API_URL` in the dashboard hosting provider to the new backend URL. Do not point it at the legacy production host.

## Android debug build

The Android debug variant supports an API override through the ignored `android/local.properties` file:

```properties
API_BASE_URL=http://10.0.2.2:3000/api/v1
```

Use your computer's LAN IP instead of `10.0.2.2` when testing on a physical phone. The backend must be reachable from the device. The release build configuration still needs to be wired to the new deployment URL before producing a release APK.

## Backend environment

Start from `backend/.env.example`, generate new random JWT secrets, and configure new PostgreSQL/Redis credentials and new voice-provider credentials in the hosting provider's secret store. Never copy values from old production files. Do not run migrations or seed scripts against the previous database.

## Deployment checklist

- [ ] Choose a new backend host and create a separate PostgreSQL database and Redis instance.
- [ ] Configure the backend using new credentials; run schema migrations only against the new database.
- [ ] Configure new voice-provider credentials and validate room join/token issuance.
- [ ] Create a separate Firebase project and replace `android/app/google-services.json`.
- [ ] Configure dashboard hosting with `VITE_API_URL` pointing at the new API.
- [ ] Configure Android release builds to use the new API URL and a newly generated signing key.
- [ ] Build and test the APK, login, dashboard permissions, voice rooms, chat, uploads, and notifications.
- [ ] Confirm no requests reach the legacy API before sharing the test build.
