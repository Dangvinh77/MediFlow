import '../../../core/domain/page_result.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/page_decoder.dart';
import '../domain/reception.dart';

class GatewayReceptionRepository implements ReceptionRepository {
  const GatewayReceptionRepository(this.client);
  final ApiClient client;
  @override
  Future<PageResult<ReceptionSession>> sessions({
    required String departmentId,
    required String fromDate,
    required String toDate,
    int page = 0,
  }) async {
    if (!isReceptionId(departmentId) || page < 0) {
      throw ArgumentError('Chọn khoa hợp lệ.');
    }
    final from = DateTime.tryParse(fromDate), to = DateTime.tryParse(toDate);
    if (from == null ||
        to == null ||
        from.toIso8601String().substring(0, 10) != fromDate ||
        to.toIso8601String().substring(0, 10) != toDate ||
        to.isBefore(from) ||
        to.difference(from).inDays > 30) {
      throw ArgumentError('Khoảng ngày không hợp lệ hoặc vượt quá 30 ngày.');
    }
    return decodePage(
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
      (json) {
        final result = ReceptionSession(
          id: _id(json, 'sessionId'),
          revision: _number(json, 'revision', 1),
          departmentId: _id(json, 'departmentId'),
          doctorId: _id(json, 'doctorId'),
          roomId: _id(json, 'roomId'),
          date: _text(json, 'sessionDate'),
          startTime: _text(json, 'startTime'),
          endTime: _text(json, 'endTime'),
          capacity: _number(json, 'capacity', 1),
          availableCount: _number(json, 'availableCount', 0),
          status: _text(json, 'status'),
        );
        if (result.departmentId != departmentId ||
            result.date.compareTo(fromDate) < 0 ||
            result.date.compareTo(toDate) > 0 ||
            result.availableCount > result.capacity ||
            !const ['OPEN', 'STOPPED', 'CANCELLED'].contains(result.status)) {
          throw const FormatException('Buổi khám không khớp bộ lọc.');
        }
        return result;
      },
    );
  }

  @override
  Future<PageResult<ReceptionFollowUp>> followUpSources(
    String patientId, {
    int page = 0,
  }) async {
    if (!isReceptionId(patientId) || page < 0) {
      throw ArgumentError('Chọn bệnh nhân hợp lệ.');
    }
    return decodePage(
      await client.get(
        '/v2/outpatient-registrations/follow-up-sources',
        query: {'patientId': patientId, 'page': page, 'size': 20},
      ),
      (json) {
        final raw = json['recommendation'];
        final rec = raw == null ? null : _object(raw);
        final result = ReceptionFollowUp(
          registrationId: _id(json, 'registrationId'),
          patientId: _id(json, 'patientId'),
          departmentId: _id(json, 'departmentId'),
          doctorId: _id(json, 'doctorId'),
          completedAt: _text(json, 'completedAt'),
          recommendationId: rec == null ? null : _id(rec, 'recommendationId'),
          recommendedFrom: rec == null ? null : _text(rec, 'recommendedFrom'),
          recommendedThrough: rec == null
              ? null
              : _text(rec, 'recommendedThrough'),
          reason: rec == null ? null : _text(rec, 'reason'),
        );
        if (result.patientId != patientId) {
          throw const FormatException('Lượt khám không khớp bệnh nhân.');
        }
        if (rec != null &&
            (_id(rec, 'patientId') != patientId ||
                _id(rec, 'departmentId') != result.departmentId ||
                _id(rec, 'sourceRegistrationId') != result.registrationId ||
                _id(rec, 'preferredDoctorId') != result.doctorId ||
                !_validDay(result.recommendedFrom!) ||
                !_validDay(result.recommendedThrough!) ||
                result.recommendedThrough!.compareTo(result.recommendedFrom!) <
                    0)) {
          throw const FormatException(
            'Đề xuất tái khám không khớp lượt khám trước.',
          );
        }
        return result;
      },
    );
  }

  @override
  Future<ReceptionReceipt> register(ReceptionIntent intent) async {
    final issue = intent.command.validate();
    if (issue != null) throw ArgumentError(issue);
    final json = _object(
      await client.post(
        '/v2/outpatient-registrations',
        intent.command.toJson(),
        idempotencyKey: intent.key,
        correlationId: intent.correlationId,
      ),
    );
    final result = ReceptionReceipt(
      id: _id(json, 'registrationId'),
      patientId: _id(json, 'patientId'),
      sessionId: _id(json, 'sessionId'),
      doctorId: _id(json, 'doctorId'),
      status: _text(json, 'status'),
      expiresAt: _text(json, 'reservationExpiresAt'),
      queueNumber: json['queueNumber'] == null
          ? null
          : _number(json, 'queueNumber', 1),
    );
    if (result.patientId != intent.command.patientId ||
        result.sessionId != intent.command.session.id ||
        result.doctorId != intent.command.session.doctorId ||
        !const [
          'AWAITING_PAYMENT',
          'CONFIRMED',
          'IN_EXAM',
          'COMPLETED',
          'EXPIRED',
          'CANCELLED',
          'RECONCILIATION_REQUIRED',
        ].contains(result.status)) {
      throw const FormatException(
        'Phiếu trả về không khớp yêu cầu. Đối soát cùng yêu cầu trước khi tiếp tục.',
      );
    }
    return result;
  }
}

bool _validDay(String value) {
  final date = DateTime.tryParse(value);
  return date != null && date.toIso8601String().substring(0, 10) == value;
}

Map<String, dynamic> _object(Object? value) {
  if (value is! Map<String, dynamic>) {
    throw const FormatException('Phản hồi tiếp nhận không hợp lệ.');
  }
  return value;
}

String _text(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value is! String || value.trim().isEmpty) {
    throw FormatException('Thiếu trường $key.');
  }
  return value;
}

String _id(Map<String, dynamic> json, String key) {
  final value = _text(json, key);
  if (!isReceptionId(value)) throw FormatException('Mã $key không hợp lệ.');
  return value;
}

int _number(Map<String, dynamic> json, String key, int min) {
  final value = json[key];
  if (value is! int || value < min) {
    throw FormatException('Trường $key không hợp lệ.');
  }
  return value;
}
