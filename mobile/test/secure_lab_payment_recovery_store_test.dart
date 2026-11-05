import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/features/billing/data/secure_lab_payment_recovery_store.dart';
import 'package:mediflow_mobile/features/billing/domain/lab_quote_payment.dart';

const _firstAccount = '10000000-0000-4000-8000-000000000041';
const _secondAccount = '10000000-0000-4000-8000-000000000042';
const _paymentRequest = '10000000-0000-4000-8000-000000000043';
const _quote = '10000000-0000-4000-8000-000000000044';
const _key = '10000000-0000-4000-8000-000000000045';
const _correlation = '10000000-0000-4000-8000-000000000046';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late FlutterSecureStorage storage;

  setUp(() {
    FlutterSecureStorage.setMockInitialValues({});
    storage = const FlutterSecureStorage();
  });

  test('a payment survives a restart for the same account only', () async {
    await SecureLabPaymentRecoveryStore(storage, accountId: _firstAccount).save(
      _paymentRequest,
      const LabPaymentRecovery(
        idempotencyKey: _key,
        correlationId: _correlation,
        quoteId: _quote,
        payment: LabPaymentCommand(
          quoteId: _quote,
          amount: '36000.00',
          currency: 'VND',
          paymentMethod: 'BANK_TRANSFER',
        ),
      ),
    );

    final resumed = await SecureLabPaymentRecoveryStore(
      storage,
      accountId: _firstAccount,
    ).read(_paymentRequest);
    expect(resumed?.idempotencyKey, _key);
    expect(resumed?.correlationId, _correlation);
    expect(resumed?.payment?.quoteId, _quote);
    expect(resumed?.payment?.amount, '36000.00');
    expect(resumed?.payment?.paymentMethod, 'BANK_TRANSFER');

    expect(
      await SecureLabPaymentRecoveryStore(
        storage,
        accountId: _secondAccount,
      ).read(_paymentRequest),
      isNull,
    );
  });

  test('a zero-liability command is restored without a payment', () async {
    final store = SecureLabPaymentRecoveryStore(
      storage,
      accountId: _firstAccount,
    );
    await store.save(
      _paymentRequest,
      const LabPaymentRecovery(
        idempotencyKey: _key,
        correlationId: _correlation,
        quoteId: _quote,
        payment: null,
      ),
    );

    final resumed = await store.read(_paymentRequest);
    expect(resumed?.quoteId, _quote);
    expect(resumed?.payment, isNull);

    await store.clear(_paymentRequest);
    expect(await store.read(_paymentRequest), isNull);
  });

  test(
    'a damaged entry is refused instead of creating a new command',
    () async {
      final store = SecureLabPaymentRecoveryStore(
        storage,
        accountId: _firstAccount,
      );
      await storage.write(
        key: 'mediflow.billing.lab-payment.v1.$_firstAccount.$_paymentRequest',
        value: '{"idempotencyKey":"not-a-uuid"}',
      );

      await expectLater(store.read(_paymentRequest), throwsFormatException);
    },
  );
}
