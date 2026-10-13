import 'package:flutter_test/flutter_test.dart';
import 'package:jeho_own_flutter/main.dart';

void main() {
  testWidgets('renders JEHO-OWN Arabic navigation shell', (tester) async {
    await tester.pumpWidget(const JehoOwnApp());

    expect(find.text('JEHO-OWN'), findsOneWidget);
    expect(find.text('الرئيسية'), findsWidgets);
    expect(find.text('الرسائل'), findsOneWidget);
    expect(find.text('أنا'), findsOneWidget);
  });
}
