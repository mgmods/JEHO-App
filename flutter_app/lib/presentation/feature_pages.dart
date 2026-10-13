import 'dart:io';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:url_launcher/url_launcher.dart';

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
  bool _roomEntered = false;
  bool _roomActionBusy = false;
  String? _backgroundUrl;
  String? _coverUrl;
  String? _error;

  String get _roomId => (widget.room['id'] ?? widget.room['roomId'] ?? '').toString();
  String get _title => (widget.room['title'] ?? widget.room['name'] ?? 'غرفة JEHO').toString();

  @override
  void initState() {
    super.initState();
    final initialCover = (widget.room['coverUrl'] ?? '').toString();
    if (initialCover.isNotEmpty) _coverUrl = _resolveMedia(initialCover);
    final initial = (widget.room['backgroundUrl'] ?? widget.room['background'] ?? '').toString();
    if (initial.isNotEmpty) {
      if (initial.startsWith('http://') || initial.startsWith('https://')) {
        _backgroundUrl = initial;
      } else {
        final base = Uri.parse(widget.apiClient.baseUrl);
        final origin = Uri(scheme: base.scheme, host: base.host, port: base.hasPort ? base.port : null);
        _backgroundUrl = origin.resolve(initial.startsWith('/') ? initial : '/$initial').toString();
      }
    }
  }

  String _resolveMedia(String value) {
    if (value.startsWith('http://') || value.startsWith('https://')) return value;
    final base = Uri.parse(widget.apiClient.baseUrl);
    final origin = Uri(scheme: base.scheme, host: base.host, port: base.hasPort ? base.port : null);
    final normalized = value.replaceFirst(RegExp(r'^/uploads/'), 'uploads/');
    if (normalized.startsWith('uploads/')) return base.resolve(normalized).toString();
    return origin.resolve(value.startsWith('/') ? value : '/$value').toString();
  }

  Future<void> _roomAction(Future<Map<String, dynamic>> Function() action, String success) async {
    if (_roomActionBusy) return;
    setState(() { _roomActionBusy = true; _error = null; });
    try {
      await action();
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(success)));
    } on JehoApiException catch (error) {
      if (mounted) setState(() => _error = error.message);
    } finally {
      if (mounted) setState(() => _roomActionBusy = false);
    }
  }

  Future<void> _enterRoom() async {
    String? password;
    final isLocked = widget.room['isLocked'] == true ||
        widget.room['hasPassword'] == true ||
        widget.room['passwordProtected'] == true;
    if (isLocked) {
      final controller = TextEditingController();
      try {
        password = await showDialog<String>(
          context: context,
          builder: (dialogContext) => AlertDialog(
            title: const Text('الغرفة محمية بكلمة مرور'),
            content: TextField(
              controller: controller,
              obscureText: true,
              autofocus: true,
              decoration: const InputDecoration(labelText: 'كلمة المرور'),
              onSubmitted: (_) => Navigator.pop(dialogContext, controller.text),
            ),
            actions: [
              TextButton(onPressed: () => Navigator.pop(dialogContext), child: const Text('إلغاء')),
              FilledButton(onPressed: () => Navigator.pop(dialogContext, controller.text), child: const Text('دخول')),
            ],
          ),
        );
      } finally {
        controller.dispose();
      }
      if (password == null || !mounted) return;
    }
    await _roomAction(
      () => widget.apiClient.joinRoom(widget.accessToken, _roomId, password: password),
      'تم تسجيل دخولك إلى الغرفة.',
    );
    if (mounted && _error == null) setState(() => _roomEntered = true);
  }

  Future<void> _leaveRoom() async {
    await _roomAction(
      () => widget.apiClient.leaveRoom(widget.accessToken, _roomId),
      'غادرت الغرفة.',
    );
    if (mounted && _error == null) setState(() => _roomEntered = false);
  }

  Future<void> _takeSeat(int seatIndex) async {
    await _roomAction(
      () => widget.apiClient.takeSeat(widget.accessToken, _roomId, seatIndex),
      'تم تنفيذ طلب المقعد.',
    );
  }

  Future<void> _raiseHand() async {
    await _roomAction(
      () => widget.apiClient.raiseHand(widget.accessToken, _roomId),
      'تم إرسال طلب المايك للمشرف.',
    );
  }

  Future<void> _showSeatRequests() async {
    if (_roomActionBusy) return;
    try {
      final requests = await widget.apiClient.getSeatRequests(widget.accessToken, _roomId);
      if (!mounted) return;
      if (requests.isEmpty) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('لا توجد طلبات مايك معلّقة.')));
        return;
      }
      final selected = await showModalBottomSheet<Map<String, dynamic>>(
        context: context,
        isScrollControlled: true,
        builder: (sheetContext) => SafeArea(
          child: ListView(
            shrinkWrap: true,
            children: [
              const ListTile(title: Text('طلبات المايك المعلّقة')),
              for (final request in requests)
                ListTile(
                  leading: const Icon(Icons.pan_tool_alt_rounded),
                  title: Text((request['displayName'] ?? request['username'] ?? request['userId'] ?? 'مستخدم').toString()),
                  subtitle: Text((request['createdAt'] ?? request['status'] ?? '').toString()),
                  trailing: Wrap(
                    spacing: 4,
                    children: [
                      IconButton(
                        tooltip: 'قبول',
                        onPressed: () => Navigator.pop(sheetContext, {...request, '_accept': true}),
                        icon: const Icon(Icons.check_circle_outline, color: Colors.green),
                      ),
                      IconButton(
                        tooltip: 'رفض',
                        onPressed: () => Navigator.pop(sheetContext, {...request, '_accept': false}),
                        icon: const Icon(Icons.cancel_outlined, color: Colors.red),
                      ),
                    ],
                  ),
                ),
            ],
          ),
        ),
      );
      if (selected == null || !mounted) return;
      final userId = (selected['userId'] ?? selected['id'] ?? selected['user']?['id'] ?? '').toString();
      if (userId.isEmpty) {
        setState(() => _error = 'طلب المايك لا يحتوي معرّف المستخدم المطلوب.');
        return;
      }
      final accept = selected['_accept'] == true;
      await _roomAction(
        () => widget.apiClient.respondSeatRequest(
          widget.accessToken,
          _roomId,
          userId,
          accept: accept,
        ),
        accept ? 'تم قبول طلب المايك.' : 'تم رفض طلب المايك.',
      );
    } on JehoApiException catch (error) {
      if (mounted) setState(() => _error = error.message);
    }
  }

  Future<void> _showModerationTools() async {
    final userIdController = TextEditingController();
    try {
      final choice = await showDialog<String>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('إشراف الغرفة'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text('أدخل معرّف المستخدم المستهدف. صلاحيات المضيف والمشرف يتحقق منها الخادم.'),
              TextField(controller: userIdController, decoration: const InputDecoration(labelText: 'معرّف المستخدم UUID')),
            ],
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context), child: const Text('إلغاء')),
            TextButton(onPressed: () => Navigator.pop(context, 'kick'), child: const Text('إخراج')),
            FilledButton(onPressed: () => Navigator.pop(context, 'ban'), child: const Text('حظر 30 دقيقة')),
          ],
        ),
      );
      final target = userIdController.text.trim();
      if (choice == null || target.isEmpty || !mounted) return;
      await _roomAction(
        () => choice == 'kick'
            ? widget.apiClient.kickRoomUser(widget.accessToken, _roomId, target)
            : widget.apiClient.banRoomUser(widget.accessToken, _roomId, target),
        choice == 'kick' ? 'تم إرسال طلب إخراج المستخدم.' : 'تم إرسال طلب الحظر.',
      );
    } finally {
      userIdController.dispose();
    }
  }

  Future<void> _showSeatLock() async {
    final seat = await showModalBottomSheet<int>(
      context: context,
      builder: (context) => SafeArea(
        child: ListView(
          shrinkWrap: true,
          children: [
            const ListTile(title: Text('اختر المقعد لقفله أو فتحه')),
            for (var index = 0; index < ((widget.room['seatCount'] is int ? widget.room['seatCount'] as int : 11).clamp(1, 30)); index++)
              ListTile(
                leading: CircleAvatar(child: Text('$index')),
                title: Text('المقعد $index'),
                onTap: () => Navigator.pop(context, index),
              ),
          ],
        ),
      ),
    );
    if (seat == null || !mounted) return;
    final lock = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text('المقعد $seat'),
        content: const Text('اختر إذا كنت تريد قفل المقعد أو فتحه. صلاحيات المضيف والمشرف يتحقق منها الخادم.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(dialogContext), child: const Text('إلغاء')),
          TextButton(onPressed: () => Navigator.pop(dialogContext, false), child: const Text('فتح المقعد')),
          FilledButton(onPressed: () => Navigator.pop(dialogContext, true), child: const Text('قفل المقعد')),
        ],
      ),
    );
    if (lock == null || !mounted) return;
    await _roomAction(
      () => widget.apiClient.lockRoomSeat(widget.accessToken, _roomId, seat, locked: lock),
      lock ? 'تم قفل المقعد.' : 'تم فتح المقعد.',
    );
  }

  Future<void> _showRoomPasswordLock() async {
    final passwordController = TextEditingController();
    bool lock = true;
    try {
      final result = await showDialog<Map<String, Object?>>(
        context: context,
        builder: (dialogContext) => StatefulBuilder(
          builder: (context, setDialogState) => AlertDialog(
            title: const Text('قفل الغرفة بكلمة مرور'),
            content: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  value: lock,
                  onChanged: (value) => setDialogState(() => lock = value),
                  title: Text(lock ? 'تفعيل القفل' : 'إلغاء القفل'),
                ),
                if (lock)
                  TextField(
                    controller: passwordController,
                    obscureText: true,
                    decoration: const InputDecoration(labelText: 'كلمة مرور الغرفة'),
                  ),
                const SizedBox(height: 8),
                const Text('هذا يقفل دخول الغرفة بكلمة مرور؛ لا يقفل مقعدًا منفردًا.'),
              ],
            ),
            actions: [
              TextButton(onPressed: () => Navigator.pop(dialogContext), child: const Text('إلغاء')),
              FilledButton(
                onPressed: () => Navigator.pop(dialogContext, {
                  'locked': lock,
                  'password': passwordController.text.trim(),
                }),
                child: const Text('حفظ'),
              ),
            ],
          ),
        ),
      );
      if (result == null || !mounted) return;
      final shouldLock = result['locked'] == true;
      final password = (result['password'] ?? '').toString();
      if (shouldLock && password.isEmpty) {
        setState(() => _error = 'أدخل كلمة مرور لقفل الغرفة.');
        return;
      }
      await _roomAction(
        () => widget.apiClient.setRoomLock(
          widget.accessToken,
          _roomId,
          locked: shouldLock,
          password: password.isEmpty ? null : password,
        ),
        shouldLock ? 'تم تفعيل كلمة مرور الغرفة.' : 'تم إلغاء قفل الغرفة.',
      );
    } finally {
      passwordController.dispose();
    }
  }

  Future<void> _sendGiftToHost() async {
    if (_roomActionBusy) return;
    final host = widget.room['host'] is Map<String, dynamic>
        ? widget.room['host'] as Map<String, dynamic>
        : <String, dynamic>{};
    final receiverId = (host['id'] ?? widget.room['hostId'] ?? '').toString();
    if (receiverId.isEmpty || widget.accessToken.isEmpty) {
      setState(() => _error = 'لا تتوفر بيانات المضيف لإرسال الهدية.');
      return;
    }
    try {
      final gifts = await widget.apiClient.getGiftCatalog();
      if (!mounted) return;
      if (gifts.isEmpty) {
        setState(() => _error = 'كتالوج الهدايا فارغ حاليًا.');
        return;
      }
      final gift = await showModalBottomSheet<Map<String, dynamic>>(
        context: context,
        isScrollControlled: true,
        builder: (context) => SafeArea(
          child: ListView(
            shrinkWrap: true,
            children: [
              const ListTile(title: Text('اختر هدية للمضيف')),
              for (final item in gifts)
                ListTile(
                  leading: const Icon(Icons.card_giftcard_rounded, color: _featureGold),
                  title: Text((item['name'] ?? item['title'] ?? 'هدية').toString()),
                  subtitle: Text('السعر: ${item['price'] ?? item['coinCost'] ?? item['cost'] ?? '—'}'),
                  onTap: () => Navigator.pop(context, item),
                ),
            ],
          ),
        ),
      );
      if (gift == null || !mounted) return;
      final giftId = (gift['id'] ?? gift['giftId'] ?? '').toString();
      if (giftId.isEmpty) {
        setState(() => _error = 'معرّف الهدية غير موجود في بيانات الخادم.');
        return;
      }
      await _roomAction(
        () => widget.apiClient.sendGift(
          widget.accessToken,
          giftId: giftId,
          receiverId: receiverId,
          roomId: _roomId,
        ),
        'تم إرسال الهدية.',
      );
    } on JehoApiException catch (error) {
      if (mounted) setState(() => _error = error.message);
    }
  }

  Future<void> _chooseCover() async {
    if (_uploading) return;
    if (_roomId.isEmpty || widget.accessToken.isEmpty) {
      setState(() => _error = 'بيانات الغرفة أو تسجيل الدخول غير مكتملة.');
      return;
    }
    try {
      final picked = await _picker.pickImage(source: ImageSource.gallery, imageQuality: 88, maxWidth: 1920, maxHeight: 1920);
      if (picked == null || !mounted) return;
      setState(() { _uploading = true; _error = null; });
      final url = await widget.apiClient.uploadImage(widget.accessToken, File(picked.path));
      await widget.apiClient.updateRoomCover(widget.accessToken, _roomId, url);
      if (!mounted) return;
      setState(() => _coverUrl = url);
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('تم حفظ صورة الغلاف.')));
    } on JehoApiException catch (error) {
      if (mounted) setState(() => _error = error.message);
    } catch (_) {
      if (mounted) setState(() => _error = 'تعذر اختيار الغلاف أو حفظه.');
    } finally {
      if (mounted) setState(() => _uploading = false);
    }
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
    final coverUrl = _coverUrl;
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
          const SizedBox(height: 14),
          FilledButton.icon(
            onPressed: _roomActionBusy || widget.accessToken.isEmpty ? null : (_roomEntered ? _leaveRoom : _enterRoom),
            icon: _roomActionBusy
                ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
                : Icon(_roomEntered ? Icons.logout_rounded : Icons.login_rounded),
            label: Text(_roomActionBusy ? 'جارٍ التنفيذ…' : _roomEntered ? 'مغادرة الغرفة' : 'دخول الغرفة'),
          ),
          OutlinedButton.icon(
            onPressed: _roomActionBusy || !_roomEntered ? null : _raiseHand,
            icon: const Icon(Icons.pan_tool_alt_rounded),
            label: const Text('طلب المايك'),
          ),
          OutlinedButton.icon(
            onPressed: _roomActionBusy || !_roomEntered ? null : _showSeatRequests,
            icon: const Icon(Icons.how_to_reg_rounded),
            label: const Text('مراجعة طلبات المايك'),
          ),
          OutlinedButton.icon(
            onPressed: _roomActionBusy || !_roomEntered ? null : _sendGiftToHost,
            icon: const Icon(Icons.card_giftcard_rounded),
            label: const Text('إرسال هدية للمضيف'),
          ),
          OutlinedButton.icon(
            onPressed: _roomActionBusy || !_roomEntered ? null : _showModerationTools,
            icon: const Icon(Icons.admin_panel_settings_outlined),
            label: const Text('إشراف الغرفة: إخراج أو حظر'),
          ),
          OutlinedButton.icon(
            onPressed: _roomActionBusy || !_roomEntered ? null : _showSeatLock,
            icon: const Icon(Icons.event_seat_rounded),
            label: const Text('قفل أو فتح مقعد'),
          ),
          OutlinedButton.icon(
            onPressed: _roomActionBusy || !_roomEntered ? null : _showRoomPasswordLock,
            icon: const Icon(Icons.lock_outline_rounded),
            label: const Text('قفل الغرفة بكلمة مرور'),
          ),
          OutlinedButton.icon(
            onPressed: _roomActionBusy || !_roomEntered ? null : () async {
              final seat = await showModalBottomSheet<int>(
                context: context,
                builder: (context) => SafeArea(
                  child: ListView(
                    shrinkWrap: true,
                    children: [
                      const ListTile(title: Text('اختر المقعد المطلوب')),
                      for (var index = 0; index < ((widget.room['seatCount'] is int ? widget.room['seatCount'] as int : 11).clamp(1, 30)); index++)
                        ListTile(
                          leading: CircleAvatar(child: Text('$index')),
                          title: Text(index == 0 ? 'مقعد المضيف' : 'المقعد $index'),
                          onTap: () => Navigator.pop(context, index),
                        ),
                    ],
                  ),
                ),
              );
              if (seat != null && mounted) await _takeSeat(seat);
            },
            icon: const Icon(Icons.event_seat_rounded),
            label: const Text('اختيار مقعد'),
          ),
          if (coverUrl != null) ...[
            const SizedBox(height: 12),
            ClipRRect(
              borderRadius: BorderRadius.circular(14),
              child: Image.network(
                coverUrl,
                height: 110,
                width: double.infinity,
                fit: BoxFit.cover,
                errorBuilder: (_, __, ___) => const SizedBox.shrink(),
              ),
            ),
          ],
          const SizedBox(height: 20),
          OutlinedButton.icon(
            onPressed: _uploading ? null : _chooseCover,
            icon: const Icon(Icons.photo_size_select_actual_outlined),
            label: const Text('تغيير صورة غلاف الغرفة'),
          ),
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
            'الدخول والمقاعد وطلب المايك هنا تتعامل مع واجهات الغرفة فقط. لم يتم تغيير نظام الصوت أو ZEGO؛ الاتصال الصوتي الحالي يبقى كما هو.',
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
  List<Map<String, dynamic>> _transactions = const [];
  List<Map<String, dynamic>> _packages = const [];
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
      List<Map<String, dynamic>> transactions = const [];
      List<Map<String, dynamic>> packages = const [];
      try {
        transactions = await widget.apiClient.getWalletTransactions(widget.accessToken);
      } on JehoApiException {
        // Keep the balance visible if transaction history is unavailable.
      }
      try {
        packages = await widget.apiClient.getRechargePackages();
      } on JehoApiException {
        // Packages are informational until native store billing is integrated.
      }
      if (mounted) {
        setState(() {
          _wallet = result;
          _transactions = transactions;
          _packages = packages;
        });
      }
    } on JehoApiException catch (error) {
      if (mounted) setState(() => _error = error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _startCheckout(Map<String, dynamic> pkg) async {
    final sku = (pkg['sku'] ?? pkg['code'] ?? pkg['id'] ?? '').toString();
    if (sku.isEmpty) {
      setState(() => _error = 'هذه الباقة لا تحتوي على SKU صالح للدفع.');
      return;
    }
    final provider = await showModalBottomSheet<String>(
      context: context,
      builder: (context) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const ListTile(title: Text('اختر طريقة الدفع')),
            ListTile(leading: const Icon(Icons.credit_card_rounded), title: const Text('بطاقة — Stripe'), onTap: () => Navigator.pop(context, 'stripe/checkout')),
            ListTile(leading: const Icon(Icons.shopping_bag_outlined), title: const Text('بطاقة — Fourthwall'), onTap: () => Navigator.pop(context, 'fourthwall/checkout')),
            ListTile(leading: const Icon(Icons.account_balance_wallet_rounded), title: const Text('PayPal'), onTap: () => Navigator.pop(context, 'paypal/create')),
          ],
        ),
      ),
    );
    if (provider == null || !mounted) return;
    try {
      setState(() { _loading = true; _error = null; });
      final result = await widget.apiClient.createPaymentCheckout(
        widget.accessToken,
        provider,
        {'sku': sku, 'currency': 'USD'},
      );
      final checkout = (result['url'] ?? result['checkoutUrl'] ?? result['approvalUrl'] ?? result['redirectUrl'] ?? '').toString();
      if (checkout.isEmpty) {
        setState(() => _error = 'أنشأ الخادم الطلب لكن لم يرجع رابط دفع. تحقق من إعدادات مزود الدفع في الداشبورد.');
        return;
      }
      final uri = Uri.tryParse(checkout);
      if (uri == null || (uri.scheme != 'https' && uri.scheme != 'http')) {
        setState(() => _error = 'رابط الدفع الذي أعاده الخادم غير صالح.');
        return;
      }
      final opened = await launchUrl(uri, mode: LaunchMode.externalApplication);
      if (!opened && mounted) setState(() => _error = 'تعذر فتح صفحة الدفع على هذا الجهاز.');
    } on JehoApiException catch (error) {
      if (mounted) setState(() => _error = error.message);
    } catch (_) {
      if (mounted) setState(() => _error = 'تعذر بدء الدفع. تحقق من إعدادات مزود الدفع ثم حاول مجددًا.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _requestWithdrawal() async {
    final amountController = TextEditingController();
    final accountController = TextEditingController();
    final agentController = TextEditingController();
    String method = 'paypal';
    try {
      final form = await showDialog<Map<String, Object?>>(
        context: context,
        builder: (dialogContext) => StatefulBuilder(
          builder: (context, setDialogState) => AlertDialog(
            title: const Text('طلب سحب الأرباح'),
            content: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  TextField(
                    controller: amountController,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(labelText: 'عدد الألماس (الحد الأدنى 100)'),
                  ),
                  const SizedBox(height: 10),
                  DropdownButtonFormField<String>(
                    initialValue: method,
                    decoration: const InputDecoration(labelText: 'طريقة الاستلام'),
                    items: const [
                      DropdownMenuItem(value: 'paypal', child: Text('PayPal')),
                      DropdownMenuItem(value: 'bank', child: Text('تحويل بنكي')),
                      DropdownMenuItem(value: 'usdt', child: Text('USDT')),
                      DropdownMenuItem(value: 'agent', child: Text('وكيل شحن')),
                      DropdownMenuItem(value: 'sham_cash', child: Text('شام كاش')),
                    ],
                    onChanged: (value) => setDialogState(() => method = value ?? 'paypal'),
                  ),
                  const SizedBox(height: 10),
                  TextField(
                    controller: accountController,
                    minLines: 1,
                    maxLines: 3,
                    decoration: InputDecoration(
                      labelText: method == 'paypal' ? 'بريد PayPal' : method == 'bank' ? 'تفاصيل الحساب البنكي' : method == 'usdt' ? 'عنوان محفظة USDT والشبكة' : method == 'sham_cash' ? 'رقم/معرّف شام كاش' : 'بيانات التواصل مع الوكيل',
                    ),
                  ),
                  if (method == 'agent') ...[
                    const SizedBox(height: 10),
                    TextField(controller: agentController, decoration: const InputDecoration(labelText: 'معرّف الوكيل')),
                  ],
                  const SizedBox(height: 8),
                  const Text('سيُرسل الطلب إلى الخادم للمراجعة. لا يعني الإرسال أن الدفع تم.', style: TextStyle(fontSize: 12)),
                ],
              ),
            ),
            actions: [
              TextButton(onPressed: () => Navigator.pop(dialogContext), child: const Text('إلغاء')),
              FilledButton(
                onPressed: () => Navigator.pop(dialogContext, {
                  'diamonds': int.tryParse(amountController.text.trim()) ?? 0,
                  'method': method,
                  'account': accountController.text.trim(),
                  'agentId': agentController.text.trim(),
                }),
                child: const Text('إرسال الطلب'),
              ),
            ],
          ),
        ),
      );
      if (form == null || !mounted) return;
      final diamonds = form['diamonds'] as int? ?? 0;
      final account = form['account'] as String? ?? '';
      final selectedMethod = form['method'] as String? ?? 'paypal';
      final agentId = form['agentId'] as String? ?? '';
      if (diamonds < 100 || account.isEmpty || (selectedMethod == 'agent' && agentId.isEmpty)) {
        setState(() => _error = 'تحقق من قيمة السحب وبيانات الاستلام ومعرّف الوكيل عند الحاجة.');
        return;
      }
      setState(() { _loading = true; _error = null; });
      await widget.apiClient.requestWithdrawal(
        widget.accessToken,
        diamonds: diamonds,
        method: selectedMethod,
        payoutDetails: {'account': account},
        agentId: selectedMethod == 'agent' ? agentId : null,
      );
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('تم إرسال طلب السحب للمراجعة.')));
      await _load();
    } on JehoApiException catch (error) {
      if (mounted) setState(() => _error = error.message);
    } catch (_) {
      if (mounted) setState(() => _error = 'تعذر إرسال طلب السحب.');
    } finally {
      amountController.dispose();
      accountController.dispose();
      agentController.dispose();
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
                    _BalanceTile(label: 'العملات', value: balances['coins'] ?? balances['coinBalance'] ?? '—', icon: Icons.monetization_on_rounded),
                    _BalanceTile(label: 'تقدّم حد السحب', value: '${((double.tryParse((balances['withdrawProgress'] ?? 0).toString()) ?? 0) * 100).clamp(0, 100).toStringAsFixed(0)}%', icon: Icons.trending_up_rounded),
                    const SizedBox(height: 12),
                    FilledButton.tonalIcon(
                      onPressed: _loading ? null : _requestWithdrawal,
                      icon: const Icon(Icons.payments_outlined),
                      label: const Text('طلب سحب الألماس'),
                    ),
                    const SizedBox(height: 18),
                    Text('باقات الشحن', style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w800)),
                    if (_packages.isEmpty)
                      const Padding(
                        padding: EdgeInsets.symmetric(vertical: 8),
                        child: Text('لا تتوفر باقات للعرض حاليًا.'),
                      )
                    else
                      ..._packages.map((pkg) {
                        final coins = pkg['coins'] ?? pkg['amount'] ?? '—';
                        final bonus = pkg['bonusCoins'] ?? 0;
                        final price = pkg['priceUsd'] ?? pkg['price'] ?? '—';
                        return Card(
                          child: ListTile(
                            leading: const Icon(Icons.add_card_rounded, color: _featureGold),
                            title: Text('$coins عملة${bonus is num && bonus > 0 ? ' + $bonus إضافية' : ''}'),
                            trailing: Text('$price'),
                            subtitle: const Text('إنشاء طلب دفع مستضاف عبر مزود الدفع المفعّل في الخادم.'),
                            onTap: _loading ? null : () => _startCheckout(pkg),
                          ),
                        );
                      }),
                    const SizedBox(height: 18),
                    Text('آخر الحركات', style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w800)),
                    if (_transactions.isEmpty)
                      const Padding(
                        padding: EdgeInsets.symmetric(vertical: 8),
                        child: Text('لا توجد حركات حديثة أو تعذّر تحميل السجل.'),
                      )
                    else
                      ..._transactions.map((tx) {
                        final amount = tx['amount'] ?? '—';
                        final description = (tx['description'] ?? tx['type'] ?? 'حركة رصيد').toString();
                        final created = (tx['createdAt'] ?? '').toString();
                        return ListTile(
                          contentPadding: EdgeInsets.zero,
                          leading: const Icon(Icons.receipt_long_rounded),
                          title: Text(description, maxLines: 2, overflow: TextOverflow.ellipsis),
                          subtitle: Text(created),
                          trailing: Text('$amount'),
                        );
                      }),
                    const SizedBox(height: 12),
                    const Text('الرصيد وسجل الحركات من الخادم الحالي. الدفع يفتح صفحة مزود الدفع المستضاف؛ لا يتم إضافة الرصيد إلا بعد تأكيد الخادم للدفعة.'),
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
  const GiftCatalogPage({required this.apiClient, required this.accessToken, this.roomId, super.key});
  final JehoApiClient apiClient;
  final String accessToken;
  final String? roomId;
  @override
  State<GiftCatalogPage> createState() => _GiftCatalogPageState();
}

class _GiftCatalogPageState extends State<GiftCatalogPage> {
  List<Map<String, dynamic>> _gifts = const [];
  bool _loading = true;
  bool _sending = false;
  String? _error;

  @override
  void initState() { super.initState(); _load(); }

  Future<void> _load() async {
    setState(() { _loading = true; _error = null; });
    try {
      final gifts = await widget.apiClient.getGiftCatalog();
      if (mounted) setState(() => _gifts = gifts);
    } on JehoApiException catch (error) {
      if (mounted) setState(() => _error = error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _sendGift(Map<String, dynamic> gift) async {
    if (widget.accessToken.isEmpty) { setState(() => _error = 'سجّل الدخول أولًا لإرسال الهدايا.'); return; }
    final giftId = (gift['id'] ?? '').toString();
    if (giftId.isEmpty) { setState(() => _error = 'هذه الهدية لا تحتوي على معرّف صالح من الخادم.'); return; }
    final receiver = TextEditingController();
    final quantity = TextEditingController(text: '1');
    try {
      final form = await showDialog<Map<String, String>>(
        context: context,
        builder: (dialogContext) => AlertDialog(
          title: Text('إرسال ${(gift['name'] ?? gift['title'] ?? 'هدية').toString()}'),
          content: Column(mainAxisSize: MainAxisSize.min, children: [
            const Text('أدخل معرّف المستخدم UUID المستلم. يتحقق الخادم من الرصيد وصلاحيات الإرسال.'),
            const SizedBox(height: 10),
            TextField(controller: receiver, decoration: const InputDecoration(labelText: 'معرّف المستلم')),
            TextField(controller: quantity, keyboardType: TextInputType.number, decoration: const InputDecoration(labelText: 'الكمية')),
          ]),
          actions: [
            TextButton(onPressed: () => Navigator.pop(dialogContext), child: const Text('إلغاء')),
            FilledButton(onPressed: () => Navigator.pop(dialogContext, {'receiverId': receiver.text.trim(), 'quantity': quantity.text.trim()}), child: const Text('إرسال')),
          ],
        ),
      );
      if (form == null || !mounted) return;
      final receiverId = form['receiverId'] ?? '';
      final count = int.tryParse(form['quantity'] ?? '');
      if (receiverId.isEmpty || count == null || count < 1 || count > 99) {
        setState(() => _error = 'أدخل معرّف مستلم صحيحًا وكمية بين 1 و99.');
        return;
      }
      setState(() { _sending = true; _error = null; });
      await widget.apiClient.sendGift(widget.accessToken, giftId: giftId, receiverId: receiverId, roomId: widget.roomId, quantity: count);
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('تم إرسال طلب الهدية بنجاح.')));
    } on JehoApiException catch (error) {
      if (mounted) setState(() => _error = error.message);
    } catch (_) {
      if (mounted) setState(() => _error = 'تعذر إرسال الهدية.');
    } finally {
      receiver.dispose();
      quantity.dispose();
      if (mounted) setState(() => _sending = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('الهدايا'), actions: [IconButton(onPressed: _loading || _sending ? null : _load, icon: const Icon(Icons.refresh_rounded))]),
    body: _loading ? const Center(child: CircularProgressIndicator()) : Column(children: [
      if (_error != null) Padding(padding: const EdgeInsets.all(12), child: Text(_error!, style: const TextStyle(color: Colors.red))),
      if (_sending) const LinearProgressIndicator(),
      Expanded(child: _gifts.isEmpty ? const Center(child: Text('لا توجد هدايا متاحة حاليًا.')) : GridView.builder(
        padding: const EdgeInsets.all(12),
        gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(crossAxisCount: 2, childAspectRatio: .78, crossAxisSpacing: 10, mainAxisSpacing: 10),
        itemCount: _gifts.length,
        itemBuilder: (context, index) {
          final gift = _gifts[index];
          final title = (gift['name'] ?? gift['title'] ?? 'هدية').toString();
          final cost = gift['price'] ?? gift['coinCost'] ?? gift['cost'] ?? '—';
          final image = (gift['imageUrl'] ?? gift['image'] ?? gift['mediaUrl'] ?? '').toString();
          return Card(clipBehavior: Clip.antiAlias, child: Padding(padding: const EdgeInsets.all(10), child: Column(children: [
            Expanded(child: image.startsWith('http') ? Image.network(image, fit: BoxFit.contain, errorBuilder: (_, __, ___) => const Icon(Icons.card_giftcard_rounded, size: 52)) : const Icon(Icons.card_giftcard_rounded, size: 52, color: _featureGold)),
            const SizedBox(height: 8),
            Text(title, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w700)),
            const SizedBox(height: 4),
            Text('السعر: $cost'),
            const SizedBox(height: 6),
            SizedBox(width: double.infinity, child: FilledButton.tonalIcon(onPressed: _sending ? null : () => _sendGift(gift), icon: const Icon(Icons.send_rounded, size: 16), label: const Text('إرسال'))),
          ])));
        },
      )),
    ]),
  );
}

class VanityIdsPage extends StatefulWidget {
  const VanityIdsPage({required this.apiClient, required this.accessToken, super.key});
  final JehoApiClient apiClient;
  final String accessToken;
  @override
  State<VanityIdsPage> createState() => _VanityIdsPageState();
}

class _VanityIdsPageState extends State<VanityIdsPage> {
  List<Map<String, dynamic>> _ids = const [];
  Map<String, dynamic>? _mine;
  bool _loading = true;
  bool _busy = false;
  String? _error;

  @override
  void initState() { super.initState(); _load(); }

  Future<void> _load() async {
    setState(() { _loading = true; _error = null; });
    try {
      final ids = await widget.apiClient.getVanityIds();
      Map<String, dynamic>? mine;
      try {
        final response = await widget.apiClient.getMyVanityId(widget.accessToken);
        if (response['item'] is Map<String, dynamic>) mine = response['item'] as Map<String, dynamic>;
      } on JehoApiException { /* catalog remains usable if account has no current ID */ }
      if (mounted) setState(() { _ids = ids; _mine = mine; });
    } on JehoApiException catch (error) {
      if (mounted) setState(() => _error = error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _buy(Map<String, dynamic> item) async {
    final publicId = (item['publicId'] ?? '').toString();
    if (publicId.isEmpty || widget.accessToken.isEmpty) {
      setState(() => _error = 'تعذر تحديد الآي دي أو جلسة الدخول.');
      return;
    }
    final price = item['priceCoins'] ?? 0;
    final confirm = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text('شراء الآي دي $publicId'),
        content: Text('السعر: $price عملة. مدة الإيجار 30 يومًا، ويخصم السعر من رصيد العملات عند تأكيد الخادم.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(dialogContext, false), child: const Text('إلغاء')),
          FilledButton(onPressed: () => Navigator.pop(dialogContext, true), child: const Text('تأكيد الشراء')),
        ],
      ),
    );
    if (confirm != true || !mounted) return;
    setState(() { _busy = true; _error = null; });
    try {
      await widget.apiClient.reserveVanityId(widget.accessToken, publicId);
      await widget.apiClient.purchaseVanityId(widget.accessToken, publicId);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('تم شراء الآي دي وتحديثه في حسابك.')));
      await _load();
    } on JehoApiException catch (error) {
      if (mounted) setState(() => _error = error.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('الآي دي المميز'), actions: [IconButton(onPressed: _loading || _busy ? null : _load, icon: const Icon(Icons.refresh_rounded))]),
    body: _loading ? const Center(child: CircularProgressIndicator()) : ListView(
      padding: const EdgeInsets.all(14),
      children: [
        if (_mine != null) Card(child: ListTile(leading: const Icon(Icons.verified_rounded, color: _featureGold), title: Text('آي دي حسابك: ${_mine!['publicId'] ?? '—'}'), subtitle: Text('ينتهي: ${_mine!['expiresAt'] ?? 'غير محدد'}'))),
        if (_error != null) Padding(padding: const EdgeInsets.only(bottom: 10), child: Text(_error!, style: const TextStyle(color: Colors.red))),
        const Text('الآي دي المميز', style: TextStyle(fontWeight: FontWeight.w800, fontSize: 20)),
        const SizedBox(height: 8),
        if (_ids.isEmpty) const Text('لا توجد أرقام متاحة حاليًا.'),
        for (final item in _ids) Card(child: ListTile(
          leading: const CircleAvatar(child: Icon(Icons.tag_rounded)),
          title: Text((item['publicId'] ?? '—').toString(), style: const TextStyle(fontWeight: FontWeight.w800)),
          subtitle: Text('مدة 30 يومًا · ${item['renewPriceCoins'] ?? item['priceCoins'] ?? '—'} عملة للتجديد'),
          trailing: FilledButton(
            onPressed: _busy || widget.accessToken.isEmpty ? null : () => _buy(item),
            child: Text(_busy ? 'جارٍ التنفيذ' : '${item['priceCoins'] ?? '—'} عملة'),
          ),
        )),
        const SizedBox(height: 10),
        const Text('الشراء يغيّر المعرّف العام بعد تأكيد الخادم. لا يتم تعديل الرصيد محليًا.', style: TextStyle(color: Colors.black54, height: 1.5)),
      ],
    ),
  );
}

class CosmeticsPage extends StatefulWidget {
  const CosmeticsPage({
    required this.apiClient,
    required this.accessToken,
    super.key,
  });

  final JehoApiClient apiClient;
  final String accessToken;

  @override
  State<CosmeticsPage> createState() => _CosmeticsPageState();
}

class _CosmeticsPageState extends State<CosmeticsPage> {
  List<Map<String, dynamic>> _catalog = const [];
  List<Map<String, dynamic>> _inventory = const [];
  bool _loading = true;
  String? _error;
  final Set<String> _busy = {};

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
      final results = await Future.wait([
        widget.apiClient.getCosmetics(),
        widget.apiClient.getCosmeticInventory(widget.accessToken),
      ]);
      if (mounted) {
        setState(() {
          _catalog = results[0];
          _inventory = results[1];
        });
      }
    } on JehoApiException catch (error) {
      if (mounted) setState(() => _error = error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  String _mediaUrl(String value) {
    if (value.startsWith('http://') || value.startsWith('https://')) return value;
    final base = Uri.parse(widget.apiClient.baseUrl);
    final origin = Uri(scheme: base.scheme, host: base.host, port: base.hasPort ? base.port : null);
    return origin.resolve(value.startsWith('/') ? value : '/$value').toString();
  }

  Map<String, dynamic>? _ownedFor(String id) {
    for (final row in _inventory) {
      final cosmetic = row['cosmetic'] is Map<String, dynamic>
          ? row['cosmetic'] as Map<String, dynamic>
          : <String, dynamic>{};
      if ((row['cosmeticId'] ?? cosmetic['id'])?.toString() == id) return row;
    }
    return null;
  }

  Future<void> _act(Map<String, dynamic> item, {required bool owned}) async {
    final id = item['id']?.toString() ?? '';
    if (id.isEmpty || _busy.contains(id)) return;
    setState(() => _busy.add(id));
    try {
      if (owned) {
        await widget.apiClient.equipCosmetic(widget.accessToken, id);
      } else {
        await widget.apiClient.purchaseCosmetic(widget.accessToken, id);
      }
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(owned ? 'تم ارتداء العنصر.' : 'تم الشراء ومحاولة ارتداء العنصر.')),
      );
      await _load();
    } on JehoApiException catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(error.message)));
      }
    } finally {
      if (mounted) setState(() => _busy.remove(id));
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: const Text('الإطارات والزينة'), actions: [
          IconButton(onPressed: _loading ? null : _load, icon: const Icon(Icons.refresh_rounded)),
        ]),
        body: _loading
            ? const Center(child: CircularProgressIndicator())
            : _error != null
                ? Center(child: Padding(padding: const EdgeInsets.all(24), child: Text(_error!, textAlign: TextAlign.center)))
                : _catalog.isEmpty
                    ? const Center(child: Text('لا توجد عناصر زينة متاحة حاليًا.'))
                    : GridView.builder(
                        padding: const EdgeInsets.all(12),
                        gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                          crossAxisCount: 2,
                          childAspectRatio: .72,
                          crossAxisSpacing: 10,
                          mainAxisSpacing: 10,
                        ),
                        itemCount: _catalog.length,
                        itemBuilder: (context, index) {
                          final item = _catalog[index];
                          final id = item['id']?.toString() ?? '';
                          final owned = _ownedFor(id);
                          final isOwned = owned != null &&
                              (owned['expiresAt'] == null ||
                                  DateTime.tryParse(owned['expiresAt'].toString())?.isAfter(DateTime.now()) == true);
                          final title = (item['name'] ?? item['code'] ?? 'زينة').toString();
                          final preview = (item['animationUrl'] ?? item['previewUrl'] ?? '').toString();
                          final price = item['coinPrice'] ?? 0;
                          final busy = _busy.contains(id);
                          return Card(
                            clipBehavior: Clip.antiAlias,
                            child: Padding(
                              padding: const EdgeInsets.all(10),
                              child: Column(
                                children: [
                                  Expanded(
                                    child: preview.isNotEmpty
                                        ? Image.network(
                                            _mediaUrl(preview),
                                            fit: BoxFit.contain,
                                            errorBuilder: (_, __, ___) => const Icon(Icons.auto_awesome_rounded, size: 48, color: _featureGold),
                                          )
                                        : const Icon(Icons.auto_awesome_rounded, size: 48, color: _featureGold),
                                  ),
                                  Text(title, maxLines: 2, overflow: TextOverflow.ellipsis, textAlign: TextAlign.center, style: const TextStyle(fontWeight: FontWeight.w700)),
                                  const SizedBox(height: 4),
                                  Text(isOwned ? 'موجود في أغراضي' : 'السعر: $price عملة', maxLines: 1, overflow: TextOverflow.ellipsis),
                                  const SizedBox(height: 6),
                                  SizedBox(
                                    width: double.infinity,
                                    child: FilledButton(
                                      onPressed: busy ? null : () => _act(item, owned: isOwned),
                                      child: Text(busy ? 'جارٍ التنفيذ…' : isOwned ? 'ارتداء' : 'شراء'),
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          );
                        },
                      ),
      );
}


class SocialHubPage extends StatefulWidget {
  const SocialHubPage({required this.apiClient, required this.accessToken, super.key});
  final JehoApiClient apiClient;
  final String accessToken;
  @override
  State<SocialHubPage> createState() => _SocialHubPageState();
}

class _SocialHubPageState extends State<SocialHubPage> {
  int _tab = 0;
  bool _loading = true;
  String? _error;
  Map<String, dynamic> _cp = const {};
  List<Map<String, dynamic>> _friends = const [];
  List<Map<String, dynamic>> _visitors = const [];
  List<Map<String, dynamic>> _relations = const [];

  @override
  void initState() { super.initState(); _load(); }

  Future<void> _load() async {
    setState(() { _loading = true; _error = null; });
    try {
      final results = await Future.wait<dynamic>([
        widget.apiClient.getMyCp(widget.accessToken),
        widget.apiClient.getMyFriends(widget.accessToken),
        widget.apiClient.getMyVisitors(widget.accessToken),
        widget.apiClient.getMyRelations(widget.accessToken),
      ]);
      if (!mounted) return;
      setState(() {
        _cp = results[0] as Map<String, dynamic>;
        _friends = results[1] as List<Map<String, dynamic>>;
        _visitors = results[2] as List<Map<String, dynamic>>;
        _relations = results[3] as List<Map<String, dynamic>>;
      });
    } on JehoApiException catch (error) {
      if (mounted) setState(() => _error = error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  String _name(Map<String, dynamic> row) {
    final user = row['user'] is Map<String, dynamic> ? row['user'] as Map<String, dynamic> : row;
    final partner = row['partner'] is Map<String, dynamic> ? row['partner'] as Map<String, dynamic> : user;
    return (partner['displayName'] ?? partner['username'] ?? row['displayName'] ?? row['name'] ?? 'مستخدم JEHO').toString();
  }

  String _avatar(Map<String, dynamic> row) {
    final user = row['user'] is Map<String, dynamic> ? row['user'] as Map<String, dynamic> : row;
    final partner = row['partner'] is Map<String, dynamic> ? row['partner'] as Map<String, dynamic> : user;
    return (partner['avatarUrl'] ?? row['avatarUrl'] ?? '').toString();
  }

  Future<void> _searchAndRequestFriend() async {
    final query = TextEditingController();
    try {
      final value = await showDialog<String>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('البحث عن مستخدم'),
          content: TextField(
            controller: query,
            autofocus: true,
            decoration: const InputDecoration(labelText: 'اسم المستخدم أو جزء منه'),
            textInputAction: TextInputAction.search,
            onSubmitted: (value) => Navigator.pop(context, value.trim()),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context), child: const Text('إلغاء')),
            FilledButton(onPressed: () => Navigator.pop(context, query.text.trim()), child: const Text('بحث')),
          ],
        ),
      );
      if (value == null || value.trim().isEmpty || !mounted) return;
      final users = await widget.apiClient.searchUsers(value.trim());
      if (!mounted) return;
      if (users.isEmpty) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('لم يتم العثور على مستخدمين.')));
        return;
      }
      await showModalBottomSheet<void>(
        context: context,
        isScrollControlled: true,
        builder: (sheetContext) => SafeArea(
          child: ListView(
            shrinkWrap: true,
            children: [
              const ListTile(title: Text('نتائج البحث — أرسل طلب صداقة')),
              for (final user in users)
                ListTile(
                  leading: const CircleAvatar(child: Icon(Icons.person_rounded)),
                  title: Text((user['displayName'] ?? user['username'] ?? 'مستخدم').toString()),
                  subtitle: Text((user['username'] ?? user['publicId'] ?? '').toString()),
                  trailing: IconButton(
                    tooltip: 'إرسال طلب صداقة',
                    icon: const Icon(Icons.person_add_alt_1_rounded),
                    onPressed: () async {
                      final id = (user['id'] ?? '').toString();
                      if (id.isEmpty) return;
                      try {
                        await widget.apiClient.socialRequest(widget.accessToken, id, type: 'friend');
                        if (!mounted || !sheetContext.mounted || !mounted) return;
                        Navigator.of(sheetContext).pop();
                        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('تم إرسال طلب الصداقة.')));
                        await _load();
                      } on JehoApiException catch (error) {
                        if (mounted && mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(error.message)));
                      }
                    },
                  ),
                ),
            ],
          ),
        ),
      );
    } on JehoApiException catch (error) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(error.message)));
    } finally {
      query.dispose();
    }
  }

  @override
  Widget build(BuildContext context) {
    const tabs = ['CP', 'الأصدقاء', 'الزوار', 'العلاقات'];
    final list = _tab == 1 ? _friends : _tab == 2 ? _visitors : _relations;
    final cpPartner = _cp['partner'] is Map<String, dynamic>
        ? _cp['partner'] as Map<String, dynamic>
        : _cp['cp'] is Map<String, dynamic> ? _cp['cp'] as Map<String, dynamic> : <String, dynamic>{};
    final cpName = (cpPartner['displayName'] ?? cpPartner['username'] ?? _cp['partnerName'] ?? 'لا يوجد شريك CP مرتبط').toString();
    final score = _cp['intimacyScore'] ?? _cp['score'] ?? _cp['intimacy'] ?? 0;
    final level = _cp['level'] ?? _cp['cpLevel'] ?? 0;
    return Scaffold(
      appBar: AppBar(title: const Text('CP والعلاقات'), actions: [IconButton(tooltip: 'إضافة صديق', onPressed: _searchAndRequestFriend, icon: const Icon(Icons.person_add_alt_1_rounded)), IconButton(onPressed: _loading ? null : _load, icon: const Icon(Icons.refresh_rounded))]),
      body: Column(children: [
        SizedBox(
          height: 54,
          child: ListView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
            children: List.generate(tabs.length, (index) => Padding(
              padding: const EdgeInsetsDirectional.only(end: 8),
              child: ChoiceChip(label: Text(tabs[index]), selected: _tab == index, onSelected: _loading ? null : (_) => setState(() => _tab = index)),
            )),
          ),
        ),
        if (_error != null) Padding(padding: const EdgeInsets.all(12), child: Text(_error!, style: const TextStyle(color: Colors.red))),
        Expanded(
          child: _loading
              ? const Center(child: CircularProgressIndicator())
              : _tab == 0
                  ? ListView(
                      padding: const EdgeInsets.all(16),
                      children: [
                        Card(child: Padding(
                          padding: const EdgeInsets.all(20),
                          child: Column(children: [
                            const Icon(Icons.favorite_rounded, color: _featureGold, size: 48),
                            const SizedBox(height: 10),
                            Text(cpName, style: Theme.of(context).textTheme.titleLarge, textAlign: TextAlign.center),
                            const SizedBox(height: 8),
                            Text('نقاط القرب: $score'),
                            Text('مستوى CP: $level'),
                            const SizedBox(height: 10),
                            const Text('بيانات CP معروضة من الخادم. إرسال الطلبات وتغيير الشريك غير مفعّل من هذه الشاشة.'),
                          ]),
                        )),
                        ListTile(leading: const Icon(Icons.people_alt_rounded), title: const Text('الأصدقاء'), trailing: Text('${_friends.length}')),
                        ListTile(leading: const Icon(Icons.visibility_rounded), title: const Text('الزوار'), trailing: Text('${_visitors.length}')),
                        ListTile(leading: const Icon(Icons.diversity_3_rounded), title: const Text('العلاقات المقبولة'), trailing: Text('${_relations.length}')),
                      ],
                    )
                  : list.isEmpty
                      ? Center(child: Text(_tab == 1 ? 'لا يوجد أصدقاء لعرضهم.' : _tab == 2 ? 'لا يوجد زوار لعرضهم.' : 'لا توجد علاقات مقبولة لعرضها.'))
                      : ListView.separated(
                          itemCount: list.length,
                          separatorBuilder: (_, __) => const Divider(height: 1),
                          itemBuilder: (context, index) {
                            final row = list[index];
                            final avatar = _avatar(row);
                            return ListTile(
                              leading: CircleAvatar(
                                backgroundColor: const Color(0xFFFFE7B0),
                                backgroundImage: avatar.startsWith('http') ? NetworkImage(avatar) : null,
                                child: avatar.startsWith('http') ? null : const Icon(Icons.person_rounded, color: _featureGold),
                              ),
                              title: Text(_name(row)),
                              subtitle: Text((row['type'] ?? row['relationType'] ?? '').toString()),
                            );
                          },
                        ),
        ),
      ]),
    );
  }
}


class SocialPage extends StatefulWidget {
  const SocialPage({required this.apiClient, required this.accessToken, super.key});
  final JehoApiClient apiClient;
  final String accessToken;

  @override
  State<SocialPage> createState() => _SocialPageState();
}

class _SocialPageState extends State<SocialPage> {
  final _searchController = TextEditingController();
  List<Map<String, dynamic>> _results = const [];
  List<Map<String, dynamic>> _friends = const [];
  List<Map<String, dynamic>> _relations = const [];
  bool _loading = false;
  String? _error;
  int _tab = 0;

  @override
  void initState() {
    super.initState();
    _loadSocialData();
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _loadSocialData() async {
    setState(() { _loading = true; _error = null; });
    try {
      final friends = await widget.apiClient.getMyFriends(widget.accessToken);
      List<Map<String, dynamic>> relations = const [];
      try {
          relations = await widget.apiClient.getMyRelations(widget.accessToken);
        } on JehoApiException {
          // Friend data remains useful if the optional relations endpoint is unavailable.
        }
      if (mounted) setState(() { _friends = friends; _relations = relations; });
    } on JehoApiException catch (error) {
      if (mounted) setState(() => _error = error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _search() async {
    final query = _searchController.text.trim();
    if (query.length < 2) { setState(() => _error = 'اكتب حرفين على الأقل للبحث.'); return; }
    setState(() { _loading = true; _error = null; });
    try {
      final results = await widget.apiClient.searchUsers(query);
      if (mounted) setState(() => _results = results);
    } on JehoApiException catch (error) {
      if (mounted) setState(() => _error = error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _act(Future<void> Function() action, String success) async {
    setState(() { _loading = true; _error = null; });
    try {
      await action();
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(success)));
      await _loadSocialData();
      if (_searchController.text.trim().length >= 2) await _search();
    } on JehoApiException catch (error) {
      if (mounted) setState(() => _error = error.message);
    } finally {
      if (mounted && _loading) setState(() => _loading = false);
    }
  }

  String _userId(Map<String, dynamic> user) =>
      (user['id'] ?? user['userId'] ?? user['user']?['id'] ?? '').toString();

  Widget _userTile(Map<String, dynamic> user, {bool searchable = false}) {
    final id = _userId(user);
    final title = (user['displayName'] ?? user['username'] ?? user['user']?['displayName'] ?? 'مستخدم').toString();
    final subtitle = (user['publicId'] ?? user['username'] ?? user['status'] ?? '').toString();
    return Card(
      child: ListTile(
        leading: const CircleAvatar(child: Icon(Icons.person_outline_rounded)),
        title: Text(title, maxLines: 1, overflow: TextOverflow.ellipsis),
        subtitle: subtitle.isEmpty ? null : Text(subtitle),
        trailing: searchable
            ? PopupMenuButton<String>(
                enabled: id.isNotEmpty && !_loading,
                onSelected: (action) {
                  if (action == 'friend') {
                    _act(() async { await widget.apiClient.socialRequest(widget.accessToken, id, type: 'friend'); }, 'تم إرسال طلب الصداقة.');
                  } else if (action == 'follow') {
                    _act(() async { await widget.apiClient.followUser(widget.accessToken, id, follow: true); }, 'تمت متابعة المستخدم.');
                  } else if (action == 'block') {
                    _act(() async { await widget.apiClient.blockUser(widget.accessToken, id); }, 'تم حظر المستخدم.');
                  } else if (action == 'chat') {
                    _act(() async { await widget.apiClient.startConversation(widget.accessToken, id); }, 'تم إنشاء المحادثة أو فتحها من الخادم.');
                  }
                },
                itemBuilder: (_) => const [
                  PopupMenuItem(value: 'friend', child: Text('إرسال طلب صداقة')),
                  PopupMenuItem(value: 'follow', child: Text('متابعة')),
                  PopupMenuItem(value: 'chat', child: Text('بدء محادثة')),
                  PopupMenuItem(value: 'block', child: Text('حظر المستخدم')),
                ],
              )
            : const Icon(Icons.verified_user_outlined),
      ),
    );
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text('العلاقات والأصدقاء'),
      actions: [IconButton(onPressed: _loading ? null : _loadSocialData, icon: const Icon(Icons.refresh_rounded))],
    ),
    body: Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(12, 12, 12, 6),
          child: Row(children: [
            Expanded(child: TextField(
              controller: _searchController,
              textInputAction: TextInputAction.search,
              onSubmitted: (_) => _search(),
              decoration: const InputDecoration(prefixIcon: Icon(Icons.search_rounded), labelText: 'ابحث بالاسم أو المعرّف'),
            )),
            const SizedBox(width: 8),
            IconButton.filled(onPressed: _loading ? null : _search, icon: const Icon(Icons.search_rounded)),
          ]),
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12),
          child: SegmentedButton<int>(
            segments: const [
              ButtonSegment(value: 0, label: Text('بحث'), icon: Icon(Icons.search_rounded)),
              ButtonSegment(value: 1, label: Text('أصدقائي'), icon: Icon(Icons.people_alt_outlined)),
              ButtonSegment(value: 2, label: Text('علاقاتي'), icon: Icon(Icons.favorite_border_rounded)),
            ],
            selected: {_tab},
            onSelectionChanged: (value) => setState(() => _tab = value.first),
          ),
        ),
        if (_loading) const LinearProgressIndicator(),
        if (_error != null) Padding(padding: const EdgeInsets.all(10), child: Text(_error!, style: const TextStyle(color: Colors.red))),
        Expanded(
          child: Builder(builder: (context) {
            final items = _tab == 0 ? _results : _tab == 1 ? _friends : _relations;
            if (items.isEmpty) return Center(child: Text(_tab == 0 ? 'ابحث عن مستخدم لإرسال طلب صداقة أو بدء محادثة.' : _tab == 1 ? 'لا توجد صداقات للعرض حاليًا.' : 'لا توجد علاقات نشطة للعرض حاليًا.'));
            return ListView.builder(
              padding: const EdgeInsets.all(10),
              itemCount: items.length,
              itemBuilder: (context, index) => _userTile(items[index], searchable: _tab == 0),
            );
          }),
        ),
      ],
    ),
  );
}
