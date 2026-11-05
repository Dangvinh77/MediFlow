import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/config/app_config.dart';
import 'package:mediflow_mobile/core/network/api_client.dart';
import 'package:mediflow_mobile/features/appointment/data/gateway_schedule_management_repository.dart';
import 'package:mediflow_mobile/features/appointment/domain/schedule_management.dart';

import 'billing_notification_fixtures.dart';

const _department = '10000000-0000-4000-8000-0000000000a1';
const _account = '10000000-0000-4000-8000-0000000000a2';

void main() {
  late BillingNotificationHttpAdapter adapter;
  late ApiClient client;
  late GatewayScheduleManagementRepository repository;

  setUp(() {
    adapter = BillingNotificationHttpAdapter();
    client = ApiClient(
      AppConfig('http://localhost:8080/api'),
      dio: Dio()..httpClientAdapter = adapter,
      token: () => 'test-token',
    );
    repository = GatewayScheduleManagementRepository(client);
  });
  tearDown(() => client.close());

  test('capabilities list the departments a doctor heads', () async {
    adapter.data = {
      'role': 'DOCTOR',
      'managementDepartments': [
        {
          'departmentId': _department,
          'departmentName': 'Khoa Nội',
          'appointmentId': _account,
          'revision': 1,
        },
      ],
    };
    final departments = await repository.managementDepartments();

    expect(adapter.requests.single.uri.path, '/api/v1/org/me/capabilities');
    expect(departments.single.name, 'Khoa Nội');
  });

  test('a summary maps every counter by its wire name', () async {
    adapter.data = {
      'openSessionCount': 1,
      'stoppedSessionCount': 2,
      'capacity': 3,
      'availableCount': 4,
      'heldCount': 5,
      'paymentLockedCount': 6,
      'confirmedCount': 7,
      'consumedCount': 8,
    };
    final summary = await repository.summary(
      _department,
      fromDate: '2026-11-05',
      toDate: '2026-11-05',
    );

    expect(adapter.requests.single.uri.queryParameters, {
      'departmentId': _department,
      'fromDate': '2026-11-05',
      'toDate': '2026-11-05',
    });
    expect(
      [
        summary.openSessions,
        summary.stoppedSessions,
        summary.capacity,
        summary.available,
        summary.held,
        summary.paymentLocked,
        summary.confirmed,
        summary.consumed,
      ],
      [1, 2, 3, 4, 5, 6, 7, 8],
    );
  });

  test('a head appointment for another department is rejected', () async {
    adapter.data = {
      'departmentId': _account,
      'revision': 0,
      'staffId': null,
      'history': <Object>[],
    };
    await expectLater(
      repository.headAppointment(_department),
      throwsFormatException,
    );
  });

  test('commands send their method, body and stable keys', () async {
    adapter.data = {'ok': true};
    final create = ScheduleCommand.createShift(
      doctorId: _account,
      departmentId: _department,
      roomId: _account,
      date: '2026-11-05',
      startTime: '08:00',
      endTime: '12:00',
    );
    await repository.execute(create);
    expect(adapter.requests.last.method, 'POST');
    expect(adapter.requests.last.uri.path, '/api/v2/doctor-work-shifts');
    expect(
      adapter.requests.last.headers['Idempotency-Key'],
      create.idempotencyKey,
    );

    final replace = ScheduleCommand.replaceScope(
      const SchedulingScope(
        accountId: _account,
        revision: 2,
        departmentIds: [],
      ),
      const [_department],
      'Cấp quyền',
    );
    await repository.execute(replace);
    await repository.execute(replace);
    expect(adapter.requests.last.method, 'PUT');
    expect(
      adapter.requests.last.uri.path,
      '/api/v2/org/accounts/$_account/schedule-management-scope',
    );
    expect(adapter.requests.last.data, {
      'expectedRevision': 2,
      'departmentIds': [_department],
      'reason': 'Cấp quyền',
    });
    expect(
      adapter.requests[adapter.requests.length - 2].headers['Idempotency-Key'],
      adapter.requests.last.headers['Idempotency-Key'],
    );
  });
}
