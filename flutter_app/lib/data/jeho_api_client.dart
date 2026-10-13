import 'dart:convert';
import 'dart:io';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:http/http.dart' as http;

class JehoApiException implements Exception {
  const JehoApiException(this.message);

  final String message;

  @override
  String toString() => message;
}

class JehoApiClient {
  JehoApiClient({
    required this.baseUrl,
    http.Client? client,
    FlutterSecureStorage? secureStorage,
  })  : _client = client ?? http.Client(),
        _secureStorage = secureStorage ?? const FlutterSecureStorage();

  final String baseUrl;
  final http.Client _client;
  final FlutterSecureStorage _secureStorage;

  Future<Map<String, dynamic>> login({
    required String identifier,
    required String password,
  }) {
    return _authenticate(
      'auth/login',
      {'identifier': identifier.trim(), 'password': password},
    );
  }

  Future<Map<String, dynamic>> register({
    required String email,
    required String username,
    required String password,
    String? displayName,
  }) {
    return _authenticate('auth/register', {
      'email': email.trim(),
      'username': username.trim(),
      'password': password,
      if (displayName != null && displayName.trim().isNotEmpty)
        'displayName': displayName.trim(),
    });
  }

  Future<Map<String, dynamic>> guestLogin({String? displayName}) {
    return _authenticate('auth/guest', {
      if (displayName != null && displayName.trim().isNotEmpty)
        'displayName': displayName.trim(),
    });
  }

  Future<Map<String, dynamic>> _authenticate(
    String endpoint,
    Map<String, Object?> body,
  ) async {
    final base = Uri.parse(baseUrl.endsWith('/') ? baseUrl : '$baseUrl/');
    final uri = base.resolve(endpoint);

    http.Response response;
    try {
      response = await _client
          .post(
            uri,
            headers: const {'Content-Type': 'application/json', 'Accept': 'application/json'},
            body: jsonEncode(body),
          )
          .timeout(const Duration(seconds: 20));
    } on Exception {
      throw const JehoApiException(
        'تعذّر الاتصال بالخادم. تحقق من الإنترنت وحاول مرة أخرى.',
      );
    }

    dynamic decoded;
    try {
      decoded = jsonDecode(response.body);
    } on FormatException {
      throw const JehoApiException('استجابة الخادم غير مفهومة.');
    }

    if (response.statusCode < 200 || response.statusCode >= 300) {
      final message = decoded is Map<String, dynamic>
          ? (decoded['message'] ?? decoded['error'])
          : null;
      throw JehoApiException(_safeMessage(message, response.statusCode));
    }

    if (decoded is! Map<String, dynamic>) {
      throw const JehoApiException('استجابة تسجيل الدخول غير مكتملة.');
    }

    // Accept both direct NestJS responses and the app's { data: ... } envelope.
    final payload = decoded['data'] is Map<String, dynamic>
        ? decoded['data'] as Map<String, dynamic>
        : decoded;
    final accessToken = payload['accessToken'];
    final refreshToken = payload['refreshToken'];

    if (accessToken is! String || accessToken.isEmpty) {
      throw const JehoApiException('لم يرجع الخادم رمز دخول صالحًا.');
    }

    await _secureStorage.write(key: 'jeho_access_token', value: accessToken);
    if (refreshToken is String && refreshToken.isNotEmpty) {
      await _secureStorage.write(key: 'jeho_refresh_token', value: refreshToken);
    }
    return payload;
  }

  Future<Map<String, dynamic>> getMyProfile(String accessToken) async {
    final payload = await _get('users/me', accessToken: accessToken);
    if (payload is Map<String, dynamic>) return payload;
    throw const JehoApiException('تعذر قراءة بيانات الملف الشخصي.');
  }

  Future<List<Map<String, dynamic>>> getConversationMessages(
    String accessToken,
    String conversationId,
  ) async {
    final payload = await _get(
      'chat/conversations/$conversationId/messages?page=1&limit=50',
      accessToken: accessToken,
    );
    dynamic items = payload;
    if (items is Map<String, dynamic>) {
      items = items['items'] ?? items['results'] ?? items['data'] ?? items['messages'];
    }
    if (items is! List) return const [];
    return items.whereType<Map<String, dynamic>>().toList(growable: true);
  }

  Future<Map<String, dynamic>> sendTextMessage(
    String accessToken,
    String conversationId,
    String content,
  ) async {
    final payload = await _post(
      'chat/conversations/$conversationId/messages',
      {'type': 'text', 'content': content},
      accessToken: accessToken,
    );
    if (payload is Map<String, dynamic>) return payload;
    throw const JehoApiException('لم يرجع الخادم بيانات الرسالة المرسلة.');
  }

  Future<Map<String, dynamic>> updateMyProfile(
    String accessToken,
    Map<String, Object?> changes,
  ) async {
    final payload = await _patch('users/me', changes, accessToken: accessToken);
    if (payload is Map<String, dynamic>) return payload;
    throw const JehoApiException('تعذر تحديث الملف الشخصي.');
  }

  Future<List<Map<String, dynamic>>> getConversations(String accessToken) async {
    final payload = await _get(
      'chat/conversations?page=1&limit=20',
      accessToken: accessToken,
    );
    dynamic items = payload;
    if (items is Map<String, dynamic>) {
      items = items['items'] ?? items['results'] ?? items['data'] ?? items['conversations'];
    }
    if (items is! List) return const [];
    return items.whereType<Map<String, dynamic>>().toList(growable: false);
  }

  Future<List<Map<String, dynamic>>> getCosmetics({String? type}) async {
    final suffix = type == null ? '' : '?type=${Uri.encodeQueryComponent(type)}';
    final payload = await _get('cosmetics$suffix');
    dynamic items = payload;
    if (items is Map<String, dynamic>) {
      items = items['items'] ?? items['results'] ?? items['cosmetics'] ?? items['data'];
    }
    if (items is! List) return const [];
    return items.whereType<Map<String, dynamic>>().toList(growable: false);
  }

  Future<List<Map<String, dynamic>>> getCosmeticInventory(String accessToken) async {
    final payload = await _get('cosmetics/inventory', accessToken: accessToken);
    dynamic items = payload;
    if (items is Map<String, dynamic>) {
      items = items['items'] ?? items['results'] ?? items['inventory'] ?? items['data'];
    }
    if (items is! List) return const [];
    return items.whereType<Map<String, dynamic>>().toList(growable: false);
  }

  Future<Map<String, dynamic>> purchaseCosmetic(String accessToken, String cosmeticId) async {
    final payload = await _post('cosmetics/purchase', {'cosmeticId': cosmeticId}, accessToken: accessToken);
    if (payload is Map<String, dynamic>) return payload;
    return <String, dynamic>{};
  }

  Future<Map<String, dynamic>> equipCosmetic(String accessToken, String cosmeticId) async {
    final payload = await _post('cosmetics/equip', {'cosmeticId': cosmeticId}, accessToken: accessToken);
    if (payload is Map<String, dynamic>) return payload;
    return <String, dynamic>{};
  }

  Future<Map<String, dynamic>> unequipCosmetic(String accessToken, String cosmeticId) async {
    final payload = await _post('cosmetics/unequip', {'cosmeticId': cosmeticId}, accessToken: accessToken);
    if (payload is Map<String, dynamic>) return payload;
    return <String, dynamic>{};
  }

  // Room lifecycle and moderation (business rules remain enforced by the API).
  Future<Map<String, dynamic>> createRoom(String token, Map<String, Object?> data) async =>
      _asMap(await _post('rooms', data, accessToken: token), 'تعذر إنشاء الغرفة.');

  Future<Map<String, dynamic>> joinRoom(String token, String roomId, {String? password}) async =>
      _asMap(await _post('rooms/$roomId/join', {if (password != null) 'password': password}, accessToken: token), 'تعذر دخول الغرفة.');

  Future<Map<String, dynamic>> leaveRoom(String token, String roomId) async =>
      _asMap(await _post('rooms/$roomId/leave', const {}, accessToken: token), 'تعذر مغادرة الغرفة.');

  Future<Map<String, dynamic>> takeSeat(String token, String roomId, int seatIndex) async =>
      _asMap(await _post('rooms/$roomId/seats/take', {'seatIndex': seatIndex}, accessToken: token), 'تعذر حجز المقعد.');

  Future<Map<String, dynamic>> leaveSeat(String token, String roomId) async =>
      _asMap(await _post('rooms/$roomId/seats/leave', const {}, accessToken: token), 'تعذر مغادرة المقعد.');

  Future<Map<String, dynamic>> kickRoomUser(String token, String roomId, String userId) async =>
      _asMap(await _post('rooms/$roomId/kick', {'userId': userId}, accessToken: token), 'تعذر إخراج المستخدم.');

  Future<Map<String, dynamic>> banRoomUser(String token, String roomId, String userId, {int durationMinutes = 30}) async =>
      _asMap(await _post('rooms/$roomId/ban', {'userId': userId, 'durationMinutes': durationMinutes}, accessToken: token), 'تعذر حظر المستخدم.');

  Future<Map<String, dynamic>> forceLeaveSeat(String token, String roomId, String userId, {String? reason}) async =>
      _asMap(await _post('rooms/$roomId/seats/force-leave', {'userId': userId, if (reason != null) 'reason': reason}, accessToken: token), 'تعذر إنزال المستخدم من المقعد.');

  Future<Map<String, dynamic>> setRoomGiftSounds(String token, String roomId, {required bool enabled}) async =>
      _asMap(await _post('rooms/$roomId/gift-sounds', {'enabled': enabled}, accessToken: token), 'تعذر تحديث أصوات الهدايا.');

  Future<Map<String, dynamic>> setRoomDisplaySettings(String token, String roomId, Map<String, Object?> settings) async =>
      _asMap(await _post('rooms/$roomId/display-settings', settings, accessToken: token), 'تعذر تحديث إعدادات عرض الغرفة.');

  Future<Map<String, dynamic>> clearRoomChat(String token, String roomId) async =>
      _asMap(await _post('rooms/$roomId/chat/clear', const {}, accessToken: token), 'تعذر مسح دردشة الغرفة.');

  Future<Map<String, dynamic>> setRoomChatAutoClear(String token, String roomId, int minutes) async =>
      _asMap(await _post('rooms/$roomId/chat/auto-clear', {'minutes': minutes}, accessToken: token), 'تعذر تحديث مؤقت مسح الدردشة.');

  Future<Map<String, dynamic>> closeRoom(String token, String roomId) async =>
      _asMap(await _post('rooms/$roomId/close', const {}, accessToken: token), 'تعذر إنهاء الغرفة.');

  Future<Map<String, dynamic>> followRoom(String token, String roomId, {required bool follow}) async {
    final result = follow
        ? await _post('rooms/$roomId/follow', const {}, accessToken: token)
        : await _delete('rooms/$roomId/follow', accessToken: token);
    return _asMap(result, 'تعذر تحديث متابعة الغرفة.');
  }

  Future<Map<String, dynamic>> sendAllMicGift(String token, {required String giftId, required List<String> receiverIds, required String roomId, int quantity = 1}) async =>
      _asMap(await _post('gifts/send-all-mic', {'giftId': giftId, 'receiverIds': receiverIds, 'roomId': roomId, 'quantity': quantity}, accessToken: token), 'تعذر إرسال الهدية الجماعية.');

  Future<List<Map<String, dynamic>>> getGiftHistory(String token) async =>
      _asList(await _get('gifts/history?page=1&limit=30', accessToken: token), ['items', 'results', 'history', 'data']);

  Future<List<Map<String, dynamic>>> getVanityIds() async =>
      _asList(await _get('vanity-ids'), ['items', 'results', 'data']);

  Future<Map<String, dynamic>> getMyVanityId(String token) async =>
      _asMap(await _get('vanity-ids/mine', accessToken: token), 'تعذر تحميل الآي دي المميز.');

  Future<Map<String, dynamic>> reserveVanityId(String token, String publicId) async =>
      _asMap(await _post('vanity-ids/id/$publicId/reserve', const {}, accessToken: token), 'تعذر حجز الآي دي.');

  Future<Map<String, dynamic>> purchaseVanityId(String token, String publicId) async =>
      _asMap(await _post('vanity-ids/id/$publicId/purchase', const {}, accessToken: token), 'تعذر شراء الآي دي.');

  Future<List<Map<String, dynamic>>> getMyApplications(String token) async =>
      _asList(await _get('agencies/applications/mine', accessToken: token), ['items', 'results', 'applications', 'data']);

  Future<Map<String, dynamic>> lockRoomSeat(String token, String roomId, int seatIndex, {bool locked = true}) async =>
      _asMap(
        await _post('rooms/$roomId/seats/lock', {'seatIndex': seatIndex, 'locked': locked}, accessToken: token),
        'تعذر تحديث قفل المقعد.',
      );

  Future<Map<String, dynamic>> setRoomPasswordLock(
    String token,
    String roomId, {
    required bool locked,
    String? password,
  }) async =>
      _asMap(
        await _post(
          'rooms/$roomId/lock',
          {'locked': locked, if (password != null && password.isNotEmpty) 'password': password},
          accessToken: token,
        ),
        'تعذر تحديث قفل الغرفة.',
      );

  Future<List<Map<String, dynamic>>> getSeatRequests(String token, String roomId) async =>
      _asList(await _get('rooms/$roomId/seat-requests', accessToken: token), ['items', 'results', 'requests', 'data']);

  Future<Map<String, dynamic>> respondSeatRequest(
    String token,
    String roomId,
    String userId, {
    required bool accept,
    int? seatIndex,
  }) async =>
      _asMap(
        await _post(
          'rooms/$roomId/seat-requests/${accept ? 'approve' : 'reject'}',
          {'userId': userId, if (accept && seatIndex != null) 'seatIndex': seatIndex},
          accessToken: token,
        ),
        'تعذر تحديث طلب المقعد.',
      );

  Future<Map<String, dynamic>> inviteUserToSeat(String token, String roomId, String userId, {int? seatIndex}) async =>
      _asMap(await _post('rooms/$roomId/seat-invites', {'userId': userId, if (seatIndex != null) 'seatIndex': seatIndex}, accessToken: token), 'تعذر إرسال دعوة المقعد.');

  Future<Map<String, dynamic>> respondSeatInvite(String token, String roomId, {required bool accept}) async =>
      _asMap(await _post('rooms/$roomId/seat-invites/respond', {'accept': accept}, accessToken: token), 'تعذر الرد على دعوة المقعد.');

  Future<Map<String, dynamic>> setRoomModerator(String token, String roomId, String userId, {required bool enabled}) async {
    final payload = enabled
        ? await _post('rooms/$roomId/moderators/$userId', const {}, accessToken: token)
        : await _delete('rooms/$roomId/moderators/$userId', accessToken: token);
    return _asMap(payload, 'تعذر تحديث مشرف الغرفة.');
  }

  Future<Map<String, dynamic>> updateModeratorPermissions(String token, String roomId, String userId, Map<String, Object?> permissions) async =>
      _asMap(await _patch('rooms/$roomId/moderators/$userId/permissions', permissions, accessToken: token), 'تعذر تحديث صلاحيات المشرف.');

  Future<Map<String, dynamic>> setRoomLock(String token, String roomId, {required bool locked, String? password}) async =>
      setRoomPasswordLock(token, roomId, locked: locked, password: password);


  Future<Map<String, dynamic>> raiseHand(String token, String roomId, {bool raised = true, int? seatIndex}) async =>
      _asMap(await _post('rooms/$roomId/raise-hand', {'raised': raised, if (seatIndex != null) 'seatIndex': seatIndex}, accessToken: token), 'تعذر إرسال طلب المايك.');

  Future<Map<String, dynamic>> sendGift(String token, {required String giftId, required String receiverId, String? roomId, int quantity = 1}) async =>
      _asMap(await _post('gifts/send', {'giftId': giftId, 'receiverId': receiverId, 'quantity': quantity, if (roomId != null) 'roomId': roomId}, accessToken: token), 'تعذر إرسال الهدية.');

  Future<Map<String, dynamic>> sendMessageAttachment(String token, String conversationId, String url, {required String mimeType, required int size, String? caption, String? type}) async =>
      _asMap(await _post('chat/conversations/$conversationId/messages', {'type': type ?? (mimeType.startsWith('image/') ? 'image' : mimeType.startsWith('video/') ? 'video' : mimeType.startsWith('audio/') ? 'audio' : 'file'), 'content': caption ?? '', 'media': {'url': url, 'mimeType': mimeType, 'size': size}}, accessToken: token), 'تعذر إرسال المرفق.');

  Future<Map<String, dynamic>> setConversationMuted(String token, String conversationId, {required bool muted}) async =>
      _asMap(await _post('chat/conversations/$conversationId/mute', {'muted': muted}, accessToken: token), 'تعذر تحديث كتم المحادثة.');

  Future<Map<String, dynamic>> setConversationPinned(String token, String conversationId, {required bool pinned}) async =>
      _asMap(await _post('chat/conversations/$conversationId/pin', {'pinned': pinned}, accessToken: token), 'تعذر تحديث تثبيت المحادثة.');

  Future<Map<String, dynamic>> setConversationArchived(String token, String conversationId, {required bool archived}) async =>
      _asMap(await _post('chat/conversations/$conversationId/archive', {'archived': archived}, accessToken: token), 'تعذر تحديث أرشفة المحادثة.');

  Future<Map<String, dynamic>> deleteConversationForMe(String token, String conversationId) async =>
      _asMap(await _post('chat/conversations/$conversationId/delete', const {}, accessToken: token), 'تعذر حذف المحادثة من قائمتك.');

  Future<Map<String, dynamic>> editMessage(String token, String messageId, String content) async =>
      _asMap(await _patch('chat/messages/$messageId', {'content': content}, accessToken: token), 'تعذر تعديل الرسالة.');

  Future<Map<String, dynamic>> unsendMessage(String token, String messageId) async =>
      _asMap(await _post('chat/messages/$messageId/unsend', const {}, accessToken: token), 'تعذر سحب الرسالة.');

  Future<Map<String, dynamic>> startConversation(String token, String peerId) async =>
      _asMap(await _post('chat/conversations', {'peerId': peerId}, accessToken: token), 'تعذر بدء المحادثة.');

  Future<List<Map<String, dynamic>>> searchUsers(String query) async {
    final payload = await _get('users/search?q=${Uri.encodeQueryComponent(query)}&page=1&limit=20');
    return _asList(payload, ['items', 'results', 'users', 'data']);
  }

  Future<Map<String, dynamic>> socialRequest(String token, String userId, {String type = 'friend'}) async =>
      _asMap(await _post('users/$userId/request', {'type': type}, accessToken: token), 'تعذر إرسال طلب العلاقة.');

  Future<Map<String, dynamic>> respondSocialRequest(String token, String requestId, {required bool accept}) async =>
      _asMap(await _post('users/me/requests/$requestId/${accept ? 'accept' : 'reject'}', const {}, accessToken: token), 'تعذر تحديث الطلب.');

  Future<List<Map<String, dynamic>>> getIncomingRequests(String token) async =>
      _asList(await _get('users/me/requests', accessToken: token), ['items', 'results', 'requests', 'data']);

  Future<List<Map<String, dynamic>>> getDailyTasks(String token) async =>
      _asList(await _get('tasks/daily', accessToken: token), ['items', 'results', 'tasks', 'data']);

  Future<Map<String, dynamic>> claimTask(String token, String taskId) async =>
      _asMap(await _post('tasks/daily/$taskId/claim', const {}, accessToken: token), 'تعذر استلام المكافأة.');

  Future<Map<String, dynamic>> dailyCheckIn(String token) async =>
      _asMap(await _post('tasks/checkin', const {}, accessToken: token), 'تعذر تسجيل الحضور.');

  Future<List<Map<String, dynamic>>> getAgencies() async =>
      _asList(await _get('agencies?page=1&limit=30'), ['items', 'results', 'agencies', 'data']);

  Future<List<Map<String, dynamic>>> getAgencyJoinRequests(String token, String agencyId) async =>
      _asList(await _get('agencies/$agencyId/join-requests', accessToken: token), ['items', 'results', 'requests', 'data']);

  Future<Map<String, dynamic>> respondAgencyJoinRequest(String token, String agencyId, String userId, {required bool approve}) async =>
      _asMap(
        await _post('agencies/$agencyId/join-requests/$userId/${approve ? 'approve' : 'reject'}', const {}, accessToken: token),
        'تعذر تحديث طلب الانضمام للوكالة.',
      );

  Future<Map<String, dynamic>> getAgencyEarnings(String token, String agencyId) async =>
      _asMap(await _get('agencies/$agencyId/earnings', accessToken: token), 'تعذر تحميل أرباح الوكالة.');

  Future<Map<String, dynamic>> getMyAgency(String token) async =>
      _asMap(await _get('agencies/mine', accessToken: token), 'لا توجد وكالة مرتبطة بالحساب أو تعذر تحميلها.');

  Future<Map<String, dynamic>> joinAgencyByCode(String token, String code) async =>
      _asMap(await _post('agencies/join-by-code', {'code': code.trim()}, accessToken: token), 'تعذر إرسال طلب الانضمام للوكالة.');

  Future<Map<String, dynamic>> applyForAgency(String token, Map<String, Object?> data) async =>
      _asMap(await _post('agencies/applications', data, accessToken: token), 'تعذر إرسال طلب الوكالة.');

  Future<List<Map<String, dynamic>>> getCpDiscover({String? type}) async {
    final suffix = type == null ? '' : '?type=${Uri.encodeQueryComponent(type)}';
    return _asList(await _get('cp/discover$suffix'), ['items', 'results', 'data']);
  }

  Future<Map<String, dynamic>> getCpStatus(String token) async =>
      _asMap(await _get('cp/me', accessToken: token), 'تعذر تحميل قسم CP.');

  Future<List<Map<String, dynamic>>> getPaymentPackages() async => getRechargePackages();

  Future<Map<String, dynamic>> createRechargeOrder(String token, {required String sku, required String provider, String currency = 'USD'}) async =>
      _asMap(await _post('wallet/recharge', {'sku': sku, 'provider': provider, 'currency': currency}, accessToken: token), 'تعذر إنشاء طلب الشحن.');

  Future<Map<String, dynamic>> exchangeDiamonds(String token, int diamonds) async =>
      _asMap(await _post('wallet/exchange', {'diamonds': diamonds}, accessToken: token), 'تعذر تبديل الرصيد.');

  /// Provider-specific checkout paths mirror PaymentsController exactly.
  /// Never send arbitrary provider names to a guessed /payments/:provider URL.
  Future<Map<String, dynamic>> createPaymentCheckout(
    String token,
    String provider,
    Map<String, Object?> data,
  ) async {
    final normalized = provider.trim().toLowerCase().replaceAll('_', '-');
    final routes = <String, String>{
      'stripe': 'payments/stripe/checkout',
      'stripe/checkout': 'payments/stripe/checkout',
      'fourthwall': 'payments/fourthwall/checkout',
      'fourthwall/checkout': 'payments/fourthwall/checkout',
      'paypal': 'payments/paypal/create',
      'paypal/create': 'payments/paypal/create',
      'google-play': 'payments/google-play/verify',
      'googleplay': 'payments/google-play/verify',
      'crypto': 'payments/crypto/orders',
      'binance-wallet': 'payments/binance-wallet/orders',
      'binance-wallet-deposit-address': 'payments/binance-wallet/deposit-address',
      'binance-pay': 'payments/binance-pay/orders',
      'sham-cash': 'payments/sham-cash/orders',
      'shamcash': 'payments/sham-cash/orders',
    };
    final route = routes[normalized];
    if (route == null) {
      throw const JehoApiException('طريقة الدفع غير مدعومة. اختر طريقة ظاهرة في إعدادات الخادم.');
    }
    return _asMap(
      await _post(route, data, accessToken: token),
      'تعذر إنشاء عملية الدفع.',
    );
  }

  Future<Map<String, dynamic>> getWalletEconomyConfig() async =>
      _asMap(await _get('wallet/economy-config'), 'تعذر تحميل إعدادات المحفظة.');

  Future<List<Map<String, dynamic>>> getWithdrawPackages() async =>
      _asList(await _get('wallet/withdraw-packages'), ['items', 'results', 'packages', 'data']);

  Future<List<Map<String, dynamic>>> getMyWithdrawals(String token) async =>
      _asList(await _get('wallet/withdraws?page=1&limit=30', accessToken: token), ['items', 'results', 'withdraws', 'data']);

  Future<Map<String, dynamic>> requestWithdrawal(
    String token, {
    required int diamonds,
    required String method,
    required Map<String, Object?> payoutDetails,
    String? agentId,
  }) async =>
      _asMap(
        await _post('wallet/withdraw', {
          'diamonds': diamonds,
          'method': method,
          'payoutDetails': payoutDetails,
          if (agentId != null && agentId.isNotEmpty) 'agentId': agentId,
        }, accessToken: token),
        'تعذر إرسال طلب السحب.',
      );

  Future<Map<String, dynamic>> createCpRequest(
    String token, {
    required String recipientId,
    required String type,
    required String roomId,
    required String idempotencyKey,
  }) async =>
      _asMap(
        await _post('cp/requests', {
          'recipientId': recipientId,
          'type': type,
          'roomId': roomId,
          'idempotencyKey': idempotencyKey,
        }, accessToken: token),
        'تعذر إرسال طلب CP.',
      );

  Future<List<Map<String, dynamic>>> getCpRequests(String token) async =>
      _asList(await _get('cp/requests', accessToken: token), ['items', 'results', 'requests', 'data']);

  Future<Map<String, dynamic>> respondCpRequest(
    String token,
    String requestId, {
    required bool accept,
  }) async =>
      _asMap(
        await _post('cp/requests/$requestId/respond', {'accept': accept}, accessToken: token),
        'تعذر الرد على طلب CP.',
      );

  Future<Map<String, dynamic>> cancelCpRequest(String token, String requestId) async =>
      _asMap(await _post('cp/requests/$requestId/cancel', const {}, accessToken: token), 'تعذر إلغاء طلب CP.');

  Future<Map<String, dynamic>> endCpRelationship(String token, String relationshipId) async =>
      _asMap(await _post('cp/$relationshipId/end', const {}, accessToken: token), 'تعذر إنهاء العلاقة.');

  Future<Map<String, dynamic>> followUser(String token, String userId, {required bool follow}) async {
    final payload = follow
        ? await _post('users/$userId/follow', const {}, accessToken: token)
        : await _delete('users/$userId/follow', accessToken: token);
    return _asMap(payload, 'تعذر تحديث المتابعة.');
  }

  Future<Map<String, dynamic>> blockUser(String token, String userId, {String? reason}) async =>
      _asMap(await _post('users/$userId/block', {if (reason != null && reason.trim().isNotEmpty) 'reason': reason.trim()}, accessToken: token), 'تعذر حظر المستخدم.');

  Future<Map<String, dynamic>> unblockUser(String token, String userId) async =>
      _asMap(await _delete('users/$userId/block', accessToken: token), 'تعذر إلغاء الحظر.');

  Future<Map<String, dynamic>> endSocialBond(String token, String userId, {String? type}) async {
    final suffix = type == null ? '' : '?type=${Uri.encodeQueryComponent(type)}';
    return _asMap(await _delete('users/$userId/bond$suffix', accessToken: token), 'تعذر إنهاء العلاقة.');
  }

  Future<List<Map<String, dynamic>>> getMyBlocks(String token) async =>
      _asList(await _get('users/me/blocks?page=1&limit=30', accessToken: token), ['items', 'results', 'blocks', 'data']);

  Future<Map<String, dynamic>> pollPaymentOrder(String token, String provider, String orderId) async {
    final normalized = provider.trim().toLowerCase().replaceAll('_', '-');
    final routes = <String, String>{
      'fourthwall': 'payments/fourthwall/orders/$orderId',
      'sham-cash': 'payments/sham-cash/orders/$orderId',
      'shamcash': 'payments/sham-cash/orders/$orderId',
      'binance-wallet': 'payments/binance-wallet/orders/$orderId',
      'binance-pay': 'payments/binance-pay/orders/$orderId',
    };
    final route = routes[normalized];
    if (route == null) {
      throw const JehoApiException('لا تتوفر حالة طلب لهذه الطريقة.');
    }
    return _asMap(await _get(route, accessToken: token), 'تعذر قراءة حالة طلب الدفع.');
  }

  Map<String, dynamic> _asMap(dynamic payload, String error) {
    if (payload is Map<String, dynamic>) return payload;
    throw JehoApiException(error);
  }

  List<Map<String, dynamic>> _asList(dynamic payload, List<String> keys) {
    dynamic items = payload;
    if (items is Map<String, dynamic>) {
      for (final key in keys) {
        if (items[key] is List) { items = items[key]; break; }
      }
    }
    if (items is! List) return const [];
    return items.whereType<Map<String, dynamic>>().toList(growable: false);
  }

  Future<Map<String, dynamic>> getWallet(String accessToken) async {
    final payload = await _get('wallet', accessToken: accessToken);
    if (payload is Map<String, dynamic>) return payload;
    throw const JehoApiException('تعذر تحميل المحفظة.');
  }

  Future<Map<String, dynamic>> getMyCp(String accessToken) async {
    final payload = await _get('users/me/cp', accessToken: accessToken);
    if (payload is Map<String, dynamic>) return payload;
    return <String, dynamic>{'data': payload};
  }

  Future<List<Map<String, dynamic>>> getMyFriends(String accessToken) async {
    final payload = await _get('users/me/friends?page=1&limit=30', accessToken: accessToken);
    dynamic items = payload;
    if (items is Map<String, dynamic>) items = items['items'] ?? items['results'] ?? items['friends'] ?? items['data'];
    if (items is! List) return const [];
    return items.whereType<Map<String, dynamic>>().toList(growable: false);
  }

  Future<List<Map<String, dynamic>>> getMyVisitors(String accessToken) async {
    final payload = await _get('users/me/visitors?page=1&limit=30', accessToken: accessToken);
    dynamic items = payload;
    if (items is Map<String, dynamic>) items = items['items'] ?? items['results'] ?? items['visitors'] ?? items['data'];
    if (items is! List) return const [];
    return items.whereType<Map<String, dynamic>>().toList(growable: false);
  }

  Future<List<Map<String, dynamic>>> getMyRelations(String accessToken, {String? type}) async {
    final suffix = type == null ? '' : '?type=${Uri.encodeQueryComponent(type)}';
    final payload = await _get('users/me/relations$suffix', accessToken: accessToken);
    dynamic items = payload;
    if (items is Map<String, dynamic>) items = items['items'] ?? items['results'] ?? items['relations'] ?? items['data'];
    if (items is! List) return const [];
    return items.whereType<Map<String, dynamic>>().toList(growable: false);
  }

  Future<List<Map<String, dynamic>>> getWalletTransactions(String accessToken) async {
    final payload = await _get('wallet/transactions?page=1&limit=30', accessToken: accessToken);
    dynamic items = payload;
    if (items is Map<String, dynamic>) {
      items = items['items'] ?? items['results'] ?? items['transactions'] ?? items['data'];
    }
    if (items is! List) return const [];
    return items.whereType<Map<String, dynamic>>().toList(growable: false);
  }

  Future<List<Map<String, dynamic>>> getRechargePackages() async {
    final payload = await _get('wallet/packages');
    dynamic items = payload;
    if (items is Map<String, dynamic>) {
      items = items['items'] ?? items['results'] ?? items['packages'] ?? items['data'];
    }
    if (items is! List) return const [];
    return items.whereType<Map<String, dynamic>>().toList(growable: false);
  }

  Future<List<Map<String, dynamic>>> getGiftCategories() async {
    final payload = await _get('gifts/categories');
    dynamic items = payload;
    if (items is Map<String, dynamic>) {
      items = items['items'] ?? items['results'] ?? items['categories'] ?? items['data'];
    }
    if (items is! List) return const [];
    return items.whereType<Map<String, dynamic>>().toList(growable: false);
  }

  Future<List<Map<String, dynamic>>> getGiftCatalog() async {
    final payload = await _get('gifts');
    dynamic items = payload;
    if (items is Map<String, dynamic>) {
      items = items['items'] ?? items['results'] ?? items['gifts'] ?? items['data'];
    }
    if (items is! List) return const [];
    return items.whereType<Map<String, dynamic>>().toList(growable: false);
  }

  Future<String> uploadImage(String accessToken, File file) async {
    final base = Uri.parse(baseUrl.endsWith('/') ? baseUrl : '$baseUrl/');
    final uri = base.resolve('uploads');
    final request = http.MultipartRequest('POST', uri)
      ..headers['Accept'] = 'application/json'
      ..headers['Authorization'] = 'Bearer $accessToken'
      ..files.add(await http.MultipartFile.fromPath('file', file.path));
    http.StreamedResponse streamed;
    try {
      streamed = await request.send().timeout(const Duration(seconds: 60));
    } on Exception {
      throw const JehoApiException('تعذر رفع الصورة. تحقق من الإنترنت ومساحة التخزين.');
    }
    final response = await http.Response.fromStream(streamed);
    final payload = _decodeResponse(response);
    if (payload is! Map<String, dynamic>) {
      throw const JehoApiException('الخادم لم يرجع رابط الصورة.');
    }
    final path = (payload['url'] ?? payload['path'])?.toString();
    if (path == null || path.isEmpty) {
      throw const JehoApiException('الخادم لم يرجع رابط الصورة.');
    }
    if (path.startsWith('https://') || path.startsWith('http://')) return path;
    // Upload URLs are returned as /uploads/<filename>. In cloud deployments
    // the durable-storage controller lives under the API prefix, so resolve
    // that path through the configured API base instead of the static root.
    final normalizedPath = path.replaceFirst(RegExp(r'^/uploads/'), 'uploads/');
    if (normalizedPath.startsWith('uploads/')) {
      return base.resolve(normalizedPath).toString();
    }
    final origin = Uri(
      scheme: base.scheme,
      host: base.host,
      port: base.hasPort ? base.port : null,
    );
    return origin.resolve(normalizedPath.startsWith('/') ? normalizedPath : '/$normalizedPath').toString();
  }

  Future<Map<String, dynamic>> updateRoomCover(
    String accessToken,
    String roomId,
    String coverUrl,
  ) async {
    final payload = await _patch(
      'rooms/$roomId',
      {'coverUrl': coverUrl},
      accessToken: accessToken,
    );
    if (payload is Map<String, dynamic>) return payload;
    return <String, dynamic>{};
  }

  Future<Map<String, dynamic>> setRoomBackground(
    String accessToken,
    String roomId,
    String? backgroundUrl,
  ) async {
    final payload = await _patch(
      'rooms/$roomId/background',
      {'backgroundUrl': backgroundUrl},
      accessToken: accessToken,
    );
    if (payload is Map<String, dynamic>) return payload;
    return <String, dynamic>{};
  }

  Future<List<Map<String, dynamic>>> getPublicRooms() async {
    final payload = await _get('rooms?page=1&limit=20');
    dynamic items = payload;
    if (items is Map<String, dynamic>) {
      items = items['items'] ?? items['results'] ?? items['data'] ?? items['rooms'];
    }
    if (items is! List) return const [];
    return items.whereType<Map<String, dynamic>>().toList(growable: false);
  }

  Future<dynamic> _post(
    String endpoint,
    Map<String, Object?> body, {
    String? accessToken,
  }) async {
    final base = Uri.parse(baseUrl.endsWith('/') ? baseUrl : '$baseUrl/');
    final uri = base.resolve(endpoint);
    http.Response response;
    try {
      response = await _client.post(uri, headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json',
        if (accessToken != null && accessToken.isNotEmpty)
          'Authorization': 'Bearer $accessToken',
      }, body: jsonEncode(body)).timeout(const Duration(seconds: 20));
    } on Exception {
      throw const JehoApiException('تعذّر الاتصال بالخادم. تحقق من الإنترنت وحاول مرة أخرى.');
    }
    return _decodeResponse(response);
  }

  Future<dynamic> _delete(String endpoint, {String? accessToken}) async {
    final base = Uri.parse(baseUrl.endsWith('/') ? baseUrl : '$baseUrl/');
    final uri = base.resolve(endpoint);
    http.Response response;
    try {
      response = await _client.delete(uri, headers: {
        'Accept': 'application/json',
        if (accessToken != null && accessToken.isNotEmpty)
          'Authorization': 'Bearer $accessToken',
      }).timeout(const Duration(seconds: 20));
    } on Exception {
      throw const JehoApiException('تعذّر الاتصال بالخادم. تحقق من الإنترنت وحاول مرة أخرى.');
    }
    return _decodeResponse(response);
  }

  Future<dynamic> _patch(
    String endpoint,
    Map<String, Object?> body, {
    String? accessToken,
  }) async {
    final base = Uri.parse(baseUrl.endsWith('/') ? baseUrl : '$baseUrl/');
    final uri = base.resolve(endpoint);
    http.Response response;
    try {
      response = await _client.patch(uri, headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json',
        if (accessToken != null && accessToken.isNotEmpty)
          'Authorization': 'Bearer $accessToken',
      }, body: jsonEncode(body)).timeout(const Duration(seconds: 20));
    } on Exception {
      throw const JehoApiException('تعذّر الاتصال بالخادم. تحقق من الإنترنت وحاول مرة أخرى.');
    }
    return _decodeResponse(response);
  }

  dynamic _decodeResponse(http.Response response) {
    dynamic decoded;
    try {
      decoded = jsonDecode(response.body);
    } on FormatException {
      throw const JehoApiException('استجابة الخادم غير مفهومة.');
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      final message = decoded is Map<String, dynamic>
          ? (decoded['message'] ?? decoded['error'])
          : null;
      throw JehoApiException(_safeMessage(message, response.statusCode));
    }
    if (decoded is Map<String, dynamic> && decoded.containsKey('data')) {
      return decoded['data'];
    }
    return decoded;
  }

  Future<dynamic> _get(String endpoint, {String? accessToken}) async {
    final base = Uri.parse(baseUrl.endsWith('/') ? baseUrl : '$baseUrl/');
    final uri = base.resolve(endpoint);
    http.Response response;
    try {
      response = await _client.get(
        uri,
        headers: {
          'Accept': 'application/json',
          if (accessToken != null && accessToken.isNotEmpty)
            'Authorization': 'Bearer $accessToken',
        },
      ).timeout(const Duration(seconds: 20));
    } on Exception {
      throw const JehoApiException(
        'تعذّر الاتصال بالخادم. تحقق من الإنترنت وحاول مرة أخرى.',
      );
    }

    dynamic decoded;
    try {
      decoded = jsonDecode(response.body);
    } on FormatException {
      throw const JehoApiException('استجابة الخادم غير مفهومة.');
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      final message = decoded is Map<String, dynamic>
          ? (decoded['message'] ?? decoded['error'])
          : null;
      throw JehoApiException(_safeMessage(message, response.statusCode));
    }
    if (decoded is Map<String, dynamic> && decoded.containsKey('data')) {
      return decoded['data'];
    }
    return decoded;
  }

  Future<String?> readAccessToken() =>
      _secureStorage.read(key: 'jeho_access_token');

  Future<void> clearSession() async {
    await _secureStorage.delete(key: 'jeho_access_token');
    await _secureStorage.delete(key: 'jeho_refresh_token');
  }

  String _safeMessage(dynamic message, int statusCode) {
    if (message is List && message.isNotEmpty) {
      return message.map((item) => item.toString()).join('\n');
    }
    if (message is String && message.trim().isNotEmpty) {
      if (statusCode == 401) return 'بيانات الدخول غير صحيحة.';
      if (statusCode == 409) return 'البريد أو اسم المستخدم مسجّل مسبقًا.';
      if (statusCode == 429) return 'طلبات كثيرة. انتظر قليلًا ثم حاول مجددًا.';
      return message;
    }
    if (statusCode == 401) return 'بيانات الدخول غير صحيحة.';
    if (statusCode == 429) return 'طلبات كثيرة. انتظر قليلًا ثم حاول مجددًا.';
    return 'تعذّر إكمال الطلب (HTTP $statusCode).';
  }

  void dispose() => _client.close();
}
