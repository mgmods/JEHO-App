import 'package:flutter/material.dart';

const String apiBaseUrl = String.fromEnvironment(
  'JEHO_API_BASE_URL',
  defaultValue: 'https://jeho-own-backend.mgs.blitz.cloud/api/v1/',
);

void main() {
  runApp(const JehoOwnApp());
}

class JehoOwnApp extends StatelessWidget {
  const JehoOwnApp({super.key});

  @override
  Widget build(BuildContext context) {
    const brandColor = Color(0xFFB68A35);

    return MaterialApp(
      title: 'JEHO-OWN',
      debugShowCheckedModeBanner: false,
      locale: const Locale('ar'),
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(
          seedColor: brandColor,
          brightness: Brightness.light,
        ),
        scaffoldBackgroundColor: const Color(0xFFFFFBF3),
        appBarTheme: const AppBarTheme(
          backgroundColor: Color(0xFFFFF0CC),
          foregroundColor: Color(0xFF493512),
          centerTitle: true,
        ),
      ),
      builder: (context, child) => Directionality(
        textDirection: TextDirection.rtl,
        child: child ?? const SizedBox.shrink(),
      ),
      home: const MigrationHomePage(),
    );
  }
}

class MigrationHomePage extends StatefulWidget {
  const MigrationHomePage({super.key});

  @override
  State<MigrationHomePage> createState() => _MigrationHomePageState();
}

class _MigrationHomePageState extends State<MigrationHomePage> {
  int _selectedIndex = 0;

  static const _sections = <_SectionInfo>[
    _SectionInfo(
      title: 'الرئيسية',
      subtitle: 'سيتم نقل واجهة الصفحة الرئيسية وربط بياناتها في المرحلة التالية.',
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

    return Scaffold(
      appBar: AppBar(
        title: const Text(
          'JEHO-OWN',
          style: TextStyle(fontWeight: FontWeight.w800),
        ),
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
                    child: const Icon(
                      Icons.forum_rounded,
                      size: 46,
                      color: Color(0xFF8C641D),
                    ),
                  ),
                  const SizedBox(height: 22),
                  Text(
                    section.title,
                    style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                          fontWeight: FontWeight.w800,
                        ),
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
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(16),
                      child: Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Icon(Icons.shield_outlined, color: brandColor),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Text(
                              'هذه نسخة Flutter مستقلة للتطوير. التطبيق الحالي وواجهات الخادم وقاعدة البيانات لم يتم تغييرها.',
                              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                                    height: 1.6,
                                  ),
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
