import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/config/app_config.dart';
import 'package:mediflow_mobile/core/network/api_client.dart';
import 'package:mediflow_mobile/core/network/api_failure.dart';
import 'package:mediflow_mobile/features/medical_record/data/gateway_medical_record_repository.dart';
import 'package:mediflow_mobile/features/medical_record/domain/medical_record.dart';

const recordId = '10000000-0000-4000-8000-000000000005';
const patientId = '10000000-0000-4000-8000-000000000001';
const doctorId = '10000000-0000-4000-8000-000000000002';
const departmentId = '10000000-0000-4000-8000-000000000003';
const appointmentId = '10000000-0000-4000-8000-000000000004';
const sourceOrderId = '10000000-0000-4000-8000-000000000007';

class _RecordedRequest {
  const _RecordedRequest(this.options);
  final RequestOptions options;
}

class _ClinicalLabAdapter implements HttpClientAdapter {
  final requests = <_RecordedRequest>[];
  Object? data;
  int status = 200;
  String errorCode = 'HTTP_404';

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(_RecordedRequest(options));
    final Object body = status >= 400
        ? {
            'success': false,
            'data': null,
            'error': {'code': errorCode, 'message': 'Catalogue unavailable'},
          }
        : {'success': true, 'data': data, 'error': null};
    return ResponseBody.fromString(
      jsonEncode(body),
      status,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

Map<String, dynamic> _serviceJson() => {
  'serviceCode': 'CBC001',
  'name': 'Công thức máu toàn bộ',
  'displayGroup': 'HEMATOLOGY',
  'specimenType': 'Máu toàn phần',
  'preparation': 'Nhịn ăn 8 giờ',
  'turnaroundHours': 4,
  'priceCode': 'CBC-APPROVED',
  'templateCode': 'CBC_V1',
  'panel': true,
};

Map<String, dynamic> _sourceOrderJson() => {
  'sourceOrderId': sourceOrderId,
  'recordId': recordId,
  'patientId': patientId,
  'requestingDoctorId': doctorId,
  'requestingDepartmentId': departmentId,
  'careEpisodeType': 'OUTPATIENT_VISIT',
  'careEpisodeId': appointmentId,
  'labType': 'Công thức máu toàn bộ',
  'requestedDate': '2026-10-22',
  'priceCode': 'CBC-APPROVED',
  'serviceCode': 'CBC001',
  'orderedAt': '2026-10-22T09:00:00Z',
};

void main() {
  late _ClinicalLabAdapter adapter;
  late ApiClient client;
  late GatewayMedicalRecordRepository repository;

  setUp(() {
    adapter = _ClinicalLabAdapter();
    client = ApiClient(
      AppConfig('http://localhost:8080/api'),
      dio: Dio()..httpClientAdapter = adapter,
      token: () => 'clinical-session',
    );
    repository = GatewayMedicalRecordRepository(client);
  });
  tearDown(() => client.close());

  test('catalogue DTO preserves the exact Lab service fields', () async {
    adapter.data = [_serviceJson()];

    final items = await repository.labCatalog(group: 'HEMATOLOGY');

    expect(adapter.requests.single.options.method, 'GET');
    expect(adapter.requests.single.options.uri.path, '/api/v2/lab/catalog');
    expect(adapter.requests.single.options.queryParameters, {
      'group': 'HEMATOLOGY',
    });
    expect(items, hasLength(1));
    expect(items!.single.serviceCode, 'CBC001');
    expect(items.single.name, 'Công thức máu toàn bộ');
    expect(items.single.displayGroup, 'HEMATOLOGY');
    expect(items.single.specimenType, 'Máu toàn phần');
    expect(items.single.preparation, 'Nhịn ăn 8 giờ');
    expect(items.single.turnaroundHours, 4);
    expect(items.single.priceCode, 'CBC-APPROVED');
    expect(items.single.templateCode, 'CBC_V1');
    expect(items.single.panel, isTrue);
  });

  test('only the catalogue disabled 404 selects legacy mode', () async {
    adapter
      ..status = 404
      ..errorCode = 'LAB_CATALOG_DISABLED';

    expect(await repository.labCatalog(), isNull);
    expect(adapter.requests, hasLength(1));

    adapter.errorCode = 'NOT_FOUND';
    await expectLater(
      repository.labCatalog(),
      throwsA(
        isA<ApiFailure>()
            .having((failure) => failure.status, 'status', 404)
            .having((failure) => failure.code, 'code', 'NOT_FOUND'),
      ),
    );
  });

  test(
    'serviceCode is decoded and carried into the locked Lab request',
    () async {
      adapter.data = _sourceOrderJson();
      final order = await repository.createClinicalLabOrder(
        recordId,
        const ClinicalLabOrderDraft(
          labType: 'Công thức máu toàn bộ',
          requestedDate: '2026-10-22',
          priceCode: 'CBC-APPROVED',
          serviceCode: 'CBC001',
        ),
      );

      expect(order.serviceCode, 'CBC001');
      expect(adapter.requests.single.options.data, {
        'labType': 'Công thức máu toàn bộ',
        'requestedDate': '2026-10-22',
        'priceCode': 'CBC-APPROVED',
        'serviceCode': 'CBC001',
      });

      adapter.data = {'testId': 'opaque-lab', 'status': 'PENDING'};
      await repository.createLabRequestFromSourceOrder(order);

      expect(adapter.requests.last.options.uri.path, '/api/v1/lab');
      expect(adapter.requests.last.options.data, {
        'recordId': recordId,
        'patientId': patientId,
        'requestingDepartmentId': departmentId,
        'labType': 'Công thức máu toàn bộ',
        'requestedDate': '2026-10-22',
        'sourceOrderId': sourceOrderId,
        'careEpisodeType': 'OUTPATIENT_VISIT',
        'careEpisodeId': appointmentId,
        'priceCode': 'CBC-APPROVED',
        'serviceCode': 'CBC001',
      });
    },
  );
}
