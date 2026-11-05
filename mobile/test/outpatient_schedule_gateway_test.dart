import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/config/app_config.dart';
import 'package:mediflow_mobile/core/network/api_client.dart';
import 'package:mediflow_mobile/core/network/api_failure.dart';
import 'package:mediflow_mobile/features/appointment/data/gateway_outpatient_schedule_repository.dart';
import 'package:mediflow_mobile/features/medical_record/data/gateway_follow_up_recommendation_repository.dart';
import 'package:mediflow_mobile/features/medical_record/domain/follow_up_recommendations.dart';

import 'billing_notification_fixtures.dart';

const _session = '10000000-0000-4000-8000-000000000081';
const _registration = '10000000-0000-4000-8000-000000000082';
const _record = '10000000-0000-4000-8000-000000000083';
const _key = '10000000-0000-4000-8000-000000000084';

Map<String, Object?> _registrationJson({String id = _registration}) => {
  'registrationId': id,
  'revision': 4,
  'patientId': '10000000-0000-4000-8000-000000000085',
  'doctorId': '10000000-0000-4000-8000-000000000086',
  'departmentId': '10000000-0000-4000-8000-000000000087',
  'roomId': '10000000-0000-4000-8000-000000000088',
  'sessionDate': '2026-11-05',
  'startTime': '07:00',
  'endTime': '17:00',
  'receptionMode': 'PREBOOKED',
  'visitType': 'NEW_VISIT',
  'status': 'CONFIRMED',
  'reservationExpiresAt': '2026-11-05T03:00:00Z',
  'checkedInAt': null,
  'queueNumber': 2,
  'readyForExam': false,
  'followUpRecommendation': null,
};

const _recommendation = {
  'recommendationId': '10000000-0000-4000-8000-000000000089',
  'sourceRecordId': _record,
  'sourceRegistrationId': _registration,
  'patientId': '10000000-0000-4000-8000-000000000085',
  'departmentId': '10000000-0000-4000-8000-000000000087',
  'preferredDoctorId': '10000000-0000-4000-8000-000000000086',
  'recommendedFrom': '2026-11-10',
  'recommendedThrough': '2026-11-20',
  'reason': 'Theo dõi',
  'preparationInstructions': null,
  'createdAt': '2026-11-05T03:00:00Z',
};

void main() {
  late BillingNotificationHttpAdapter adapter;
  late ApiClient client;
  late GatewayOutpatientScheduleRepository schedule;
  late GatewayFollowUpRecommendationRepository followUp;

  setUp(() {
    adapter = BillingNotificationHttpAdapter();
    client = ApiClient(
      AppConfig('http://localhost:8080/api'),
      dio: Dio()..httpClientAdapter = adapter,
      token: () => 'test-token',
    );
    schedule = GatewayOutpatientScheduleRepository(client);
    followUp = GatewayFollowUpRecommendationRepository(client);
  });
  tearDown(() => client.close());

  test('the calendar sends exactly one of date or weekStart', () async {
    adapter.data = {
      'fromDate': '2026-11-02',
      'toDate': '2026-11-08',
      'sessions': [
        {
          'sessionId': _session,
          'departmentId': '10000000-0000-4000-8000-000000000087',
          'roomId': '10000000-0000-4000-8000-000000000088',
          'sessionDate': '2026-11-05',
          'startTime': '07:00',
          'endTime': '17:00',
          'status': 'OPEN',
          'confirmedCount': 2,
          'availableCount': 38,
        },
      ],
    };
    final calendar = await schedule.myCalendar(weekStart: '2026-11-02');

    expect(
      adapter.requests.single.uri.path,
      '/api/v2/consultation-sessions/mine',
    );
    expect(adapter.requests.single.uri.queryParameters, {
      'weekStart': '2026-11-02',
    });
    expect(calendar.sessions.single.availableCount, 38);
    expect(() => schedule.myCalendar(), throwsArgumentError);
    expect(
      () => schedule.myCalendar(date: '2026-11-05', weekStart: '2026-11-02'),
      throwsArgumentError,
    );
  });

  test('work shifts read the signed-in doctor range', () async {
    adapter.data = [
      {
        'shiftId': '10000000-0000-4000-8000-00000000008a',
        'departmentId': '10000000-0000-4000-8000-000000000087',
        'roomId': '10000000-0000-4000-8000-000000000088',
        'shiftDate': '2026-11-05',
        'startTime': '08:00',
        'endTime': '12:00',
        'status': 'ACTIVE',
      },
    ];
    final shifts = await schedule.myWorkShifts('2026-11-02', '2026-11-08');

    expect(adapter.requests.single.uri.path, '/api/v2/doctor-work-shifts/mine');
    expect(adapter.requests.single.uri.queryParameters, {
      'fromDate': '2026-11-02',
      'toDate': '2026-11-08',
    });
    expect(shifts.single.date, '2026-11-05');
  });

  test('arrival sends the expected revision and command headers', () async {
    adapter.data = _registrationJson();
    final result = await schedule.markArrival(
      _registration,
      expectedRevision: 4,
      idempotencyKey: _key,
      correlationId: _key,
    );

    final request = adapter.requests.single;
    expect(request.method, 'PUT');
    expect(
      request.uri.path,
      '/api/v2/outpatient-registrations/$_registration/arrival',
    );
    expect(request.data, {'expectedRevision': 4});
    expect(request.headers['Idempotency-Key'], _key);
    expect(request.headers['X-Correlation-Id'], _key);
    expect(result.queueNumber, 2);
  });

  test('a registration for another ID is rejected', () async {
    adapter.data = _registrationJson(
      id: '10000000-0000-4000-8000-000000000099',
    );
    await expectLater(
      schedule.registration(_registration),
      throwsFormatException,
    );
  });

  test('a record without advice reads as empty', () async {
    adapter
      ..status = 404
      ..data = {
        'success': false,
        'error': {'code': 'RESOURCE_NOT_FOUND', 'message': 'Không có'},
      };
    expect(await followUp.read(_record), isNull);
  });

  test('another 404 is an error, not an empty recommendation', () async {
    adapter
      ..status = 404
      ..data = {
        'success': false,
        'error': {'code': 'RECORD_NOT_FOUND', 'message': 'Không có hồ sơ'},
      };
    await expectLater(followUp.read(_record), throwsA(isA<ApiFailure>()));
  });

  test('a recommendation posts the exact body and stable keys', () async {
    adapter.data = _recommendation;
    final draft = FollowUpDraft(
      recommendedFrom: '2026-11-10',
      recommendedThrough: '2026-11-20',
      reason: 'Theo dõi',
    );
    final created = await followUp.create(_record, draft);

    final request = adapter.requests.single;
    expect(
      request.uri.path,
      '/api/v2/records/$_record/follow-up-recommendations',
    );
    expect(request.data, {
      'recommendedFrom': '2026-11-10',
      'recommendedThrough': '2026-11-20',
      'reason': 'Theo dõi',
      'preparationInstructions': null,
    });
    expect(request.headers['Idempotency-Key'], draft.idempotencyKey);
    expect(created.reason, 'Theo dõi');
  });
}
