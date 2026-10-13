# JEHO-OWN Flutter client migration workspace

This branch is an isolated Flutter migration candidate. The existing Android application remains the production/rollback path. Do not merge or release this branch until feature-by-feature review and live-device acceptance are complete.

## Build and local run

Requirements: Flutter SDK 3.22+ and Android SDK.

```bash
flutter create --platforms=android --project-name jeho_own_flutter --org com.jehoown .
flutter pub get
flutter analyze
flutter test
flutter build apk --debug --dart-define=JEHO_API_BASE_URL=https://jeho-own-backend.mgs.blitz.cloud/api/v1/
```

CI generates the Android platform scaffold in its workspace, runs analysis and widget tests, builds a debug APK, and uploads the artifact. The latest successful run at the time of this update is documented in the pull request checks. A debug APK is a test build, not a signed production release.

## Implemented Flutter flows (client-side integration)

- Arabic RTL theme and login/register/guest authentication, secure token storage, and session restoration.
- Public room listing, room creation, room details, room entry/leave, password prompts, seat requests and selection, seat locking, room password lock, moderator actions, room follow, gift-to-host UI, room cover/background uploads, and room display controls where exposed by the existing API.
- Profile display name/bio editing and avatar upload.
- Direct-message list, message history, text sending, image/file attachment upload and sending, media rendering, message edit/unsend, mute/pin/archive/delete-for-me controls.
- Wallet balances and transaction history, recharge packages and hosted checkout launch, withdrawal request submission, and withdrawal-status API support.
- Gift catalog, sending gifts, gift history API, and room gift-to-host flow.
- Cosmetics catalog/inventory, purchase/equip/unequip API and UI.
- Vanity ID catalog and purchase flow.
- Social/CP hub, friends/visitors/relations, follow/block tools, and CP request/status flows exposed by existing endpoints.
- Tasks, agency discovery/application/join-by-code, agency earnings and join-request views, events, contests, rankings, notifications, app-feature config, and mini-game views exposed by existing endpoints.
- CI checks for Flutter analysis, widget tests, debug APK build, and artifact upload; backend/dashboard/Docker checks run separately.

## Important limits (not yet production parity)

- **Voice is deliberately not migrated or modified.** The current ZEGO integration, voice tokens, room audio connection, audio settings, and Redis/realtime infrastructure must remain untouched. Room entry and seat management UI calls do not themselves provide a complete native voice-room experience.
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
