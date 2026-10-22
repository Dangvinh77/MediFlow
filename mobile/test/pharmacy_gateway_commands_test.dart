import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/config/app_config.dart';
import 'package:mediflow_mobile/core/network/api_client.dart';
import 'package:mediflow_mobile/core/network/api_failure.dart';
import 'package:mediflow_mobile/features/pharmacy/data/gateway_pharmacy_repository.dart';
import 'package:mediflow_mobile/features/pharmacy/data/prescription_request_dto.dart';
import 'package:mediflow_mobile/features/pharmacy/domain/prescription_commands.dart';

import 'pharmacy_fixtures.dart';
import 'pharmacy_prescription_commands_test.dart' show rxCommand;
import 'pharmacy_dispense_test.dart' show slip;

void main() {
  late PharmacyHttpAdapter adapter;
  late ApiClient client;
  late GatewayPharmacyRepository repository;
  setUp(() {
    adapter = PharmacyHttpAdapter();
    client = ApiClient(
      AppConfig('http://localhost:8080/api'),
      dio: Dio()..httpClientAdapter = adapter,
      token: () => 'test-access',
    );
    repository = GatewayPharmacyRepository(client);
  });
  tearDown(() => client.close());
  test('V1 retry preserves intent header/body, never sends source note or computed price', () async {
    final intent = PrescriptionIntent(
      rxCommand(patient: drugId, doctor: otherDrugId, department: drugId),
    );
    adapter.networkFailure = true;
    await expectLater(
      repository.createPrescription(intent),
      throwsA(isA<ApiFailure>()),
    );
    expect(adapter.requests, hasLength(1));
    adapter.networkFailure = false;
    adapter.data = prescriptionJson()..['recordId'] = drugId;
    final result = await repository.createPrescription(intent);
    expect(result.id, rxId);
    expect(adapter.requests, hasLength(2));
    for (final request in adapter.requests) {
      expect(request.method, 'POST');
      expect(request.uri.path, '/api/v1/pharmacy/prescriptions');
      expect(request.headers['Idempotency-Key'], intent.key);
      expect(request.headers['Authorization'], 'Bearer test-access');
      expect(request.data, PrescriptionRequestDto.outpatient(intent));
      expect((request.data as Map).containsKey('sourceReference'), isFalse);
      expect((request.data as Map).containsKey('totalAmount'), isFalse);
    }
  });
  test(
    'creation rejects a response belonging to a different clinical context',
    () async {
      adapter.data = prescriptionJson();
      await expectLater(
        repository.createPrescription(PrescriptionIntent(rxCommand())),
        throwsFormatException,
      );
      expect(adapter.requests, hasLength(1));
    },
  );
  test(
    'dispense uses exact bodyless command and preserves audit receipt',
    () async {
      adapter.data = slip();
      final result = await repository.dispensePrescription(rxId);
      expect(result.prescriptionId, rxId);
      expect(
        adapter.requests.single.uri.path,
        '/api/v1/pharmacy/prescriptions/$rxId/dispense',
      );
      expect(adapter.requests.single.method, 'PUT');
      expect(adapter.requests.single.data, isNull);
      expect(
        adapter.requests.single.headers.containsKey('Idempotency-Key'),
        isFalse,
      );
      expect(result.dispensedActorType, isNotNull);
    },
  );
}
