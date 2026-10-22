import '../../../core/domain/page_result.dart';

class Appointment {
  const Appointment({
    required this.id,
    required this.patientId,
    required this.doctorId,
    required this.departmentId,
    required this.date,
    required this.time,
    required this.status,
    required this.createdAt,
    required this.careContractVersion,
    this.reason,
    this.updatedAt,
    this.examClearanceId,
    this.examClearanceAt,
    this.emergencyOverrideId,
    this.examPriceCode,
    this.checkedInAt,
    this.examinationStartedAt,
    this.completedAt,
    this.cancelledBy,
    this.cancellationReason,
    this.cancelledAt,
  });

  final String id, patientId, doctorId, departmentId, date, time, status;
  final String createdAt;
  final int careContractVersion;
  final String? reason, updatedAt;
  final String? examClearanceId, examClearanceAt, emergencyOverrideId;
  final String? examPriceCode, checkedInAt, examinationStartedAt, completedAt;
  final String? cancelledBy, cancellationReason, cancelledAt;
}

class AppointmentDraft {
  const AppointmentDraft({
    required this.patientId,
    required this.doctorId,
    required this.departmentId,
    required this.date,
    required this.time,
    this.reason,
  });

  final String patientId, doctorId, departmentId, date, time;
  final String? reason;
}

class AppointmentEdit {
  const AppointmentEdit({required this.date, required this.time, this.reason});
  final String date, time;
  final String? reason;
}

abstract interface class AppointmentRepository {
  Future<PageResult<Appointment>> search({
    int page = 0,
    String? date,
    String? departmentId,
  });
  Future<Appointment> getById(String appointmentId);
  Future<Appointment> create(AppointmentDraft draft);
  Future<Appointment> update(String appointmentId, AppointmentEdit edit);
  Future<Appointment> checkIn(String appointmentId);
  Future<Appointment> startExam(String appointmentId);
  Future<Appointment> cancel(String appointmentId);
}

class FindAppointments {
  const FindAppointments(this.repository);
  final AppointmentRepository repository;
  Future<PageResult<Appointment>> call({
    int page = 0,
    String? date,
    String? departmentId,
  }) {
    if (page < 0) throw ArgumentError.value(page, 'page');
    return repository.search(
      page: page,
      date: date,
      departmentId: departmentId,
    );
  }
}

bool isAppointmentUuid(String value) => RegExp(
  r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$',
).hasMatch(value);

String? validateAppointmentDraft(AppointmentDraft draft) {
  if (!isAppointmentUuid(draft.patientId) ||
      !isAppointmentUuid(draft.doctorId) ||
      !isAppointmentUuid(draft.departmentId)) {
    return 'Chọn bệnh nhân, bác sĩ và khoa từ danh sách.';
  }
  if (!_validDate(draft.date) || _isBeforeToday(draft.date)) {
    return 'Ngày hẹn phải hợp lệ và không ở quá khứ.';
  }
  if (!_validTime(draft.time)) return 'Giờ hẹn không hợp lệ.';
  if ((draft.reason?.trim().length ?? 0) > 1000) {
    return 'Lý do không được dài quá 1000 ký tự.';
  }
  return null;
}

String? validateAppointmentEdit(AppointmentEdit edit) {
  if (!_validDate(edit.date) || _isBeforeToday(edit.date)) {
    return 'Ngày hẹn phải hợp lệ và không ở quá khứ.';
  }
  if (!_validTime(edit.time)) return 'Giờ hẹn không hợp lệ.';
  if ((edit.reason?.trim().length ?? 0) > 1000) {
    return 'Lý do không được dài quá 1000 ký tự.';
  }
  return null;
}

bool _validDate(String value) {
  final match = RegExp(r'^(\d{4})-(\d{2})-(\d{2})$').firstMatch(value);
  if (match == null) return false;
  final year = int.parse(match[1]!);
  final month = int.parse(match[2]!);
  final day = int.parse(match[3]!);
  final parsed = DateTime.tryParse(value);
  return parsed != null &&
      parsed.year == year &&
      parsed.month == month &&
      parsed.day == day;
}

bool _validTime(String value) =>
    RegExp(r'^([01]\d|2[0-3]):[0-5]\d$').hasMatch(value);

bool _isBeforeToday(String value) {
  final date = DateTime.parse(value);
  final now = DateTime.now();
  final today = DateTime(now.year, now.month, now.day);
  return date.isBefore(today);
}

bool canEditAppointment(Appointment appointment, {required String? role}) =>
    const {'ADMIN', 'DOCTOR', 'NURSE'}.contains(role) &&
    appointment.status == 'PENDING';

bool canCheckInAppointment(
  Appointment appointment, {
  required String? role,
  required bool outpatientEnabled,
}) =>
    outpatientEnabled &&
    const {'ADMIN', 'NURSE'}.contains(role) &&
    appointment.careContractVersion == 0 &&
    appointment.status == 'PENDING';

bool canStartAppointment(
  Appointment appointment, {
  required String? role,
  required bool outpatientEnabled,
}) =>
    outpatientEnabled &&
    const {'ADMIN', 'DOCTOR'}.contains(role) &&
    appointment.careContractVersion == 1 &&
    appointment.status == 'READY_FOR_EXAM' &&
    appointment.examClearanceId?.trim().isNotEmpty == true;

bool canCancelAppointment(Appointment appointment, {required String? role}) =>
    canEditAppointment(appointment, role: role);
