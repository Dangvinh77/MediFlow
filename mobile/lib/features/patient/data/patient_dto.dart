import '../../../core/network/json_reader.dart';
import '../domain/patient.dart';

abstract final class PatientDto {
  static Patient fromJson(Map<String, dynamic> json) {
    final gender = JsonReader.string(json, 'gioiTinh');
    if (!const {'M', 'F'}.contains(gender)) {
      throw const FormatException('Dữ liệu gioiTinh không hợp lệ.');
    }
    final birthDate = JsonReader.string(json, 'ngaySinh');
    if (DateTime.tryParse(birthDate) == null) {
      throw const FormatException('Dữ liệu ngaySinh không hợp lệ.');
    }
    return Patient(
      id: JsonReader.string(json, 'maBenhNhan'),
      name: JsonReader.string(json, 'hoTen'),
      birthDate: birthDate,
      gender: gender,
      identityNumber: JsonReader.string(json, 'soCmnd'),
      address: JsonReader.optionalString(json, 'diaChi'),
      phoneNumber: JsonReader.optionalString(json, 'soDienThoai'),
      email: JsonReader.optionalString(json, 'email'),
      insuranceNumber: JsonReader.optionalString(json, 'bhytSo'),
      createdAt: JsonReader.string(json, 'createdAt'),
      updatedAt: JsonReader.string(json, 'updatedAt'),
    );
  }

  static Map<String, Object?> createBody(PatientDraft draft) => {
    'hoTen': draft.name.trim(),
    'ngaySinh': draft.birthDate,
    'gioiTinh': draft.gender,
    'soCmnd': draft.identityNumber?.trim(),
    'diaChi': _nullable(draft.address),
    'soDienThoai': _nullable(draft.phoneNumber),
    'email': _nullable(draft.email),
    'bhytSo': _nullable(draft.insuranceNumber),
  };

  // Identity number is deliberately absent from UpdatePatientRequest.
  static Map<String, Object?> updateBody(PatientDraft draft) => {
    'hoTen': draft.name.trim(),
    'ngaySinh': draft.birthDate,
    'gioiTinh': draft.gender,
    'diaChi': _nullable(draft.address),
    'soDienThoai': _nullable(draft.phoneNumber),
    'email': _nullable(draft.email),
    'bhytSo': _nullable(draft.insuranceNumber),
  };

  static String? _nullable(String? value) {
    final trimmed = value?.trim();
    return trimmed == null || trimmed.isEmpty ? null : trimmed;
  }
}
