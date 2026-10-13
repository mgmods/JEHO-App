import 'dart:io';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../data/jeho_api_client.dart';

const _featureGold = Color(0xFFB68A35);

class RoomDetailPage extends StatefulWidget {
  const RoomDetailPage({
    required this.apiClient,
    required this.accessToken,
    required this.room,
    super.key,
  });

  final JehoApiClient apiClient;
  final String accessToken;
  final Map<String, dynamic> room;

  @override
  State<RoomDetailPage> createState() => _RoomDetailPageState();
}

class _RoomDetailPageState extends State<RoomDetailPage> {
  final _picker = ImagePicker();
  bool _uploading = false;
  String? _backgroundUrl;
  String? _error;

  String get _roomId => (widget.room['id'] ?? widget.room['roomId'] ?? '').toString();
  String get _title => (widget.room['title'] ?? widget.room['name'] ?? 'غرفة JEHO').toString();

  @override
  void initState() {
    super.initState();
    _backgroundUrl = (widget.room['backgroundUrl'] ?? widget.room['background'] ?? '').toString();
    if (_backgroundUrl?.isEmpty ?? true) _backgroundUrl = null;
  }

  Future<void> _chooseBackground() async {
    if (_uploading) return;
    if (_roomId.isEmpty || widget.accessToken.isEmpty) {
      setState(() => _error = 'بيانات الغرفة أو تسجيل الدخول غير مكتملة.');
      return;
    }
    try {
      final picked = await _picker.pickImage(
        source: ImageSource.gallery,
        imageQuality: 88,
        maxWidth: 1920,
        maxHeight: 1920,
      );
      if (picked == null || !mounted) return;
      setState(() {
        _uploading = true;
        _error = null;
      });
      final url = await widget.apiClient.uploadImage(widget.accessToken, File(picked.path));
      await widget.apiClient.setRoomBackground(widget.accessToken, _roomId, url);
      if (!mounted) return;
      setState(() => _backgroundUrl = url);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('تم حفظ خلفية الغرفة.')),
      );
    } on JehoApiException catch (error) {
      if (mounted) setState(() => _error = error.message);
    } catch (_) {
      if (mounted) setState(() => _error = 'تعذر اختيار الصورة أو حفظها. حاول مرة أخرى.');
    } finally {
      if (mounted) setState(() => _uploading = false);
    }
  }

  Future<void> _removeBackground() async {
    if (_uploading || _roomId.isEmpty) return;
    setState(() {
      _uploading = true;
      _error = null;
    });
    try {
      await widget.apiClient.setRoomBackground(widget.accessToken, _roomId, null);
      if (mounted) setState(() => _backgroundUrl = null);
    } on JehoApiException catch (error) {
      if (mounted) setState(() => _error = error.message);
    } finally {
      if (mounted) setState(() => _uploading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final host = widget.room['host'] is Map<String, dynamic>
        ? widget.room['host'] as Map<String, dynamic>
        : <String, dynamic>{};
    final imageUrl = _backgroundUrl;
    return Scaffold(
      appBar: AppBar(title: Text(_title, maxLines: 1, overflow: TextOverflow.ellipsis)),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(20),
            child: AspectRatio(
              aspectRatio: 16 / 9,
              child: imageUrl == null
                  ? Container(
                      color: const Color(0xFFFFE7B0),
                      child: const Icon(Icons.forum_rounded, size: 64, color: Color(0xFF8C641D)),
                    )
                  : Image.network(
                      imageUrl,
                      fit: BoxFit.cover,
                      errorBuilder: (_, __, ___) => Container(
                        color: const Color(0xFFFFE7B0),
                        child: const Icon(Icons.broken_image_outlined, size: 48),
                      ),
                    ),
            ),
          ),
          const SizedBox(height: 16),
          Text(_title, style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w800)),
          const SizedBox(height: 6),
          Text('المضيف: ${host['displayName'] ?? host['username'] ?? 'غير معروف'}'),
          const SizedBox(height: 8),
          Text((widget.room['description'] ?? widget.room['topic'] ?? '').toString()),
          const SizedBox(height: 20),
          FilledButton.icon(
            onPressed: _uploading ? null : _chooseBackground,
            icon: _uploading
                ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
                : const Icon(Icons.wallpaper_rounded),
            label: Text(_uploading ? 'جارٍ رفع الصورة وحفظها…' : 'تغيير خلفية الغرفة من الهاتف'),
          ),
          if (imageUrl != null)
            OutlinedButton.icon(
              onPressed: _uploading ? null : _removeBackground,
              icon: const Icon(Icons.delete_outline_rounded),
              label: const Text('إزالة الخلفية المخصصة'),
            ),
          if (_error != null) ...[
            const SizedBox(height: 8),
            Text(_error!, style: const TextStyle(color: Colors.red)),
          ],
          const SizedBox(height: 12),
          const Text(
            'تغيير الخلفية يستخدم صلاحيات الخادم الحالية. إذا لم تكن مالك الغرفة أو مشرفًا مخولًا، سيرفض الخادم التغيير ولن تُحفظ الصورة كخلفية.',
            style: TextStyle(height: 1.5, color: Colors.black54),
          ),
        ],
      ),
    );
  }
}

class WalletPage extends StatefulWidget {
  const WalletPage({required this.apiClient, required this.accessToken, super.key});

  final JehoApiClient apiClient;
  final String accessToken;

  @override
  State<WalletPage> createState() => _WalletPageState();
}

class _WalletPageState extends State<WalletPage> {
  Map<String, dynamic>? _wallet;
  bool _loading = true;
  String? _error;

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
      final result = await widget.apiClient.getWallet(widget.accessToken);
      if (mounted) setState(() => _wallet = result);
    } on JehoApiException catch (error) {
      if (mounted) setState(() => _error = error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final data = _wallet ?? <String, dynamic>{};
    final balances = data['balances'] is Map<String, dynamic>
        ? data['balances'] as Map<String, dynamic>
        : data;
    return Scaffold(
      appBar: AppBar(
        title: const Text('المحفظة'),
        actions: [IconButton(onPressed: _load, icon: const Icon(Icons.refresh_rounded))],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(child: Padding(padding: const EdgeInsets.all(24), child: Text(_error!, textAlign: TextAlign.center)))
              : ListView(
                  padding: const EdgeInsets.all(16),
                  children: [
                    _BalanceTile(label: 'الماس', value: balances['diamonds'] ?? balances['diamondBalance'] ?? '—', icon: Icons.diamond_rounded),
                    _BalanceTile(label: 'عملات', value: balances['coins'] ?? balances['coinBalance'] ?? '—', icon: Icons.monetization_on_rounded),
                    _BalanceTile(label: 'رصيد إضافي', value: balances['balance'] ?? balances['credits'] ?? '—', icon: Icons.account_balance_wallet_rounded),
                    const SizedBox(height: 12),
                    const Text('الرصيد معروض من الخادم الحالي. عمليات الشحن والسحب والتبديل لم تُفعّل من هذه الشاشة بعد.'),
                  ],
                ),
    );
  }
}

class _BalanceTile extends StatelessWidget {
  const _BalanceTile({required this.label, required this.value, required this.icon});

  final String label;
  final dynamic value;
  final IconData icon;

  @override
  Widget build(BuildContext context) => Card(
        child: ListTile(
          leading: CircleAvatar(backgroundColor: const Color(0xFFFFE7B0), child: Icon(icon, color: const Color(0xFF8C641D))),
          title: Text(label),
          trailing: Text('$value', style: const TextStyle(fontWeight: FontWeight.w800, color: _featureGold, fontSize: 18)),
        ),
      );
}

class GiftCatalogPage extends StatefulWidget {
  const GiftCatalogPage({required this.apiClient, super.key});

  final JehoApiClient apiClient;

  @override
  State<GiftCatalogPage> createState() => _GiftCatalogPageState();
}

class _GiftCatalogPageState extends State<GiftCatalogPage> {
  List<Map<String, dynamic>> _gifts = const [];
  bool _loading = true;
  String? _error;

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
      final gifts = await widget.apiClient.getGiftCatalog();
      if (mounted) setState(() => _gifts = gifts);
    } on JehoApiException catch (error) {
      if (mounted) setState(() => _error = error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: const Text('الهدايا'), actions: [IconButton(onPressed: _load, icon: const Icon(Icons.refresh_rounded))]),
        body: _loading
            ? const Center(child: CircularProgressIndicator())
            : _error != null
                ? Center(child: Padding(padding: const EdgeInsets.all(24), child: Text(_error!, textAlign: TextAlign.center)))
                : _gifts.isEmpty
                    ? const Center(child: Text('لا توجد هدايا متاحة حاليًا.'))
                    : GridView.builder(
                        padding: const EdgeInsets.all(12),
                        gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(crossAxisCount: 2, childAspectRatio: .86, crossAxisSpacing: 10, mainAxisSpacing: 10),
                        itemCount: _gifts.length,
                        itemBuilder: (context, index) {
                          final gift = _gifts[index];
                          final title = (gift['name'] ?? gift['title'] ?? 'هدية').toString();
                          final cost = gift['price'] ?? gift['coinCost'] ?? gift['cost'] ?? '—';
                          final image = (gift['imageUrl'] ?? gift['image'] ?? gift['mediaUrl'] ?? '').toString();
                          return Card(
                            clipBehavior: Clip.antiAlias,
                            child: Padding(
                              padding: const EdgeInsets.all(10),
                              child: Column(
                                children: [
                                  Expanded(
                                    child: image.startsWith('http')
                                        ? Image.network(image, fit: BoxFit.contain, errorBuilder: (_, __, ___) => const Icon(Icons.card_giftcard_rounded, size: 52))
                                        : const Icon(Icons.card_giftcard_rounded, size: 52, color: _featureGold),
                                  ),
                                  const SizedBox(height: 8),
                                  Text(title, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w700)),
                                  const SizedBox(height: 4),
                                  Text('السعر: $cost'),
                                ],
                              ),
                            ),
                          );
                        },
                      ),
      );
}
