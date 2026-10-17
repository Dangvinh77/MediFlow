import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/config/app_config.dart';
import 'package:mediflow_mobile/core/network/api_client.dart';
import 'package:mediflow_mobile/core/network/api_failure.dart';

class RecordingAdapter implements HttpClientAdapter {
  final requests = <RequestOptions>[];
  Object? body = {
    'success': true,
    'data': {'id': 'actual'},
  };
  int status = 200;
  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    return ResponseBody.fromString(
      jsonEncode(body),
      status,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
        'X-Correlation-Id': ['trace-header'],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

void main() {
  late RecordingAdapter adapter;
  late ApiClient client;
  final invalidated = <String>[];
  setUp(() {
    adapter = RecordingAdapter();
    client = ApiClient(
      AppConfig('http://localhost:8080/api'),
      dio: Dio()..httpClientAdapter = adapter,
      token: () => 'signed-token',
      onUnauthorized: invalidated.add,
    );
    invalidated.clear();
  });
  tearDown(() => client.close());
  test('uses one Gateway prefix and exact encoded filter query', () async {
    expect(
      await client.get('/v1/appointments', query: {'page': 2, 'size': 20}),
      {'id': 'actual'},
    );
    expect(adapter.requests.single.uri.path, '/api/v1/appointments');
    expect(adapter.requests.single.uri.queryParameters['page'], '2');
    expect(
      adapter.requests.single.headers['Authorization'],
      'Bearer signed-token',
    );
  });
  test('login is raw, public and never sends the old token', () async {
    adapter.body = {'accessToken': 'a', 'refreshToken': 'r', 'role': 'DOCTOR'};
    final result = await client.post(
      '/v1/auth/login',
      {'username': 'doctor', 'password': 'private'},
      raw: true,
      public: true,
    );
    expect((result as Map)['role'], 'DOCTOR');
    expect(
      adapter.requests.single.headers.containsKey('Authorization'),
      isFalse,
    );
    expect(adapter.requests.single.method, 'POST');
  });
  test('rejects absolute destination before sending credentials', () async {
    await expectLater(
      client.get('https://other.example/v1/patients'),
      throwsArgumentError,
    );
    expect(adapter.requests, isEmpty);
  });
  test(
    '401 reports the exact request token; does not automatically replay',
    () async {
      adapter.status = 401;
      adapter.body = {
        'error': {'code': 'AUTH', 'message': 'Expired'},
        'correlationId': 'trace-body',
      };
      await expectLater(
        client.get('/v1/appointments'),
        throwsA(
          isA<ApiFailure>().having(
            (e) => e.correlationId,
            'correlation',
            'trace-body',
          ),
        ),
      );
      expect(invalidated, ['signed-token']);
      expect(adapter.requests, hasLength(1));
    },
  );
  test('403 does not clear session; field errors survive', () async {
    adapter.status = 403;
    adapter.body = {
      'error': {
        'code': 'DENIED',
        'message': 'Denied',
        'details': [
          {'field': 'sourceId', 'message': 'Wrong source'},
        ],
      },
    };
    await expectLater(
      client.get('/v1/lab'),
      throwsA(
        isA<ApiFailure>()
            .having((e) => e.status, 'status', 403)
            .having((e) => e.fields['sourceId'], 'field', 'Wrong source'),
      ),
    );
    expect(invalidated, isEmpty);
  });
  test(
    'malformed success envelope fails instead of inventing empty data',
    () async {
      adapter.body = {'success': true};
      await expectLater(client.get('/v1/lab'), throwsA(isA<ApiFailure>()));
    },
  );
}
