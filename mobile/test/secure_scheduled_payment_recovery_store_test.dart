import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/features/billing/data/secure_scheduled_payment_recovery_store.dart';
import 'package:mediflow_mobile/features/billing/domain/scheduled_payments.dart';

const _firstAccount = '10000000-0000-4000-8000-000000000031';
const _secondAccount = '10000000-0000-4000-8000-000000000032';
const _paymentRequest = '10000000-0000-4000-8000-000000000033';
const _reservation = '10000000-0000-4000-8000-000000000034';
const _idempotencyKey = '10000000-0000-4000-8000-000000000035';
const _correlationId = '10000000-0000-4000-8000-000000000036';

ScheduledPaymentRecovery _recovery() => const ScheduledPaymentRecovery(
  kind: ScheduledPaymentRecoveryKind.payment,
  idempotencyKey: _idempotencyKey,
  correlationId: _correlationId,
  paymentCommand: ScheduledPaymentCommand(
    reservationId: _reservation,
    expectedReservationRevision: 3,
    quoteId: null,
    amount: '75.00',
    currency: 'VND',
    paymentMethod: 'CASH',
    providerReference: null,
  ),
  zeroCommand: null,
  operationId: null,
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late Map<String, String> storageValues;
  late FlutterSecureStorage storage;

  setUp(() {
    storageValues = {};
    FlutterSecureStorage.setMockInitialValues(storageValues);
    storage = const FlutterSecureStorage();
  });

  test('recovery persists for the same account but is isolated across logins', () async {
    final firstSession = SecureScheduledPaymentRecoveryStore(
      storage,
      accountId: _firstAccount,
    );
    await firstSession.save(_paymentRequest, _recovery());

    // A fresh store represents app restart or token refresh for the same account.
    final resumedSession = SecureScheduledPaymentRecoveryStore(
      storage,
      accountId: _firstAccount,
    );
    expect(
      (await resumedSession.read(_paymentRequest))?.idempotencyKey,
      _idempotencyKey,
    );

    // A different signed `sub` cannot read, overwrite, or clear the first actor's command.
    final nextLogin = SecureScheduledPaymentRecoveryStore(
      storage,
      accountId: _secondAccount,
    );
    expect(await nextLogin.read(_paymentRequest), isNull);
    await nextLogin.save(
      _paymentRequest,
      _recovery().withOperation('10000000-0000-4000-8000-000000000037'),
    );
    await nextLogin.clear(_paymentRequest);
    expect(
      (await resumedSession.read(_paymentRequest))?.idempotencyKey,
      _idempotencyKey,
    );
    expect(
      storageValues.keys,
      contains(
        'mediflow.billing.scheduled-payment.v3.$_firstAccount.$_paymentRequest',
      ),
    );
    expect(
      storageValues.keys,
      isNot(
        contains(
          'mediflow.billing.scheduled-payment.v3.$_secondAccount.$_paymentRequest',
        ),
      ),
    );
  });

  test('store rejects missing or malformed stable account identity', () {
    expect(
      () => SecureScheduledPaymentRecoveryStore(storage, accountId: ''),
      throwsArgumentError,
    );
    expect(
      () => SecureScheduledPaymentRecoveryStore(
        storage,
        accountId: 'not-an-account-id',
      ),
      throwsArgumentError,
    );
  });
}
