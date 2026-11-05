import '../../../core/domain/page_result.dart';
import 'billing_models.dart';

class ScheduledPaymentQueueItem {
  const ScheduledPaymentQueueItem({
    required this.registrationId,
    required this.patientId,
    required this.departmentId,
    required this.createdAt,
    required this.expiresAt,
  });

  final String registrationId, patientId, departmentId, createdAt, expiresAt;
}

class RegistrationPaymentRequest {
  const RegistrationPaymentRequest({
    required this.paymentRequestId,
    required this.invoiceId,
    required this.accountId,
    required this.registrationId,
    required this.reservationId,
    required this.reservationRevision,
    required this.patientId,
    required this.departmentId,
    required this.sourceType,
    required this.sourceId,
    required this.careEpisodeType,
    required this.careEpisodeId,
    required this.requestedAmount,
    required this.paidAmount,
    required this.remainingAmount,
    required this.currency,
    required this.status,
    required this.expiresAt,
    required this.observedAt,
  });

  final String paymentRequestId,
      invoiceId,
      accountId,
      registrationId,
      reservationId,
      patientId,
      departmentId,
      sourceType,
      sourceId,
      careEpisodeType,
      careEpisodeId,
      currency,
      status,
      observedAt;
  final int reservationRevision;
  final Money requestedAmount, paidAmount, remainingAmount;
  final String? expiresAt;
}

class OutpatientQuoteLine {
  const OutpatientQuoteLine({
    required this.sourceId,
    required this.sourceType,
    required this.priceCode,
    required this.catalogueVersion,
    required this.policyVersion,
    required this.description,
    required this.quantity,
    required this.unitPrice,
    required this.grossAmount,
    required this.supportRate,
    required this.supportAmount,
    required this.patientPayable,
  });

  final String sourceId,
      sourceType,
      priceCode,
      catalogueVersion,
      policyVersion,
      description,
      quantity,
      supportRate;
  final Money unitPrice, grossAmount, supportAmount, patientPayable;
}

class OutpatientQuote {
  const OutpatientQuote({
    required this.quoteId,
    required this.paymentRequestId,
    required this.patientId,
    required this.careEpisodeType,
    required this.careEpisodeId,
    required this.currency,
    required this.lines,
    required this.grossAmount,
    required this.supportAmount,
    required this.patientPayable,
    required this.issuedAt,
    required this.expiresAt,
  });

  final String quoteId,
      paymentRequestId,
      patientId,
      careEpisodeType,
      careEpisodeId,
      currency,
      issuedAt,
      expiresAt;
  final List<OutpatientQuoteLine> lines;
  final Money grossAmount, supportAmount, patientPayable;
}

class ScheduledPaymentCommand {
  const ScheduledPaymentCommand({
    required this.reservationId,
    required this.expectedReservationRevision,
    required this.quoteId,
    required this.amount,
    required this.currency,
    required this.paymentMethod,
    required this.providerReference,
  });

  final String reservationId, amount, currency, paymentMethod;
  final int expectedReservationRevision;
  final String? quoteId, providerReference;
}

class ScheduledZeroLiabilityCommand {
  const ScheduledZeroLiabilityCommand({
    required this.reservationId,
    required this.expectedReservationRevision,
    required this.quoteId,
  });

  final String reservationId, quoteId;
  final int expectedReservationRevision;
}

class ScheduledPaymentOperation {
  const ScheduledPaymentOperation({
    required this.paymentOperationId,
    required this.paymentRequestId,
    required this.registrationId,
    required this.reservationId,
    required this.status,
    required this.transactionId,
    required this.amount,
    required this.currency,
    required this.createdAt,
    required this.updatedAt,
    required this.basis,
    required this.quoteId,
    required this.completedAt,
  });

  final String paymentOperationId,
      paymentRequestId,
      registrationId,
      reservationId,
      status,
      amount,
      currency,
      createdAt,
      updatedAt,
      basis;
  final String? transactionId, quoteId, completedAt;
}

class ScheduledLedgerPayment {
  const ScheduledLedgerPayment({
    required this.transactionId,
    required this.accountId,
    required this.paymentRequestId,
    required this.classification,
    required this.status,
    required this.amount,
    required this.currency,
    required this.paymentMethod,
    required this.completedAt,
  });

  final String transactionId,
      accountId,
      paymentRequestId,
      classification,
      status,
      amount,
      currency,
      paymentMethod,
      completedAt;
}

class ScheduledPaymentResult {
  const ScheduledPaymentResult({
    required this.operation,
    required this.payment,
    required this.clearanceId,
  });

  final ScheduledPaymentOperation operation;
  final ScheduledLedgerPayment? payment;
  final String? clearanceId;
}

abstract interface class ScheduledPaymentRepository {
  Future<PageResult<ScheduledPaymentQueueItem>> scheduledPaymentQueue({
    String? patientId,
    int page = 0,
  });

  Future<RegistrationPaymentRequest> scheduledPaymentRequestByRegistration(
    String registrationId,
  );

  Future<OutpatientQuote> outpatientQuote(String paymentRequestId);

  Future<ScheduledPaymentResult> payScheduledRequest(
    String paymentRequestId,
    ScheduledPaymentCommand command, {
    required String idempotencyKey,
    required String correlationId,
  });

  Future<ScheduledPaymentResult> confirmScheduledZeroLiability(
    String paymentRequestId,
    ScheduledZeroLiabilityCommand command, {
    required String idempotencyKey,
    required String correlationId,
  });

  Future<ScheduledPaymentResult> scheduledPaymentOperation(String operationId);
}

enum ScheduledPaymentRecoveryKind { payment, zeroLiability }

class ScheduledPaymentRecovery {
  const ScheduledPaymentRecovery({
    required this.kind,
    required this.idempotencyKey,
    required this.correlationId,
    required this.paymentCommand,
    required this.zeroCommand,
    required this.operationId,
  });

  final ScheduledPaymentRecoveryKind kind;
  final String idempotencyKey, correlationId;
  final ScheduledPaymentCommand? paymentCommand;
  final ScheduledZeroLiabilityCommand? zeroCommand;
  final String? operationId;

  ScheduledPaymentRecovery withOperation(String id) => ScheduledPaymentRecovery(
    kind: kind,
    idempotencyKey: idempotencyKey,
    correlationId: correlationId,
    paymentCommand: paymentCommand,
    zeroCommand: zeroCommand,
    operationId: id,
  );
}

abstract interface class ScheduledPaymentRecoveryStore {
  Future<ScheduledPaymentRecovery?> read(String paymentRequestId);
  Future<void> save(String paymentRequestId, ScheduledPaymentRecovery recovery);
  Future<void> clear(String paymentRequestId);
}
