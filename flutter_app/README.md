# JEHO-OWN Flutter client foundation

This folder is an **independent migration workspace**. The existing Android Java/Kotlin application remains intact and is still the current app. This is only the first scaffold; it does not yet replace existing screens or features.

## Requirements

- Flutter SDK 3.22 or newer
- Dart SDK matching the Flutter installation
- Android SDK and an Android emulator/device

## Run

From this directory:

```bash
flutter pub get
flutter analyze
flutter test
flutter run --dart-define=JEHO_API_BASE_URL=https://jeho-own-backend.mgs.blitz.cloud/api/v1/
```

The API base URL is configurable through `JEHO_API_BASE_URL`. No API calls are made by the starter screen yet.

## Migration safety rules

1. Keep the current Android application and APK as the rollback path until the Flutter build passes feature-by-feature acceptance.
2. Reuse the existing NestJS API and current data; do not create or reset a second database.
3. Do not change ZEGO, voice-room behavior, audio credentials/tokens, or Redis dependencies as part of the UI migration.
4. Port and verify authentication, room list, profile, chat, wallet, gifts, and other features in small isolated stages.
5. Audio integration must be planned only after inspecting the exact currently used ZEGO SDK and native configuration; this starter deliberately contains no audio SDK or audio changes.
6. Do not release or switch the production APK until device tests and feature parity checks are complete.
