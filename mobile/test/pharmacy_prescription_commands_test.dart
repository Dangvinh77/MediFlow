import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/config/app_config.dart';
import 'package:mediflow_mobile/core/network/api_client.dart';
import 'package:mediflow_mobile/core/network/api_failure.dart';
import 'package:mediflow_mobile/features/pharmacy/data/gateway_pharmacy_repository.dart';
import 'package:mediflow_mobile/features/pharmacy/data/pharmacy_dto.dart';
import 'package:mediflow_mobile/features/pharmacy/data/prescription_request_dto.dart';
import 'package:mediflow_mobile/features/pharmacy/domain/drug_commands.dart';
import 'package:mediflow_mobile/features/pharmacy/domain/prescription_commands.dart';

import 'pharmacy_fixtures.dart';

CreateOutpatientPrescription rxCommand({
  String record = drugId,
  String patient = otherDrugId,
  String doctor = drugId,
  String department = otherDrugId,
  String episode = rxId,
  String price = 'CONFIRMED-CATALOG',
  String source = 'Public visit receipt + approved Billing policy',
  String date = '2026-10-20',
  List<RequestedPrescriptionLine>? lines,
}) => CreateOutpatientPrescription(
  context: OutpatientPrescriptionContext(
    recordId: record,
    patientId: patient,
    doctorId: doctor,
    departmentId: department,
    careEpisodeId: episode,
    priceCode: price,
    sourceReference: source,
  ),
  prescribedDate: date,
  lines:
      lines ?? [const RequestedPrescriptionLine(drugId: drugId, quantity: 2)],
);

void main() {
  test('source IDs and catalog policy are required, never synthesized', () {
    for (final invalid in [
      rxCommand(record: ''),
      rxCommand(patient: ''),
      rxCommand(doctor: ''),
      rxCommand(department: ''),
      rxCommand(episode: ''),
      rxCommand(episode: 'record'),
      rxCommand(price: ''),
      rxCommand(price: 'a' * 65),
      rxCommand(source: ''),
    ]) {
      expect(invalid.errors(), isNotEmpty);
      expect(
        () => PrescriptionIntent(invalid),
        throwsA(isA<PharmacyValidationException>()),
      );
    }
  });
  test('date and lines mirror Java date, Integer and dosage constraints', () {
    for (final invalid in [
      rxCommand(date: '2026-02-30'),
      rxCommand(date: '2099-01-01'),
      rxCommand(lines: []),
      rxCommand(
        lines: [const RequestedPrescriptionLine(drugId: drugId, quantity: 0)],
      ),
      rxCommand(
        lines: [
          const RequestedPrescriptionLine(drugId: drugId, quantity: 2147483648),
        ],
      ),
      rxCommand(
        lines: [
          RequestedPrescriptionLine(
            drugId: drugId,
            quantity: 1,
            dosage: 'a' * 256,
          ),
        ],
      ),
    ]) {
      expect(invalid.errors(), isNotEmpty);
    }
    expect(rxCommand(date: '2024-02-29').errors(), isEmpty);
  });
  test('intent freezes lines, creates canonical UUID and body preserves exact V1 contract', () {
    final lines = [
      const RequestedPrescriptionLine(
        drugId: drugId,
        quantity: 2,
        dosage: ' Sau ăn ',
      ),
    ];
    final command = rxCommand(lines: lines, price: ' Drug-policy ');
    final intent = PrescriptionIntent(command);
    lines.clear();
    expect(
      intent.key,
      matches(
        RegExp(
          r'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$',
        ),
      ),
    );
    expect(() => command.lines.clear(), throwsUnsupportedError);
    final payload = PrescriptionRequestDto.outpatient(intent);
    expect(payload, {
      'recordId': drugId,
      'patientId': otherDrugId,
      'doctorId': drugId,
      'departmentId': otherDrugId,
      'prescribedDate': '2026-10-20',
      'lines': [
        {'drugId': drugId, 'quantity': 2, 'dosage': ' Sau ăn '},
      ],
      'careContractVersion': 1,
      'careContext': 'OUTPATIENT',
      'careEpisodeType': 'OUTPATIENT_VISIT',
      'careEpisodeId': rxId,
      'admissionId': null,
      'priceCode': ' Drug-policy ',
    });
    (payload['lines'] as List).clear();
    expect(PrescriptionRequestDto.outpatient(intent)['lines'], hasLength(1));
    expect(payload.containsKey('sourceReference'), isFalse);
    expect(payload.containsKey('Idempotency-Key'), isFalse);
    expect(
      () => PrescriptionIntent(command, key: 'short-id'),
      throwsArgumentError,
    );
  });
  test('cancellation requires a reason and active/pending state', () {
    for (final reason in ['', ' ', 'a' * 501]) {
      expect(
        () => CancelPrescription(reason).validate(),
        throwsA(isA<PharmacyValidationException>()),
      );
    }
    CancelPrescription('a' * 500).validate();
    expect(
      canCancelPrescription(PharmacyDto.prescription(prescriptionJson())),
      isTrue,
    );
    for (final status in [
      'FULFILLED',
      'CANCELLED',
      'EXPIRED',
      'DISPENSE_FAILED',
    ]) {
      expect(
        canCancelPrescription(
          PharmacyDto.prescription({...prescriptionJson(), 'status': status}),
        ),
        isFalse,
      );
    }
    for (final status in ['DISPENSED', 'FAILED', 'CANCELLED', 'EXPIRED']) {
      expect(
        canCancelPrescription(
          PharmacyDto.prescription({
            ...prescriptionJson(),
            'dispenseStatus': status,
          }),
        ),
        isFalse,
      );
    }
  });
  group('cancel Gateway adapter', () {
    late PharmacyHttpAdapter adapter;
    late ApiClient client;
    late GatewayPharmacyRepository repository;
    setUp(() {
      adapter = PharmacyHttpAdapter()
        ..data = {
          'prescriptionId': rxId,
          'status': 'CANCELLED',
          'releasedReservations': 2,
          'cancelledAt': '2026-10-22T00:00:00Z',
        };
      client = ApiClient(
        AppConfig('http://localhost:8080/api'),
        dio: Dio()..httpClientAdapter = adapter,
        token: () => 'test-bearer',
      );
      repository = GatewayPharmacyRepository(client);
    });
    tearDown(() => client.close());
    test('exact method/path/reason, no actor or role from body', () async {
      final result = await repository.cancelPrescription(
        rxId,
        const CancelPrescription(' Chỉnh chỉ định '),
      );
      final request = adapter.requests.single;
      expect(request.method, 'PUT');
      expect(request.uri.path, '/api/v1/pharmacy/prescriptions/$rxId/cancel');
      expect(request.data, {'reason': ' Chỉnh chỉ định '});
      expect(request.headers['Authorization'], 'Bearer test-bearer');
      expect(result.releasedReservations, 2);
    });
    test('invalid input cannot cross transport', () async {
      await expectLater(
        repository.cancelPrescription(
          'bad',
          const CancelPrescription('reason'),
        ),
        throwsArgumentError,
      );
      await expectLater(
        repository.cancelPrescription(rxId, const CancelPrescription(' ')),
        throwsA(isA<PharmacyValidationException>()),
      );
      expect(adapter.requests, isEmpty);
    });
    for (final status in [400, 401, 403, 409, 422, 503]) {
      test(
        '$status propagates without replay or manufactured cancellation',
        () async {
          adapter.status = status;
          adapter.data = {
            'success': false,
            'error': {'code': 'CANCEL_DENIED', 'message': 'Denied'},
            'correlationId': 'cancel-trace',
          };
          await expectLater(
            repository.cancelPrescription(
              rxId,
              const CancelPrescription('Reason'),
            ),
            throwsA(
              isA<ApiFailure>()
                  .having((e) => e.status, 'status', status)
                  .having(
                    (e) => e.correlationId,
                    'correlation',
                    'cancel-trace',
                  ),
            ),
          );
          expect(adapter.requests, hasLength(1));
        },
      );
    }
    test('mismatched identity or non-cancelled result is malformed', () async {
      for (final change in [
        {'prescriptionId': otherDrugId},
        {'status': 'ACTIVE'},
      ]) {
        adapter.data = {...adapter.data as Map<String, dynamic>, ...change};
        await expectLater(
          repository.cancelPrescription(
            rxId,
            const CancelPrescription('Reason'),
          ),
          throwsFormatException,
        );
      }
    });
  });
}
