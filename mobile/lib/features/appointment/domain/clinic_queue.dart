import '../../../core/network/api_failure.dart';
import 'reception.dart';

const clinicQueueStages = {
  'AWAITING_PAYMENT',
  'NOT_ARRIVED',
  'WAITING_ROOM_TICKET',
  'WAITING_CALL',
  'CALLED',
  'IN_EXAM',
  'ABSENT',
};

class ClinicQueueEntry {
  const ClinicQueueEntry({
    required this.registrationId,
    required this.patientId,
    required this.sessionId,
    required this.doctorId,
    required this.departmentId,
    required this.roomId,
    required this.queueDate,
    required this.stage,
    required this.priority,
    required this.vitalSignsRecorded,
    required this.readyForExam,
    required this.continuationRequired,
    this.queueNumber,
    this.ticketId,
    this.ticketRevision,
    this.roomQueueNumber,
    this.checkedInAt,
    this.calledAt,
  });

  final String registrationId, patientId, sessionId, doctorId, departmentId;
  final String roomId, queueDate, stage;
  final bool priority, vitalSignsRecorded, readyForExam, continuationRequired;
  final int? queueNumber, ticketRevision, roomQueueNumber;
  final String? ticketId, checkedInAt, calledAt;
}

class ClinicQueueTicket {
  const ClinicQueueTicket({
    required this.ticketId,
    required this.revision,
    required this.registrationId,
    required this.roomId,
    required this.queueDate,
    required this.roomQueueNumber,
    required this.status,
    required this.priority,
    required this.issuedAt,
    this.calledAt,
  });

  final String ticketId, registrationId, roomId, queueDate, status, issuedAt;
  final int revision, roomQueueNumber;
  final bool priority;
  final String? calledAt;
}

class VitalSignInput {
  const VitalSignInput({
    this.pulseBpm,
    this.systolicMmhg,
    this.diastolicMmhg,
    this.temperatureC,
    this.respiratoryRate,
    this.spo2Percent,
    this.weightKg,
    this.notMeasuredReason,
  });

  final int? pulseBpm,
      systolicMmhg,
      diastolicMmhg,
      respiratoryRate,
      spo2Percent;
  final double? temperatureC, weightKg;
  final String? notMeasuredReason;

  /// Device ranges stay with the backend; this only catches an unfinished form.
  String? validate() {
    final systolic = systolicMmhg, diastolic = diastolicMmhg;
    if ((systolic == null) != (diastolic == null)) {
      return 'Cần nhập cả huyết áp tâm thu và tâm trương.';
    }
    if (systolic != null && diastolic != null && diastolic >= systolic) {
      return 'Huyết áp tâm trương phải nhỏ hơn tâm thu.';
    }
    final incomplete = [
      pulseBpm,
      systolic,
      diastolic,
      temperatureC,
      respiratoryRate,
      spo2Percent,
      weightKg,
    ].contains(null);
    if (incomplete && (notMeasuredReason?.trim() ?? '').isEmpty) {
      return 'Cần ghi lý do khi chưa đo đủ các chỉ số.';
    }
    return null;
  }

  Map<String, Object?> toJson() => {
    'pulseBpm': pulseBpm,
    'systolicMmhg': systolicMmhg,
    'diastolicMmhg': diastolicMmhg,
    'temperatureC': temperatureC,
    'respiratoryRate': respiratoryRate,
    'spo2Percent': spo2Percent,
    'weightKg': weightKg,
    'notMeasuredReason': notMeasuredReason,
  };
}

class VitalSignRecord {
  const VitalSignRecord({
    required this.observationId,
    required this.registrationId,
    required this.observedAt,
    required this.input,
  });

  final String observationId, registrationId, observedAt;
  final VitalSignInput input;
}

class RescheduleInput {
  const RescheduleInput({
    required this.expectedRevision,
    required this.targetSessionId,
    required this.reason,
    required this.patientConfirmed,
  });

  final int expectedRevision;
  final String targetSessionId, reason;
  final bool patientConfirmed;

  String? validate() {
    if (targetSessionId.isEmpty) return 'Chọn buổi khám mới.';
    if (reason.trim().isEmpty) return 'Nhập lý do đổi lịch.';
    if (!patientConfirmed) {
      return 'Cần xác nhận bệnh nhân đã đồng ý với lịch mới.';
    }
    return null;
  }

  Map<String, Object?> toJson() => {
    'expectedRevision': expectedRevision,
    'targetSessionId': targetSessionId,
    'reason': reason.trim(),
    'patientConfirmed': patientConfirmed,
  };
}

class AffectedRegistration {
  const AffectedRegistration({
    required this.registrationId,
    required this.patientId,
    required this.sessionId,
    required this.status,
    this.reservationStatus,
    this.checkedInAt,
    this.queueNumber,
    this.roomQueueNumber,
  });

  final String registrationId, patientId, sessionId, status;
  final String? reservationStatus, checkedInAt;
  final int? queueNumber, roomQueueNumber;
}

class ScheduleImpact {
  const ScheduleImpact({
    required this.heldCount,
    required this.paymentLockedCount,
    required this.confirmedCount,
    required this.consumedCount,
    required this.registrations,
    this.sessionIds = const [],
  });

  final int heldCount, paymentLockedCount, confirmedCount, consumedCount;
  final List<AffectedRegistration> registrations;
  final List<String> sessionIds;
}

abstract interface class ClinicQueueRepository {
  Future<List<ClinicQueueEntry>> board({required String date, String? roomId});
  Future<ClinicQueueTicket> issueTicket(
    String registrationId, {
    required String idempotencyKey,
  });
  Future<ClinicQueueTicket> call(
    String ticketId,
    int expectedRevision, {
    required String idempotencyKey,
  });
  Future<ClinicQueueTicket> adjust(
    String ticketId,
    int expectedRevision,
    String type,
    String reason, {
    required String idempotencyKey,
  });
  Future<VitalSignRecord> recordVitals(
    String registrationId,
    VitalSignInput input, {
    required String idempotencyKey,
  });
  Future<List<VitalSignRecord>> listVitals(String registrationId);
  Future<void> reschedule(
    String registrationId,
    RescheduleInput input, {
    required String idempotencyKey,
  });
  Future<ScheduleImpact> sessionImpact(String sessionId);
  Future<ScheduleImpact> shiftImpact(String shiftId);
}

/// Keeps one Idempotency-Key per pending command, so a retry after an unknown
/// outcome cannot apply the same change twice.
class ClinicCommandKeys {
  final _keys = <String, (String fingerprint, String key)>{};

  String keyFor(String scope, String fingerprint) {
    final current = _keys[scope];
    if (current != null && current.$1 == fingerprint) return current.$2;
    final key = newReceptionKey();
    _keys[scope] = (fingerprint, key);
    return key;
  }

  void settle(String scope) => _keys.remove(scope);
}

bool clinicOutcomeUnknown(Object error) {
  if (error is! ApiFailure) return true;
  final status = error.status;
  return status == null || status == 408 || status >= 500;
}

const _clinicMessages = {
  'CLINIC_DESK_SCOPE_DENIED': 'Chỉ điều dưỡng của khoa này mới dùng được quầy khoa. Điều dưỡng tiếp nhận vui lòng dùng màn Tiếp nhận ca khám.',
  'ROOM_TICKET_EXISTS':
      'Bệnh nhân này đã có số phòng. Danh sách đã được tải lại.',
  'ROOM_TICKET_NOT_ELIGIBLE':
      'Chỉ cấp số phòng cho bệnh nhân đã thanh toán và đã đến quầy tiếp nhận.',
  'ROOM_TICKET_WRONG_DATE': 'Chỉ cấp số phòng trong đúng ngày khám.',
  'TICKET_STATE_CONFLICT': 'Lượt này vừa đổi trạng thái nên thao tác không còn phù hợp. Danh sách đã được tải lại.',
  'REVISION_CONFLICT':
      'Lượt này vừa được cập nhật ở nơi khác. Danh sách đã được tải lại.',
  'VITAL_SIGNS_INVALID': 'Có chỉ số nằm ngoài khoảng đo được của thiết bị hoặc huyết áp chưa hợp lệ. Hãy kiểm tra lại.',
  'VITAL_SIGNS_REASON_REQUIRED': 'Cần ghi lý do khi chưa đo đủ các chỉ số.',
};

Object friendlyClinicError(Object error) {
  if (error is! ApiFailure) return error;
  final message = _clinicMessages[error.code];
  if (message == null) return error;
  return ApiFailure(
    message,
    status: error.status,
    code: error.code,
    correlationId: error.correlationId,
  );
}

String queueNumberLabel(bool clinicIntakeEnabled) =>
    clinicIntakeEnabled ? 'Số quầy' : 'Số thứ tự';

const roomQueueNumberLabel = 'Số phòng khám';

/// Text for a counter number and, once issued, the separate room number.
String clinicNumbersText(int? queueNumber, int? roomQueueNumber) => [
  if (queueNumber != null) 'Số quầy $queueNumber',
  if (roomQueueNumber != null) '$roomQueueNumberLabel $roomQueueNumber',
].join(' · ');

const sessionAvailabilityLabels = {
  'AVAILABLE': 'Còn chỗ',
  'FULL': 'Đã hết chỗ',
  'NOT_OPEN': 'Buổi không nhận đăng ký',
  'LOAD_ERROR': 'Không tải được lịch',
};

String sessionAvailability(ReceptionSession? session, Object? loadError) {
  if (loadError != null) return 'LOAD_ERROR';
  if (session == null || session.status != 'OPEN') return 'NOT_OPEN';
  return session.availableCount < 1 ? 'FULL' : 'AVAILABLE';
}

/// The calendar date in Vietnam, which is what clinic numbers reset on.
String vietnamDate([DateTime? now]) {
  final local = (now ?? DateTime.now()).toUtc().add(const Duration(hours: 7));
  return local.toIso8601String().substring(0, 10);
}
