import 'dart:math';

import 'medical_record.dart';

const recordClosureReasonLabels = <String, String>{
  'PATIENT_LEFT': 'Bệnh nhân tự ý về',
  'PATIENT_REFUSED_EXAM': 'Bệnh nhân từ chối khám',
  'PATIENT_REFUSED_ORDER': 'Bệnh nhân từ chối chỉ định',
  'OTHER': 'Lý do khác',
};

const recordClosureNoteMax = 1000;
const labOrderPostponePrefix = 'Hoãn sang lần khám sau: ';

String newExamCommandKey() {
  final random = Random.secure();
  final bytes = List.generate(16, (_) => random.nextInt(256));
  bytes[6] = (bytes[6] & 15) | 64;
  bytes[8] = (bytes[8] & 63) | 128;
  final s = bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
  return '${s.substring(0, 8)}-${s.substring(8, 12)}-${s.substring(12, 16)}-'
      '${s.substring(16, 20)}-${s.substring(20)}';
}

/// One lab order of a record, with the status Lab reports for it.
class RecordLabOrder {
  const RecordLabOrder({
    required this.sourceOrderId,
    required this.labType,
    required this.status,
    required this.orderedAt,
    this.testId,
  });

  final String sourceOrderId, labType, status, orderedAt;

  /// Absent while Lab has not received the order, so it cannot be cancelled yet.
  final String? testId;

  bool get inProgress =>
      testId != null && status != 'COMPLETED' && status != 'CANCELLED';
}

abstract interface class ExamLifecycleRepository {
  Future<List<RecordLabOrder>> labOrders(String recordId);
  Future<MedicalRecord> closeWithoutConclusion(
    String recordId, {
    required String reason,
    required String note,
    required String idempotencyKey,
  });
}

/// The server makes the final decision; this only decides what to offer.
bool canCloseWithoutConclusion(
  MedicalRecord record, {
  required String? role,
  required bool outpatientEnabled,
}) =>
    outpatientEnabled &&
    role == 'DOCTOR' &&
    record.status == 'OPEN' &&
    record.appointmentId?.trim().isNotEmpty == true;
