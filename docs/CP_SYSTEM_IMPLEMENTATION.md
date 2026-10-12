# JEHO-OWN CP System — implementation notes

## Confirmed product rules

- Independent types: friendship, brotherhood, love, engagement, marriage. No automatic love → engagement → marriage progression.
- Initiation is offered from the in-room participant card only. The server validates the authenticated sender, active voice room, and recipient account ID seated/present in that room before debiting.
- The receiver must explicitly accept before an active relationship is created. Accept/reject can happen outside the room.
- Reject, expiry, and sender cancellation refund the full original debit; the refund ledger reference is idempotent.
- Either partner may end an active relationship; ended rows remain as history.
- Multiple friendship/brotherhood partners are allowed. No additional cardinality restrictions for other types have been assumed.
- No prices, gift IDs, level thresholds, or rewards are seeded. A type cannot be used until an administrator enables it and configures a real gift and positive price.
- Existing audio configuration, credentials, providers, tokens, voice connections, and Redis voice settings remain out of scope.

## Changes currently on this feature branch

- CP-specific TypeORM entities: relationships, requests, audit logs, and points ledger.
- Explicit non-destructive schema migration: `npm run migrate:cp` from `backend/`. It creates only CP tables/indexes and adds CP gift snapshot columns. It has **not** been run against any database.
- CP backend endpoints:
  - `GET /api/v1/cp/catalog` — enabled types and configured gift prices/media.
  - `GET /api/v1/cp/discover?type=love` — active relationships ranked by server points, with duration, level progress, and unlocked rewards.
  - `GET /api/v1/cp/me`, `GET /api/v1/cp/requests`, `GET /api/v1/cp/sent-requests`.
  - `POST /api/v1/cp/requests`, `POST /api/v1/cp/requests/:id/respond`, `POST /api/v1/cp/requests/:id/cancel`, `POST /api/v1/cp/:id/end`.
  - `GET /api/v1/cp/users/:id` — active CP relationships for a profile, without wallet data.
- Protected dashboard API and Vue settings page:
  - Dashboard route `/admin/cp` configures all five types, actual gift selection, initiation and exchange points, prices, levels/rewards, expiry policy, request/refund logs, audit history, and audited manual point corrections.
  - `GET /api/v1/admin/cp/config`
  - `PATCH /api/v1/admin/cp/config/:type`
  - `PATCH /api/v1/admin/cp/policy`
  - `GET /api/v1/admin/cp/requests`, `GET /api/v1/admin/cp/audit`
  - `POST /api/v1/admin/cp/relationships/points` for audited manual point adjustments.
- Android CP hub now reads the CP-specific relationships and request APIs; incoming requests can be accepted/rejected and outgoing pending requests cancelled.
- The voice-room participant card includes a CP initiation action. It obtains the enabled type catalog and sends a unique idempotency key plus the exact room and recipient IDs.
- Android profile pages display active CP relationships.
- CP dashboard API access is included in the existing dashboard permission model under module key `cp`.

## Admin configuration keys

For each type `friendship`, `brotherhood`, `love`, `engagement`, `marriage`:
- `cp.enabled.<type>` — must be `true`.
- `cp.price.<type>` — positive coin price; intentionally absent until configured.
- `cp.gift.<type>` — an existing gift UUID; the service snapshots name/icon/animation when the request is sent.
- `cp.gift_points.<type>` — points awarded for the initiation gift after acceptance.
- `cp.levels.<type>` — JSON array of `{ level, pointsRequired, reward? }`, sorted by threshold.
- `cp.max_level.<type>` and `cp.rules.<type>` — optional cap and future eligibility/cardinality rules.
- `cp.policy.request_expiry_minutes` — admin-configurable between 5 and 1440; fallback is 30 minutes.

The dashboard view is included in `backend/dashboard/src/views/CpSettingsView.vue` and is permission-gated under the `cp` module. Normal one-to-one and all-mic gift sends call the CP point processor after the gift transaction commits; only active CP pairs earn the configured per-type points, and the event ledger prevents duplicate scoring.

## HTML concept

`backend/public/cp-preview.html` is a visual concept only. It contains demo names, avatars, scores, and levels and is not the Android screen or live ranking.

## Release guardrails and outstanding verification

- Do not merge or deploy yet. No Android APK build, backend TypeScript build, integration test, database migration, or production deployment has been executed in this environment.
- Review compilation and migration details first. Test concurrent requests, repeated idempotency keys, double response, cancellation, rejection/expiry refunds, missing gift config, room presence, and profile privacy.
- The native discovery screen has filters and live server data, but advanced animated frames and a complete detail/reward presentation still need visual QA.
- Do not alter Zego/LiveKit modules or environment settings as part of CP.
