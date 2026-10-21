import '../../../core/domain/page_result.dart';

const departmentTypes = ['CLINICAL', 'PARACLINICAL', 'ADMINISTRATIVE'];
const staffJobTitles = [
  'DOCTOR',
  'NURSE',
  'TECHNICIAN',
  'PHARMACIST',
  'CASHIER',
  'MANAGER',
  'ADMINISTRATIVE',
];
const accountRoles = [
  'ADMIN',
  'DOCTOR',
  'NURSE',
  'PHARMACIST',
  'CASHIER',
  'LAB_TECH',
  'MANAGER',
  'PATIENT',
];

class Department {
  const Department({
    required this.id,
    required this.name,
    required this.abbreviation,
    required this.type,
    required this.headId,
    required this.location,
    required this.active,
    required this.createdAt,
    required this.updatedAt,
  });
  final String id, name, abbreviation, type, createdAt, updatedAt;
  final String? headId, location;
  final bool active;
}

class Staff {
  const Staff({
    required this.id,
    required this.name,
    required this.departmentId,
    required this.jobTitle,
    required this.specialization,
    required this.licenseNumber,
    required this.phoneNumber,
    required this.email,
    required this.active,
    required this.createdAt,
    required this.updatedAt,
  });
  final String id, name, departmentId, jobTitle, createdAt, updatedAt;
  final String? specialization, licenseNumber, phoneNumber, email;
  final bool active;
}

class Account {
  const Account({
    required this.id,
    required this.username,
    required this.staffId,
    required this.patientId,
    required this.role,
    required this.active,
    required this.lastLoginAt,
    required this.createdAt,
    required this.updatedAt,
  });
  final String id, username, role, createdAt, updatedAt;
  final String? staffId, patientId, lastLoginAt;
  final bool active;
}

class DepartmentDraft {
  const DepartmentDraft({
    required this.name,
    required this.type,
    required this.location,
    required this.abbreviation,
    required this.headId,
    required this.active,
  });
  final String name, type, abbreviation;
  final String? location, headId;
  final bool active;
}

class StaffDraft {
  const StaffDraft({
    required this.name,
    required this.departmentId,
    required this.jobTitle,
    required this.specialization,
    required this.licenseNumber,
    required this.phoneNumber,
    required this.email,
  });
  final String name, jobTitle, departmentId;
  final String? specialization, licenseNumber, phoneNumber, email;
}

class AccountDraft {
  const AccountDraft({
    required this.username,
    required this.password,
    required this.ownerId,
    required this.role,
  });
  final String username, password, ownerId, role;
}

abstract interface class OrganizationRepository {
  Future<List<Department>> departments({bool activeOnly = false});
  Future<Department> department(String id);
  Future<Department> createDepartment(DepartmentDraft draft);
  Future<Department> updateDepartment(String id, DepartmentDraft draft);
  Future<PageResult<Staff>> staff({
    String? departmentId,
    String? jobTitle,
    int page = 0,
    int size = 20,
  });
  Future<Staff> staffMember(String id);
  Future<Staff> createStaff(StaffDraft draft);
  Future<Staff> updateStaff(String id, StaffDraft draft);
  Future<Staff> transferStaff(String id, String newDepartmentId);
  Future<Account> createAccount(AccountDraft draft);
  Future<Account> updateAccountStatus(String id, bool active);
}

bool isOrganizationId(String value) => RegExp(
  r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$',
).hasMatch(value);

class FindStaff {
  const FindStaff(this.repository);
  final OrganizationRepository repository;

  Future<PageResult<Staff>> call({
    String? departmentId,
    String? jobTitle,
    int page = 0,
    int size = 20,
  }) {
    if (page < 0 || size < 1 || size > 100) {
      throw ArgumentError('Trang hoặc kích thước trang không hợp lệ.');
    }
    if (departmentId != null && !isOrganizationId(departmentId)) {
      throw ArgumentError('Mã khoa phòng không hợp lệ.');
    }
    if (jobTitle != null && !staffJobTitles.contains(jobTitle)) {
      throw ArgumentError('Chức danh không hợp lệ.');
    }
    return repository.staff(
      departmentId: departmentId,
      jobTitle: jobTitle,
      page: page,
      size: size,
    );
  }
}

class ReadDepartment {
  const ReadDepartment(this.repository);
  final OrganizationRepository repository;
  Future<Department> call(String id) {
    _requireId(id, 'khoa phòng');
    return repository.department(id);
  }
}

class ReadStaff {
  const ReadStaff(this.repository);
  final OrganizationRepository repository;
  Future<Staff> call(String id) {
    _requireId(id, 'nhân viên');
    return repository.staffMember(id);
  }
}

class SaveDepartment {
  const SaveDepartment(this.repository);
  final OrganizationRepository repository;
  Future<Department> create(DepartmentDraft draft) {
    _validate(draft, creating: true);
    return repository.createDepartment(draft);
  }

  Future<Department> update(String id, DepartmentDraft draft) {
    _requireId(id, 'khoa phòng');
    _validate(draft, creating: false);
    return repository.updateDepartment(id, draft);
  }

  void _validate(DepartmentDraft draft, {required bool creating}) {
    if (draft.name.trim().isEmpty || draft.name.trim().length > 100) {
      throw ArgumentError('Tên khoa phòng phải có từ 1 đến 100 ký tự.');
    }
    if (!departmentTypes.contains(draft.type)) {
      throw ArgumentError('Loại khoa phòng không hợp lệ.');
    }
    if (creating &&
        (draft.abbreviation.trim().isEmpty ||
            draft.abbreviation.trim().length > 20)) {
      throw ArgumentError('Mã viết tắt phải có từ 1 đến 20 ký tự.');
    }
    if ((draft.location?.length ?? 0) > 255) {
      throw ArgumentError('Địa điểm tối đa 255 ký tự.');
    }
    if (draft.headId != null &&
        draft.headId!.isNotEmpty &&
        !isOrganizationId(draft.headId!)) {
      throw ArgumentError('Mã trưởng khoa phải là UUID hợp lệ.');
    }
  }
}

class SaveStaff {
  const SaveStaff(this.repository);
  final OrganizationRepository repository;
  Future<Staff> create(StaffDraft draft) {
    _validate(draft, creating: true);
    return repository.createStaff(draft);
  }

  Future<Staff> update(String id, StaffDraft draft) {
    _requireId(id, 'nhân viên');
    _validate(draft, creating: false);
    return repository.updateStaff(id, draft);
  }

  Future<Staff> transfer(String id, String departmentId) {
    _requireId(id, 'nhân viên');
    _requireId(departmentId, 'khoa phòng');
    return repository.transferStaff(id, departmentId);
  }

  void _validate(StaffDraft draft, {required bool creating}) {
    if (draft.name.trim().isEmpty || draft.name.trim().length > 100) {
      throw ArgumentError('Họ tên phải có từ 1 đến 100 ký tự.');
    }
    if (creating) _requireId(draft.departmentId, 'khoa phòng');
    if (!staffJobTitles.contains(draft.jobTitle)) {
      throw ArgumentError('Chức danh không hợp lệ.');
    }
    if ((draft.specialization?.length ?? 0) > 100 ||
        (draft.licenseNumber?.length ?? 0) > 50) {
      throw ArgumentError('Thông tin chuyên môn vượt quá độ dài cho phép.');
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
  }
}

class SaveAccount {
  const SaveAccount(this.repository);
  final OrganizationRepository repository;
  Future<Account> create(AccountDraft draft) {
    if (!RegExp(r'^[a-zA-Z0-9._-]{3,50}$').hasMatch(draft.username)) {
      throw ArgumentError('Tên đăng nhập phải có 3 đến 50 ký tự hợp lệ.');
    }
    if (draft.password.length < 8 || draft.password.length > 72) {
      throw ArgumentError('Mật khẩu phải có từ 8 đến 72 ký tự.');
    }
    if (!accountRoles.contains(draft.role)) {
      throw ArgumentError('Vai trò không hợp lệ.');
    }
    _requireId(
      draft.ownerId,
      draft.role == 'PATIENT' ? 'bệnh nhân' : 'nhân viên',
    );
    return repository.createAccount(draft);
  }

  Future<Account> updateStatus(String id, bool active) {
    _requireId(id, 'tài khoản');
    return repository.updateAccountStatus(id, active);
  }
}

void _requireId(String value, String label) {
  if (!isOrganizationId(value)) throw ArgumentError('Mã $label không hợp lệ.');
}
