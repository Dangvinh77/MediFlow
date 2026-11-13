import '../../../core/network/json_reader.dart';
import '../domain/organization.dart';

abstract final class OrganizationDto {
  static Department department(Object? value) {
    final json = _object(value, 'khoa phòng');
    final type = JsonReader.string(json, 'departmentType');
    if (!departmentTypes.contains(type)) {
      throw const FormatException('Loại khoa phòng không hợp lệ.');
    }
    final active = json['active'];
    if (active is! bool) {
      throw const FormatException('Trạng thái khoa phòng không hợp lệ.');
    }
    return Department(
      id: JsonReader.string(json, 'departmentId'),
      name: JsonReader.string(json, 'departmentName'),
      abbreviation: JsonReader.string(json, 'abbreviation'),
      type: type,
      headId: JsonReader.optionalString(json, 'departmentHeadId'),
      location: JsonReader.optionalString(json, 'location'),
      active: active,
      createdAt: _instant(json, 'createdAt'),
      updatedAt: _instant(json, 'updatedAt'),
    );
  }

  static Staff staff(Object? value) {
    final json = _object(value, 'nhân viên');
    final title = JsonReader.string(json, 'jobTitle');
    if (!staffJobTitles.contains(title)) {
      throw const FormatException('Chức danh không hợp lệ.');
    }
    final active = json['active'];
    if (active is! bool) {
      throw const FormatException('Trạng thái nhân viên không hợp lệ.');
    }
    return Staff(
      id: JsonReader.string(json, 'staffId'),
      name: JsonReader.string(json, 'fullName'),
      departmentId: JsonReader.string(json, 'departmentId'),
      jobTitle: title,
      specialization: JsonReader.optionalString(json, 'specialization'),
      licenseNumber: JsonReader.optionalString(json, 'licenseNumber'),
      phoneNumber: JsonReader.optionalString(json, 'phoneNumber'),
      email: JsonReader.optionalString(json, 'email'),
      active: active,
      createdAt: _instant(json, 'createdAt'),
      updatedAt: _instant(json, 'updatedAt'),
    );
  }

  static Room room(Object? value) {
    final json = _object(value, 'phòng làm việc');
    final active = json['active'];
    if (active is! bool) {
      throw const FormatException('Trạng thái phòng làm việc không hợp lệ.');
    }
    return Room(
      id: JsonReader.string(json, 'roomId'),
      departmentId: JsonReader.string(json, 'departmentId'),
      roomType: JsonReader.string(json, 'roomType'),
      roomNumber: JsonReader.string(json, 'roomNumber'),
      roomName: JsonReader.string(json, 'roomName'),
      zone: JsonReader.string(json, 'zone'),
      location: JsonReader.string(json, 'location'),
      active: active,
    );
  }

  static RoomType roomType(Object? value) {
    final json = _object(value, 'loại phòng');
    final departmentType = JsonReader.string(json, 'departmentType');
    if (!departmentTypes.contains(departmentType)) {
      throw const FormatException('Khối của loại phòng không hợp lệ.');
    }
    bool flag(String key) {
      final flag = json[key];
      if (flag is! bool) throw FormatException('Dữ liệu $key không hợp lệ.');
      return flag;
    }

    final count = json['roomCount'];
    if (count is! int || count < 0) {
      throw const FormatException('Số phòng đang dùng không hợp lệ.');
    }
    return RoomType(
      code: JsonReader.string(json, 'code'),
      name: JsonReader.string(json, 'name'),
      description: JsonReader.optionalString(json, 'description'),
      departmentType: departmentType,
      singlePerDepartment: flag('singlePerDepartment'),
      schedulable: flag('schedulable'),
      systemDefined: flag('systemDefined'),
      roomCount: count,
    );
  }

  static Account account(Object? value) {
    final json = _object(value, 'tài khoản');
    final active = json['isActive'];
    if (active is! bool) {
      throw const FormatException('Trạng thái tài khoản không hợp lệ.');
    }
    return Account(
      id: JsonReader.string(json, 'accountId'),
      username: JsonReader.string(json, 'username'),
      staffId: JsonReader.optionalString(json, 'staffId'),
      patientId: JsonReader.optionalString(json, 'patientId'),
      role: JsonReader.string(json, 'role'),
      active: active,
      lastLoginAt: JsonReader.optionalString(json, 'lastLoginAt'),
      createdAt: _instant(json, 'createdAt'),
      updatedAt: _instant(json, 'updatedAt'),
    );
  }

  static Map<String, Object?> createDepartmentBody(DepartmentDraft draft) => {
    'departmentName': draft.name.trim(),
    'abbreviation': draft.abbreviation.trim(),
    'departmentType': draft.type,
    'location': _nullable(draft.location),
  };

  static Map<String, Object?> updateDepartmentBody(DepartmentDraft draft) => {
    'departmentName': draft.name.trim(),
    'departmentType': draft.type,
    'location': _nullable(draft.location),
    'departmentHeadId': _nullable(draft.headId),
    'active': draft.active,
  };

  static Map<String, Object?> createStaffBody(StaffDraft draft) => {
    'fullName': draft.name.trim(),
    'departmentId': draft.departmentId,
    'jobTitle': draft.jobTitle,
    'specialization': _nullable(draft.specialization),
    'licenseNumber': _nullable(draft.licenseNumber),
    'phoneNumber': _nullable(draft.phoneNumber),
    'email': _normalizedEmail(draft.email),
  };

  static Map<String, Object?> updateStaffBody(StaffDraft draft) => {
    'fullName': draft.name.trim(),
    'jobTitle': draft.jobTitle,
    'specialization': _nullable(draft.specialization),
    'licenseNumber': _nullable(draft.licenseNumber),
    'phoneNumber': _nullable(draft.phoneNumber),
    'email': _normalizedEmail(draft.email),
  };

  static Map<String, Object?> createAccountBody(AccountDraft draft) => {
    'username': draft.username.trim(),
    'staffId': draft.role == 'PATIENT' ? null : draft.ownerId.trim(),
    'patientId': draft.role == 'PATIENT' ? draft.ownerId.trim() : null,
    'role': draft.role,
  };

  static Map<String, Object?> accountStatusBody(bool active) => {
    'isActive': active,
  };

  static Map<String, dynamic> _object(Object? value, String label) {
    if (value is! Map<String, dynamic>) {
      throw FormatException('Phản hồi $label không hợp lệ.');
    }
    return value;
  }

  static String _instant(Map<String, dynamic> json, String key) {
    final value = JsonReader.string(json, key);
    if (DateTime.tryParse(value) == null) {
      throw FormatException('Dữ liệu $key không hợp lệ.');
    }
    return value;
  }

  static String? _nullable(String? value) {
    final trimmed = value?.trim();
    return trimmed == null || trimmed.isEmpty ? null : trimmed;
  }

  static String? _normalizedEmail(String? value) {
    final trimmed = value?.trim();
    return trimmed == null || trimmed.isEmpty ? null : trimmed.toLowerCase();
  }
}
