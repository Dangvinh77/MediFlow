import '../domain/drug.dart';
import '../domain/pharmacy_repository.dart';
import '../domain/prescription.dart';

// Feature-local strict readers: missing or malformed facts never become zero/false.
abstract final class PharmacyDto {
  static Map<String, dynamic> object(Object? value) {
    if (value is! Map<String, dynamic>) {
      throw const FormatException('Dữ liệu Dược không hợp lệ.');
    }
    return value;
  }

  static Object? field(Map<String, dynamic> json, String key) {
    if (!json.containsKey(key)) throw FormatException('Thiếu dữ liệu $key.');
    return json[key];
  }

  static String text(Map<String, dynamic> json, String key) {
    final value = field(json, key);
    if (value is! String || value.trim().isEmpty) {
      throw FormatException('Dữ liệu $key không hợp lệ.');
    }
    return value;
  }

  static String? optionalText(Map<String, dynamic> json, String key) {
    final value = field(json, key);
    if (value == null) return null;
    if (value is! String) throw FormatException('Dữ liệu $key không hợp lệ.');
    return value;
  }

  static String id(Map<String, dynamic> json, String key) {
    final value = text(json, key);
    if (!isPharmacyId(value)) {
      throw FormatException('Dữ liệu $key không phải UUID.');
    }
    return value;
  }

  static String? optionalId(Map<String, dynamic> json, String key) =>
      field(json, key) == null ? null : id(json, key);

  static int integer(Map<String, dynamic> json, String key, {int minimum = 0}) {
    final value = field(json, key);
    if (value is! int || value < minimum) {
      throw FormatException('Dữ liệu $key không hợp lệ.');
    }
    return value;
  }

  static num money(Map<String, dynamic> json, String key) {
    final value = field(json, key);
    if (value is! num || !value.isFinite || value < 0) {
      throw FormatException('Dữ liệu $key không hợp lệ.');
    }
    return value;
  }

  static String date(Map<String, dynamic> json, String key) {
    final value = text(json, key);
    final parsed = DateTime.tryParse(value);
    if (!RegExp(r'^\d{4}-\d{2}-\d{2}$').hasMatch(value) ||
        parsed == null ||
        parsed.toIso8601String().substring(0, 10) != value) {
      throw FormatException('Dữ liệu $key không phải ngày hợp lệ.');
    }
    return value;
  }

  static DateTime instant(Map<String, dynamic> json, String key) {
    final value = text(json, key);
    final parsed = DateTime.tryParse(value);
    if (parsed == null ||
        !value.contains('T') ||
        !RegExp(r'(Z|[+-]\d{2}:\d{2})$').hasMatch(value)) {
      throw FormatException('Dữ liệu $key không phải thời điểm hợp lệ.');
    }
    return parsed;
  }

  static DateTime? optionalInstant(Map<String, dynamic> json, String key) =>
      field(json, key) == null ? null : instant(json, key);

  static PrescriptionStatus prescriptionStatus(Map<String, dynamic> json) =>
      switch (text(json, 'status')) {
        'ACTIVE' => PrescriptionStatus.active,
        'FULFILLED' => PrescriptionStatus.fulfilled,
        'CANCELLED' => PrescriptionStatus.cancelled,
        'EXPIRED' => PrescriptionStatus.expired,
        'DISPENSE_FAILED' => PrescriptionStatus.dispenseFailed,
        _ => throw const FormatException(
          'Trạng thái đơn thuốc chưa được hỗ trợ.',
        ),
      };

  static DispenseStatus dispenseStatus(Map<String, dynamic> json, String key) =>
      switch (text(json, key)) {
        'PENDING' => DispenseStatus.pending,
        'DISPENSED' => DispenseStatus.dispensed,
        'FAILED' => DispenseStatus.failed,
        'CANCELLED' => DispenseStatus.cancelled,
        'EXPIRED' => DispenseStatus.expired,
        _ => throw const FormatException(
          'Trạng thái xuất thuốc chưa được hỗ trợ.',
        ),
      };

  static Drug drug(Object? value) {
    final json = object(value);
    return Drug(
      id: id(json, 'drugId'),
      name: text(json, 'drugName'),
      activeIngredient: optionalText(json, 'activeIngredient'),
      unit: text(json, 'unit'),
      price: money(json, 'price'),
      stockQuantity: integer(json, 'stockQuantity'),
      expiryDate: date(json, 'expiryDate'),
      manufacturer: optionalText(json, 'manufacturer'),
      lowStockThreshold: integer(json, 'lowStockThreshold'),
      createdAt: instant(json, 'createdAt'),
      updatedAt: instant(json, 'updatedAt'),
    );
  }

  static Prescription prescription(Object? value) {
    final json = object(value);
    final lines = field(json, 'lines');
    if (lines is! List || json['paymentConfirmed'] is! bool) {
      throw const FormatException(
        'Dữ liệu dòng thuốc hoặc thanh toán không hợp lệ.',
      );
    }
    return Prescription(
      id: id(json, 'prescriptionId'),
      recordId: optionalId(json, 'recordId'),
      patientId: id(json, 'patientId'),
      doctorId: id(json, 'doctorId'),
      departmentId: id(json, 'departmentId'),
      prescribedDate: date(json, 'prescribedDate'),
      totalAmount: money(json, 'totalAmount'),
      lines: lines.map((value) {
        final line = object(value);
        return PrescriptionLine(
          id: id(line, 'lineId'),
          drugId: id(line, 'drugId'),
          drugName: optionalText(line, 'drugName'),
          quantity: integer(line, 'quantity', minimum: 1),
          unitPrice: money(line, 'unitPrice'),
          dosage: optionalText(line, 'dosage'),
          lineTotal: money(line, 'lineTotal'),
        );
      }).toList(),
      status: prescriptionStatus(json),
      dispenseStatus: dispenseStatus(json, 'dispenseStatus'),
      paymentConfirmed: json['paymentConfirmed'] as bool,
      cancelledAt: optionalInstant(json, 'cancelledAt'),
      cancelledBy: optionalId(json, 'cancelledBy'),
      cancellationReason: optionalText(json, 'cancellationReason'),
      createdAt: instant(json, 'createdAt'),
      updatedAt: instant(json, 'updatedAt'),
    );
  }

  static DispenseResult dispense(Object? value) {
    final json = object(value);
    return DispenseResult(
      id: id(json, 'dispenseId'),
      prescriptionId: id(json, 'prescriptionId'),
      status: dispenseStatus(json, 'status'),
      dispensedAt: optionalInstant(json, 'dispensedAt'),
      dispensedBy: optionalId(json, 'dispensedBy'),
      failureReason: optionalText(json, 'failureReason'),
    );
  }

  static CancelPrescriptionResult cancellation(Object? value) {
    final json = object(value);
    return CancelPrescriptionResult(
      prescriptionId: id(json, 'prescriptionId'),
      status: prescriptionStatus(json),
      releasedReservations: integer(json, 'releasedReservations'),
      cancelledAt: instant(json, 'cancelledAt'),
    );
  }
}
