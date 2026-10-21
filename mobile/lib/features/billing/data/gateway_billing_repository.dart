import '../../../core/domain/page_result.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/page_decoder.dart';
import '../domain/billing_commands.dart';
import '../domain/billing_models.dart';
import 'billing_dto.dart';

class GatewayBillingRepository implements BillingRepository {
  const GatewayBillingRepository(this.client);
  final ApiClient client;

  @override
  Future<PageResult<Invoice>> invoicesForPatient(
    String patientId, {
    int page = 0,
    int size = 20,
  }) async {
    _requireUuid(patientId);
    if (page < 0 || size < 1 || size > 100) {
      throw ArgumentError('Trang hóa đơn không hợp lệ.');
    }
    final result = await client.get(
      '/v1/billing/patient/$patientId',
      query: {'page': page, 'size': size},
    );
    final json = BillingDto.object(result);
    if (json['size'] is! int || json['size'] != size) {
      throw const FormatException('Kích thước trang hóa đơn không khớp.');
    }
    return decodePage(json, BillingDto.invoice);
  }

  @override
  Future<Invoice> invoice(String invoiceId) async {
    _requireUuid(invoiceId);
    final result = BillingDto.invoice(
      await client.get('/v1/billing/invoices/$invoiceId'),
    );
    if (result.id.toLowerCase() != invoiceId.toLowerCase()) {
      throw const FormatException('Mã hóa đơn trả về không khớp.');
    }
    return result;
  }

  @override
  Future<Invoice> createInvoice(String patientId, String createdDate) async {
    _requireUuid(patientId);
    if (!isValidDate(createdDate)) {
      throw ArgumentError('Ngày lập hóa đơn không hợp lệ.');
    }
    return BillingDto.invoice(
      await client.post('/v1/billing/invoices', {
        'patientId': patientId,
        'createdDate': createdDate,
      }),
    );
  }

  @override
  Future<PaymentResult> payInvoice(
    String invoiceId,
    String paymentMethod,
  ) async {
    _requireUuid(invoiceId);
    if (!const {'CASH', 'TRANSFER', 'INSURANCE'}.contains(paymentMethod)) {
      throw ArgumentError('Hình thức thanh toán hóa đơn không hợp lệ.');
    }
    final result = BillingDto.paymentResult(
      await client.put('/v1/billing/invoices/$invoiceId/pay', {
        'paymentMethod': paymentMethod,
      }),
    );
    if (result.invoiceId.toLowerCase() != invoiceId.toLowerCase()) {
      throw const FormatException('Mã hóa đơn thanh toán trả về không khớp.');
    }
    return result;
  }

  @override
  Future<PaymentDiscovery> discoverPaymentRequest({
    required String purpose,
    required String sourceId,
    required String patientId,
    required String careEpisodeId,
  }) async {
    if (!const {'EXAM', 'LAB_TEST', 'PRESCRIPTION'}.contains(purpose)) {
      throw ArgumentError('Loại khoản thu không hợp lệ.');
    }
    _requireUuid(sourceId);
    _requireUuid(patientId);
    _requireUuid(careEpisodeId);
    final result = BillingDto.paymentDiscovery(
      await client.get(
        '/v1/billing/payment-requests/by-source',
        query: {
          'purpose': purpose,
          'sourceId': sourceId,
          'patientId': patientId,
          'careEpisodeId': careEpisodeId,
        },
      ),
    );
    if (result.purpose != purpose ||
        result.sourceId.toLowerCase() != sourceId.toLowerCase() ||
        result.patientId.toLowerCase() != patientId.toLowerCase() ||
        result.careEpisodeId.toLowerCase() != careEpisodeId.toLowerCase()) {
      throw const FormatException(
        'Khoản thu trả về không khớp thông tin tra cứu.',
      );
    }
    return result;
  }

  @override
  Future<LedgerPayment> completePayment(
    String paymentRequestId,
    CompletePaymentCommand command,
  ) async {
    _requireUuid(paymentRequestId);
    final result = BillingDto.ledgerPayment(
      await client.post(
        '/v1/billing/payment-requests/$paymentRequestId/payments',
        paymentJson(command),
      ),
    );
    if (result.paymentRequestId.toLowerCase() !=
        paymentRequestId.toLowerCase()) {
      throw const FormatException(
        'Giao dịch trả về không khớp yêu cầu thanh toán.',
      );
    }
    return result;
  }

  @override
  Future<PrescriptionAdjustment> refundsDue(String prescriptionId) async {
    _requireUuid(prescriptionId);
    final result = BillingDto.adjustment(
      await client.get(
        '/v1/billing/prescription-adjustments/$prescriptionId/refunds-due',
      ),
    );
    if (result.prescriptionId.toLowerCase() != prescriptionId.toLowerCase()) {
      throw const FormatException('Điều chỉnh trả về không khớp đơn thuốc.');
    }
    return result;
  }

  @override
  Future<LedgerRefund> recordRefund(
    String originalTransactionId,
    RecordRefundCommand command,
  ) async {
    _requireUuid(originalTransactionId);
    final result = BillingDto.ledgerRefund(
      await client.post(
        '/v1/billing/transactions/$originalTransactionId/refunds',
        refundJson(command),
      ),
    );
    if (result.originalTransactionId.toLowerCase() !=
        originalTransactionId.toLowerCase()) {
      throw const FormatException(
        'Biên nhận hoàn tiền không khớp giao dịch gốc.',
      );
    }
    return result;
  }

  @override
  Future<List<RevenueRow>> revenue({
    required String fromDate,
    required String toDate,
    String? departmentId,
  }) async {
    if (!isValidDate(fromDate) ||
        !isValidDate(toDate) ||
        fromDate.compareTo(toDate) > 0) {
      throw ArgumentError('Khoảng ngày báo cáo không hợp lệ.');
    }
    if (departmentId != null) _requireUuid(departmentId);
    final result = await client.get(
      '/v1/billing/revenue',
      query: {
        'fromDate': fromDate,
        'toDate': toDate,
        'departmentId': ?departmentId,
      },
    );
    if (result is! List) {
      throw const FormatException('Dữ liệu báo cáo không hợp lệ.');
    }
    return List.unmodifiable(result.map(BillingDto.revenueRow));
  }

  @override
  Future<OutboxReplayResult> replayOutboxEvent(String eventId) async {
    _requireUuid(eventId);
    final result = BillingDto.replayResult(
      await client.post('/v1/billing/admin/outbox/$eventId/replay', const {}),
    );
    if (result.eventId.toLowerCase() != eventId.toLowerCase()) {
      throw const FormatException('Kết quả phát lại không khớp sự kiện.');
    }
    return result;
  }

  static Map<String, Object?> paymentJson(CompletePaymentCommand command) => {
    'idempotencyKey': command.idempotencyKey,
    'amount': command.amountMinorUnits / 100,
    'currency': command.currency,
    'paymentMethod': command.paymentMethod,
    'providerReference': command.providerReference,
  };

  static Map<String, Object?> refundJson(RecordRefundCommand command) => {
    'idempotencyKey': command.idempotencyKey,
    'amount': command.amountMinorUnits / 100,
    'reason': command.reason,
    'paymentMethod': command.paymentMethod,
  };

  static void _requireUuid(String value) {
    if (!isBillingUuid(value)) throw ArgumentError('Mã phải là UUID hợp lệ.');
  }
}
