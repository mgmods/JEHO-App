import 'dart:io';

import 'package:flutter/material.dart';
import 'package:file_picker/file_picker.dart';
import 'package:image_picker/image_picker.dart';

import '../data/jeho_api_client.dart';

class ConversationPage extends StatefulWidget {
  const ConversationPage({
    required this.apiClient,
    required this.accessToken,
    required this.currentUserId,
    required this.conversation,
    super.key,
  });

  final JehoApiClient apiClient;
  final String accessToken;
  final String currentUserId;
  final Map<String, dynamic> conversation;

  @override
  State<ConversationPage> createState() => _ConversationPageState();
}

class _ConversationPageState extends State<ConversationPage> {
  final _composer = TextEditingController();
  final _scrollController = ScrollController();
  List<Map<String, dynamic>> _messages = [];
  bool _loading = true;
  bool _sending = false;
  bool _uploadingAttachment = false;
  String? _error;

  String get _conversationId => widget.conversation['id']?.toString() ?? '';
  String get _title => (widget.conversation['title'] ??
      (widget.conversation['peer'] is Map<String, dynamic>
          ? (widget.conversation['peer'] as Map<String, dynamic>)['displayName']
          : null) ??
      'محادثة').toString();

  @override
  void initState() {
    super.initState();
    _loadMessages();
  }

  @override
  void dispose() {
    _composer.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  String _resolveMediaUrl(String value) {
    if (value.startsWith('https://') || value.startsWith('http://')) return value;
    final base = Uri.parse(widget.apiClient.baseUrl.endsWith('/') ? widget.apiClient.baseUrl : '${widget.apiClient.baseUrl}/');
    final origin = Uri(scheme: base.scheme, host: base.host, port: base.hasPort ? base.port : null);
    final normalized = value.replaceFirst(RegExp(r'^/uploads/'), 'uploads/');
    if (normalized.startsWith('uploads/')) return origin.resolve('/$normalized').toString();
    return origin.resolve(value.startsWith('/') ? value : '/$value').toString();
  }

  Future<void> _loadMessages() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final items = await widget.apiClient.getConversationMessages(
        widget.accessToken,
        _conversationId,
      );
      if (!mounted) return;
      setState(() => _messages = items);
    } on JehoApiException catch (error) {
      if (mounted) setState(() => _error = error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _sendMessage() async {
    final content = _composer.text.trim();
    if (content.isEmpty || _sending) return;
    setState(() {
      _sending = true;
      _error = null;
    });
    try {
      final sent = await widget.apiClient.sendTextMessage(
        widget.accessToken,
        _conversationId,
        content,
      );
      if (!mounted) return;
      _composer.clear();
      setState(() {
        _messages = [..._messages, sent];
      });
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (_scrollController.hasClients) {
          _scrollController.animateTo(
            _scrollController.position.maxScrollExtent,
            duration: const Duration(milliseconds: 180),
            curve: Curves.easeOut,
          );
        }
      });
    } on JehoApiException catch (error) {
      if (mounted) setState(() => _error = error.message);
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  Future<void> _sendFileAttachment() async {
    if (_uploadingAttachment || _sending) return;
    try {
      final result = await FilePicker.platform.pickFiles(
        type: FileType.custom,
        allowedExtensions: ['jpg', 'jpeg', 'png', 'gif', 'webp', 'mp4', 'mov', 'webm', 'mp3', 'm4a', 'aac', 'wav', 'pdf'],
        allowMultiple: false,
        withData: false,
      );
      if (result == null || result.files.isEmpty || !mounted) return;
      final picked = result.files.single;
      final path = picked.path;
      if (path == null || path.isEmpty) {
        setState(() => _error = 'تعذر الوصول إلى الملف المختار.');
        return;
      }
      final extension = (picked.extension ?? '').toLowerCase();
      final mimeType = switch (extension) {
        'jpg' || 'jpeg' => 'image/jpeg',
        'png' => 'image/png',
        'gif' => 'image/gif',
        'webp' => 'image/webp',
        'mp4' || 'mov' || 'webm' => 'video/$extension',
        'mp3' || 'm4a' || 'aac' || 'wav' => 'audio/$extension',
        'pdf' => 'application/pdf',
        _ => 'application/octet-stream',
      };
      final type = mimeType.startsWith('image/')
          ? 'image'
          : mimeType.startsWith('video/')
              ? 'video'
              : mimeType.startsWith('audio/')
                  ? 'audio'
                  : 'file';
      setState(() { _uploadingAttachment = true; _error = null; });
      final file = File(path);
      final url = await widget.apiClient.uploadImage(widget.accessToken, file);
      final size = picked.size > 0 ? picked.size : await file.length();
      final sent = await widget.apiClient.sendMessageAttachment(
        widget.accessToken,
        _conversationId,
        url,
        mimeType: mimeType,
        size: size,
        type: type,
      );
      if (!mounted) return;
      setState(() => _messages = [..._messages, sent]);
    } on JehoApiException catch (error) {
      if (mounted) setState(() => _error = error.message);
    } catch (_) {
      if (mounted) setState(() => _error = 'تعذر رفع الملف وإرساله.');
    } finally {
      if (mounted) setState(() => _uploadingAttachment = false);
    }
  }

  Future<void> _sendImageAttachment() async {
    if (_uploadingAttachment || _sending) return;
    try {
      final picked = await ImagePicker().pickImage(
        source: ImageSource.gallery,
        imageQuality: 88,
        maxWidth: 1920,
        maxHeight: 1920,
      );
      if (picked == null || !mounted) return;
      setState(() {
        _uploadingAttachment = true;
        _error = null;
      });
      final file = File(picked.path);
      final url = await widget.apiClient.uploadImage(widget.accessToken, file);
      final size = await file.length();
      final sent = await widget.apiClient.sendMessageAttachment(
        widget.accessToken,
        _conversationId,
        url,
        mimeType: 'image/${picked.path.toLowerCase().endsWith('.png') ? 'png' : picked.path.toLowerCase().endsWith('.webp') ? 'webp' : 'jpeg'}',
        size: size,
        type: 'image',
      );
      if (!mounted) return;
      setState(() => _messages = [..._messages, sent]);
    } on JehoApiException catch (error) {
      if (mounted) setState(() => _error = error.message);
    } catch (_) {
      if (mounted) setState(() => _error = 'تعذر رفع الصورة وإرسالها.');
    } finally {
      if (mounted) setState(() => _uploadingAttachment = false);
    }
  }

  Future<void> _manageMessage(Map<String, dynamic> message, {required bool isMine}) async {
    final messageId = (message['id'] ?? message['messageId'] ?? '').toString();
    if (!isMine || messageId.isEmpty) return;
    final choice = await showModalBottomSheet<String>(
      context: context,
      builder: (sheetContext) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.edit_outlined),
              title: const Text('تعديل الرسالة'),
              onTap: () => Navigator.pop(sheetContext, 'edit'),
            ),
            ListTile(
              leading: const Icon(Icons.undo_rounded),
              title: const Text('سحب الرسالة'),
              onTap: () => Navigator.pop(sheetContext, 'unsend'),
            ),
          ],
        ),
      ),
    );
    if (choice == null || !mounted) return;
    if (choice == 'edit') {
      final controller = TextEditingController(text: (message['content'] ?? '').toString());
      try {
        final content = await showDialog<String>(
          context: context,
          builder: (dialogContext) => AlertDialog(
            title: const Text('تعديل الرسالة'),
            content: TextField(
              controller: controller,
              autofocus: true,
              maxLines: 5,
              maxLength: 4000,
              decoration: const InputDecoration(labelText: 'نص الرسالة'),
            ),
            actions: [
              TextButton(onPressed: () => Navigator.pop(dialogContext), child: const Text('إلغاء')),
              FilledButton(onPressed: () => Navigator.pop(dialogContext, controller.text.trim()), child: const Text('حفظ')),
            ],
          ),
        );
        if (content == null || content.isEmpty || !mounted) return;
        final updated = await widget.apiClient.editMessage(widget.accessToken, messageId, content);
        if (!mounted) return;
        setState(() {
          final index = _messages.indexWhere((item) => (item['id'] ?? item['messageId']).toString() == messageId);
          if (index >= 0) _messages[index] = {..._messages[index], ...updated, 'content': content};
        });
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('تم تعديل الرسالة.')));
      } on JehoApiException catch (error) {
        if (mounted) setState(() => _error = error.message);
      } finally {
        controller.dispose();
      }
    } else {
      final confirm = await showDialog<bool>(
        context: context,
        builder: (dialogContext) => AlertDialog(
          title: const Text('سحب الرسالة؟'),
          content: const Text('سيطلب التطبيق من الخادم سحب الرسالة وفق صلاحياته وسياساته.'),
          actions: [
            TextButton(onPressed: () => Navigator.pop(dialogContext, false), child: const Text('إلغاء')),
            FilledButton(onPressed: () => Navigator.pop(dialogContext, true), child: const Text('سحب')),
          ],
        ),
      );
      if (confirm != true || !mounted) return;
      try {
        await widget.apiClient.unsendMessage(widget.accessToken, messageId);
        if (!mounted) return;
        await _loadMessages();
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('تم طلب سحب الرسالة.')));
      } on JehoApiException catch (error) {
        if (mounted) setState(() => _error = error.message);
      }
    }
  }

  Future<void> _manageConversation(String action) async {
    final token = widget.accessToken;
    if (_conversationId.isEmpty || token.isEmpty) return;
    if (action == 'archive' || action == 'delete') {
      final confirmed = await showDialog<bool>(
        context: context,
        builder: (dialogContext) => AlertDialog(
          title: Text(action == 'archive' ? 'أرشفة المحادثة؟' : 'حذف المحادثة من قائمتك؟'),
          content: Text(action == 'archive'
              ? 'يمكنك الرجوع للمحادثة لاحقًا حسب إعدادات الخادم.'
              : 'سيؤثر الحذف على قائمتك فقط وفق سياسة الخادم.'),
          actions: [
            TextButton(onPressed: () => Navigator.pop(dialogContext, false), child: const Text('إلغاء')),
            FilledButton(onPressed: () => Navigator.pop(dialogContext, true), child: const Text('تأكيد')),
          ],
        ),
      );
      if (confirmed != true || !mounted) return;
    }
    try {
      switch (action) {
        case 'mute':
          await widget.apiClient.setConversationMuted(token, _conversationId, muted: true);
          if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('تم كتم إشعارات المحادثة.')));
          break;
        case 'pin':
          await widget.apiClient.setConversationPinned(token, _conversationId, pinned: true);
          if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('تم تثبيت المحادثة.')));
          break;
        case 'archive':
          await widget.apiClient.setConversationArchived(token, _conversationId, archived: true);
          if (mounted) Navigator.of(context).pop();
          break;
        case 'delete':
          await widget.apiClient.deleteConversationForMe(token, _conversationId);
          if (mounted) Navigator.of(context).pop();
          break;
      }
    } on JehoApiException catch (error) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(error.message)));
    } catch (_) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('تعذر تنفيذ الإجراء على المحادثة.')));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(_title, maxLines: 1, overflow: TextOverflow.ellipsis),
        actions: [
          PopupMenuButton<String>(
            tooltip: 'خيارات المحادثة',
            onSelected: _manageConversation,
            itemBuilder: (context) => const [
              PopupMenuItem(value: 'mute', child: ListTile(leading: Icon(Icons.notifications_off_outlined), title: Text('كتم الإشعارات'), dense: true)),
              PopupMenuItem(value: 'pin', child: ListTile(leading: Icon(Icons.push_pin_outlined), title: Text('تثبيت المحادثة'), dense: true)),
              PopupMenuItem(value: 'archive', child: ListTile(leading: Icon(Icons.archive_outlined), title: Text('أرشفة المحادثة'), dense: true)),
              PopupMenuItem(value: 'delete', child: ListTile(leading: Icon(Icons.delete_outline_rounded), title: Text('حذف من قائمتي'), dense: true)),
            ],
          ),
          IconButton(
            tooltip: 'تحديث الرسائل',
            onPressed: _loading ? null : _loadMessages,
            icon: const Icon(Icons.refresh_rounded),
          ),
        ],
      ),
      body: SafeArea(
        child: Column(
          children: [
            if (_error != null)
              MaterialBanner(
                content: Text(_error!),
                actions: [
                  TextButton(
                    onPressed: _loadMessages,
                    child: const Text('إعادة المحاولة'),
                  ),
                ],
              ),
            Expanded(
              child: _loading
                  ? const Center(child: CircularProgressIndicator())
                  : _messages.isEmpty
                      ? const Center(
                          child: Padding(
                            padding: EdgeInsets.all(24),
                            child: Text(
                              'لا توجد رسائل بعد. ابدأ المحادثة برسالة نصية.',
                              textAlign: TextAlign.center,
                            ),
                          ),
                        )
                      : ListView.builder(
                          controller: _scrollController,
                          padding: const EdgeInsets.all(12),
                          itemCount: _messages.length,
                          itemBuilder: (context, index) {
                            final message = _messages[index];
                            final content = (message['content'] ?? '').toString();
                            final type = (message['type'] ?? 'text').toString().toLowerCase();
                            final sender = message['sender'] is Map<String, dynamic>
                                ? message['sender'] as Map<String, dynamic>
                                : <String, dynamic>{};
                            final isMine =
                                message['isMine'] == true ||
                                message['isOwn'] == true ||
                                (widget.currentUserId.isNotEmpty &&
                                  message['senderId']?.toString() == widget.currentUserId);
                            final text = content.isNotEmpty
                                ? content
                                : (type == 'image' ? '📷 صورة مرفقة' : 'رسالة من نوع $type');
                            return Align(
                              alignment: isMine
                                  ? Alignment.centerLeft
                                  : Alignment.centerRight,
                              child: GestureDetector(
                                onLongPress: isMine ? () => _manageMessage(message, isMine: isMine) : null,
                                child: Container(
                                constraints: const BoxConstraints(maxWidth: 320),
                                margin: const EdgeInsets.symmetric(vertical: 4),
                                padding: const EdgeInsets.symmetric(
                                  horizontal: 14,
                                  vertical: 10,
                                ),
                                decoration: BoxDecoration(
                                  color: isMine
                                      ? const Color(0xFFFFE7B0)
                                      : Colors.white,
                                  borderRadius: BorderRadius.circular(16),
                                  border: Border.all(
                                    color: const Color(0xFFE8DCC5),
                                  ),
                                ),
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    if (sender['displayName'] != null)
                                      Text(
                                        sender['displayName'].toString(),
                                        style: const TextStyle(
                                          fontSize: 11,
                                          fontWeight: FontWeight.w700,
                                        ),
                                      ),
                                    if (message['media'] is Map<String, dynamic>) ...[
                                      Builder(builder: (context) {
                                        final media = message['media'] as Map<String, dynamic>;
                                        final mediaUrl = (media['url'] ?? '').toString();
                                        if (mediaUrl.isEmpty) return const SizedBox.shrink();
                                        final resolved = _resolveMediaUrl(mediaUrl);
                                        if (type == 'image') {
                                          return Padding(
                                            padding: const EdgeInsets.only(bottom: 6),
                                            child: ClipRRect(
                                              borderRadius: BorderRadius.circular(10),
                                              child: Image.network(
                                                resolved,
                                                width: 230,
                                                fit: BoxFit.cover,
                                                errorBuilder: (_, __, ___) => const Text('تعذر عرض الصورة المرفقة'),
                                              ),
                                            ),
                                          );
                                        }
                                        return Padding(
                                          padding: const EdgeInsets.only(bottom: 6),
                                          child: SelectableText('مرفق: ${media['mimeType'] ?? type}\\n$resolved'),
                                        );
                                      }),
                                    ],
                                    if (content.isNotEmpty || message['media'] == null) Text(text),
                                  ],
                                ),
                                ),
                              ),
                            );
                          },
                        ),
            ),
            const Divider(height: 1),
            Padding(
              padding: const EdgeInsets.fromLTRB(12, 8, 12, 12),
              child: Row(
                children: [
                  IconButton(
                    tooltip: 'إرفاق صورة',
                    onPressed: _uploadingAttachment ? null : _sendImageAttachment,
                    icon: _uploadingAttachment
                        ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
                        : const Icon(Icons.image_outlined),
                  ),
                  IconButton(
                    tooltip: 'إرفاق ملف أو فيديو',
                    onPressed: _uploadingAttachment ? null : _sendFileAttachment,
                    icon: const Icon(Icons.attach_file_rounded),
                  ),
                  Expanded(
                    child: TextField(
                      controller: _composer,
                      minLines: 1,
                      maxLines: 4,
                      textInputAction: TextInputAction.send,
                      onSubmitted: (_) => _sendMessage(),
                      decoration: const InputDecoration(
                        hintText: 'اكتب رسالة نصية…',
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.all(Radius.circular(22)),
                        ),
                        contentPadding: EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 10,
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  IconButton.filled(
                    tooltip: 'إرسال',
                    onPressed: _sending ? null : _sendMessage,
                    icon: _sending
                        ? const SizedBox(
                            width: 18,
                            height: 18,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Icon(Icons.send_rounded),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
