# JEHO-OWN Flutter client migration workspace

This is an isolated Flutter client migration. The existing Android app remains unchanged and is still the production/rollback path. This branch is not a complete replacement and must not be merged or released until all feature checks pass.

## Requirements and local run

- Flutter SDK 3.22 or newer
- Dart SDK matching Flutter
- Android SDK and a test device/emulator

```bash
flutter create --platforms=android --project-name jeho_own_flutter --org com.jehoown .
flutter pub get
flutter analyze
flutter test
flutter run --dart-define=JEHO_API_BASE_URL=https://jeho-own-backend.mgs.blitz.cloud/api/v1/
```

The Android platform scaffold is generated in CI, rather than committed as generated files at this stage. Configure the API root using `JEHO_API_BASE_URL`.

## Implemented client flows

- Arabic RTL theme and login/register/guest authentication with access-token storage and session restoration.
- Public room listing and room details.
- Gallery image upload for a room background, then `PATCH rooms/:id/background`; API permissions remain authoritative.
- Profile display-name/bio edits and avatar image upload.
- Conversations list, message history, and text-message sending.
- Wallet balance, withdrawal progress, transaction history, and recharge-package display.
- Gift catalog browsing.
- Cosmetics catalog/inventory, purchase, and equip calls.
- CP summary, friends, visitors, and accepted relations read-only hub.
- Flutter CI runs dependency install, `flutter analyze`, widget tests, debug APK build, and artifact upload.

## API routes currently used

- `POST auth/login`, `POST auth/register`, `POST auth/guest`
- `GET rooms?page=1&limit=20`, `GET rooms/:id`, `PATCH rooms/:id/background`
- `POST uploads` (multipart field `file`)
- `GET users/me`, `PATCH users/me`
- `GET chat/conversations?page=1&limit=20`
- `GET chat/conversations/:id/messages?page=1&limit=50`, `POST chat/conversations/:id/messages` (text)
- `GET wallet`, `GET wallet/transactions`, `GET wallet/packages`
- `GET gifts`, `GET gifts/categories`
- `GET cosmetics`, `GET cosmetics/inventory`, `POST cosmetics/purchase`, `POST cosmetics/equip`, `POST cosmetics/unequip`
- `GET users/me/cp`, `GET users/me/friends`, `GET users/me/visitors`, `GET users/me/relations`

## Known migration gaps (not yet feature parity)

- Native in-room voice, seat/mic moderation and realtime events are not ported. ZEGO and current voice infrastructure have intentionally not been touched.
- Room join/leave, room creation, room covers, seats, moderators, follows, room music, and live room controls still need implementation and device acceptance.
- Sending gifts to a selected user/room is not wired to a UI; only the gift catalog is shown.
- Recharge packages are informational only. Native store billing, purchase verification, withdrawal requests, and exchanges are not enabled in this Flutter UI.
- Cosmetics page supports catalog, purchase, and equip; richer filters, expiration details, and complete frame rendering still need parity work.
- CP/friends/visitors/relations are read-only. Requests, relationship creation/removal, and all requested CP subsections are not yet migrated.
- Chat media, attachments, delivery/read states, notifications, blocks, agency economy, mall/promotions, vanity IDs, drama, moderation tools, and dashboard-controlled navigation/splash settings still require migration.
- The image picker and cloud upload URL handling require device-level validation against the live environment.

## Safety / release rules

1. Keep the existing Android APK as the rollback path until feature-by-feature acceptance.
2. Reuse the existing NestJS API and current data; do not create, reset, or delete a database.
3. Do not change ZEGO, voice-room behavior, audio credentials/tokens, or Redis dependencies.
4. Do not change encryption keys or secrets during this migration.
5. Do not release or switch the production APK until CI succeeds and authentication, upload persistence, chat, wallet, and visual behavior are tested on a device.
