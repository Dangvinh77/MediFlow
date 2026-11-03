import '../../../core/domain/page_result.dart';
import 'billing_models.dart';

class PaymentQueueItem {
  const PaymentQueueItem({required this.request, required this.createdAt});
  final PaymentDiscovery request;
  final String createdAt;
}

abstract interface class PaymentQueueRepository {
  Future<PageResult<PaymentQueueItem>> paymentQueue({
    String? patientId,
    String? purpose,
    int page = 0,
  });
}

class OutpatientRefundDue {
  const OutpatientRefundDue({
    required this.sourceId,
    required this.sourceType,
    required this.status,
    required this.refundsDue,
    this.rejectionCode,
  });
  final String sourceId, sourceType, status;
  final String? rejectionCode;
  final List<RefundDue> refundsDue;
}

abstract interface class OutpatientRefundRepository {
  Future<OutpatientRefundDue> labRefundsDue(String sourceId);
}
