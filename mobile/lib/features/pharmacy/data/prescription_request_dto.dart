import '../domain/prescription_commands.dart';

/// Exact V1 wire body. Source evidence stays local and is never sent as authority.
/// The missing shared transport must send intent.key as Idempotency-Key, not JSON.
abstract final class PrescriptionRequestDto {
  static Map<String, Object?> outpatient(PrescriptionIntent intent) {
    final command = intent.command;
    command.validate();
    return {
      'recordId': command.context.recordId,
      'patientId': command.context.patientId,
      'doctorId': command.context.doctorId,
      'departmentId': command.context.departmentId,
      'prescribedDate': command.prescribedDate,
      'lines': command.lines
          .map(
            (line) => {
              'drugId': line.drugId,
              'quantity': line.quantity,
              'dosage': line.dosage,
            },
          )
          .toList(),
      'careContractVersion': 1,
      'careContext': 'OUTPATIENT',
      'careEpisodeType': 'OUTPATIENT_VISIT',
      'careEpisodeId': command.context.careEpisodeId,
      'admissionId': null,
      'priceCode': command.context.priceCode,
    };
  }
}
