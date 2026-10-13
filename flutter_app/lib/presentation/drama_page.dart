import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';
import '../data/jeho_api_client.dart';

class DramaPage extends StatefulWidget {
  const DramaPage({required this.apiClient, required this.accessToken, super.key});
  final JehoApiClient apiClient;
  final String accessToken;

  @override
  State<DramaPage> createState() => _DramaPageState();
}

class _DramaPageState extends State<DramaPage> {
  bool _loading = true;
  String? _error;
  List<Map<String, dynamic>> _series = const [];
  Map<String, dynamic> _config = const {};
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
        widget.apiClient.getDramaSeries(),
        widget.apiClient.getDramaConfig(),
        widget.apiClient.getAppTheme(),
      ]);
      if (!mounted) return;
      setState(() {
        _series = results[0] as List<Map<String, dynamic>>;
        _config = results[1] as Map<String, dynamic>;
        final theme = results[2] as Map<String, dynamic>;
        final backgrounds = theme['backgrounds'] is Map<String, dynamic>
            ? theme['backgrounds'] as Map<String, dynamic>
            : <String, dynamic>{};
        _wallpaper = (backgrounds['allSections'] ?? backgrounds['home'] ?? backgrounds['app'] ?? '').toString().trim();
      });
    } on JehoApiException catch (e) {
      if (mounted) setState(() => _error = e.message);
    } catch (_) {
      if (mounted) setState(() => _error = 'تعذر تحميل مكتبة الدراما.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  String _text(dynamic value, [String fallback = '']) =>
      value == null || value.toString().trim().isEmpty ? fallback : value.toString();

  Future<void> _openSeries(Map<String, dynamic> item) async {
    final id = _text(item['id']);
    if (id.isEmpty) return;
    try {
      final detail = await widget.apiClient.getDramaSeriesDetail(id);
      if (!mounted) return;
      try {
        await widget.apiClient.viewDramaSeries(id, token: widget.accessToken.isEmpty ? null : widget.accessToken);
      } catch (_) {
        // Viewing details must remain possible if analytics is unavailable.
      }
      if (!mounted) return;
      final episodes = detail['episodes'] is List
          ? (detail['episodes'] as List).whereType<Map<String, dynamic>>().toList()
          : item['episodes'] is List
              ? (item['episodes'] as List).whereType<Map<String, dynamic>>().toList()
              : <Map<String, dynamic>>[];
      await showModalBottomSheet<void>(
        context: context,
        isScrollControlled: true,
        showDragHandle: true,
        builder: (sheetContext) => SafeArea(
          child: SizedBox(
            height: MediaQuery.of(sheetContext).size.height * .78,
            child: Column(
              children: [
                ListTile(
                  title: Text(_text(detail['title'], _text(item['title'], 'مسلسل')), style: const TextStyle(fontWeight: FontWeight.bold)),
                  subtitle: Text(_text(detail['description'], _text(item['description']))),
                  trailing: IconButton(
                    tooltip: 'إعجاب',
                    onPressed: () async {
                      try {
                        await widget.apiClient.likeDramaSeries(id, token: widget.accessToken.isEmpty ? null : widget.accessToken);
                        if (sheetContext.mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('تم إرسال طلب الإعجاب.')));
                      } catch (_) {
                        if (sheetContext.mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('تعذر تحديث الإعجاب.')));
                      }
                    },
                    icon: const Icon(Icons.favorite_border_rounded),
                  ),
                ),
                const Divider(height: 1),
                Expanded(
                  child: episodes.isEmpty
                      ? const Center(child: Text('لا توجد حلقات متاحة لهذا المسلسل حتى الآن.'))
                      : ListView.separated(
                          itemCount: episodes.length,
                          separatorBuilder: (_, __) => const Divider(height: 1),
                          itemBuilder: (context, index) {
                            final episode = episodes[index];
                            final episodeId = _text(episode['id']);
                            final number = episode['episodeNumber'] ?? index + 1;
                            return ListTile(
                              leading: const CircleAvatar(child: Icon(Icons.play_arrow_rounded)),
                              title: Text(_text(episode['title'], 'الحلقة $number')),
                              subtitle: Text('الحلقة $number • ${_text(episode['durationSec'], '—')} ثانية'),
                              trailing: IconButton(
                                tooltip: 'تشغيل الحلقة',
                                icon: const Icon(Icons.play_circle_outline_rounded),
                                onPressed: episodeId.isEmpty ? null : () async {
                                  final videoUrl = _text(episode['videoUrl']);
                                  if (videoUrl.isEmpty) {
                                    ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('رابط الحلقة غير متوفر بعد.')));
                                    return;
                                  }
                                  try {
                                    await widget.apiClient.viewDramaEpisode(episodeId, token: widget.accessToken.isEmpty ? null : widget.accessToken);
                                  } catch (_) {
                                    // Playback should not be blocked by a view-counter failure.
                                  }
                                  final uri = Uri.tryParse(videoUrl);
                                  if (uri == null || !uri.hasScheme || !(uri.scheme == 'https' || uri.scheme == 'http')) {
                                    if (context.mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('رابط الحلقة غير صالح.')));
                                    return;
                                  }
                                  final opened = await launchUrl(uri, mode: LaunchMode.externalApplication);
                                  if (!opened && context.mounted) {
                                    ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('تعذر فتح مشغل الحلقة.')));
                                  }
                                },
                              ),
                            );
                          },
                        ),
                ),
              ],
            ),
          ),
        ),
      );
    } on JehoApiException catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
    } catch (_) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('تعذر فتح تفاصيل المسلسل.')));
    }
  }

  @override
  Widget build(BuildContext context) {
    final enabled = _config['enabled'] != false;
    return Scaffold(
      appBar: AppBar(
        title: const Text('الدراما والمسلسلات'),
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
            ? ListView(children: [SizedBox(height: 280, child: Center(child: CircularProgressIndicator()))])
            : _error != null
                ? ListView(children: [
                    const SizedBox(height: 70),
                    Center(child: Padding(padding: const EdgeInsets.all(20), child: Text(_error!, textAlign: TextAlign.center))),
                    Center(child: FilledButton.icon(onPressed: _load, icon: const Icon(Icons.refresh), label: const Text('إعادة المحاولة'))),
                  ])
                : !enabled
                    ? const ListView(children: [SizedBox(height: 100), Center(child: Text('قسم الدراما متوقف حاليًا من إعدادات التطبيق.'))])
                    : _series.isEmpty
                        ? const ListView(children: [SizedBox(height: 100), Center(child: Text('لا توجد مسلسلات متاحة حاليًا.'))])
                        : GridView.builder(
                            padding: const EdgeInsets.all(12),
                            gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                              crossAxisCount: 2, childAspectRatio: .62, crossAxisSpacing: 12, mainAxisSpacing: 12,
                            ),
                            itemCount: _series.length,
                            itemBuilder: (context, index) {
                              final item = _series[index];
                              final title = _text(item['title'], 'مسلسل');
                              final cover = _text(item['coverUrl']);
                              final featured = item['isFeatured'] == true;
                              return Card(
                                clipBehavior: Clip.antiAlias,
                                child: InkWell(
                                  onTap: () => _openSeries(item),
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.stretch,
                                    children: [
                                      Expanded(
                                        child: Stack(
                                          fit: StackFit.expand,
                                          children: [
                                            if (cover.isNotEmpty)
                                              Image.network(cover, fit: BoxFit.cover,
                                                errorBuilder: (_, __, ___) => const ColoredBox(color: Color(0xFFEDE4D2), child: Icon(Icons.movie_outlined, size: 48)))
                                            else
                                              const ColoredBox(color: Color(0xFFEDE4D2), child: Icon(Icons.movie_outlined, size: 48)),
                                            if (featured)
                                              const Positioned(top: 8, right: 8, child: Chip(avatar: Icon(Icons.star_rounded, size: 16), label: Text('مميز'))),
                                          ],
                                        ),
                                      ),
                                      Padding(
                                        padding: const EdgeInsets.all(10),
                                        child: Column(
                                          crossAxisAlignment: CrossAxisAlignment.start,
                                          children: [
                                            Text(title, maxLines: 2, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.bold)),
                                            const SizedBox(height: 4),
                                            Text('${_text(item['episodeCount'], '0')} حلقة • ${_text(item['category'], 'دراما')}', maxLines: 1, overflow: TextOverflow.ellipsis),
                                          ],
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
      ),
    );
  }
}
