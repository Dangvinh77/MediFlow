import '../../../core/domain/page_result.dart';

class Patient {
  const Patient({
    required this.id,
    required this.name,
    required this.birthDate,
    required this.gender,
    required this.identityNumber,
    required this.address,
    required this.phoneNumber,
    required this.email,
    required this.insuranceNumber,
    required this.createdAt,
    required this.updatedAt,
  });

  final String id;
  final String name;
  final String birthDate;
  final String gender;
  final String identityNumber;
  final String? address;
  final String? phoneNumber;
  final String? email;
  final String? insuranceNumber;
  final String createdAt;
  final String updatedAt;
}

class PatientDraft {
  const PatientDraft({
    required this.name,
    required this.birthDate,
    required this.gender,
    this.identityNumber,
    this.address,
    this.phoneNumber,
    this.email,
    this.insuranceNumber,
  });

  final String name;
  final String birthDate;
  final String gender;
  final String? identityNumber;
  final String? address;
  final String? phoneNumber;
  final String? email;
  final String? insuranceNumber;
}

abstract interface class PatientRepository {
  Future<PageResult<Patient>> search({
    String? keyword,
    int page = 0,
    int size = 20,
  });
  Future<Patient> getById(String id);
  Future<Patient> create(PatientDraft draft);
  Future<Patient> update(String id, PatientDraft draft);
  Future<void> delete(String id);
}

bool isPatientId(String value) => RegExp(
  r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$',
).hasMatch(value);

bool isValidPatientBirthDate(String value, {DateTime? today}) {
  final match = RegExp(r'^(\d{4})-(\d{2})-(\d{2})$').firstMatch(value);
  if (match == null) return false;
  final year = int.parse(match[1]!);
  final month = int.parse(match[2]!);
  final day = int.parse(match[3]!);
  final date = DateTime(year, month, day);
  if (date.year != year || date.month != month || date.day != day) {
    return false;
  }
  final reference = today ?? DateTime.now();
  return !date.isAfter(
    DateTime(reference.year, reference.month, reference.day),
  );
}

class FindPatients {
  const FindPatients(this.repository);
  final PatientRepository repository;

  Future<PageResult<Patient>> call({
    String? keyword,
    int page = 0,
    int size = 20,
  }) {
    if (page < 0 || size < 1 || size > 100) {
      throw ArgumentError('Trang hoặc kích thước trang không hợp lệ.');
    }
    final normalized = keyword?.trim();
    return repository.search(
      keyword: normalized == null || normalized.isEmpty ? null : normalized,
      page: page,
      size: size,
    );
  }
}

class ReadPatient {
  const ReadPatient(this.repository);
  final PatientRepository repository;
  Future<Patient> call(String id) {
    if (!isPatientId(id)) throw ArgumentError('Mã bệnh nhân không hợp lệ.');
    return repository.getById(id);
  }
}

class SavePatient {
  const SavePatient(this.repository);
  final PatientRepository repository;

  Future<Patient> create(PatientDraft draft) {
    _validate(draft, creating: true);
    return repository.create(draft);
  }

  Future<Patient> update(String id, PatientDraft draft) {
    if (!isPatientId(id)) throw ArgumentError('Mã bệnh nhân không hợp lệ.');
    _validate(draft, creating: false);
    return repository.update(id, draft);
  }

  void _validate(PatientDraft draft, {required bool creating}) {
    if (draft.name.trim().isEmpty || draft.name.trim().length > 100) {
      throw ArgumentError('Họ tên phải có từ 1 đến 100 ký tự.');
    }
    if (!isValidPatientBirthDate(draft.birthDate)) {
      throw ArgumentError('Ngày sinh không hợp lệ.');
    }
    if (!const {'M', 'F'}.contains(draft.gender)) {
      throw ArgumentError('Giới tính không hợp lệ.');
    }
    if (creating && (draft.identityNumber?.trim().isEmpty ?? true)) {
      throw ArgumentError('Số CCCD/CMND là bắt buộc.');
    }
    final identity = draft.identityNumber?.trim();
    if (identity != null && identity.length > 20) {
      throw ArgumentError('Số CCCD/CMND tối đa 20 ký tự.');
    }
    if ((draft.address?.length ?? 0) > 255) {
      throw ArgumentError('Địa chỉ tối đa 255 ký tự.');
    }
    final phone = draft.phoneNumber?.trim();
    if (phone != null &&
        phone.isNotEmpty &&
        !RegExp(r'^\d{10,15}$').hasMatch(phone)) {
      throw ArgumentError('Số điện thoại phải gồm 10 đến 15 chữ số.');
    }
    final email = draft.email?.trim();
    if (email != null &&
        email.isNotEmpty &&
        (email.length > 100 ||
            !RegExp(r'^[^\s@]+@[^\s@]+\.[^\s@]+$').hasMatch(email))) {
      throw ArgumentError('Email không hợp lệ.');
    }
    final insurance = draft.insuranceNumber?.trim();
    if (insurance != null &&
        insurance.isNotEmpty &&
        !RegExp(r'^\d{2}-\d{8}-\d$').hasMatch(insurance)) {
      throw ArgumentError('Mã BHYT không hợp lệ.');
    }
  }
}
