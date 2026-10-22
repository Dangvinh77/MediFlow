import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/domain/workspace_access.dart';
import 'package:mediflow_mobile/core/domain/page_result.dart';
import 'package:mediflow_mobile/core/network/api_failure.dart';
import 'package:mediflow_mobile/core/providers.dart';
import 'package:mediflow_mobile/features/billing/domain/billing_models.dart';
import 'package:mediflow_mobile/features/billing/presentation/billing_page.dart';
import 'package:mediflow_mobile/features/billing/presentation/invoice_page.dart';
import 'package:mediflow_mobile/features/billing/presentation/providers.dart';

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

Widget billingHost(BillingStub repository, {String role = 'ADMIN'}) =>
    ProviderScope(
      overrides: [
        workspaceAccessProvider.overrideWithValue(
          WorkspaceAccess(sessionIdentity: Object(), role: role),
        ),
        billingRepositoryProvider.overrideWithValue(repository),
      ],
      child: const MaterialApp(home: Scaffold(body: BillingPage())),
    );

Future<void> openLedger(WidgetTester tester, BillingStub repository) async {
  await tester.pumpWidget(billingHost(repository));
  await tester.pumpAndSettle();
  await tester.tap(
    find.descendant(of: find.byType(TabBar), matching: find.text('Khoản thu')),
  );
  await tester.pumpAndSettle();
  await enterField(tester, 'Mã nguồn (sourceId)', sourceId);
  await enterField(tester, 'Mã bệnh nhân (patientId)', patientId);
  await enterField(tester, 'Mã đợt khám (careEpisodeId)', episodeId);
  await tester.tap(find.text('Tra cứu từ Billing'));
  await tester.pumpAndSettle();
  expect(repository.paymentDiscoveries, hasLength(1));
  expect(find.text('Khoản thu do Billing cung cấp'), findsOneWidget);
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

    final firstCard = find.ancestor(
      of: find.text('Mã hóa đơn: $invoiceId'),
      matching: find.byType(Card),
    );
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
      paymentDiscovery(remainingMinor: attempts == 0 ? 7525 : 5025),
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
    await tester.tap(find.text('Khoản thu'));
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
          paymentDiscovery(remainingMinor: discoveryCalls == 1 ? 7525 : 5025),
        );
      };
      repository.onCompletePayment = (_, _) {
        paymentAttempts++;
        return paymentAttempts == 1
            ? Future.value(ledgerPayment())
            : Future.error(const ApiFailure('Timeout'));
      };

      await openLedger(tester, repository);
      await enterField(tester, 'Số tiền thực nhận (VND)', '25.00');
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
      final reconciling = find.text('Đang đối soát…');
      await ensureFinderVisible(tester, reconciling);
      expect(reconciling, findsOneWidget);
      expect(repository.paymentDiscoveries, hasLength(2));

      pendingRefresh.complete(paymentDiscovery(remainingMinor: 5025));
      await tester.pumpAndSettle();
      expect(reconciling, findsNothing);
      expect(find.text('Đối soát số dư mới từ Billing'), findsNothing);
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
        paymentDiscovery(remainingMinor: attempts == 0 ? 7525 : 5025),
      );
      await openLedger(tester, repository);
      await enterField(tester, 'Số tiền thực nhận (VND)', '25.00');
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
      expect(find.text('Đối soát số dư mới từ Billing'), findsNothing);
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
