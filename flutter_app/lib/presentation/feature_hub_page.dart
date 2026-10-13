import 'package:flutter/material.dart';

import '../data/jeho_api_client.dart';

class FeatureHubPage extends StatefulWidget {
  const FeatureHubPage({
    required this.apiClient,
    required this.accessToken,
    super.key,
  });

  final JehoApiClient apiClient;
  final String accessToken;

  @override
  State<FeatureHubPage> createState() => _FeatureHubPageState();
}

class _FeatureHubPageState extends State<FeatureHubPage> {
  int _section = 0;
  bool _loading = false;
  String? _error;
  List<Map<String, dynamic>> _items = const [];
  Map<String, dynamic>? _cp;
  Map<String, dynamic>? _agency;

  static const _titles = ['المهام', 'العلاقات', 'الوكالات', 'CP'];
  static const _icons = [
    Icons.task_alt_rounded,
    Icons.people_alt_rounded,
    Icons.groups_rounded,
    Icons.favorite_rounded,
  ];

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      if (_section == 0) {
        _items = await widget.apiClient.getDailyTasks(widget.accessToken);
      } else if (_section == 1) {
        _items = await widget.apiClient.getIncomingRequests(widget.accessToken);
      } else if (_section == 2) {
        List<Map<String, dynamic>> agencies = const [];
        try {
          _agency = await widget.apiClient.getMyAgency(widget.accessToken);
        } on JehoApiException {
          _agency = null;
        }
        agencies = await widget.apiClient.getAgencies();
        _items = agencies;
      } else {
        _cp = await widget.apiClient.getCpStatus(widget.accessToken);
        _items = await widget.apiClient.getCpDiscover();
      }
    } on JehoApiException catch (error) {
      _error = error.message;
    } catch (_) {
      _error = 'تعذر تحميل هذا القسم.';
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _showAgencyApplication() async {
    final name = TextEditingController();
    final description = TextEditingController();
    final plan = TextEditingController();
    final country = TextEditingController();
    final email = TextEditingController();
    final phone = TextEditingController();
    final experience = TextEditingController();
    final hosts = TextEditingController(text: '5');
    bool accepted = false;
    try {
      final payload = await showDialog<Map<String, Object?>>(
        context: context,
        builder: (dialogContext) => StatefulBuilder(
          builder: (context, setDialogState) => AlertDialog(
            title: const Text('طلب إنشاء وكالة'),
            content: SizedBox(
              width: 440,
              child: SingleChildScrollView(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    TextField(controller: name, decoration: const InputDecoration(labelText: 'اسم الوكالة')),
                    TextField(controller: description, minLines: 2, maxLines: 4, decoration: const InputDecoration(labelText: 'وصف الوكالة')),
                    TextField(controller: plan, minLines: 2, maxLines: 4, decoration: const InputDecoration(labelText: 'خطة العمل')),
                    TextField(controller: country, decoration: const InputDecoration(labelText: 'الدولة')),
                    TextField(controller: email, keyboardType: TextInputType.emailAddress, decoration: const InputDecoration(labelText: 'البريد الإلكتروني للتواصل')),
                    TextField(controller: phone, keyboardType: TextInputType.phone, decoration: const InputDecoration(labelText: 'رقم الهاتف')),
                    TextField(controller: experience, minLines: 2, maxLines: 4, decoration: const InputDecoration(labelText: 'الخبرة السابقة')),
                    TextField(controller: hosts, keyboardType: TextInputType.number, decoration: const InputDecoration(labelText: 'عدد المضيفين المتوقع')),
                    CheckboxListTile(
                      contentPadding: EdgeInsets.zero,
                      value: accepted,
                      onChanged: (value) => setDialogState(() => accepted = value ?? false),
                      title: const Text('أوافق على الشروط'),
                      controlAffinity: ListTileControlAffinity.leading,
                    ),
                    const Text('أدخل بيانات صحيحة؛ سيخضع الطلب لمراجعة إدارة التطبيق.', style: TextStyle(fontSize: 12)),
                  ],
                ),
              ),
            ),
            actions: [
              TextButton(onPressed: () => Navigator.pop(dialogContext), child: const Text('إلغاء')),
              FilledButton(
                onPressed: () => Navigator.pop(dialogContext, {
                  'proposedName': name.text.trim(),
                  'description': description.text.trim(),
                  'businessPlan': plan.text.trim(),
                  'country': country.text.trim(),
                  'contactEmail': email.text.trim(),
                  'contactPhone': phone.text.trim(),
                  'experience': experience.text.trim(),
                  'expectedHostCount': int.tryParse(hosts.text.trim()) ?? 1,
                  'documentUrls': <String>[],
                  'termsAccepted': accepted,
                }),
                child: const Text('إرسال الطلب'),
              ),
            ],
          ),
        ),
      );
      if (payload == null || !mounted) return;
      if ((payload['proposedName'] as String).length < 3 ||
          (payload['description'] as String).length < 20 ||
          (payload['businessPlan'] as String).length < 50 ||
          (payload['country'] as String).length < 2 ||
          (payload['contactEmail'] as String).isEmpty ||
          (payload['contactPhone'] as String).length < 6 ||
          (payload['experience'] as String).length < 10 ||
          payload['termsAccepted'] != true) {
        setState(() => _error = 'أكمل الحقول المطلوبة ووافق على الشروط. خطة العمل 50 حرفًا على الأقل.');
        return;
      }
      await _runAction(() async {
        await widget.apiClient.applyForAgency(widget.accessToken, payload);
      }, 'تم إرسال طلب الوكالة للمراجعة.');
    } finally {
      name.dispose();
      description.dispose();
      plan.dispose();
      country.dispose();
      email.dispose();
      phone.dispose();
      experience.dispose();
      hosts.dispose();
    }
  }

  Future<void> _runAction(Future<void> Function() action, String success) async {
    try {
      await action();
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(success)));
      await _load();
    } on JehoApiException catch (error) {
      if (mounted) setState(() => _error = error.message);
    } catch (_) {
      if (mounted) setState(() => _error = 'تعذر تنفيذ العملية.');
    }
  }

  String _label(Map<String, dynamic> item) =>
      (item['title'] ?? item['name'] ?? item['displayName'] ?? item['username'] ?? item['type'] ?? item['code'] ?? 'عنصر').toString();

  String _subtitle(Map<String, dynamic> item) =>
      (item['description'] ?? item['status'] ?? item['progressText'] ?? item['reward'] ?? '').toString();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(_titles[_section]),
        actions: [IconButton(onPressed: _loading ? null : _load, icon: const Icon(Icons.refresh_rounded))],
      ),
      body: Column(
        children: [
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
            child: Row(
              children: List.generate(_titles.length, (index) => Padding(
                padding: const EdgeInsets.symmetric(horizontal: 4),
                child: ChoiceChip(
                  selected: _section == index,
                  avatar: Icon(_icons[index], size: 18),
                  label: Text(_titles[index]),
                  onSelected: (_) {
                    setState(() => _section = index);
                    _load();
                  },
                ),
              )),
            ),
          ),
          if (_section == 2)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12),
              child: SizedBox(
                width: double.infinity,
                child: OutlinedButton.icon(
                  onPressed: _loading ? null : _showAgencyApplication,
                  icon: const Icon(Icons.add_business_rounded),
                  label: const Text('تقديم طلب إنشاء وكالة'),
                ),
              ),
            ),
          if (_section == 0)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12),
              child: SizedBox(
                width: double.infinity,
                child: OutlinedButton.icon(
                  onPressed: _loading ? null : () => _runAction(
                    () async { await widget.apiClient.dailyCheckIn(widget.accessToken); },
                    'تم تسجيل الحضور.',
                  ),
                  icon: const Icon(Icons.calendar_month_rounded),
                  label: const Text('تسجيل الحضور اليومي'),
                ),
              ),
            ),
          if (_error != null)
            Padding(padding: const EdgeInsets.all(12), child: Text(_error!, style: const TextStyle(color: Colors.red))),
          Expanded(
            child: _loading
                ? const Center(child: CircularProgressIndicator())
                : _items.isEmpty
                    ? Center(child: Padding(
                        padding: const EdgeInsets.all(24),
                        child: Text(
                          _section == 2 && _agency == null
                              ? 'لا توجد وكالة مرتبطة بحسابك. يمكنك استعراض الوكالات المتاحة أدناه.'
                              : 'لا توجد عناصر للعرض حاليًا.',
                          textAlign: TextAlign.center,
                        ),
                      ))
                    : ListView.separated(
                        padding: const EdgeInsets.all(12),
                        itemCount: _items.length,
                        separatorBuilder: (_, __) => const SizedBox(height: 6),
                        itemBuilder: (context, index) {
                          final item = _items[index];
                          final id = (item['id'] ?? item['taskId'] ?? '').toString();
                          final claimed = item['claimed'] == true || item['isClaimed'] == true || item['claimedAt'] != null;
                          final status = (item['status'] ?? '').toString().toLowerCase();
                          Widget? trailing;
                          if (_section == 0) {
                            trailing = FilledButton(
                              onPressed: id.isEmpty || claimed || _loading
                                  ? null
                                  : () => _runAction(
                                        () async { await widget.apiClient.claimTask(widget.accessToken, id); },
                                        'تم استلام المكافأة.',
                                      ),
                              child: Text(claimed ? 'تم الاستلام' : 'استلام'),
                            );
                          } else if (_section == 1) {
                            trailing = Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                IconButton(
                                  tooltip: 'قبول',
                                  onPressed: id.isEmpty || _loading ? null : () => _runAction(
                                    () async { await widget.apiClient.respondSocialRequest(widget.accessToken, id, accept: true); },
                                    'تم قبول الطلب.',
                                  ),
                                  icon: const Icon(Icons.check_circle_outline, color: Colors.green),
                                ),
                                IconButton(
                                  tooltip: 'رفض',
                                  onPressed: id.isEmpty || _loading ? null : () => _runAction(
                                    () async { await widget.apiClient.respondSocialRequest(widget.accessToken, id, accept: false); },
                                    'تم رفض الطلب.',
                                  ),
                                  icon: const Icon(Icons.cancel_outlined, color: Colors.red),
                                ),
                              ],
                            );
                          }
                          final subtitle = _subtitle(item);
                          return Card(
                            child: ListTile(
                              leading: CircleAvatar(child: Icon(_icons[_section])),
                              title: Text(_label(item), maxLines: 1, overflow: TextOverflow.ellipsis),
                              subtitle: subtitle.isEmpty
                                  ? (status.isEmpty ? null : Text(status))
                                  : Text(subtitle, maxLines: 2, overflow: TextOverflow.ellipsis),
                              trailing: trailing,
                            ),
                          );
                        },
                      ),
          ),
          if (_section == 3 && _cp != null)
            Padding(
              padding: const EdgeInsets.all(10),
              child: Text('حالة CP: ${_cp!['status'] ?? _cp!['type'] ?? 'تم تحميل البيانات'}'),
            ),
        ],
      ),
    );
  }
}
