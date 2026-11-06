import '../../../core/domain/page_result.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/json_reader.dart';
import '../../../core/network/page_decoder.dart';
import '../domain/schedule_management.dart';

class GatewayScheduleManagementRepository
    implements ScheduleManagementRepository {
  const GatewayScheduleManagementRepository(this.client);
  final ApiClient client;

  @override
  Future<PageResult<ManagedSession>> sessions(
    String departmentId, {
    required String fromDate,
    required String toDate,
    int page = 0,
  }) async => decodePage(
    await client.get(
      '/v2/consultation-sessions',
      query: {
        'departmentId': departmentId,
        'fromDate': fromDate,
        'toDate': toDate,
        'page': page,
        'size': 20,
      },
    ),
    _session,
  );

  @override
  Future<PageResult<ManagedShift>> shifts(
    String departmentId, {
    required String fromDate,
    required String toDate,
    int page = 0,
  }) async => decodePage(
    await client.get(
      '/v2/doctor-work-shifts',
      query: {
        'departmentId': departmentId,
        'fromDate': fromDate,
        'toDate': toDate,
        'page': page,
        'size': 20,
      },
    ),
    _shift,
  );

  @override
  Future<List<ConsultationRoom>> rooms(String departmentId) async {
    final rooms = <ConsultationRoom>[];
    var page = 0;
    var pages = 1;
    while (page < pages) {
      final result = decodePage(
        await client.get(
          '/v2/org/consultation-rooms',
          query: {'departmentId': departmentId, 'page': page, 'size': 50},
        ),
        (json) => ConsultationRoom(
          id: JsonReader.string(json, 'roomId'),
          name: JsonReader.string(json, 'roomName'),
        ),
      );
      rooms.addAll(result.items);
      pages = result.totalPages;
      page++;
    }
    return rooms;
  }

  @override
  Future<DepartmentSummary> summary(
    String departmentId, {
    required String fromDate,
    required String toDate,
  }) async {
    final json = _object(
      await client.get(
        '/v2/consultation-sessions/summary',
        query: {
          'departmentId': departmentId,
          'fromDate': fromDate,
          'toDate': toDate,
        },
      ),
    );
    return DepartmentSummary(
      openSessions: _int(json, 'openSessionCount'),
      stoppedSessions: _int(json, 'stoppedSessionCount'),
      capacity: _int(json, 'capacity'),
      available: _int(json, 'availableCount'),
      held: _int(json, 'heldCount'),
      paymentLocked: _int(json, 'paymentLockedCount'),
      confirmed: _int(json, 'confirmedCount'),
      consumed: _int(json, 'consumedCount'),
    );
  }

  @override
  Future<List<ManagedDepartment>> managementDepartments() async {
    final json = _object(await client.get('/v1/org/me/capabilities'));
    final rows = json['managementDepartments'];
    if (rows == null) return const [];
    if (rows is! List) {
      throw const FormatException('Phản hồi quyền quản lý khoa không hợp lệ.');
    }
    return [
      for (final row in rows)
        ManagedDepartment(
          id: JsonReader.string(_object(row), 'departmentId'),
          name: JsonReader.string(_object(row), 'departmentName'),
        ),
    ];
  }

  @override
  Future<List<ManagedDepartment>> activeDepartments() async {
    final rows = await client.get(
      '/v1/org/departments',
      query: {'activeOnly': true},
    );
    if (rows is! List) {
      throw const FormatException('Phản hồi danh sách khoa không hợp lệ.');
    }
    return [
      for (final row in rows)
        ManagedDepartment(
          id: JsonReader.string(_object(row), 'departmentId'),
          name: JsonReader.string(_object(row), 'departmentName'),
        ),
    ];
  }

  @override
  Future<HeadAppointment> headAppointment(String departmentId) async {
    final json = _object(
      await client.get(
        '/v2/org/departments/${Uri.encodeComponent(departmentId)}/head-appointments',
      ),
    );
    if (JsonReader.string(json, 'departmentId') != departmentId) {
      throw const FormatException('Bổ nhiệm trả về không khớp khoa.');
    }
    final history = json['history'];
    return HeadAppointment(
      departmentId: departmentId,
      revision: _int(json, 'revision'),
      staffId: JsonReader.optionalString(json, 'staffId'),
      history: [
        if (history is List)
          for (final row in history)
            HeadAppointmentTerm(
              staffId: JsonReader.string(_object(row), 'staffId'),
              startedAt: JsonReader.string(_object(row), 'startedAt'),
              endedAt: JsonReader.optionalString(_object(row), 'endedAt'),
            ),
      ],
    );
  }

  @override
  Future<void> execute(ScheduleCommand command) async {
    if (command.method == 'POST') {
      await client.post(
        command.path,
        command.body,
        idempotencyKey: command.idempotencyKey,
        correlationId: command.correlationId,
      );
    } else {
      await client.putIdempotent(
        command.path,
        command.body,
        idempotencyKey: command.idempotencyKey,
        correlationId: command.correlationId,
      );
    }
  }

  static ManagedSession _session(Map<String, dynamic> json) {
    final breaks = json['breaks'];
    return ManagedSession(
      id: JsonReader.string(json, 'sessionId'),
      revision: _int(json, 'revision'),
      doctorId: JsonReader.string(json, 'doctorId'),
      departmentId: JsonReader.string(json, 'departmentId'),
      roomId: JsonReader.string(json, 'roomId'),
      date: JsonReader.string(json, 'sessionDate'),
      startTime: JsonReader.string(json, 'startTime'),
      endTime: JsonReader.string(json, 'endTime'),
      breaks: [
        if (breaks is List)
          for (final row in breaks)
            SessionBreak(
              startTime: JsonReader.string(_object(row), 'startTime'),
              endTime: JsonReader.string(_object(row), 'endTime'),
            ),
      ],
      capacity: _int(json, 'capacity'),
      status: JsonReader.string(json, 'status'),
      confirmedCount: _int(json, 'confirmedCount'),
      availableCount: _int(json, 'availableCount'),
    );
  }

  static ManagedShift _shift(Map<String, dynamic> json) => ManagedShift(
    id: JsonReader.string(json, 'shiftId'),
    revision: _int(json, 'revision'),
    doctorId: JsonReader.string(json, 'doctorId'),
    departmentId: JsonReader.string(json, 'departmentId'),
    roomId: JsonReader.string(json, 'roomId'),
    date: JsonReader.string(json, 'shiftDate'),
    startTime: JsonReader.string(json, 'startTime'),
    endTime: JsonReader.string(json, 'endTime'),
    status: JsonReader.string(json, 'status'),
  );

  static Map<String, dynamic> _object(Object? value) {
    if (value is! Map<String, dynamic>) {
      throw const FormatException('Phản hồi quản lý lịch không hợp lệ.');
    }
    return value;
  }

  static int _int(Map<String, dynamic> json, String key) {
    final value = json[key];
    if (value is num && value == value.toInt()) return value.toInt();
    throw FormatException('Dữ liệu $key không hợp lệ.');
  }
}
