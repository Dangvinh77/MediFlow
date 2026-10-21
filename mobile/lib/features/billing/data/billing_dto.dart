import '../../../core/network/json_reader.dart';
import '../domain/billing_models.dart';

abstract final class BillingDto {
  static Map<String, dynamic> object(Object? value) {
    if (value is! Map<String, dynamic>) {
      throw const FormatException('Phản hồi Billing không hợp lệ.');
    }
    return value;
  }

  static String uuid(Map<String, dynamic> json, String key) {
    final value = JsonReader.string(json, key);
    if (!isBillingUuid(value)) {
      throw FormatException('Dữ liệu $key không hợp lệ.');
    }
    return value;
  }

  static String? optionalUuid(Map<String, dynamic> json, String key) {
    final value = JsonReader.optionalString(json, key);
    if (value != null && !isBillingUuid(value)) {
      throw FormatException('Dữ liệu $key không hợp lệ.');
    }
    return value;
  }

  static bool boolean(Map<String, dynamic> json, String key) {
    final value = json[key];
    if (value is! bool) throw FormatException('Dữ liệu $key không hợp lệ.');
    return value;
  }

  static int integer(Map<String, dynamic> json, String key, {int min = 0}) {
    final value = json[key];
    if (value is! int || value < min) {
      throw FormatException('Dữ liệu $key không hợp lệ.');
    }
    return value;
  }

  static Money money(Map<String, dynamic> json, String key) {
    final value = json[key];
    if (value is! num || !value.isFinite || value < 0) {
      throw FormatException('Dữ liệu $key không hợp lệ.');
    }
    final decimal = value.toString();
    final match = RegExp(r'^(0|[1-9]\d*)(?:\.(\d{1,2}))?$').firstMatch(decimal);
    if (match == null || (value is double && value.abs() > 90071992547409.9)) {
      throw FormatException('Số tiền $key không thể biểu diễn chính xác.');
    }
    final whole = int.tryParse(match.group(1)!);
    if (whole == null || whole > 90071992547409) {
      throw FormatException('Số tiền $key vượt giới hạn an toàn.');
    }
    final fraction = (match.group(2) ?? '').padRight(2, '0');
    final cents = fraction.isEmpty ? 0 : int.parse(fraction);
    return Money(whole * 100 + cents);
  }

  static String date(Map<String, dynamic> json, String key) {
    final value = JsonReader.string(json, key);
    if (!isValidDate(value)) {
      throw FormatException('Dữ liệu $key không hợp lệ.');
    }
    return value;
  }

  static String instant(Map<String, dynamic> json, String key) {
    final value = JsonReader.string(json, key);
    if (DateTime.tryParse(value) == null) {
      throw FormatException('Dữ liệu $key không hợp lệ.');
    }
    return value;
  }

  static String? optionalInstant(Map<String, dynamic> json, String key) {
    final value = JsonReader.optionalString(json, key);
    if (value != null && DateTime.tryParse(value) == null) {
      throw FormatException('Dữ liệu $key không hợp lệ.');
    }
    return value;
  }

  static String oneOf(
    Map<String, dynamic> json,
    String key,
    Set<String> allowed,
  ) {
    final value = JsonReader.string(json, key);
    if (!allowed.contains(value)) {
      throw FormatException('Dữ liệu $key không hợp lệ.');
    }
    return value;
  }

  static Invoice invoice(Object? value) {
    final json = object(value);
    final fees = json['fees'];
    if (fees is! List) {
      throw const FormatException('Dữ liệu fees không hợp lệ.');
    }
    return Invoice(
      id: uuid(json, 'invoiceId'),
      patientId: uuid(json, 'patientId'),
      createdDate: date(json, 'createdDate'),
      totalAmount: money(json, 'totalAmount'),
      isPaid: boolean(json, 'isPaid'),
      paymentMethod: oneOfOrNull(json, 'paymentMethod', const {
        'CASH',
        'TRANSFER',
        'INSURANCE',
      }),
      prescriptionId: optionalUuid(json, 'prescriptionId'),
      sagaStatus: oneOf(json, 'sagaStatus', const {
        'NONE',
        'AWAITING_PAYMENT',
        'PAID',
        'AWAITING_DISPENSE',
        'COMPLETED',
        'REFUNDED',
      }),
      paidAt: optionalInstant(json, 'paidAt'),
      fees: List.unmodifiable(fees.map((item) => fee(object(item)))),
    );
  }

  static Fee fee(Map<String, dynamic> json) => Fee(
    id: uuid(json, 'feeId'),
    type: oneOf(json, 'feeType', const {'EXAM', 'LAB', 'DRUG', 'SERVICE'}),
    departmentId: uuid(json, 'departmentId'),
    incurredDate: date(json, 'incurredDate'),
    amount: money(json, 'amount'),
    isPaid: boolean(json, 'isPaid'),
  );

  static PaymentResult paymentResult(Object? value) {
    final json = object(value);
    return PaymentResult(
      invoiceId: uuid(json, 'invoiceId'),
      success: boolean(json, 'success'),
      totalAmount: money(json, 'totalAmount'),
      paymentMethod: oneOf(json, 'paymentMethod', const {
        'CASH',
        'TRANSFER',
        'INSURANCE',
      }),
      paidAt: instant(json, 'paidAt'),
      sagaStatus: oneOf(json, 'sagaStatus', const {
        'NONE',
        'AWAITING_PAYMENT',
        'PAID',
        'AWAITING_DISPENSE',
        'COMPLETED',
        'REFUNDED',
      }),
    );
  }

  static PaymentDiscovery paymentDiscovery(Object? value) {
    final json = object(value);
    final tests = json['labTestIds'];
    if (tests is! List) {
      throw const FormatException('Dữ liệu labTestIds không hợp lệ.');
    }
    return PaymentDiscovery(
      paymentRequestId: uuid(json, 'paymentRequestId'),
      invoiceId: uuid(json, 'invoiceId'),
      accountId: uuid(json, 'accountId'),
      patientId: uuid(json, 'patientId'),
      departmentId: uuid(json, 'departmentId'),
      purpose: oneOf(json, 'purpose', const {
        'EXAM',
        'LAB_TEST',
        'PRESCRIPTION',
      }),
      sourceType: JsonReader.string(json, 'sourceType'),
      sourceId: uuid(json, 'sourceId'),
      careEpisodeId: uuid(json, 'careEpisodeId'),
      careEpisodeType: JsonReader.string(json, 'careEpisodeType'),
      appointmentId: optionalUuid(json, 'appointmentId'),
      recordId: optionalUuid(json, 'recordId'),
      prescriptionId: optionalUuid(json, 'prescriptionId'),
      labTestIds: List.unmodifiable(
        tests.map((item) {
          if (item is! String || !isBillingUuid(item)) {
            throw const FormatException('Dữ liệu labTestIds không hợp lệ.');
          }
          return item;
        }),
      ),
      requestedAmount: money(json, 'requestedAmount'),
      paidAmount: money(json, 'paidAmount'),
      remainingAmount: money(json, 'remainingAmount'),
      currency: currency(json, 'currency'),
      status: oneOf(json, 'status', const {
        'PENDING',
        'PARTIALLY_PAID',
        'PAID',
        'EXPIRED',
        'CANCELLED',
      }),
      accountStatus: oneOf(json, 'accountStatus', const {
        'OPEN',
        'CHARGE_CLOSED',
        'SETTLEMENT_PENDING',
        'SETTLED',
        'CLOSED',
      }),
      expiresAt: optionalInstant(json, 'expiresAt'),
      observedAt: instant(json, 'observedAt'),
    );
  }

  static LedgerPayment ledgerPayment(Object? value) {
    final json = object(value);
    return LedgerPayment(
      transactionId: uuid(json, 'transactionId'),
      accountId: uuid(json, 'accountId'),
      paymentRequestId: uuid(json, 'paymentRequestId'),
      classification: oneOf(json, 'classification', const {
        'SERVICE_PAYMENT',
        'ADMISSION_DEPOSIT',
        'SETTLEMENT_PAYMENT',
      }),
      status: oneOf(json, 'status', const {'PENDING', 'COMPLETED', 'FAILED'}),
      amount: money(json, 'amount'),
      currency: currency(json, 'currency'),
      paymentMethod: JsonReader.string(json, 'paymentMethod'),
      completedAt: optionalInstant(json, 'completedAt'),
    );
  }

  static PrescriptionAdjustment adjustment(Object? value) {
    final json = object(value);
    final due = json['refundsDue'];
    if (due is! List) {
      throw const FormatException('Dữ liệu refundsDue không hợp lệ.');
    }
    return PrescriptionAdjustment(
      prescriptionId: uuid(json, 'prescriptionId'),
      eventType: JsonReader.string(json, 'eventType'),
      status: JsonReader.string(json, 'status'),
      rejectionCode: JsonReader.optionalString(json, 'rejectionCode'),
      refundsDue: List.unmodifiable(
        due.map((item) {
          final row = object(item);
          return RefundDue(
            originalTransactionId: uuid(row, 'originalTransactionId'),
            amount: money(row, 'amount'),
            currency: currency(row, 'currency'),
          );
        }),
      ),
    );
  }

  static LedgerRefund ledgerRefund(Object? value) {
    final json = object(value);
    return LedgerRefund(
      refundTransactionId: uuid(json, 'refundTransactionId'),
      originalTransactionId: uuid(json, 'originalTransactionId'),
      accountId: uuid(json, 'accountId'),
      paymentRequestId: uuid(json, 'paymentRequestId'),
      classification: oneOf(json, 'classification', const {
        'SERVICE_PAYMENT',
        'ADMISSION_DEPOSIT',
        'SETTLEMENT_PAYMENT',
      }),
      status: oneOf(json, 'status', const {'PENDING', 'COMPLETED', 'FAILED'}),
      amount: money(json, 'amount'),
      currency: currency(json, 'currency'),
      paymentMethod: JsonReader.string(json, 'paymentMethod'),
      completedAt: optionalInstant(json, 'completedAt'),
    );
  }

  static RevenueRow revenueRow(Object? value) {
    final json = object(value);
    return RevenueRow(
      departmentId: uuid(json, 'departmentId'),
      totalRevenue: money(json, 'totalRevenue'),
      invoiceCount: integer(json, 'invoiceCount'),
    );
  }

  static OutboxReplayResult replayResult(Object? value) {
    final json = object(value);
    return OutboxReplayResult(
      eventId: uuid(json, 'eventId'),
      replayed: boolean(json, 'replayed'),
    );
  }

  static String currency(Map<String, dynamic> json, String key) {
    final value = JsonReader.string(json, key);
    if (!RegExp(r'^[A-Z]{3}$').hasMatch(value)) {
      throw FormatException('Dữ liệu $key không hợp lệ.');
    }
    return value;
  }

  static String? oneOfOrNull(
    Map<String, dynamic> json,
    String key,
    Set<String> allowed,
  ) {
    final value = JsonReader.optionalString(json, key);
    if (value != null && !allowed.contains(value)) {
      throw FormatException('Dữ liệu $key không hợp lệ.');
    }
    return value;
  }
}
