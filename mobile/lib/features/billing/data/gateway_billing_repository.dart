import '../../../core/domain/page_result.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/page_decoder.dart';
import '../domain/billing_commands.dart';
import '../domain/billing_models.dart';
import 'billing_dto.dart';
import '../domain/payment_queue.dart';
import '../domain/lab_quote_payment.dart';
import '../domain/scheduled_payments.dart';

class GatewayBillingRepository
    implements
        BillingRepository,
        PaymentQueueRepository,
        OutpatientRefundRepository,
        ScheduledPaymentRepository,
        LabQuotePaymentRepository {
  const GatewayBillingRepository(this.client);
  final ApiClient client;

  @override
  Future<OutpatientRefundDue> labRefundsDue(String sourceId) async {
    _requireUuid(sourceId);
    final json = BillingDto.object(
      await client.get('/v1/billing/lab-adjustments/$sourceId/refunds-due'),
    );
    if (BillingDto.uuid(json, 'sourceId') != sourceId ||
        json['sourceType'] != 'LAB_TEST' ||
        json['refundsDue'] is! List) {
      throw const FormatException('Nghĩa vụ hoàn tiền không khớp xét nghiệm.');
    }
    return OutpatientRefundDue(
      sourceId: sourceId,
      sourceType: 'LAB_TEST',
      status: json['status'] as String,
      rejectionCode: json['rejectionCode'] as String?,
      refundsDue: (json['refundsDue'] as List)
          .map((value) {
            final row = BillingDto.object(value);
            return RefundDue(
              originalTransactionId: BillingDto.uuid(
                row,
                'originalTransactionId',
              ),
              amount: BillingDto.money(row, 'amount'),
              currency: BillingDto.currency(row, 'currency'),
            );
          })
          .toList(growable: false),
    );
  }

  @override
  Future<PageResult<PaymentQueueItem>> paymentQueue({
    String? patientId,
    String? purpose,
    int page = 0,
  }) async {
    if (patientId != null) _requireUuid(patientId);
    if (page < 0 ||
        purpose != null &&
            !const ['LAB_TEST', 'PRESCRIPTION'].contains(purpose)) {
      throw ArgumentError('Bộ lọc khoản thu không hợp lệ.');
    }
    return decodePage(
      await client.get(
        '/v1/billing/payment-requests/queue',
        query: {
          'page': page,
          'size': 20,
          'patientId': ?patientId,
          'purpose': ?purpose,
        },
      ),
      (row) {
        final request = BillingDto.paymentDiscovery(row['request']);
        if (!const ['LAB_TEST', 'PRESCRIPTION'].contains(request.purpose) ||
            purpose != null && request.purpose != purpose ||
            patientId != null &&
                request.patientId.toLowerCase() != patientId.toLowerCase()) {
          throw const FormatException('Khoản thu không khớp bộ lọc hàng chờ.');
        }
        return PaymentQueueItem(
          request: request,
          createdAt: BillingDto.instant(row, 'createdAt'),
        );
      },
    );
  }

  @override
  Future<PageResult<ScheduledPaymentQueueItem>> scheduledPaymentQueue({
    String? patientId,
    int page = 0,
  }) async {
    if (patientId != null) _requireUuid(patientId);
    if (page < 0) throw ArgumentError('Trang hàng chờ phí khám không hợp lệ.');
    return decodePage(
      await client.get(
        '/v2/billing/payment-requests',
        query: {'page': page, 'size': 10, 'patientId': ?patientId},
      ),
      (row) {
        final item = BillingDto.scheduledPaymentQueueItem(row);
        if (patientId != null &&
            item.patientId.toLowerCase() != patientId.toLowerCase()) {
          throw const FormatException('Đăng ký không khớp bộ lọc bệnh nhân.');
        }
        return item;
      },
    );
  }

  @override
  Future<RegistrationPaymentRequest> scheduledPaymentRequestByRegistration(
    String registrationId,
  ) async {
    _requireUuid(registrationId);
    final result = BillingDto.scheduledPaymentRequest(
      await client.get(
        '/v2/billing/payment-requests/by-registration',
        query: {'registrationId': registrationId},
      ),
    );
    if (result.registrationId.toLowerCase() != registrationId.toLowerCase()) {
      throw const FormatException('Khoản phí trả về không khớp mã đăng ký.');
    }
    return result;
  }

  @override
  Future<OutpatientQuote> outpatientQuote(String paymentRequestId) async {
    _requireUuid(paymentRequestId);
    final result = BillingDto.outpatientQuote(
      await client.get('/v2/billing/payment-requests/$paymentRequestId/quote'),
    );
    if (result.paymentRequestId.toLowerCase() !=
        paymentRequestId.toLowerCase()) {
      throw const FormatException('Báo giá không khớp khoản thanh toán.');
    }
    return result;
  }

  @override
  Future<ScheduledPaymentResult> payScheduledRequest(
    String paymentRequestId,
    ScheduledPaymentCommand command, {
    required String idempotencyKey,
    required String correlationId,
  }) async {
    _requireScheduledPaymentCommand(paymentRequestId, command);
    final result = BillingDto.scheduledPaymentResult(
      await client.post(
        '/v2/billing/payment-requests/$paymentRequestId/payments',
        _scheduledPaymentJson(command),
        idempotencyKey: idempotencyKey,
        correlationId: correlationId,
      ),
    );
    if (result.operation.paymentRequestId.toLowerCase() !=
            paymentRequestId.toLowerCase() ||
        result.operation.basis != 'PATIENT_PAYMENT' ||
        result.payment != null &&
            result.payment!.paymentRequestId.toLowerCase() !=
                paymentRequestId.toLowerCase()) {
      throw const FormatException('Kết quả thu phí không khớp yêu cầu.');
    }
    return result;
  }

  @override
  Future<LedgerPayment> payLabQuote(
    String paymentRequestId,
    LabPaymentCommand command, {
    required String idempotencyKey,
    required String correlationId,
  }) async {
    final result = BillingDto.ledgerPayment(
      await client.post(
        '/v2/billing/payment-requests/$paymentRequestId/lab-payments',
        {
          'quoteId': command.quoteId,
          'amount': command.amount,
          'currency': command.currency,
          'paymentMethod': command.paymentMethod,
        },
        idempotencyKey: idempotencyKey,
        correlationId: correlationId,
      ),
    );
    if (result.paymentRequestId.toLowerCase() !=
        paymentRequestId.toLowerCase()) {
      throw const FormatException('Kết quả thu không khớp yêu cầu.');
    }
    return result;
  }

  @override
  Future<LabZeroLiabilityReceipt> confirmLabZeroLiability(
    String paymentRequestId,
    String quoteId, {
    required String idempotencyKey,
    required String correlationId,
  }) async {
    final result = BillingDto.labZeroLiabilityReceipt(
      await client.post(
        '/v2/billing/payment-requests/$paymentRequestId/lab-zero-payable-eligibility',
        {'quoteId': quoteId},
        idempotencyKey: idempotencyKey,
        correlationId: correlationId,
      ),
    );
    if (result.paymentRequestId.toLowerCase() !=
            paymentRequestId.toLowerCase() ||
        result.quoteId.toLowerCase() != quoteId.toLowerCase()) {
      throw const FormatException('Kết quả xác nhận không khớp yêu cầu.');
    }
    return result;
  }

  @override
  Future<ScheduledPaymentResult> confirmScheduledZeroLiability(
    String paymentRequestId,
    ScheduledZeroLiabilityCommand command, {
    required String idempotencyKey,
    required String correlationId,
  }) async {
    _requireUuid(paymentRequestId);
    _requireUuid(command.reservationId);
    _requireUuid(command.quoteId);
    if (command.expectedReservationRevision < 1) {
      throw ArgumentError('Phiên bản giữ chỗ không hợp lệ.');
    }
    final result = BillingDto.scheduledPaymentResult(
      await client.post(
        '/v2/billing/payment-requests/$paymentRequestId/zero-payable-eligibility',
        {
          'reservationId': command.reservationId,
          'expectedReservationRevision': command.expectedReservationRevision,
          'quoteId': command.quoteId,
        },
        idempotencyKey: idempotencyKey,
        correlationId: correlationId,
      ),
    );
    if (result.operation.paymentRequestId.toLowerCase() !=
            paymentRequestId.toLowerCase() ||
        result.operation.basis != 'ZERO_LIABILITY') {
      throw const FormatException(
        'Kết quả xác nhận nghĩa vụ không khớp yêu cầu.',
      );
    }
    return result;
  }

  @override
  Future<ScheduledPaymentResult> scheduledPaymentOperation(
    String operationId,
  ) async {
    _requireUuid(operationId);
    final result = BillingDto.scheduledPaymentResult(
      await client.get('/v2/billing/payment-operations/$operationId'),
    );
    if (result.operation.paymentOperationId.toLowerCase() !=
        operationId.toLowerCase()) {
      throw const FormatException('Giao dịch trả về không khớp mã thao tác.');
    }
    return result;
  }

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

  static Map<String, Object?> _scheduledPaymentJson(
    ScheduledPaymentCommand command,
  ) => {
    'reservationId': command.reservationId,
    'expectedReservationRevision': command.expectedReservationRevision,
    'quoteId': command.quoteId,
    'amount': command.amount,
    'currency': command.currency,
    'paymentMethod': command.paymentMethod,
    'providerReference': command.providerReference,
  };

  static void _requireScheduledPaymentCommand(
    String paymentRequestId,
    ScheduledPaymentCommand command,
  ) {
    _requireUuid(paymentRequestId);
    _requireUuid(command.reservationId);
    if (command.quoteId != null) _requireUuid(command.quoteId!);
    if (command.expectedReservationRevision < 1 ||
        !RegExp(r'^(0|[1-9]\d{0,16})(?:\.\d{1,2})?$')
            .hasMatch(command.amount) ||
        command.amount == '0' ||
        command.amount == '0.0' ||
        command.amount == '0.00' ||
        command.currency != 'VND' ||
        !const {'CASH', 'BANK_TRANSFER'}.contains(command.paymentMethod) ||
        (command.providerReference?.length ?? 0) > 120 ||
        command.paymentMethod == 'BANK_TRANSFER' &&
            (command.providerReference == null ||
                command.providerReference!.trim().isEmpty)) {
      throw ArgumentError('Lệnh thu phí khám không hợp lệ.');
    }
  }

  static void _requireUuid(String value) {
    if (!isBillingUuid(value)) throw ArgumentError('Mã phải là UUID hợp lệ.');
  }
}
