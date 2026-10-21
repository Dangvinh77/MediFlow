import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/config/app_config.dart';
import 'package:mediflow_mobile/core/network/api_client.dart';
import 'package:mediflow_mobile/core/network/api_failure.dart';
import 'package:mediflow_mobile/features/billing/data/gateway_billing_repository.dart';
import 'package:mediflow_mobile/features/billing/domain/billing_commands.dart';

import 'billing_notification_fixtures.dart';

void main() {
  late BillingNotificationHttpAdapter adapter;
  late ApiClient client;
  late GatewayBillingRepository repository;

  setUp(() {
    adapter = BillingNotificationHttpAdapter();
    client = ApiClient(
      AppConfig('http://localhost:8080/api'),
      dio: Dio()..httpClientAdapter = adapter,
      token: () => 'billing-test-token',
    );
    repository = GatewayBillingRepository(client);
  });
  tearDown(() => client.close());

  test('invoice history uses patient UUID and exact paging contract', () async {
    adapter.data = invoicePageJson();
    final result = await repository.invoicesForPatient(patientId, page: 0);
    final request = adapter.requests.single;
    expect(request.method, 'GET');
    expect(request.uri.path, '/api/v1/billing/patient/$patientId');
    expect(request.uri.queryParameters, {'page': '0', 'size': '20'});
    expect(request.headers['Authorization'], 'Bearer billing-test-token');
    expect(result.items.single.totalAmount.minorUnits, 123450);
    expect(result.items.single.fees.single.amount.format(), '1234.50');
  });

  test('payment discovery sends all explicit source identities', () async {
    adapter.data = paymentDiscoveryJson();
    final result = await repository.discoverPaymentRequest(
      purpose: 'EXAM',
      sourceId: sourceId,
      patientId: patientId,
      careEpisodeId: episodeId,
    );
    final request = adapter.requests.single;
    expect(request.method, 'GET');
    expect(request.uri.path, '/api/v1/billing/payment-requests/by-source');
    expect(request.uri.queryParameters, {
      'purpose': 'EXAM',
      'sourceId': sourceId,
      'patientId': patientId,
      'careEpisodeId': episodeId,
    });
    expect(result.remainingAmount.minorUnits, 7525);
  });

  test(
    'ledger payment posts exact frozen payment fields and parses receipt',
    () async {
      adapter.data = ledgerPaymentJson();
      final result = await repository.completePayment(
        paymentRequestId,
        const CompletePaymentCommand(
          idempotencyKey: eventId,
          amountMinorUnits: 2525,
          currency: 'VND',
          paymentMethod: 'CASH',
          providerReference: 'cash-drawer-7',
        ),
      );
      final request = adapter.requests.single;
      expect(request.method, 'POST');
      expect(
        request.uri.path,
        '/api/v1/billing/payment-requests/$paymentRequestId/payments',
      );
      expect(adapter.bodies.single, {
        'idempotencyKey': eventId,
        'amount': 25.25,
        'currency': 'VND',
        'paymentMethod': 'CASH',
        'providerReference': 'cash-drawer-7',
      });
      expect(result.status, 'COMPLETED');
      expect(result.amount.minorUnits, 2525);
    },
  );

  test(
    'refund lookup and partial refund preserve the original transaction',
    () async {
      adapter.data = refundDueJson();
      final due = await repository.refundsDue(prescriptionId);
      expect(
        adapter.requests.single.uri.path,
        '/api/v1/billing/prescription-adjustments/$prescriptionId/refunds-due',
      );
      expect(due.refundsDue.single.amount.minorUnits, 6000);

      adapter.data = ledgerRefundJson();
      final receipt = await repository.recordRefund(
        transactionId,
        const RecordRefundCommand(
          idempotencyKey: eventId,
          amountMinorUnits: 1500,
          reason: 'Unused medication returned',
          paymentMethod: 'TRANSFER',
        ),
      );
      expect(
        adapter.requests.last.uri.path,
        '/api/v1/billing/transactions/$transactionId/refunds',
      );
      expect(adapter.bodies.last, {
        'idempotencyKey': eventId,
        'amount': 15.0,
        'reason': 'Unused medication returned',
        'paymentMethod': 'TRANSFER',
      });
      expect(receipt.originalTransactionId, transactionId);
      expect(receipt.amount.minorUnits, 1500);
    },
  );

  test('strict money rejects precision loss and strings', () async {
    for (final amount in <Object>[1234.567, '1234.50']) {
      adapter.data = invoicePageJson(rows: [invoiceJson(amount: amount)]);
      await expectLater(
        repository.invoicesForPatient(patientId),
        throwsFormatException,
      );
    }
  });

  test('mismatched identities and invalid UUIDs never pass silently', () async {
    adapter.data = paymentDiscoveryJson(source: eventId);
    await expectLater(
      repository.discoverPaymentRequest(
        purpose: 'EXAM',
        sourceId: sourceId,
        patientId: patientId,
        careEpisodeId: episodeId,
      ),
      throwsFormatException,
    );
    await expectLater(repository.invoice('../other'), throwsArgumentError);
    expect(adapter.requests, hasLength(1));
  });

  test('forbidden response remains visible and is not retried', () async {
    adapter.status = 403;
    adapter.data = {
      'success': false,
      'data': null,
      'error': {'code': 'FORBIDDEN', 'message': 'No permission'},
      'correlationId': 'billing-forbidden',
    };
    await expectLater(
      repository.invoicesForPatient(patientId),
      throwsA(
        isA<ApiFailure>()
            .having((failure) => failure.status, 'status', 403)
            .having(
              (failure) => failure.correlationId,
              'trace',
              'billing-forbidden',
            ),
      ),
    );
    expect(adapter.requests, hasLength(1));
  });
}
