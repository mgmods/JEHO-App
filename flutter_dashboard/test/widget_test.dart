import 'package:flutter_test/flutter_test.dart';
import 'package:jeho_admin_flutter/main.dart';

void main() {
  testWidgets('admin login screen renders in Arabic', (tester) async {
    await tester.pumpWidget(const AdminApp());
    expect(find.text('JEHO-OWN'), findsOneWidget);
    expect(find.text('لوحة الإدارة Flutter'), findsOneWidget);
    expect(find.text('تسجيل الدخول'), findsOneWidget);
  });
}
