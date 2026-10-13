import 'dart:convert';

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
