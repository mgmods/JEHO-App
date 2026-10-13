import 'package:flutter/material.dart';

import '../data/jeho_api_client.dart';

class SettingsPage extends StatefulWidget {
  const SettingsPage({required this.apiClient, required this.accessToken, super.key});
  final JehoApiClient apiClient;
  final String accessToken;

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  bool _loading = true;
  String? _error;
  Map<String, dynamic> _theme = const {};
  Map<String, dynamic> _features = const {};

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() { _loading = true; _error = null; });
    try {
      final results = await Future.wait([
        widget.apiClient.getAppTheme(),
        widget.apiClient.getAppFeatures(),
      ]);
      if (!mounted) return;
      setState(() {
        _theme = results[0];
        _features = results[1];
        _loading = false;
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _error = error.toString().replaceFirst('Exception: ', '');
        _loading = false;
      });
    }
  }

  String _value(dynamic value) {
    if (value == null) return 'غير محدد';
    if (value is bool) return value ? 'مفعّل' : 'غير مفعّل';
    if (value is Map || value is List) return value.toString();
    final text = value.toString().trim();
    return text.isEmpty ? 'غير محدد' : text;
  }

  Widget _section(String title, IconData icon, Map<String, dynamic> data) {
    final entries = data.entries.where((entry) => entry.value != null).take(30).toList();
    return Card(
      margin: const EdgeInsets.only(bottom: 14),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          Row(children: [
            Icon(icon, color: const Color(0xFF9A7026)),
            const SizedBox(width: 10),
            Expanded(child: Text(title, style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 16))),
          ]),
          const SizedBox(height: 10),
          if (entries.isEmpty)
            const Text('لا توجد قيم ظاهرة من الخادم حاليًا.', style: TextStyle(color: Colors.black54))
          else
            ...entries.map((entry) => Padding(
              padding: const EdgeInsets.symmetric(vertical: 5),
              child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Expanded(flex: 4, child: Text(entry.key, style: const TextStyle(fontWeight: FontWeight.w600))),
                const SizedBox(width: 10),
                Expanded(flex: 6, child: SelectableText(_value(entry.value))),
              ]),
            )),
        ]),
      ),
    );
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text('إعدادات التطبيق'),
      actions: [IconButton(tooltip: 'تحديث', onPressed: _loading ? null : _load, icon: const Icon(Icons.refresh_rounded))],
    ),
    body: _loading
      ? const Center(child: CircularProgressIndicator())
      : _error != null
        ? Center(child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(mainAxisSize: MainAxisSize.min, children: [
              const Icon(Icons.cloud_off_rounded, size: 42),
              const SizedBox(height: 12),
              Text(_error!, textAlign: TextAlign.center),
              const SizedBox(height: 12),
              FilledButton.icon(onPressed: _load, icon: const Icon(Icons.refresh_rounded), label: const Text('إعادة المحاولة')),
            ]),
          ))
        : ListView(padding: const EdgeInsets.all(16), children: [
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                gradient: const LinearGradient(colors: [Color(0xFFFFF0CC), Color(0xFFFFFAF0)]),
                borderRadius: BorderRadius.circular(18),
                border: Border.all(color: const Color(0xFFE8D4A5)),
              ),
              child: const Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Icon(Icons.info_outline_rounded, color: Color(0xFF9A7026)),
                SizedBox(width: 10),
                Expanded(child: Text(
                  'تعرض هذه الصفحة إعدادات المظهر والميزات القادمة من الخادم. لا تغيّر إعدادات الإنتاج أو الحسابات أو الخدمات.',
                  style: TextStyle(height: 1.5),
                )),
              ]),
            ),
            const SizedBox(height: 14),
            _section('المظهر الذي يحدده الداشبورد', Icons.palette_outlined, _theme),
            _section('الميزات المفعّلة في التطبيق', Icons.toggle_on_outlined, _features),
            const Card(child: ListTile(
              leading: Icon(Icons.shield_outlined, color: Color(0xFF9A7026)),
              title: Text('الصوت والاتصال'),
              subtitle: Text('لم يتم تعديل إعدادات ZEGO أو اتصال الغرف الصوتية أو Redis ضمن نقل Flutter.'),
            )),
          ]),
  );
}
