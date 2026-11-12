import '../../../core/domain/follow_up_recommendation.dart';
import '../../../core/domain/page_result.dart';

const sessionStatusLabels = {
  'OPEN': 'Đang nhận đăng ký',
  'STOPPED': 'Tạm ngừng nhận',
  'CANCELLED': 'Đã hủy',
};

const registrationStatusLabels = {
  'AWAITING_PAYMENT': 'Chờ thanh toán',
  'CONFIRMED': 'Đã xác nhận',
  'IN_EXAM': 'Đang khám',
  'COMPLETED': 'Đã hoàn tất',
  'EXPIRED': 'Hết hạn giữ chỗ',
  'CANCELLED': 'Đã hủy',
};

String displayDate(String isoDate) {
  final parts = isoDate.split('-');
  return parts.length == 3 ? '${parts[2]}/${parts[1]}/${parts[0]}' : isoDate;
}

String isoDate(DateTime value) =>
    '${value.year.toString().padLeft(4, '0')}-'
    '${value.month.toString().padLeft(2, '0')}-'
    '${value.day.toString().padLeft(2, '0')}';

/// Business dates follow the clinic's timezone, not the device's.
DateTime clinicToday([DateTime? now]) {
  final local = (now ?? DateTime.now()).toUtc().add(const Duration(hours: 7));
  return DateTime(local.year, local.month, local.day);
}

DateTime mondayOfWeek(DateTime date) =>
    DateTime(date.year, date.month, date.day - (date.weekday - 1));

class ScheduleSession {
  const ScheduleSession({
    required this.id,
    required this.departmentId,
    required this.roomId,
    required this.date,
    required this.startTime,
    required this.endTime,
    required this.status,
    required this.confirmedCount,
    required this.availableCount,
  });

  final String id, departmentId, roomId, date, startTime, endTime, status;
  final int confirmedCount, availableCount;
}

class DoctorCalendar {
  const DoctorCalendar({
    required this.fromDate,
    required this.toDate,
    required this.sessions,
  });

  final String fromDate, toDate;
  final List<ScheduleSession> sessions;
}

class DoctorWorkShift {
  const DoctorWorkShift({
    required this.id,
    required this.departmentId,
    required this.roomId,
    required this.date,
    required this.startTime,
    required this.endTime,
    required this.status,
  });

  final String id, departmentId, roomId, date, startTime, endTime, status;
}

class RegistrationRow {
  const RegistrationRow({
    required this.id,
    required this.patientId,
    required this.visitType,
    required this.status,
    required this.readyForExam,
    this.checkedInAt,
    this.queueNumber,
    this.roomQueueNumber,
  });

  final String id, patientId, visitType, status;
  final bool readyForExam;
  final String? checkedInAt;
  final int? queueNumber, roomQueueNumber;
}

class OutpatientRegistration {
  const OutpatientRegistration({
    required this.id,
    required this.revision,
    required this.doctorId,
    required this.departmentId,
    required this.roomId,
    required this.sessionDate,
    required this.startTime,
    required this.endTime,
    required this.receptionMode,
    required this.visitType,
    required this.status,
    required this.reservationExpiresAt,
    this.checkedInAt,
    this.queueNumber,
    this.roomQueueNumber,
    this.followUp,
  });

  final String id,
      doctorId,
      departmentId,
      roomId,
      sessionDate,
      startTime,
      endTime,
      receptionMode,
      visitType,
      status,
      reservationExpiresAt;
  final int revision;
  final String? checkedInAt;
  final int? queueNumber, roomQueueNumber;
  final FollowUpRecommendation? followUp;
}

abstract interface class OutpatientScheduleRepository {
  /// Pass exactly one of [date] or [weekStart], as yyyy-MM-dd.
  Future<DoctorCalendar> myCalendar({String? date, String? weekStart});
  Future<List<DoctorWorkShift>> myWorkShifts(String fromDate, String toDate);
  Future<PageResult<RegistrationRow>> sessionRegistrations(
    String sessionId, {
    int page = 0,
  });
  Future<OutpatientRegistration> registration(String registrationId);
  Future<OutpatientRegistration> markArrival(
    String registrationId, {
    required int expectedRevision,
    required String idempotencyKey,
    required String correlationId,
  });
  Future<PageResult<OutpatientRegistration>> myRegistrations({int page = 0});
}
