import '../../../core/network/json_reader.dart';
import '../domain/appointment.dart';

// Read projection of AppointmentDTO; no synthesized relationship/status defaults.
abstract final class AppointmentDto {
  static Appointment fromJson(Map<String, dynamic> json) => Appointment(
    id: JsonReader.string(json, 'appointmentId'),
    patientId: JsonReader.string(json, 'patientId'),
    doctorId: JsonReader.string(json, 'doctorId'),
    departmentId: JsonReader.string(json, 'departmentId'),
    date: JsonReader.string(json, 'appointmentDate'),
    time: JsonReader.string(json, 'appointmentTime'),
    status: JsonReader.string(json, 'status'),
    reason: JsonReader.optionalString(json, 'reason'),
  );
}
