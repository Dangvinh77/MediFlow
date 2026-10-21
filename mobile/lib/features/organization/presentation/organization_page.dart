import 'package:flutter/material.dart';

import '../../../core/domain/workspace_access.dart';
import '../../../core/network/api_failure.dart';
import '../../../core/widgets/paged_list.dart';
import '../../../core/widgets/request_state.dart';
import 'access_boundary.dart';
import '../domain/organization.dart';
import 'department_staff_pages.dart';

class OrganizationPage extends StatelessWidget {
  const OrganizationPage({super.key});
  @override
  Widget build(BuildContext context) => OrganizationBoundary(
    builder: (repository, access) =>
        _OrganizationTabs(repository: repository, access: access),
  );
}

class _OrganizationTabs extends StatelessWidget {
  const _OrganizationTabs({required this.repository, required this.access});
  final OrganizationRepository repository;
  final WorkspaceAccess access;

  @override
  Widget build(BuildContext context) {
    final isAdmin = access.role == 'ADMIN';
    return DefaultTabController(
      length: isAdmin ? 3 : 2,
      child: Column(
        children: [
          TabBar(
            isScrollable: true,
            tabs: [
              const Tab(text: 'Khoa phòng'),
              const Tab(text: 'Nhân sự'),
              if (isAdmin) const Tab(text: 'Tài khoản'),
            ],
          ),
          Expanded(
            child: TabBarView(
              children: [
                DepartmentSection(repository: repository, access: access),
                StaffSection(repository: repository, access: access),
                if (isAdmin) AccountSection(repository: repository),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class DepartmentSection extends StatefulWidget {
  const DepartmentSection({
    super.key,
    required this.repository,
    required this.access,
  });
  final OrganizationRepository repository;
  final WorkspaceAccess access;
  @override
  State<DepartmentSection> createState() => _DepartmentSectionState();
}

class _DepartmentSectionState extends State<DepartmentSection> {
  List<Department>? _departments;
  Object? _error;
  bool _loading = true, _activeOnly = false;
  String _keyword = '';
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
      _departments = null;
    });
    try {
      final departments = await widget.repository.departments(
        activeOnly: _activeOnly,
      );
      if (mounted && generation == _generation) {
        setState(() => _departments = departments);
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
    final items = (_departments ?? const <Department>[])
        .where(
          (department) =>
              department.name.toLowerCase().contains(_keyword.toLowerCase()) ||
              department.abbreviation.toLowerCase().contains(
                _keyword.toLowerCase(),
              ),
        )
        .toList(growable: false);
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.all(12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              TextField(
                decoration: const InputDecoration(
                  labelText: 'Tìm khoa phòng',
                  prefixIcon: Icon(Icons.search),
                ),
                onChanged: (value) => setState(() => _keyword = value.trim()),
              ),
              Wrap(
                crossAxisAlignment: WrapCrossAlignment.center,
                spacing: 8,
                children: [
                  FilterChip(
                    label: const Text('Chỉ đang hoạt động'),
                    selected: _activeOnly,
                    onSelected: (value) {
                      setState(() => _activeOnly = value);
                      _load();
                    },
                  ),
                  if (widget.access.role == 'ADMIN')
                    FilledButton.tonalIcon(
                      onPressed: () => _openEditor(context),
                      icon: const Icon(Icons.add),
                      label: const Text('Thêm khoa phòng'),
                    ),
                  IconButton(
                    tooltip: 'Làm mới khoa phòng',
                    onPressed: _loading ? null : _load,
                    icon: const Icon(Icons.refresh),
                  ),
                ],
              ),
            ],
          ),
        ),
        Expanded(
          child: _loading
              ? const Center(child: CircularProgressIndicator())
              : _error != null
              ? SingleChildScrollView(
                  child: RequestError(
                    _error!,
                    onRetry: canRetryOrganization(_error) ? _load : null,
                  ),
                )
              : RefreshIndicator(
                  onRefresh: _load,
                  child: items.isEmpty
                      ? ListView(
                          physics: const AlwaysScrollableScrollPhysics(),
                          children: const [
                            Padding(
                              padding: EdgeInsets.all(24),
                              child: Text('Chưa có khoa phòng phù hợp.'),
                            ),
                          ],
                        )
                      : ListView.separated(
                          physics: const AlwaysScrollableScrollPhysics(),
                          padding: const EdgeInsets.all(12),
                          itemCount: items.length,
                          separatorBuilder: (_, _) => const SizedBox(height: 8),
                          itemBuilder: (context, index) {
                            final department = items[index];
                            return Card(
                              child: ListTile(
                                title: Text(department.name),
                                subtitle: Text(
                                  '${department.abbreviation} · ${department.type}\n${department.active ? 'Đang hoạt động' : 'Ngừng hoạt động'}',
                                ),
                                isThreeLine: true,
                                trailing: const Icon(Icons.chevron_right),
                                onTap: () =>
                                    _openDetail(context, department.id),
                              ),
                            );
                          },
                        ),
                ),
        ),
      ],
    );
  }

  Future<void> _openDetail(BuildContext context, String id) async {
    await Navigator.of(context).push<void>(
      MaterialPageRoute(
        builder: (_) => Scaffold(
          appBar: AppBar(title: const Text('Khoa phòng')),
          body: DepartmentDetailPage(departmentId: id),
        ),
      ),
    );
    if (mounted) _load();
  }

  Future<void> _openEditor(BuildContext context) async {
    await Navigator.of(context).push<void>(
      MaterialPageRoute(
        builder: (_) => Scaffold(
          appBar: AppBar(title: const Text('Thêm khoa phòng')),
          body: DepartmentEditorPage(
            onSaved: () => Navigator.of(context).pop(),
          ),
        ),
      ),
    );
    if (mounted) _load();
  }
}

class StaffSection extends StatefulWidget {
  const StaffSection({
    super.key,
    required this.repository,
    required this.access,
  });
  final OrganizationRepository repository;
  final WorkspaceAccess access;
  @override
  State<StaffSection> createState() => _StaffSectionState();
}

class _StaffSectionState extends State<StaffSection> {
  List<Department>? _departments;
  Object? _departmentError;
  String? _departmentId, _jobTitle;
  int _generation = 0;
  int _refreshIdentity = 0;

  @override
  void initState() {
    super.initState();
    _loadDepartments();
  }

  @override
  void dispose() {
    _generation++;
    super.dispose();
  }

  Future<void> _loadDepartments() async {
    final generation = ++_generation;
    setState(() {
      _departments = null;
      _departmentError = null;
    });
    try {
      final departments = await widget.repository.departments(activeOnly: true);
      if (mounted && generation == _generation) {
        setState(() => _departments = departments);
      }
    } catch (error) {
      if (mounted && generation == _generation) {
        setState(() => _departmentError = error);
      }
    }
  }

  @override
  Widget build(BuildContext context) => Column(
    children: [
      Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (_departmentError != null)
              RequestError(
                _departmentError!,
                onRetry: canRetryOrganization(_departmentError)
                    ? _loadDepartments
                    : null,
              )
            else if (_departments == null)
              const LinearProgressIndicator()
            else
              DropdownButtonFormField<String>(
                initialValue: _departmentId ?? '',
                decoration: const InputDecoration(labelText: 'Khoa phòng'),
                items: [
                  const DropdownMenuItem(value: '', child: Text('Tất cả khoa')),
                  for (final department in _departments!)
                    DropdownMenuItem(
                      value: department.id,
                      child: Text(department.name),
                    ),
                ],
                onChanged: (value) => setState(
                  () => _departmentId = value == null || value.isEmpty
                      ? null
                      : value,
                ),
              ),
            const SizedBox(height: 8),
            DropdownButtonFormField<String>(
              initialValue: _jobTitle ?? '',
              decoration: const InputDecoration(labelText: 'Chức danh'),
              items: [
                const DropdownMenuItem(
                  value: '',
                  child: Text('Tất cả chức danh'),
                ),
                for (final title in staffJobTitles)
                  DropdownMenuItem(value: title, child: Text(title)),
              ],
              onChanged: (value) => setState(
                () => _jobTitle = value == null || value.isEmpty ? null : value,
              ),
            ),
            Wrap(
              spacing: 8,
              children: [
                if (widget.access.role == 'ADMIN')
                  FilledButton.tonalIcon(
                    onPressed: () => _openEditor(context),
                    icon: const Icon(Icons.person_add_alt_1),
                    label: const Text('Thêm nhân viên'),
                  ),
                IconButton(
                  tooltip: 'Làm mới nhân sự',
                  onPressed: _refresh,
                  icon: const Icon(Icons.refresh),
                ),
              ],
            ),
          ],
        ),
      ),
      Expanded(
        child: _StaffResults(
          key: ValueKey((_departmentId, _jobTitle, _refreshIdentity)),
          repository: widget.repository,
          departmentId: _departmentId,
          jobTitle: _jobTitle,
          onOpenStaff: (id) => _openStaff(context, id),
        ),
      ),
    ],
  );

  void _refresh() {
    setState(() => _refreshIdentity++);
    _loadDepartments();
  }

  Future<void> _openStaff(BuildContext context, String id) async {
    await Navigator.of(context).push<void>(
      MaterialPageRoute(
        builder: (_) => Scaffold(
          appBar: AppBar(title: const Text('Nhân viên')),
          body: StaffDetailPage(staffId: id),
        ),
      ),
    );
    if (mounted) _refresh();
  }

  Future<void> _openEditor(BuildContext context) async {
    await Navigator.of(context).push<void>(
      MaterialPageRoute(
        builder: (_) => Scaffold(
          appBar: AppBar(title: const Text('Thêm nhân viên')),
          body: StaffEditorPage(onSaved: () => Navigator.of(context).pop()),
        ),
      ),
    );
    if (mounted) _refresh();
  }
}

class _StaffResults extends StatelessWidget {
  const _StaffResults({
    super.key,
    required this.repository,
    required this.departmentId,
    required this.jobTitle,
    required this.onOpenStaff,
  });
  final OrganizationRepository repository;
  final String? departmentId, jobTitle;
  final ValueChanged<String> onOpenStaff;
  @override
  Widget build(BuildContext context) => PagedList<Staff>(
    canRetry: canRetryOrganization,
    load: (page) => FindStaff(repository)(
      departmentId: departmentId,
      jobTitle: jobTitle,
      page: page,
    ),
    itemBuilder: (context, staff) => Card(
      child: ListTile(
        title: Text(staff.name),
        subtitle: Text(
          '${staff.jobTitle} · ${staff.active ? 'Đang làm việc' : 'Ngừng hoạt động'}\nMã: ${staff.id}',
        ),
        isThreeLine: true,
        trailing: const Icon(Icons.chevron_right),
        onTap: () => onOpenStaff(staff.id),
      ),
    ),
  );
}

class AccountSection extends StatelessWidget {
  const AccountSection({super.key, required this.repository});
  final OrganizationRepository repository;
  @override
  Widget build(BuildContext context) => _AdminOrganizationBoundary(
    builder: (repo, _) => _AccountPanel(repository: repo),
  );
}

class _AdminOrganizationBoundary extends StatelessWidget {
  const _AdminOrganizationBoundary({required this.builder});
  final Widget Function(OrganizationRepository, WorkspaceAccess) builder;
  @override
  Widget build(BuildContext context) => OrganizationBoundary(
    builder: (repository, access) => access.role == 'ADMIN'
        ? builder(repository, access)
        : const Center(
            child: Text('Chỉ quản trị viên được quản lý tài khoản.'),
          ),
  );
}

class _AccountPanel extends StatefulWidget {
  const _AccountPanel({required this.repository});
  final OrganizationRepository repository;
  @override
  State<_AccountPanel> createState() => _AccountPanelState();
}

class _AccountPanelState extends State<_AccountPanel> {
  final _createForm = GlobalKey<FormState>();
  final _statusForm = GlobalKey<FormState>();
  final _username = TextEditingController();
  final _password = TextEditingController();
  final _ownerId = TextEditingController();
  final _accountId = TextEditingController();
  String _role = 'DOCTOR';
  bool _active = true, _saving = false, _uncertainCreate = false;
  Account? _result;
  Object? _error;

  @override
  void dispose() {
    _username.dispose();
    _password.dispose();
    _ownerId.dispose();
    _accountId.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ListView(
    padding: const EdgeInsets.all(16),
    children: [
      if (_error != null) RequestError(_error!),
      if (_result != null)
        Card(
          child: ListTile(
            title: Text('Tài khoản ${_result!.username}'),
            subtitle: SelectableText(
              'Mã: ${_result!.id}\n${_result!.role} · ${_result!.active ? 'Đang hoạt động' : 'Đã khóa'}',
            ),
          ),
        ),
      Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Form(
            key: _createForm,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(
                  'Tạo tài khoản',
                  style: Theme.of(context).textTheme.titleLarge,
                ),
                const SizedBox(height: 8),
                const Text(
                  'Tài khoản PATIENT gắn mã bệnh nhân; các vai trò khác gắn mã nhân viên.',
                ),
                TextFormField(
                  controller: _username,
                  decoration: const InputDecoration(labelText: 'Tên đăng nhập'),
                  validator: (value) =>
                      value == null ||
                          !RegExp(r'^[a-zA-Z0-9._-]{3,50}$')
                              .hasMatch(value.trim())
                      ? 'Dùng 3 đến 50 ký tự: chữ, số, dấu chấm, gạch dưới hoặc gạch ngang.'
                      : null,
                ),
                TextFormField(
                  controller: _password,
                  obscureText: true,
                  decoration: const InputDecoration(labelText: 'Mật khẩu'),
                  validator: (value) =>
                      value == null || value.length < 8 || value.length > 72
                      ? 'Mật khẩu phải có từ 8 đến 72 ký tự.'
                      : null,
                ),
                DropdownButtonFormField<String>(
                  initialValue: _role,
                  decoration: const InputDecoration(labelText: 'Vai trò'),
                  items: [
                    for (final role in accountRoles)
                      DropdownMenuItem(value: role, child: Text(role)),
                  ],
                  onChanged: (value) =>
                      setState(() => _role = value ?? 'DOCTOR'),
                ),
                TextFormField(
                  controller: _ownerId,
                  decoration: InputDecoration(
                    labelText: _role == 'PATIENT'
                        ? 'Mã bệnh nhân'
                        : 'Mã nhân viên',
                  ),
                  validator: (value) =>
                      value == null || !isOrganizationId(value.trim())
                      ? 'Nhập UUID hợp lệ.'
                      : null,
                ),
                if (_uncertainCreate)
                  const Padding(
                    padding: EdgeInsets.symmetric(vertical: 8),
                    child: Text(
                      'Kết quả tạo chưa xác định. Không gửi lại biểu mẫu này để tránh tạo trùng tài khoản.',
                    ),
                  ),
                FilledButton.icon(
                  onPressed: _saving || _uncertainCreate ? null : _create,
                  icon: const Icon(Icons.person_add_alt_1),
                  label: const Text('Tạo tài khoản'),
                ),
              ],
            ),
          ),
        ),
      ),
      Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Form(
            key: _statusForm,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(
                  'Khóa hoặc mở tài khoản',
                  style: Theme.of(context).textTheme.titleLarge,
                ),
                const SizedBox(height: 8),
                const Text(
                  'API hiện không cung cấp danh sách tài khoản. Nhập mã tài khoản đã nhận khi tạo.',
                ),
                TextFormField(
                  controller: _accountId,
                  decoration: const InputDecoration(labelText: 'Mã tài khoản'),
                  validator: (value) =>
                      value == null || !isOrganizationId(value.trim())
                      ? 'Nhập UUID hợp lệ.'
                      : null,
                ),
                DropdownButtonFormField<bool>(
                  initialValue: _active,
                  decoration: const InputDecoration(labelText: 'Trạng thái'),
                  items: const [
                    DropdownMenuItem(
                      value: true,
                      child: Text('Đang hoạt động'),
                    ),
                    DropdownMenuItem(
                      value: false,
                      child: Text('Khóa tài khoản'),
                    ),
                  ],
                  onChanged: (value) => setState(() => _active = value ?? true),
                ),
                FilledButton.icon(
                  onPressed: _saving ? null : _updateStatus,
                  icon: const Icon(Icons.manage_accounts_outlined),
                  label: const Text('Cập nhật trạng thái'),
                ),
              ],
            ),
          ),
        ),
      ),
    ],
  );

  Future<void> _create() async {
    if (!_createForm.currentState!.validate()) return;
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      final account = await SaveAccount(widget.repository).create(
        AccountDraft(
          username: _username.text.trim(),
          password: _password.text,
          ownerId: _ownerId.text.trim(),
          role: _role,
        ),
      );
      if (mounted) {
        setState(() => _result = account);
        _password.clear();
      }
    } catch (error) {
      if (mounted) {
        setState(() {
          _error = error;
          if (_outcomeMayBeUnknown(error)) _uncertainCreate = true;
        });
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _updateStatus() async {
    if (!_statusForm.currentState!.validate()) return;
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      final account = await SaveAccount(widget.repository)
          .updateStatus(_accountId.text.trim(), _active);
      if (mounted) setState(() => _result = account);
    } catch (error) {
      if (mounted) setState(() => _error = error);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }
}

bool _outcomeMayBeUnknown(Object error) =>
    error is! ApiFailure || error.status == null || error.status! >= 500;
