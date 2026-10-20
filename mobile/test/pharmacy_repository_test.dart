import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/config/app_config.dart';
import 'package:mediflow_mobile/core/network/api_client.dart';
import 'package:mediflow_mobile/core/network/api_failure.dart';
import 'package:mediflow_mobile/features/pharmacy/data/gateway_pharmacy_repository.dart';

import 'pharmacy_fixtures.dart';

void main() {
  late PharmacyHttpAdapter adapter;
  late ApiClient client;
  late GatewayPharmacyRepository repository;
  setUp(() {
    adapter = PharmacyHttpAdapter();
    client = ApiClient(
      AppConfig('http://localhost:8080/api'),
      dio: Dio()..httpClientAdapter = adapter,
      token: () => 'test-bearer',
    );
    repository = GatewayPharmacyRepository(client);
  });
  tearDown(() => client.close());
  test(
    'search crosses shared Gateway transport with exact query and no writes',
    () async {
      final result = await repository.searchDrugs(
        keyword: '  Natri & C  ',
        page: 1,
      );
      final request = adapter.requests.single;
      expect(request.method, 'GET');
      expect(request.uri.path, '/api/v1/pharmacy/drugs');
      expect(request.uri.queryParameters, {
        'page': '1',
        'size': '20',
        'keyword': 'Natri & C',
      });
      expect(request.headers['Authorization'], 'Bearer test-bearer');
      expect(result.items.single.id, drugId);
      expect(result.totalElements, 21);
    },
  );
  test('blank keyword is omitted and detail paths remain exact', () async {
    await repository.searchDrugs(keyword: ' ');
    expect(
      adapter.requests.last.queryParameters.containsKey('keyword'),
      isFalse,
    );
    adapter.data = drugJson();
    await repository.getDrug(drugId);
    expect(adapter.requests.last.uri.path, '/api/v1/pharmacy/drugs/$drugId');
    adapter.data = prescriptionJson();
    await repository.getPrescription(rxId);
    expect(
      adapter.requests.last.uri.path,
      '/api/v1/pharmacy/prescriptions/$rxId',
    );
  });
  test('invalid parameters never dispatch HTTP', () async {
    await expectLater(repository.getDrug('../secret'), throwsArgumentError);
    await expectLater(repository.getPrescription('abc'), throwsArgumentError);
    await expectLater(repository.searchDrugs(size: 0), throwsArgumentError);
    expect(adapter.requests, isEmpty);
  });
  test('mismatched detail identity is rejected', () async {
    adapter.data = {...drugJson(), 'drugId': otherDrugId};
    await expectLater(repository.getDrug(drugId), throwsFormatException);
    adapter.data = {...prescriptionJson(), 'prescriptionId': otherDrugId};
    await expectLater(repository.getPrescription(rxId), throwsFormatException);
  });
  test('malformed page metadata or row is not an empty result', () async {
    for (final body in [
      null,
      {...pageJson(), 'number': -1},
      {...pageJson(), 'size': null},
      {
        ...pageJson(),
        'content': [null],
      },
      {...pageJson(), 'totalElements': '21'},
    ]) {
      adapter.data = body;
      await expectLater(repository.searchDrugs(), throwsFormatException);
    }
  });
  for (final status in [401, 403, 404, 422, 503]) {
    test(
      '$status preserves backend code, field and correlation without retry',
      () async {
        adapter.status = status;
        adapter.data = {
          'success': false,
          'data': null,
          'error': {
            'code': 'READ_DENIED',
            'message': 'Rejected',
            'details': [
              {'field': 'drugId', 'message': 'Invalid'},
            ],
          },
          'correlationId': 'trace-test',
        };
        await expectLater(
          repository.getDrug(drugId),
          throwsA(
            isA<ApiFailure>()
                .having((e) => e.status, 'status', status)
                .having((e) => e.code, 'code', 'READ_DENIED')
                .having((e) => e.fields['drugId'], 'field', 'Invalid')
                .having((e) => e.correlationId, 'correlation', 'trace-test'),
          ),
        );
        expect(adapter.requests, hasLength(1));
      },
    );
  }
  test(
    'malformed success envelope is an API failure, not an empty page',
    () async {
      adapter.wrapSuccess = false;
      adapter.data = pageJson();
      await expectLater(repository.searchDrugs(), throwsA(isA<ApiFailure>()));
      expect(adapter.requests, hasLength(1));
    },
  );
  test(
    'network failures remain unavailable and never become zero stock',
    () async {
      adapter.networkFailure = true;
      await expectLater(repository.searchDrugs(), throwsA(isA<ApiFailure>()));
      expect(adapter.requests, hasLength(1));
    },
  );
}
