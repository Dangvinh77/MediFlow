import '../../../core/network/json_reader.dart';
import '../domain/self_profile.dart';

abstract final class SelfProfileDto {
  static StaffSelfProfile staff(Object? value) {
    final json = _object(value, 'nhân viên');
    final jobTitle = JsonReader.string(json, 'jobTitle');
    if (!const {
      'DOCTOR',
      'NURSE',
      'TECHNICIAN',
      'PHARMACIST',
      'CASHIER',
      'ADMINISTRATIVE',
    }.contains(jobTitle)) {
      throw const FormatException('Dữ liệu jobTitle không hợp lệ.');
    }
    final active = json['active'];
    if (active is! bool) {
      throw const FormatException('Dữ liệu active không hợp lệ.');
    }
    final gender = JsonReader.optionalString(json, 'gender');
    if (gender != null && !const {'MALE', 'FEMALE', 'OTHER'}.contains(gender)) {
      throw const FormatException('Dữ liệu gender không hợp lệ.');
    }
    final dateOfBirth = _optionalDate(json, 'dateOfBirth');
    final hireDate = _optionalDate(json, 'hireDate');
    return StaffSelfProfile(
      id: JsonReader.string(json, 'staffId'),
      fullName: JsonReader.string(json, 'fullName'),
      identityNumber: JsonReader.optionalString(json, 'identityNumber'),
      dateOfBirth: dateOfBirth,
      gender: gender,
      address: JsonReader.optionalString(json, 'address'),
      hireDate: hireDate,
      qualification: JsonReader.optionalString(json, 'qualification'),
      departmentId: JsonReader.string(json, 'departmentId'),
      jobTitle: jobTitle,
      specialization: JsonReader.optionalString(json, 'specialization'),
      licenseNumber: JsonReader.optionalString(json, 'licenseNumber'),
      phoneNumber: JsonReader.optionalString(json, 'phoneNumber'),
      email: JsonReader.optionalString(json, 'email'),
      active: active,
    );
  }

  static PatientSelfProfile patient(Object? value) {
    final json = _object(value, 'bệnh nhân');
    final gender = JsonReader.string(json, 'gioiTinh');
    if (!const {'M', 'F'}.contains(gender)) {
      throw const FormatException('Dữ liệu gioiTinh không hợp lệ.');
    }
    _date(JsonReader.string(json, 'ngaySinh'), 'ngaySinh');
    if (json['daLuuTru'] is! bool) {
      throw const FormatException('Dữ liệu daLuuTru không hợp lệ.');
    }
    JsonReader.string(json, 'createdAt');
    JsonReader.string(json, 'updatedAt');
    return PatientSelfProfile(
      id: JsonReader.string(json, 'maBenhNhan'),
      fullName: JsonReader.string(json, 'hoTen'),
      dateOfBirth: JsonReader.string(json, 'ngaySinh'),
      gender: gender,
      identityNumber: JsonReader.string(json, 'soCmnd'),
      insuranceNumber: JsonReader.optionalString(json, 'bhytSo'),
      phoneNumber: JsonReader.optionalString(json, 'soDienThoai'),
      email: JsonReader.optionalString(json, 'email'),
      address: JsonReader.optionalString(json, 'diaChi'),
    );
  }

  static String staffDepartment(Object? value) {
    final json = _object(value, 'đơn vị công tác');
    _requireId(JsonReader.string(json, 'departmentId'));
    return JsonReader.string(json, 'departmentName');
  }

  static Map<String, Object?> ownStaffContactBody(StaffContactDraft draft) => {
    'phoneNumber': draft.phoneNumber,
    'address': draft.address,
  };

  static Map<String, dynamic> _object(Object? value, String label) {
    if (value is! Map<String, dynamic>) {
      throw FormatException('Phản hồi $label không hợp lệ.');
    }
    return value;
  }

  static String? _optionalDate(Map<String, dynamic> json, String key) {
    final value = JsonReader.optionalString(json, key);
    if (value != null) _date(value, key);
    return value;
  }

  static void _date(String value, String key) {
    if (DateTime.tryParse(value) == null) {
      throw FormatException('Dữ liệu $key không hợp lệ.');
    }
  }

  static void _requireId(String id) {
    if (!RegExp(
      r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$',
    ).hasMatch(id)) {
      throw const FormatException('Mã hồ sơ trả về không hợp lệ.');
    }
  }
}
