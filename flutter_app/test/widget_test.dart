import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:jeho_own_flutter/data/jeho_api_client.dart';
import 'package:jeho_own_flutter/main.dart';
import 'package:jeho_own_flutter/presentation/auth_page.dart';

void main() {
  test('section wallpaper overrides shared wallpaper and falls back safely', () {
    expect(resolveSectionWallpaper({'allSections': 'https://cdn.example/shared.jpg', 'profile': 'https://cdn.example/profile.jpg'}, 'profile'), 'https://cdn.example/profile.jpg');
    expect(resolveSectionWallpaper({'allSections': 'https://cdn.example/shared.jpg', 'profile': '', 'app': 'https://cdn.example/app.jpg'}, 'profile'), 'https://cdn.example/shared.jpg');
    expect(resolveSectionWallpaper({'allSections': '', 'profile': 'https://cdn.example/profile.jpg', 'app': 'https://cdn.example/app.jpg'}, 'profile'), 'https://cdn.example/profile.jpg');
    expect(resolveSectionWallpaper({'allSections': '  ', 'profile': '', 'app': 'https://cdn.example/app.jpg'}, 'profile'), 'https://cdn.example/app.jpg');
  });

  testWidgets('shows Arabic login and guest entry', (tester) async {
    final apiClient = JehoApiClient(
      baseUrl: 'https://example.invalid/api/v1/',
    );

    await tester.pumpWidget(
      MaterialApp(
        home: Directionality(
          textDirection: TextDirection.rtl,
          child: AuthPage(
            apiClient: apiClient,
            onAuthenticated: (_) {},
          ),
        ),
      ),
    );

    expect(find.text('أهلًا بك في JEHO-OWN'), findsOneWidget);
    expect(find.text('تسجيل الدخول'), findsOneWidget);
    expect(find.text('الدخول كضيف'), findsOneWidget);
    apiClient.dispose();
  });

  testWidgets('app renders Arabic auth screen', (tester) async {
    await tester.pumpWidget(const JehoOwnApp());

    expect(find.byType(AuthPage), findsOneWidget);
    expect(find.text('الدخول كضيف'), findsOneWidget);
  });
}
