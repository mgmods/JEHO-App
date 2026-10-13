import 'package:flutter/material.dart';

import 'data/jeho_api_client.dart';
import 'presentation/auth_page.dart';

const Color _brandColor = Color(0xFFB68A35);

const String apiBaseUrl = String.fromEnvironment(
  'JEHO_API_BASE_URL',
  defaultValue: 'https://jeho-own-backend.mgs.blitz.cloud/api/v1/',
);

void main() {
  runApp(const JehoOwnApp());
}

class JehoOwnApp extends StatefulWidget {
  const JehoOwnApp({super.key});

  @override
  State<JehoOwnApp> createState() => _JehoOwnAppState();
}

class _JehoOwnAppState extends State<JehoOwnApp> {
  late final JehoApiClient _apiClient;
  Map<String, dynamic>? _session;

  @override
  void initState() {
    super.initState();
    _apiClient = JehoApiClient(baseUrl: apiBaseUrl);
  }

  @override
  void dispose() {
    _apiClient.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'JEHO-OWN',
      debugShowCheckedModeBanner: false,
      locale: const Locale('ar'),
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(
          seedColor: _brandColor,
          brightness: Brightness.light,
        ),
        scaffoldBackgroundColor: const Color(0xFFFFFBF3),
        appBarTheme: const AppBarTheme(
          backgroundColor: Color(0xFFFFF0CC),
          foregroundColor: Color(0xFF493512),
          centerTitle: true,
        ),
        inputDecorationTheme: const InputDecorationTheme(
          filled: true,
          fillColor: Colors.white,
        ),
      ),
      builder: (context, child) => Directionality(
        textDirection: TextDirection.rtl,
        child: child ?? const SizedBox.shrink(),
      ),
      home: _session == null
          ? AuthPage(
              apiClient: _apiClient,
              onAuthenticated: (session) => setState(() => _session = session),
            )
          : MigrationHomePage(
              session: _session!,
              onLogout: () async {
                await _apiClient.clearSession();
                if (mounted) setState(() => _session = null);
              },
            ),
    );
  }
}

class MigrationHomePage extends StatefulWidget {
  const MigrationHomePage({
    required this.session,
    required this.onLogout,
    super.key,
  });

  final Map<String, dynamic> session;
  final VoidCallback onLogout;

  @override
  State<MigrationHomePage> createState() => _MigrationHomePageState();
}

class _MigrationHomePageState extends State<MigrationHomePage> {
  int _selectedIndex = 0;

  static const _sections = <_SectionInfo>[
    _SectionInfo(
      title: 'الرئيسية',
      subtitle: 'تم تسجيل الدخول بنجاح. سيتم نقل واجهة الصفحة الرئيسية وربط بياناتها في المرحلة التالية.',
      icon: Icons.home_rounded,
    ),
    _SectionInfo(
      title: 'الرسائل',
      subtitle: 'سيتم ربط المحادثات وواجهاتها مع API الحالي بعد تدقيق العقود.',
      icon: Icons.chat_bubble_rounded,
    ),
    _SectionInfo(
      title: 'أنا',
      subtitle: 'سيتم نقل الملف الشخصي والإطارات والإعدادات تدريجيًا.',
      icon: Icons.person_rounded,
    ),
  ];

  @override
  Widget build(BuildContext context) {
    final section = _sections[_selectedIndex];
    final user = widget.session['user'] is Map<String, dynamic>
        ? widget.session['user'] as Map<String, dynamic>
        : <String, dynamic>{};
    final displayName = (user['displayName'] ?? user['username'] ?? 'مستخدم JEHO')
        .toString();

    return Scaffold(
      appBar: AppBar(
        title: const Text(
          'JEHO-OWN',
          style: TextStyle(fontWeight: FontWeight.w800),
        ),
        actions: [
          IconButton(
            tooltip: 'تسجيل الخروج',
            onPressed: widget.onLogout,
            icon: const Icon(Icons.logout_rounded),
          ),
        ],
      ),
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 480),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Container(
                    width: 88,
                    height: 88,
                    decoration: BoxDecoration(
                      color: const Color(0xFFFFE7B0),
                      borderRadius: BorderRadius.circular(26),
                    ),
                    child: Icon(
                      section.icon,
                      size: 46,
                      color: const Color(0xFF8C641D),
                    ),
                  ),
                  const SizedBox(height: 22),
                  Text(
                    section.title,
                    style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                          fontWeight: FontWeight.w800,
                        ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    'أهلًا $displayName',
                    textAlign: TextAlign.center,
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  const SizedBox(height: 12),
                  Text(
                    section.subtitle,
                    textAlign: TextAlign.center,
                    style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                          height: 1.7,
                          color: Colors.black54,
                        ),
                  ),
                  const SizedBox(height: 24),
                  const Card(
                    child: Padding(
                      padding: EdgeInsets.all(16),
                      child: Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Icon(Icons.shield_outlined, color: _brandColor),
                          SizedBox(width: 12),
                          Expanded(
                            child: Text(
                              'هذه نسخة Flutter مستقلة للتطوير. التطبيق الحالي وواجهات الخادم وقاعدة البيانات لم يتم تغييرها.',
                              style: TextStyle(height: 1.6),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _selectedIndex,
        onDestinationSelected: (index) {
          setState(() => _selectedIndex = index);
        },
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.home_outlined),
            selectedIcon: Icon(Icons.home_rounded),
            label: 'الرئيسية',
          ),
          NavigationDestination(
            icon: Icon(Icons.chat_bubble_outline_rounded),
            selectedIcon: Icon(Icons.chat_bubble_rounded),
            label: 'الرسائل',
          ),
          NavigationDestination(
            icon: Icon(Icons.person_outline_rounded),
            selectedIcon: Icon(Icons.person_rounded),
            label: 'أنا',
          ),
        ],
      ),
    );
  }
}

class _SectionInfo {
  const _SectionInfo({
    required this.title,
    required this.subtitle,
    required this.icon,
  });

  final String title;
  final String subtitle;
  final IconData icon;
}
