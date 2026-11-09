import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/config/app_config.dart';
import 'package:mediflow_mobile/core/domain/reference.dart';
import 'package:mediflow_mobile/core/network/api_client.dart';
import 'package:mediflow_mobile/core/network/gateway_reference_reader.dart';

const recordId = '11111111-1111-4111-8111-111111111111';

class _Adapter implements HttpClientAdapter {
  final requests = <RequestOptions>[];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    return ResponseBody.fromString('{}', 500);
  }

  @override
  void close({bool force = false}) {}
}

void main() {
  test('labels a record without reading it', () async {
    final adapter = _Adapter();
    final reader = GatewayReferenceReader(
      ApiClient(
        AppConfig('http://localhost:8080/api'),
        dio: Dio()..httpClientAdapter = adapter,
        token: () => 'token',
      ),
    );

    final option = await reader.read(ReferenceKind.record, recordId);

    expect(adapter.requests, isEmpty);
    expect(option.id, recordId);
    expect(option.name, 'Hồ sơ khám');
  });
}
