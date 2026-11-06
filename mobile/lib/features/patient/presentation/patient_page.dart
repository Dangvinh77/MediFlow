import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mediflow_loader/mediflow_loader.dart';

import '../../../core/domain/workspace_access.dart';
import '../../../core/network/api_failure.dart';
import '../../../core/providers.dart';
import '../../../core/theme/medical_colors.dart';
import '../../../core/widgets/medical_ui.dart';
import '../../../core/widgets/paged_list.dart';
import '../../../core/widgets/request_state.dart';
import '../domain/patient.dart';
import 'providers.dart';

typedef PatientAccountPanelBuilder = Widget Function(
  BuildContext context,
  Patient patient,
  WorkspaceAccess access,
);

bool _canRetry(Object? error) =>
    error is! FormatException &&
    error is! ArgumentError &&
    (error is! ApiFailure ||
        error.status == null ||
        error.status! >= 500 ||
        error.status == 429);

class _PatientBoundary extends ConsumerWidget {
  const _PatientBoundary({required this.builder});
  final Widget Function(PatientRepository repository, WorkspaceAccess access)
  builder;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final access = ref.watch(workspaceAccessProvider);
    final repository = ref.watch(patientRepositoryProvider);
    if (access == null) {
      return const Center(child: Text('Phiên đăng nhập chưa sẵn sàng.'));
    }
    if (!const {'ADMIN', 'DOCTOR', 'NURSE'}.contains(access.role)) {
      return const Center(
        child: Text('Bạn không có quyền xem hồ sơ bệnh nhân.'),
      );
    }
    return KeyedSubtree(
      key: ValueKey((access.sessionIdentity, repository)),
      child: builder(repository, access),
    );
  }
}

class PatientPage extends StatelessWidget {
  const PatientPage({
    super.key,
    this.initialPatientId,
    this.initiallyCreate = false,
    this.accountPanelBuilder,
    this.onRegisterPatient,
  });

  final String? initialPatientId;
  final bool initiallyCreate;
  final PatientAccountPanelBuilder? accountPanelBuilder;
  final ValueChanged<String>? onRegisterPatient;

  @override
  Widget build(BuildContext context) => _PatientBoundary(
    builder: (repository, access) {
      final patientId = initialPatientId;
      if (patientId != null) {
        return _PatientDetail(
          key: ValueKey((patientId, access.sessionIdentity)),
          repository: repository,
          access: access,
          patientId: patientId,
          accountPanelBuilder: accountPanelBuilder,
          onRegisterPatient: onRegisterPatient,
        );
      }
      if (initiallyCreate) {
        return _InitialPatientCreateFlow(
          accountPanelBuilder: accountPanelBuilder,
          onRegisterPatient: onRegisterPatient,
        );
      }
      return _PatientList(
        repository: repository,
        access: access,
        accountPanelBuilder: accountPanelBuilder,
        onRegisterPatient: onRegisterPatient,
      );
    },
  );
}

class _InitialPatientCreateFlow extends StatefulWidget {
  const _InitialPatientCreateFlow({
    this.accountPanelBuilder,
    this.onRegisterPatient,
  });

  final PatientAccountPanelBuilder? accountPanelBuilder;
  final ValueChanged<String>? onRegisterPatient;

  @override
  State<_InitialPatientCreateFlow> createState() =>
      _InitialPatientCreateFlowState();
}

class _InitialPatientCreateFlowState extends State<_InitialPatientCreateFlow> {
  String? _createdPatientId;

  @override
  Widget build(BuildContext context) {
    final createdPatientId = _createdPatientId;
    if (createdPatientId != null) {
      return PatientDetailPage(
        patientId: createdPatientId,
        accountPanelBuilder: widget.accountPanelBuilder,
        onRegisterPatient: widget.onRegisterPatient,
      );
    }
    return PatientEditorPage(
      onSaved: () {},
      onCreated: (patient) {
        if (mounted) setState(() => _createdPatientId = patient.id);
      },
    );
  }
}

class _PatientList extends StatefulWidget {
  const _PatientList({
    required this.repository,
    required this.access,
    this.accountPanelBuilder,
    this.onRegisterPatient,
  });
  final PatientRepository repository;
  final WorkspaceAccess access;
  final PatientAccountPanelBuilder? accountPanelBuilder;
  final ValueChanged<String>? onRegisterPatient;
  @override
  State<_PatientList> createState() => _PatientListState();
}

class _PatientListState extends State<_PatientList> {
  final _keyword = TextEditingController();
  String? _submitted;
  int _refreshIdentity = 0;

  @override
  void dispose() {
    _keyword.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Column(
    children: [
      Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            TextField(
              controller: _keyword,
              textInputAction: TextInputAction.search,
              decoration: const InputDecoration(
                labelText: 'Tìm theo tên hoặc mã bệnh nhân',
                prefixIcon: Icon(Icons.search),
              ),
              onSubmitted: (_) => _search(),
            ),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                FilledButton.icon(
                  onPressed: _search,
                  icon: const Icon(Icons.search),
                  label: const Text('Tìm kiếm'),
                ),
                OutlinedButton.icon(
                  onPressed: _clearSearch,
                  icon: const Icon(Icons.clear),
                  label: const Text('Xóa tìm kiếm'),
                ),
                IconButton(
                  tooltip: 'Làm mới danh sách',
                  onPressed: _refresh,
                  icon: const Icon(Icons.refresh),
                ),
                if (const {'ADMIN', 'NURSE'}.contains(widget.access.role))
                  FilledButton.tonalIcon(
                    onPressed: () => _openEditor(context),
                    icon: const Icon(Icons.person_add_alt_1),
                    label: const Text('Thêm bệnh nhân'),
                  ),
              ],
            ),
          ],
        ),
      ),
      Expanded(
        child: PagedList<Patient>(
          key: ValueKey((_submitted, _refreshIdentity)),
          load: (page) =>
              FindPatients(widget.repository)(keyword: _submitted, page: page),
          canRetry: _canRetry,
          itemBuilder: (context, patient) => Card(
            child: ListTile(
              leading: const IconBadge(
                Icons.person_outline,
                color: Wayfinding.patient,
              ),
              title: Text(patient.name),
              subtitle: Text('CCCD/CMND: ${patient.identityNumber}'),
              trailing: const Icon(Icons.chevron_right),
              onTap: () => _openDetail(context, patient.id),
            ),
          ),
        ),
      ),
    ],
  );

  void _search() {
    FocusScope.of(context).unfocus();
    setState(() {
      final value = _keyword.text.trim();
      _submitted = value.isEmpty ? null : value;
      _refreshIdentity++;
    });
  }

  void _clearSearch() {
    _keyword.clear();
    setState(() {
      _submitted = null;
      _refreshIdentity++;
    });
  }

  void _refresh() => setState(() => _refreshIdentity++);

  Future<void> _openDetail(BuildContext context, String id) async {
    await Navigator.of(context).push<void>(
      MaterialPageRoute(
        builder: (_) => Scaffold(
          appBar: AppBar(title: const Text('Hồ sơ bệnh nhân')),
          body: PatientDetailPage(
            patientId: id,
            accountPanelBuilder: widget.accountPanelBuilder,
            onRegisterPatient: widget.onRegisterPatient,
          ),
        ),
      ),
    );
    if (mounted) _refresh();
  }

  Future<void> _openEditor(BuildContext context) async {
    await Navigator.of(context).push<void>(
      MaterialPageRoute(
        builder: (_) => Scaffold(
          appBar: AppBar(title: const Text('Thêm bệnh nhân')),
          body: PatientEditorPage(onSaved: () => Navigator.of(context).pop()),
        ),
      ),
    );
    if (mounted) _refresh();
  }
}

class PatientDetailPage extends StatelessWidget {
  const PatientDetailPage({
    super.key,
    required this.patientId,
    this.accountPanelBuilder,
    this.onRegisterPatient,
  });
  final String patientId;
  final PatientAccountPanelBuilder? accountPanelBuilder;
  final ValueChanged<String>? onRegisterPatient;

  @override
  Widget build(BuildContext context) => _PatientBoundary(
    builder: (repository, access) => _PatientDetail(
      key: ValueKey(patientId),
      repository: repository,
      access: access,
      patientId: patientId,
      accountPanelBuilder: accountPanelBuilder,
      onRegisterPatient: onRegisterPatient,
    ),
  );
}

class _PatientDetail extends StatefulWidget {
  const _PatientDetail({
    super.key,
    required this.repository,
    required this.access,
    required this.patientId,
    this.accountPanelBuilder,
    this.onRegisterPatient,
  });
  final PatientRepository repository;
  final WorkspaceAccess access;
  final String patientId;
  final PatientAccountPanelBuilder? accountPanelBuilder;
  final ValueChanged<String>? onRegisterPatient;
  @override
  State<_PatientDetail> createState() => _PatientDetailState();
}

class _PatientDetailState extends State<_PatientDetail> {
  Patient? _patient;
  Object? _error;
  Object? _commandError;
  bool _loading = true;
  bool _deleting = false;
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
      _commandError = null;
      _patient = null;
    });
    try {
      final patient = await ReadPatient(widget.repository)(widget.patientId);
      if (mounted && generation == _generation) {
        setState(() => _patient = patient);
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
    if (_loading) {
      return const Center(
        child: MediFlowLoader(semanticLabel: 'Đang tải bệnh nhân'),
      );
    }
    if (_error != null) {
      return SingleChildScrollView(
        child: RequestError(_error!, onRetry: _canRetry(_error) ? _load : null),
      );
    }
    final patient = _patient!;
    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.all(16),
        children: [
          Text(patient.name, style: Theme.of(context).textTheme.headlineSmall),
          const SizedBox(height: 16),
          ExpansionTile(
            title: const Text('Thông tin kỹ thuật'),
            children: [_PatientField('Mã bệnh nhân', patient.id)],
          ),
          _PatientField('Ngày sinh', patient.birthDate),
          _PatientField('Giới tính', patient.gender == 'M' ? 'Nam' : 'Nữ'),
          _PatientField('CCCD/CMND', patient.identityNumber),
          _PatientField('Điện thoại', patient.phoneNumber),
          _PatientField('Email', patient.email),
          _PatientField('Địa chỉ', patient.address),
          _PatientField('Mã BHYT', patient.insuranceNumber),
          if (widget.accountPanelBuilder case final builder?)
            builder(context, patient, widget.access),
          _PatientField('Tạo lúc', patient.createdAt),
          _PatientField('Cập nhật', patient.updatedAt),
          if (_commandError != null) ...[
            const SizedBox(height: 8),
            RequestError(
              _commandError!,
              onRetry: _canRetry(_commandError) ? _load : null,
            ),
          ],
          const SizedBox(height: 16),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              if (const {'ADMIN', 'NURSE'}.contains(widget.access.role))
                OutlinedButton.icon(
                  onPressed: _deleting ? null : () => _edit(patient),
                  icon: const Icon(Icons.edit_outlined),
                  label: const Text('Chỉnh sửa hồ sơ'),
                ),
              if (widget.access.role == 'ADMIN')
                FilledButton.tonalIcon(
                  onPressed: _deleting ? null : () => _confirmDelete(patient),
                  icon: const Icon(Icons.delete_outline),
                  label: const Text('Xóa hồ sơ'),
                ),
              if (!patient.archived &&
                  widget.onRegisterPatient != null &&
                  const {'ADMIN', 'NURSE'}.contains(widget.access.role))
                FilledButton.icon(
                  onPressed: _deleting
                      ? null
                      : () => widget.onRegisterPatient!(patient.id),
                  icon: const Icon(Icons.event_available_outlined),
                  label: const Text('Đăng ký khám'),
                ),
            ],
          ),
          if (_deleting) const LinearProgressIndicator(),
        ],
      ),
    );
  }

  Future<void> _edit(Patient patient) async {
    await Navigator.of(context).push<void>(
      MaterialPageRoute(
        builder: (_) => Scaffold(
          appBar: AppBar(title: const Text('Chỉnh sửa bệnh nhân')),
          body: PatientEditorPage(
            patientId: patient.id,
            onSaved: () => Navigator.of(context).pop(),
          ),
        ),
      ),
    );
    if (mounted) _load();
  }

  Future<void> _confirmDelete(Patient patient) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => Consumer(
        builder: (context, ref, _) {
          bool belongsToCurrentAdminSession() {
            final access = ref.read(workspaceAccessProvider);
            return access?.sessionIdentity == widget.access.sessionIdentity &&
                access?.role == 'ADMIN';
          }

          final access = ref.watch(workspaceAccessProvider);
          if (access?.sessionIdentity != widget.access.sessionIdentity ||
              access?.role != 'ADMIN') {
            return AlertDialog(
              title: const Text('Phiên đăng nhập đã thay đổi'),
              content: const Text('Đóng hộp thoại này trước khi tiếp tục.'),
              actions: [
                TextButton(
                  onPressed: () => Navigator.of(dialogContext).pop(false),
                  child: const Text('Đóng'),
                ),
              ],
            );
          }
          return AlertDialog(
            title: const Text('Xóa hồ sơ bệnh nhân?'),
            content: Text('Hồ sơ của ${patient.name} sẽ bị xóa.'),
            actions: [
              TextButton(
                onPressed: () => Navigator.of(dialogContext).pop(false),
                child: const Text('Hủy'),
              ),
              FilledButton(
                onPressed: () =>
                    Navigator.of(dialogContext)
                        .pop(belongsToCurrentAdminSession()),
                child: const Text('Xóa hồ sơ'),
              ),
            ],
          );
        },
      ),
    );
    if (confirmed != true || !mounted) return;
    setState(() {
      _deleting = true;
      _commandError = null;
    });
    try {
      await widget.repository.delete(patient.id);
      if (mounted) Navigator.of(context).pop();
    } catch (error) {
      if (mounted) setState(() => _commandError = error);
    } finally {
      if (mounted) setState(() => _deleting = false);
    }
  }
}

class _PatientField extends StatelessWidget {
  const _PatientField(this.label, this.value);
  final String label;
  final String? value;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 6),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(width: 120, child: Text(label)),
        Expanded(
          child: SelectableText(value?.isNotEmpty == true ? value! : '—'),
        ),
      ],
    ),
  );
}

class PatientEditorPage extends StatelessWidget {
  const PatientEditorPage({
    super.key,
    this.patientId,
    required this.onSaved,
    this.onCreated,
  });
  final String? patientId;
  final VoidCallback onSaved;
  final ValueChanged<Patient>? onCreated;
  @override
  Widget build(BuildContext context) => _PatientBoundary(
    builder: (repository, access) {
      if (!const {'ADMIN', 'NURSE'}.contains(access.role)) {
        return const Center(child: Text('Bạn không có quyền thay đổi hồ sơ.'));
      }
      return KeyedSubtree(
        key: ValueKey((access.sessionIdentity, repository, patientId)),
        child: _PatientForm(
          repository: repository,
          patientId: patientId,
          onSaved: onSaved,
          onCreated: onCreated,
        ),
      );
    },
  );
}

class _PatientForm extends StatefulWidget {
  const _PatientForm({
    required this.repository,
    required this.patientId,
    required this.onSaved,
    this.onCreated,
  });
  final PatientRepository repository;
  final String? patientId;
  final VoidCallback onSaved;
  final ValueChanged<Patient>? onCreated;
  @override
  State<_PatientForm> createState() => _PatientFormState();
}

class _PatientFormState extends State<_PatientForm> {
  final _form = GlobalKey<FormState>();
  final _name = TextEditingController();
  final _birthDate = TextEditingController();
  final _identity = TextEditingController();
  final _address = TextEditingController();
  final _phone = TextEditingController();
  final _email = TextEditingController();
  final _insurance = TextEditingController();
  String _gender = 'M';
  Patient? _loaded;
  Object? _loadError, _saveError;
  bool _loading = false, _saving = false, _uncertainCreate = false;
  int _generation = 0;

  bool get _editing => widget.patientId != null;

  @override
  void initState() {
    super.initState();
    if (_editing) _load();
  }

  @override
  void dispose() {
    _generation++;
    _name.dispose();
    _birthDate.dispose();
    _identity.dispose();
    _address.dispose();
    _phone.dispose();
    _email.dispose();
    _insurance.dispose();
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
      final patient = await ReadPatient(widget.repository)(widget.patientId!);
      if (mounted && generation == _generation) {
        _name.text = patient.name;
        _birthDate.text = patient.birthDate;
        _identity.text = patient.identityNumber;
        _gender = patient.gender;
        _address.text = patient.address ?? '';
        _phone.text = patient.phoneNumber ?? '';
        _email.text = patient.email ?? '';
        _insurance.text = patient.insuranceNumber ?? '';
        setState(() => _loaded = patient);
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
      return const Center(
        child: MediFlowLoader(semanticLabel: 'Đang tải hồ sơ'),
      );
    }
    if (_editing && _loadError != null) {
      return SingleChildScrollView(
        child: RequestError(
          _loadError!,
          onRetry: _canRetry(_loadError) ? _load : null,
        ),
      );
    }
    if (_editing && _loaded == null) {
      return const Center(child: Text('Không thể mở hồ sơ bệnh nhân.'));
    }
    return Form(
      key: _form,
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            TextFormField(
              controller: _name,
              decoration: const InputDecoration(labelText: 'Họ và tên *'),
              maxLength: 100,
              validator: (value) => value == null || value.trim().isEmpty
                  ? 'Vui lòng nhập họ tên.'
                  : value.trim().length > 100
                  ? 'Họ tên tối đa 100 ký tự.'
                  : null,
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _birthDate,
              readOnly: true,
              decoration: const InputDecoration(labelText: 'Ngày sinh *'),
              onTap: _pickBirthDate,
              validator: (value) => isValidPatientBirthDate(value ?? '')
                  ? null
                  : 'Vui lòng chọn ngày sinh hợp lệ, không ở tương lai.',
            ),
            const SizedBox(height: 12),
            DropdownButtonFormField<String>(
              isExpanded: true,
              initialValue: _gender,
              decoration: const InputDecoration(labelText: 'Giới tính *'),
              items: const [
                DropdownMenuItem(value: 'M', child: Text('Nam')),
                DropdownMenuItem(value: 'F', child: Text('Nữ')),
              ],
              onChanged: (value) => setState(() => _gender = value ?? 'M'),
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _identity,
              decoration: const InputDecoration(labelText: 'CCCD/CMND *'),
              maxLength: 20,
              validator: (value) => value == null || value.trim().isEmpty
                  ? 'Vui lòng nhập số CCCD/CMND.'
                  : value.trim().length > 20
                  ? 'Tối đa 20 ký tự.'
                  : null,
            ),
            const SizedBox(height: 12),
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
            const SizedBox(height: 12),
            TextFormField(
              controller: _email,
              decoration: const InputDecoration(labelText: 'Email'),
              keyboardType: TextInputType.emailAddress,
              maxLength: 254,
              validator: (value) =>
                  value != null &&
                      value.trim().isNotEmpty &&
                      (value.trim().length > 254 ||
                          !RegExp(r'^[^\s@]+@[^\s@]+\.[^\s@]+$')
                              .hasMatch(value.trim()))
                  ? 'Email không hợp lệ.'
                  : null,
            ),
            if (_editing)
              const Padding(
                padding: EdgeInsets.only(top: 8),
                child: Text(
                  'Email của tài khoản đã cấp được đổi trong mục Tài khoản của hồ sơ. Nếu chưa có tài khoản, cập nhật email tại đây rồi cấp tài khoản.',
                ),
              ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _address,
              decoration: const InputDecoration(labelText: 'Địa chỉ'),
              maxLength: 255,
              maxLines: 2,
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _insurance,
              decoration: const InputDecoration(labelText: 'Mã BHYT'),
              validator: (value) =>
                  value != null &&
                      value.trim().isNotEmpty &&
                      !RegExp(r'^\d{2}-\d{8}-\d$').hasMatch(value.trim())
                  ? 'Mã BHYT phải theo dạng 12-12345678-1.'
                  : null,
            ),
            if (_saveError != null) ...[
              const SizedBox(height: 8),
              RequestError(_saveError!),
            ],
            if (_uncertainCreate)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 12),
                child: Text(
                  'Kết quả tạo chưa xác định. Hãy quay lại danh sách và làm mới trước khi tạo lại để tránh hồ sơ trùng.',
                ),
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
              label: Text(_editing ? 'Lưu thay đổi' : 'Tạo bệnh nhân'),
            ),
            if (_uncertainCreate)
              TextButton(
                onPressed: () => Navigator.of(context).pop(),
                child: const Text('Quay lại danh sách để làm mới'),
              ),
          ],
        ),
      ),
    );
  }

  Future<void> _pickBirthDate() async {
    final existing = DateTime.tryParse(_birthDate.text);
    final selected = await showDatePicker(
      context: context,
      initialDate: existing ?? DateTime(2000),
      firstDate: DateTime(1900),
      lastDate: DateTime.now(),
    );
    if (selected != null && mounted) {
      _birthDate.text =
          '${selected.year.toString().padLeft(4, '0')}-${selected.month.toString().padLeft(2, '0')}-${selected.day.toString().padLeft(2, '0')}';
      _form.currentState?.validate();
    }
  }

  Future<void> _save() async {
    if (!_form.currentState!.validate()) return;
    setState(() {
      _saving = true;
      _saveError = null;
    });
    final draft = PatientDraft(
      name: _name.text.trim(),
      birthDate: _birthDate.text,
      gender: _gender,
      identityNumber: _identity.text.trim(),
      address: _address.text,
      phoneNumber: _phone.text,
      email: _email.text,
      insuranceNumber: _insurance.text,
    );
    try {
      late final Patient savedPatient;
      if (_editing) {
        savedPatient = await SavePatient(widget.repository)
            .update(widget.patientId!, draft);
      } else {
        savedPatient = await SavePatient(widget.repository).create(draft);
      }
      if (mounted) {
        widget.onSaved();
        if (!_editing) widget.onCreated?.call(savedPatient);
      }
    } catch (error) {
      if (mounted) {
        setState(() {
          _saveError = error;
          if (!_editing && _outcomeMayBeUnknown(error)) _uncertainCreate = true;
        });
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }
}

bool _outcomeMayBeUnknown(Object error) =>
    error is! ApiFailure || error.status == null || error.status! >= 500;
