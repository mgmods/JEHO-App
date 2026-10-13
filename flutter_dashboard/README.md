# JEHO-OWN Flutter Web Dashboard — Migration Workspace

**Status: preparation only; the existing Vue dashboard remains the active dashboard.**

The source of truth is currently `backend/dashboard/` (Vue 3 + Vite + Pinia + Vue Router + Bootstrap). Do not remove it, change its authentication contract, or redirect production traffic while Flutter Web is being built.

## Migration order

1. Inventory the Vue router, sidebar/menu configuration, auth store, route permissions, API client, upload helpers, theme/locale handling, and all view-level actions.
2. Build a Flutter Web shell with Arabic RTL, responsive navigation, shared theme, session handling, loading/error states, and permission-aware routes.
3. Port and test one page at a time against the existing NestJS endpoints. Keep API payloads and permission behavior compatible.
4. Port uploads, image/media previews, PDF/export/report behavior, filters, pagination, confirmations, and audit logs where used by the original pages.
5. Run route-level regression tests and compare each Flutter page with its Vue equivalent using a test admin account.
6. Keep Vue as the rollback path until every route is matched and validated. A shell or successful web build is not feature parity.

## Required page inventory

See [the migration audit](../docs/FLUTTER_MIGRATION_AUDIT_AR.md) for the initial route inventory and acceptance gates. At minimum, migration coverage includes login/dashboard; users and conversations; rooms, gifts and seat stickers; banners and navigation icons; wallet, withdrawals, packages and promos; host targets, agencies and recharge agents; VIP, vanity IDs and cosmetics; drama, tasks, contests, rankings, games, ads and lucky boxes; reports, verifications, notifications, CP settings, general settings, logs and policy PDF.

## Non-negotiable safeguards

- Reuse the current NestJS API and existing database; no reset, reseed, destructive migration, or data copying.
- Do not alter ZEGO, audio-room connections, audio credentials/tokens, or Redis voice/realtime dependencies.
- Do not rotate encryption keys or change secrets as part of UI migration.
- Keep `backend/dashboard/` and `jeho-own` intact; do not merge or deploy automatically.
- Validate authorization on the server; client-side route hiding is not an authorization boundary.
- Do not declare the dashboard migrated until the page matrix and live integration checks pass.
