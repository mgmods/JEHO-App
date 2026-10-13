import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:jeho_own_flutter/data/jeho_api_client.dart';
import 'package:jeho_own_flutter/main.dart';
import 'package:jeho_own_flutter/presentation/auth_page.dart';

void main() {
  test('section wallpaper overrides shared wallpaper and falls back safely', () {
    expect(
      resolveSectionWallpaper({
        'allSections': 'https://cdn.example/shared.jpg',
        'profile': 'https://cdn.example/profile.jpg',
      }, 'profile'),
      'https://cdn.example/profile.jpg',
    );
    expect(
      resolveSectionWallpaper({
        'allSections': 'https://cdn.example/shared.jpg',
        'profile': '',
        'app': 'https://cdn.example/app.jpg',
      }, 'profile'),
      'https://cdn.example/shared.jpg',
    );
    expect(
      resolveSectionWallpaper({
        'allSections': '',
        'profile': 'https://cdn.example/profile.jpg',
        'app': 'https://cdn.example/app.jpg',
      }, 'profile'),
      'https://cdn.example/profile.jpg',
    );
    expect(
      resolveSectionWallpaper({
        'allSections': '  ',
        'profile': '',
        'app': 'https://cdn.example/app.jpg',
      }, 'profile'),
      'https://cdn.example/app.jpg',
    );
  });

  test('only definitive auth failures invalidate a saved session', () {
    expect(
      shouldClearSessionForError(
        const JehoApiException('expired', statusCode: 401),
      ),
      isTrue,
    );
    expect(
      shouldClearSessionForError(
        const JehoApiException('forbidden', statusCode: 403),
      ),
      isTrue,
    );
    expect(
      shouldClearSessionForError(
        const JehoApiException('rate limited', statusCode: 429),
      ),
      isFalse,
    );
    expect(
      shouldClearSessionForError(
        const JehoApiException('server error', statusCode: 500),
      ),
      isFalse,
    );
    expect(
      shouldClearSessionForError(
        const JehoApiException('network unavailable'),
      ),
      isFalse,
    );
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

    expect(find.text('تواصل مع أصدقائك، واكتشف الغرف والمجتمعات في JEHO'), findsOneWidget);
    expect(find.text('تسجيل الدخول'), findsOneWidget);
    expect(find.text('الدخول كضيف'), findsOneWidget);
    apiClient.dispose();
  });

  testWidgets('app renders Arabic auth screen after session restore', (
    tester,
  ) async {
    await tester.pumpWidget(const JehoOwnApp());

    // Session restoration is asynchronous and initially shows the branded
    // startup splash. Advance the test clock without waiting on its spinner.
    await tester.pump();
    // Secure-storage plugins may not be registered in widget tests. The
    // startup restore has a bounded timeout, after which login must render.
    await tester.pump(const Duration(seconds: 9));
    await tester.pump();

    expect(find.byType(AuthPage), findsOneWidget);
    expect(find.text('الدخول كضيف'), findsOneWidget);
  });
}
