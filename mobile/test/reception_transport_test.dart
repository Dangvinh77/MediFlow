import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/config/app_config.dart';
import 'package:mediflow_mobile/core/network/api_client.dart';
import 'package:mediflow_mobile/core/network/gateway_reference_reader.dart';
import 'package:mediflow_mobile/core/domain/reference.dart';
import 'package:mediflow_mobile/features/appointment/data/gateway_reception_repository.dart';
import 'package:mediflow_mobile/features/appointment/domain/reception.dart';

const patientId = '11111111-1111-4111-8111-111111111111';
const departmentId = '22222222-2222-4222-8222-222222222222';
const doctorId = '33333333-3333-4333-8333-333333333333';
const sessionId = '44444444-4444-4444-8444-444444444444';
const roomId = '55555555-5555-4555-8555-555555555555';
const registrationId = '66666666-6666-4666-8666-666666666666';
const session = ReceptionSession(
  id: sessionId,
  revision: 7,
  departmentId: departmentId,
  doctorId: doctorId,
  roomId: roomId,
  date: '2026-11-04',
  startTime: '08:00',
  endTime: '11:00',
  capacity: 20,
  availableCount: 3,
  status: 'OPEN',
);

class ReceptionTransport implements HttpClientAdapter {
  final requests = <RequestOptions>[];
  Object? data;
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

void main() {
  late ReceptionTransport adapter;
  late ApiClient client;
  setUp(() {
    adapter = ReceptionTransport();
    client = ApiClient(
      AppConfig('http://localhost:28080/api'),
      dio: Dio()..httpClientAdapter = adapter,
      token: () => 'nurse-token',
    );
  });
  tearDown(() => client.close());

  test(
    'follow-up recommendations must belong to their displayed source',
    () async {
      final recommendation = {
        'recommendationId': roomId,
        'patientId': patientId,
        'departmentId': departmentId,
        'sourceRegistrationId': registrationId,
        'preferredDoctorId': doctorId,
        'recommendedFrom': '2026-11-04',
        'recommendedThrough': '2026-11-10',
        'reason': 'Kiểm tra lại',
      };
      final source = {
        'registrationId': registrationId,
        'patientId': patientId,
        'departmentId': departmentId,
        'doctorId': doctorId,
        'completedAt': '2026-11-03T08:00:00Z',
        'recommendation': recommendation,
      };
      adapter.data = {
        'content': [source],
        'number': 0,
        'totalPages': 1,
        'totalElements': 1,
      };
      final repository = GatewayReceptionRepository(client);
      expect(
        (await repository.followUpSources(patientId))
            .items
            .single
            .recommendationId,
        roomId,
      );
      recommendation['patientId'] = doctorId;
      await expectLater(
        repository.followUpSources(patientId),
        throwsFormatException,
      );
    },
  );

  test(
    'registration retry preserves body, revision, idempotency and correlation',
    () async {
      adapter.data = {
        'registrationId': registrationId,
        'patientId': patientId,
        'sessionId': sessionId,
        'doctorId': doctorId,
        'status': 'AWAITING_PAYMENT',
        'reservationExpiresAt': '2026-11-04T02:30:00Z',
        'queueNumber': null,
      };
      final repository = GatewayReceptionRepository(client);
      final intent = ReceptionIntent(
        const ReceptionCommand(patientId: patientId, session: session),
      );
      final receipt = await repository.register(intent);
      await repository.register(intent);
      expect(receipt.status, 'AWAITING_PAYMENT');
      expect(adapter.requests, hasLength(2));
      for (final request in adapter.requests) {
        expect(request.uri.path, '/api/v2/outpatient-registrations');
        expect(request.headers['Idempotency-Key'], intent.key);
        expect(request.headers['X-Correlation-Id'], intent.correlationId);
        expect(request.data, containsPair('expectedSessionRevision', 7));
        expect(request.data, containsPair('receptionMode', 'AT_COUNTER'));
      }
      adapter.data = {
        ...(adapter.data as Map<String, Object?>),
        'patientId': doctorId,
      };
      await expectLater(repository.register(intent), throwsFormatException);
    },
  );

  test(
    'reset sends idempotency header without exposing secret values',
    () async {
      adapter.data = {'messageId': registrationId, 'status': 'PENDING'};
      await client.putIdempotent(
        '/v1/org/patient-accounts/$patientId/password',
        {},
        idempotencyKey: registrationId,
      );
      expect(adapter.requests.single.method, 'PUT');
      expect(adapter.requests.single.data, isEmpty);
      expect(
        adapter.requests.single.headers['Idempotency-Key'],
        registrationId,
      );
      await expectLater(
        client.post(
          '/v2/outpatient-registrations',
          {},
          correlationId: 'bad\r\nheader',
        ),
        throwsArgumentError,
      );
      expect(adapter.requests, hasLength(1));
    },
  );

  test(
    'room label uses the public care label endpoint and verifies owner',
    () async {
      adapter.data = {'id': roomId, 'label': 'Phòng khám 2'};
      final reader = GatewayReferenceReader(client, role: 'NURSE');
      expect(
        (await reader.read(ReferenceKind.room, roomId)).name,
        'Phòng khám 2',
      );
      expect(
        adapter.requests.single.uri.path,
        '/api/v1/org/care-labels/room/$roomId',
      );
      adapter.data = {'id': doctorId, 'label': 'Sai phòng'};
      await expectLater(
        reader.read(ReferenceKind.room, roomId),
        throwsFormatException,
      );
    },
  );

  test(
    'follow-up cannot use another patient or omit doctor replacement consent',
    () {
      const prior = ReceptionFollowUp(
        registrationId: registrationId,
        patientId: patientId,
        departmentId: departmentId,
        doctorId: roomId,
        completedAt: '2026-11-03T08:00:00Z',
      );
      expect(
        const ReceptionCommand(
          patientId: patientId,
          session: session,
          followUp: true,
          source: prior,
          reason: 'Tái khám',
        ).validate(),
        contains('đồng ý'),
      );
      expect(
        const ReceptionCommand(
          patientId: patientId,
          session: session,
          followUp: true,
          source: prior,
          reason: 'Tái khám',
          replacementReason: 'Bác sĩ trước nghỉ',
          agreedToReplacement: true,
        ).validate(),
        isNull,
      );
      expect(
        const ReceptionCommand(
          patientId: doctorId,
          session: session,
          followUp: true,
          source: prior,
          reason: 'Tái khám',
        ).validate(),
        contains('cùng khoa'),
      );
      expect(
        () => ReceptionIntent(
          const ReceptionCommand(patientId: '', session: session),
        ),
        throwsArgumentError,
      );
    },
  );

  test(
    'session listing rejects another department and invalid date ranges',
    () async {
      final repository = GatewayReceptionRepository(client);
      adapter.data = {
        'content': [
          {
            'sessionId': sessionId,
            'revision': 7,
            'departmentId': doctorId,
            'doctorId': doctorId,
            'roomId': roomId,
            'sessionDate': '2026-11-04',
            'startTime': '08:00',
            'endTime': '11:00',
            'capacity': 20,
            'availableCount': 3,
            'status': 'OPEN',
          },
        ],
        'number': 0,
        'totalPages': 1,
        'totalElements': 1,
      };
      await expectLater(
        repository.sessions(
          departmentId: departmentId,
          fromDate: '2026-11-04',
          toDate: '2026-11-04',
        ),
        throwsFormatException,
      );
      await expectLater(
        repository.sessions(
          departmentId: departmentId,
          fromDate: '2026-02-30',
          toDate: '2026-11-04',
        ),
        throwsArgumentError,
      );
      expect(adapter.requests, hasLength(1));
    },
  );
  test(
    'allows versioned scheduling routes through the same gateway only',
    () async {
      final adapter = ReceptionTransport()..data = {'content': []};
      final client = ApiClient(
        AppConfig('http://localhost:28080/api'),
        dio: Dio()..httpClientAdapter = adapter,
        token: () => 'nurse-token',
      );
      addTearDown(client.close);
      await client.get(
        '/v2/consultation-sessions',
        query: {'departmentId': 'department'},
      );
      expect(adapter.requests.single.uri.path, '/api/v2/consultation-sessions');
      expect(
        adapter.requests.single.headers['Authorization'],
        'Bearer nurse-token',
      );
      for (final path in [
        'http://localhost:8082/api/v2/sessions',
        '/v3/sessions',
        '/v2/../v1/org',
        '/v2/sessions?x=1',
      ]) {
        await expectLater(client.get(path), throwsArgumentError);
      }
      expect(adapter.requests, hasLength(1));
    },
  );
}
