# Isolated dependency upgrade assessment

Status: planning and compatibility gate only. No runtime dependency changes have been applied in this commit.

## Scope and safety boundaries

- This work is isolated to `experiment/major-dependency-upgrades-20261120`.
- Do not merge or deploy this branch automatically.
- Do not change production environment variables, Ghaymah services, PostgreSQL data/schema, Firebase project or credentials, voice-provider settings, storage, or CP dashboard configuration.
- Keep the current API contract and CommonJS module format unless a separately reviewed change proves a migration is required.

## Verified backend baseline

From `backend/package.json` and `backend/Dockerfile` on the experiment branch:

- Nest framework packages are on major 10 (for example `@nestjs/core`, `@nestjs/common`, Express and Socket.IO platform adapters).
- `@nestjs/cli` and `@nestjs/schematics` are major 10.
- `@nestjs/swagger` is major 8; `@nestjs/typeorm` is major 10.
- Firebase Admin is major 12.
- TypeORM is `^0.3.20`.
- TypeScript is `^5.7.2`.
- The backend build and runtime Docker stages use Node 20; dashboard build uses Node 22.
- The backend compiles as CommonJS and uses TypeORM/PostgreSQL.

## Safe upgrade sequence

### Stage 1 — NestJS 10 to 11 only

Upgrade all installed core Nest packages together to compatible 11.x releases: common, core, platform-express, platform-socket.io, websockets, CLI, and schematics. Upgrade companion packages only after confirming their peer dependency ranges; in particular check Swagger and Nest TypeORM adapters. Keep RxJS 7 and the existing TypeORM 0.3 line.

NestJS' official compatibility guidance says major upgrades should be performed one major at a time, with a build and test pass between majors. Nest 11 requires Node 20+, which matches the current backend Docker major. Do not proceed to Nest 12 as part of this stage.

### Stage 1 compatibility risks to test

- Nest 11's Express 5 default in the Express platform adapter can change route path matching and wildcard behavior. Review every wildcard/optional route and run API route tests.
- Verify Socket.IO gateway initialization, Redis adapter wiring, reconnect behavior, room membership, and event authorization.
- Verify bootstrap ordering, global pipes/guards/interceptors, Swagger generation, throttling, and startup scripts.
- Verify TypeORM entity discovery, migrations/bootstrap, schema isolation, transactions, and PostgreSQL CP fresh-install/upgrade tests.
- Verify Firebase Admin initialization and Google sign-in token verification without changing Firebase configuration or credentials.
- Verify LiveKit/Zego integration boundaries without changing provider settings.
- Verify production Docker build and startup using the existing Node 20 runtime.

### Stage 2 — Firebase Admin

Do not combine with Stage 1. Evaluate Firebase Admin 13 in a separate commit after Nest 11 is green. Preserve all existing Firebase project settings, service-account handling, Google login API contracts, and token verification behavior. Require dedicated auth tests.

### Stage 3 — TypeORM

Do not jump to TypeORM 1.x. Keep TypeORM 0.3 for the Nest 11 stage. Any TypeORM major migration must be a separate experiment with explicit review of entity metadata, query behavior, migrations, transactions, PostgreSQL compatibility, and data-preservation tests.

### Stage 4 — NestJS 12

Not in scope until Nest 11 is fully green. NestJS' current compatibility table requires Node 20.19+ (or Node 22.12+ on the 22 line) to run Nest 12; its CLI/schematics have a higher Node requirement. The current Dockerfile says Node 20 without pinning a patch version, so a Nest 12 migration must first pin/verify an adequate runtime. Nest 12 also changes core packages to ESM-only, requiring explicit validation of this CommonJS build and test tooling.

## Required CI gates before considering any merge

1. Clean `npm ci` from the committed lockfile.
2. Backend TypeScript validation and production build.
3. Dashboard build and existing checks.
4. Games and utility unit tests.
5. CP PostgreSQL 17 fresh-install, legacy-upgrade, lock-timeout/retry, and schema-guard tests against a disposable CI database only.
6. API/auth tests covering Google token verification and existing login response shape.
7. HTTP route regression tests, especially wildcard routes and static/admin/API route precedence.
8. Socket.IO gateway and room lifecycle tests.
9. Docker multi-stage build and production startup smoke test.
10. Dependency audit report with before/after advisory counts.

## Current decision

No major package bump is made until the compatible 11.x package set and lockfile can be generated together by npm and all CI gates can run. This avoids hand-editing a lockfile or introducing an unverified dependency graph. No production deployment or merge is authorized by this assessment.
