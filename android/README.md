# AuraLive Android

Production-oriented Java Android client for the AuraLive live streaming platform.

## Requirements

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 34
- Min SDK 24

## Open the project

1. Open Android Studio
2. **File → Open** and select this `android` folder
3. Wait for Gradle sync
4. Replace `app/google-services.json` with your real Firebase config when ready

## Configure keys (`local.properties`)

Create or edit `android/local.properties` (already gitignored by Android Studio defaults):

```
sdk.dir=C:\\Users\\<YOU>\\AppData\\Local\\Android\\Sdk
ZEGO_APP_ID=123456789
ZEGO_APP_SIGN=your_zego_app_sign_hex
API_BASE_URL=http://10.0.2.2:3000/api/v1/
```

Notes:

- `10.0.2.2` is the Android emulator alias for the host machine `localhost`
- Use your LAN IP for a physical device (e.g. `http://192.168.1.10:3000/api/v1/`)
- Values are injected into `BuildConfig` at build time

## Architecture

```
app/
  presentation/   # Activities, Fragments, ViewModels (MVVM)
  domain/         # UseCases + repository interfaces
  data/           # Retrofit APIs, Room cache, repository impls
  di/             # AppContainer manual DI
  zego/           # ZegoEngineManager (Express Video SDK)
  billing/        # Google Play BillingHelper
  fcm/            # AuraMessagingService
```

## Features wired

- Auth: login, register, OTP, guest, social buttons (token → `/auth/social`)
- Main bottom nav: Home, Discover, Live, Messages, Profile
- Live streaming + voice rooms via ZEGOCLOUD Express
- Chat list + conversation (reply UI + typing indicator)
- Wallet + Play Billing verify → `/wallet/verify-purchase`
- Gift store bottom sheet, VIP 1–10, Ranking, Agency, Settings, Notifications

## Run

1. Start the AuraLive Nest backend on port 3000
2. Sync Gradle and run the `app` configuration on an emulator/device
3. Grant camera/mic permissions when joining live/voice rooms

## Brand

Teal/cyan aurora Material 3 theme (light + dark). Arabic strings in `values-ar`.
