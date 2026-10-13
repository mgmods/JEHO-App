# JEHO-OWN Flutter dashboard migration

This package is a **separate Flutter Web migration target**. It does not replace or delete the existing Vue dashboard, and it does not change production routing.

## Run locally

```sh
cd flutter_dashboard
flutter pub get
flutter run -d chrome --dart-define=JEHO_API_BASE_URL=https://jeho-own-backend.mgs.blitz.cloud/api/v1/
```

## Migration status

- Flutter Web login uses the existing `/admin/auth/login` endpoint.
- The initial responsive admin shell and read-only API viewer are in place for dashboard overview, users, conversations, rooms, gifts, paid-message settings, banners, offers, games, drama, agencies, wallet, VIP and settings.
- Each section currently reads data and displays the response. This is a migration foundation, **not feature parity** with the existing Vue admin.
- CRUD forms, role/permission-aware navigation, upload flows, analytics charts, and specialized management workflows still need porting and verification before the Flutter dashboard can replace Vue.
- Do not expose any secrets in source. Keep production dashboard and API settings unchanged during migration.
- Audio / ZEGO settings are deliberately not included as an editable module.
