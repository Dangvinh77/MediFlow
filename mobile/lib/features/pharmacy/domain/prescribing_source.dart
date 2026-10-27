import 'prescription_commands.dart';

/// Authoritative public Pharmacy context; source IDs are never inferred by UI.
class PrescribingSource {
  PrescribingSource({
    required this.recordId,
    required this.patientId,
    required this.doctorId,
    required this.departmentId,
    required this.careEpisodeId,
    required List<String> priceCodes,
  }) : priceCodes = List.unmodifiable(priceCodes);

  final String recordId, patientId, doctorId, departmentId, careEpisodeId;
  final List<String> priceCodes;

  OutpatientPrescriptionContext context(String priceCode) {
    if (!priceCodes.contains(priceCode)) {
      throw ArgumentError('Chọn nhóm thu do hệ thống cung cấp.');
    }
    return OutpatientPrescriptionContext(
      recordId: recordId,
      patientId: patientId,
      doctorId: doctorId,
      departmentId: departmentId,
      careEpisodeId: careEpisodeId,
      priceCode: priceCode,
      sourceReference: 'Gateway Pharmacy prescribing/context',
    );
  }
}

abstract interface class PrescribingSourceReader {
  Future<PrescribingSource> prescribingSource(String recordId);
}
