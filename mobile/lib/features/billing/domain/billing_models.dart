import '../../../core/domain/page_result.dart';
import 'billing_commands.dart';

final _uuidPattern = RegExp(
  r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$',
);

bool isBillingUuid(String value) => _uuidPattern.hasMatch(value);

class Money {
  const Money(this.minorUnits);
  final int minorUnits;
  String format() {
    final whole = minorUnits ~/ 100;
    final cents = (minorUnits % 100).toString().padLeft(2, '0');
    return '$whole.$cents';
  }
}

class Fee {
  const Fee({
    required this.id,
    required this.type,
    required this.departmentId,
    required this.incurredDate,
    required this.amount,
    required this.isPaid,
  });
  final String id, type, departmentId, incurredDate;
  final Money amount;
  final bool isPaid;
}

class Invoice {
  const Invoice({
    required this.id,
    required this.patientId,
    required this.createdDate,
    required this.totalAmount,
    required this.isPaid,
    required this.paymentMethod,
    required this.prescriptionId,
    required this.sagaStatus,
    required this.paidAt,
    required this.fees,
  });
  final String id, patientId, createdDate, sagaStatus;
  final Money totalAmount;
  final bool isPaid;
  final String? paymentMethod, prescriptionId, paidAt;
  final List<Fee> fees;
}

class PaymentResult {
  const PaymentResult({
    required this.invoiceId,
    required this.success,
    required this.totalAmount,
    required this.paymentMethod,
    required this.paidAt,
    required this.sagaStatus,
  });
  final String invoiceId, paymentMethod, paidAt, sagaStatus;
  final bool success;
  final Money totalAmount;
}

class PaymentDiscovery {
  const PaymentDiscovery({
    required this.paymentRequestId,
    required this.invoiceId,
    required this.accountId,
    required this.patientId,
    required this.departmentId,
    required this.purpose,
    required this.sourceType,
    required this.sourceId,
    required this.careEpisodeId,
    required this.careEpisodeType,
    required this.appointmentId,
    required this.recordId,
    required this.prescriptionId,
    required this.labTestIds,
    required this.requestedAmount,
    required this.paidAmount,
    required this.remainingAmount,
    required this.currency,
    required this.status,
    required this.accountStatus,
    required this.expiresAt,
    required this.observedAt,
  });
  final String paymentRequestId,
      invoiceId,
      accountId,
      patientId,
      departmentId,
      purpose,
      sourceType,
      sourceId,
      careEpisodeId,
      careEpisodeType,
      currency,
      status,
      accountStatus,
      observedAt;
  final String? appointmentId, recordId, prescriptionId, expiresAt;
  final List<String> labTestIds;
  final Money requestedAmount, paidAmount, remainingAmount;
}

class LedgerPayment {
  const LedgerPayment({
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
      status;
  final Money amount;
  final String currency, paymentMethod;
  final String? completedAt;
}

class RefundDue {
  const RefundDue({
    required this.originalTransactionId,
    required this.amount,
    required this.currency,
  });
  final String originalTransactionId, currency;
  final Money amount;
}

class PrescriptionAdjustment {
  const PrescriptionAdjustment({
    required this.prescriptionId,
    required this.eventType,
    required this.status,
    required this.rejectionCode,
    required this.refundsDue,
  });
  final String prescriptionId, eventType, status;
  final String? rejectionCode;
  final List<RefundDue> refundsDue;
}

class LedgerRefund {
  const LedgerRefund({
    required this.refundTransactionId,
    required this.originalTransactionId,
    required this.accountId,
    required this.paymentRequestId,
    required this.classification,
    required this.status,
    required this.amount,
    required this.currency,
    required this.paymentMethod,
    required this.completedAt,
  });
  final String refundTransactionId,
      originalTransactionId,
      accountId,
      paymentRequestId,
      classification,
      status,
      currency,
      paymentMethod;
  final Money amount;
  final String? completedAt;
}

class RevenueRow {
  const RevenueRow({
    required this.departmentId,
    required this.totalRevenue,
    required this.invoiceCount,
  });
  final String departmentId;
  final Money totalRevenue;
  final int invoiceCount;
}

class OutboxReplayResult {
  const OutboxReplayResult({required this.eventId, required this.replayed});
  final String eventId;
  final bool replayed;
}

abstract interface class BillingRepository {
  Future<PageResult<Invoice>> invoicesForPatient(
    String patientId, {
    int page = 0,
    int size = 20,
  });
  Future<Invoice> invoice(String invoiceId);
  Future<Invoice> createInvoice(String patientId, String createdDate);
  Future<PaymentResult> payInvoice(String invoiceId, String paymentMethod);
  Future<PaymentDiscovery> discoverPaymentRequest({
    required String purpose,
    required String sourceId,
    required String patientId,
    required String careEpisodeId,
  });
  Future<LedgerPayment> completePayment(
    String paymentRequestId,
    CompletePaymentCommand command,
  );
  Future<PrescriptionAdjustment> refundsDue(String prescriptionId);
  Future<LedgerRefund> recordRefund(
    String originalTransactionId,
    RecordRefundCommand command,
  );
  Future<List<RevenueRow>> revenue({
    required String fromDate,
    required String toDate,
    String? departmentId,
  });
  Future<OutboxReplayResult> replayOutboxEvent(String eventId);
}

class PaymentRequestSource {
  const PaymentRequestSource({
    required this.purpose,
    required this.sourceId,
    required this.patientId,
    required this.careEpisodeId,
  });
  final String purpose, sourceId, patientId, careEpisodeId;
}

bool isValidDate(String value) {
  final date = DateTime.tryParse(value);
  return date != null && date.toIso8601String().substring(0, 10) == value;
}
