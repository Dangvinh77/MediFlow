import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/domain/workspace_access.dart';
import 'package:mediflow_mobile/core/config/app_config.dart';
import 'package:mediflow_mobile/core/domain/page_result.dart';
import 'package:mediflow_mobile/core/network/api_failure.dart';
import 'package:mediflow_mobile/core/providers.dart';
import 'package:mediflow_mobile/features/billing/domain/billing_models.dart';
import 'package:mediflow_mobile/features/billing/domain/scheduled_payments.dart';
import 'package:mediflow_mobile/features/billing/data/billing_dto.dart';
import 'package:mediflow_mobile/features/billing/presentation/billing_page.dart';
import 'package:mediflow_mobile/features/billing/presentation/invoice_page.dart';
import 'package:mediflow_mobile/features/billing/presentation/providers.dart';
import 'package:mediflow_mobile/features/billing/presentation/scheduled_payment_page.dart';

import 'billing_notification_fixtures.dart';

Finder fieldWithLabel(String label) => find.byWidgetPredicate(
  (widget) => widget is TextField && widget.decoration?.labelText == label,
);

void useTallViewport(WidgetTester tester) {
  tester.view.physicalSize = const Size(800, 1600);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
}

Future<void> ensureFinderVisible(
  WidgetTester tester,
  Finder finder, {
  bool settle = false,
}) async {
  if (finder.evaluate().isEmpty) {
    final verticalScrollables = find.byWidgetPredicate(
      (widget) =>
          widget is Scrollable && widget.axisDirection == AxisDirection.down,
    );
    if (verticalScrollables.evaluate().isEmpty) {
      throw StateError('No vertical scrollable is available for $finder');
    }
    await tester.scrollUntilVisible(
      finder,
      240,
      scrollable: verticalScrollables.first,
    );
  } else {
    await tester.ensureVisible(finder);
  }
  if (settle) {
    await tester.pumpAndSettle();
  } else {
    await tester.pump();
  }
}

Future<void> enterField(WidgetTester tester, String label, String value) async {
  if (label == 'Mã bệnh nhân (UUID)' ||
      label == 'Mã nguồn (sourceId)' ||
      label == 'Mã bệnh nhân (patientId)' ||
      label == 'Mã đợt khám (careEpisodeId)') {
    final expansion = find.widgetWithText(
      ExpansionTile,
      label == 'Mã bệnh nhân (UUID)'
          ? 'Mã tham chiếu do tiếp nhận cung cấp'
          : 'Tham chiếu khoản thu từ tiếp nhận',
    );
    if (fieldWithLabel(label).evaluate().isEmpty &&
        expansion.evaluate().isNotEmpty) {
      await tester.ensureVisible(expansion);
      await tester.tap(expansion);
      await tester.pumpAndSettle();
    }
  }

  final finder = fieldWithLabel(label);
  if (label == 'Mã bệnh nhân (UUID)' && finder.evaluate().isEmpty) {
    final scrollable = find
        .byWidgetPredicate(
          (widget) =>
              widget is Scrollable &&
              widget.axisDirection == AxisDirection.down,
        )
        .first;
    for (var attempt = 0; attempt < 6 && finder.evaluate().isEmpty; attempt++) {
      await tester.drag(scrollable, const Offset(0, 420));
      await tester.pumpAndSettle();
    }
  }
  await ensureFinderVisible(tester, finder);
  await tester.enterText(finder, value);
}

class ScheduledBillingFake extends Fake implements ScheduledPaymentRepository {
  ScheduledBillingFake({
    this.readGate,
    this.firstPayError,
    this.firstZeroError,
    this.resultStatus = 'PREPARING',
    this.quote,
    this.quoteError,
    Map<String, dynamic>? requestJson,
  }) : request = BillingDto.scheduledPaymentRequest(
         requestJson ?? scheduledPaymentRequestJson(),
       );

  final Completer<void>? readGate;
  final Object? firstPayError, firstZeroError;
  final String resultStatus;
  final RegistrationPaymentRequest request;
  OutpatientQuote? quote;
  Object? quoteError;
  final payments =
      <
        ({
          String paymentRequestId,
          ScheduledPaymentCommand command,
          String idempotencyKey,
          String correlationId,
        })
      >[];
  final zeroPayments =
      <
        ({
          String paymentRequestId,
          ScheduledZeroLiabilityCommand command,
          String idempotencyKey,
          String correlationId,
        })
      >[];

  @override
  Future<PageResult<ScheduledPaymentQueueItem>> scheduledPaymentQueue({
    String? patientId,
    int page = 0,
  }) async => PageResult(
    items: const [],
    number: page,
    totalPages: 1,
    totalElements: 0,
  );

  @override
  Future<RegistrationPaymentRequest> scheduledPaymentRequestByRegistration(
    String registrationId,
  ) async => request;

  @override
  Future<OutpatientQuote> outpatientQuote(String paymentRequestId) async {
    final error = quoteError;
    if (error != null) throw error;
    return quote ??
        (throw UnsupportedError('No quote configured for this test.'));
  }

  @override
  Future<ScheduledPaymentResult> payScheduledRequest(
    String paymentRequestId,
    ScheduledPaymentCommand command, {
    required String idempotencyKey,
    required String correlationId,
  }) async {
    payments.add((
      paymentRequestId: paymentRequestId,
      command: command,
      idempotencyKey: idempotencyKey,
      correlationId: correlationId,
    ));
    if (payments.length == 1 && firstPayError != null) throw firstPayError!;
    return BillingDto.scheduledPaymentResult(
      scheduledPaymentResultJson(status: resultStatus),
    );
  }

  @override
  Future<ScheduledPaymentResult> confirmScheduledZeroLiability(
    String paymentRequestId,
    ScheduledZeroLiabilityCommand command, {
    required String idempotencyKey,
    required String correlationId,
  }) async {
    zeroPayments.add((
      paymentRequestId: paymentRequestId,
      command: command,
      idempotencyKey: idempotencyKey,
      correlationId: correlationId,
    ));
    if (zeroPayments.length == 1 && firstZeroError != null) {
      throw firstZeroError!;
    }
    return BillingDto.scheduledPaymentResult(
      scheduledPaymentResultJson(
        status: resultStatus,
        basis: 'ZERO_LIABILITY',
        quoteId: command.quoteId,
      ),
    );
  }
}

class MemoryScheduledPaymentRecoveryStore
    implements ScheduledPaymentRecoveryStore {
  MemoryScheduledPaymentRecoveryStore({this.readGate});

  final Completer<void>? readGate;
  final Map<String, ScheduledPaymentRecovery> entries = {};

  @override
  Future<ScheduledPaymentRecovery?> read(String paymentRequestId) async {
    await readGate?.future;
    return entries[paymentRequestId];
  }

  @override
  Future<void> save(
    String paymentRequestId,
    ScheduledPaymentRecovery recovery,
  ) async {
    entries[paymentRequestId] = recovery;
  }

  @override
  Future<void> clear(String paymentRequestId) async {
    entries.remove(paymentRequestId);
  }
}

Widget billingHost(
  BillingStub repository, {
  String role = 'ADMIN',
  bool schedulingEnabled = false,
}) => ProviderScope(
  overrides: [
    workspaceAccessProvider.overrideWithValue(
      WorkspaceAccess(sessionIdentity: Object(), role: role),
    ),
    billingRepositoryProvider.overrideWithValue(repository),
    scheduledPaymentRepositoryProvider.overrideWithValue(
      ScheduledBillingFake(),
    ),
    scheduledPaymentRecoveryStoreProvider.overrideWithValue(
      MemoryScheduledPaymentRecoveryStore(),
    ),
    appConfigProvider.overrideWithValue(
      AppConfig(
        'http://localhost:8080/api',
        scheduledPaymentsEnabled: schedulingEnabled,
      ),
    ),
  ],
  child: const MaterialApp(home: Scaffold(body: BillingPage())),
);

OutpatientQuote examQuote({
  required String quoteId,
  required Money payable,
  required Money support,
  required String supportRate,
}) {
  final now = DateTime.now().toUtc();
  return OutpatientQuote(
    quoteId: quoteId,
    paymentRequestId: paymentRequestId,
    patientId: patientId,
    careEpisodeType: 'OUTPATIENT_VISIT',
    careEpisodeId: episodeId,
    currency: 'VND',
    lines: [
      OutpatientQuoteLine(
        sourceId: registrationId,
        sourceType: 'EXAM',
        priceCode: 'EXAM-001',
        catalogueVersion: 'catalogue-v1',
        policyVersion: 'support-v1',
        description: 'Khám ngoại trú',
        quantity: '1',
        unitPrice: const Money(10000),
        grossAmount: const Money(10000),
        supportRate: supportRate,
        supportAmount: support,
        patientPayable: payable,
      ),
    ],
    grossAmount: const Money(10000),
    supportAmount: support,
    patientPayable: payable,
    issuedAt: now.toIso8601String(),
    expiresAt: now.add(const Duration(hours: 1)).toIso8601String(),
  );
}

Future<void> openLedger(WidgetTester tester, BillingStub repository) async {
  await tester.pumpWidget(billingHost(repository));
  await tester.pumpAndSettle();
  await tester.tap(
    find.descendant(
      of: find.byType(TabBar),
      matching: find.text('Xét nghiệm và thuốc'),
    ),
  );
  await tester.pumpAndSettle();
  await enterField(tester, 'Mã nguồn (sourceId)', sourceId);
  await enterField(tester, 'Mã bệnh nhân (patientId)', patientId);
  await enterField(tester, 'Mã đợt khám (careEpisodeId)', episodeId);
  await tester.tap(find.text('Tra cứu từ Billing'));
  await tester.pumpAndSettle();
  expect(repository.paymentDiscoveries, hasLength(1));
  expect(find.textContaining('Còn lại:'), findsOneWidget);
}

void main() {
  testWidgets('MANAGER can see revenue without cashier mutations', (
    tester,
  ) async {
    useTallViewport(tester);
    final repository = BillingStub();
    await tester.pumpWidget(billingHost(repository, role: 'MANAGER'));
    await tester.pumpAndSettle();
    expect(find.text('Doanh thu theo khoa'), findsOneWidget);
    expect(find.text('Tra cứu hóa đơn'), findsNothing);
    expect(find.text('Tra cứu khoản thu'), findsNothing);
    expect(find.text('Hoàn tiền đơn thuốc'), findsNothing);
    await tester.tap(find.text('Xem báo cáo'));
    await tester.pumpAndSettle();
    expect(repository.completedPayments, isEmpty);
    expect(repository.refundRequests, isEmpty);
  });

  testWidgets(
    'cashier starts in the live collection queue and sees web tasks',
    (tester) async {
      useTallViewport(tester);
      await tester.pumpWidget(billingHost(BillingStub(), role: 'CASHIER'));
      await tester.pumpAndSettle();

      expect(
        find.descendant(
          of: find.byType(TabBar),
          matching: find.text('Xét nghiệm và thuốc'),
        ),
        findsOneWidget,
      );
      expect(find.text('Lịch sử thu'), findsOneWidget);
      expect(find.text('Hoàn tiền'), findsOneWidget);
      expect(find.text('Phí khám'), findsNothing);
      expect(find.text('Báo cáo'), findsNothing);
      expect(find.text('Tra cứu khoản thu'), findsOneWidget);
    },
  );

  testWidgets(
    'scheduled exam fees appear only for cashier roles when enabled',
    (tester) async {
      useTallViewport(tester);
      await tester.pumpWidget(
        billingHost(BillingStub(), role: 'CASHIER', schedulingEnabled: true),
      );
      await tester.pumpAndSettle();
      final scheduledTab = find.descendant(
        of: find.byType(TabBar),
        matching: find.text('Phí khám'),
      );
      expect(scheduledTab, findsOneWidget);
      await tester.tap(scheduledTab);
      await tester.pumpAndSettle();
      expect(find.text('Thu phí khám'), findsOneWidget);

      await tester.pumpWidget(
        billingHost(BillingStub(), role: 'MANAGER', schedulingEnabled: true),
      );
      await tester.pumpAndSettle();
      expect(find.text('Phí khám'), findsNothing);
      expect(find.text('Doanh thu theo khoa'), findsOneWidget);
    },
  );

  testWidgets(
    'scheduled queue search scrolls into view on a narrow keyboard-height viewport',
    (tester) async {
      tester.view.physicalSize = const Size(320, 640);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      await tester.pumpWidget(
        MaterialApp(
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(context)
                .copyWith(textScaler: const TextScaler.linear(1.45)),
            child: child!,
          ),
          home: Scaffold(
            body: ScheduledPaymentQueuePage(
              repository: ScheduledBillingFake(),
              recoveryStore: MemoryScheduledPaymentRecoveryStore(),
              quoteEnabled: false,
              zeroPayableEnabled: false,
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Tra cứu theo mã đăng ký'));
      await tester.pumpAndSettle();
      tester.view.viewInsets = const FakeViewPadding(bottom: 290);
      await tester.pumpAndSettle();

      final registration = fieldWithLabel('Mã đăng ký (UUID)');
      await tester.ensureVisible(registration);
      await tester.pumpAndSettle();
      final search = find.widgetWithText(FilledButton, 'Tra cứu phí khám');
      await tester.scrollUntilVisible(
        search,
        100,
        scrollable: find.byType(Scrollable).first,
      );
      expect(tester.getRect(registration).bottom, lessThanOrEqualTo(350));
      expect(tester.getRect(search).bottom, lessThanOrEqualTo(350));
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'scheduled collection waits for recovery and retries the frozen amount with the same key',
    (tester) async {
      useTallViewport(tester);
      final readGate = Completer<void>();
      final repository = ScheduledBillingFake(
        firstPayError: StateError('Connection lost'),
      );
      final recoveryStore = MemoryScheduledPaymentRecoveryStore(
        readGate: readGate,
      );
      final request = repository.request;
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: ScheduledPaymentCollectionPage(
              repository: repository,
              recoveryStore: recoveryStore,
              request: request,
              quoteEnabled: false,
              zeroPayableEnabled: false,
            ),
          ),
        ),
      );
      await tester.pump();

      final pay = find.widgetWithText(FilledButton, 'Thu phí khám');
      expect(pay, findsOneWidget);
      expect(tester.widget<FilledButton>(pay).onPressed, isNull);
      readGate.complete();
      await tester.pumpAndSettle();
      expect(tester.widget<FilledButton>(pay).onPressed, isNotNull);

      await tester.tap(pay);
      await tester.pumpAndSettle();
      expect(find.text('Gửi lại đúng yêu cầu đã khóa'), findsOneWidget);
      expect(repository.payments, hasLength(1));
      expect(repository.payments.single.command.amount, '75.00');
      expect(repository.payments.single.command.reservationId, reservationId);
      final firstAttemptKey = repository.payments.single.idempotencyKey;
      expect(
        recoveryStore.entries[request.paymentRequestId]?.idempotencyKey,
        firstAttemptKey,
      );

      await tester.tap(find.text('Gửi lại đúng yêu cầu đã khóa'));
      await tester.pumpAndSettle();
      expect(repository.payments, hasLength(2));
      expect(repository.payments.last.idempotencyKey, firstAttemptKey);
      expect(repository.payments.last.command.amount, '75.00');
      expect(
        recoveryStore.entries[request.paymentRequestId]?.operationId,
        paymentOperationId,
      );
      expect(
        find.textContaining('Đang chờ Billing hoàn tất giao dịch'),
        findsOneWidget,
      );
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'idempotency conflict retains the frozen command for an exact retry',
    (tester) async {
      useTallViewport(tester);
      final repository = ScheduledBillingFake(
        firstPayError: const ApiFailure(
          'Idempotency conflict',
          status: 409,
          code: 'BILLING_IDEMPOTENCY_CONFLICT',
        ),
      );
      final recoveryStore = MemoryScheduledPaymentRecoveryStore();
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: ScheduledPaymentCollectionPage(
              repository: repository,
              recoveryStore: recoveryStore,
              request: repository.request,
              quoteEnabled: false,
              zeroPayableEnabled: false,
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(FilledButton, 'Thu phí khám'));
      await tester.pumpAndSettle();

      final firstKey = repository.payments.single.idempotencyKey;
      expect(recoveryStore.entries[paymentRequestId]?.idempotencyKey, firstKey);
      expect(find.text('Gửi lại đúng yêu cầu đã khóa'), findsOneWidget);

      await tester.tap(find.text('Gửi lại đúng yêu cầu đã khóa'));
      await tester.pumpAndSettle();
      expect(repository.payments, hasLength(2));
      expect(repository.payments.last.idempotencyKey, firstKey);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('discounted exam collection uses the Billing payable amount', (
    tester,
  ) async {
    useTallViewport(tester);
    final quote = examQuote(
      quoteId: '10000000-0000-4000-8000-000000000020',
      payable: const Money(7500),
      support: const Money(2500),
      supportRate: '0.25',
    );
    final repository = ScheduledBillingFake(
      quote: quote,
      requestJson: scheduledPaymentRequestJson(
        status: 'PENDING',
        requestedAmount: '75.00',
        paidAmount: '0.00',
        remainingAmount: '75.00',
      ),
    );
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: ScheduledPaymentCollectionPage(
            repository: repository,
            recoveryStore: MemoryScheduledPaymentRecoveryStore(),
            request: repository.request,
            quoteEnabled: true,
            zeroPayableEnabled: false,
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(FilledButton, 'Thu phí khám'));
    await tester.pumpAndSettle();

    expect(repository.payments, hasLength(1));
    expect(repository.payments.single.command.amount, '75.00');
    expect(
      repository.payments.single.command.amount,
      quote.patientPayable.format(),
    );
    expect(repository.payments.single.command.quoteId, quote.quoteId);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'zero-liability request is available at zero balance and frozen retry ignores a missing refreshed quote',
    (tester) async {
      useTallViewport(tester);
      const zeroQuoteId = '10000000-0000-4000-8000-000000000021';
      final repository = ScheduledBillingFake(
        quote: examQuote(
          quoteId: zeroQuoteId,
          payable: const Money(0),
          support: const Money(10000),
          supportRate: '1.0',
        ),
        firstZeroError: StateError('Connection lost'),
        requestJson: scheduledPaymentRequestJson(
          status: 'PENDING',
          requestedAmount: '0.00',
          paidAmount: '0.00',
          remainingAmount: '0.00',
        ),
      );
      final recoveryStore = MemoryScheduledPaymentRecoveryStore();
      Widget page(Key key) => MaterialApp(
        home: Scaffold(
          body: ScheduledPaymentCollectionPage(
            key: key,
            repository: repository,
            recoveryStore: recoveryStore,
            request: repository.request,
            quoteEnabled: true,
            zeroPayableEnabled: true,
          ),
        ),
      );

      await tester.pumpWidget(page(const ValueKey('zero-first')));
      await tester.pumpAndSettle();
      final confirmZero = find.text('Xác nhận nghĩa vụ 0 đồng');
      expect(confirmZero, findsOneWidget);
      await tester.tap(confirmZero);
      await tester.pumpAndSettle();
      expect(repository.zeroPayments, hasLength(1));
      final frozen = recoveryStore.entries[paymentRequestId]!;
      final frozenKey = frozen.idempotencyKey;
      expect(frozen.zeroCommand?.quoteId, zeroQuoteId);
      expect(find.text('Gửi lại đúng yêu cầu đã khóa'), findsOneWidget);

      repository.quote = null;
      repository.quoteError = const ApiFailure(
        'No current quote',
        status: 404,
        code: 'OUTPATIENT_QUOTE_NOT_FOUND',
      );
      await tester.pumpWidget(page(const ValueKey('zero-reopened')));
      await tester.pumpAndSettle();
      expect(find.text('Gửi lại đúng yêu cầu đã khóa'), findsOneWidget);
      await tester.tap(find.text('Gửi lại đúng yêu cầu đã khóa'));
      await tester.pumpAndSettle();
      expect(repository.zeroPayments, hasLength(2));
      expect(repository.zeroPayments.last.idempotencyKey, frozenKey);
      expect(repository.zeroPayments.last.command.quoteId, zeroQuoteId);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'a closed unpaid operation clears recovery and blocks another collection',
    (tester) async {
      useTallViewport(tester);
      final repository = ScheduledBillingFake(resultStatus: 'CLOSED_NOT_PAID');
      final recoveryStore = MemoryScheduledPaymentRecoveryStore();
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: ScheduledPaymentCollectionPage(
              repository: repository,
              recoveryStore: recoveryStore,
              request: repository.request,
              quoteEnabled: false,
              zeroPayableEnabled: false,
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(FilledButton, 'Thu phí khám'));
      await tester.pumpAndSettle();

      expect(find.textContaining('được đóng mà chưa thu tiền'), findsOneWidget);
      expect(find.text('Thu phí khám'), findsNothing);
      expect(recoveryStore.entries, isEmpty);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'closed-not-paid conflict clears recovery and disables a stale request',
    (tester) async {
      useTallViewport(tester);
      final repository = ScheduledBillingFake(
        firstPayError: const ApiFailure(
          'Payment operation closed without payment',
          status: 409,
          code: 'PAYMENT_OPERATION_CLOSED',
        ),
      );
      final recoveryStore = MemoryScheduledPaymentRecoveryStore();
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: ScheduledPaymentCollectionPage(
              repository: repository,
              recoveryStore: recoveryStore,
              request: repository.request,
              quoteEnabled: false,
              zeroPayableEnabled: false,
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(FilledButton, 'Thu phí khám'));
      await tester.pumpAndSettle();

      expect(recoveryStore.entries, isEmpty);
      expect(find.textContaining('được đóng mà chưa thu tiền'), findsOneWidget);
      expect(find.widgetWithText(FilledButton, 'Thu phí khám'), findsNothing);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('invoice patient edit invalidates a late history response', (
    tester,
  ) async {
    useTallViewport(tester);
    final repository = BillingStub();
    final pendingHistory = Completer<PageResult<Invoice>>();
    repository.onInvoices = (id, _) => id == patientId
        ? pendingHistory.future
        : Future.value(invoicePage(items: [invoiceRecord(patient: id)]));
    repository.onCreateInvoice = (id, _) =>
        Future.value(invoiceRecord(id: eventId, patient: id));
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: InvoicePage(repository: repository)),
      ),
    );
    await enterField(tester, 'Mã bệnh nhân (UUID)', patientId);
    await tester.tap(find.text('Tải lịch sử'));
    await tester.pump();
    expect(repository.invoiceHistoryCalls, hasLength(1));

    await enterField(tester, 'Mã bệnh nhân (UUID)', eventId);
    pendingHistory.complete(invoicePage(items: [invoiceRecord()]));
    await tester.pumpAndSettle();
    expect(find.text('Hóa đơn · 2026-10-21'), findsNothing);

    await tester.tap(find.widgetWithText(FilledButton, 'Lập hóa đơn'));
    await tester.pumpAndSettle();
    expect(find.text('Hóa đơn · 2026-10-21'), findsNothing);
    expect(find.textContaining('đã tạo cho mã bệnh nhân'), findsNothing);
  });

  testWidgets('each invoice submits the payment method shown on its own row', (
    tester,
  ) async {
    useTallViewport(tester);
    final repository = BillingStub();
    repository.onInvoices = (_, _) => Future.value(
      invoicePage(
        items: [
          invoiceRecord(id: invoiceId),
          invoiceRecord(id: feeId),
        ],
      ),
    );
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: InvoicePage(repository: repository)),
      ),
    );
    await enterField(tester, 'Mã bệnh nhân (UUID)', patientId);
    await tester.tap(find.text('Tải lịch sử'));
    await tester.pumpAndSettle();
    final titles = find.text('Hóa đơn · 2026-10-21');
    expect(titles, findsNWidgets(2));
    final secondTile = find.byKey(ValueKey('invoice-$feeId'));
    final firstTile = find.byKey(ValueKey('invoice-$invoiceId'));
    await ensureFinderVisible(tester, secondTile);
    await tester.tap(secondTile);
    await tester.pumpAndSettle();
    final secondMethod = find.byKey(ValueKey('payment-method-$feeId'));
    await ensureFinderVisible(tester, secondMethod);
    await tester.tap(secondMethod);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Chuyển khoản').last);
    await tester.pumpAndSettle();

    await ensureFinderVisible(tester, firstTile);
    await tester.tap(firstTile);
    await tester.pumpAndSettle();

    final firstCard = firstTile;
    await tester.tap(
      find.descendant(
        of: firstCard,
        matching: find.text('Ghi nhận thanh toán'),
      ),
    );
    await tester.pumpAndSettle();
    expect(repository.invoicePayments.single.invoiceId, invoiceId);
    expect(repository.invoicePayments.single.paymentMethod, 'CASH');
  });

  testWidgets(
    'uncertain invoice payment stays locked after unpaid refresh and patient switch',
    (tester) async {
      useTallViewport(tester);
      final repository = BillingStub();
      repository.onInvoices = (id, _) =>
          Future.value(invoicePage(items: [invoiceRecord(patient: id)]));
      repository.onPayInvoice = (_, _) =>
          Future.error(const ApiFailure('Timeout'));
      repository.onInvoice = (id) =>
          Future.value(invoiceRecord(id: id, paid: false));
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(body: InvoicePage(repository: repository)),
        ),
      );
      await enterField(tester, 'Mã bệnh nhân (UUID)', patientId);
      await tester.tap(find.text('Tải lịch sử'));
      await tester.pumpAndSettle();
      final invoiceTile = find.byKey(ValueKey('invoice-$invoiceId'));
      await ensureFinderVisible(tester, invoiceTile);
      await tester.tap(invoiceTile);
      await tester.pumpAndSettle();
      final payButton = find.text('Ghi nhận thanh toán');
      await ensureFinderVisible(tester, payButton);
      await tester.tap(payButton);
      await tester.pumpAndSettle();
      expect(repository.invoicePayments, hasLength(1));
      final reconcile = find.text(
        'Tra cứu lại hóa đơn trước khi thanh toán lại',
      );
      // The row may collapse as the timeout error is rendered after its child
      // button was tapped. Reopen the same invoice before reconciliation.
      if (reconcile.evaluate().isEmpty) {
        await tester.tap(invoiceTile);
        await tester.pumpAndSettle();
      }
      await ensureFinderVisible(tester, reconcile);
      await tester.tap(reconcile);
      await tester.pumpAndSettle();
      expect(repository.invoiceLookups, [invoiceId]);
      expect(reconcile, findsOneWidget);

      await enterField(tester, 'Mã bệnh nhân (UUID)', eventId);
      final historyButton = find.text('Tải lịch sử');
      await ensureFinderVisible(tester, historyButton);
      await tester.tap(historyButton);
      await tester.pumpAndSettle();
      await enterField(tester, 'Mã bệnh nhân (UUID)', patientId);
      await ensureFinderVisible(tester, historyButton);
      await tester.tap(historyButton);
      await tester.pumpAndSettle();
      await ensureFinderVisible(tester, invoiceTile);
      await tester.tap(invoiceTile);
      await tester.pumpAndSettle();
      expect(reconcile, findsOneWidget);
      expect(find.text('Ghi nhận thanh toán'), findsNothing);
      expect(repository.invoicePayments, hasLength(1));
    },
  );

  testWidgets(
    'paid refresh keeps the receipt visible outside the collection form',
    (tester) async {
      useTallViewport(tester);
      final repository = BillingStub();
      var reads = 0;
      repository.onDiscoverPayment = (_, _, _, _) async => ++reads == 1
          ? paymentDiscovery()
          : BillingDto.paymentDiscovery({
              ...paymentDiscoveryJson(),
              'status': 'PAID',
              'paidAmount': 100.0,
              'remainingAmount': 0.0,
            });
      await openLedger(tester, repository);
      final confirmation = find.byType(CheckboxListTile).last;
      await ensureFinderVisible(tester, confirmation);
      await tester.tap(confirmation);
      await tester.pump();
      await ensureFinderVisible(tester, find.text('Ghi nhận khoản thu'));
      await tester.tap(find.text('Ghi nhận khoản thu'));
      await tester.pumpAndSettle();
      expect(find.text('Đã thu thành công'), findsOneWidget);
      expect(find.text('Ghi nhận khoản thu'), findsNothing);
      expect(repository.completedPayments, hasLength(1));
    },
  );

  testWidgets('stale refresh keeps payment locked and retries only the read', (
    tester,
  ) async {
    useTallViewport(tester);
    final repository = BillingStub();
    var reads = 0;
    repository.onDiscoverPayment = (_, _, _, _) async {
      reads++;
      return reads < 3
          ? paymentDiscovery()
          : BillingDto.paymentDiscovery({
              ...paymentDiscoveryJson(),
              'status': 'PAID',
              'remainingAmount': 0.0,
              'paidAmount': 100.0,
            });
    };
    await openLedger(tester, repository);
    final confirmation = find.byType(CheckboxListTile).last;
    await ensureFinderVisible(tester, confirmation);
    await tester.tap(confirmation);
    await tester.pump();
    await ensureFinderVisible(tester, find.text('Ghi nhận khoản thu'));
    await tester.tap(find.text('Ghi nhận khoản thu'));
    await tester.pumpAndSettle();
    expect(find.text('Ghi nhận khoản thu'), findsNothing);
    final retry = find.text('Thử kiểm tra lại');
    await ensureFinderVisible(tester, retry);
    await tester.tap(retry);
    await tester.pumpAndSettle();
    expect(reads, 3);
    expect(repository.completedPayments, hasLength(1));
    expect(find.text('Đã thu thành công'), findsOneWidget);
  });

  testWidgets('intermediate stale balance cannot unlock a completed payment', (
    tester,
  ) async {
    useTallViewport(tester);
    final repository = BillingStub();
    var reads = 0;
    repository.onDiscoverPayment = (_, _, _, _) async => ++reads == 1
        ? paymentDiscovery()
        : BillingDto.paymentDiscovery({
            ...paymentDiscoveryJson(),
            'status': 'PARTIALLY_PAID',
            'paidAmount': 30.0,
            'remainingAmount': 70.0,
          });
    await openLedger(tester, repository);
    await enterField(tester, 'Số tiền thực nhận (VND)', '25.25');
    final confirmation = find.byType(CheckboxListTile).last;
    await ensureFinderVisible(tester, confirmation);
    await tester.tap(confirmation);
    await tester.pump();
    await ensureFinderVisible(tester, find.text('Ghi nhận khoản thu'));
    await tester.tap(find.text('Ghi nhận khoản thu'));
    await tester.pumpAndSettle();
    expect(find.text('Ghi nhận khoản thu'), findsNothing);
    expect(find.text('Thử kiểm tra lại'), findsOneWidget);
    expect(repository.completedPayments, hasLength(1));
  });

  testWidgets('ledger freezes and retries the same payment after tab changes', (
    tester,
  ) async {
    useTallViewport(tester);
    final repository = BillingStub();
    var attempts = 0;
    repository.onCompletePayment = (_, _) {
      attempts++;
      if (attempts == 1) return Future.error(const ApiFailure('Timeout'));
      return Future.value(ledgerPayment());
    };
    repository.onDiscoverPayment = (_, _, _, _) => Future.value(
      paymentDiscovery(remainingMinor: attempts == 0 ? 7525 : 5000),
    );
    await openLedger(tester, repository);
    await enterField(tester, 'Số tiền thực nhận (VND)', '25.25');
    final confirmation = find.byType(CheckboxListTile).last;
    await ensureFinderVisible(tester, confirmation);
    await tester.tap(confirmation);
    await tester.pump();
    final submit = find.text('Ghi nhận khoản thu');
    await ensureFinderVisible(tester, submit);
    await tester.tap(submit);
    await tester.pumpAndSettle();
    expect(repository.completedPayments, hasLength(1));
    final frozen = repository.completedPayments.single.body;

    await tester.tap(find.text('Báo cáo'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Xét nghiệm và thuốc'));
    await tester.pumpAndSettle();
    final retry = find.text('Gửi lại đúng lệnh đã khóa');
    await ensureFinderVisible(tester, retry);
    expect(retry, findsOneWidget);
    await tester.tap(retry);
    await tester.pumpAndSettle();

    expect(repository.completedPayments, hasLength(2));
    expect(repository.completedPayments.last.body, frozen);
    expect(
      repository.completedPayments.last.body['idempotencyKey'],
      matches(RegExp(r'^[0-9a-f-]{36}$')),
    );
    await ensureFinderVisible(tester, find.text('Thông tin biên nhận'));
    await tester.tap(find.text('Thông tin biên nhận'));
    await tester.pumpAndSettle();
    expect(find.text('Mã giao dịch: $transactionId'), findsOneWidget);
  });

  testWidgets(
    'automatic ledger reconciliation follows completion and cannot unlock the next uncertain command',
    (tester) async {
      useTallViewport(tester);
      final repository = BillingStub();
      final pendingRefresh = Completer<PaymentDiscovery>();
      var discoveryCalls = 0;
      var paymentAttempts = 0;
      repository.onDiscoverPayment = (_, _, _, _) {
        discoveryCalls++;
        if (discoveryCalls == 2) return pendingRefresh.future;
        return Future.value(
          paymentDiscovery(remainingMinor: discoveryCalls == 1 ? 7525 : 5000),
        );
      };
      repository.onCompletePayment = (_, _) {
        paymentAttempts++;
        return paymentAttempts == 1
            ? Future.value(ledgerPayment())
            : Future.error(const ApiFailure('Timeout'));
      };

      await openLedger(tester, repository);
      await enterField(tester, 'Số tiền thực nhận (VND)', '25.25');
      final confirmation = find.byType(CheckboxListTile).last;
      await ensureFinderVisible(tester, confirmation);
      await tester.tap(confirmation);
      await tester.pump();
      final submit = find.text('Ghi nhận khoản thu');
      await ensureFinderVisible(tester, submit);
      await tester.tap(submit);
      await tester.pump();

      expect(repository.completedPayments, hasLength(1));
      expect(discoveryCalls, 2);
      final reconciling = find.text('Đang cập nhật trạng thái khoản thu…');
      await ensureFinderVisible(tester, reconciling);
      expect(reconciling, findsOneWidget);
      expect(repository.paymentDiscoveries, hasLength(2));

      pendingRefresh.complete(paymentDiscovery(remainingMinor: 5000));
      await tester.pumpAndSettle();
      expect(reconciling, findsNothing);
      expect(find.text('Thử kiểm tra lại'), findsNothing);
      expect(find.text('Ghi nhận khoản thu'), findsOneWidget);
      tester
          .state<ScaffoldMessengerState>(find.byType(ScaffoldMessenger))
          .hideCurrentSnackBar();
      await tester.pumpAndSettle();

      await enterField(tester, 'Số tiền thực nhận (VND)', '15.00');
      await ensureFinderVisible(tester, confirmation);
      await tester.tap(confirmation);
      await tester.pump();
      await ensureFinderVisible(tester, submit);
      await tester.tap(submit);
      await tester.pumpAndSettle();

      expect(repository.completedPayments, hasLength(2));
      expect(discoveryCalls, 2);
      final retry = find.text('Gửi lại đúng lệnh đã khóa');
      await ensureFinderVisible(tester, retry);
      expect(retry, findsOneWidget);
      expect(find.textContaining('Mã giao dịch:'), findsNothing);
    },
  );

  testWidgets('reconciliation never unlocks a replacement payment request', (
    tester,
  ) async {
    useTallViewport(tester);
    final repository = BillingStub();
    var reads = 0;
    repository.onDiscoverPayment = (_, _, _, _) async => ++reads == 1
        ? paymentDiscovery()
        : BillingDto.paymentDiscovery({
            ...paymentDiscoveryJson(),
            'paymentRequestId': sourceId,
          });
    await openLedger(tester, repository);
    final confirmation = find.byType(CheckboxListTile).last;
    await ensureFinderVisible(tester, confirmation);
    await tester.tap(confirmation);
    await tester.pump();
    final submit = find.text('Ghi nhận khoản thu');
    await ensureFinderVisible(tester, submit);
    await tester.tap(submit);
    await tester.pumpAndSettle();
    expect(repository.completedPayments, hasLength(1));
    expect(reads, 2);
    expect(find.text('Ghi nhận khoản thu'), findsNothing);
    expect(find.text('Thử kiểm tra lại'), findsOneWidget);
  });

  testWidgets(
    'new partial payment does not expose the prior receipt to unlock uncertainty',
    (tester) async {
      useTallViewport(tester);
      final repository = BillingStub();
      var attempts = 0;
      repository.onCompletePayment = (_, _) {
        attempts++;
        if (attempts == 1) return Future.value(ledgerPayment());
        return Future.error(const ApiFailure('Timeout'));
      };
      repository.onDiscoverPayment = (_, _, _, _) => Future.value(
        paymentDiscovery(remainingMinor: attempts == 0 ? 7525 : 5000),
      );
      await openLedger(tester, repository);
      await enterField(tester, 'Số tiền thực nhận (VND)', '25.25');
      final confirmation = find.byType(CheckboxListTile).last;
      await ensureFinderVisible(tester, confirmation);
      await tester.tap(confirmation);
      await tester.pump();
      final submit = find.text('Ghi nhận khoản thu');
      await ensureFinderVisible(tester, submit);
      await tester.tap(submit);
      await tester.pumpAndSettle();
      expect(repository.paymentDiscoveries, hasLength(2));
      tester
          .state<ScaffoldMessengerState>(find.byType(ScaffoldMessenger))
          .hideCurrentSnackBar();
      await tester.pumpAndSettle();

      await ensureFinderVisible(tester, confirmation);
      await tester.tap(confirmation);
      await tester.pump();
      await enterField(tester, 'Số tiền thực nhận (VND)', '15.00');
      await ensureFinderVisible(tester, submit);
      await tester.tap(submit);
      await tester.pumpAndSettle();
      expect(repository.completedPayments, hasLength(2));
      final retry = find.text('Gửi lại đúng lệnh đã khóa');
      await ensureFinderVisible(tester, retry);
      expect(retry, findsOneWidget);
      expect(find.text('Thử kiểm tra lại'), findsNothing);
    },
  );

  testWidgets(
    'refund stays mounted while the write is pending and keeps its receipt',
    (tester) async {
      useTallViewport(tester);
      final repository = BillingStub();
      final pendingRefund = Completer<LedgerRefund>();
      repository.onRecordRefund = (_, _) => pendingRefund.future;
      await tester.pumpWidget(billingHost(repository));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Hoàn tiền'));
      await tester.pumpAndSettle();
      await enterField(tester, 'Mã đơn thuốc (UUID)', prescriptionId);
      await tester.tap(find.text('Tra cứu nghĩa vụ'));
      await tester.pumpAndSettle();
      await enterField(tester, 'Số tiền đã hoàn (VND)', '15.00');
      await enterField(tester, 'Lý do', 'Unused medication returned');
      await tester.tap(find.byType(CheckboxListTile).last);
      await tester.pump();
      expect(
        tester
            .widget<CheckboxListTile>(find.byType(CheckboxListTile).last)
            .value,
        isTrue,
      );
      await tester.tap(find.text('Ghi nhận hoàn tiền'));
      await tester.pump();
      expect(repository.refundLookups, hasLength(1));
      expect(find.text('Tải lại để kiểm tra trạng thái'), findsNothing);

      pendingRefund.complete(ledgerRefund());
      await tester.pumpAndSettle();
      expect(repository.refundLookups, hasLength(2));
      final receipt = find.textContaining('Biên nhận hoàn tiền · COMPLETED');
      await ensureFinderVisible(tester, receipt);
      expect(receipt, findsOneWidget);
      expect(
        find.textContaining('Giao dịch gốc: $transactionId'),
        findsOneWidget,
      );
    },
  );

  testWidgets('refund keeps the exact retry command across Billing tabs', (
    tester,
  ) async {
    useTallViewport(tester);
    final repository = BillingStub();
    var attempts = 0;
    repository.onRecordRefund = (_, _) {
      attempts++;
      if (attempts == 1) return Future.error(const ApiFailure('Timeout'));
      return Future.value(ledgerRefund());
    };
    await tester.pumpWidget(billingHost(repository));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Hoàn tiền'));
    await tester.pumpAndSettle();
    await enterField(tester, 'Mã đơn thuốc (UUID)', prescriptionId);
    await tester.tap(find.text('Tra cứu nghĩa vụ'));
    await tester.pumpAndSettle();
    await enterField(tester, 'Số tiền đã hoàn (VND)', '15.00');
    await enterField(tester, 'Lý do', 'Unused medication returned');
    await tester.tap(find.byType(CheckboxListTile).last);
    await tester.pump();
    expect(
      tester.widget<CheckboxListTile>(find.byType(CheckboxListTile).last).value,
      isTrue,
    );
    await tester.tap(find.text('Ghi nhận hoàn tiền'));
    await tester.pumpAndSettle();
    expect(repository.refundRequests, hasLength(1));
    final frozen = repository.refundRequests.single.body;

    await tester.tap(find.text('Báo cáo'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Hoàn tiền'));
    await tester.pumpAndSettle();
    expect(find.text('Gửi lại đúng lệnh hoàn tiền đã khóa'), findsOneWidget);
    final retry = find.text('Gửi lại đúng lệnh hoàn tiền đã khóa');
    await ensureFinderVisible(tester, retry);
    await tester.tap(retry);
    await tester.pumpAndSettle();

    expect(repository.refundRequests, hasLength(2));
    expect(repository.refundRequests.last.body, frozen);
    expect(repository.refundLookups, hasLength(2));
    final receipt = find.textContaining('Biên nhận hoàn tiền · COMPLETED');
    await ensureFinderVisible(tester, receipt);
    expect(receipt, findsOneWidget);
  });
}
