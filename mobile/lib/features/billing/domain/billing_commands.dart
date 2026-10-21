class CompletePaymentCommand {
  const CompletePaymentCommand({
    required this.idempotencyKey,
    required this.amountMinorUnits,
    required this.currency,
    required this.paymentMethod,
    required this.providerReference,
  });
  final String idempotencyKey, currency, paymentMethod;
  final int amountMinorUnits;
  final String? providerReference;
}

class RecordRefundCommand {
  const RecordRefundCommand({
    required this.idempotencyKey,
    required this.amountMinorUnits,
    required this.reason,
    required this.paymentMethod,
  });
  final String idempotencyKey, reason, paymentMethod;
  final int amountMinorUnits;
}
