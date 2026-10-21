import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/config/app_config.dart';
import 'package:mediflow_mobile/core/network/api_client.dart';
import 'package:mediflow_mobile/core/network/api_failure.dart';
import 'package:mediflow_mobile/features/notification/data/gateway_notification_repository.dart';
import 'package:mediflow_mobile/features/notification/domain/notification.dart';

import 'billing_notification_fixtures.dart';

void main() {
  late BillingNotificationHttpAdapter adapter;
  late ApiClient client;
  late GatewayNotificationRepository repository;

  setUp(() {
    adapter = BillingNotificationHttpAdapter();
    client = ApiClient(
      AppConfig('http://localhost:8080/api'),
      dio: Dio()..httpClientAdapter = adapter,
      token: () => 'notification-test-token',
    );
    repository = GatewayNotificationRepository(client);
  });
  tearDown(() => client.close());

  test(
    'patient history uses explicit patient UUID and page contract',
    () async {
      adapter.data = notificationPageJson();
      final page = await repository.byPatient(patientId, page: 0);
      final request = adapter.requests.single;
      expect(request.method, 'GET');
      expect(request.uri.path, '/api/v1/notifications/patient/$patientId');
      expect(request.uri.queryParameters, {'page': '0', 'size': '20'});
      expect(
        request.headers['Authorization'],
        'Bearer notification-test-token',
      );
      expect(page.items.single.id, notificationId);
    },
  );

  test(
    'detail does not expose recipient address from response extensions',
    () async {
      adapter.data = notificationJson();
      final notification = await repository.byId(notificationId);
      expect(
        adapter.requests.single.uri.path,
        '/api/v1/notifications/$notificationId',
      );
      expect(notification.title, 'Kết quả khám');
      expect(notification.toString(), isNot(contains('private@example.test')));
    },
  );

  test(
    'send posts recipient address only in request and checks patient identity',
    () async {
      adapter.data = notificationJson();
      final receipt = await repository.send(
        const SendNotificationCommand(
          patientId: patientId,
          title: ' Kết quả khám ',
          content: ' Vui lòng xem lịch hẹn. ',
          channel: 'EMAIL',
          recipientAddress: 'private@example.test',
        ),
      );
      expect(adapter.requests.single.method, 'POST');
      expect(adapter.requests.single.uri.path, '/api/v1/notifications/send');
      expect(adapter.bodies.single, {
        'patientId': patientId,
        'title': ' Kết quả khám ',
        'content': ' Vui lòng xem lịch hẹn. ',
        'channel': 'EMAIL',
        'recipientAddress': 'private@example.test',
      });
      expect(receipt.patientId, patientId);
      expect(receipt.toString(), isNot(contains('private@example.test')));
    },
  );

  test('explicit IN_APP command omits recipient address', () async {
    adapter.data = notificationJson();
    const command = SendNotificationCommand(
      patientId: patientId,
      title: 'Update',
      content: 'A message',
      channel: 'IN_APP',
      recipientAddress: null,
    );
    await repository.send(command);
    expect(adapter.bodies.single, {
      'patientId': patientId,
      'title': 'Update',
      'content': 'A message',
      'channel': 'IN_APP',
      'recipientAddress': null,
    });
  });

  test('invalid patient identity is rejected before HTTP', () async {
    await expectLater(
      repository.byPatient('account-id-not-patient'),
      throwsArgumentError,
    );
    expect(adapter.requests, isEmpty);
  });

  test('403 preserves gateway authorization error without retry', () async {
    adapter.status = 403;
    adapter.data = {
      'success': false,
      'data': null,
      'error': {'code': 'FORBIDDEN', 'message': 'No permission'},
      'correlationId': 'notification-forbidden',
    };
    await expectLater(
      repository.byPatient(patientId),
      throwsA(
        isA<ApiFailure>()
            .having((failure) => failure.status, 'status', 403)
            .having(
              (failure) => failure.correlationId,
              'trace',
              'notification-forbidden',
            ),
      ),
    );
    expect(adapter.requests, hasLength(1));
  });
}
