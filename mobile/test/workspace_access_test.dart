import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/app/dependencies.dart';
import 'package:mediflow_mobile/core/providers.dart';
import 'package:mediflow_mobile/features/auth/domain/session.dart';
import 'package:mediflow_mobile/features/auth/presentation/session_controller.dart';
import 'package:mediflow_mobile/features/billing/data/secure_scheduled_payment_recovery_store.dart';
import 'package:mediflow_mobile/features/billing/domain/scheduled_payments.dart';
import 'package:mediflow_mobile/features/billing/presentation/providers.dart';

const _recoveryRequestId = '10000000-0000-4000-8000-000000000041';
const _recoveryReservationId = '10000000-0000-4000-8000-000000000042';
const _recoveryIdempotencyKey = '10000000-0000-4000-8000-000000000043';
const _recoveryCorrelationId = '10000000-0000-4000-8000-000000000044';

String _accessToken(String accountId) =>
    'header.${base64Url.encode(utf8.encode(jsonEncode({'sub': accountId})))}.signature';

const _recovery = ScheduledPaymentRecovery(
  kind: ScheduledPaymentRecoveryKind.payment,
  idempotencyKey: _recoveryIdempotencyKey,
  correlationId: _recoveryCorrelationId,
  paymentCommand: ScheduledPaymentCommand(
    reservationId: _recoveryReservationId,
    expectedReservationRevision: 4,
    quoteId: null,
    amount: '75.00',
    currency: 'VND',
    paymentMethod: 'CASH',
    providerReference: null,
  ),
  zeroCommand: null,
  operationId: null,
);

class TestSession extends SessionController {
  @override
  Future<Session?> build() async => null;

  void replace(Session? session) => state = AsyncData(session);
  void loading() => state = const AsyncLoading();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test(
    'composition removes access during login/logout and changes identity',
    () async {
      final container = ProviderContainer(
        overrides: [
          ...appOverrides,
          sessionProvider.overrideWith(TestSession.new),
        ],
      );
      addTearDown(container.dispose);
      await container.read(sessionProvider.future);
      expect(container.read(workspaceAccessProvider), isNull);
      final controller =
          container.read(sessionProvider.notifier) as TestSession;
      controller.replace(
        Session(
          accessToken: 'session-one',
          refreshToken: 'r',
          role: UserRole.admin,
        ),
      );
      final first = container.read(workspaceAccessProvider)!;
      expect(first.role, 'ADMIN');
      expect(first.sessionIdentity, isNot(isA<String>()));
      expect(
        container.read(workspaceAccessProvider)!.sessionIdentity,
        same(first.sessionIdentity),
      );
      controller.loading();
      expect(container.read(workspaceAccessProvider), isNull);
      controller.replace(
        Session(
          accessToken: 'session-two',
          refreshToken: 'r',
          role: UserRole.nurse,
        ),
      );
      final second = container.read(workspaceAccessProvider)!;
      expect(second.role, 'NURSE');
      expect(second.sessionIdentity, isNot(first.sessionIdentity));
      controller.replace(null);
      expect(container.read(workspaceAccessProvider), isNull);
    },
  );

  test(
    'billing recovery survives same-account refresh and is isolated on account switch',
    () async {
      final storageValues = <String, String>{};
      FlutterSecureStorage.setMockInitialValues(storageValues);
      final container = ProviderContainer(
        overrides: [
          ...appOverrides,
          sessionProvider.overrideWith(TestSession.new),
        ],
      );
      addTearDown(container.dispose);
      await container.read(sessionProvider.future);
      final session = container.read(sessionProvider.notifier) as TestSession;

      session.replace(
        Session(
          accessToken: _accessToken('10000000-0000-4000-8000-000000000045'),
          refreshToken: 'r1',
          role: UserRole.cashier,
        ),
      );
      final firstStore = container.read(scheduledPaymentRecoveryStoreProvider);
      expect(firstStore, isA<SecureScheduledPaymentRecoveryStore>());
      await firstStore!.save(_recoveryRequestId, _recovery);

      // A refreshed token keeps the same authenticated account subject.
      session.replace(
        Session(
          accessToken: _accessToken('10000000-0000-4000-8000-000000000045'),
          refreshToken: 'r2',
          role: UserRole.cashier,
        ),
      );
      final refreshedStore = container.read(scheduledPaymentRecoveryStoreProvider);
      expect(
        (await refreshedStore!.read(_recoveryRequestId))?.idempotencyKey,
        _recoveryIdempotencyKey,
      );

      session.replace(
        Session(
          accessToken: _accessToken('10000000-0000-4000-8000-000000000046'),
          refreshToken: 'other',
          role: UserRole.cashier,
        ),
      );
      final otherStore = container.read(scheduledPaymentRecoveryStoreProvider);
      expect(await otherStore!.read(_recoveryRequestId), isNull);

      session.replace(
        const Session(
          accessToken: 'invalid-token-without-account-subject',
          refreshToken: 'broken',
          role: UserRole.cashier,
        ),
      );
      expect(container.read(scheduledPaymentRecoveryStoreProvider), isNull);
    },
  );
}
