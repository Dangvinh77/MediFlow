import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../../../core/network/api_client.dart';
import '../domain/auth_repository.dart';
import '../domain/session.dart';
import 'session_dto.dart';

class GatewayAuthRepository implements AuthRepository {
  GatewayAuthRepository(this.client, this.storage, {required String gateway})
    : _key = 'mediflow.session.v1.${base64Url.encode(utf8.encode(gateway))}';
  final ApiClient client;
  final FlutterSecureStorage storage;
  final String _key;
  Future<void> _writes = Future.value();

  Future<void> _write(Future<void> Function() action) {
    final next = _writes.then((_) => action());
    _writes = next.catchError((Object _) {});
    return next;
  }

  @override
  Future<Session?> restore() async {
    final value = await storage.read(key: _key);
    if (value == null) return null;
    try {
      final session = SessionDto.fromJson(
        jsonDecode(value) as Map<String, dynamic>,
      );
      if (!session.isExpired) return session;
    } on FormatException {
      // Invalid/expired local session never grants access to a workspace.
    } on TypeError {
      // Old storage formats are removed rather than interpreted as a new role.
    }
    await clear();
    return null;
  }

  @override
  Future<Session> login(String username, String password) async {
    final response = await client.post(
      '/v1/auth/login',
      {'username': username, 'password': password},
      raw: true,
      public: true,
    );
    if (response is! Map<String, dynamic>) {
      throw const FormatException('Phản hồi đăng nhập không hợp lệ.');
    }
    return SessionDto.fromJson(response);
  }

  @override
  Future<void> save(Session session) => _write(
    () =>
        storage.write(key: _key, value: jsonEncode(SessionDto.toJson(session))),
  );

  @override
  Future<void> clear() => _write(() => storage.delete(key: _key));
}
