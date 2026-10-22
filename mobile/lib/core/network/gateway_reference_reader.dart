import '../domain/reference.dart';
import 'api_client.dart';

class GatewayReferenceReader implements ReferenceReader {
  const GatewayReferenceReader(this.client);
  final ApiClient client;

  static (String, String, String) _fields(ReferenceKind kind) => switch (kind) {
    ReferenceKind.patient => ('patients', 'maBenhNhan', 'hoTen'),
    ReferenceKind.staff ||
    ReferenceKind.doctor => ('org/staff', 'staffId', 'fullName'),
    ReferenceKind.department => (
      'org/departments',
      'departmentId',
      'departmentName',
    ),
    ReferenceKind.appointment => (
      'appointments',
      'appointmentId',
      'appointmentDate',
    ),
    ReferenceKind.record => ('records', 'recordId', 'examinationDate'),
  };

  ReferenceOption _decode(ReferenceKind kind, Object? raw) {
    final (_, idKey, nameKey) = _fields(kind);
    if (raw is! Map ||
        raw[idKey] is! String ||
        !isReferenceId(raw[idKey] as String) ||
        raw[nameKey] is! String ||
        (raw[nameKey] as String).trim().isEmpty) {
      throw const FormatException('Thông tin tên không hợp lệ.');
    }
    final String name = switch (kind) {
      ReferenceKind.appointment =>
        'Lịch hẹn ${raw[nameKey]} · ${raw['appointmentTime'] ?? ''}',
      ReferenceKind.record => 'Hồ sơ ${raw[nameKey]}',
      _ => raw[nameKey] as String,
    };
    return ReferenceOption(
      id: raw[idKey] as String,
      name: name,
      detail: switch (kind) {
        ReferenceKind.patient => raw['ngaySinh'] as String?,
        ReferenceKind.staff || ReferenceKind.doctor =>
          raw['specialization'] as String? ?? raw['jobTitle'] as String?,
        ReferenceKind.department => raw['abbreviation'] as String?,
        ReferenceKind.appointment => raw['reason'] as String?,
        ReferenceKind.record => raw['symptoms'] as String?,
      },
    );
  }

  @override
  Future<ReferenceOption> read(ReferenceKind kind, String id) async {
    if (!isReferenceId(id)) throw ArgumentError('Định danh không hợp lệ.');
    final (path, _, _) = _fields(kind);
    final result = _decode(kind, await client.get('/v1/$path/$id'));
    if (result.id.toLowerCase() != id.toLowerCase()) {
      throw const FormatException('Tên không khớp định danh yêu cầu.');
    }
    return result;
  }

  @override
  Future<ReferencePage> search(
    ReferenceKind kind, {
    String query = '',
    int page = 0,
    String? scope,
  }) async {
    if (page < 0 || (scope != null && !isReferenceId(scope))) {
      throw ArgumentError('Bộ lọc không hợp lệ.');
    }
    final (path, _, _) = _fields(kind);
    if (kind == ReferenceKind.record && scope == null) {
      throw ArgumentError('Chọn bệnh nhân trước khi tìm hồ sơ.');
    }
    final raw = await client.get(
      kind == ReferenceKind.record ? '/v1/records/patient/$scope' : '/v1/$path',
      query: switch (kind) {
        ReferenceKind.department => {'activeOnly': true},
        ReferenceKind.record => null,
        _ => {
          'page': page,
          'size': 20,
          if (kind == ReferenceKind.patient && query.trim().isNotEmpty)
            'keyword': query.trim(),
          if (kind == ReferenceKind.doctor) 'jobTitle': 'DOCTOR',
          if ((kind == ReferenceKind.staff || kind == ReferenceKind.doctor) &&
              scope != null)
            'departmentId': scope,
        },
      },
    );
    final list = raw is List
        ? raw
        : raw is Map && raw['content'] is List
        ? raw['content'] as List
        : null;
    if (list == null) {
      throw const FormatException('Danh sách tra cứu không hợp lệ.');
    }
    final items = list
        .where((row) => row is Map && row['active'] != false)
        .where(
          (row) =>
              kind != ReferenceKind.appointment ||
              scope == null ||
              (row as Map)['patientId'] == scope,
        )
        .map((row) => _decode(kind, row))
        .where(
          (item) =>
              kind == ReferenceKind.patient ||
              '${item.name} ${item.detail ?? ''}'.toLowerCase().contains(
                query.trim().toLowerCase(),
              ),
        )
        .toList();
    if (raw is List) {
      return ReferencePage(
        items.skip(page * 20).take(20).toList(),
        hasMore: items.length > (page + 1) * 20,
      );
    }
    if (raw is! Map || raw['number'] is! int || raw['totalPages'] is! int) {
      throw const FormatException('Trang tra cứu không hợp lệ.');
    }
    return ReferencePage(
      items,
      hasMore: (raw['number'] as int) + 1 < (raw['totalPages'] as int),
    );
  }
}
