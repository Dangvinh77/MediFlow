import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:mediflow_mobile/core/domain/page_result.dart';
import 'package:mediflow_mobile/features/billing/domain/billing_commands.dart';
import 'package:mediflow_mobile/features/billing/domain/billing_models.dart';
import 'package:mediflow_mobile/features/notification/domain/notification.dart';

const patientId = '10000000-0000-4000-8000-000000000001';
const invoiceId = '10000000-0000-4000-8000-000000000002';
const feeId = '10000000-0000-4000-8000-000000000003';
const sourceId = '10000000-0000-4000-8000-000000000004';
const episodeId = '10000000-0000-4000-8000-000000000005';
const paymentRequestId = '10000000-0000-4000-8000-000000000006';
const transactionId = '10000000-0000-4000-8000-000000000007';
const prescriptionId = '10000000-0000-4000-8000-000000000008';
const notificationId = '10000000-0000-4000-8000-000000000009';
const eventId = '10000000-0000-4000-8000-000000000010';
const departmentId = '10000000-0000-4000-8000-000000000011';

Map<String, dynamic> invoiceJson({
  String id = invoiceId,
  String patient = patientId,
  Object? amount = 1234.50,
  bool paid = false,
}) => {
  'invoiceId': id,
  'patientId': patient,
  'createdDate': '2026-10-21',
  'totalAmount': amount,
  'isPaid': paid,
  'paymentMethod': paid ? 'CASH' : null,
  'prescriptionId': prescriptionId,
  'sagaStatus': paid ? 'PAID' : 'AWAITING_PAYMENT',
  'paidAt': paid ? '2026-10-21T10:30:00Z' : null,
  'fees': [
    {
      'feeId': feeId,
      'feeType': 'EXAM',
      'departmentId': departmentId,
      'incurredDate': '2026-10-21',
      'amount': 1234.50,
      'isPaid': paid,
    },
  ],
};

Map<String, dynamic> invoicePageJson({List<Map<String, dynamic>>? rows}) => {
  'content': rows ?? [invoiceJson()],
  'totalElements': 1,
  'totalPages': 1,
  'number': 0,
  'size': 20,
};

Map<String, dynamic> paymentDiscoveryJson({
  String source = sourceId,
  String patient = patientId,
  String episode = episodeId,
  Object? remaining = 75.25,
  String status = 'PARTIALLY_PAID',
}) => {
  'paymentRequestId': paymentRequestId,
  'invoiceId': invoiceId,
  'accountId': '10000000-0000-4000-8000-000000000012',
  'patientId': patient,
  'departmentId': departmentId,
  'purpose': 'EXAM',
  'sourceType': 'APPOINTMENT',
  'sourceId': source,
  'careEpisodeId': episode,
  'careEpisodeType': 'OUTPATIENT',
  'appointmentId': null,
  'recordId': null,
  'prescriptionId': null,
  'labTestIds': <String>[],
  'requestedAmount': 100.00,
  'paidAmount': 24.75,
  'remainingAmount': remaining,
  'currency': 'VND',
  'status': status,
  'accountStatus': 'OPEN',
  'expiresAt': null,
  'observedAt': '2026-10-21T10:30:00Z',
};

Map<String, dynamic> ledgerPaymentJson({String status = 'COMPLETED'}) => {
  'transactionId': transactionId,
  'accountId': '10000000-0000-4000-8000-000000000012',
  'paymentRequestId': paymentRequestId,
  'classification': 'SERVICE_PAYMENT',
  'status': status,
  'amount': 25.25,
  'currency': 'VND',
  'paymentMethod': 'CASH',
  'completedAt': status == 'COMPLETED' ? '2026-10-21T10:30:00Z' : null,
};

Map<String, dynamic> refundDueJson() => {
  'prescriptionId': prescriptionId,
  'eventType': 'PRESCRIPTION_ADJUSTED',
  'status': 'APPLIED',
  'rejectionCode': null,
  'refundsDue': [
    {
      'originalTransactionId': transactionId,
      'amount': 60.00,
      'currency': 'VND',
    },
  ],
};

Map<String, dynamic> ledgerRefundJson({String status = 'COMPLETED'}) => {
  'refundTransactionId': '10000000-0000-4000-8000-000000000013',
  'originalTransactionId': transactionId,
  'accountId': '10000000-0000-4000-8000-000000000012',
  'paymentRequestId': paymentRequestId,
  'classification': 'SERVICE_PAYMENT',
  'status': status,
  'amount': 15.00,
  'currency': 'VND',
  'paymentMethod': 'TRANSFER',
  'completedAt': status == 'COMPLETED' ? '2026-10-21T10:30:00Z' : null,
};

Map<String, dynamic> notificationJson({String id = notificationId}) => {
  'notificationId': id,
  'patientId': patientId,
  'title': 'Kết quả khám',
  'content': 'Vui lòng xem lịch hẹn.',
  'channel': 'IN_APP',
  'status': 'SENT',
  'failureReason': null,
  'createdAt': '2026-10-21T10:00:00Z',
  'sentAt': '2026-10-21T10:00:05Z',
  // The backend DTO deliberately does not expose this private request field.
  'recipientAddress': 'private@example.test',
};

Map<String, dynamic> notificationPageJson({
  List<Map<String, dynamic>>? rows,
  int page = 0,
  int size = 20,
}) => {
  'content': rows ?? [notificationJson()],
  'totalElements': rows?.length ?? 1,
  'totalPages': 1,
  'number': page,
  'size': size,
};

class BillingNotificationHttpAdapter implements HttpClientAdapter {
  final requests = <RequestOptions>[];
  final bodies = <Object?>[];
  Object? data;
  int status = 200;
  bool networkFailure = false;
  bool wrapSuccess = true;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    bodies.add(options.data);
    if (networkFailure) {
      throw DioException.connectionError(
        requestOptions: options,
        reason: 'Disconnected',
      );
    }
    final payload = status >= 400 || !wrapSuccess
        ? data
        : {
            'success': true,
            'data': data,
            'error': null,
            'correlationId': 'trace-fixture',
          };
    return ResponseBody.fromString(
      jsonEncode(payload),
      status,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

Invoice invoiceRecord({
  String id = invoiceId,
  String patient = patientId,
  bool paid = false,
}) => Invoice(
  id: id,
  patientId: patient,
  createdDate: '2026-10-21',
  totalAmount: const Money(123450),
  isPaid: paid,
  paymentMethod: paid ? 'CASH' : null,
  prescriptionId: prescriptionId,
  sagaStatus: paid ? 'PAID' : 'AWAITING_PAYMENT',
  paidAt: paid ? '2026-10-21T10:30:00Z' : null,
  fees: const [],
);

PageResult<Invoice> invoicePage({List<Invoice> items = const []}) => PageResult(
  items: items,
  number: 0,
  totalPages: 1,
  totalElements: items.length,
);

PaymentDiscovery paymentDiscovery({int remainingMinor = 7525}) =>
    PaymentDiscovery(
      paymentRequestId: paymentRequestId,
      invoiceId: invoiceId,
      accountId: '10000000-0000-4000-8000-000000000012',
      patientId: patientId,
      departmentId: departmentId,
      purpose: 'EXAM',
      sourceType: 'APPOINTMENT',
      sourceId: sourceId,
      careEpisodeId: episodeId,
      careEpisodeType: 'OUTPATIENT',
      appointmentId: null,
      recordId: null,
      prescriptionId: null,
      labTestIds: const [],
      requestedAmount: const Money(10000),
      paidAmount: const Money(2475),
      remainingAmount: Money(remainingMinor),
      currency: 'VND',
      status: 'PARTIALLY_PAID',
      accountStatus: 'OPEN',
      expiresAt: null,
      observedAt: '2026-10-21T10:30:00Z',
    );

LedgerPayment ledgerPayment({String status = 'COMPLETED'}) => LedgerPayment(
  transactionId: transactionId,
  accountId: '10000000-0000-4000-8000-000000000012',
  paymentRequestId: paymentRequestId,
  classification: 'SERVICE_PAYMENT',
  status: status,
  amount: const Money(2525),
  currency: 'VND',
  paymentMethod: 'CASH',
  completedAt: status == 'COMPLETED' ? '2026-10-21T10:30:00Z' : null,
);

PrescriptionAdjustment refundDue() => const PrescriptionAdjustment(
  prescriptionId: prescriptionId,
  eventType: 'PRESCRIPTION_ADJUSTED',
  status: 'APPLIED',
  rejectionCode: null,
  refundsDue: [
    RefundDue(
      originalTransactionId: transactionId,
      amount: Money(6000),
      currency: 'VND',
    ),
  ],
);

LedgerRefund ledgerRefund() => const LedgerRefund(
  refundTransactionId: '10000000-0000-4000-8000-000000000013',
  originalTransactionId: transactionId,
  accountId: '10000000-0000-4000-8000-000000000012',
  paymentRequestId: paymentRequestId,
  classification: 'SERVICE_PAYMENT',
  status: 'COMPLETED',
  amount: Money(1500),
  currency: 'VND',
  paymentMethod: 'TRANSFER',
  completedAt: '2026-10-21T10:30:00Z',
);

PatientNotification patientNotification() => const PatientNotification(
  id: notificationId,
  patientId: patientId,
  title: 'Kết quả khám',
  content: 'Vui lòng xem lịch hẹn.',
  channel: 'IN_APP',
  status: 'SENT',
  failureReason: null,
  createdAt: '2026-10-21T10:00:00Z',
  sentAt: '2026-10-21T10:00:05Z',
);

class BillingStub implements BillingRepository {
  final invoiceHistoryCalls = <({String patientId, int page})>[];
  final invoiceLookups = <String>[];
  final paymentDiscoveries =
      <
        ({
          String purpose,
          String sourceId,
          String patientId,
          String careEpisodeId,
        })
      >[];
  final completedPayments = <({String id, Map<String, Object?> body})>[];
  final invoicePayments = <({String invoiceId, String paymentMethod})>[];
  final refundLookups = <String>[];
  final refundRequests = <({String originalId, Map<String, Object?> body})>[];
  Future<PageResult<Invoice>> Function(String, int)? onInvoices;
  Future<Invoice> Function(String)? onInvoice;
  Future<Invoice> Function(String, String)? onCreateInvoice;
  Future<PaymentDiscovery> Function(String, String, String, String)?
  onDiscoverPayment;
  Future<LedgerPayment> Function(String, CompletePaymentCommand)?
  onCompletePayment;
  Future<PrescriptionAdjustment> Function(String)? onRefundsDue;
  Future<LedgerRefund> Function(String, RecordRefundCommand)? onRecordRefund;
  Future<PaymentResult> Function(String, String)? onPayInvoice;
  Future<List<RevenueRow>> Function(String, String, String?)? onRevenue;
  Future<OutboxReplayResult> Function(String)? onReplay;

  @override
  Future<PageResult<Invoice>> invoicesForPatient(
    String patientId, {
    int page = 0,
    int size = 20,
  }) {
    invoiceHistoryCalls.add((patientId: patientId, page: page));
    return onInvoices?.call(patientId, page) ?? Future.value(invoicePage());
  }

  @override
  Future<Invoice> invoice(String id) {
    invoiceLookups.add(id);
    return onInvoice?.call(id) ??
        Future.value(invoiceRecord(id: id, paid: true));
  }

  @override
  Future<Invoice> createInvoice(String patientId, String createdDate) =>
      onCreateInvoice?.call(patientId, createdDate) ??
      Future.value(invoiceRecord(patient: patientId));
  @override
  Future<PaymentResult> payInvoice(String invoiceId, String paymentMethod) {
    invoicePayments.add((invoiceId: invoiceId, paymentMethod: paymentMethod));
    return onPayInvoice?.call(invoiceId, paymentMethod) ??
        Future.value(
          PaymentResult(
            invoiceId: invoiceId,
            success: true,
            totalAmount: const Money(123450),
            paymentMethod: paymentMethod,
            paidAt: '2026-10-21T10:30:00Z',
            sagaStatus: 'PAID',
          ),
        );
  }

  @override
  Future<PaymentDiscovery> discoverPaymentRequest({
    required String purpose,
    required String sourceId,
    required String patientId,
    required String careEpisodeId,
  }) {
    paymentDiscoveries.add((
      purpose: purpose,
      sourceId: sourceId,
      patientId: patientId,
      careEpisodeId: careEpisodeId,
    ));
    return onDiscoverPayment?.call(
          purpose,
          sourceId,
          patientId,
          careEpisodeId,
        ) ??
        Future.value(paymentDiscovery());
  }

  @override
  Future<LedgerPayment> completePayment(
    String paymentRequestId,
    CompletePaymentCommand request,
  ) {
    completedPayments.add((
      id: paymentRequestId,
      body: {
        'idempotencyKey': request.idempotencyKey,
        'amountMinorUnits': request.amountMinorUnits,
        'currency': request.currency,
        'paymentMethod': request.paymentMethod,
        'providerReference': request.providerReference,
      },
    ));
    return onCompletePayment?.call(paymentRequestId, request) ??
        Future.value(ledgerPayment());
  }

  @override
  Future<PrescriptionAdjustment> refundsDue(String id) {
    refundLookups.add(id);
    return onRefundsDue?.call(id) ?? Future.value(refundDue());
  }

  @override
  Future<LedgerRefund> recordRefund(
    String originalTransactionId,
    RecordRefundCommand request,
  ) {
    refundRequests.add((
      originalId: originalTransactionId,
      body: {
        'idempotencyKey': request.idempotencyKey,
        'amountMinorUnits': request.amountMinorUnits,
        'reason': request.reason,
        'paymentMethod': request.paymentMethod,
      },
    ));
    return onRecordRefund?.call(originalTransactionId, request) ??
        Future.value(ledgerRefund());
  }

  @override
  Future<List<RevenueRow>> revenue({
    required String fromDate,
    required String toDate,
    String? departmentId,
  }) =>
      onRevenue?.call(fromDate, toDate, departmentId) ?? Future.value(const []);

  @override
  Future<OutboxReplayResult> replayOutboxEvent(String eventId) =>
      onReplay?.call(eventId) ??
      Future.value(OutboxReplayResult(eventId: eventId, replayed: true));
}

class NotificationStub implements NotificationRepository {
  final historyCalls = <({String patientId, int page})>[];
  final detailCalls = <String>[];
  final sends = <SendNotificationCommand>[];
  Future<PageResult<PatientNotification>> Function(String, int)? onHistory;
  Future<PatientNotification> Function(String)? onDetail;
  Future<PatientNotification> Function(SendNotificationCommand)? onSend;

  @override
  Future<PageResult<PatientNotification>> byPatient(
    String patientId, {
    int page = 0,
    int size = 20,
  }) {
    historyCalls.add((patientId: patientId, page: page));
    return onHistory?.call(patientId, page) ??
        Future.value(
          PageResult(
            items: const [],
            number: page,
            totalPages: 1,
            totalElements: 0,
          ),
        );
  }

  @override
  Future<PatientNotification> byId(String notificationId) {
    detailCalls.add(notificationId);
    return onDetail?.call(notificationId) ??
        Future.value(patientNotification());
  }

  @override
  Future<PatientNotification> send(SendNotificationCommand command) {
    sends.add(command);
    return onSend?.call(command) ?? Future.value(patientNotification());
  }
}
