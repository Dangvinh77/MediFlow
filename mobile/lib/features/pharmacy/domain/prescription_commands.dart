import 'dart:math';

import 'drug_commands.dart';
import 'pharmacy_repository.dart';
import 'prescription.dart';
import 'pharmacy_mode.dart';

/// Values must come from the public visit/staff workflow and confirmed Billing
/// policy. UUID syntax is not evidence of authority; the server checks relations.
class OutpatientPrescriptionContext {
  const OutpatientPrescriptionContext({
    required this.recordId,
    required this.patientId,
    required this.doctorId,
    required this.departmentId,
    required this.careEpisodeId,
    required this.priceCode,
    required this.sourceReference,
  });
  final String recordId, patientId, doctorId, departmentId, careEpisodeId;
  final String priceCode, sourceReference;
  Map<String, String> errors() => {
    for (final field in {
      'recordId': recordId,
      'patientId': patientId,
      'doctorId': doctorId,
      'departmentId': departmentId,
      'careEpisodeId': careEpisodeId,
    }.entries)
      if (!isPharmacyId(field.value))
        field.key: 'Nhập UUID từ nguồn đã xác minh.',
    if (priceCode.trim().isEmpty || priceCode.length > 64)
      'priceCode': 'Nhập mã giá được Billing xác nhận, tối đa 64 ký tự.',
    if (sourceReference.trim().isEmpty)
      'sourceReference': 'Ghi nguồn public workflow và policy đã đối chiếu.',
  };
}

class RequestedPrescriptionLine {
  const RequestedPrescriptionLine({
    required this.drugId,
    required this.quantity,
    this.dosage,
  });
  final String drugId;
  final int quantity;
  final String? dosage;
}

class CreateOutpatientPrescription {
  CreateOutpatientPrescription({
    required this.context,
    required this.prescribedDate,
    required List<RequestedPrescriptionLine> lines,
  }) : lines = List.unmodifiable(lines);
  final OutpatientPrescriptionContext context;
  final String prescribedDate;
  final List<RequestedPrescriptionLine> lines;
  Map<String, String> errors({DateTime? today}) {
    final result = context.errors();
    final date = DateTime.tryParse(prescribedDate);
    final now = today ?? DateTime.now();
    if (!RegExp(r'^\d{4}-\d{2}-\d{2}$').hasMatch(prescribedDate) ||
        date == null ||
        date.toIso8601String().substring(0, 10) != prescribedDate ||
        date.isAfter(DateTime(now.year, now.month, now.day))) {
      result['prescribedDate'] = 'Ngày YYYY-MM-DD hợp lệ, không ở tương lai.';
    }
    if (lines.isEmpty) result['lines'] = 'Chọn ít nhất một thuốc từ danh mục.';
    for (var i = 0; i < lines.length; i++) {
      final line = lines[i];
      if (!isPharmacyId(line.drugId) ||
          line.quantity < 1 ||
          line.quantity > 2147483647 ||
          (line.dosage?.length ?? 0) > 255) {
        result['lines.$i'] =
            'Thuốc hợp lệ, số lượng 1–2147483647, liều dùng tối đa 255 ký tự.';
      }
    }
    return result;
  }

  void validate() {
    final fields = errors();
    if (fields.isNotEmpty) throw PharmacyValidationException(fields);
  }
}

/// Frozen in memory for one intent. Retrying reuses this exact object and key.
class PrescriptionIntent {
  PrescriptionIntent(this.command, {String? key}) : key = key ?? _newKey() {
    command.validate();
    if (!isPharmacyId(this.key)) {
      throw ArgumentError('Idempotency-Key phải là UUID.');
    }
  }
  final CreateOutpatientPrescription command;
  final String key;
  static String _newKey() {
    final random = Random.secure();
    final bytes = List.generate(16, (_) => random.nextInt(256));
    bytes[6] = (bytes[6] & 15) | 64;
    bytes[8] = (bytes[8] & 63) | 128;
    final hex = bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
    return '${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}';
  }
}

/// The composition root supplies this capability only with a transport that
/// sends the real Idempotency-Key header. Never emulate it in the body/query.
abstract interface class OutpatientPrescriptionWriter {
  Future<Prescription> createPrescription(PrescriptionIntent intent);
}

/// Bind only when the shared Gateway client supports a truly bodyless PUT.
abstract interface class PrescriptionDispenser {
  Future<DispenseResult> dispensePrescription(String prescriptionId);
}

bool canDispensePrescription(Prescription rx, PharmacyMode mode) =>
    rx.status == PrescriptionStatus.active &&
    rx.dispenseStatus == DispenseStatus.pending &&
    switch (mode) {
      PharmacyMode.outpatient => true,
      PharmacyMode.legacy => rx.paymentConfirmed,
      PharmacyMode.unavailable => false,
    };

class DispensePrescription {
  const DispensePrescription(this.dispenser);
  final PrescriptionDispenser dispenser;
  Future<DispenseResult> call(Prescription snapshot, PharmacyMode mode) async {
    if (!isPharmacyId(snapshot.id) ||
        !canDispensePrescription(snapshot, mode)) {
      throw StateError('Đơn thuốc chưa cho phép thực hiện xuất.');
    }
    final result = await dispenser.dispensePrescription(snapshot.id);
    if (result.prescriptionId.toLowerCase() != snapshot.id.toLowerCase() ||
        !isPharmacyId(result.id) ||
        (result.status != DispenseStatus.dispensed &&
            result.status != DispenseStatus.failed) ||
        (result.status == DispenseStatus.dispensed &&
            (result.dispensedAt == null ||
                result.dispensedBy == null ||
                !isPharmacyId(result.dispensedBy!) ||
                result.dispensedActorType == null)) ||
        (result.status == DispenseStatus.failed &&
            (result.failureReason?.trim().isEmpty ?? true))) {
      throw const FormatException(
        'Phiếu xuất trả về không đủ dữ liệu hoặc không khớp đơn.',
      );
    }
    return result;
  }
}

class CancelPrescription {
  const CancelPrescription(this.reason);
  final String reason;
  void validate() {
    if (reason.trim().isEmpty || reason.length > 500) {
      throw const PharmacyValidationException({
        'reason': 'Lý do bắt buộc, tối đa 500 ký tự.',
      });
    }
  }
}

bool canCancelPrescription(Prescription rx) =>
    rx.status == PrescriptionStatus.active &&
    rx.dispenseStatus == DispenseStatus.pending;
