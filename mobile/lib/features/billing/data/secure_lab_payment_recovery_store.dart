import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../domain/billing_models.dart';
import '../domain/lab_quote_payment.dart';

class SecureLabPaymentRecoveryStore implements LabPaymentRecoveryStore {
  SecureLabPaymentRecoveryStore(this.storage, {required String accountId})
    : accountId = accountId.toLowerCase() {
    if (!isBillingUuid(this.accountId)) {
      throw ArgumentError.value(
        accountId,
        'accountId',
        'Expected an account UUID.',
      );
    }
  }

  final FlutterSecureStorage storage;
  final String accountId;

  String _key(String paymentRequestId) {
    if (!isBillingUuid(paymentRequestId)) {
      throw ArgumentError.value(paymentRequestId, 'paymentRequestId');
    }
    return 'mediflow.billing.lab-payment.v1.$accountId.$paymentRequestId';
  }

  @override
  Future<LabPaymentRecovery?> read(String paymentRequestId) async {
    final value = await storage.read(key: _key(paymentRequestId));
    if (value == null) return null;
    final json = jsonDecode(value);
    if (json is! Map<String, dynamic>) {
      throw const FormatException('Yêu cầu thu đã lưu không hợp lệ.');
    }
    final payment = json['payment'];
    if (payment != null && payment is! Map<String, dynamic>) {
      throw const FormatException('Yêu cầu thu đã lưu không hợp lệ.');
    }
    final quoteId = _uuid(json, 'quoteId');
    return LabPaymentRecovery(
      idempotencyKey: _uuid(json, 'idempotencyKey'),
      correlationId: _uuid(json, 'correlationId'),
      quoteId: quoteId,
      payment: payment is Map<String, dynamic>
          ? LabPaymentCommand(
              quoteId: quoteId,
              amount: _string(payment, 'amount'),
              currency: _string(payment, 'currency'),
              paymentMethod: _string(payment, 'paymentMethod'),
            )
          : null,
    );
  }

  @override
  Future<void> save(String paymentRequestId, LabPaymentRecovery recovery) =>
      storage.write(
        key: _key(paymentRequestId),
        value: jsonEncode({
          'idempotencyKey': recovery.idempotencyKey,
          'correlationId': recovery.correlationId,
          'quoteId': recovery.quoteId,
          'payment': recovery.payment == null
              ? null
              : {
                  'amount': recovery.payment!.amount,
                  'currency': recovery.payment!.currency,
                  'paymentMethod': recovery.payment!.paymentMethod,
                },
        }),
      );

  @override
  Future<void> clear(String paymentRequestId) =>
      storage.delete(key: _key(paymentRequestId));

  static String _string(Map<String, dynamic> json, String key) {
    final value = json[key];
    if (value is! String || value.isEmpty) {
      throw const FormatException('Yêu cầu thu đã lưu không hợp lệ.');
    }
    return value;
  }

  static String _uuid(Map<String, dynamic> json, String key) {
    final value = _string(json, key);
    if (!isBillingUuid(value)) {
      throw const FormatException('Yêu cầu thu đã lưu không hợp lệ.');
    }
    return value;
  }
}
