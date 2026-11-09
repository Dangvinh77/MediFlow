import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/config/app_config.dart';
import 'package:mediflow_mobile/core/network/api_client.dart';
import 'package:mediflow_mobile/features/medical_record/data/gateway_medical_record_repository.dart';

const recordId = '11111111-1111-4111-8111-111111111111';
const patientId = '33333333-3333-4333-8333-333333333333';

class _Adapter implements HttpClientAdapter {
  _Adapter(this.data);
  final Object? data;
  final requests = <RequestOptions>[];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    return ResponseBody.fromString(
      jsonEncode({'success': true, 'data': data}),
      200,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

GatewayMedicalRecordRepository repositoryFor(_Adapter adapter) =>
    GatewayMedicalRecordRepository(
      ApiClient(
        AppConfig('http://localhost:8080/api'),
        dio: Dio()..httpClientAdapter = adapter,
        token: () => 'token',
      ),
    );

void main() {
  test('reads capabilities through the gateway', () async {
    final adapter = _Adapter({
      'recordId': recordId,
      'patientId': patientId,
      'canRead': true,
      'readBasis': 'CARE_ASSIGNMENT',
      'canWriteContent': true,
      'canComplete': false,
      'canRequestAmendment': false,
      'writeBlockedReason': null,
    });

    final result = await repositoryFor(adapter).capabilities(recordId);

    expect(adapter.requests.single.path, endsWith('v2/records/$recordId/capabilities'));
    expect(result.patientId, patientId);
    expect(result.canWriteContent, isTrue);
    expect(result.canComplete, isFalse);
  });

  test('treats unknown capability payload as read only', () async {
    final result = await repositoryFor(
      _Adapter({'recordId': recordId, 'canWriteContent': 'yes', 'canComplete': 1}),
    ).capabilities(recordId);

    expect(result.canWriteContent, isFalse);
    expect(result.canComplete, isFalse);
    expect(result.patientId, isNull);
  });

  test('treats capabilities for another record as read only', () async {
    final result = await repositoryFor(
      _Adapter({
        'recordId': patientId,
        'patientId': patientId,
        'canRead': true,
        'canWriteContent': true,
        'canComplete': true,
      }),
    ).capabilities(recordId);

    expect(result.canWriteContent, isFalse);
  });

  test('posts the access declaration', () async {
    final adapter = _Adapter({'declarationId': 'd'});

    await repositoryFor(
      adapter,
    ).declareAccess(patientId: patientId, purpose: 'EMERGENCY');

    final request = adapter.requests.single;
    expect(request.method, 'POST');
    expect(request.path, endsWith('v2/records/access-declarations'));
    expect(request.data, {
      'patientId': patientId,
      'purpose': 'EMERGENCY',
      'reason': null,
    });
  });
}
