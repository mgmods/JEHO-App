import 'package:flutter/material.dart';

import '../data/jeho_api_client.dart';

class UserSearchPage extends StatefulWidget {
  const UserSearchPage({
    required this.apiClient,
    required this.accessToken,
    super.key,
  });

  final JehoApiClient apiClient;
  final String accessToken;

  @override
  State<UserSearchPage> createState() => _UserSearchPageState();
}

class _UserSearchPageState extends State<UserSearchPage> {
  final _query = TextEditingController();
  List<Map<String, dynamic>> _users = const [];
  bool _loading = false;
  String? _error;
  String _lastQuery = '';

  @override
  void dispose() {
    _query.dispose();
    super.dispose();
  }

  Future<void> _search([String? value]) async {
    final query = (value ?? _query.text).trim();
    if (query.isEmpty) {
      setState(() { _users = const []; _error = null; _lastQuery = ''; });
      return;
    }
    setState(() { _loading = true; _error = null; _lastQuery = query; });
    try {
      final results = await widget.apiClient.searchUsers(query, accessToken: widget.accessToken);
      if (mounted) setState(() => _users = results);
    } on JehoApiException catch (error) {
      if (mounted) setState(() => _error = error.message);
    } catch (_) {
      if (mounted) setState(() => _error = 'تعذر البحث الآن. حاول مرة أخرى.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  String _text(Map<String, dynamic> user, List<String> keys, [String fallback = 'مستخدم JEHO']) {
    for (final key in keys) {
      final value = user[key]?.toString().trim() ?? '';
      if (value.isNotEmpty && value != 'null') return value;
    }
    return fallback;
  }

  String _media(String value) {
    if (value.startsWith('http://') || value.startsWith('https://')) return value;
    final base = Uri.parse(widget.apiClient.baseUrl);
    final origin = Uri(scheme: base.scheme, host: base.host, port: base.hasPort ? base.port : null);
    final normalized = value.replaceFirst(RegExp(r'^/uploads/'), 'uploads/');
    if (normalized.startsWith('uploads/')) return base.resolve(normalized).toString();
    return origin.resolve(value.startsWith('/') ? value : '/$value').toString();
  }

  Future<void> _openUser(Map<String, dynamic> user) async {
    final id = _text(user, ['id', 'userId'], '');
    if (id.isEmpty) return;
    try {
      final details = await widget.apiClient.getPublicUser(id, accessToken: widget.accessToken);
      if (!mounted) return;
      await showModalBottomSheet<void>(
        context: context,
        isScrollControlled: true,
        showDragHandle: true,
        builder: (context) {
          final avatar = _text(details, ['avatarUrl', 'avatar', 'photoUrl'], '');
          final name = _text(details, ['displayName', 'username', 'name']);
          return SafeArea(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  CircleAvatar(
                    radius: 42,
                    backgroundImage: avatar.isEmpty ? null : NetworkImage(_media(avatar)),
                    child: avatar.isEmpty ? const Icon(Icons.person, size: 38) : null,
                  ),
                  const SizedBox(height: 12),
                  Text(name, style: Theme.of(context).textTheme.titleLarge),
                  if (_text(details, ['username'], '').isNotEmpty)
                    Text('@${_text(details, ['username'], '')}', textDirection: TextDirection.ltr),
                  if (_text(details, ['bio', 'description'], '').isNotEmpty) ...[
                    const SizedBox(height: 12),
                    Text(_text(details, ['bio', 'description'], ''), textAlign: TextAlign.center),
                  ],
                  const SizedBox(height: 18),
                  OutlinedButton.icon(
                    onPressed: () => Navigator.pop(context),
                    icon: const Icon(Icons.close),
                    label: const Text('إغلاق'),
                  ),
                ],
              ),
            ),
          );
        },
      );
    } on JehoApiException catch (error) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(error.message)));
    } catch (_) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('تعذر فتح الملف الشخصي.')));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('البحث عن المستخدمين')),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 14, 16, 8),
            child: TextField(
              controller: _query,
              textInputAction: TextInputAction.search,
              onSubmitted: _search,
              decoration: InputDecoration(
                hintText: 'ابحث بالاسم أو اسم المستخدم',
                prefixIcon: const Icon(Icons.search),
                suffixIcon: IconButton(
                  tooltip: 'بحث',
                  onPressed: _loading ? null : () => _search(),
                  icon: const Icon(Icons.arrow_forward_rounded),
                ),
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(18)),
              ),
            ),
          ),
          if (_loading) const LinearProgressIndicator(),
          if (_error != null)
            Padding(
              padding: const EdgeInsets.all(16),
              child: Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
            ),
          Expanded(
            child: _users.isEmpty
                ? Center(
                    child: Text(
                      _lastQuery.isEmpty
                          ? 'اكتب اسمًا للعثور على المستخدمين.'
                          : _loading ? 'جارٍ البحث…' : 'لا توجد نتائج مطابقة.',
                    ),
                  )
                : ListView.separated(
                    padding: const EdgeInsets.all(12),
                    itemCount: _users.length,
                    separatorBuilder: (_, __) => const Divider(height: 1),
                    itemBuilder: (context, index) {
                      final user = _users[index];
                      final avatar = _text(user, ['avatarUrl', 'avatar', 'photoUrl'], '');
                      final name = _text(user, ['displayName', 'username', 'name']);
                      final username = _text(user, ['username'], '');
                      return ListTile(
                        onTap: () => _openUser(user),
                        leading: CircleAvatar(
                          backgroundImage: avatar.isEmpty ? null : NetworkImage(_media(avatar)),
                          child: avatar.isEmpty ? const Icon(Icons.person_outline) : null,
                        ),
                        title: Text(name),
                        subtitle: username.isEmpty ? null : Text('@$username', textDirection: TextDirection.ltr),
                        trailing: const Icon(Icons.chevron_left_rounded),
                      );
                    },
                  ),
          ),
        ],
      ),
    );
  }
}
