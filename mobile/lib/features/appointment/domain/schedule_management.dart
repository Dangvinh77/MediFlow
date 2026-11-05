import 'dart:math';

import '../../../core/domain/page_result.dart';
import '../../../core/network/api_failure.dart';

String newScheduleKey() {
  final random = Random.secure();
  final bytes = List<int>.generate(16, (_) => random.nextInt(256));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  final hex = bytes
      .map((value) => value.toRadixString(16).padLeft(2, '0'))
      .join();
  return '${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}';
}

class ManagedDepartment {
  const ManagedDepartment({required this.id, required this.name});
  final String id, name;
}

class ConsultationRoom {
  const ConsultationRoom({required this.id, required this.name});
  final String id, name;
}

class SessionBreak {
  const SessionBreak({required this.startTime, required this.endTime});
  final String startTime, endTime;
  Map<String, Object?> toJson() => {'startTime': startTime, 'endTime': endTime};
}

class ManagedSession {
  const ManagedSession({
    required this.id,
    required this.revision,
    required this.doctorId,
    required this.departmentId,
    required this.roomId,
    required this.date,
    required this.startTime,
    required this.endTime,
    required this.breaks,
    required this.capacity,
    required this.status,
    required this.confirmedCount,
    required this.availableCount,
  });

  final String id, doctorId, departmentId, roomId, date, startTime, endTime;
  final String status;
  final List<SessionBreak> breaks;
  final int revision, capacity, confirmedCount, availableCount;
}

class ManagedShift {
  const ManagedShift({
    required this.id,
    required this.revision,
    required this.doctorId,
    required this.departmentId,
    required this.roomId,
    required this.date,
    required this.startTime,
    required this.endTime,
    required this.status,
  });

  final String id, doctorId, departmentId, roomId, date, startTime, endTime;
  final String status;
  final int revision;
}

class DepartmentSummary {
  const DepartmentSummary({
    required this.openSessions,
    required this.stoppedSessions,
    required this.capacity,
    required this.available,
    required this.held,
    required this.paymentLocked,
    required this.confirmed,
    required this.consumed,
  });

  final int openSessions, stoppedSessions, capacity, available;
  final int held, paymentLocked, confirmed, consumed;
}

class SchedulingScope {
  const SchedulingScope({
    required this.accountId,
    required this.revision,
    required this.departmentIds,
  });
  final String accountId;
  final int revision;
  final List<String> departmentIds;
}

class ManagerAccount {
  const ManagerAccount({required this.id, required this.username});
  final String id, username;
}

class HeadAppointmentTerm {
  const HeadAppointmentTerm({
    required this.staffId,
    required this.startedAt,
    this.endedAt,
  });
  final String staffId, startedAt;
  final String? endedAt;
}

class HeadAppointment {
  const HeadAppointment({
    required this.departmentId,
    required this.revision,
    required this.staffId,
    required this.history,
  });
  final String departmentId;
  final int revision;
  final String? staffId;
  final List<HeadAppointmentTerm> history;
}

/// One write. Its key and body stay fixed, so an unresolved command is resent
/// exactly as it was first sent.
class ScheduleCommand {
  ScheduleCommand._(this.method, this.path, this.body)
    : idempotencyKey = newScheduleKey(),
      correlationId = newScheduleKey();

  final String method, path;
  final Map<String, Object?> body;
  final String idempotencyKey, correlationId;

  static Map<String, Object?> _session({
    required String doctorId,
    required String departmentId,
    required String roomId,
    required String date,
    required String startTime,
    required String endTime,
    required List<SessionBreak> breaks,
    required int capacity,
  }) => {
    'doctorId': doctorId,
    'departmentId': departmentId,
    'roomId': roomId,
    'sessionDate': date,
    'startTime': startTime,
    'endTime': endTime,
    'breaks': [for (final item in breaks) item.toJson()],
    'capacity': capacity,
  };

  factory ScheduleCommand.createSession({
    required String doctorId,
    required String departmentId,
    required String roomId,
    required String date,
    required String startTime,
    required String endTime,
    required List<SessionBreak> breaks,
    required int capacity,
  }) => ScheduleCommand._(
    'POST',
    '/v2/consultation-sessions',
    _session(
      doctorId: doctorId,
      departmentId: departmentId,
      roomId: roomId,
      date: date,
      startTime: startTime,
      endTime: endTime,
      breaks: breaks,
      capacity: capacity,
    ),
  );

  factory ScheduleCommand.updateSession(
    ManagedSession session, {
    required String doctorId,
    required String roomId,
    required String date,
    required String startTime,
    required String endTime,
    required List<SessionBreak> breaks,
    required int capacity,
    required String reason,
  }) => ScheduleCommand._(
    'PUT',
    '/v2/consultation-sessions/${Uri.encodeComponent(session.id)}',
    {
      ..._session(
        doctorId: doctorId,
        departmentId: session.departmentId,
        roomId: roomId,
        date: date,
        startTime: startTime,
        endTime: endTime,
        breaks: breaks,
        capacity: capacity,
      ),
      'expectedRevision': session.revision,
      'reason': reason,
    },
  );

  factory ScheduleCommand.sessionStatus(
    ManagedSession session,
    String status,
    String reason,
  ) => ScheduleCommand._(
    'PUT',
    '/v2/consultation-sessions/${Uri.encodeComponent(session.id)}/status',
    {'expectedRevision': session.revision, 'status': status, 'reason': reason},
  );

  factory ScheduleCommand.createShift({
    required String doctorId,
    required String departmentId,
    required String roomId,
    required String date,
    required String startTime,
    required String endTime,
  }) => ScheduleCommand._('POST', '/v2/doctor-work-shifts', {
    'doctorId': doctorId,
    'departmentId': departmentId,
    'roomId': roomId,
    'shiftDate': date,
    'startTime': startTime,
    'endTime': endTime,
  });

  factory ScheduleCommand.updateShift(
    ManagedShift shift, {
    required String doctorId,
    required String roomId,
    required String date,
    required String startTime,
    required String endTime,
    required String reason,
  }) => ScheduleCommand._(
    'PUT',
    '/v2/doctor-work-shifts/${Uri.encodeComponent(shift.id)}',
    {
      'doctorId': doctorId,
      'departmentId': shift.departmentId,
      'roomId': roomId,
      'shiftDate': date,
      'startTime': startTime,
      'endTime': endTime,
      'expectedRevision': shift.revision,
      'reason': reason,
    },
  );

  factory ScheduleCommand.cancelShift(ManagedShift shift, String reason) =>
      ScheduleCommand._(
        'PUT',
        '/v2/doctor-work-shifts/${Uri.encodeComponent(shift.id)}/cancel',
        {'expectedRevision': shift.revision, 'reason': reason},
      );

  factory ScheduleCommand.sessionFromShift(
    ManagedShift shift, {
    required String startTime,
    required String endTime,
    required List<SessionBreak> breaks,
    required int capacity,
  }) => ScheduleCommand._(
    'POST',
    '/v2/doctor-work-shifts/${Uri.encodeComponent(shift.id)}/consultation-sessions',
    _session(
      doctorId: shift.doctorId,
      departmentId: shift.departmentId,
      roomId: shift.roomId,
      date: shift.date,
      startTime: startTime,
      endTime: endTime,
      breaks: breaks,
      capacity: capacity,
    ),
  );

  factory ScheduleCommand.replaceScope(
    SchedulingScope scope,
    List<String> departmentIds,
    String reason,
  ) => ScheduleCommand._(
    'PUT',
    '/v2/org/accounts/${Uri.encodeComponent(scope.accountId)}/schedule-management-scope',
    {
      'expectedRevision': scope.revision,
      'departmentIds': [...departmentIds]..sort(),
      'reason': reason,
    },
  );

  factory ScheduleCommand.replaceHead(
    HeadAppointment current, {
    required String? staffId,
    required String reason,
  }) => ScheduleCommand._(
    'PUT',
    '/v2/org/departments/${Uri.encodeComponent(current.departmentId)}/head-appointments',
    {
      'staffId': staffId,
      'expectedRevision': current.revision,
      'reason': reason,
    },
  );
}

abstract interface class ScheduleManagementRepository {
  Future<PageResult<ManagedSession>> sessions(
    String departmentId, {
    required String fromDate,
    required String toDate,
    int page = 0,
  });
  Future<PageResult<ManagedShift>> shifts(
    String departmentId, {
    required String fromDate,
    required String toDate,
    int page = 0,
  });
  Future<List<ConsultationRoom>> rooms(String departmentId);
  Future<DepartmentSummary> summary(
    String departmentId, {
    required String fromDate,
    required String toDate,
  });

  /// Departments the signed-in doctor currently heads.
  Future<List<ManagedDepartment>> managementDepartments();
  Future<List<ManagedDepartment>> activeDepartments();
  Future<List<ManagerAccount>> managerAccounts();
  Future<SchedulingScope> scope(String accountId);
  Future<HeadAppointment> headAppointment(String departmentId);
  Future<void> execute(ScheduleCommand command);
}

/// Null when the command never took effect. A lost or server-side failure stays
/// unresolved, so only the same command may follow it.
bool scheduleOutcomeUnknown(Object error) =>
    error is! ApiFailure ||
    error.status == null ||
    error.status! < 400 ||
    error.status == 408 ||
    error.status! >= 500 ||
    error.code == 'IDEMPOTENCY_CONFLICT';

const _scheduleMessages = {
  'SHIFT_HAS_SESSIONS': 'Ca đã có buổi khám đang hoạt động nên chưa thể sửa hoặc hủy. Hãy kiểm tra các buổi khám liên quan trước khi điều chỉnh phân công.',
  'SHIFT_OVERLAP': 'Bác sĩ hoặc phòng đã có ca trùng giờ. Hãy chọn khoảng giờ hoặc phòng khác.',
  'SESSION_OVERLAP': 'Buổi khám trùng giờ với buổi đã lập của bác sĩ hoặc phòng. Hãy kiểm tra lịch và chọn khoảng giờ khác.',
  'SHIFT_SESSION_MISMATCH': 'Buổi khám phải nằm trong ca và giữ đúng bác sĩ, khoa, phòng đã phân công.',
  'SHIFT_INACTIVE': 'Ca đã bị hủy. Hãy tải lại danh sách trước khi thao tác.',
  'REVISION_CONFLICT': 'Dữ liệu đã được cập nhật bởi phiên khác. Hãy tải lại danh sách và kiểm tra thay đổi.',
  'SCHEDULE_INELIGIBLE': 'Bác sĩ, khoa hoặc phòng không còn đáp ứng điều kiện phân công. Hãy kiểm tra lại lựa chọn.',
  'INVALID_REQUEST': 'Thông tin chưa hợp lệ. Hãy kiểm tra ngày, giờ, sức chứa và lý do điều chỉnh.',
  'HEAD_REVISION_CONFLICT':
      'Bổ nhiệm đã thay đổi. Hãy kiểm tra trạng thái mới rồi xác nhận lại.',
  'HEAD_INELIGIBLE': 'Bác sĩ chưa đủ điều kiện bổ nhiệm: cần tài khoản bác sĩ đang hoạt động, thuộc khoa lâm sàng này và chưa có nhiệm kỳ khác.',
};

Object friendlyScheduleError(Object error) {
  if (error is! ApiFailure) return error;
  final message = _scheduleMessages[error.code];
  if (message != null) {
    return ApiFailure(message, status: error.status, code: error.code);
  }
  return error.status == 403
      ? ApiFailure(
          'Bạn không còn quyền quản lý khoa này. Hãy kiểm tra lại bổ nhiệm.',
          status: 403,
          code: error.code,
        )
      : error;
}

/// Mirrors the session limits shown on the web form.
String? validateTimeRange(String startTime, String endTime) {
  final time = RegExp(r'^([01]\d|2[0-3]):[0-5]\d$');
  if (!time.hasMatch(startTime) || !time.hasMatch(endTime)) {
    return 'Nhập giờ theo dạng HH:mm.';
  }
  if (endTime.compareTo(startTime) <= 0) {
    return 'Giờ kết thúc phải sau giờ bắt đầu.';
  }
  return null;
}

String? validateCapacity(String value) {
  final capacity = int.tryParse(value.trim());
  return capacity == null || capacity < 1 || capacity > 200
      ? 'Sức chứa phải từ 1 đến 200.'
      : null;
}
