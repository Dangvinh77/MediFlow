import '../../../core/network/json_reader.dart';
import '../domain/appointment.dart';

// Exact read projection of AppointmentDTO; no synthesized identifiers, status,
// or contract version are used to make an action appear available.
abstract final class AppointmentDto {
  static Appointment fromJson(Map<String, dynamic> json) {
    final version = json['careContractVersion'];
    if (version is! int || version < 0) {
      throw const FormatException('Dữ liệu careContractVersion không hợp lệ.');
    }
    return Appointment(
      id: JsonReader.string(json, 'appointmentId'),
      patientId: JsonReader.string(json, 'patientId'),
      doctorId: JsonReader.string(json, 'doctorId'),
      departmentId: JsonReader.string(json, 'departmentId'),
      date: JsonReader.string(json, 'appointmentDate'),
      time: JsonReader.string(json, 'appointmentTime'),
      status: JsonReader.string(json, 'status'),
      reason: JsonReader.optionalString(json, 'reason'),
      createdAt: JsonReader.string(json, 'createdAt'),
      updatedAt: JsonReader.optionalString(json, 'updatedAt'),
      careContractVersion: version,
      examClearanceId: JsonReader.optionalString(json, 'examClearanceId'),
      examClearanceAt: JsonReader.optionalString(json, 'examClearanceAt'),
      emergencyOverrideId: JsonReader.optionalString(
        json,
        'emergencyOverrideId',
      ),
      examPriceCode: JsonReader.optionalString(json, 'examPriceCode'),
      checkedInAt: JsonReader.optionalString(json, 'checkedInAt'),
      examinationStartedAt: JsonReader.optionalString(
        json,
        'examinationStartedAt',
      ),
      completedAt: JsonReader.optionalString(json, 'completedAt'),
      cancelledBy: JsonReader.optionalString(json, 'cancelledBy'),
      cancellationReason: JsonReader.optionalString(json, 'cancellationReason'),
      cancelledAt: JsonReader.optionalString(json, 'cancelledAt'),
    );
  }
}
