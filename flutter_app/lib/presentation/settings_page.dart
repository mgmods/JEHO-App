import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../data/jeho_api_client.dart';
import 'feature_pages.dart';
import 'feature_hub_page.dart';

const _settingsGold = Color(0xFF9A7026);

class SettingsPage extends StatelessWidget {
  const SettingsPage({required this.apiClient, required this.accessToken, super.key});
  final JehoApiClient apiClient;
  final String accessToken;

  void _open(BuildContext context, Widget page) => Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => page));

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('الإعدادات')),
    body: ListView(padding: const EdgeInsets.all(16), children: [
      Container(
        padding: const EdgeInsets.all(18),
        decoration: BoxDecoration(
          gradient: const LinearGradient(colors: [Color(0xFFFFE8AE), Color(0xFFFFF8E8)]),
          borderRadius: BorderRadius.circular(20),
          border: Border.all(color: const Color(0xFFE8D4A5)),
        ),
        child: const Row(children: [
          Icon(Icons.settings_suggest_rounded, size: 34, color: _settingsGold),
          SizedBox(width: 12),
          Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text('إعدادات حساب JEHO', style: TextStyle(fontWeight: FontWeight.w900, fontSize: 18, color: Color(0xFF553B0E))),
            SizedBox(height: 4),
            Text('الحساب والأمان والخصوصية واللغة والدعم والمتجر', style: TextStyle(height: 1.4, color: Color(0xFF6B552D))),
          ])),
        ]),
      ),
      const SizedBox(height: 14),
      _SettingsTile(icon: Icons.person_outline_rounded, title: 'الحساب والملف الشخصي', subtitle: 'تحديث الاسم والنبذة وبيانات الحساب', onTap: () => _open(context, _AccountSettingsPage(apiClient: apiClient, token: accessToken))),
      _SettingsTile(icon: Icons.shield_outlined, title: 'الأمان وكلمة المرور', subtitle: 'تغيير كلمة المرور مع التحقق من الحالية', onTap: () => _open(context, _SecuritySettingsPage(apiClient: apiClient, token: accessToken))),
      _SettingsTile(icon: Icons.privacy_tip_outlined, title: 'الخصوصية والرسائل', subtitle: 'الظهور وحظر الرسائل من الغرباء وبوابة هدايا الرسائل', onTap: () => _open(context, _PrivacySettingsPage(apiClient: apiClient, token: accessToken))),
      _SettingsTile(icon: Icons.language_rounded, title: 'اللغة', subtitle: 'اختيار اللغة المفضلة وحفظ الاختيار على الجهاز', onTap: () => _open(context, _LanguageSettingsPage(apiClient: apiClient))),
      _SettingsTile(icon: Icons.support_agent_rounded, title: 'الدعم الفني', subtitle: 'قنوات الدعم التي يحددها الخادم', onTap: () => _open(context, _SupportSettingsPage(apiClient: apiClient))),
      _SettingsTile(icon: Icons.workspace_premium_rounded, title: 'المتجر وعضوية VIP', subtitle: 'عرض خطط VIP وشراء العضوية من رصيد الحساب', onTap: () => _open(context, StoreHubPage(apiClient: apiClient, accessToken: accessToken))),
      _SettingsTile(icon: Icons.card_giftcard_rounded, title: 'كتالوج الهدايا', subtitle: 'عرض الهدايا وأسعارها من الخادم', onTap: () => _open(context, GiftCatalogPage(apiClient: apiClient, accessToken: accessToken))),
      _SettingsTile(icon: Icons.history_rounded, title: 'سجل الهدايا', subtitle: 'الهدايا المرسلة وسجلها', onTap: () => _open(context, GiftHistoryPage(apiClient: apiClient, accessToken: accessToken))),
      const Card(child: ListTile(leading: Icon(Icons.mic_off_outlined), title: Text('الصوت واتصال الغرف'), subtitle: Text('لم يتم تغيير ZEGO أو إعدادات الصوت أو اتصال الغرف أو Redis ضمن هذه المهمة.'))),
    ]),
  );
}

class _SettingsTile extends StatelessWidget {
  const _SettingsTile({required this.icon, required this.title, required this.subtitle, required this.onTap});
  final IconData icon;
  final String title;
  final String subtitle;
  final VoidCallback onTap;
  @override
  Widget build(BuildContext context) => Card(
    margin: const EdgeInsets.only(bottom: 8),
    child: ListTile(
      leading: Container(width: 44, height: 44, decoration: BoxDecoration(color: const Color(0xFFFFF2D2), borderRadius: BorderRadius.circular(13)), child: Icon(icon, color: _settingsGold)),
      title: Text(title, style: const TextStyle(fontWeight: FontWeight.w800)),
      subtitle: Text(subtitle),
      trailing: const Icon(Icons.chevron_right_rounded),
      onTap: onTap,
    ),
  );
}

class _AccountSettingsPage extends StatefulWidget {
  const _AccountSettingsPage({required this.apiClient, required this.token});
  final JehoApiClient apiClient;
  final String token;
  @override
  State<_AccountSettingsPage> createState() => _AccountSettingsPageState();
}
class _AccountSettingsPageState extends State<_AccountSettingsPage> {
  final _name = TextEditingController();
  final _bio = TextEditingController();
  Map<String, dynamic> _profile = {};
  bool _loading = true, _saving = false;
  String? _error;
  @override
  void initState() { super.initState(); _load(); }
  @override
  void dispose() { _name.dispose(); _bio.dispose(); super.dispose(); }
  Future<void> _load() async {
    setState(() { _loading = true; _error = null; });
    try {
      final p = await widget.apiClient.getMyProfile(widget.token);
      if (!mounted) return;
      setState(() { _profile = p; _name.text = (p['displayName'] ?? p['username'] ?? '').toString(); _bio.text = (p['bio'] ?? p['about'] ?? '').toString(); _loading = false; });
    } catch (e) { if (mounted) setState(() { _error = e.toString(); _loading = false; }); }
  }
  Future<void> _save() async {
    if (_name.text.trim().isEmpty) { _message('الاسم مطلوب.'); return; }
    setState(() => _saving = true);
    try {
      await widget.apiClient.updateMyProfile(widget.token, {'displayName': _name.text.trim(), 'bio': _bio.text.trim()});
      if (!mounted) return;
      setState(() => _saving = false);
      _message('تم حفظ بيانات الملف الشخصي.');
      await _load();
    } catch (e) { if (mounted) { setState(() => _saving = false); _message('تعذر الحفظ: $e'); } }
  }
  void _message(String value) { if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(value))); }
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('الحساب والملف الشخصي')),
    body: _loading ? const Center(child: CircularProgressIndicator()) : _error != null
      ? _ErrorPanel(message: _error!, retry: _load)
      : ListView(padding: const EdgeInsets.all(16), children: [
        Card(child: ListTile(leading: const Icon(Icons.badge_outlined), title: Text((_profile['displayName'] ?? _profile['username'] ?? 'حساب JEHO').toString()), subtitle: Text('Public ID: '+(_profile['publicId'] ?? _profile['publicID'] ?? '—').toString()))),
        const SizedBox(height: 12),
        TextField(controller: _name, maxLength: 60, decoration: const InputDecoration(labelText: 'الاسم الظاهر', border: OutlineInputBorder())),
        const SizedBox(height: 12),
        TextField(controller: _bio, maxLength: 240, minLines: 3, maxLines: 5, decoration: const InputDecoration(labelText: 'النبذة الشخصية', border: OutlineInputBorder())),
        const SizedBox(height: 12),
        FilledButton.icon(onPressed: _saving ? null : _save, icon: const Icon(Icons.save_rounded), label: Text(_saving ? 'جارٍ الحفظ…' : 'حفظ التعديلات')),
      ]),
  );
}

class _PrivacySettingsPage extends StatefulWidget {
  const _PrivacySettingsPage({required this.apiClient, required this.token});
  final JehoApiClient apiClient;
  final String token;
  @override
  State<_PrivacySettingsPage> createState() => _PrivacySettingsPageState();
}
class _PrivacySettingsPageState extends State<_PrivacySettingsPage> {
  bool _loading = true, _saving = false, _showOnline = true, _allowDm = true, _giftGate = false;
  String? _giftId, _error;
  List<Map<String, dynamic>> _gifts = [];
  @override
  void initState() { super.initState(); _load(); }
  Future<void> _load() async {
    setState(() { _loading = true; _error = null; });
    try {
      final results = await Future.wait([widget.apiClient.getMyProfile(widget.token), widget.apiClient.getGiftCatalog()]);
      final p = results[0] as Map<String, dynamic>;
      if (!mounted) return;
      setState(() {
        _showOnline = p['showOnlineStatus'] is bool ? p['showOnlineStatus'] as bool : true;
        _allowDm = p['allowDmFromStrangers'] is bool ? p['allowDmFromStrangers'] as bool : true;
        _giftGate = p['dmGiftGateEnabled'] == true;
        _giftId = p['dmRequiredGiftId']?.toString();
        _gifts = (results[1] as List<Map<String, dynamic>>);
        _loading = false;
      });
    } catch (e) { if (mounted) setState(() { _error = e.toString(); _loading = false; }); }
  }
  Future<void> _save() async {
    setState(() => _saving = true);
    try {
      await widget.apiClient.updateMyProfile(widget.token, {'showOnlineStatus': _showOnline, 'allowDmFromStrangers': _allowDm, 'dmGiftGateEnabled': _giftGate, 'dmRequiredGiftId': _giftGate ? _giftId : null});
      if (!mounted) return;
      setState(() => _saving = false);
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('تم حفظ إعدادات الخصوصية.')));
    } catch (e) { if (mounted) { setState(() => _saving = false); ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('تعذر حفظ الإعدادات: $e'))); } }
  }
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('الخصوصية والرسائل')),
    body: _loading ? const Center(child: CircularProgressIndicator()) : _error != null
      ? _ErrorPanel(message: _error!, retry: _load)
      : ListView(padding: const EdgeInsets.all(16), children: [
        SwitchListTile(value: _showOnline, onChanged: _saving ? null : (v) => setState(() => _showOnline = v), title: const Text('إظهار حالة الاتصال'), subtitle: const Text('السماح للآخرين برؤية أنك متصل')),
        const Divider(),
        SwitchListTile(value: _allowDm, onChanged: _saving ? null : (v) => setState(() => _allowDm = v), title: const Text('السماح برسائل الغرباء'), subtitle: const Text('إيقافه يقيّد الرسائل الواردة من أشخاص غير معروفين')),
        const Divider(),
        SwitchListTile(value: _giftGate, onChanged: _saving ? null : (v) => setState(() => _giftGate = v), title: const Text('اشتراط هدية لرسائل الخاص'), subtitle: const Text('تفعيل بوابة الهدية للرسائل الخاصة')),
        if (_giftGate) Padding(padding: const EdgeInsets.symmetric(vertical: 12), child: DropdownButtonFormField<String>(
          value: _gifts.any((g) => (g['id'] ?? '').toString() == _giftId) ? _giftId : '',
          decoration: const InputDecoration(labelText: 'الهدية المطلوبة', border: OutlineInputBorder()),
          items: [const DropdownMenuItem(value: '', child: Text('أي هدية')), ..._gifts.map((g) => DropdownMenuItem(value: (g['id'] ?? '').toString(), child: Text((g['name'] ?? g['title'] ?? 'هدية').toString()+' · '+(g['coinPrice'] ?? g['price'] ?? '?').toString())))],
          onChanged: _saving ? null : (v) => setState(() => _giftId = (v == null || v.isEmpty) ? null : v),
        )),
        const SizedBox(height: 14),
        FilledButton.icon(onPressed: _saving ? null : _save, icon: const Icon(Icons.save_rounded), label: Text(_saving ? 'جارٍ الحفظ…' : 'حفظ الخصوصية')),
      ]),
  );
}

class _SecuritySettingsPage extends StatefulWidget {
  const _SecuritySettingsPage({required this.apiClient, required this.token});
  final JehoApiClient apiClient;
  final String token;
  @override
  State<_SecuritySettingsPage> createState() => _SecuritySettingsPageState();
}
class _SecuritySettingsPageState extends State<_SecuritySettingsPage> {
  final _current = TextEditingController(), _next = TextEditingController(), _confirm = TextEditingController();
  bool _busy = false, _hide1 = true, _hide2 = true, _hide3 = true;
  @override
  void dispose() { _current.dispose(); _next.dispose(); _confirm.dispose(); super.dispose(); }
  Future<void> _save() async {
    if (_current.text.length < 6 || _next.text.length < 6) { _msg('كلمة المرور يجب أن تتكون من 6 أحرف على الأقل.'); return; }
    if (_next.text != _confirm.text) { _msg('تأكيد كلمة المرور الجديدة غير متطابق.'); return; }
    setState(() => _busy = true);
    try {
      await widget.apiClient.changePassword(widget.token, currentPassword: _current.text, newPassword: _next.text);
      if (!mounted) return;
      setState(() => _busy = false);
      _current.clear(); _next.clear(); _confirm.clear();
      _msg('تم إرسال تغيير كلمة المرور بنجاح.');
    } catch (e) { if (mounted) { setState(() => _busy = false); _msg('تعذر تغيير كلمة المرور: $e'); } }
  }
  void _msg(String text) { if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text))); }
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('الأمان وكلمة المرور')),
    body: ListView(padding: const EdgeInsets.all(16), children: [
      const ListTile(leading: Icon(Icons.lock_outline_rounded, color: _settingsGold), title: Text('تغيير كلمة المرور'), subtitle: Text('لن تتغير كلمة المرور إلا بعد قبول الخادم للطلب.')),
      TextField(controller: _current, obscureText: _hide1, decoration: InputDecoration(labelText: 'كلمة المرور الحالية', border: const OutlineInputBorder(), suffixIcon: IconButton(onPressed: () => setState(() => _hide1 = !_hide1), icon: Icon(_hide1 ? Icons.visibility : Icons.visibility_off)))),
      const SizedBox(height: 12),
      TextField(controller: _next, obscureText: _hide2, decoration: InputDecoration(labelText: 'كلمة المرور الجديدة', border: const OutlineInputBorder(), suffixIcon: IconButton(onPressed: () => setState(() => _hide2 = !_hide2), icon: Icon(_hide2 ? Icons.visibility : Icons.visibility_off)))),
      const SizedBox(height: 12),
      TextField(controller: _confirm, obscureText: _hide3, decoration: InputDecoration(labelText: 'تأكيد كلمة المرور الجديدة', border: const OutlineInputBorder(), suffixIcon: IconButton(onPressed: () => setState(() => _hide3 = !_hide3), icon: Icon(_hide3 ? Icons.visibility : Icons.visibility_off)))),
      const SizedBox(height: 16),
      FilledButton.icon(onPressed: _busy ? null : _save, icon: const Icon(Icons.verified_user_outlined), label: Text(_busy ? 'جارٍ التحقق…' : 'تغيير كلمة المرور')),
    ]),
  );
}

class _LanguageSettingsPage extends StatefulWidget {
  const _LanguageSettingsPage({required this.apiClient});
  final JehoApiClient apiClient;
  @override
  State<_LanguageSettingsPage> createState() => _LanguageSettingsPageState();
}
class _LanguageSettingsPageState extends State<_LanguageSettingsPage> {
  static const _options = {'ar': 'العربية', 'en': 'English', 'tr': 'Türkçe', 'fr': 'Français', 'de': 'Deutsch', 'es': 'Español', 'ru': 'Русский', 'pt-BR': 'Português', 'id': 'Bahasa Indonesia', 'hi': 'हिन्दी', 'ur': 'اردو', 'zh-CN': '中文'};
  String _language = 'ar';
  bool _loading = true;
  @override
  void initState() { super.initState(); _load(); }
  Future<void> _load() async { final v = await widget.apiClient.readLocalPreference('language'); if (mounted) setState(() { _language = _options.containsKey(v) ? v! : 'ar'; _loading = false; }); }
  Future<void> _save(String? v) async { if (v == null) return; await widget.apiClient.writeLocalPreference('language', v); if (mounted) setState(() => _language = v); }
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('اللغة')),
    body: _loading ? const Center(child: CircularProgressIndicator()) : ListView(padding: const EdgeInsets.all(16), children: [
      const ListTile(leading: Icon(Icons.translate_rounded, color: _settingsGold), title: Text('اللغة المفضلة'), subtitle: Text('يُحفظ اختيارك محليًا على هذا الجهاز. تغيير لغة جميع الشاشات يتطلب استكمال موارد الترجمة داخل التطبيق.')),
      ..._options.entries.map((e) => RadioListTile<String>(value: e.key, groupValue: _language, title: Text(e.value), onChanged: _save)),
      const SizedBox(height: 10),
      FilledButton.icon(onPressed: () => ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('تم حفظ اللغة المفضلة.'))), icon: const Icon(Icons.check_rounded), label: const Text('تم')),
    ]),
  );
}

class _SupportSettingsPage extends StatefulWidget {
  const _SupportSettingsPage({required this.apiClient});
  final JehoApiClient apiClient;
  @override
  State<_SupportSettingsPage> createState() => _SupportSettingsPageState();
}
class _SupportSettingsPageState extends State<_SupportSettingsPage> {
  bool _loading = true;
  String? _error;
  Map<String, dynamic> _config = {};
  @override
  void initState() { super.initState(); _load(); }
  Future<void> _load() async {
    setState(() { _loading = true; _error = null; });
    try { final v = await widget.apiClient.getSupportConfig(); if (mounted) setState(() { _config = v; _loading = false; }); }
    catch (e) { if (mounted) setState(() { _error = e.toString(); _loading = false; }); }
  }
  Future<void> _open(String key, String scheme) async {
    final raw = (_config[key] ?? '').toString().trim();
    if (raw.isEmpty) return;
    Uri? uri;
    if (key == 'email') { uri = Uri(scheme: 'mailto', path: raw); }
    else if (key == 'phone') { uri = Uri(scheme: 'tel', path: raw); }
    else if (key == 'whatsapp') { final digits = raw.replaceAll(RegExp(r'[^0-9+]'), '').replaceFirst(RegExp(r'^\+'), ''); if (digits.isNotEmpty) uri = Uri.parse('https://wa.me/$digits'); }
    else if (raw.startsWith('https://')) { uri = Uri.tryParse(raw); }
    else if (key == 'telegram') { uri = Uri.parse('https://t.me/'+raw.replaceFirst(RegExp(r'^@'), '')); }
    else if (key == 'instagram') { uri = Uri.parse('https://instagram.com/'+raw.replaceFirst(RegExp(r'^@'), '')); }
    if (uri != null && await canLaunchUrl(uri)) await launchUrl(uri, mode: LaunchMode.externalApplication);
    else if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('تعذر فتح قناة الدعم أو أن بياناتها غير متاحة.')));
  }
  @override
  Widget build(BuildContext context) {
    const channels = [('whatsapp', 'واتساب', Icons.chat_rounded), ('telegram', 'تيليغرام', Icons.send_rounded), ('instagram', 'إنستغرام', Icons.camera_alt_outlined), ('email', 'البريد الإلكتروني', Icons.email_outlined), ('phone', 'اتصال هاتفي', Icons.phone_outlined)];
    return Scaffold(appBar: AppBar(title: const Text('الدعم الفني')), body: _loading ? const Center(child: CircularProgressIndicator()) : _error != null ? _ErrorPanel(message: _error!, retry: _load) : ListView(padding: const EdgeInsets.all(16), children: [
      const Text('قنوات التواصل التالية تأتي من إعدادات الخادم، ولا نعرض بيانات اتصال مخمّنة.'),
      const SizedBox(height: 12),
      ...channels.where((c) => (_config[c.$1] ?? '').toString().trim().isNotEmpty).map((c) => Card(child: ListTile(leading: Icon(c.$3, color: _settingsGold), title: Text(c.$2), subtitle: Text(_config[c.$1].toString()), trailing: const Icon(Icons.open_in_new_rounded), onTap: () => _open(c.$1, '')))),
      if (channels.every((c) => (_config[c.$1] ?? '').toString().trim().isEmpty)) const Center(child: Padding(padding: EdgeInsets.all(24), child: Text('لم يضبط الخادم قنوات دعم حتى الآن.'))),
    ]));
  }
}

class _StoreSettingsPage extends StatefulWidget {
  const _StoreSettingsPage({required this.apiClient, required this.token});
  final JehoApiClient apiClient;
  final String token;
  @override
  State<_StoreSettingsPage> createState() => _StoreSettingsPageState();
}
class _StoreSettingsPageState extends State<_StoreSettingsPage> {
  bool _loading = true;
  String? _error;
  List<Map<String, dynamic>> _plans = [];
  List<Map<String, dynamic>> _gifts = [];
  final Set<int> _buying = {};
  @override
  void initState() { super.initState(); _load(); }
  Future<void> _load() async {
    setState(() { _loading = true; _error = null; });
    try {
      final r = await Future.wait([widget.apiClient.getVipPlans(), widget.apiClient.getGiftCatalog()]);
      if (mounted) setState(() { _plans = r[0] as List<Map<String, dynamic>>; _gifts = r[1] as List<Map<String, dynamic>>; _loading = false; });
    } catch (e) { if (mounted) setState(() { _error = e.toString(); _loading = false; }); }
  }
  Future<void> _buy(Map<String, dynamic> plan) async {
    final level = int.tryParse((plan['level'] ?? '').toString());
    if (level == null || level < 1) { ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('خطة VIP لا تحتوي مستوى صالحًا.'))); return; }
    final price = (plan['coinPriceMonthly'] ?? plan['price'] ?? '?').toString();
    final yes = await showDialog<bool>(context: context, builder: (ctx) => AlertDialog(title: const Text('تأكيد شراء VIP'), content: Text('شراء '+(plan['name'] ?? 'VIP').toString()+' لمدة 30 يومًا مقابل '+price+' عملة؟'), actions: [TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('إلغاء')), FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('شراء'))]));
    if (yes != true) return;
    setState(() => _buying.add(level));
    try {
      await widget.apiClient.purchaseVip(widget.token, level: level, durationDays: 30);
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('قبل الخادم طلب شراء VIP.')));
    } catch (e) { if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('لم يكتمل الشراء: $e'))); }
    finally { if (mounted) setState(() => _buying.remove(level)); }
  }
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('المتجر وVIP'), actions: [IconButton(onPressed: _loading ? null : _load, icon: const Icon(Icons.refresh_rounded))]),
    body: _loading ? const Center(child: CircularProgressIndicator()) : _error != null ? _ErrorPanel(message: _error!, retry: _load) : ListView(padding: const EdgeInsets.all(16), children: [
      const Text('عضويات VIP', style: TextStyle(fontWeight: FontWeight.w900, fontSize: 19)),
      const SizedBox(height: 8),
      if (_plans.isEmpty) const Text('لا توجد خطط VIP متاحة حاليًا.'),
      ..._plans.map((p) {
        final level = int.tryParse((p['level'] ?? '').toString()) ?? -1;
        return Card(child: ListTile(
          leading: const CircleAvatar(backgroundColor: Color(0xFFFFF0CC), child: Icon(Icons.workspace_premium_rounded, color: _settingsGold)),
          title: Text((p['name'] ?? 'VIP '+(p['level'] ?? '').toString()).toString()),
          subtitle: Text('المستوى: '+(p['level'] ?? '—').toString()+' · السعر الشهري: '+(p['coinPriceMonthly'] ?? p['price'] ?? '—').toString()+' عملة'),
          trailing: FilledButton(onPressed: _buying.contains(level) ? null : () => _buy(p), child: Text(_buying.contains(level) ? 'جارٍ…' : 'شراء')),
        ));
      }),
      const Divider(height: 28),
      const Text('كتالوج الهدايا', style: TextStyle(fontWeight: FontWeight.w900, fontSize: 19)),
      const SizedBox(height: 8),
      if (_gifts.isEmpty) const Text('لا توجد هدايا متاحة حاليًا.'),
      ..._gifts.take(60).map((g) => Card(child: ListTile(
        leading: const CircleAvatar(backgroundColor: Color(0xFFFFF0CC), child: Icon(Icons.card_giftcard_rounded, color: _settingsGold)),
        title: Text((g['name'] ?? g['title'] ?? 'هدية').toString()),
        subtitle: Text('السعر: '+(g['coinPrice'] ?? g['price'] ?? '—').toString()+' عملة'),
      ))),
    ]),
  );
}

class _ErrorPanel extends StatelessWidget {
  const _ErrorPanel({required this.message, required this.retry});
  final String message;
  final VoidCallback retry;
  @override
  Widget build(BuildContext context) => Center(child: Padding(padding: const EdgeInsets.all(24), child: Column(mainAxisSize: MainAxisSize.min, children: [
    const Icon(Icons.cloud_off_rounded, size: 42),
    const SizedBox(height: 12),
    Text(message, textAlign: TextAlign.center),
    const SizedBox(height: 12),
    FilledButton.icon(onPressed: retry, icon: const Icon(Icons.refresh_rounded), label: const Text('إعادة المحاولة')),
  ])));
}
