# JEHO-OWN Flutter migration — incomplete candidate

**Important status:** this is not yet a complete migration of the original Android app. The original app is `android/`, package `com.Dramizo.Series`, and contains the existing production screens, assets, native integrations, and ZEGO voice-room implementation. `flutter_app/` began as a separate Flutter client workspace and must not be represented as a full replacement. The original Android app remains the production/rollback path.

The CI-generated Flutter APK is now configured and checked to target the same Android application ID, `com.Dramizo.Series`. Matching the package ID does **not** mean feature parity has been achieved, and installing an incomplete build over the original app is not approved. Do not merge or release this branch until the actual original app's screens, assets, API flows, permissions, and platform integrations have been migrated and tested feature by feature.

## Build and local run

Requirements: Flutter SDK 3.22+ and Android SDK.

```bash
flutter create --platforms=android --project-name jeho_own_flutter --org com.jehoown .
flutter pub get
flutter analyze
flutter test
flutter build apk --debug --dart-define=JEHO_API_BASE_URL=https://jeho-own-backend.mgs.blitz.cloud/api/v1/
```

CI generates the Android platform scaffold in its workspace, runs analysis and widget tests, builds a debug APK, and uploads the artifact. CI must be green on the latest branch commit before treating this checkpoint as validated. A debug APK is a test build, not a signed production release.

## Current partial Flutter flows (not production parity)

- Arabic RTL theme and login/register/guest authentication, secure token storage, and session restoration.
- Public room listing, room creation, room details, room entry/leave, password prompts, seat requests and selection, seat locking, room password lock, moderator actions, room follow, gift-to-host UI, room cover/background uploads, and room display controls where exposed by the existing API.
- Profile display name/bio editing and avatar upload.
- Direct-message list, message history, text sending, image/file attachment upload and sending, media rendering, message edit/unsend, mute/pin/archive/delete-for-me controls.
- Added a Flutter user-search screen using the existing public `GET users/search` and `GET users/:id` routes; search is accessible from the main app bar and opens a public profile preview.
- Added a separate `flutter_dashboard/` Flutter Web migration target with existing admin login integration and a responsive shell for key admin areas. It is currently a read-only migration foundation, not a replacement for the Vue dashboard; full CRUD, permissions, uploads, charts, and module-specific workflows remain incomplete.
- CI now includes a dedicated Flutter Web analyze/build job for the dashboard package.
- Initial Flutter drama catalog connected to the existing `drama/config`, `drama/series`, series detail, view, like, and episode playback routes. This is a partial port; player reward timing, all native player behaviors, and remaining app screens still require parity work.
- Initial Flutter games catalog reads Dashboard-controlled `config/games` and `config/offers`, opens configured web games, and applies the Dashboard `games` wallpaper when supplied. Native-only game modes, offline bundled game assets, daily task claiming, contests, and game store are not yet at parity.
- Flutter home/profile, drama, and games surfaces now read the existing `config/theme` background configuration; image loading is optional and does not block navigation.
- Wallet balances and transaction history, recharge packages and hosted checkout launch, withdrawal request submission, and withdrawal-status API support.
- Gift catalog, sending gifts, gift history API, and room gift-to-host flow.
- Cosmetics catalog/inventory, purchase/equip/unequip API and UI.
- Vanity ID catalog and purchase flow.
- Social/CP hub, friends/visitors/relations, follow/block tools, and CP request/status flows exposed by existing endpoints.
- Tasks, agency discovery/application/join-by-code, agency earnings and join-request views, events, contests, rankings, notifications, app-feature config, and mini-game views exposed by existing endpoints.
- CI checks for Flutter analysis, widget tests, debug APK build, and artifact upload; backend/dashboard/Docker checks run separately.

## Latest dashboard migration work (2026-10-13)

- Added existing read-only dashboard routes for dashboard charts, live streams, gift categories, VIP plans, wallet package lists, recharge-agent pricing, lucky-box configuration/open history, room-cup leaderboard, system logs, policy snapshot, and drama settings.
- Added guarded JSON-list editing for the existing daily-task and game-store catalog APIs. Both require a second confirmation before sending updates. These controls do not touch voice settings, ZEGO, LiveKit, Redis, credentials, encryption keys, or database infrastructure.
- These additions increase route coverage but do not replace the Vue dashboard's full CRUD forms, upload workflows, role-based UI permissions, chart visualizations, or acceptance testing. Verify each route against a non-production/test account before relying on administrative writes.

## Important limits (not yet production parity)

- **The original native application has not yet been fully ported.** The current Flutter screens are a partial reimplementation, not the original full app converted to Flutter. In particular, existing native screen behavior, deep links, platform integrations, and many production flows still need parity review.
- Some screens expose only a subset of the existing server functionality. Remaining parity work includes deeper moderation tools, complete relationship/CP sections and request edge cases, agency member/commission/payout workflows, task rewards, all wallet exchange/payment provider variants and verification/polling, gift combo/all-mic flows, and complete Dashboard-driven navigation/theme/content settings.
- The Dashboard remains its existing web application; this Flutter branch migrates the **mobile client**, not the Dashboard frontend.
- API method presence and a successful CI build do not prove live server correctness. Authenticated flows must be validated against the deployed backend using test accounts, including uploads on the deployed storage configuration, payment-provider callbacks, wallet accounting, and authorization failures.
- The current workflow creates a **debug APK**. A signed release APK/AAB requires the project’s release signing configuration and a separate release workflow.

## Release safety

1. Keep the current production APK available as the rollback path.
2. Reuse the existing NestJS API and current database. Never reset, delete, or reseed live user data as part of the migration.
3. Do not change ZEGO, voice-room behavior, audio credentials/tokens, or Redis dependencies.
4. Do not rotate encryption keys or change secrets during this migration.
5. Do not merge or switch production until CI succeeds and representative device and live-server acceptance tests pass.


## Safety checkpoints (2026-10-13)

- `jeho-own-backup-2026-10-13` is a preserved branch copied from the current `jeho-own` production branch before further Flutter migration work. It must not receive migration commits.
- `jeho-own-flutter-checkpoint-2026-10-13` preserves the Flutter migration state at commit `d9f85cdc6f366e85c54066a96d7f07348d417db0`.
- Continue migration only on `flutter-migration-jeho-own-2026-10-13`. Keep PR #8 as a draft until parity and validation are complete.
- To recover the app source baseline, use the `jeho-own-backup-2026-10-13` branch as the comparison/restore source; do not reset or force-push `jeho-own`.
- Never modify ZEGO, LiveKit, voice-room connection behavior, Redis, production secrets, encryption keys, or database state as part of the Flutter UI migration without a separate, explicit approval.
