enum PrescriptionStatus {
  active,
  fulfilled,
  cancelled,
  expired,
  dispenseFailed,
}

enum DispenseStatus { pending, dispensed, failed, cancelled, expired }

class PrescriptionLine {
  const PrescriptionLine({
    required this.id,
    required this.drugId,
    required this.drugName,
    required this.quantity,
    required this.unitPrice,
    required this.dosage,
    required this.lineTotal,
  });
  final String id, drugId;
  final String? drugName, dosage;
  final int quantity;
  final num unitPrice, lineTotal;
}

class Prescription {
  Prescription({
    required this.id,
    required this.recordId,
    required this.patientId,
    required this.doctorId,
    required this.departmentId,
    required this.prescribedDate,
    required this.totalAmount,
    required List<PrescriptionLine> lines,
    required this.status,
    required this.dispenseStatus,
    required this.paymentConfirmed,
    required this.cancelledAt,
    required this.cancelledBy,
    required this.cancellationReason,
    required this.createdAt,
    required this.updatedAt,
  }) : lines = List.unmodifiable(lines);
  final String id, patientId, doctorId, departmentId, prescribedDate;
  final String? recordId, cancelledBy, cancellationReason;
  final DateTime? cancelledAt;
  final DateTime createdAt, updatedAt;
  final num totalAmount;
  final List<PrescriptionLine> lines;
  final PrescriptionStatus status;
  final DispenseStatus dispenseStatus;
  final bool paymentConfirmed;
  // The read contract contains no version metadata. Do not infer a care mode.
}

enum DispenseActorType { staff, account, system, legacyUnknown }

class DispenseResult {
  const DispenseResult({
    required this.id,
    required this.prescriptionId,
    required this.status,
    required this.dispensedAt,
    required this.dispensedBy,
    required this.failureReason,
    this.dispensedActorType,
  });
  final String id, prescriptionId;
  final DispenseStatus status;
  final DateTime? dispensedAt;
  final String? dispensedBy, failureReason;
  final DispenseActorType? dispensedActorType;
}

class CancelPrescriptionResult {
  const CancelPrescriptionResult({
    required this.prescriptionId,
    required this.status,
    required this.releasedReservations,
    required this.cancelledAt,
  });
  final String prescriptionId;
  final PrescriptionStatus status;
  final int releasedReservations;
  final DateTime cancelledAt;
}
