import '../../../core/domain/page_result.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/page_decoder.dart';
import '../domain/appointment.dart';
import 'appointment_dto.dart';

class GatewayAppointmentRepository implements AppointmentRepository {
  const GatewayAppointmentRepository(this.client);
  final ApiClient client;

  @override
  Future<PageResult<Appointment>> search({
    int page = 0,
    String? date,
    String? departmentId,
  }) async {
    if (page < 0) throw ArgumentError.value(page, 'page');
    if (date != null && !_validDate(date)) {
      throw ArgumentError.value(date, 'date', 'Expected YYYY-MM-DD.');
    }
    if (departmentId != null && !isAppointmentUuid(departmentId)) {
      throw ArgumentError.value(departmentId, 'departmentId');
    }
    return decodePage(
      await client.get(
        '/v1/appointments',
        query: {
          'page': page,
          'size': 20,
          'appointmentDate': ?date,
          'departmentId': ?departmentId,
        },
      ),
      AppointmentDto.fromJson,
    );
  }

  @override
  Future<Appointment> getById(String appointmentId) async {
    _requireId(appointmentId, 'appointmentId');
    return _read(
      await client.get(
        '/v1/appointments/${Uri.encodeComponent(appointmentId)}',
      ),
      appointmentId,
    );
  }

  @override
  Future<Appointment> create(AppointmentDraft draft) async {
    final issue = validateAppointmentDraft(draft);
    if (issue != null) throw ArgumentError(issue);
    return _read(
      await client.post('/v1/appointments', {
        'patientId': draft.patientId.trim(),
        'doctorId': draft.doctorId.trim(),
        'departmentId': draft.departmentId.trim(),
        'appointmentDate': draft.date,
        'appointmentTime': draft.time,
        'reason': _clean(draft.reason),
      }),
    );
  }

  @override
  Future<Appointment> update(String appointmentId, AppointmentEdit edit) async {
    _requireId(appointmentId, 'appointmentId');
    final issue = validateAppointmentEdit(edit);
    if (issue != null) throw ArgumentError(issue);
    return _read(
      await client.put(
        '/v1/appointments/${Uri.encodeComponent(appointmentId)}',
        {
          'appointmentDate': edit.date,
          'appointmentTime': edit.time,
          'reason': _clean(edit.reason),
        },
      ),
      appointmentId,
    );
  }

  @override
  Future<Appointment> checkIn(String appointmentId) async {
    _requireId(appointmentId, 'appointmentId');
    return _read(
      await client.put(
        '/v1/appointments/${Uri.encodeComponent(appointmentId)}/check-in',
      ),
      appointmentId,
    );
  }

  @override
  Future<Appointment> startExam(String appointmentId) async {
    _requireId(appointmentId, 'appointmentId');
    return _read(
      await client.put(
        '/v1/appointments/${Uri.encodeComponent(appointmentId)}/start-exam',
        {'emergencyOverride': null},
      ),
      appointmentId,
    );
  }

  @override
  Future<Appointment> cancel(String appointmentId) async {
    _requireId(appointmentId, 'appointmentId');
    return _read(
      await client.put(
        '/v1/appointments/${Uri.encodeComponent(appointmentId)}/status',
        {'status': 'CANCELLED'},
      ),
      appointmentId,
    );
  }

  static Appointment _read(Object? value, [String? expectedId]) {
    if (value is! Map<String, dynamic>) {
      throw const FormatException('Phản hồi lịch hẹn không hợp lệ.');
    }
    final appointment = AppointmentDto.fromJson(value);
    if (expectedId != null && appointment.id != expectedId) {
      throw const FormatException('Mã lịch hẹn trả về không khớp yêu cầu.');
    }
    return appointment;
  }

  static void _requireId(String id, String field) {
    if (!isAppointmentUuid(id)) throw ArgumentError.value(id, field);
  }

  static String? _clean(String? value) {
    final trimmed = value?.trim();
    return trimmed == null || trimmed.isEmpty ? null : trimmed;
  }

  static bool _validDate(String value) {
    final match = RegExp(r'^(\d{4})-(\d{2})-(\d{2})$').firstMatch(value);
    if (match == null) return false;
    final date = DateTime.tryParse(value);
    return date != null &&
        date.year == int.parse(match[1]!) &&
        date.month == int.parse(match[2]!) &&
        date.day == int.parse(match[3]!);
  }
}
