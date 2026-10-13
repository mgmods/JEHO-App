import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../data/jeho_api_client.dart';

class GamesPage extends StatefulWidget {
  const GamesPage({required this.apiClient, required this.accessToken, super.key});
  final JehoApiClient apiClient;
  final String accessToken;

  @override
  State<GamesPage> createState() => _GamesPageState();
}

class _GamesPageState extends State<GamesPage> {
  bool _loading = true;
  String? _error;
  List<Map<String, dynamic>> _games = const [];
  List<Map<String, dynamic>> _offers = const [];
  String _wallpaper = '';

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() { _loading = true; _error = null; });
    try {
      final results = await Future.wait<Object>([
        widget.apiClient.getConfiguredGames(),
        widget.apiClient.getGamePromotions(),
        widget.apiClient.getAppTheme(),
      ]);
      if (!mounted) return;
      setState(() {
        _games = (results[0] as List<Map<String, dynamic>>)
            .where((game) => game['enabled'] != false)
            .toList(growable: false);
        _offers = results[1] as List<Map<String, dynamic>>;
        final theme = results[2] as Map<String, dynamic>;
        final backgrounds = theme['backgrounds'] is Map<String, dynamic>
            ? theme['backgrounds'] as Map<String, dynamic>
            : <String, dynamic>{};
        _wallpaper = (backgrounds['allSections'] ?? backgrounds['games'] ?? backgrounds['app'] ?? '').toString().trim();
      });
    } on JehoApiException catch (e) {
      if (mounted) setState(() => _error = e.message);
    } catch (_) {
      if (mounted) setState(() => _error = 'تعذر تحميل قائمة الألعاب.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  String _text(dynamic value, [String fallback = '']) =>
      value == null || value.toString().trim().isEmpty ? fallback : value.toString();

  Future<void> _openGame(Map<String, dynamic> game) async {
    final rawUrl = _text(game['playUrl']).trim();
    if (rawUrl.isEmpty) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('رابط اللعبة غير متوفر في إعدادات الداشبورد.')),
      );
      return;
    }
    final base = Uri.tryParse(widget.apiClient.baseUrl);
    if (base == null || !base.hasScheme || base.host.isEmpty) return;
    Uri? uri = Uri.tryParse(rawUrl);
    if (rawUrl.startsWith('file:///android_asset/games/')) {
      final fileName = rawUrl.substring('file:///android_asset/games/'.length).split('?').first;
      uri = base.replace(path: '/games/$fileName', query: null, fragment: null);
    } else if (uri == null || !uri.hasScheme) {
      final fileName = rawUrl.startsWith('/')
          ? rawUrl
          : '/games/${rawUrl.contains('.') ? rawUrl : '$rawUrl.html'}';
      uri = base.replace(path: fileName, query: null, fragment: null);
    }
    if (uri == null || !uri.hasScheme || (uri.scheme != 'https' && uri.scheme != 'http')) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('رابط اللعبة غير صالح.')),
      );
      return;
    }
    final opened = await launchUrl(uri, mode: LaunchMode.inAppBrowserView);
    if (!opened && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('تعذر فتح اللعبة على هذا الجهاز.')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('الألعاب'),
        actions: [IconButton(onPressed: _loading ? null : _load, icon: const Icon(Icons.refresh_rounded))],
      ),
      body: Stack(
        fit: StackFit.expand,
        children: [
          if (_wallpaper.startsWith('https://') || _wallpaper.startsWith('http://'))
            Positioned.fill(
              child: Image.network(_wallpaper, fit: BoxFit.cover,
                errorBuilder: (_, __, ___) => const SizedBox.shrink()),
            ),
          RefreshIndicator(
        onRefresh: _load,
        child: _loading
            ? ListView(children: [SizedBox(height: 260, child: Center(child: CircularProgressIndicator()))])
            : _error != null
                ? ListView(children: [
                    const SizedBox(height: 70),
                    Center(child: Padding(padding: const EdgeInsets.all(20), child: Text(_error!, textAlign: TextAlign.center))),
                    Center(child: FilledButton.icon(onPressed: _load, icon: const Icon(Icons.refresh), label: const Text('إعادة المحاولة'))),
                  ])
                : _games.isEmpty
                    ? ListView(children: const [SizedBox(height: 100), Center(child: Text('لا توجد ألعاب متاحة حاليًا.'))])
                    : CustomScrollView(
                        physics: const AlwaysScrollableScrollPhysics(),
                        slivers: [
                          if (_offers.isNotEmpty)
                            SliverToBoxAdapter(
                              child: SizedBox(
                                height: 132,
                                child: ListView.separated(
                                  padding: const EdgeInsets.all(12),
                                  scrollDirection: Axis.horizontal,
                                  itemCount: _offers.length,
                                  separatorBuilder: (_, __) => const SizedBox(width: 10),
                                  itemBuilder: (context, index) {
                                    final offer = _offers[index];
                                    final image = _text(offer['imageUrl'], _text(offer['coverUrl'], _text(offer['bannerUrl'])));
                                    return SizedBox(
                                      width: 260,
                                      child: ClipRRect(
                                        borderRadius: BorderRadius.circular(14),
                                        child: Stack(
                                          fit: StackFit.expand,
                                          children: [
                                            if (image.isNotEmpty)
                                              Image.network(image, fit: BoxFit.cover,
                                                errorBuilder: (_, __, ___) => const ColoredBox(color: Color(0xFF493512))),
                                            if (image.isEmpty) const ColoredBox(color: Color(0xFF493512)),
                                            Align(
                                              alignment: Alignment.bottomCenter,
                                              child: Container(
                                                width: double.infinity,
                                                padding: const EdgeInsets.all(8),
                                                color: Colors.black54,
                                                child: Text(_text(offer['title'], 'عرض مميز'), maxLines: 2, overflow: TextOverflow.ellipsis, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold)),
                                              ),
                                            ),
                                          ],
                                        ),
                                      ),
                                    );
                                  },
                                ),
                              ),
                            ),
                          SliverPadding(
                            padding: const EdgeInsets.fromLTRB(12, 4, 12, 20),
                            sliver: SliverGrid(
                              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                                crossAxisCount: 3, childAspectRatio: .68, crossAxisSpacing: 10, mainAxisSpacing: 10,
                              ),
                              delegate: SliverChildBuilderDelegate(
                                (context, index) {
                                  final game = _games[index];
                                  final cover = _text(game['coverUrl']);
                                  final title = _text(game['title'], _text(game['titleEn'], 'لعبة'));
                                  return Card(
                                    clipBehavior: Clip.antiAlias,
                                    child: InkWell(
                                      onTap: () => _openGame(game),
                                      child: Column(
                                        crossAxisAlignment: CrossAxisAlignment.stretch,
                                        children: [
                                          Expanded(
                                            child: cover.isEmpty
                                                ? const ColoredBox(color: Color(0xFFEDE4D2), child: Icon(Icons.sports_esports_rounded, size: 38))
                                                : Image.network(cover, fit: BoxFit.cover,
                                                    errorBuilder: (_, __, ___) => const ColoredBox(color: Color(0xFFEDE4D2), child: Icon(Icons.sports_esports_rounded, size: 38))),
                                          ),
                                          Padding(
                                            padding: const EdgeInsets.all(7),
                                            child: Text(title, maxLines: 2, overflow: TextOverflow.ellipsis, textAlign: TextAlign.center,
                                              style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 12)),
                                          ),
                                        ],
                                      ),
                                    ),
                                  );
                                },
                                childCount: _games.length,
                              ),
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
