# JEHO-OWN CP System — implementation notes

## Confirmed product rules

- Relationship types are independent: friendship, brotherhood, love, engagement, marriage.
- Relationship-initiation gifts may be sent only from a valid voice-room context. This must be enforced server-side.
- The receiver must explicitly accept before an active relationship is created.
- Rejecting or expiring a request refunds the full gift price; technical failure before commit must not charge.
- Either partner may end an active relationship alone.
- Multiple friendship and brotherhood relationships are allowed.
- Other relationship cardinality is not yet specified; do not assume one romantic bond or allow conflicting bonds without an explicit product rule.
- Gift prices are intentionally unset. Example prices must not be seeded as production defaults.
- Existing audio configuration, credentials, provider settings, tokens, and room voice connectivity are out of scope and must not be modified.

## Existing source discovered

- Android native client: Java, Android views, existing `presentation/cp/CpCenterActivity.java` and `activity_cp_center.xml`.
- Backend: NestJS 10, TypeScript, TypeORM, PostgreSQL, Redis, Socket.IO/JWT.
- Existing wallet: `Wallet.coins`, `Wallet.diamonds`; existing `WalletTransaction` supports a unique user/reference tuple.
- Existing generic social relation: `SocialRequestType.RELATION`, `UsersService.myCpStatus`, request accept/reject, and end-bond endpoints. This flow is currently generic and is not sufficient to model all five CP types independently.
- The Android CP screen currently allows a CP request from outside a room and presents a single partner; that initiation path must be replaced for relationship-creation gifts, while existing request acceptance/rejection can remain available outside the room.

## HTML concept

Open `backend/public/cp-preview.html` for a responsive RTL concept of the CP discovery page. It uses demo-only names, avatar images, scores, levels, and durations. It is not wired to real API data and must never be used to claim live ranking results.

## Production implementation order

1. Define CP-specific entities/tables and migrations for relationship types, gifts, requests, relationships/history, points ledger, levels/rewards, refund records, and audit events.
2. Confirm the app's existing authoritative room-membership source and wallet transaction semantics before implementing send-gift endpoints.
3. Implement idempotent transactional initiation: validate authenticated sender, actual room membership and selected recipient, validate configured gift price and eligibility, debit coins once, write wallet transaction and pending request atomically, then emit notification after commit.
4. Implement accept/reject/expiry state transitions. Acceptance creates the relationship only after checking current conflicts. Rejection/expiry refund the full original debit exactly once.
5. Implement server-derived relationship rankings based on documented activity/points, not client-supplied scores; add profile relationship projection for both partners.
6. Update Android CP hub and in-room user action to use CP-specific endpoints. Add a discover page with five filters, framed paired avatars, per-type lightweight animation, details, and reduced-motion behavior.
7. Add authenticated dashboard settings and audit history. Keep prices unset until configured by an authorized admin.
8. Add tests for race conditions, duplicate idempotency keys, double acceptance, repeated refunds, leaving rooms, recipient mismatch, blocked/ineligible users, conflict rules, ended relationships, and no audio regression.

## Release guardrails

- Do not enable TypeORM schema synchronization as a substitute for reviewed migrations.
- Do not alter Zego/LiveKit modules or environment settings as part of CP.
- Do not deploy this feature or apply database migrations until build/test results and migration review pass.
- The five-type CP system is not complete until API, database, Android UI, profile UI, admin configuration, and security tests are integrated.
