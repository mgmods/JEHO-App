import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:image_picker/image_picker.dart';

import 'data/jeho_api_client.dart';
import 'presentation/auth_page.dart';
import 'presentation/conversation_page.dart';
import 'presentation/feature_pages.dart' hide Center;
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
  bool _loading = true;
  String? _loadError;
  List<Map<String, dynamic>> _rooms = const [];
  List<Map<String, dynamic>> _banners = const [];
  Map<String, dynamic> _theme = const {};
  List<Map<String, dynamic>> _conversations = const [];
  Map<String, dynamic>? _profile;

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
    final section = _sections[_selectedIndex];
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
                    child: _selectedIndex == 2 &&
                            (activeUser['avatarUrl'] ?? '').toString().isNotEmpty
                        ? ClipRRect(
                            borderRadius: BorderRadius.circular(26),
                            child: Image.network(
                              _resolveMediaUrl((activeUser['avatarUrl'] ?? '').toString()),
                              fit: BoxFit.cover,
                              errorBuilder: (_, __, ___) => Icon(section.icon, size: 46, color: const Color(0xFF8C641D)),
                            ),
                          )
                        : Icon(section.icon, size: 46, color: const Color(0xFF8C641D)),
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
                  if (_selectedIndex == 0) ...[
                    if (_banners.isNotEmpty) ...[
                      const SizedBox(height: 16),
                      SizedBox(
                        height: 156,
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
                              width: MediaQuery.sizeOf(context).width * 0.82,
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
                    const SizedBox(height: 20),
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
                          '${_rooms.length} غرفة',
                          style: Theme.of(context).textTheme.bodySmall,
                        ),
                      ],
                    ),
                    const SizedBox(height: 10),
                    SizedBox(
                      width: double.infinity,
                      child: FilledButton.icon(
                        onPressed: _loading ? null : _createRoom,
                        icon: const Icon(Icons.add_circle_outline_rounded),
                        label: const Text('إنشاء غرفة'),
                      ),
                    ),
                    const SizedBox(height: 10),
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
                    else if (_rooms.isEmpty)
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
                      ..._rooms.map((room) {
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
                        return Card(
                          clipBehavior: Clip.antiAlias,
                          margin: const EdgeInsets.only(bottom: 14),
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
                                  height: 154,
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
                                                  fontSize: 19,
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
                                  padding: const EdgeInsets.fromLTRB(
                                    14,
                                    12,
                                    14,
                                    14,
                                  ),
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
                        );
                      }),
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
                          child: ListTile(
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
                            leading: CircleAvatar(
                              backgroundColor: const Color(0xFFFFE7B0),
                              child: Text(
                                title.isEmpty ? '؟' : title.characters.first,
                                style: const TextStyle(color: Color(0xFF8C641D)),
                              ),
                            ),
                            title: Text(
                              title,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(fontWeight: FontWeight.w700),
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
                    const SizedBox(height: 16),
                    Card(
                      clipBehavior: Clip.antiAlias,
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(22)),
                      child: Padding(
                        padding: const EdgeInsets.all(16),
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.center,
                          children: [
                            ClipRRect(
                              borderRadius: BorderRadius.circular(16),
                              child: SizedBox(
                                width: 82,
                                height: 82,
                                child: (activeUser['avatarUrl'] ?? '').toString().trim().isNotEmpty
                                    ? Image.network(
                                        _resolveMediaUrl(activeUser['avatarUrl'].toString()),
                                        fit: BoxFit.cover,
                                        errorBuilder: (_, __, ___) => const ColoredBox(
                                          color: Color(0xFFFFE7B0),
                                          child: Icon(Icons.person_rounded, size: 42, color: Color(0xFF8C641D)),
                                        ),
                                      )
                                    : const ColoredBox(
                                        color: Color(0xFFFFE7B0),
                                        child: Icon(Icons.person_rounded, size: 42, color: Color(0xFF8C641D)),
                                      ),
                              ),
                            ),
                            const SizedBox(width: 14),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(displayName, maxLines: 1, overflow: TextOverflow.ellipsis,
                                      style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w900)),
                                  const SizedBox(height: 5),
                                  Text('@${activeUser['username'] ?? '—'}',
                                      maxLines: 1, overflow: TextOverflow.ellipsis,
                                      style: const TextStyle(color: Colors.black54)),
                                  Text('ID ${activeUser['publicId'] ?? '—'}',
                                      maxLines: 1, overflow: TextOverflow.ellipsis,
                                      style: const TextStyle(color: Color(0xFF8C641D), fontWeight: FontWeight.w700)),
                                  if ((activeUser['bio'] ?? '').toString().trim().isNotEmpty) ...[
                                    const SizedBox(height: 7),
                                    Text(activeUser['bio'].toString(), maxLines: 2, overflow: TextOverflow.ellipsis,
                                        style: const TextStyle(color: Colors.black54, height: 1.4)),
                                  ],
                                ],
                              ),
                            ),
                            IconButton(
                              tooltip: 'إعدادات التطبيق',
                              onPressed: () => Navigator.of(context).push(MaterialPageRoute<void>(
                                builder: (_) => SettingsPage(
                                  apiClient: widget.apiClient,
                                  accessToken: widget.session['accessToken']?.toString() ?? '',
                                ),
                              )),
                              icon: const Icon(Icons.settings_rounded, color: Color(0xFF8C641D)),
                            ),
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(height: 18),
                    FilledButton.icon(
                      onPressed: _loading ? null : _changeAvatar,
                      icon: const Icon(Icons.add_a_photo_rounded),
                      label: const Text('تغيير الصورة الشخصية'),
                    ),
                    const SizedBox(height: 8),
                    FilledButton.tonalIcon(
                      onPressed: _loading ? null : () => _editProfile(activeUser),
                      icon: const Icon(Icons.edit_rounded),
                      label: const Text('تعديل الملف الشخصي'),
                    ),
                    const SizedBox(height: 18),
                    Wrap(
                      alignment: WrapAlignment.center,
                      spacing: 10,
                      runSpacing: 10,
                      children: [
                        OutlinedButton.icon(
                          onPressed: () => Navigator.of(context).push(
                            MaterialPageRoute<void>(
                              builder: (_) => SettingsPage(
                                apiClient: widget.apiClient,
                                accessToken: widget.session['accessToken']?.toString() ?? '',
                              ),
                            ),
                          ),
                          icon: const Icon(Icons.settings_rounded),
                          label: const Text('إعدادات التطبيق'),
                        ),
                        OutlinedButton.icon(
                          onPressed: () => Navigator.of(context).push(
                            MaterialPageRoute<void>(
                              builder: (_) => WalletPage(
                                apiClient: widget.apiClient,
                                accessToken: widget.session['accessToken']?.toString() ?? '',
                              ),
                            ),
                          ),
                          icon: const Icon(Icons.account_balance_wallet_rounded),
                          label: const Text('المحفظة'),
                        ),
                        OutlinedButton.icon(
                          onPressed: () => Navigator.of(context).push(
                            MaterialPageRoute<void>(
                              builder: (_) => GiftCatalogPage(apiClient: widget.apiClient, accessToken: widget.session['accessToken']?.toString() ?? ''),
                            ),
                          ),
                          icon: const Icon(Icons.card_giftcard_rounded),
                          label: const Text('الهدايا'),
                        ),
                        OutlinedButton.icon(
                          onPressed: () => Navigator.of(context).push(
                            MaterialPageRoute<void>(
                              builder: (_) => GiftHistoryPage(
                                apiClient: widget.apiClient,
                                accessToken: widget.session['accessToken']?.toString() ?? '',
                              ),
                            ),
                          ),
                          icon: const Icon(Icons.history_rounded),
                          label: const Text('سجل الهدايا'),
                        ),
                        OutlinedButton.icon(
                          onPressed: () => Navigator.of(context).push(
                            MaterialPageRoute<void>(
                              builder: (_) => CosmeticsPage(
                                apiClient: widget.apiClient,
                                accessToken: widget.session['accessToken']?.toString() ?? '',
                              ),
                            ),
                          ),
                          icon: const Icon(Icons.auto_awesome_rounded),
                          label: const Text('الإطارات والزينة'),
                        ),
                        OutlinedButton.icon(
                          onPressed: () => Navigator.of(context).push(
                            MaterialPageRoute<void>(
                              builder: (_) => VanityIdsPage(
                                apiClient: widget.apiClient,
                                accessToken: widget.session['accessToken']?.toString() ?? '',
                              ),
                            ),
                          ),
                          icon: const Icon(Icons.tag_rounded),
                          label: const Text('الآي دي المميز'),
                        ),
                        OutlinedButton.icon(
                          onPressed: () => Navigator.of(context).push(
                            MaterialPageRoute<void>(
                              builder: (_) => SocialHubPage(
                                apiClient: widget.apiClient,
                                accessToken: widget.session['accessToken']?.toString() ?? '',
                              ),
                            ),
                          ),
                          icon: const Icon(Icons.favorite_rounded),
                          label: const Text('CP والعلاقات'),
                        ),
                        OutlinedButton.icon(
                          onPressed: () => Navigator.of(context).push(
                            MaterialPageRoute<void>(
                              builder: (_) => ProfilePeoplePage(
                                apiClient: widget.apiClient,
                                accessToken: widget.session['accessToken']?.toString() ?? '',
                                visitors: true,
                              ),
                            ),
                          ),
                          icon: const Icon(Icons.visibility_outlined),
                          label: const Text('الزوار'),
                        ),
                        OutlinedButton.icon(
                          onPressed: () => Navigator.of(context).push(
                            MaterialPageRoute<void>(
                              builder: (_) => ProfilePeoplePage(
                                apiClient: widget.apiClient,
                                accessToken: widget.session['accessToken']?.toString() ?? '',
                                visitors: false,
                              ),
                            ),
                          ),
                          icon: const Icon(Icons.people_alt_outlined),
                          label: const Text('أصدقائي'),
                        ),
                        OutlinedButton.icon(
                          onPressed: () => Navigator.of(context).push(
                            MaterialPageRoute<void>(
                              builder: (_) => FeatureHubPage(
                                apiClient: widget.apiClient,
                                accessToken: widget.session['accessToken']?.toString() ?? '',
                              ),
                            ),
                          ),
                          icon: const Icon(Icons.dashboard_customize_rounded),
                          label: const Text('المهام والوكالات'),
                        ),
                        OutlinedButton.icon(
                          onPressed: () => Navigator.of(context).push(
                            MaterialPageRoute<void>(
                              builder: (_) => DiscoveryHubPage(
                                apiClient: widget.apiClient,
                                accessToken: widget.session['accessToken']?.toString() ?? '',
                              ),
                            ),
                          ),
                          icon: const Icon(Icons.notifications_active_outlined),
                          label: const Text('الإشعارات والترتيب والفعاليات والألعاب'),
                        ),
                        OutlinedButton.icon(
                          onPressed: () => Navigator.of(context).push(
                            MaterialPageRoute<void>(
                              builder: (_) => DramaPage(
                                apiClient: widget.apiClient,
                                accessToken: widget.session['accessToken']?.toString() ?? '',
                              ),
                            ),
                          ),
                          icon: const Icon(Icons.movie_creation_outlined),
                          label: const Text('الدراما والمسلسلات'),
                        ),
                        OutlinedButton.icon(
                          onPressed: () => Navigator.of(context).push(
                            MaterialPageRoute<void>(
                              builder: (_) => GamesPage(
                                apiClient: widget.apiClient,
                                accessToken: widget.session['accessToken']?.toString() ?? '',
                              ),
                            ),
                          ),
                          icon: const Icon(Icons.sports_esports_rounded),
                          label: const Text('الألعاب'),
                        ),
                        OutlinedButton.icon(
                          onPressed: () => Navigator.of(context).push(
                            MaterialPageRoute<void>(
                              builder: (_) => SocialPage(
                                apiClient: widget.apiClient,
                                accessToken: widget.session['accessToken']?.toString() ?? '',
                              ),
                            ),
                          ),
                          icon: const Icon(Icons.person_search_rounded),
                          label: const Text('البحث عن أشخاص'),
                        ),


                      ],
                    ),
                    const SizedBox(height: 18),
                    const Text(
                      'تجد هنا الملف الشخصي والمحفظة والشحن والهدايا والإطارات والآي دي والعلاقات والمهام والوكالات.',
                      textAlign: TextAlign.center,
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
      bottomNavigationBar: NavigationBar(
        selectedIndex: _selectedIndex,
        onDestinationSelected: (index) {
          setState(() => _selectedIndex = index);
        },
        destinations: [
          NavigationDestination(
            icon: SvgPicture.asset(
              'assets/original/icons/ic_home_tab_party_normal.svg',
              width: 24,
              height: 24,
            ),
            selectedIcon: SvgPicture.asset(
              'assets/original/icons/ic_home_tab_party_selected.svg',
              width: 24,
              height: 24,
            ),
            label: 'الرئيسية',
          ),
          NavigationDestination(
            icon: SvgPicture.asset(
              'assets/original/icons/ic_home_tab_chat_normal.svg',
              width: 24,
              height: 24,
            ),
            selectedIcon: SvgPicture.asset(
              'assets/original/icons/ic_home_tab_chat_selected.svg',
              width: 24,
              height: 24,
            ),
            label: 'الرسائل',
          ),
          NavigationDestination(
            icon: SvgPicture.asset(
              'assets/original/icons/ic_home_tab_me_normal.svg',
              width: 24,
              height: 24,
            ),
            selectedIcon: SvgPicture.asset(
              'assets/original/icons/ic_home_tab_me_selected.svg',
              width: 24,
              height: 24,
            ),
            label: 'أنا',
          ),
          NavigationDestination(
            icon: SvgPicture.asset(
              'assets/original/icons/ic_home_tab_game_normal.svg',
              width: 24,
              height: 24,
            ),
            selectedIcon: SvgPicture.asset(
              'assets/original/icons/ic_home_tab_game_selected.svg',
              width: 24,
              height: 24,
            ),
            label: 'الألعاب',
          ),
          NavigationDestination(
            icon: SvgPicture.asset(
              'assets/original/icons/ic_home_tab_drama_normal.svg',
              width: 24,
              height: 24,
            ),
            selectedIcon: SvgPicture.asset(
              'assets/original/icons/ic_home_tab_drama_selected.svg',
              width: 24,
              height: 24,
            ),
            label: 'الدراما',
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
