import '../../../core/domain/follow_up_recommendation.dart';
import '../../../core/domain/page_result.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/json_reader.dart';
import '../../../core/network/page_decoder.dart';
import '../domain/outpatient_schedule.dart';
import '../domain/reception.dart';

class GatewayOutpatientScheduleRepository
    implements OutpatientScheduleRepository {
  const GatewayOutpatientScheduleRepository(this.client);
  final ApiClient client;

  @override
  Future<DoctorCalendar> myCalendar({String? date, String? weekStart}) async {
    if ((date == null) == (weekStart == null)) {
      throw ArgumentError('Chỉ chọn một ngày hoặc ngày đầu tuần.');
    }
    final json = _object(
      await client.get(
        '/v2/consultation-sessions/mine',
        query: {'date': ?date, 'weekStart': ?weekStart},
      ),
    );
    final sessions = json['sessions'];
    if (sessions is! List) {
      throw const FormatException('Phản hồi lịch khám không hợp lệ.');
    }
    return DoctorCalendar(
      fromDate: JsonReader.string(json, 'fromDate'),
      toDate: JsonReader.string(json, 'toDate'),
      sessions: [for (final row in sessions) _session(_object(row))],
    );
  }

  @override
  Future<List<DoctorWorkShift>> myWorkShifts(
    String fromDate,
    String toDate,
  ) async {
    final value = await client.get(
      '/v2/doctor-work-shifts/mine',
      query: {'fromDate': fromDate, 'toDate': toDate},
    );
    if (value is! List) {
      throw const FormatException('Phản hồi lịch làm việc không hợp lệ.');
    }
    return [
      for (final row in value)
        () {
          final json = _object(row);
          return DoctorWorkShift(
            id: JsonReader.string(json, 'shiftId'),
            departmentId: JsonReader.string(json, 'departmentId'),
            roomId: JsonReader.string(json, 'roomId'),
            date: JsonReader.string(json, 'shiftDate'),
            startTime: JsonReader.string(json, 'startTime'),
            endTime: JsonReader.string(json, 'endTime'),
            status: JsonReader.string(json, 'status'),
          );
        }(),
    ];
  }

  @override
  Future<PageResult<RegistrationRow>> sessionRegistrations(
    String sessionId, {
    int page = 0,
  }) async {
    _requireId(sessionId);
    if (page < 0) throw ArgumentError.value(page, 'page');
    return decodePage(
      await client.get(
        '/v2/consultation-sessions/${Uri.encodeComponent(sessionId)}/registrations',
        query: {'page': page, 'size': 20},
      ),
      (json) => RegistrationRow(
        id: JsonReader.string(json, 'registrationId'),
        patientId: JsonReader.string(json, 'patientId'),
        visitType: JsonReader.string(json, 'visitType'),
        status: JsonReader.string(json, 'status'),
        readyForExam: json['readyForExam'] == true,
        checkedInAt: JsonReader.optionalString(json, 'checkedInAt'),
        queueNumber: _optionalInt(json, 'queueNumber'),
        roomQueueNumber: _optionalInt(json, 'roomQueueNumber'),
      ),
    );
  }

  @override
  Future<OutpatientRegistration> registration(String registrationId) async {
    _requireId(registrationId);
    return _matching(
      _registration(
        _object(
          await client.get(
            '/v2/outpatient-registrations/${Uri.encodeComponent(registrationId)}',
          ),
        ),
      ),
      registrationId,
    );
  }

  @override
  Future<OutpatientRegistration> markArrival(
    String registrationId, {
    required int expectedRevision,
    required String idempotencyKey,
    required String correlationId,
  }) async {
    _requireId(registrationId);
    return _matching(
      _registration(
        _object(
          await client.putIdempotent(
            '/v2/outpatient-registrations/${Uri.encodeComponent(registrationId)}/arrival',
            {'expectedRevision': expectedRevision},
            idempotencyKey: idempotencyKey,
            correlationId: correlationId,
          ),
        ),
      ),
      registrationId,
    );
  }

  @override
  Future<PageResult<OutpatientRegistration>> myRegistrations({
    int page = 0,
  }) async {
    if (page < 0) throw ArgumentError.value(page, 'page');
    return decodePage(
      await client.get(
        '/v2/outpatient-registrations/mine',
        query: {'page': page, 'size': 10},
      ),
      _registration,
    );
  }

  static OutpatientRegistration _matching(
    OutpatientRegistration result,
    String registrationId,
  ) {
    if (result.id.toLowerCase() != registrationId.toLowerCase()) {
      throw const FormatException('Phiếu trả về không khớp yêu cầu.');
    }
    return result;
  }

  static ScheduleSession _session(Map<String, dynamic> json) => ScheduleSession(
    id: JsonReader.string(json, 'sessionId'),
    departmentId: JsonReader.string(json, 'departmentId'),
    roomId: JsonReader.string(json, 'roomId'),
    date: JsonReader.string(json, 'sessionDate'),
    startTime: JsonReader.string(json, 'startTime'),
    endTime: JsonReader.string(json, 'endTime'),
    status: JsonReader.string(json, 'status'),
    confirmedCount: _int(json, 'confirmedCount'),
    availableCount: _int(json, 'availableCount'),
  );

  static OutpatientRegistration _registration(Map<String, dynamic> json) {
    final followUp = json['followUpRecommendation'];
    return OutpatientRegistration(
      id: JsonReader.string(json, 'registrationId'),
      revision: _int(json, 'revision'),
      doctorId: JsonReader.string(json, 'doctorId'),
      departmentId: JsonReader.string(json, 'departmentId'),
      roomId: JsonReader.string(json, 'roomId'),
      sessionDate: JsonReader.string(json, 'sessionDate'),
      startTime: JsonReader.string(json, 'startTime'),
      endTime: JsonReader.string(json, 'endTime'),
      receptionMode: JsonReader.string(json, 'receptionMode'),
      visitType: JsonReader.string(json, 'visitType'),
      status: JsonReader.string(json, 'status'),
      reservationExpiresAt: JsonReader.string(json, 'reservationExpiresAt'),
      checkedInAt: JsonReader.optionalString(json, 'checkedInAt'),
      queueNumber: _optionalInt(json, 'queueNumber'),
      roomQueueNumber: _optionalInt(json, 'roomQueueNumber'),
      followUp: followUp is Map<String, dynamic>
          ? FollowUpRecommendation.fromJson(followUp)
          : null,
    );
  }

  static Map<String, dynamic> _object(Object? value) {
    if (value is! Map<String, dynamic>) {
      throw const FormatException('Phản hồi lịch khám không hợp lệ.');
    }
    return value;
  }

  static int _int(Map<String, dynamic> json, String key) {
    final value = _optionalInt(json, key);
    if (value == null) {
      throw FormatException('Dữ liệu $key không hợp lệ.');
    }
    return value;
  }

  static int? _optionalInt(Map<String, dynamic> json, String key) {
    final value = json[key];
    if (value == null) return null;
    if (value is num && value == value.toInt()) return value.toInt();
    throw FormatException('Dữ liệu $key không hợp lệ.');
  }

  static void _requireId(String id) {
    if (!isReceptionId(id)) throw ArgumentError.value(id, 'id');
  }
}
