import '../../../core/network/api_client.dart';
import '../../../core/network/api_failure.dart';
import '../../../core/network/json_reader.dart';
import '../domain/record_collaboration.dart';

class GatewayRecordCollaborationRepository
    implements RecordCollaborationRepository {
  const GatewayRecordCollaborationRepository(this.client);
  final ApiClient client;

  @override
  Future<List<TreatmentCourse>> courses(String patientId) async => _list(
    await _guard(
      () => client.get(
        '/v2/patients/${_id(patientId, 'patientId')}/treatment-courses',
      ),
    ),
    _course,
  );

  @override
  Future<void> openCourse({
    required String patientId,
    required String problem,
    required String openingReason,
    required String recordId,
    required String idempotencyKey,
  }) => _guard(
    () => client.post('/v2/treatment-courses', {
      'patientId': _uuid(patientId, 'patientId'),
      'problem': problem.trim(),
      'openingReason': openingReason.trim(),
      'recordId': _uuid(recordId, 'recordId'),
    }, idempotencyKey: idempotencyKey),
  );

  @override
  Future<void> attachVisit({
    required String courseId,
    required String recordId,
    required String idempotencyKey,
  }) => _guard(
    () => client.post(
      '/v2/treatment-courses/${_id(courseId, 'courseId')}/visits',
      {'recordId': _uuid(recordId, 'recordId')},
      idempotencyKey: idempotencyKey,
    ),
  );

  @override
  Future<void> closeCourse({
    required String courseId,
    required String decision,
    required String note,
    required String idempotencyKey,
  }) => _guard(
    () => client.putIdempotent(
      '/v2/treatment-courses/${_id(courseId, 'courseId')}/closure',
      {'decision': decision, 'note': note.trim()},
      idempotencyKey: idempotencyKey,
    ),
  );

  @override
  Future<List<RecordHandover>> handovers(String recordId) async => _list(
    await _guard(
      () => client.get('/v2/records/${_id(recordId, 'recordId')}/handovers'),
    ),
    _handover,
  );

  @override
  Future<void> requestHandover(
    String recordId, {
    required String toDoctorId,
    required String reason,
    required String pendingItems,
    required String idempotencyKey,
  }) => _guard(
    () => client.post('/v2/records/${_id(recordId, 'recordId')}/handovers', {
      'toDoctorId': _uuid(toDoctorId, 'toDoctorId'),
      'reason': reason.trim(),
      'pendingItems': pendingItems.trim(),
    }, idempotencyKey: idempotencyKey),
  );

  @override
  Future<void> acceptHandover(
    String recordId,
    String handoverId, {
    required String idempotencyKey,
  }) => _guard(
    () => client.putIdempotent(
      '/v2/records/${_id(recordId, 'recordId')}/handovers/'
      '${_id(handoverId, 'handoverId')}/acceptance',
      const <String, Object?>{},
      idempotencyKey: idempotencyKey,
    ),
  );

  @override
  Future<List<ConsultationNote>> notes(String recordId) async => _list(
    await _guard(
      () => client.get(
        '/v2/records/${_id(recordId, 'recordId')}/consultation-notes',
      ),
    ),
    _note,
  );

  @override
  Future<void> addNote(
    String recordId, {
    required String opinion,
    required String idempotencyKey,
  }) => _guard(
    () => client.post(
      '/v2/records/${_id(recordId, 'recordId')}/consultation-notes',
      {'opinion': opinion.trim()},
      idempotencyKey: idempotencyKey,
    ),
  );

  @override
  Future<List<RecordAmendment>> amendments(String recordId) async => _list(
    await _guard(
      () => client.get('/v2/records/${_id(recordId, 'recordId')}/amendments'),
    ),
    _amendment,
  );

  @override
  Future<void> requestAmendment(
    String recordId, {
    required String section,
    required String amendedText,
    required String reason,
    required String idempotencyKey,
  }) => _guard(
    () => client.post('/v2/records/${_id(recordId, 'recordId')}/amendments', {
      'section': section,
      'amendedText': amendedText.trim(),
      'reason': reason.trim(),
    }, idempotencyKey: idempotencyKey),
  );

  @override
  Future<void> decideAmendment(
    String recordId,
    String amendmentId, {
    required String decision,
    required String note,
    required String idempotencyKey,
  }) => _guard(
    () => client.putIdempotent(
      '/v2/records/${_id(recordId, 'recordId')}/amendments/'
      '${_id(amendmentId, 'amendmentId')}/decision',
      {'decision': decision, 'note': note.trim()},
      idempotencyKey: idempotencyKey,
    ),
  );

  @override
  Future<List<RecordAmendment>> pendingAmendments() async => _list(
    await _guard(() => client.get('/v2/amendments/pending')),
    _amendment,
  );

  /// A 404 on these endpoints means the server feature flag is off.
  static Future<Object?> _guard(Future<Object?> Function() call) async {
    try {
      return await call();
    } on ApiFailure catch (error) {
      if (error.status == 404) throw const CollaborationUnavailable();
      rethrow;
    }
  }

  static final _uuidPattern = RegExp(
    r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$',
  );

  static String _uuid(String value, String name) {
    if (!_uuidPattern.hasMatch(value)) {
      throw ArgumentError('$name không hợp lệ.');
    }
    return value;
  }

  static String _id(String value, String name) =>
      Uri.encodeComponent(_uuid(value, name));

  static List<T> _list<T>(
    Object? value,
    T Function(Map<String, dynamic>) parse,
  ) {
    if (value is! List) {
      throw const FormatException('Phản hồi không hợp lệ.');
    }
    return value
        .map((item) {
          if (item is! Map<String, dynamic>) {
            throw const FormatException('Dữ liệu không hợp lệ.');
          }
          return parse(item);
        })
        .toList(growable: false);
  }

  static TreatmentCourse _course(Map<String, dynamic> json) {
    final ids = json['recordIds'];
    if (ids is! List || ids.any((id) => id is! String)) {
      throw const FormatException('Dữ liệu recordIds không hợp lệ.');
    }
    return TreatmentCourse(
      courseId: JsonReader.string(json, 'courseId'),
      patientId: JsonReader.string(json, 'patientId'),
      departmentId: JsonReader.string(json, 'departmentId'),
      problem: JsonReader.string(json, 'problem'),
      openingReason: JsonReader.string(json, 'openingReason'),
      responsibleDoctorId: JsonReader.string(json, 'responsibleDoctorId'),
      status: JsonReader.string(json, 'status'),
      recordIds: List<String>.from(ids),
      openedAt: JsonReader.string(json, 'openedAt'),
      lastVisitDate: JsonReader.string(json, 'lastVisitDate'),
      reviewDue: json['reviewDue'] == true,
      closureDecision: JsonReader.optionalString(json, 'closureDecision'),
      closureNote: JsonReader.optionalString(json, 'closureNote'),
      closedAt: JsonReader.optionalString(json, 'closedAt'),
    );
  }

  static RecordHandover _handover(Map<String, dynamic> json) => RecordHandover(
    handoverId: JsonReader.string(json, 'handoverId'),
    recordId: JsonReader.string(json, 'recordId'),
    fromDoctorId: JsonReader.string(json, 'fromDoctorId'),
    toDoctorId: JsonReader.string(json, 'toDoctorId'),
    reason: JsonReader.string(json, 'reason'),
    pendingItems: JsonReader.string(json, 'pendingItems'),
    status: JsonReader.string(json, 'status'),
    requestedAt: JsonReader.string(json, 'requestedAt'),
    acceptedAt: JsonReader.optionalString(json, 'acceptedAt'),
  );

  static ConsultationNote _note(Map<String, dynamic> json) => ConsultationNote(
    noteId: JsonReader.string(json, 'noteId'),
    recordId: JsonReader.string(json, 'recordId'),
    authorStaffId: JsonReader.string(json, 'authorStaffId'),
    opinion: JsonReader.string(json, 'opinion'),
    createdAt: JsonReader.string(json, 'createdAt'),
  );

  static RecordAmendment _amendment(Map<String, dynamic> json) =>
      RecordAmendment(
        amendmentId: JsonReader.string(json, 'amendmentId'),
        recordId: JsonReader.string(json, 'recordId'),
        section: JsonReader.string(json, 'section'),
        originalText: JsonReader.optionalString(json, 'originalText') ?? '',
        amendedText: JsonReader.string(json, 'amendedText'),
        reason: JsonReader.string(json, 'reason'),
        authorStaffId: JsonReader.string(json, 'authorStaffId'),
        assignedByStaffId: JsonReader.optionalString(
          json,
          'assignedByStaffId',
        ),
        status: JsonReader.string(json, 'status'),
        requestedAt: JsonReader.string(json, 'requestedAt'),
        decidedByStaffId: JsonReader.optionalString(json, 'decidedByStaffId'),
        decisionNote: JsonReader.optionalString(json, 'decisionNote'),
        decidedAt: JsonReader.optionalString(json, 'decidedAt'),
        contentHash: JsonReader.optionalString(json, 'contentHash'),
      );
}
