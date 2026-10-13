import 'package:flutter/material.dart';

import '../data/jeho_api_client.dart';

class DiscoveryHubPage extends StatefulWidget {
  const DiscoveryHubPage({
    required this.apiClient,
    required this.accessToken,
    super.key,
  });

  final JehoApiClient apiClient;
  final String accessToken;

  @override
  State<DiscoveryHubPage> createState() => _DiscoveryHubPageState();
}

class _DiscoveryHubPageState extends State<DiscoveryHubPage> {
  static const _sections = ['الإشعارات', 'الترتيب', 'الفعاليات', 'المسابقات', 'الألعاب', 'إعدادات التطبيق'];
  static const _gold = Color(0xFFB68A35);

  int _section = 0;
  bool _loading = false;
  bool _busy = false;
  String? _error;
  List<Map<String, dynamic>> _items = const [];
  Map<String, dynamic> _extra = const {};

  String _str(dynamic value, [String fallback = '—']) {
    if (value == null || value.toString().trim().isEmpty) return fallback;
    return value.toString();
  }

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() { _loading = true; _error = null; });
    try {
      switch (_section) {
        case 0:
          _items = await widget.apiClient.getNotifications(widget.accessToken);
          _extra = await widget.apiClient.getUnreadNotificationCount(widget.accessToken).catchError((_) => <String, dynamic>{});
          break;
        case 1:
          _items = await widget.apiClient.getRanking('weekly', 'rich');
          _extra = const {};
          break;
        case 2:
          _items = await widget.apiClient.getEvents(widget.accessToken);
          _extra = const {};
          break;
        case 3:
          _items = await widget.apiClient.getContests(widget.accessToken);
          _extra = const {};
          break;
        case 4:
          _items = const [];
          _extra = await widget.apiClient.getDailyBossStatus(widget.accessToken);
          break;
        case 5:
          _items = const [];
          _extra = await widget.apiClient.getAppFeatures();
          break;
      }
    } on JehoApiException catch (error) {
      _error = error.message;
    } catch (_) {
      _error = 'تعذر تحميل القسم. حاول مرة أخرى.';
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _run(Future<void> Function() action, {String? success}) async {
    if (_busy) return;
    setState(() { _busy = true; _error = null; });
    try {
      await action();
      if (!mounted) return;
      if (success != null) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(success)));
      }
      await _load();
    } on JehoApiException catch (error) {
      if (mounted) setState(() => _error = error.message);
    } catch (_) {
      if (mounted) setState(() => _error = 'تعذر تنفيذ العملية.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _markRead(Map<String, dynamic> item) async {
    final id = _str(item['id'], '');
    if (id.isEmpty) return;
    await _run(() async {
      await widget.apiClient.markNotificationRead(widget.accessToken, id);
    }, success: 'تم تعليم الإشعار كمقروء.');
  }

  Future<void> _changeRanking(String period, String category) async {
    setState(() { _loading = true; _error = null; });
    try {
      final rows = await widget.apiClient.getRanking(period, category);
      if (!mounted) return;
      setState(() { _items = rows; _extra = {'period': period, 'category': category}; });
    } on JehoApiException catch (error) {
      if (mounted) setState(() => _error = error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _openLuckyWheel() async {
    try {
      final state = await widget.apiClient.getLuckyWheelState(widget.accessToken);
      if (!mounted) return;
      final config = state['config'] is Map<String, dynamic>
          ? state['config'] as Map<String, dynamic>
          : <String, dynamic>{};
      final rawChips = config['chips'];
      final chips = rawChips is List
          ? rawChips.whereType<num>().map((value) => value.toInt()).toList()
          : <int>[];
      final minBet = (config['minBet'] as num?)?.toInt() ?? 100;
      final maxBet = (config['maxBet'] as num?)?.toInt() ?? 10000;
      if (chips.isEmpty) chips.add(minBet);
      var amount = chips.first;
      final chosen = await showDialog<int>(
        context: context,
        builder: (dialogContext) => StatefulBuilder(
          builder: (context, setDialogState) => AlertDialog(
            title: const Text('عجلة الحظ'),
            content: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('رصيدك: ${_str(state['balance'], '0')} كوينز'),
                const SizedBox(height: 10),
                const Text('اختر قيمة الرهان. يمكن خسارة المبلغ المختار.'),
                const SizedBox(height: 8),
                DropdownButtonFormField<int>(
                  initialValue: amount,
                  items: chips.map((chip) => DropdownMenuItem<int>(value: chip, child: Text('$chip كوينز'))).toList(),
                  onChanged: (value) { if (value != null) setDialogState(() => amount = value); },
                  decoration: const InputDecoration(labelText: 'قيمة الرهان'),
                ),
                const SizedBox(height: 8),
                Text('الحدود المعتمدة من الخادم: $minBet – $maxBet كوينز', style: const TextStyle(fontSize: 12)),
              ],
            ),
            actions: [
              TextButton(onPressed: () => Navigator.pop(dialogContext), child: const Text('إلغاء')),
              FilledButton(
                onPressed: () => Navigator.pop(dialogContext, amount),
                child: const Text('تأكيد الرهان واللف'),
              ),
            ],
          ),
        ),
      );
      if (chosen == null || !mounted) return;
      if (chosen < minBet || chosen > maxBet) {
        setState(() => _error = 'قيمة الرهان خارج الحدود التي أعادها الخادم.');
        return;
      }
      setState(() { _busy = true; _error = null; });
      try {
        final result = await widget.apiClient.spinLuckyWheel(widget.accessToken, {'amount': chosen});
        if (!mounted) return;
        final resultText = result.entries
            .where((entry) => entry.value == null || entry.value is String || entry.value is num || entry.value is bool)
            .map((entry) => '${entry.key}: ${entry.value ?? '—'}')
            .join('\n');
        await showDialog<void>(
          context: context,
          builder: (dialogContext) => AlertDialog(
            title: const Text('نتيجة عجلة الحظ'),
            content: SelectableText(resultText.isEmpty ? 'أعاد الخادم نتيجة اللفة.' : resultText),
            actions: [TextButton(onPressed: () => Navigator.pop(dialogContext), child: const Text('إغلاق'))],
          ),
        );
        if (mounted) await _load();
      } finally {
        if (mounted) setState(() => _busy = false);
      }
    } on JehoApiException catch (error) {
      if (mounted) setState(() => _error = error.message);
    } catch (_) {
      if (mounted) setState(() => _error = 'تعذر فتح عجلة الحظ أو قراءة نتيجتها.');
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(_sections[_section]),
        actions: [IconButton(onPressed: _loading ? null : _load, icon: const Icon(Icons.refresh_rounded), tooltip: 'تحديث')],
      ),
      body: Column(
        children: [
          SizedBox(
            height: 62,
            child: ListView(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
              scrollDirection: Axis.horizontal,
              children: [
                for (var i = 0; i < _sections.length; i++)
                  Padding(
                    padding: const EdgeInsetsDirectional.only(end: 6),
                    child: ChoiceChip(
                      label: Text(_sections[i]),
                      selected: _section == i,
                      onSelected: _loading ? null : (_) {
                        setState(() => _section = i);
                        _load();
                      },
                    ),
                  ),
              ],
            ),
          ),
          if (_error != null)
            Material(
              color: Theme.of(context).colorScheme.errorContainer,
              child: ListTile(
                leading: const Icon(Icons.error_outline),
                title: Text(_error!),
                trailing: IconButton(onPressed: _load, icon: const Icon(Icons.refresh)),
              ),
            ),
          Expanded(
            child: _loading
                ? const Center(child: CircularProgressIndicator())
                : _buildSection(),
          ),
        ],
      ),
    );
  }

  Widget _buildSection() {
    switch (_section) {
      case 0:
        return _notifications();
      case 1:
        return _ranking();
      case 2:
        return _events();
      case 3:
        return _contests();
      case 4:
        return _games();
      default:
        return _settings();
    }
  }

  Widget _notifications() {
    final unread = _extra['count'] ?? _extra['unread'] ?? _extra['unreadCount'] ?? 0;
    return ListView(
      padding: const EdgeInsets.all(12),
      children: [
        Card(
          child: ListTile(
            leading: const Icon(Icons.notifications_active_outlined, color: _gold),
            title: const Text('الإشعارات'),
            subtitle: Text('غير المقروء: $unread'),
            trailing: TextButton(
              onPressed: _busy ? null : () => _run(() async {
                await widget.apiClient.markAllNotificationsRead(widget.accessToken);
              }, success: 'تم تعليم جميع الإشعارات كمقروءة.'),
              child: const Text('قراءة الكل'),
            ),
          ),
        ),
        if (_items.isEmpty)
          const Padding(padding: EdgeInsets.all(28), child: Text('لا توجد إشعارات لعرضها.', textAlign: TextAlign.center)),
        for (final item in _items)
          Card(
            child: ListTile(
              leading: Icon(item['isRead'] == true ? Icons.notifications_none : Icons.notifications_active, color: _gold),
              title: Text(_str(item['title'], 'إشعار')),
              subtitle: Text('${_str(item['body'], '')}\n${_str(item['createdAt'], '')}'),
              isThreeLine: true,
              trailing: item['isRead'] == true
                  ? const Icon(Icons.check, color: Colors.grey)
                  : IconButton(onPressed: _busy ? null : () => _markRead(item), icon: const Icon(Icons.done)),
            ),
          ),
      ],
    );
  }

  Widget _ranking() {
    final period = _str(_extra['period'], 'weekly');
    final category = _str(_extra['category'], 'rich');
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.all(12),
          child: Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              DropdownButton<String>(
                value: ['daily', 'weekly', 'monthly'].contains(period) ? period : 'weekly',
                items: const [
                  DropdownMenuItem(value: 'daily', child: Text('يومي')),
                  DropdownMenuItem(value: 'weekly', child: Text('أسبوعي')),
                  DropdownMenuItem(value: 'monthly', child: Text('شهري')),
                ],
                onChanged: (value) { if (value != null) _changeRanking(value, category); },
              ),
              DropdownButton<String>(
                value: ['rich', 'popular', 'host', 'agency', 'room', 'gifts'].contains(category) ? category : 'rich',
                items: const [
                  DropdownMenuItem(value: 'rich', child: Text('الأغنى')),
                  DropdownMenuItem(value: 'popular', child: Text('الأكثر شعبية')),
                  DropdownMenuItem(value: 'host', child: Text('المضيفون')),
                  DropdownMenuItem(value: 'agency', child: Text('الوكالات')),
                  DropdownMenuItem(value: 'room', child: Text('الغرف')),
                  DropdownMenuItem(value: 'gifts', child: Text('الهدايا')),
                ],
                onChanged: (value) { if (value != null) _changeRanking(period, value); },
              ),
            ],
          ),
        ),
        Expanded(
          child: _items.isEmpty
              ? const Center(child: Text('لا توجد نتائج ترتيب متاحة لهذه الفترة.'))
              : ListView.builder(
                  itemCount: _items.length,
                  itemBuilder: (context, index) {
                    final row = _items[index];
                    return ListTile(
                      leading: CircleAvatar(backgroundColor: const Color(0xFFFFE7B0), child: Text(_str(row['rank'], '${index + 1}'))),
                      title: Text(_str(row['targetName'] ?? row['displayName'] ?? row['name'], 'مشارك')),
                      subtitle: Text('النقاط: ${_str(row['score'] ?? row['diamonds'] ?? row['value'])}'),
                    );
                  },
                ),
        ),
      ],
    );
  }

  Widget _events() {
    return ListView(
      padding: const EdgeInsets.all(12),
      children: [
        if (_items.isEmpty)
          const Padding(padding: EdgeInsets.all(24), child: Text('لا توجد فعاليات عامة حاليًا.', textAlign: TextAlign.center)),
        for (final item in _items)
          Card(
            child: Column(
              children: [
                ListTile(
                  leading: const CircleAvatar(child: Icon(Icons.event_available_rounded)),
                  title: Text(_str(item['title'], 'فعالية')),
                  subtitle: Text('${_str(item['description'], '')}\n${_str(item['startAt'], '')} · ${_str(item['status'], '')}'),
                  isThreeLine: true,
                ),
                Align(
                  alignment: AlignmentDirectional.centerEnd,
                  child: Padding(
                    padding: const EdgeInsetsDirectional.only(end: 12, bottom: 8),
                    child: OutlinedButton.icon(
                      onPressed: _busy || _str(item['id'], '').isEmpty ? null : () => _run(() async {
                        await widget.apiClient.subscribeEvent(widget.accessToken, _str(item['id']), subscribe: item['subscribed'] != true);
                      }, success: item['subscribed'] == true ? 'تم إلغاء متابعة الفعالية.' : 'تمت متابعة الفعالية.'),
                      icon: Icon(item['subscribed'] == true ? Icons.notifications_off_outlined : Icons.notifications_active_outlined),
                      label: Text(item['subscribed'] == true ? 'إلغاء المتابعة' : 'تابع الفعالية'),
                    ),
                  ),
                ),
              ],
            ),
          ),
      ],
    );
  }

  Widget _contests() {
    return ListView(
      padding: const EdgeInsets.all(12),
      children: [
        if (_items.isEmpty)
          const Padding(padding: EdgeInsets.all(24), child: Text('لا توجد مسابقات متاحة حاليًا.', textAlign: TextAlign.center)),
        for (final item in _items)
          Card(
            child: ListTile(
              leading: const CircleAvatar(child: Icon(Icons.emoji_events_rounded)),
              title: Text(_str(item['title'] ?? item['name'], 'مسابقة')),
              subtitle: Text('${_str(item['description'] ?? item['prizeLabel'], '')}\nالحالة: ${_str(item['status'])}'),
              isThreeLine: true,
              trailing: FilledButton(
                onPressed: _busy || item['joined'] == true || _str(item['id'], '').isEmpty ? null : () => _run(() async {
                  await widget.apiClient.joinContest(widget.accessToken, _str(item['id']));
                }, success: 'تم تسجيل انضمامك للمسابقة.'),
                child: Text(item['joined'] == true ? 'منضم' : 'انضمام'),
              ),
            ),
          ),
      ],
    );
  }

  Widget _games() {
    final boss = _extra;
    final status = _str(boss['status'] ?? boss['state'], 'جاهز');
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Row(children: [Icon(Icons.sports_esports_rounded, color: _gold), SizedBox(width: 8), Text('تحدي الزعيم اليومي', style: TextStyle(fontWeight: FontWeight.w800, fontSize: 18))]),
                const SizedBox(height: 12),
                Text('الحالة: $status'),
                Text('الهجمات المتبقية: ${_str(boss['remainingAttacks'] ?? boss['attacksRemaining'] ?? boss['remaining'], '—')}'),
                Text('المكافأة: ${_str(boss['reward'] ?? boss['rewardCoins'] ?? boss['prize'], 'تُحدد من الخادم')}'),
                const SizedBox(height: 12),
                FilledButton.icon(
                  onPressed: _busy ? null : () => _run(() async {
                    await widget.apiClient.attackDailyBoss(widget.accessToken);
                  }, success: 'تم إرسال الهجوم والتحقق من النتيجة عبر الخادم.'),
                  icon: const Icon(Icons.flash_on_rounded),
                  label: const Text('تنفيذ هجوم اليوم'),
                ),
              ],
            ),
          ),
        ),
        Card(
          child: ListTile(
            leading: const Icon(Icons.casino_outlined, color: _gold, size: 32),
            title: const Text('عجلة الحظ'),
            subtitle: const Text('تُعرض قيمة الرهان والحدود من الخادم قبل التأكيد.'),
            trailing: const Icon(Icons.chevron_left_rounded),
            onTap: _busy ? null : _openLuckyWheel,
          ),
        ),
        const Padding(
          padding: EdgeInsets.all(8),
          child: Text('عمليات اللعب والمكافآت تُعتمد من الخادم. راجع قيمة الرهان قبل التأكيد؛ قد تخسر المبلغ المختار.'),
        ),
      ],
    );
  }

  Widget _settings() {
    if (_extra.isEmpty) {
      return const Center(child: Text('لا توجد إعدادات ميزات متاحة.'));
    }
    return ListView(
      padding: const EdgeInsets.all(12),
      children: [
        const ListTile(leading: Icon(Icons.tune_rounded, color: _gold), title: Text('إعدادات ميزات التطبيق'), subtitle: Text('قراءة إعدادات الخادم فقط؛ التعديل الإداري يبقى في الداشبورد.')),
        for (final entry in _extra.entries)
          Card(
            child: ListTile(
              title: Text(entry.key),
              trailing: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 180),
                child: Text(_str(entry.value), textAlign: TextAlign.end, maxLines: 3, overflow: TextOverflow.ellipsis),
              ),
            ),
          ),
      ],
    );
  }
}
