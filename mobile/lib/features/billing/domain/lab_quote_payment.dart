import 'dart:math';

import 'billing_models.dart';
import 'scheduled_payments.dart';

String newLabCommandKey() {
  final random = Random.secure();
  final bytes = List<int>.generate(16, (_) => random.nextInt(256));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  final hex = bytes
      .map((value) => value.toRadixString(16).padLeft(2, '0'))
      .join();
  return '${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}';
}

class LabPaymentCommand {
  const LabPaymentCommand({
    required this.quoteId,
    required this.amount,
    required this.currency,
    required this.paymentMethod,
  });

  final String quoteId, amount, currency, paymentMethod;
}

class LabZeroLiabilityReceipt {
  const LabZeroLiabilityReceipt({
    required this.eligibilityOperationId,
    required this.paymentRequestId,
    required this.quoteId,
    required this.clearanceId,
  });

  final String eligibilityOperationId, paymentRequestId, quoteId, clearanceId;
}

abstract interface class LabQuotePaymentRepository {
  Future<OutpatientQuote> outpatientQuote(String paymentRequestId);

  Future<LedgerPayment> payLabQuote(
    String paymentRequestId,
    LabPaymentCommand command, {
    required String idempotencyKey,
    required String correlationId,
  });

  Future<LabZeroLiabilityReceipt> confirmLabZeroLiability(
    String paymentRequestId,
    String quoteId, {
    required String idempotencyKey,
    required String correlationId,
  });
}

/// An unresolved command. A resend reuses its key and body exactly, even when
/// the quote has since expired, because only Billing knows the first outcome.
class LabPaymentRecovery {
  const LabPaymentRecovery({
    required this.idempotencyKey,
    required this.correlationId,
    required this.quoteId,
    required this.payment,
  });

  final String idempotencyKey, correlationId, quoteId;

  /// Null for a zero-liability confirmation.
  final LabPaymentCommand? payment;
}

abstract interface class LabPaymentRecoveryStore {
  Future<LabPaymentRecovery?> read(String paymentRequestId);
  Future<void> save(String paymentRequestId, LabPaymentRecovery recovery);
  Future<void> clear(String paymentRequestId);
}
