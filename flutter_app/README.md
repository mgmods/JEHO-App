# JEHO-OWN Flutter client foundation

This folder is an **independent migration workspace**. The existing Android Java/Kotlin application remains intact and is still the current app. This is only the first scaffold; it does not yet replace existing screens or features.

## Requirements

- Flutter SDK 3.22 or newer
- Dart SDK matching the Flutter installation
- Android SDK and an Android emulator/device

## Run

From this directory, generate the Android platform folder once (it is intentionally generated in CI rather than committed during this first scaffold):

```bash
flutter create --platforms=android --project-name jeho_own_flutter --org com.jehoown .
flutter pub get
flutter analyze
flutter test
flutter run --dart-define=JEHO_API_BASE_URL=https://jeho-own-backend.mgs.blitz.cloud/api/v1/
```

The API base URL is configurable through `JEHO_API_BASE_URL`. The current starter calls the existing API for authentication, public rooms, profile, conversations, conversation messages, text-message sending, and basic profile edits. It is not yet a feature-complete replacement for the existing app.

## Current API coverage

The client currently calls the existing NestJS endpoints only:

- `POST auth/login`, `POST auth/register`, `POST auth/guest`
- `GET rooms?page=1&limit=20`
- `GET users/me` with the returned bearer token
- `GET chat/conversations?page=1&limit=20` with the returned bearer token
- `GET chat/conversations/:id/messages?page=1&limit=50` and `POST chat/conversations/:id/messages` for text messages
- `PATCH users/me` for display name and bio edits

No backend route or database schema was changed for this migration.

## Migration safety rules

1. Keep the current Android application and APK as the rollback path until the Flutter build passes feature-by-feature acceptance.
2. Reuse the existing NestJS API and current data; do not create or reset a second database.
3. Do not change ZEGO, voice-room behavior, audio credentials/tokens, or Redis dependencies as part of the UI migration.
4. Port and verify authentication, room list, profile, chat, wallet, gifts, and other features in small isolated stages.
5. Audio integration must be planned only after inspecting the exact currently used ZEGO SDK and native configuration; this starter deliberately contains no audio SDK or audio changes.
6. Do not release or switch the production APK until device tests and feature parity checks are complete.
