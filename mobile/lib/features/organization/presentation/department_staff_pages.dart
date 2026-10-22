import '../../../core/widgets/reference_widgets.dart';

import 'package:flutter/material.dart';

import '../../../core/domain/workspace_access.dart';
import '../../../core/network/api_failure.dart';
import '../../../core/widgets/request_state.dart';
import '../domain/organization.dart';
import 'access_boundary.dart';

class DepartmentDetailPage extends StatelessWidget {
  const DepartmentDetailPage({super.key, required this.departmentId});
  final String departmentId;
  @override
  Widget build(BuildContext context) => OrganizationBoundary(
    builder: (repository, access) => _DepartmentDetail(
      key: ValueKey((departmentId, access.sessionIdentity)),
      repository: repository,
      access: access,
      id: departmentId,
    ),
  );
}

class _DepartmentDetail extends StatefulWidget {
  const _DepartmentDetail({
    super.key,
    required this.repository,
    required this.access,
    required this.id,
  });
  final OrganizationRepository repository;
  final WorkspaceAccess access;
  final String id;
  @override
  State<_DepartmentDetail> createState() => _DepartmentDetailState();
}

class _DepartmentDetailState extends State<_DepartmentDetail> {
  Department? _department;
  Object? _error;
  bool _loading = true;
  int _generation = 0;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _generation++;
    super.dispose();
  }

  Future<void> _load() async {
    final generation = ++_generation;
    setState(() {
      _loading = true;
      _error = null;
      _department = null;
    });
    try {
      final department = await ReadDepartment(widget.repository)(widget.id);
      if (mounted && generation == _generation) {
        setState(() => _department = department);
      }
    } catch (error) {
      if (mounted && generation == _generation) setState(() => _error = error);
    } finally {
      if (mounted && generation == _generation) {
        setState(() => _loading = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) return const Center(child: CircularProgressIndicator());
    if (_error != null) {
      return SingleChildScrollView(
        child: RequestError(
          _error!,
          onRetry: canRetryOrganization(_error) ? _load : null,
        ),
      );
    }
    final department = _department!;
    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.all(16),
        children: [
          Text(
            department.name,
            style: Theme.of(context).textTheme.headlineSmall,
          ),
          const SizedBox(height: 12),
          OrganizationField('Viết tắt', department.abbreviation),
          OrganizationField('Loại', department.type),
          if (department.headId != null)
            ReferenceLabel(
              kind: ReferenceKind.staff,
              id: department.headId!,
              label: 'Trưởng khoa',
            ),
          OrganizationField('Địa điểm', department.location),
          OrganizationField(
            'Trạng thái',
            department.active ? 'Đang hoạt động' : 'Ngừng hoạt động',
          ),
          OrganizationField('Tạo lúc', department.createdAt),
          OrganizationField('Cập nhật', department.updatedAt),
          if (widget.access.role == 'ADMIN')
            OutlinedButton.icon(
              onPressed: () => _edit(department),
              icon: const Icon(Icons.edit_outlined),
              label: const Text('Chỉnh sửa khoa phòng'),
            ),
        ],
      ),
    );
  }

  Future<void> _edit(Department department) async {
    await Navigator.of(context).push<void>(
      MaterialPageRoute(
        builder: (_) => Scaffold(
          appBar: AppBar(title: const Text('Chỉnh sửa khoa phòng')),
          body: DepartmentEditorPage(
            departmentId: department.id,
            onSaved: () => Navigator.of(context).pop(),
          ),
        ),
      ),
    );
    if (mounted) _load();
  }
}

class DepartmentEditorPage extends StatelessWidget {
  const DepartmentEditorPage({
    super.key,
    this.departmentId,
    required this.onSaved,
  });
  final String? departmentId;
  final VoidCallback onSaved;
  @override
  Widget build(BuildContext context) => OrganizationBoundary(
    builder: (repository, access) {
      if (access.role != 'ADMIN') {
        return const Center(
          child: Text('Chỉ quản trị viên được sửa khoa phòng.'),
        );
      }
      return KeyedSubtree(
        key: ValueKey((departmentId, access.sessionIdentity, repository)),
        child: _DepartmentForm(
          repository: repository,
          id: departmentId,
          onSaved: onSaved,
        ),
      );
    },
  );
}

class _DepartmentForm extends StatefulWidget {
  const _DepartmentForm({
    required this.repository,
    required this.id,
    required this.onSaved,
  });
  final OrganizationRepository repository;
  final String? id;
  final VoidCallback onSaved;
  @override
  State<_DepartmentForm> createState() => _DepartmentFormState();
}

class _DepartmentFormState extends State<_DepartmentForm> {
  final _form = GlobalKey<FormState>();
  final _name = TextEditingController();
  final _abbreviation = TextEditingController();
  final _location = TextEditingController();
  final _headId = TextEditingController();
  String _type = departmentTypes.first;
  Department? _loaded;
  Object? _loadError, _saveError;
  bool _loading = false,
      _saving = false,
      _active = true,
      _uncertainCreate = false;
  int _generation = 0;
  bool get _editing => widget.id != null;

  @override
  void initState() {
    super.initState();
    if (_editing) _load();
  }

  @override
  void dispose() {
    _generation++;
    _name.dispose();
    _abbreviation.dispose();
    _location.dispose();
    _headId.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    final generation = ++_generation;
    setState(() {
      _loading = true;
      _loadError = null;
      _loaded = null;
    });
    try {
      final department = await ReadDepartment(widget.repository)(widget.id!);
      if (mounted && generation == _generation) {
        _name.text = department.name;
        _abbreviation.text = department.abbreviation;
        _location.text = department.location ?? '';
        _headId.text = department.headId ?? '';
        setState(() {
          _loaded = department;
          _type = department.type;
          _active = department.active;
        });
      }
    } catch (error) {
      if (mounted && generation == _generation) {
        setState(() => _loadError = error);
      }
    } finally {
      if (mounted && generation == _generation) {
        setState(() => _loading = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_editing && _loading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_editing && _loadError != null) {
      return SingleChildScrollView(
        child: RequestError(
          _loadError!,
          onRetry: canRetryOrganization(_loadError) ? _load : null,
        ),
      );
    }
    if (_editing && _loaded == null) {
      return const Center(child: Text('Không thể tải khoa phòng.'));
    }
    return Form(
      key: _form,
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          TextFormField(
            controller: _name,
            decoration: const InputDecoration(labelText: 'Tên khoa phòng *'),
            maxLength: 100,
            validator: (value) =>
                value == null ||
                    value.trim().isEmpty ||
                    value.trim().length > 100
                ? 'Tên khoa phòng phải có từ 1 đến 100 ký tự.'
                : null,
          ),
          if (!_editing)
            TextFormField(
              controller: _abbreviation,
              decoration: const InputDecoration(labelText: 'Mã viết tắt *'),
              maxLength: 20,
              validator: (value) =>
                  value == null ||
                      value.trim().isEmpty ||
                      value.trim().length > 20
                  ? 'Mã viết tắt phải có từ 1 đến 20 ký tự.'
                  : null,
            )
          else
            InputDecorator(
              decoration: const InputDecoration(
                labelText: 'Mã viết tắt (không đổi qua API hiện tại)',
              ),
              child: Text(_loaded!.abbreviation),
            ),
          DropdownButtonFormField<String>(
            initialValue: _type,
            decoration: const InputDecoration(labelText: 'Loại khoa phòng *'),
            items: [
              for (final type in departmentTypes)
                DropdownMenuItem(value: type, child: Text(type)),
            ],
            onChanged: (value) =>
                setState(() => _type = value ?? departmentTypes.first),
          ),
          TextFormField(
            controller: _location,
            decoration: const InputDecoration(labelText: 'Địa điểm'),
            maxLength: 255,
          ),
          if (_editing) ...[
            ReferencePicker(
              kind: ReferenceKind.staff,
              label: 'Trưởng khoa',
              value: _headId.text.isEmpty ? null : _headId.text,
              scope: widget.id,
              onChanged: (id) => setState(() => _headId.text = id ?? ''),
            ),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: const Text('Đang hoạt động'),
              value: _active,
              onChanged: (value) => setState(() => _active = value),
            ),
          ],
          if (_saveError != null) ...[
            const SizedBox(height: 8),
            RequestError(_saveError!),
          ],
          if (_uncertainCreate)
            const Text(
              'Kết quả tạo chưa xác định. Quay lại danh sách và làm mới trước khi thử lại để tránh tạo trùng.',
            ),
          const SizedBox(height: 12),
          FilledButton.icon(
            onPressed: _saving || _uncertainCreate ? null : _save,
            icon: _saving
                ? const SizedBox.square(
                    dimension: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.save_outlined),
            label: Text(_editing ? 'Lưu khoa phòng' : 'Tạo khoa phòng'),
          ),
          if (_uncertainCreate)
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: const Text('Quay lại danh sách'),
            ),
        ],
      ),
    );
  }

  Future<void> _save() async {
    if (!_form.currentState!.validate()) return;
    setState(() {
      _saving = true;
      _saveError = null;
    });
    try {
      final draft = DepartmentDraft(
        name: _name.text.trim(),
        abbreviation: _editing
            ? _loaded!.abbreviation
            : _abbreviation.text.trim(),
        type: _type,
        location: _location.text,
        headId: _headId.text.trim().isEmpty ? null : _headId.text.trim(),
        active: _active,
      );
      if (_editing) {
        await SaveDepartment(widget.repository).update(widget.id!, draft);
      } else {
        await SaveDepartment(widget.repository).create(draft);
      }
      if (mounted) widget.onSaved();
    } catch (error) {
      if (mounted) {
        setState(() {
          _saveError = error;
          if (!_editing && _mutationUncertain(error)) _uncertainCreate = true;
        });
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }
}

class StaffDetailPage extends StatelessWidget {
  const StaffDetailPage({super.key, required this.staffId});
  final String staffId;
  @override
  Widget build(BuildContext context) => OrganizationBoundary(
    builder: (repository, access) => _StaffDetail(
      key: ValueKey((staffId, access.sessionIdentity)),
      repository: repository,
      access: access,
      id: staffId,
    ),
  );
}

class _StaffDetail extends StatefulWidget {
  const _StaffDetail({
    super.key,
    required this.repository,
    required this.access,
    required this.id,
  });
  final OrganizationRepository repository;
  final WorkspaceAccess access;
  final String id;
  @override
  State<_StaffDetail> createState() => _StaffDetailState();
}

class _StaffDetailState extends State<_StaffDetail> {
  Staff? _staff;
  Object? _error;
  bool _loading = true;
  int _generation = 0;
  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _generation++;
    super.dispose();
  }

  Future<void> _load() async {
    final generation = ++_generation;
    setState(() {
      _loading = true;
      _error = null;
      _staff = null;
    });
    try {
      final value = await ReadStaff(widget.repository)(widget.id);
      if (mounted && generation == _generation) setState(() => _staff = value);
    } catch (error) {
      if (mounted && generation == _generation) setState(() => _error = error);
    } finally {
      if (mounted && generation == _generation) {
        setState(() => _loading = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) return const Center(child: CircularProgressIndicator());
    if (_error != null) {
      return SingleChildScrollView(
        child: RequestError(
          _error!,
          onRetry: canRetryOrganization(_error) ? _load : null,
        ),
      );
    }
    final staff = _staff!;
    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.all(16),
        children: [
          Text(staff.name, style: Theme.of(context).textTheme.headlineSmall),
          const SizedBox(height: 12),
          ReferenceLabel(
            kind: ReferenceKind.department,
            id: staff.departmentId,
            label: 'Khoa phòng',
          ),
          OrganizationField('Chức danh', staff.jobTitle),
          OrganizationField('Chuyên môn', staff.specialization),
          OrganizationField('Số chứng chỉ', staff.licenseNumber),
          OrganizationField('Điện thoại', staff.phoneNumber),
          OrganizationField('Email', staff.email),
          OrganizationField(
            'Trạng thái',
            staff.active ? 'Đang làm việc' : 'Ngừng hoạt động',
          ),
          OrganizationField('Tạo lúc', staff.createdAt),
          OrganizationField('Cập nhật', staff.updatedAt),
          if (widget.access.role == 'ADMIN')
            OutlinedButton.icon(
              onPressed: () => _edit(staff),
              icon: const Icon(Icons.edit_outlined),
              label: const Text('Chỉnh sửa hoặc chuyển khoa'),
            ),
        ],
      ),
    );
  }

  Future<void> _edit(Staff staff) async {
    await Navigator.of(context).push<void>(
      MaterialPageRoute(
        builder: (_) => Scaffold(
          appBar: AppBar(title: const Text('Chỉnh sửa nhân viên')),
          body: StaffEditorPage(
            staffId: staff.id,
            onSaved: () => Navigator.of(context).pop(),
          ),
        ),
      ),
    );
    if (mounted) _load();
  }
}

class StaffEditorPage extends StatelessWidget {
  const StaffEditorPage({super.key, this.staffId, required this.onSaved});
  final String? staffId;
  final VoidCallback onSaved;
  @override
  Widget build(BuildContext context) => OrganizationBoundary(
    builder: (repository, access) {
      if (access.role != 'ADMIN') {
        return const Center(child: Text('Chỉ quản trị viên được sửa nhân sự.'));
      }
      return KeyedSubtree(
        key: ValueKey((staffId, access.sessionIdentity, repository)),
        child: _StaffForm(
          repository: repository,
          id: staffId,
          onSaved: onSaved,
        ),
      );
    },
  );
}

class _StaffForm extends StatefulWidget {
  const _StaffForm({
    required this.repository,
    required this.id,
    required this.onSaved,
  });
  final OrganizationRepository repository;
  final String? id;
  final VoidCallback onSaved;
  @override
  State<_StaffForm> createState() => _StaffFormState();
}

class _StaffFormState extends State<_StaffForm> {
  final _form = GlobalKey<FormState>();
  final _name = TextEditingController();
  final _specialization = TextEditingController();
  final _license = TextEditingController();
  final _phone = TextEditingController();
  final _email = TextEditingController();
  String? _departmentId;
  String _jobTitle = staffJobTitles.first;
  Staff? _loaded, _savedProfile;
  List<Department>? _departments;
  Object? _staffError, _departmentError, _saveError;
  bool _loadingStaff = false,
      _loadingDepartments = true,
      _saving = false,
      _uncertainCreate = false;
  int _generation = 0;
  bool get _editing => widget.id != null;

  @override
  void initState() {
    super.initState();
    _loadDepartments();
    if (_editing) _loadStaff();
  }

  @override
  void dispose() {
    _generation++;
    _name.dispose();
    _specialization.dispose();
    _license.dispose();
    _phone.dispose();
    _email.dispose();
    super.dispose();
  }

  Future<void> _loadDepartments() async {
    setState(() {
      _loadingDepartments = true;
      _departmentError = null;
      _departments = null;
    });
    try {
      final result = await widget.repository.departments();
      if (mounted) {
        setState(() {
          _departments = result;
          _departmentId ??= _loaded?.departmentId ?? result.firstOrNull?.id;
        });
      }
    } catch (error) {
      if (mounted) setState(() => _departmentError = error);
    } finally {
      if (mounted) setState(() => _loadingDepartments = false);
    }
  }

  Future<void> _loadStaff() async {
    final generation = ++_generation;
    setState(() {
      _loadingStaff = true;
      _staffError = null;
      _loaded = null;
    });
    try {
      final staff = await ReadStaff(widget.repository)(widget.id!);
      if (mounted && generation == _generation) {
        _name.text = staff.name;
        _specialization.text = staff.specialization ?? '';
        _license.text = staff.licenseNumber ?? '';
        _phone.text = staff.phoneNumber ?? '';
        _email.text = staff.email ?? '';
        setState(() {
          _loaded = staff;
          _departmentId = staff.departmentId;
          _jobTitle = staff.jobTitle;
        });
      }
    } catch (error) {
      if (mounted && generation == _generation) {
        setState(() => _staffError = error);
      }
    } finally {
      if (mounted && generation == _generation) {
        setState(() => _loadingStaff = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_editing && _loadingStaff) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_editing && _staffError != null) {
      return SingleChildScrollView(
        child: RequestError(
          _staffError!,
          onRetry: canRetryOrganization(_staffError) ? _loadStaff : null,
        ),
      );
    }
    if (_editing && _loaded == null) {
      return const Center(child: Text('Không thể tải hồ sơ nhân viên.'));
    }
    if (!_editing && _loadingDepartments) {
      return const Center(child: CircularProgressIndicator());
    }
    if (!_editing && _departmentError != null) {
      return SingleChildScrollView(
        child: RequestError(
          _departmentError!,
          onRetry: canRetryOrganization(_departmentError)
              ? _loadDepartments
              : null,
        ),
      );
    }
    if (!_editing && (_departments?.isEmpty ?? true)) {
      return const Center(
        child: Text('Cần có ít nhất một khoa phòng trước khi tạo nhân viên.'),
      );
    }
    return Form(
      key: _form,
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          TextFormField(
            controller: _name,
            decoration: const InputDecoration(labelText: 'Họ tên *'),
            maxLength: 100,
            validator: (value) =>
                value == null ||
                    value.trim().isEmpty ||
                    value.trim().length > 100
                ? 'Họ tên phải có từ 1 đến 100 ký tự.'
                : null,
          ),
          if (!_editing || _departments != null)
            DropdownButtonFormField<String>(
              initialValue: _departmentId,
              decoration: InputDecoration(
                labelText: _editing ? 'Khoa phòng' : 'Khoa phòng *',
              ),
              items: [
                for (final department in _departments ?? const <Department>[])
                  DropdownMenuItem(
                    value: department.id,
                    child: Text(
                      '${department.name}${department.active ? '' : ' (ngừng hoạt động)'}',
                    ),
                  ),
              ],
              validator: (value) =>
                  !_editing && (value == null || !isOrganizationId(value))
                  ? 'Chọn khoa phòng.'
                  : null,
              onChanged: (value) => setState(() => _departmentId = value),
            )
          else
            InputDecorator(
              decoration: const InputDecoration(
                labelText: 'Khoa phòng hiện tại',
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(_loaded!.departmentId),
                  if (_departmentError != null)
                    RequestError(
                      _departmentError!,
                      onRetry: canRetryOrganization(_departmentError)
                          ? _loadDepartments
                          : null,
                    ),
                  if (_departmentError == null && _departments == null)
                    const LinearProgressIndicator(),
                ],
              ),
            ),
          DropdownButtonFormField<String>(
            initialValue: _jobTitle,
            decoration: const InputDecoration(labelText: 'Chức danh *'),
            items: [
              for (final title in staffJobTitles)
                DropdownMenuItem(value: title, child: Text(title)),
            ],
            onChanged: (value) =>
                setState(() => _jobTitle = value ?? staffJobTitles.first),
          ),
          TextFormField(
            controller: _specialization,
            decoration: const InputDecoration(labelText: 'Chuyên môn'),
            maxLength: 100,
          ),
          TextFormField(
            controller: _license,
            decoration: const InputDecoration(labelText: 'Số chứng chỉ'),
            maxLength: 50,
          ),
          TextFormField(
            controller: _phone,
            decoration: const InputDecoration(labelText: 'Điện thoại'),
            keyboardType: TextInputType.phone,
            validator: (value) =>
                value != null &&
                    value.trim().isNotEmpty &&
                    !RegExp(r'^\d{10,15}$').hasMatch(value.trim())
                ? 'Số điện thoại phải gồm 10 đến 15 chữ số.'
                : null,
          ),
          TextFormField(
            controller: _email,
            decoration: const InputDecoration(labelText: 'Email'),
            keyboardType: TextInputType.emailAddress,
            validator: (value) =>
                value != null &&
                    value.trim().isNotEmpty &&
                    (value.trim().length > 100 ||
                        !RegExp(r'^[^\s@]+@[^\s@]+\.[^\s@]+$')
                            .hasMatch(value.trim()))
                ? 'Email không hợp lệ.'
                : null,
          ),
          if (_savedProfile != null && _saveError != null)
            Card(
              color: Theme.of(context).colorScheme.errorContainer,
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'Thông tin nhân viên đã được lưu; chuyển khoa chưa hoàn tất.',
                    ),
                    Text(_saveError.toString()),
                    const Text(
                      'Hãy xử lý điều kiện chuyển khoa rồi chỉ thử lại thao tác chuyển, không gửi lại phần cập nhật hồ sơ.',
                    ),
                  ],
                ),
              ),
            )
          else if (_saveError != null)
            RequestError(_saveError!),
          if (_uncertainCreate)
            const Text(
              'Kết quả tạo chưa xác định. Quay lại danh sách và làm mới trước khi thử lại để tránh tạo trùng.',
            ),
          const SizedBox(height: 12),
          FilledButton.icon(
            onPressed:
                _saving ||
                    _uncertainCreate ||
                    (_savedProfile != null && _departmentError != null)
                ? null
                : _save,
            icon: _saving
                ? const SizedBox.square(
                    dimension: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : Icon(
                    _savedProfile == null
                        ? Icons.save_outlined
                        : Icons.swap_horiz,
                  ),
            label: Text(
              _savedProfile != null
                  ? 'Chỉ thử chuyển khoa'
                  : _editing
                  ? 'Lưu thay đổi'
                  : 'Tạo nhân viên',
            ),
          ),
          if (_uncertainCreate)
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: const Text('Quay lại danh sách'),
            ),
        ],
      ),
    );
  }

  StaffDraft _draft() => StaffDraft(
    name: _name.text.trim(),
    departmentId: _departmentId ?? '',
    jobTitle: _jobTitle,
    specialization: _specialization.text,
    licenseNumber: _license.text,
    phoneNumber: _phone.text,
    email: _email.text,
  );

  Future<void> _save() async {
    if (!_form.currentState!.validate()) return;
    setState(() {
      _saving = true;
      _saveError = null;
    });
    try {
      final useCase = SaveStaff(widget.repository);
      if (!_editing) {
        await useCase.create(_draft());
        if (mounted) widget.onSaved();
        return;
      }
      _savedProfile ??= await useCase.update(widget.id!, _draft());
      if (!mounted) return;
      if (_departmentId != null &&
          _departmentId != _savedProfile!.departmentId) {
        try {
          await useCase.transfer(widget.id!, _departmentId!);
        } catch (error) {
          if (mounted) setState(() => _saveError = error);
          return;
        }
      }
      if (mounted) widget.onSaved();
    } catch (error) {
      if (mounted) {
        setState(() {
          _saveError = error;
          if (!_editing && _mutationUncertain(error)) _uncertainCreate = true;
        });
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }
}

bool _mutationUncertain(Object error) =>
    error is! ApiFailure || error.status == null || error.status! >= 500;
