import 'package:flutter/material.dart';
import 'package:socket_io_client/socket_io_client.dart' as IO;

class RoomRealtimeChat extends StatefulWidget {
  const RoomRealtimeChat({
    required this.apiBaseUrl,
    required this.accessToken,
    required this.roomId,
    required this.joined,
    required this.welcomeMessage,
    required this.onEvents,
    required this.onGames,
    required this.onGifts,
    required this.onRaiseHand,
    super.key,
  });

  final String apiBaseUrl;
  final String accessToken;
  final String roomId;
  final bool joined;
  final String welcomeMessage;
  final VoidCallback onEvents;
  final VoidCallback onGames;
  final VoidCallback onGifts;
  final VoidCallback onRaiseHand;

  @override
  State<RoomRealtimeChat> createState() => _RoomRealtimeChatState();
}

class _RoomRealtimeChatState extends State<RoomRealtimeChat> {
  final _composer = TextEditingController();
  final _scrollController = ScrollController();
  final List<Map<String, dynamic>> _messages = [];
  IO.Socket? _socket;
  bool _sending = false;
  bool _connected = false;
  String? _connectionError;

  @override
  void initState() {
    super.initState();
    if (widget.joined) _connect();
  }

  @override
  void didUpdateWidget(covariant RoomRealtimeChat oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.joined != widget.joined ||
        oldWidget.roomId != widget.roomId ||
        oldWidget.accessToken != widget.accessToken) {
      _disconnect();
      if (widget.joined) _connect();
    }
  }

  Uri get _origin {
    final base = Uri.parse(widget.apiBaseUrl);
    return Uri(scheme: base.scheme, host: base.host, port: base.hasPort ? base.port : null);
  }

  void _connect() {
    if (!widget.joined ||
        widget.accessToken.isEmpty ||
        widget.roomId.isEmpty ||
        _socket != null) {
      return;
    }
    final socket = IO.io(
      '${_origin.toString()}/realtime',
      IO.OptionBuilder()
          .setTransports(['websocket'])
          .setAuth({'token': widget.accessToken})
          .enableReconnection()
          .disableAutoConnect()
          .build(),
    );
    _socket = socket;
    socket.onConnect((_) {
      if (!mounted) return;
      setState(() {
        _connected = true;
        _connectionError = null;
      });
      socket.emit('room:join', {'roomId': widget.roomId});
    });
    socket.onConnectError((dynamic error) {
      if (!mounted) return;
      setState(() {
        _connected = false;
        _connectionError = 'تعذر الاتصال بدردشة الغرفة';
      });
    });
    socket.onDisconnect((_) {
      if (!mounted) return;
      setState(() => _connected = false);
    });
    socket.on('room:event', (dynamic raw) {
      if (!mounted || raw is! Map) return;
      final event = Map<String, dynamic>.from(raw);
      if (event['roomId']?.toString() != widget.roomId) return;
      final type = event['event']?.toString() ?? '';
      final payloadRaw = event['payload'];
      final payload = payloadRaw is Map
          ? Map<String, dynamic>.from(payloadRaw)
          : <String, dynamic>{};
      if (type == 'chat:message') {
        setState(() {
          _messages.add({
            ...payload,
            'at': event['at'] ?? DateTime.now().toIso8601String(),
          });
          if (_messages.length > 100) _messages.removeAt(0);
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
      } else if (type == 'room:chat_cleared') {
        setState(_messages.clear);
      }
    });
    socket.on('moderation:blocked', (dynamic raw) {
      if (!mounted || raw is! Map) return;
      final data = Map<String, dynamic>.from(raw);
      if (data['roomId']?.toString() != widget.roomId) return;
      final reason = (data['reason'] ?? 'لم يُسمح بإرسال الرسالة').toString();
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(reason)));
    });
    socket.connect();
  }

  void _disconnect() {
    final socket = _socket;
    _socket = null;
    if (socket != null) {
      if (socket.connected && widget.roomId.isNotEmpty) {
        socket.emit('room:leave', {'roomId': widget.roomId});
      }
      socket.disconnect();
      socket.dispose();
    }
    _connected = false;
  }

  @override
  void dispose() {
    _disconnect();
    _composer.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  void _send() {
    final text = _composer.text.trim();
    final socket = _socket;
    if (text.isEmpty || _sending) return;
    if (!widget.joined || socket == null || !socket.connected || !_connected) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('ادخل الغرفة وانتظر اتصال الدردشة قبل الإرسال.')),
      );
      return;
    }
    setState(() => _sending = true);
    socket.emit('room:event', {
      'roomId': widget.roomId,
      'event': 'chat:message',
      'payload': {'text': text},
    });
    _composer.clear();
    setState(() => _sending = false);
  }

  @override
  Widget build(BuildContext context) {
    const pale = Color(0xFFEAF7EF);
    const gold = Color(0xFFFFD36B);
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: const Color(0x773D8064),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: const Color(0x55D9F3E2)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              const Icon(Icons.chat_bubble_outline_rounded, size: 17, color: gold),
              const SizedBox(width: 7),
              const Text('دردشة الغرفة', style: TextStyle(color: pale, fontWeight: FontWeight.w800)),
              const Spacer(),
              Icon(
                _connected ? Icons.circle : Icons.circle_outlined,
                size: 9,
                color: _connected ? const Color(0xFF79F2A2) : const Color(0xFFFFC078),
              ),
              const SizedBox(width: 5),
              Text(
                !widget.joined ? 'سجّل دخولك أولاً' : _connected ? 'متصل' : 'غير متصل',
                style: const TextStyle(color: pale, fontSize: 10),
              ),
            ],
          ),
          const SizedBox(height: 8),
          if (_messages.isEmpty)
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
              decoration: BoxDecoration(
                color: const Color(0xCC0A6948),
                borderRadius: BorderRadius.circular(13),
                border: Border.all(color: const Color(0x55D9F3E2)),
              ),
              child: Text(widget.welcomeMessage, style: const TextStyle(color: pale, fontSize: 12, height: 1.4)),
            )
          else
            SizedBox(
              height: 170,
              child: ListView.builder(
                controller: _scrollController,
                itemCount: _messages.length,
                itemBuilder: (context, index) {
                  final message = _messages[index];
                  final name = (message['displayName'] ?? message['name'] ?? 'عضو').toString();
                  final text = (message['text'] ?? '').toString();
                  final mine = message['userId'] != null &&
                      message['userId'].toString().isNotEmpty;
                  return Align(
                    alignment: AlignmentDirectional.centerStart,
                    child: Container(
                      margin: const EdgeInsets.only(bottom: 6),
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 7),
                      decoration: BoxDecoration(
                        color: const Color(0x66304F41),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: RichText(
                        text: TextSpan(
                          style: const TextStyle(color: pale, fontSize: 12, height: 1.35),
                          children: [
                            TextSpan(text: '$name: ', style: const TextStyle(color: gold, fontWeight: FontWeight.w800)),
                            TextSpan(text: text),
                          ],
                        ),
                      ),
                    ),
                  );
                },
              ),
            ),
          if (_connectionError != null)
            Padding(
              padding: const EdgeInsets.only(top: 4),
              child: Text(_connectionError!, style: const TextStyle(color: Color(0xFFFFD3A1), fontSize: 10)),
            ),
          const SizedBox(height: 8),
          Row(
            children: [
              Expanded(
                child: TextField(
                  controller: _composer,
                  enabled: widget.joined && _connected,
                  minLines: 1,
                  maxLines: 3,
                  textInputAction: TextInputAction.send,
                  onSubmitted: (_) => _send(),
                  style: const TextStyle(color: pale, fontSize: 12),
                  decoration: InputDecoration(
                    hintText: widget.joined ? 'اكتب رسالة...' : 'ادخل الغرفة للمشاركة بالدردشة',
                    hintStyle: const TextStyle(color: Color(0xBFEAF7EF), fontSize: 11),
                    filled: true,
                    fillColor: const Color(0x55002118),
                    contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
                    border: OutlineInputBorder(borderRadius: BorderRadius.circular(14), borderSide: BorderSide.none),
                  ),
                ),
              ),
              const SizedBox(width: 6),
              IconButton.filled(
                onPressed: widget.joined && _connected && !_sending ? _send : null,
                icon: const Icon(Icons.send_rounded, size: 17),
                style: IconButton.styleFrom(backgroundColor: const Color(0xFFB68A35), foregroundColor: Colors.white),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              _QuickAction(icon: Icons.celebration_rounded, label: 'الفعاليات', onTap: widget.onEvents),
              const SizedBox(width: 5),
              _QuickAction(icon: Icons.grid_3x3_rounded, label: 'الألعاب', onTap: widget.onGames),
              const SizedBox(width: 5),
              _QuickAction(icon: Icons.card_giftcard_rounded, label: 'الهدايا', onTap: widget.onGifts),
              const SizedBox(width: 5),
              _QuickAction(icon: Icons.pan_tool_alt_rounded, label: 'طلب المايك', onTap: widget.onRaiseHand),
            ],
          ),
        ],
      ),
    );
  }
}

class _QuickAction extends StatelessWidget {
  const _QuickAction({required this.icon, required this.label, required this.onTap});
  final IconData icon;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Expanded(
        child: InkWell(
          borderRadius: BorderRadius.circular(12),
          onTap: onTap,
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 3, vertical: 9),
            decoration: BoxDecoration(
              color: const Color(0x55205C43),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: const Color(0x44D9F3E2)),
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(icon, size: 16, color: const Color(0xFFFFD36B)),
                const SizedBox(height: 4),
                Text(label, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 9, color: Color(0xFFEAF7EF), fontWeight: FontWeight.w700)),
              ],
            ),
          ),
        ),
      );
}
