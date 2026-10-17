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
    this.reason,
  });
  final String id, patientId, doctorId, departmentId, date, time, status;
  final String? reason;
}

abstract interface class AppointmentRepository {
  Future<PageResult<Appointment>> search({int page = 0, String? date});
}

class FindAppointments {
  const FindAppointments(this.repository);
  final AppointmentRepository repository;
  Future<PageResult<Appointment>> call({int page = 0, String? date}) {
    if (page < 0) throw ArgumentError.value(page, 'page');
    return repository.search(page: page, date: date);
  }
}
