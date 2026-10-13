import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:image_picker/image_picker.dart';

import 'data/jeho_api_client.dart';
import 'presentation/auth_page.dart';
import 'presentation/conversation_page.dart';
import 'presentation/feature_pages.dart';
import 'presentation/feature_hub_page.dart';
import 'presentation/discovery_hub_page.dart';
import 'presentation/drama_page.dart';
import 'presentation/games_page.dart';
import 'presentation/user_search_page.dart';
import 'presentation/settings_page.dart';

const Color _brandColor = Color(0xFFB68A35);

const String apiBaseUrl = String.fromEnvironment(
  'JEHO_API_BASE_URL',
  defaultValue: 'https://jeho-own-backend.mgs.blitz.cloud/api/v1/',
);

/// Only definitive authentication rejection should invalidate a stored session.
/// Network errors, rate limits and server failures must not erase valid credentials.
bool shouldClearSessionForError(JehoApiException error) =>
    error.statusCode == 401 || error.statusCode == 403;

String resolveSectionWallpaper(Map<String, dynamic> backgrounds, String sectionKey) {
  // Section-specific background must override the shared wallpaper.
  final section = (backgrounds[sectionKey] ?? '').toString().trim();
  if (section.isNotEmpty) return section;
  final shared = (backgrounds['allSections'] ?? '').toString().trim();
  if (shared.isNotEmpty) return shared;
  return (backgrounds['app'] ?? '').toString().trim();
}

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
  bool _isRestoringSession = true;

  @override
  void initState() {
    super.initState();
    _apiClient = JehoApiClient(baseUrl: apiBaseUrl);
    _restoreSession();
  }

  Future<void> _restoreSession() async {
    try {
      final token = await _apiClient.readAccessToken().timeout(const Duration(seconds: 8));
      if (token == null || token.isEmpty || !mounted) return;
      final profile = await _apiClient.getMyProfile(token).timeout(const Duration(seconds: 8));
      if (!mounted) return;
      setState(() {
        _session = {'accessToken': token, 'user': profile};
      });
    } on JehoApiException catch (error) {
      if (shouldClearSessionForError(error)) {
        try {
          await _apiClient.clearSession();
        } catch (_) {
          // A storage plugin may not be available in widget-test environments.
        }
      }
    } catch (_) {
      // Keep the login screen usable if secure storage is unavailable.
    } finally {
      if (mounted) setState(() => _isRestoringSession = false);
    }
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
        scaffoldBackgroundColor: const Color(0xFFFFF9F0),
        cardTheme: CardThemeData(
          color: const Color(0xFFFFFEFB),
          elevation: 0,
          margin: EdgeInsets.zero,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(20),
            side: const BorderSide(color: Color(0xFFEEDFC6)),
          ),
        ),
        appBarTheme: const AppBarTheme(
          backgroundColor: Color(0xFFFFF9F0),
          foregroundColor: Color(0xFF493512),
          centerTitle: false,
          elevation: 0,
          scrolledUnderElevation: 0,
        ),
        navigationBarTheme: NavigationBarThemeData(
          backgroundColor: const Color(0xFFFFFEFB),
          indicatorColor: const Color(0xFFFFE7B0),
          elevation: 4,
          labelTextStyle: WidgetStateProperty.resolveWith((states) => TextStyle(
            fontSize: 11,
            fontWeight: states.contains(WidgetState.selected) ? FontWeight.w800 : FontWeight.w500,
            color: states.contains(WidgetState.selected) ? const Color(0xFF8C641D) : const Color(0xFF8A8174),
          )),
        ),
        inputDecorationTheme: const InputDecorationTheme(
          filled: true,
          fillColor: Colors.white,
          border: OutlineInputBorder(borderRadius: BorderRadius.all(Radius.circular(16)), borderSide: BorderSide(color: Color(0xFFEEDFC6))),
          enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.all(Radius.circular(16)), borderSide: BorderSide(color: Color(0xFFEEDFC6))),
        ),
      ),
      builder: (context, child) => Directionality(
        textDirection: TextDirection.rtl,
        child: child ?? const SizedBox.shrink(),
      ),
      home: _isRestoringSession
          ? const _StartupSplash()
          : _session == null
              ? AuthPage(
                  apiClient: _apiClient,
                  onAuthenticated: (session) => setState(() => _session = session),
                )
              : MigrationHomePage(
                  session: _session!,
                  apiClient: _apiClient,
                  onLogout: () async {
                    await _apiClient.clearSession();
                    if (mounted) setState(() => _session = null);
                  },
                ),
    );
  }
}

class _StartupSplash extends StatelessWidget {
  const _StartupSplash();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Stack(
        fit: StackFit.expand,
        children: [
          Image.asset(
            'assets/original/android-res/drawable-xxhdpi/home_page_bg.webp',
            fit: BoxFit.cover,
            errorBuilder: (_, __, ___) => const DecoratedBox(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [Color(0xFFFFF3D5), Color(0xFFE9C77D)],
                ),
              ),
            ),
          ),
          DecoratedBox(
            decoration: BoxDecoration(color: Colors.white.withOpacity(0.20)),
          ),
          Center(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  width: 116,
                  height: 116,
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(32),
                    boxShadow: const [
                      BoxShadow(
                        color: Color(0x22000000),
                        blurRadius: 24,
                        offset: Offset(0, 8),
                      ),
                    ],
                  ),
                  child: Image.asset(
                    'assets/original/android-res/drawable-xxhdpi/jeho_logo.webp',
                    fit: BoxFit.contain,
                    errorBuilder: (_, __, ___) => const Icon(
                      Icons.forum_rounded,
                      size: 64,
                      color: Color(0xFF8C641D),
                    ),
                  ),
                ),
                const SizedBox(height: 18),
                const Text(
                  'JEHO-OWN',
                  style: TextStyle(
                    color: Color(0xFF493512),
                    fontSize: 24,
                    fontWeight: FontWeight.w900,
                    letterSpacing: 1.2,
                  ),
                ),
                const SizedBox(height: 22),
                const SizedBox(
                  width: 24,
                  height: 24,
                  child: CircularProgressIndicator(
                    strokeWidth: 2.5,
                    color: Color(0xFF8C641D),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class MigrationHomePage extends StatefulWidget {
  const MigrationHomePage({
    required this.session,
    required this.apiClient,
    required this.onLogout,
    super.key,
  });

  final Map<String, dynamic> session;
  final JehoApiClient apiClient;
  final VoidCallback onLogout;

  @override
  State<MigrationHomePage> createState() => _MigrationHomePageState();
}

class _MigrationHomePageState extends State<MigrationHomePage> {
  int _selectedIndex = 0;
  String _homeRoomFilter = 'شائع';
  String _homeTab = 'حفلة';
  bool _loading = true;
  String? _loadError;
  List<Map<String, dynamic>> _rooms = const [];
  List<Map<String, dynamic>> _banners = const [];
  Map<String, dynamic> _theme = const {};
  List<Map<String, dynamic>> _conversations = const [];
  Map<String, dynamic>? _profile;

  List<Map<String, dynamic>> get _visibleRooms {
    var rooms = _rooms;
    if (_homeTab == 'ملكي') {
      final sessionUser = widget.session['user'] is Map
          ? widget.session['user'] as Map
          : const <String, dynamic>{};
      final userId = (_profile?['id'] ?? sessionUser['id'] ?? '').toString();
      if (userId.isNotEmpty) {
        rooms = rooms.where((room) {
          final nestedOwner = room['owner'] is Map ? room['owner'] as Map : room['host'] is Map ? room['host'] as Map : const <String, dynamic>{};
          final ownerId = (room['ownerId'] ?? room['hostId'] ?? room['creatorId'] ?? room['userId'] ?? nestedOwner['id'] ?? '').toString();
          return ownerId == userId;
        }).toList();
      } else {
        rooms = const [];
      }
    }
    if (_homeRoomFilter == 'شائع') return rooms;
    final target = _homeRoomFilter == 'سوريا' ? 'سوريا' : _homeRoomFilter == 'مصر' ? 'مصر' : 'المملكة العربية السعودية';
    return rooms.where((room) {
      final country = (room['countryName'] ?? room['country'] ?? room['region'] ?? '').toString();
      return country.contains(target) || (target == 'المملكة العربية السعودية' && (country.contains('السعودية') || country.contains('Saudi')));
    }).toList();
  }


  @override
  void initState() {
    super.initState();
    _loadHomeData();
  }

  Future<void> _loadHomeData() async {
    setState(() {
      _loading = true;
      _loadError = null;
    });
    final token = widget.session['accessToken']?.toString() ?? '';
    try {
      final rooms = await widget.apiClient.getPublicRooms();
      // Banner configuration is optional and must never block room browsing.
      List<Map<String, dynamic>> banners = const [];
      try {
        banners = await widget.apiClient.getHomeBanners();
      } on JehoApiException {
        // Keep the home screen usable when banner configuration is unavailable.
      }
      Map<String, dynamic> theme = _theme;
      try {
        theme = await widget.apiClient.getAppTheme();
      } on JehoApiException {
        // Theme is optional; retain the last loaded value or bundled appearance.
      }
      Map<String, dynamic>? profile;
      List<Map<String, dynamic>> conversations = const [];
      if (token.isNotEmpty) {
        try {
          profile = await widget.apiClient.getMyProfile(token);
        } on JehoApiException {
          // Keep public room browsing available if the profile endpoint is unavailable.
        }
        try {
          conversations = await widget.apiClient.getConversations(token);
        } on JehoApiException {
          // Conversation errors are shown only inside the messages section.
        }
      }
      if (!mounted) return;
      setState(() {
        _rooms = rooms;
        _banners = banners;
        _theme = theme;
        _profile = profile;
        _conversations = conversations;
      });
    } on JehoApiException catch (error) {
      if (mounted) setState(() => _loadError = error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _createRoom() async {
    final token = widget.session['accessToken']?.toString() ?? '';
    if (token.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('سجّل الدخول أولًا لإنشاء غرفة.')),
      );
      return;
    }
    final titleController = TextEditingController();
    final descriptionController = TextEditingController();
    int seatCount = 11;
    try {
      final form = await showDialog<Map<String, Object?>>(
        context: context,
        builder: (dialogContext) => StatefulBuilder(
          builder: (context, setDialogState) => AlertDialog(
            title: const Text('إنشاء غرفة جديدة'),
            content: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  TextField(
                    controller: titleController,
                    autofocus: true,
                    maxLength: 128,
                    decoration: const InputDecoration(labelText: 'اسم الغرفة'),
                  ),
                  const SizedBox(height: 10),
                  TextField(
                    controller: descriptionController,
                    maxLines: 3,
                    decoration: const InputDecoration(labelText: 'وصف الغرفة (اختياري)'),
                  ),
                  const SizedBox(height: 12),
                  DropdownButtonFormField<int>(
                    initialValue: seatCount,
                    decoration: const InputDecoration(labelText: 'عدد المقاعد'),
                    items: [2, 5, 8, 11, 15, 20, 30]
                        .map((count) => DropdownMenuItem<int>(
                              value: count,
                              child: Text('$count مقعد'),
                            ))
                        .toList(),
                    onChanged: (value) {
                      if (value != null) setDialogState(() => seatCount = value);
                    },
                  ),
                ],
              ),
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(dialogContext),
                child: const Text('إلغاء'),
              ),
              FilledButton(
                onPressed: () => Navigator.pop(dialogContext, {
                  'title': titleController.text.trim(),
                  'description': descriptionController.text.trim(),
                  'seatCount': seatCount,
                  'type': 'voice',
                  'preferPersonal': true,
                }),
                child: const Text('إنشاء'),
              ),
            ],
          ),
        ),
      );
      if (form == null || !mounted) return;
      final title = (form['title'] ?? '').toString();
      if (title.length < 2) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('اكتب اسمًا للغرفة من حرفين على الأقل.')),
        );
        return;
      }
      final created = await widget.apiClient.createRoom(token, form);
      if (!mounted) return;
      final room = created['room'] is Map<String, dynamic>
          ? created['room'] as Map<String, dynamic>
          : created;
      await _loadHomeData();
      if (!mounted) return;
      final roomId = (room['id'] ?? room['roomId'] ?? '').toString();
      if (roomId.isEmpty) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('تم إرسال طلب الإنشاء، لكن الخادم لم يُرجع معرّف الغرفة لفتحها.')),
        );
        return;
      }
      await Navigator.of(context).push(
        MaterialPageRoute<void>(
          builder: (_) => RoomDetailPage(
            apiClient: widget.apiClient,
            accessToken: token,
            room: room,
          ),
        ),
      );
      if (mounted) await _loadHomeData();
    } on JehoApiException catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(error.message)),
        );
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('تعذر إنشاء الغرفة. حاول مرة أخرى.')),
        );
      }
    } finally {
      titleController.dispose();
      descriptionController.dispose();
    }
  }

  Future<void> _changeAvatar() async {
    final token = widget.session['accessToken']?.toString() ?? '';
    if (token.isEmpty) return;
    try {
      final picked = await ImagePicker().pickImage(source: ImageSource.gallery, imageQuality: 86, maxWidth: 1200, maxHeight: 1200);
      if (picked == null || !mounted) return;
      final url = await widget.apiClient.uploadImage(token, File(picked.path));
      final updated = await widget.apiClient.updateMyProfile(token, {'avatarUrl': url});
      if (!mounted) return;
      setState(() => _profile = {...?_profile, ...updated, 'avatarUrl': url});
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('تم تحديث الصورة الشخصية.')));
    } on JehoApiException catch (error) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(error.message)));
    } catch (_) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('تعذر اختيار الصورة أو رفعها. حاول مرة أخرى.')));
    }
  }

  String _resolveMediaUrl(String value) {
    if (value.startsWith('http://') || value.startsWith('https://')) return value;
    final base = Uri.parse(widget.apiClient.baseUrl);
    final origin = Uri(scheme: base.scheme, host: base.host, port: base.hasPort ? base.port : null);
    final normalized = value.replaceFirst(RegExp(r'^/uploads/'), 'uploads/');
    if (normalized.startsWith('uploads/')) return origin.resolve('/$normalized').toString();
    return origin.resolve(value.startsWith('/') ? value : '/$value').toString();
  }

  Future<void> _editProfile(Map<String, dynamic> activeUser) async {
    final token = widget.session['accessToken']?.toString() ?? '';
    if (token.isEmpty) return;
    final nameController = TextEditingController(
      text: (activeUser['displayName'] ?? '').toString(),
    );
    final bioController = TextEditingController(
      text: (activeUser['bio'] ?? '').toString(),
    );
    final changes = await showDialog<Map<String, String>>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('تعديل الملف الشخصي'),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: nameController,
                maxLength: 128,
                decoration: const InputDecoration(labelText: 'الاسم الظاهر'),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: bioController,
                maxLines: 3,
                decoration: const InputDecoration(labelText: 'نبذة عني'),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: const Text('إلغاء'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(dialogContext, {
              'displayName': nameController.text.trim(),
              'bio': bioController.text.trim(),
            }),
            child: const Text('حفظ'),
          ),
        ],
      ),
    );
    nameController.dispose();
    bioController.dispose();
    if (changes == null || !mounted) return;
    try {
      final updated = await widget.apiClient.updateMyProfile(token, changes);
      if (!mounted) return;
      setState(() {
        _profile = {...? _profile, ...updated};
      });
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('تم تحديث الملف الشخصي.')),
      );
    } on JehoApiException catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(error.message)),
        );
      }
    }
  }

  static const _sections = <_SectionInfo>[
    _SectionInfo(
      title: 'الرئيسية',
      subtitle: 'تصفح الغرف العامة وتابع عدد الموجودين فيها.',
      icon: Icons.home_rounded,
    ),
    _SectionInfo(
      title: 'الرسائل',
      subtitle: 'افتح محادثاتك وأرسل الرسائل النصية عبر الخادم الحالي.',
      icon: Icons.chat_bubble_rounded,
    ),
    _SectionInfo(
      title: 'أنا',
      subtitle: 'راجع بيانات حسابك وعدّل الاسم الظاهر والنبذة.',
      icon: Icons.person_rounded,
    ),
    _SectionInfo(
      title: 'الألعاب',
      subtitle: 'ألعاب JEHO',
      icon: Icons.sports_esports_rounded,
    ),
    _SectionInfo(
      title: 'الدراما',
      subtitle: 'المسلسلات والحلقات',
      icon: Icons.movie_creation_rounded,
    ),
  ];

  @override
  Widget build(BuildContext context) {
    final user = widget.session['user'] is Map<String, dynamic>
        ? widget.session['user'] as Map<String, dynamic>
        : <String, dynamic>{};
    final activeUser = _profile ?? user;
    final displayName = (activeUser['displayName'] ??
            activeUser['username'] ??
            'مستخدم JEHO')
        .toString();

    final backgrounds = _theme['backgrounds'] is Map<String, dynamic>
        ? _theme['backgrounds'] as Map<String, dynamic>
        : <String, dynamic>{};
    final sectionKey = _selectedIndex == 0 ? 'home' : _selectedIndex == 1 ? 'chat' : _selectedIndex == 2 ? 'profile' : _selectedIndex == 3 ? 'games' : 'drama';
    final wallpaper = resolveSectionWallpaper(backgrounds, sectionKey);

    return Scaffold(
      appBar: AppBar(
        title: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Image.asset(
              'assets/original/android-res/drawable-xxhdpi/jeho_logo.webp',
              width: 30,
              height: 30,
              fit: BoxFit.contain,
              errorBuilder: (_, __, ___) => const Icon(Icons.forum_rounded, size: 26),
            ),
            const SizedBox(width: 8),
            const Text(
              'JEHO-OWN',
              style: TextStyle(fontWeight: FontWeight.w800),
            ),
          ],
        ),
        actions: [
          IconButton(
            tooltip: 'البحث عن مستخدمين',
            onPressed: () async {
              await Navigator.of(context).push(
                MaterialPageRoute<void>(
                  builder: (_) => UserSearchPage(
                    apiClient: widget.apiClient,
                    accessToken: widget.session['accessToken']?.toString() ?? '',
                  ),
                ),
              );
            },
            icon: const Icon(Icons.search_rounded),
          ),
          IconButton(
            tooltip: 'تحديث البيانات',
            onPressed: _loading ? null : _loadHomeData,
            icon: const Icon(Icons.refresh_rounded),
          ),
          IconButton(
            tooltip: 'تسجيل الخروج',
            onPressed: widget.onLogout,
            icon: const Icon(Icons.logout_rounded),
          ),
        ],
      ),
      body: Stack(
        fit: StackFit.expand,
        children: [
          if (wallpaper.isNotEmpty)
            Positioned.fill(
              child: Image.network(
                _resolveMediaUrl(wallpaper),
                fit: BoxFit.cover,
                errorBuilder: (_, __, ___) => _selectedIndex == 0
                    ? Image.asset(
                        'assets/original/android-res/drawable-xxhdpi/home_page_bg.webp',
                        fit: BoxFit.cover,
                        errorBuilder: (_, __, ___) => const SizedBox.shrink(),
                      )
                    : _selectedIndex == 2
                        ? Image.asset(
                            'assets/original/android-res/drawable-xxhdpi/me_bg.webp',
                            fit: BoxFit.cover,
                            errorBuilder: (_, __, ___) => const SizedBox.shrink(),
                          )
                        : const SizedBox.shrink(),
              ),
            )
          else if (_selectedIndex == 0)
            Positioned.fill(
              child: Image.asset(
                'assets/original/android-res/drawable-xxhdpi/home_page_bg.webp',
                fit: BoxFit.cover,
                errorBuilder: (_, __, ___) => const SizedBox.shrink(),
              ),
            )
          else if (_selectedIndex == 2)
            Positioned.fill(
              child: Opacity(
                opacity: 0.24,
                child: Image.asset(
                  'assets/original/android-res/drawable-xxhdpi/me_bg.webp',
                  fit: BoxFit.cover,
                  errorBuilder: (_, __, ___) => const SizedBox.shrink(),
                ),
              ),
            ),
          SafeArea(
        child: _selectedIndex == 3
            ? GamesPage(
                apiClient: widget.apiClient,
                accessToken: widget.session['accessToken']?.toString() ?? '',
              )
            : _selectedIndex == 4
                ? DramaPage(
                    apiClient: widget.apiClient,
                    accessToken: widget.session['accessToken']?.toString() ?? '',
                  )
                : Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 480),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  if (_selectedIndex != 2) ...[
                    if (_selectedIndex == 0) ...[
                      Align(
                        alignment: Alignment.centerRight,
                        child: Text('اكتشف غرف JEHOO', style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w900, color: const Color(0xFF493512))),
                      ),
                      const SizedBox(height: 4),
                      Align(
                        alignment: Alignment.centerRight,
                        child: Text('ادخل إلى المجتمعات وتواصل مع أصدقائك', style: Theme.of(context).textTheme.bodyMedium?.copyWith(color: const Color(0xFF88775D))),
                      ),
                      const SizedBox(height: 10),
                      Row(
                        children: [
                          _HomeHeaderIcon(
                            asset: 'assets/original/android-res/drawable-xxhdpi/ic_tab_party.webp',
                            fallback: Icons.add_home_work_rounded,
                            onTap: _loading ? () {} : _createRoom,
                          ),
                          const SizedBox(width: 8),
                          _HomeHeaderIcon(
                            asset: 'assets/original/android-res/drawable-xxhdpi/ic_search_home.png',
                            fallback: Icons.search_rounded,
                            onTap: () => Navigator.of(context).push(
                              MaterialPageRoute<void>(
                                builder: (_) => UserSearchPage(
                                  apiClient: widget.apiClient,
                                  accessToken: widget.session['accessToken']?.toString() ?? '',
                                ),
                              ),
                            ),
                          ),
                          const Spacer(),
                          InkWell(
                            onTap: () => setState(() => _homeTab = 'حفلة'),
                            child: Text('حفلة', style: TextStyle(fontSize: 25, fontWeight: FontWeight.w900, color: _homeTab == 'حفلة' ? const Color(0xFF18795D) : const Color(0xFF999999))),
                          ),
                          const SizedBox(width: 12),
                          InkWell(
                            onTap: () => setState(() => _homeTab = 'ملكي'),
                            child: Text('ملكي', style: TextStyle(fontSize: 20, fontWeight: FontWeight.w800, color: _homeTab == 'ملكي' ? const Color(0xFF18795D) : const Color(0xFF777777))),
                          ),
                          const SizedBox(width: 12),
                          InkWell(
                            onTap: () => Navigator.of(context).push(
                              MaterialPageRoute<void>(
                                builder: (_) => DiscoveryHubPage(
                                  apiClient: widget.apiClient,
                                  accessToken: widget.session['accessToken']?.toString() ?? '',
                                ),
                              ),
                            ),
                            child: const Text('الفعاليات', style: TextStyle(fontSize: 20, fontWeight: FontWeight.w800, color: Color(0xFF999999))),
                          ),
                        ],
                      ),
                    ] else ...[
                      Align(
                        alignment: Alignment.centerRight,
                        child: Text('محادثاتك', style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w900, color: const Color(0xFF493512))),
                      ),
                      const SizedBox(height: 4),
                      Align(
                        alignment: Alignment.centerRight,
                        child: Text('رسائلك وأصدقاؤك في مكان واحد', style: Theme.of(context).textTheme.bodyMedium?.copyWith(color: const Color(0xFF88775D))),
                      ),
                    ],
                    const SizedBox(height: 12),
                  ],
                  if (_selectedIndex == 0) ...[
                    if (_banners.isNotEmpty) ...[
                      const SizedBox(height: 4),
                      SizedBox(
                        height: 190,
                        child: ListView.separated(
                          scrollDirection: Axis.horizontal,
                          itemCount: _banners.length,
                          separatorBuilder: (_, __) => const SizedBox(width: 10),
                          itemBuilder: (context, index) {
                            final banner = _banners[index];
                            final imageValue = (banner['imageUrl'] ?? banner['image'] ?? banner['mediaUrl'] ?? banner['url'] ?? '').toString().trim();
                            final title = (banner['title'] ?? banner['name'] ?? '').toString();
                            final imageUrl = imageValue.isEmpty ? '' : _resolveMediaUrl(imageValue);
                            return SizedBox(
                              width: MediaQuery.sizeOf(context).width * 0.94,
                              child: ClipRRect(
                                borderRadius: BorderRadius.circular(18),
                                child: Stack(
                                  fit: StackFit.expand,
                                  children: [
                                    Container(
                                      color: const Color(0xFFFFE7B0),
                                      alignment: Alignment.center,
                                      child: const Icon(Icons.campaign_rounded, size: 42, color: Color(0xFF8C641D)),
                                    ),
                                    if (imageUrl.isNotEmpty)
                                      Image.network(
                                        imageUrl,
                                        fit: BoxFit.cover,
                                        errorBuilder: (_, __, ___) => const SizedBox.shrink(),
                                      ),
                                    if (title.trim().isNotEmpty)
                                      Positioned(
                                        right: 0,
                                        left: 0,
                                        bottom: 0,
                                        child: Container(
                                          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
                                          color: const Color.fromARGB(133, 0, 0, 0),
                                          child: Text(
                                            title,
                                            maxLines: 2,
                                            overflow: TextOverflow.ellipsis,
                                            style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w700),
                                          ),
                                        ),
                                      ),
                                  ],
                                ),
                              ),
                            );
                          },
                        ),
                      ),
                    ],
                    const SizedBox(height: 12),
                    Row(
                      children: const [
                        Expanded(child: _HomeFeatureTile(title: 'الثروة', asset: 'assets/original/android-res/drawable-xxhdpi/home_bg_wealth.webp')),
                        SizedBox(width: 8),
                        Expanded(child: _HomeFeatureTile(title: 'CP', asset: 'assets/original/android-res/drawable-xxhdpi/home_bg_cp.webp')),
                        SizedBox(width: 8),
                        Expanded(child: _HomeFeatureTile(title: 'حظ', asset: 'assets/original/android-res/drawable-xxhdpi/home_bg_charm.webp')),
                      ],
                    ),
                    const SizedBox(height: 12),
                    SizedBox(
                      height: 78,
                      child: ListView(
                        scrollDirection: Axis.horizontal,
                        children: [
                          _HomeCategoryChip(label: 'شائع 🔥', selected: _homeRoomFilter == 'شائع', onTap: () => setState(() => _homeRoomFilter = 'شائع')),
                          _HomeCategoryChip(label: 'سوريا 🇸🇾', selected: _homeRoomFilter == 'سوريا', onTap: () => setState(() => _homeRoomFilter = 'سوريا')),
                          _HomeCategoryChip(label: 'مصر 🇪🇬', selected: _homeRoomFilter == 'مصر', onTap: () => setState(() => _homeRoomFilter = 'مصر')),
                          _HomeCategoryChip(label: 'المملكة العربية 🇸🇦', selected: _homeRoomFilter == 'السعودية', onTap: () => setState(() => _homeRoomFilter = 'السعودية')),
                        ],
                      ),
                    ),
                    const SizedBox(height: 6),
                    Row(
                      children: [
                        Expanded(
                          child: Text(
                            'الغرف العامة',
                            style: Theme.of(context).textTheme.titleLarge?.copyWith(
                                  fontWeight: FontWeight.w800,
                                ),
                          ),
                        ),
                        Text(
                          '${_visibleRooms.length} غرفة',
                          style: Theme.of(context).textTheme.bodySmall,
                        ),
                      ],
                    ),
                    const SizedBox(height: 10),
                    Align(
                      alignment: Alignment.centerLeft,
                      child: TextButton.icon(
                        onPressed: _loading ? null : _createRoom,
                        icon: const Icon(Icons.add_circle_outline_rounded, size: 18),
                        label: const Text('إنشاء غرفة'),
                      ),
                    ),
                    if (_loading)
                      const Padding(
                        padding: EdgeInsets.all(24),
                        child: Center(child: CircularProgressIndicator()),
                      )
                    else if (_loadError != null)
                      Card(
                        child: Padding(
                          padding: const EdgeInsets.all(16),
                          child: Column(
                            children: [
                              Text(_loadError!, textAlign: TextAlign.center),
                              const SizedBox(height: 8),
                              OutlinedButton.icon(
                                onPressed: _loadHomeData,
                                icon: const Icon(Icons.refresh_rounded),
                                label: const Text('إعادة المحاولة'),
                              ),
                            ],
                          ),
                        ),
                      )
                    else if (_visibleRooms.isEmpty)
                      const Card(
                        child: Padding(
                          padding: EdgeInsets.all(18),
                          child: Text(
                            'لا توجد غرف عامة متاحة حاليًا.',
                            textAlign: TextAlign.center,
                          ),
                        ),
                      )
                    else
                      Wrap(
                        spacing: 8,
                        runSpacing: 10,
                        children: _visibleRooms.map((room) {
                        final title = (room['title'] ??
                                room['name'] ??
                                room['roomName'] ??
                                'غرفة JEHO')
                            .toString();
                        final host = room['host'] is Map<String, dynamic>
                            ? room['host'] as Map<String, dynamic>
                            : <String, dynamic>{};
                        final hostName = (host['displayName'] ??
                                host['username'] ??
                                room['hostName'] ??
                                'مضيف')
                            .toString();
                        final viewers = room['viewerCount'] ??
                            room['onlineCount'] ??
                            room['memberCount'] ??
                            0;
                        final seats = room['seatCount'] ?? room['maxSeats'] ?? 11;
                        final coverValue = (room['coverUrl'] ??
                                room['coverImage'] ??
                                room['imageUrl'] ??
                                room['backgroundUrl'] ??
                                '')
                            .toString()
                            .trim();
                        final coverUrl = coverValue.isEmpty
                            ? ''
                            : _resolveMediaUrl(coverValue);
                        final locked = room['isLocked'] == true ||
                            room['hasPassword'] == true ||
                            room['passwordProtected'] == true;
                        final description =
                            (room['description'] ?? room['notice'] ?? '')
                                .toString()
                                .trim();
                        return SizedBox(
                          width: (MediaQuery.sizeOf(context).width - 42) / 2,
                          child: Card(
                          clipBehavior: Clip.antiAlias,
                          margin: EdgeInsets.zero,
                          elevation: 2,
                          shadowColor: const Color(0x1A493512),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(20),
                            side: const BorderSide(color: Color(0x1AE0B85E)),
                          ),
                          child: InkWell(
                            onTap: () {
                              Navigator.of(context).push(
                                MaterialPageRoute<void>(
                                  builder: (_) => RoomDetailPage(
                                    apiClient: widget.apiClient,
                                    accessToken: widget.session['accessToken']
                                            ?.toString() ??
                                        '',
                                    room: room,
                                  ),
                                ),
                              ).then((_) => _loadHomeData());
                            },
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.stretch,
                              children: [
                                SizedBox(
                                  height: 190,
                                  child: Stack(
                                    fit: StackFit.expand,
                                    children: [
                                      if (coverUrl.isNotEmpty)
                                        Image.network(
                                          coverUrl,
                                          fit: BoxFit.cover,
                                          errorBuilder: (_, __, ___) =>
                                              Image.asset(
                                            'assets/original/android-res/drawable-xxhdpi/img_bg_room.webp',
                                            fit: BoxFit.cover,
                                            errorBuilder: (_, __, ___) =>
                                                const ColoredBox(
                                              color: Color(0xFFE9D4A7),
                                            ),
                                          ),
                                        )
                                      else
                                        Image.asset(
                                          'assets/original/android-res/drawable-xxhdpi/img_bg_room.webp',
                                          fit: BoxFit.cover,
                                          errorBuilder: (_, __, ___) =>
                                              const ColoredBox(
                                            color: Color(0xFFE9D4A7),
                                          ),
                                        ),
                                      const DecoratedBox(
                                        decoration: BoxDecoration(
                                          gradient: LinearGradient(
                                            begin: Alignment.topCenter,
                                            end: Alignment.bottomCenter,
                                            colors: [
                                              Color(0x00000000),
                                              Color(0xCC1A1713),
                                            ],
                                          ),
                                        ),
                                      ),
                                      Positioned(
                                        top: 10,
                                        right: 10,
                                        child: Container(
                                          padding: const EdgeInsets.symmetric(
                                            horizontal: 10,
                                            vertical: 6,
                                          ),
                                          decoration: BoxDecoration(
                                            color: locked
                                                ? const Color(0xE6FFF0D2)
                                                : const Color(0xE61E684A),
                                            borderRadius:
                                                BorderRadius.circular(30),
                                          ),
                                          child: Row(
                                            mainAxisSize: MainAxisSize.min,
                                            children: [
                                              Icon(
                                                locked
                                                    ? Icons.lock_rounded
                                                    : Icons.public_rounded,
                                                size: 14,
                                                color: locked
                                                    ? const Color(0xFF79551B)
                                                    : Colors.white,
                                              ),
                                              const SizedBox(width: 5),
                                              Text(
                                                locked ? 'خاصة' : 'عامة',
                                                style: TextStyle(
                                                  color: locked
                                                      ? const Color(0xFF79551B)
                                                      : Colors.white,
                                                  fontSize: 11,
                                                  fontWeight: FontWeight.w800,
                                                ),
                                              ),
                                            ],
                                          ),
                                        ),
                                      ),
                                      Positioned(
                                        bottom: 12,
                                        right: 14,
                                        left: 14,
                                        child: Row(
                                          crossAxisAlignment:
                                              CrossAxisAlignment.end,
                                          children: [
                                            Expanded(
                                              child: Text(
                                                title,
                                                maxLines: 2,
                                                overflow: TextOverflow.ellipsis,
                                                style: const TextStyle(
                                                  color: Colors.white,
                                                  fontSize: 15,
                                                  fontWeight: FontWeight.w900,
                                                  shadows: [
                                                    Shadow(
                                                      color: Color(0x88000000),
                                                      blurRadius: 8,
                                                    ),
                                                  ],
                                                ),
                                              ),
                                            ),
                                            const SizedBox(width: 8),
                                            Container(
                                              padding:
                                                  const EdgeInsets.symmetric(
                                                horizontal: 10,
                                                vertical: 7,
                                              ),
                                              decoration: BoxDecoration(
                                                color: const Color(0xD91B1712),
                                                borderRadius:
                                                    BorderRadius.circular(24),
                                              ),
                                              child: Row(
                                                mainAxisSize: MainAxisSize.min,
                                                children: [
                                                  const Icon(
                                                    Icons.people_alt_rounded,
                                                    color: Colors.white,
                                                    size: 16,
                                                  ),
                                                  const SizedBox(width: 5),
                                                  Text(
                                                    '$viewers',
                                                    style: const TextStyle(
                                                      color: Colors.white,
                                                      fontWeight:
                                                          FontWeight.w800,
                                                    ),
                                                  ),
                                                ],
                                              ),
                                            ),
                                          ],
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                                Padding(
                                  padding: const EdgeInsets.fromLTRB(9, 9, 9, 10),
                                  child: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.stretch,
                                    children: [
                                      Row(
                                        children: [
                                          const Icon(
                                            Icons.workspace_premium_rounded,
                                            size: 18,
                                            color: Color(0xFFB68A35),
                                          ),
                                          const SizedBox(width: 6),
                                          Expanded(
                                            child: Text(
                                              'المضيف: $hostName',
                                              maxLines: 1,
                                              overflow: TextOverflow.ellipsis,
                                              style: const TextStyle(
                                                fontWeight: FontWeight.w700,
                                                color: Color(0xFF493512),
                                              ),
                                            ),
                                          ),
                                          const Icon(
                                            Icons.chair_alt_rounded,
                                            size: 17,
                                            color: Color(0xFF8C641D),
                                          ),
                                          const SizedBox(width: 4),
                                          Text(
                                            '$seats مقعد',
                                            style: const TextStyle(
                                              color: Color(0xFF6E6250),
                                              fontSize: 12,
                                              fontWeight: FontWeight.w700,
                                            ),
                                          ),
                                        ],
                                      ),
                                      if (description.isNotEmpty) ...[
                                        const SizedBox(height: 7),
                                        Text(
                                          description,
                                          maxLines: 2,
                                          overflow: TextOverflow.ellipsis,
                                          style: const TextStyle(
                                            color: Color(0xFF756B5B),
                                            height: 1.35,
                                            fontSize: 12,
                                          ),
                                        ),
                                      ],
                                      const SizedBox(height: 11),
                                      Row(
                                        children: [
                                          const Icon(
                                            Icons.forum_rounded,
                                            size: 16,
                                            color: Color(0xFF8C641D),
                                          ),
                                          const SizedBox(width: 5),
                                          const Expanded(
                                            child: Text(
                                              'اضغط للدخول إلى الغرفة',
                                              style: TextStyle(
                                                color: Color(0xFF8C641D),
                                                fontSize: 12,
                                                fontWeight: FontWeight.w700,
                                              ),
                                            ),
                                          ),
                                          Icon(
                                            Icons.arrow_back_ios_new_rounded,
                                            size: 15,
                                            color: Color(0xFF8C641D),
                                          ),
                                        ],
                                      ),
                                    ],
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                        );
                      }).toList(),
                      ),
                  ] else if (_selectedIndex == 1) ...[
                    const SizedBox(height: 20),
                    Row(
                      children: [
                        Expanded(
                          child: Text(
                            'محادثاتي',
                            style: Theme.of(context).textTheme.titleLarge?.copyWith(
                                  fontWeight: FontWeight.w800,
                                ),
                          ),
                        ),
                        Text('${_conversations.length}'),
                      ],
                    ),
                    const SizedBox(height: 10),
                    if (_loading)
                      const Padding(
                        padding: EdgeInsets.all(24),
                        child: Center(child: CircularProgressIndicator()),
                      )
                    else if (_conversations.isEmpty)
                      const Card(
                        child: Padding(
                          padding: EdgeInsets.all(18),
                          child: Text(
                            'لا توجد محادثات لعرضها، أو تعذر تحميلها. يمكنك تحديث البيانات والمحاولة مجددًا.',
                            textAlign: TextAlign.center,
                          ),
                        ),
                      )
                    else
                      ..._conversations.map((conversation) {
                        final peer = conversation['peer'] is Map<String, dynamic>
                            ? conversation['peer'] as Map<String, dynamic>
                            : <String, dynamic>{};
                        final last = conversation['lastMessage'] is Map<String, dynamic>
                            ? conversation['lastMessage'] as Map<String, dynamic>
                            : <String, dynamic>{};
                        final title = (conversation['title'] ??
                                peer['displayName'] ??
                                peer['username'] ??
                                'محادثة')
                            .toString();
                        final preview = (last['content'] ??
                                (last['type'] == 'IMAGE' ? '📷 صورة' : 'ابدأ المحادثة'))
                            .toString();
                        final unread = conversation['unreadCount'] ?? 0;
                        return Card(
                          margin: const EdgeInsets.only(bottom: 10),
                          clipBehavior: Clip.antiAlias,
                          child: ListTile(
                            contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 7),
                            onTap: () {
                              final token = widget.session['accessToken']?.toString() ?? '';
                              final conversationId = conversation['id']?.toString() ?? '';
                              if (token.isEmpty || conversationId.isEmpty) {
                                ScaffoldMessenger.of(context).showSnackBar(
                                  const SnackBar(content: Text('بيانات المحادثة غير مكتملة.')),
                                );
                                return;
                              }
                              Navigator.of(context).push(
                                MaterialPageRoute<void>(
                                  builder: (_) => ConversationPage(
                                    apiClient: widget.apiClient,
                                    accessToken: token,
                                    currentUserId: (_profile?['id'] ??
                                      (widget.session['user'] is Map<String, dynamic>
                                        ? (widget.session['user'] as Map<String, dynamic>)['id']
                                        : null))?.toString() ?? '',
                                    conversation: conversation,
                                  ),
                                ),
                              ).then((_) => _loadHomeData());
                            },
                            leading: Container(
                              padding: const EdgeInsets.all(2),
                              decoration: const BoxDecoration(
                                shape: BoxShape.circle,
                                gradient: LinearGradient(colors: [Color(0xFFE5B45D), Color(0xFFFFEFD0)]),
                              ),
                              child: CircleAvatar(
                                radius: 27,
                                backgroundColor: const Color(0xFFFFF3DA),
                                backgroundImage: ((peer['avatarUrl'] ?? peer['avatar'] ?? conversation['avatarUrl'] ?? '').toString().trim().isNotEmpty)
                                    ? NetworkImage(_resolveMediaUrl((peer['avatarUrl'] ?? peer['avatar'] ?? conversation['avatarUrl']).toString()))
                                    : null,
                                child: ((peer['avatarUrl'] ?? peer['avatar'] ?? conversation['avatarUrl'] ?? '').toString().trim().isEmpty)
                                    ? Text(title.isEmpty ? '؟' : title.characters.first, style: const TextStyle(color: Color(0xFF8C641D), fontWeight: FontWeight.w900, fontSize: 19))
                                    : null,
                              ),
                            ),
                            title: Text(
                              title,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(fontWeight: FontWeight.w900, color: Color(0xFF493512), fontSize: 15),
                            ),
                            subtitle: Text(
                              preview.isEmpty ? 'لا توجد رسالة أخيرة' : preview,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                            trailing: unread is num && unread > 0
                                ? CircleAvatar(
                                    radius: 12,
                                    backgroundColor: _brandColor,
                                    child: Text(
                                      '$unread',
                                      style: const TextStyle(
                                        fontSize: 11,
                                        color: Colors.white,
                                      ),
                                    ),
                                  )
                                : null,
                          ),
                        );
                      }),
                    const SizedBox(height: 8),
                    const Text(
                      'يمكنك فتح المحادثة وإرسال النصوص والصور والملفات. تستمر إضافة بقية أدوات الرسائل تدريجيًا.',
                      textAlign: TextAlign.center,
                    ),
                  ] else ...[
                    // Original JEHOO-style profile dashboard: keep every shortcut wired
                    // to its existing functional page while using the app's original assets.
                    Container(
                      width: double.infinity,
                      decoration: BoxDecoration(
                        gradient: const LinearGradient(
                          begin: Alignment.topRight,
                          end: Alignment.bottomLeft,
                          colors: [Color(0xFFFFD8A8), Color(0xFFFFF0D7), Color(0xFFFFF9F0)],
                        ),
                        borderRadius: BorderRadius.circular(26),
                        border: Border.all(color: const Color(0xFFE8C58D)),
                        boxShadow: const [BoxShadow(color: Color(0x18000000), blurRadius: 14, offset: Offset(0, 5))],
                      ),
                      child: Padding(
                        padding: const EdgeInsets.all(18),
                        child: Column(
                          children: [
                            Row(
                              children: [
                                Container(
                                  padding: const EdgeInsets.all(3),
                                  decoration: BoxDecoration(
                                    shape: BoxShape.circle,
                                    gradient: const LinearGradient(colors: [Color(0xFFE3AD52), Color(0xFFFFEAC1)]),
                                    boxShadow: const [BoxShadow(color: Color(0x338C641D), blurRadius: 8)],
                                  ),
                                  child: CircleAvatar(
                                    radius: 39,
                                    backgroundColor: const Color(0xFFFFF7E8),
                                    backgroundImage: (activeUser['avatarUrl'] ?? '').toString().trim().isNotEmpty
                                        ? NetworkImage(_resolveMediaUrl(activeUser['avatarUrl'].toString()))
                                        : null,
                                    child: (activeUser['avatarUrl'] ?? '').toString().trim().isEmpty
                                        ? const Icon(Icons.person_rounded, size: 42, color: Color(0xFF8C641D))
                                        : null,
                                  ),
                                ),
                                const SizedBox(width: 14),
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      Text(displayName, maxLines: 1, overflow: TextOverflow.ellipsis,
                                        style: const TextStyle(fontSize: 21, fontWeight: FontWeight.w900, color: Color(0xFF493512))),
                                      const SizedBox(height: 4),
                                      Text('@${activeUser['username'] ?? '—'}', maxLines: 1, overflow: TextOverflow.ellipsis,
                                        style: const TextStyle(fontSize: 13, color: Color(0xFF786342))),
                                      const SizedBox(height: 6),
                                      Container(
                                        padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 4),
                                        decoration: BoxDecoration(color: const Color(0xFFFFF8E9), borderRadius: BorderRadius.circular(20)),
                                        child: Text('ID ${activeUser['publicId'] ?? activeUser['vanityId'] ?? '—'}',
                                          style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w800, color: Color(0xFF986B21))),
                                      ),
                                    ],
                                  ),
                                ),
                                IconButton.filledTonal(
                                  tooltip: 'تعديل الملف الشخصي',
                                  onPressed: _loading ? null : () => _editProfile(activeUser),
                                  style: IconButton.styleFrom(backgroundColor: const Color(0xFFFFF8E9), foregroundColor: const Color(0xFF8C641D)),
                                  icon: const Icon(Icons.edit_rounded),
                                ),
                              ],
                            ),
                            if ((activeUser['bio'] ?? '').toString().trim().isNotEmpty) ...[
                              const SizedBox(height: 12),
                              Align(
                                alignment: Alignment.centerRight,
                                child: Text(activeUser['bio'].toString(), maxLines: 2, overflow: TextOverflow.ellipsis,
                                  style: const TextStyle(color: Color(0xFF786342), height: 1.35)),
                              ),
                            ],
                            const SizedBox(height: 16),
                            Row(
                              children: [
                                Expanded(child: _profileMetric(
                                  icon: Icons.diamond_rounded,
                                  title: 'الألماس',
                                  value: (activeUser['diamonds'] ?? activeUser['diamondBalance'] ?? activeUser['wallet']?['diamonds'] ?? '—').toString(),
                                  tint: const Color(0xFF6C76D9),
                                )),
                                const SizedBox(width: 8),
                                Expanded(child: _profileMetric(
                                  icon: Icons.monetization_on_rounded,
                                  title: 'الذهب',
                                  value: (activeUser['gold'] ?? activeUser['goldBalance'] ?? activeUser['wallet']?['gold'] ?? '—').toString(),
                                  tint: const Color(0xFFB98324),
                                )),
                                const SizedBox(width: 8),
                                Expanded(child: _profileMetric(
                                  icon: Icons.workspace_premium_rounded,
                                  title: 'VIP',
                                  value: (activeUser['vipLevel'] ?? activeUser['vip']?['level'] ?? 'VIP').toString(),
                                  tint: const Color(0xFF9B5E9F),
                                )),
                              ],
                            ),
                            const SizedBox(height: 12),
                            Row(
                              children: [
                                Expanded(
                                  child: FilledButton.icon(
                                    onPressed: () => Navigator.of(context).push(MaterialPageRoute<void>(
                                      builder: (_) => WalletPage(apiClient: widget.apiClient, accessToken: widget.session['accessToken']?.toString() ?? ''),
                                    )),
                                    icon: const Icon(Icons.account_balance_wallet_rounded, size: 18),
                                    label: const Text('المحفظة'),
                                    style: FilledButton.styleFrom(backgroundColor: const Color(0xFF8C641D), foregroundColor: Colors.white),
                                  ),
                                ),
                                const SizedBox(width: 8),
                                Expanded(
                                  child: OutlinedButton.icon(
                                    onPressed: _loading ? null : _changeAvatar,
                                    icon: const Icon(Icons.add_a_photo_rounded, size: 18),
                                    label: const Text('الصورة'),
                                    style: OutlinedButton.styleFrom(foregroundColor: const Color(0xFF8C641D), side: const BorderSide(color: Color(0xFFD4AD70))),
                                  ),
                                ),
                              ],
                            ),
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(height: 20),
                    Align(
                      alignment: Alignment.centerRight,
                      child: Text('خدمات حسابك', style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w900, color: const Color(0xFF493512))),
                    ),
                    const SizedBox(height: 10),
                    _profileActionGroup(
                      title: 'الحساب والأمان',
                      children: [
                        _profileAction(icon: Icons.person_outline_rounded, title: 'تعديل الملف الشخصي', onTap: _loading ? null : () => _editProfile(activeUser)),
                        _profileAction(icon: Icons.settings_rounded, title: 'الإعدادات', onTap: () => Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => SettingsPage(apiClient: widget.apiClient, accessToken: widget.session['accessToken']?.toString() ?? '')))),
                        _profileAction(icon: Icons.visibility_outlined, title: 'الزوار', onTap: () => Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => ProfilePeoplePage(apiClient: widget.apiClient, accessToken: widget.session['accessToken']?.toString() ?? '', visitors: true)))),
                        _profileAction(icon: Icons.people_alt_outlined, title: 'أصدقائي', onTap: () => Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => ProfilePeoplePage(apiClient: widget.apiClient, accessToken: widget.session['accessToken']?.toString() ?? '', visitors: false)))),
                        _profileAction(icon: Icons.person_search_rounded, title: 'البحث عن أشخاص', onTap: () => Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => SocialPage(apiClient: widget.apiClient, accessToken: widget.session['accessToken']?.toString() ?? '')))),
                      ],
                    ),
                    const SizedBox(height: 12),
                    _profileActionGroup(
                      title: 'المحفظة والمظهر',
                      children: [
                        _profileAction(icon: Icons.card_giftcard_rounded, title: 'الهدايا', onTap: () => Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => GiftCatalogPage(apiClient: widget.apiClient, accessToken: widget.session['accessToken']?.toString() ?? '')))),
                        _profileAction(icon: Icons.history_rounded, title: 'سجل الهدايا', onTap: () => Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => GiftHistoryPage(apiClient: widget.apiClient, accessToken: widget.session['accessToken']?.toString() ?? '')))),
                        _profileAction(icon: Icons.workspace_premium_rounded, title: 'عضوية VIP', onTap: () => Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => VipCatalogPage(apiClient: widget.apiClient, accessToken: widget.session['accessToken']?.toString() ?? '')))),
                        _profileAction(icon: Icons.auto_awesome_rounded, title: 'الإطارات والزينة', onTap: () => Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => CosmeticsPage(apiClient: widget.apiClient, accessToken: widget.session['accessToken']?.toString() ?? '')))),
                        _profileAction(icon: Icons.tag_rounded, title: 'الآي دي المميز', onTap: () => Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => VanityIdsPage(apiClient: widget.apiClient, accessToken: widget.session['accessToken']?.toString() ?? '')))),
                      ],
                    ),
                    const SizedBox(height: 12),
                    _profileActionGroup(
                      title: 'المجتمع والفعاليات',
                      children: [
                        _profileAction(icon: Icons.favorite_rounded, title: 'CP والعلاقات', onTap: () => Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => SocialHubPage(apiClient: widget.apiClient, accessToken: widget.session['accessToken']?.toString() ?? '')))),
                        _profileAction(icon: Icons.groups_rounded, title: 'المهام والوكالات', onTap: () => Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => FeatureHubPage(apiClient: widget.apiClient, accessToken: widget.session['accessToken']?.toString() ?? '')))),
                        _profileAction(icon: Icons.notifications_active_outlined, title: 'الإشعارات والترتيب والفعاليات', onTap: () => Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => DiscoveryHubPage(apiClient: widget.apiClient, accessToken: widget.session['accessToken']?.toString() ?? '')))),
                        _profileAction(icon: Icons.movie_creation_outlined, title: 'الدراما والمسلسلات', onTap: () => Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => DramaPage(apiClient: widget.apiClient, accessToken: widget.session['accessToken']?.toString() ?? '')))),
                        _profileAction(icon: Icons.sports_esports_rounded, title: 'الألعاب', onTap: () => Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => GamesPage(apiClient: widget.apiClient, accessToken: widget.session['accessToken']?.toString() ?? '')))),
                      ],
                    ),
                    const SizedBox(height: 12),
                    Card(
                      elevation: 0,
                      color: const Color(0xFFFFF7E9),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18), side: const BorderSide(color: Color(0xFFEED8B2))),
                      child: ListTile(
                        leading: const Icon(Icons.logout_rounded, color: Color(0xFF9A4B3B)),
                        title: const Text('تسجيل الخروج', style: TextStyle(fontWeight: FontWeight.w800)),
                        subtitle: const Text('إنهاء الجلسة على هذا الجهاز'),
                        onTap: widget.onLogout,
                      ),
                    ),
                  ],
                  const SizedBox(height: 24),
                ],
              ),
            ),
          ),
        ),
          ),
        ],
      ),
      bottomNavigationBar: SafeArea(
        top: false,
        child: Container(
          padding: const EdgeInsets.fromLTRB(5, 7, 5, 5),
          decoration: const BoxDecoration(
            color: Color(0xFFFFFEFB),
            border: Border(top: BorderSide(color: Color(0xFFEDE0CC))),
            boxShadow: [BoxShadow(color: Color(0x10000000), blurRadius: 12, offset: Offset(0, -3))],
          ),
          child: Row(
            children: [
              Expanded(child: _originalNavItem(label: 'الرئيسية', selected: _selectedIndex == 0, normalAsset: 'assets/original/icons/ic_home_tab_party_normal.svg', selectedAsset: 'assets/original/icons/ic_home_tab_party_selected.svg', onTap: () => setState(() => _selectedIndex = 0))),
              Expanded(child: _originalNavItem(label: 'الدراما', selected: _selectedIndex == 4, normalAsset: 'assets/original/icons/ic_home_tab_drama_normal.svg', selectedAsset: 'assets/original/icons/ic_home_tab_drama_selected.svg', onTap: () => setState(() => _selectedIndex = 4))),
              Expanded(child: _originalNavItem(label: 'الألعاب', selected: _selectedIndex == 3, normalAsset: 'assets/original/icons/ic_home_tab_game_normal.svg', selectedAsset: 'assets/original/icons/ic_home_tab_game_selected.svg', onTap: () => setState(() => _selectedIndex = 3))),
              Expanded(
                child: InkWell(
                  borderRadius: BorderRadius.circular(22),
                  onTap: _loading ? null : _createRoom,
                  child: Padding(
                    padding: const EdgeInsets.symmetric(vertical: 1),
                    child: Column(mainAxisSize: MainAxisSize.min, children: [
                      Container(
                        width: 42, height: 42, padding: const EdgeInsets.all(4),
                        decoration: const BoxDecoration(shape: BoxShape.circle, gradient: LinearGradient(colors: [Color(0xFFFFD98E), Color(0xFFE4AD4C)]), boxShadow: [BoxShadow(color: Color(0x2A8C641D), blurRadius: 6, offset: Offset(0, 2))]),
                        child: Image.asset('assets/original/android-res/drawable-xxhdpi/ic_tab_create_room.webp', fit: BoxFit.contain,
                          errorBuilder: (_, __, ___) => const Icon(Icons.add_rounded, color: Color(0xFF493512), size: 28)),
                      ),
                      const SizedBox(height: 2),
                      const Text('إنشاء غرفة', maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(fontSize: 10, fontWeight: FontWeight.w800, color: Color(0xFF8C641D))),
                    ]),
                  ),
                ),
              ),
              Expanded(child: _originalNavItem(label: 'الرسائل', selected: _selectedIndex == 1, normalAsset: 'assets/original/icons/ic_home_tab_chat_normal.svg', selectedAsset: 'assets/original/icons/ic_home_tab_chat_selected.svg', onTap: () => setState(() => _selectedIndex = 1))),
              Expanded(child: _originalNavItem(label: 'أنا', selected: _selectedIndex == 2, normalAsset: 'assets/original/icons/ic_home_tab_me_normal.svg', selectedAsset: 'assets/original/icons/ic_home_tab_me_selected.svg', onTap: () => setState(() => _selectedIndex = 2))),
            ],
          ),
        ),
      ),
    );
  }
}


Widget _originalNavItem({
  required String label,
  required bool selected,
  required String normalAsset,
  required String selectedAsset,
  required VoidCallback onTap,
}) =>
    InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(16),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 4, horizontal: 1),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            AnimatedContainer(
              duration: const Duration(milliseconds: 160),
              padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 4),
              decoration: BoxDecoration(color: selected ? const Color(0xFFFFF0D1) : Colors.transparent, borderRadius: BorderRadius.circular(14)),
              child: SvgPicture.asset(selected ? selectedAsset : normalAsset, width: 24, height: 24),
            ),
            const SizedBox(height: 2),
            Text(label, maxLines: 1, overflow: TextOverflow.ellipsis,
              style: TextStyle(fontSize: 10, fontWeight: selected ? FontWeight.w900 : FontWeight.w500, color: selected ? const Color(0xFF8C641D) : const Color(0xFF8A8174))),
          ],
        ),
      ),
    );

Widget _profileMetric({required IconData icon, required String title, required String value, required Color tint}) =>
    Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 10),
      decoration: BoxDecoration(color: const Color(0xFFFFF8E9), borderRadius: BorderRadius.circular(15), border: Border.all(color: const Color(0xFFEED8B2))),
      child: Column(
        children: [
          Icon(icon, color: tint, size: 21),
          const SizedBox(height: 5),
          Text(value, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w900, fontSize: 14, color: Color(0xFF493512))),
          const SizedBox(height: 2),
          Text(title, style: const TextStyle(fontSize: 11, color: Color(0xFF786342))),
        ],
      ),
    );

Widget _profileActionGroup({required String title, required List<Widget> children}) =>
    Container(
      width: double.infinity,
      padding: const EdgeInsets.fromLTRB(12, 12, 12, 4),
      decoration: BoxDecoration(color: const Color(0xFFFFFEFB), borderRadius: BorderRadius.circular(20), border: Border.all(color: const Color(0xFFEEDFC6))),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(4, 0, 4, 8),
            child: Text(title, style: const TextStyle(fontWeight: FontWeight.w900, fontSize: 15, color: Color(0xFF8C641D))),
          ),
          ...children,
        ],
      ),
    );

Widget _profileOriginalIcon(String title, IconData fallback) {
  const base = 'assets/original/android-res/drawable-xxhdpi/';
  const assets = <String, String>{
    'تعديل الملف الشخصي': 'ic_me_edit.webp',
    'الإعدادات': 'ic_me_setting.webp',
    'الهدايا': 'ic_me_gift.webp',
    'سجل الهدايا': 'icon_bills_gift.webp',
    'عضوية VIP': 'ic_me_vip_card.webp',
    'الإطارات والزينة': 'ic_me_mall.webp',
    'الآي دي المميز': 'ic_me_level.webp',
    'المهام والوكالات': 'ic_me_agency.webp',
    'الإشعارات والترتيب والفعاليات': 'ic_me_notice.webp',
    'الزوار': 'ic_me_living_record.webp',
    'أصدقائي': 'ic_chat_friends.png',
    'البحث عن أشخاص': 'ic_chat_friends.png',
  };
  final asset = assets[title];
  if (asset == null) return Icon(fallback, color: const Color(0xFF8C641D), size: 21);
  return Image.asset(
    '$base$asset',
    width: 27,
    height: 27,
    fit: BoxFit.contain,
    errorBuilder: (_, __, ___) => Icon(fallback, color: const Color(0xFF8C641D), size: 21),
  );
}

Widget _profileAction({required IconData icon, required String title, required VoidCallback? onTap}) =>
    ListTile(
      contentPadding: const EdgeInsets.symmetric(horizontal: 4),
      visualDensity: const VisualDensity(vertical: -2),
      leading: Container(
        width: 40,
        height: 40,
        decoration: BoxDecoration(color: const Color(0xFFFFF0D1), borderRadius: BorderRadius.circular(13)),
        child: _profileOriginalIcon(title, icon),
      ),
      title: Text(title, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 14, color: Color(0xFF493512))),
      trailing: const Icon(Icons.chevron_left_rounded, color: Color(0xFFB7A17D)),
      onTap: onTap,
    );

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

class _HomeCategoryChip extends StatelessWidget {
  const _HomeCategoryChip({required this.label, this.selected = false, required this.onTap});
  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsetsDirectional.only(end: 8),
        child: Material(
          color: Colors.transparent,
          child: InkWell(
            onTap: onTap,
            borderRadius: BorderRadius.circular(28),
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 12),
              decoration: BoxDecoration(
                color: selected ? const Color(0xFF19C6A5) : Colors.white.withValues(alpha: 0.94),
                borderRadius: BorderRadius.circular(28),
                border: Border.all(color: selected ? const Color(0xFF19C6A5) : const Color(0xFFE7E7E7)),
                boxShadow: selected ? const [] : const [BoxShadow(color: Color(0x12000000), blurRadius: 5, offset: Offset(0, 2))],
              ),
              child: Center(
                child: Text(label, style: TextStyle(color: selected ? Colors.white : const Color(0xFF383838), fontWeight: FontWeight.w800)),
              ),
            ),
          ),
        ),
      );
}

class _HomeHeaderIcon extends StatelessWidget {
  const _HomeHeaderIcon({required this.asset, required this.fallback, required this.onTap});
  final String asset;
  final IconData fallback;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Material(
        color: Colors.white.withValues(alpha: 0.88),
        shape: const CircleBorder(),
        child: InkWell(
          customBorder: const CircleBorder(),
          onTap: onTap,
          child: SizedBox(
            width: 42,
            height: 42,
            child: Padding(
              padding: const EdgeInsets.all(8),
              child: Image.asset(asset, fit: BoxFit.contain,
                errorBuilder: (_, __, ___) => Icon(fallback, color: const Color(0xFF175E49))),
            ),
          ),
        ),
      );
}

class _HomeFeatureTile extends StatelessWidget {
  const _HomeFeatureTile({required this.title, required this.asset});
  final String title;
  final String asset;

  @override
  Widget build(BuildContext context) => AspectRatio(
        aspectRatio: 1.05,
        child: ClipRRect(
          borderRadius: BorderRadius.circular(18),
          child: Stack(
            fit: StackFit.expand,
            children: [
              Image.asset(asset, fit: BoxFit.cover,
                errorBuilder: (_, __, ___) => const ColoredBox(color: Color(0xFF45B9A0))),
              DecoratedBox(decoration: BoxDecoration(
                border: Border.all(color: Colors.white.withValues(alpha: 0.65), width: 1.4),
                borderRadius: BorderRadius.circular(18),
              )),
              Positioned(
                left: 6, right: 6, bottom: 8,
                child: Text(title, textAlign: TextAlign.center, maxLines: 1,
                  style: const TextStyle(color: Colors.white, fontSize: 16, fontWeight: FontWeight.w900,
                    shadows: [Shadow(color: Color(0xAA000000), blurRadius: 5)])),
              ),
            ],
          ),
        ),
      );
}
