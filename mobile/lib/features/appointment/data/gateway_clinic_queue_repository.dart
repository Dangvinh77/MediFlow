import '../../../core/network/api_client.dart';
import '../../../core/network/json_reader.dart';
import '../domain/clinic_queue.dart';
import '../domain/reception.dart';

class GatewayClinicQueueRepository implements ClinicQueueRepository {
  const GatewayClinicQueueRepository(this.client);
  final ApiClient client;

  @override
  Future<List<ClinicQueueEntry>> board({
    required String date,
    String? roomId,
  }) async => _list(
    await client.get(
      '/v2/clinic-queue',
      query: {'date': date, 'roomId': ?roomId},
    ),
    _entry,
  );

  @override
  Future<ClinicQueueTicket> issueTicket(
    String registrationId, {
    required String idempotencyKey,
  }) async {
    _requireId(registrationId);
    return _ticket(
      _object(
        await client.post(
          '/v2/outpatient-registrations/$registrationId/room-queue-ticket',
          const <String, dynamic>{},
          idempotencyKey: idempotencyKey,
        ),
      ),
    );
  }

  @override
  Future<ClinicQueueTicket> call(
    String ticketId,
    int expectedRevision, {
    required String idempotencyKey,
  }) async {
    _requireId(ticketId);
    return _ticket(
      _object(
        await client.putIdempotent('/v2/clinic-queue-tickets/$ticketId/call', {
          'expectedRevision': expectedRevision,
        }, idempotencyKey: idempotencyKey),
      ),
    );
  }

  @override
  Future<ClinicQueueTicket> adjust(
    String ticketId,
    int expectedRevision,
    String type,
    String reason, {
    required String idempotencyKey,
  }) async {
    _requireId(ticketId);
    return _ticket(
      _object(
        await client.post('/v2/clinic-queue-tickets/$ticketId/adjustments', {
          'expectedRevision': expectedRevision,
          'type': type,
          'reason': reason,
        }, idempotencyKey: idempotencyKey),
      ),
    );
  }

  @override
  Future<VitalSignRecord> recordVitals(
    String registrationId,
    VitalSignInput input, {
    required String idempotencyKey,
  }) async {
    _requireId(registrationId);
    return _vital(
      _object(
        await client.post(
          '/v2/outpatient-registrations/$registrationId/vital-signs',
          input.toJson(),
          idempotencyKey: idempotencyKey,
        ),
      ),
    );
  }

  @override
  Future<List<VitalSignRecord>> listVitals(String registrationId) async {
    _requireId(registrationId);
    return _list(
      await client.get(
        '/v2/outpatient-registrations/$registrationId/vital-signs',
      ),
      _vital,
    );
  }

  @override
  Future<void> reschedule(
    String registrationId,
    RescheduleInput input, {
    required String idempotencyKey,
  }) async {
    _requireId(registrationId);
    await client.post(
      '/v2/outpatient-registrations/$registrationId/reschedule',
      input.toJson(),
      idempotencyKey: idempotencyKey,
    );
  }

  @override
  Future<ScheduleImpact> sessionImpact(String sessionId) async {
    _requireId(sessionId);
    return _impact(
      _object(await client.get('/v2/consultation-sessions/$sessionId/impact')),
    );
  }

  @override
  Future<ScheduleImpact> shiftImpact(String shiftId) async {
    _requireId(shiftId);
    return _impact(
      _object(await client.get('/v2/doctor-work-shifts/$shiftId/impact')),
    );
  }

  static ClinicQueueEntry _entry(Map<String, dynamic> json) {
    final stage = JsonReader.string(json, 'stage');
    if (!clinicQueueStages.contains(stage)) {
      throw FormatException('Trạng thái hàng đợi không hợp lệ: $stage');
    }
    return ClinicQueueEntry(
      registrationId: JsonReader.string(json, 'registrationId'),
      patientId: JsonReader.string(json, 'patientId'),
      sessionId: JsonReader.string(json, 'sessionId'),
      doctorId: JsonReader.string(json, 'doctorId'),
      departmentId: JsonReader.string(json, 'departmentId'),
      roomId: JsonReader.string(json, 'roomId'),
      queueDate: JsonReader.string(json, 'queueDate'),
      queueNumber: _int(json, 'queueNumber'),
      ticketId: JsonReader.optionalString(json, 'ticketId'),
      ticketRevision: _int(json, 'ticketRevision'),
      roomQueueNumber: _int(json, 'roomQueueNumber'),
      stage: stage,
      priority: json['priority'] == true,
      checkedInAt: JsonReader.optionalString(json, 'checkedInAt'),
      calledAt: JsonReader.optionalString(json, 'calledAt'),
      vitalSignsRecorded: json['vitalSignsRecorded'] == true,
      readyForExam: json['readyForExam'] == true,
      continuationRequired: json['continuationRequired'] == true,
    );
  }

  static ClinicQueueTicket _ticket(Map<String, dynamic> json) =>
      ClinicQueueTicket(
        ticketId: JsonReader.string(json, 'ticketId'),
        revision: _requiredInt(json, 'revision'),
        registrationId: JsonReader.string(json, 'registrationId'),
        roomId: JsonReader.string(json, 'roomId'),
        queueDate: JsonReader.string(json, 'queueDate'),
        roomQueueNumber: _requiredInt(json, 'roomQueueNumber'),
        status: JsonReader.string(json, 'status'),
        priority: json['priority'] == true,
        issuedAt: JsonReader.string(json, 'issuedAt'),
        calledAt: JsonReader.optionalString(json, 'calledAt'),
      );

  static VitalSignRecord _vital(Map<String, dynamic> json) => VitalSignRecord(
    observationId: JsonReader.string(json, 'observationId'),
    registrationId: JsonReader.string(json, 'registrationId'),
    observedAt: JsonReader.string(json, 'observedAt'),
    input: VitalSignInput(
      pulseBpm: _int(json, 'pulseBpm'),
      systolicMmhg: _int(json, 'systolicMmhg'),
      diastolicMmhg: _int(json, 'diastolicMmhg'),
      temperatureC: _double(json, 'temperatureC'),
      respiratoryRate: _int(json, 'respiratoryRate'),
      spo2Percent: _int(json, 'spo2Percent'),
      weightKg: _double(json, 'weightKg'),
      notMeasuredReason: JsonReader.optionalString(json, 'notMeasuredReason'),
    ),
  );

  static ScheduleImpact _impact(Map<String, dynamic> json) => ScheduleImpact(
    sessionIds: [
      for (final id in json['sessionIds'] as List<dynamic>? ?? const [])
        id.toString(),
    ],
    heldCount: _requiredInt(json, 'heldCount'),
    paymentLockedCount: _requiredInt(json, 'paymentLockedCount'),
    confirmedCount: _requiredInt(json, 'confirmedCount'),
    consumedCount: _requiredInt(json, 'consumedCount'),
    registrations: _list(
      json['registrations'] ?? const <Object?>[],
      (row) => AffectedRegistration(
        registrationId: JsonReader.string(row, 'registrationId'),
        patientId: JsonReader.string(row, 'patientId'),
        sessionId: JsonReader.string(row, 'sessionId'),
        status: JsonReader.string(row, 'status'),
        reservationStatus: JsonReader.optionalString(row, 'reservationStatus'),
        checkedInAt: JsonReader.optionalString(row, 'checkedInAt'),
        queueNumber: _int(row, 'queueNumber'),
        roomQueueNumber: _int(row, 'roomQueueNumber'),
      ),
    ),
  );

  static Map<String, dynamic> _object(Object? value) {
    if (value is Map<String, dynamic>) return value;
    throw const FormatException('Phản hồi Gateway không đúng định dạng.');
  }

  static List<T> _list<T>(
    Object? value,
    T Function(Map<String, dynamic>) decode,
  ) {
    if (value is! List) {
      throw const FormatException('Phản hồi Gateway không đúng định dạng.');
    }
    return [for (final item in value) decode(_object(item))];
  }

  static int? _int(Map<String, dynamic> json, String key) {
    final value = json[key];
    if (value == null) return null;
    if (value is num && value == value.toInt()) return value.toInt();
    throw FormatException('Dữ liệu $key không hợp lệ.');
  }

  static int _requiredInt(Map<String, dynamic> json, String key) =>
      _int(json, key) ?? (throw FormatException('Thiếu dữ liệu $key.'));

  static double? _double(Map<String, dynamic> json, String key) {
    final value = json[key];
    if (value == null) return null;
    if (value is num) return value.toDouble();
    throw FormatException('Dữ liệu $key không hợp lệ.');
  }

  static void _requireId(String id) {
    if (!isReceptionId(id)) throw ArgumentError.value(id, 'id');
  }
}
