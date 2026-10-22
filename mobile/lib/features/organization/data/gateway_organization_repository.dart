import '../../../core/domain/page_result.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/page_decoder.dart';
import '../domain/organization.dart';
import 'organization_dto.dart';

class GatewayOrganizationRepository implements OrganizationRepository {
  const GatewayOrganizationRepository(this.client);
  final ApiClient client;

  @override
  Future<List<Department>> departments({bool activeOnly = false}) async {
    final value = await client.get(
      '/v1/org/departments',
      query: activeOnly ? {'activeOnly': true} : null,
    );
    if (value is! List) {
      throw const FormatException('Danh sách khoa phòng không hợp lệ.');
    }
    return value.map(OrganizationDto.department).toList(growable: false);
  }

  @override
  Future<Department> department(String id) async {
    _requireId(id);
    final result = OrganizationDto.department(
      await client.get('/v1/org/departments/$id'),
    );
    _verifyId(id, result.id, 'khoa phòng');
    return result;
  }

  @override
  Future<Department> createDepartment(DepartmentDraft draft) async =>
      OrganizationDto.department(
        await client.post(
          '/v1/org/departments',
          OrganizationDto.createDepartmentBody(draft),
        ),
      );

  @override
  Future<Department> updateDepartment(String id, DepartmentDraft draft) async {
    _requireId(id);
    final result = OrganizationDto.department(
      await client.put(
        '/v1/org/departments/$id',
        OrganizationDto.updateDepartmentBody(draft),
      ),
    );
    _verifyId(id, result.id, 'khoa phòng');
    return result;
  }

  @override
  Future<PageResult<Staff>> staff({
    String? departmentId,
    String? jobTitle,
    int page = 0,
    int size = 20,
  }) async {
    if (page < 0 || size < 1 || size > 100) {
      throw ArgumentError('Trang không hợp lệ.');
    }
    if (departmentId != null) _requireId(departmentId);
    if (jobTitle != null && !staffJobTitles.contains(jobTitle)) {
      throw ArgumentError('Chức danh không hợp lệ.');
    }
    return decodePage(
      await client.get(
        '/v1/org/staff',
        query: {
          'page': page,
          'size': size,
          'departmentId': ?departmentId,
          'jobTitle': ?jobTitle,
        },
      ),
      OrganizationDto.staff,
    );
  }

  @override
  Future<Staff> staffMember(String id) async {
    _requireId(id);
    final result = OrganizationDto.staff(await client.get('/v1/org/staff/$id'));
    _verifyId(id, result.id, 'nhân viên');
    return result;
  }

  @override
  Future<Staff> createStaff(StaffDraft draft) async => OrganizationDto.staff(
    await client.post('/v1/org/staff', OrganizationDto.createStaffBody(draft)),
  );

  @override
  Future<Staff> updateStaff(String id, StaffDraft draft) async {
    _requireId(id);
    final result = OrganizationDto.staff(
      await client.put(
        '/v1/org/staff/$id',
        OrganizationDto.updateStaffBody(draft),
      ),
    );
    _verifyId(id, result.id, 'nhân viên');
    return result;
  }

  @override
  Future<Staff> transferStaff(String id, String newDepartmentId) async {
    _requireId(id);
    _requireId(newDepartmentId);
    final result = OrganizationDto.staff(
      await client.put('/v1/org/staff/$id/department', {
        'newDepartmentId': newDepartmentId,
      }),
    );
    _verifyId(id, result.id, 'nhân viên');
    return result;
  }

  @override
  Future<PageResult<Account>> accounts({
    String? keyword,
    String? role,
    bool? active,
    int page = 0,
    int size = 20,
  }) async {
    if (page < 0 || size < 1 || size > 100) {
      throw ArgumentError('Trang không hợp lệ.');
    }
    if (role != null && !accountSearchRoles.contains(role)) {
      throw ArgumentError('Vai trò không hợp lệ.');
    }
    final normalizedKeyword = keyword?.trim();
    if ((normalizedKeyword?.length ?? 0) > 100) {
      throw ArgumentError('Tên đăng nhập tối đa 100 ký tự.');
    }
    return decodePage(
      await client.get(
        '/v1/org/accounts',
        query: {
          'page': page,
          'size': size,
          'keyword': ?normalizedKeyword,
          'role': ?role,
          'active': ?active,
        },
      ),
      OrganizationDto.account,
    );
  }

  @override
  Future<Account> createAccount(AccountDraft draft) async =>
      OrganizationDto.account(
        await client.post(
          '/v1/org/accounts',
          OrganizationDto.createAccountBody(draft),
        ),
      );

  @override
  Future<Account> updateAccountStatus(String id, bool active) async {
    _requireId(id);
    final result = OrganizationDto.account(
      await client.put(
        '/v1/org/accounts/$id/status',
        OrganizationDto.accountStatusBody(active),
      ),
    );
    _verifyId(id, result.id, 'tài khoản');
    return result;
  }

  static void _requireId(String value) {
    if (!isOrganizationId(value)) throw ArgumentError('Mã UUID không hợp lệ.');
  }

  static void _verifyId(String requested, String received, String label) {
    if (requested.toLowerCase() != received.toLowerCase()) {
      throw FormatException('Mã $label trả về không khớp.');
    }
  }
}
