import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/network/api_failure.dart';
import 'package:mediflow_mobile/features/billing/domain/billing_models.dart';
import 'package:mediflow_mobile/features/billing/domain/lab_quote_payment.dart';
import 'package:mediflow_mobile/features/billing/domain/scheduled_payments.dart';
import 'package:mediflow_mobile/features/billing/presentation/lab_quote_payment_panel.dart';

const requestId = '10000000-0000-4000-8000-000000000001';
const quoteId = '10000000-0000-4000-8000-000000000002';

OutpatientQuote quote({
  int payable = 8000000,
  Duration ttl = const Duration(hours: 1),
}) => OutpatientQuote(
  quoteId: quoteId,
  paymentRequestId: requestId,
  patientId: '10000000-0000-4000-8000-000000000003',
  careEpisodeType: 'OUTPATIENT_VISIT',
  careEpisodeId: '10000000-0000-4000-8000-000000000004',
  currency: 'VND',
  lines: const [],
  grossAmount: Money(payable),
  supportAmount: const Money(0),
  patientPayable: Money(payable),
  issuedAt: DateTime.now().toUtc().toIso8601String(),
  expiresAt: DateTime.now().add(ttl).toUtc().toIso8601String(),
);

LedgerPayment payment(String status) => LedgerPayment(
  transactionId: '10000000-0000-4000-8000-000000000005',
  accountId: '10000000-0000-4000-8000-000000000006',
  paymentRequestId: requestId,
  classification: 'SERVICE_PAYMENT',
  status: status,
  amount: const Money(8000000),
  currency: 'VND',
  paymentMethod: 'CASH',
  completedAt: null,
);

class FakeLabRepository implements LabQuotePaymentRepository {
  OutpatientQuote? quote;
  Object? quoteError;
  final payResults = <Object>[];
  final calls = <(LabPaymentCommand, String, String)>[];
  final zeroCalls = <(String, String)>[];
  Object? zeroError;

  @override
  Future<OutpatientQuote> outpatientQuote(String paymentRequestId) async {
    if (quoteError != null) throw quoteError!;
    return quote!;
  }

  @override
  Future<LedgerPayment> payLabQuote(
    String paymentRequestId,
    LabPaymentCommand command, {
    required String idempotencyKey,
    required String correlationId,
  }) async {
    calls.add((command, idempotencyKey, correlationId));
    final next = payResults.removeAt(0);
    if (next is LedgerPayment) return next;
    throw next;
  }

  @override
  Future<LabZeroLiabilityReceipt> confirmLabZeroLiability(
    String paymentRequestId,
    String quoteId, {
    required String idempotencyKey,
    required String correlationId,
  }) async {
    zeroCalls.add((quoteId, idempotencyKey));
    if (zeroError != null) throw zeroError!;
    return LabZeroLiabilityReceipt(
      eligibilityOperationId: '10000000-0000-4000-8000-000000000007',
      paymentRequestId: paymentRequestId,
      quoteId: quoteId,
      clearanceId: '10000000-0000-4000-8000-000000000008',
    );
  }
}

class MemoryLabRecovery implements LabPaymentRecoveryStore {
  final entries = <String, LabPaymentRecovery>{};
  bool failSave = false;

  @override
  Future<LabPaymentRecovery?> read(String id) async => entries[id];
  @override
  Future<void> save(String id, LabPaymentRecovery recovery) async {
    if (failSave) throw const FormatException('Không lưu được yêu cầu.');
    entries[id] = recovery;
  }

  @override
  Future<void> clear(String id) async => entries.remove(id);
}

void main() {
  late FakeLabRepository repository;
  late MemoryLabRecovery recovery;
  late List<bool> locks;
  late List<LedgerPayment> paid;
  late int zeroConfirmed;

  setUp(() {
    repository = FakeLabRepository()..quote = quote();
    recovery = MemoryLabRecovery();
    locks = [];
    paid = [];
    zeroConfirmed = 0;
  });

  Widget panel({bool zeroEnabled = true, Key? key}) => MaterialApp(
    home: Scaffold(
      body: SingleChildScrollView(
        child: LabQuotePaymentPanel(
          key: key,
          paymentRequestId: requestId,
          repository: repository,
          recoveryStore: recovery,
          zeroEnabled: zeroEnabled,
          onLockChange: locks.add,
          onPaid: paid.add,
          onZeroConfirmed: () => zeroConfirmed++,
        ),
      ),
    ),
  );

  testWidgets('collects exactly the quoted amount with no amount field', (
    tester,
  ) async {
    repository.payResults.add(payment('COMPLETED'));
    await tester.pumpWidget(panel());
    await tester.pumpAndSettle();

    expect(find.byType(TextField), findsNothing);
    expect(find.text('80000.00 VND'), findsOneWidget);
    await tester.tap(find.text('Xác nhận thu theo báo giá'));
    await tester.pumpAndSettle();

    final (command, _, _) = repository.calls.single;
    expect(command.quoteId, quoteId);
    expect(command.amount, '80000.00');
    expect(command.paymentMethod, 'CASH');
    expect(paid, hasLength(1));
    expect(recovery.entries, isEmpty);
    expect(locks.last, isFalse);
  });

  testWidgets('a lost response locks and resends the same key and body', (
    tester,
  ) async {
    repository.payResults.addAll([
      const ApiFailure('Mất kết nối'),
      payment('COMPLETED'),
    ]);
    await tester.pumpWidget(panel());
    await tester.pumpAndSettle();
    await tester.tap(find.text('Xác nhận thu theo báo giá'));
    await tester.pumpAndSettle();

    expect(find.text('Xác nhận thu theo báo giá'), findsNothing);
    expect(locks.last, isTrue);
    expect(recovery.entries, contains(requestId));

    await tester.tap(find.text('Gửi lại cùng yêu cầu thu'));
    await tester.pumpAndSettle();

    expect(repository.calls, hasLength(2));
    expect(repository.calls.last.$2, repository.calls.first.$2);
    expect(repository.calls.last.$3, repository.calls.first.$3);
    expect(repository.calls.last.$1.amount, '80000.00');
    expect(paid, hasLength(1));
    expect(recovery.entries, isEmpty);
  });

  testWidgets('a reopened page resumes a saved command with an expired quote', (
    tester,
  ) async {
    repository
      ..quote = quote(ttl: const Duration(hours: -1))
      ..payResults.add(payment('COMPLETED'));
    await recovery.save(
      requestId,
      const LabPaymentRecovery(
        idempotencyKey: '10000000-0000-4000-8000-0000000000a1',
        correlationId: '10000000-0000-4000-8000-0000000000a2',
        quoteId: quoteId,
        payment: LabPaymentCommand(
          quoteId: quoteId,
          amount: '80000.00',
          currency: 'VND',
          paymentMethod: 'BANK_TRANSFER',
        ),
      ),
    );
    await tester.pumpWidget(panel());
    await tester.pumpAndSettle();

    expect(find.text('Xác nhận thu theo báo giá'), findsNothing);
    await tester.tap(find.text('Gửi lại cùng yêu cầu thu'));
    await tester.pumpAndSettle();

    expect(repository.calls.single.$2, '10000000-0000-4000-8000-0000000000a1');
    expect(repository.calls.single.$1.paymentMethod, 'BANK_TRANSFER');
  });

  testWidgets('a definite rejection clears the command and unlocks', (
    tester,
  ) async {
    repository.payResults.add(
      const ApiFailure(
        'Báo giá không hợp lệ',
        status: 409,
        code: 'QUOTE_STALE',
      ),
    );
    await tester.pumpWidget(panel());
    await tester.pumpAndSettle();
    await tester.tap(find.text('Xác nhận thu theo báo giá'));
    await tester.pumpAndSettle();

    expect(recovery.entries, isEmpty);
    expect(locks.last, isFalse);
    expect(find.text('Xác nhận thu theo báo giá'), findsOneWidget);
  });

  testWidgets('a pending payment stays locked for the same command', (
    tester,
  ) async {
    repository.payResults.add(payment('PENDING'));
    await tester.pumpWidget(panel());
    await tester.pumpAndSettle();
    await tester.tap(find.text('Xác nhận thu theo báo giá'));
    await tester.pumpAndSettle();

    expect(paid, isEmpty);
    expect(recovery.entries, contains(requestId));
    expect(find.text('Gửi lại cùng yêu cầu thu'), findsOneWidget);
    expect(locks.last, isTrue);
  });

  testWidgets('nothing is sent when the command cannot be saved first', (
    tester,
  ) async {
    recovery.failSave = true;
    await tester.pumpWidget(panel());
    await tester.pumpAndSettle();
    await tester.tap(find.text('Xác nhận thu theo báo giá'));
    await tester.pumpAndSettle();

    expect(repository.calls, isEmpty);
    expect(locks.last, isFalse);
  });

  testWidgets('a missing quote blocks collection', (tester) async {
    repository.quoteError = const ApiFailure('Không có', status: 404);
    await tester.pumpWidget(panel());
    await tester.pumpAndSettle();

    expect(find.textContaining('Billing chưa có báo giá'), findsOneWidget);
    expect(find.byType(FilledButton), findsNothing);
  });

  testWidgets('an expired quote blocks collection', (tester) async {
    repository.quote = quote(ttl: const Duration(hours: -1));
    await tester.pumpWidget(panel());
    await tester.pumpAndSettle();

    expect(find.textContaining('Báo giá đã hết hạn'), findsOneWidget);
    expect(find.byType(FilledButton), findsNothing);
  });

  testWidgets('a zero quote confirms liability without collecting money', (
    tester,
  ) async {
    repository.quote = quote(payable: 0);
    await tester.pumpWidget(panel());
    await tester.pumpAndSettle();

    expect(find.text('Xác nhận thu theo báo giá'), findsNothing);
    await tester.tap(find.text('Xác nhận nghĩa vụ 0 đồng'));
    await tester.pumpAndSettle();

    expect(repository.zeroCalls.single.$1, quoteId);
    expect(repository.calls, isEmpty);
    expect(zeroConfirmed, 1);
    expect(recovery.entries, isEmpty);
  });

  testWidgets('a lost zero confirmation resends the same key', (tester) async {
    repository
      ..quote = quote(payable: 0)
      ..zeroError = const ApiFailure('Mất kết nối');
    await tester.pumpWidget(panel());
    await tester.pumpAndSettle();
    await tester.tap(find.text('Xác nhận nghĩa vụ 0 đồng'));
    await tester.pumpAndSettle();

    repository.zeroError = null;
    await tester.tap(find.text('Gửi lại cùng yêu cầu xác nhận 0 đồng'));
    await tester.pumpAndSettle();

    expect(repository.zeroCalls, hasLength(2));
    expect(repository.zeroCalls.last.$2, repository.zeroCalls.first.$2);
    expect(zeroConfirmed, 1);
  });

  testWidgets('a zero quote never offers cash when the gate is closed', (
    tester,
  ) async {
    repository.quote = quote(payable: 0);
    await tester.pumpWidget(panel(zeroEnabled: false));
    await tester.pumpAndSettle();

    expect(find.byType(FilledButton), findsNothing);
    expect(find.textContaining('Không thu tiền mặt'), findsOneWidget);
  });
}
