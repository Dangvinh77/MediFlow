import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_failure.dart';
import '../../../core/providers.dart';
import '../../../core/widgets/reference_widgets.dart';
import '../domain/medical_record.dart';

final medicalRecordRepositoryProvider = Provider<MedicalRecordRepository>(
  (ref) => throw StateError(
    'MedicalRecordRepository must be bound in app/dependencies.dart',
  ),
);
final medicalRecordOutpatientUiEnabledProvider = Provider<bool>(
  (ref) => const bool.fromEnvironment(
    'MEDIFLOW_OUTPATIENT_UI_ENABLED',
    defaultValue: false,
  ),
);

class MedicalRecordPage extends ConsumerStatefulWidget {
  const MedicalRecordPage({super.key, this.onLabTestCreated});
  final ValueChanged<LabTestReceipt>? onLabTestCreated;
  @override
  ConsumerState<MedicalRecordPage> createState() => _MedicalRecordPageState();
}

class _MedicalRecordPageState extends ConsumerState<MedicalRecordPage> {
  String? _patientId;
  List<MedicalRecord>? _records;
  Object? _error;
  bool _loading = false;
  int _generation = 0;
  Object? _identity;

  @override
  Widget build(BuildContext context) {
    final access = ref.watch(workspaceAccessProvider);
    final outpatientEnabled = ref.watch(
      medicalRecordOutpatientUiEnabledProvider,
    );
    if (access == null) {
      return const Center(child: Text('Đăng nhập để tra cứu hồ sơ khám.'));
    }
    if (!const {'ADMIN', 'DOCTOR', 'NURSE'}.contains(access.role)) {
      return const Center(
        child: Text('Bạn không có quyền tra cứu hồ sơ khám.'),
      );
    }
    if (_identity != access.sessionIdentity) {
      _identity = access.sessionIdentity;
      _patientId = null;
      _records = null;
      _error = null;
      _loading = false;
      _generation++;
    }
    final canCreate =
        const {'ADMIN', 'DOCTOR'}.contains(access.role) && !outpatientEnabled;
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.all(12),
          child: Wrap(
            spacing: 12,
            runSpacing: 8,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              SizedBox(
                width: 340,
                child: ReferencePicker(
                  kind: ReferenceKind.patient,
                  label: 'Bệnh nhân cần tra cứu',
                  value: _patientId,
                  onChanged: _selectPatient,
                  required: false,
                ),
              ),
              if (_patientId != null)
                OutlinedButton.icon(
                  onPressed: _loading ? null : () => _loadPatient(_patientId!),
                  icon: const Icon(Icons.refresh),
                  label: const Text('Làm mới hồ sơ'),
                ),
              if (canCreate)
                FilledButton.icon(
                  onPressed: () async {
                    final created = await Navigator.of(context).push<bool>(
                      MaterialPageRoute(
                        builder: (_) => const MedicalRecordEditorPage(),
                      ),
                    );
                    if (created == true && mounted && _patientId != null) {
                      _loadPatient(_patientId!);
                    }
                  },
                  icon: const Icon(Icons.add),
                  label: const Text('Tạo hồ sơ V0'),
                ),
            ],
          ),
        ),
        if (outpatientEnabled)
          const Padding(
            padding: EdgeInsets.symmetric(horizontal: 16, vertical: 4),
            child: Align(
              alignment: Alignment.centerLeft,
              child: Text('Hồ sơ được tạo khi bắt đầu khám từ lịch hẹn.'),
            ),
          ),
        Expanded(child: _buildResults()),
      ],
    );
  }

  void _selectPatient(String? patientId) {
    setState(() {
      _patientId = patientId;
      _records = null;
      _error = null;
      _loading = false;
    });
    if (patientId != null) _loadPatient(patientId);
  }

  Future<void> _loadPatient(String patientId) async {
    final generation = ++_generation;
    final identity = ref.read(workspaceAccessProvider)?.sessionIdentity;
    setState(() {
      _patientId = patientId;
      _records = null;
      _error = null;
      _loading = true;
    });
    try {
      final values = await ref
          .read(medicalRecordRepositoryProvider)
          .byPatient(patientId);
      if (mounted &&
          generation == _generation &&
          ref.read(workspaceAccessProvider)?.sessionIdentity == identity &&
          _patientId == patientId) {
        setState(() {
          _records = values;
          _loading = false;
        });
      }
    } catch (error) {
      if (mounted &&
          generation == _generation &&
          ref.read(workspaceAccessProvider)?.sessionIdentity == identity) {
        setState(() {
          _error = error;
          _loading = false;
        });
      }
    }
  }

  Widget _buildResults() {
    if (_loading) return const Center(child: CircularProgressIndicator());
    if (_error != null) {
      return _RetryMessage(
        error: _error!,
        onRetry: _patientId == null ? null : () => _loadPatient(_patientId!),
      );
    }
    if (_patientId == null) {
      return const Center(
        child: Text('Chọn bệnh nhân theo tên để xem hồ sơ khám.'),
      );
    }
    final records = _records;
    if (records == null) return const SizedBox.shrink();
    if (records.isEmpty) {
      return const Center(child: Text('Bệnh nhân chưa có hồ sơ khám.'));
    }
    return ListView.separated(
      padding: const EdgeInsets.all(16),
      itemCount: records.length,
      separatorBuilder: (_, _) => const SizedBox(height: 8),
      itemBuilder: (context, index) => _RecordCard(
        record: records[index],
        onOpen: () => Navigator.of(context).push<void>(
          MaterialPageRoute(
            builder: (_) => MedicalRecordDetailPage(
              recordId: records[index].id,
              onLabTestCreated: widget.onLabTestCreated,
            ),
          ),
        ),
      ),
    );
  }
}

class _RecordCard extends StatelessWidget {
  const _RecordCard({required this.record, required this.onOpen});
  final MedicalRecord record;
  final VoidCallback onOpen;
  @override
  Widget build(BuildContext context) => Card(
    child: InkWell(
      onTap: onOpen,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    'Ngày khám: ${record.examinationDate}',
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                ),
                Chip(
                  label: Text(
                    record.status == 'OPEN'
                        ? 'Đang khám'
                        : record.status == 'COMPLETED'
                        ? 'Đã hoàn tất'
                        : 'Trạng thái khác',
                  ),
                ),
              ],
            ),
            ReferenceLabel(
              kind: ReferenceKind.patient,
              id: record.patientId,
              label: 'Bệnh nhân',
            ),
            ReferenceLabel(
              kind: ReferenceKind.doctor,
              id: record.doctorId,
              label: 'Bác sĩ',
            ),
            ReferenceLabel(
              kind: ReferenceKind.department,
              id: record.departmentId,
              label: 'Khoa',
            ),
            if (record.appointmentId != null)
              ReferenceLabel(
                kind: ReferenceKind.appointment,
                id: record.appointmentId!,
                label: 'Lịch hẹn',
              ),
            Text(
              record.diagnoses.isEmpty
                  ? 'Chưa có chẩn đoán'
                  : record.diagnoses.map((d) => d.name).join(' · '),
            ),
            Align(
              alignment: Alignment.centerRight,
              child: TextButton.icon(
                onPressed: onOpen,
                icon: const Icon(Icons.open_in_new),
                label: const Text('Mở hồ sơ'),
              ),
            ),
          ],
        ),
      ),
    ),
  );
}

class MedicalRecordEditorPage extends ConsumerStatefulWidget {
  const MedicalRecordEditorPage({super.key});
  @override
  ConsumerState<MedicalRecordEditorPage> createState() =>
      _MedicalRecordEditorPageState();
}

class _MedicalRecordEditorPageState
    extends ConsumerState<MedicalRecordEditorPage> {
  final _formKey = GlobalKey<FormState>();
  final _symptoms = TextEditingController();
  final _diagnosisName = TextEditingController();
  final _diagnosisCode = TextEditingController();
  String? _patientId, _doctorId, _departmentId, _appointmentId;
  DateTime _date = DateTime.now();
  Object? _error;
  bool _busy = false, _uncertain = false;
  Object? _identity;

  @override
  void initState() {
    super.initState();
    _identity = ref.read(workspaceAccessProvider)?.sessionIdentity;
  }

  @override
  void dispose() {
    _symptoms.dispose();
    _diagnosisName.dispose();
    _diagnosisCode.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (_busy || _uncertain || !_formKey.currentState!.validate()) return;
    final access = ref.read(workspaceAccessProvider);
    if (access?.sessionIdentity != _identity ||
        ref.read(medicalRecordOutpatientUiEnabledProvider) ||
        !const {'ADMIN', 'DOCTOR'}.contains(access?.role)) {
      return;
    }
    final draft = CreateMedicalRecord(
      patientId: _patientId ?? '',
      doctorId: _doctorId ?? '',
      departmentId: _departmentId ?? '',
      examinationDate: _dateString(_date),
      symptoms: _symptoms.text,
      appointmentId: _appointmentId,
      diagnoses: [
        DiagnosisDraft(
          diagnosisName: _diagnosisName.text,
          icdCode: _diagnosisCode.text,
        ),
      ],
    );
    final issue = validateRecordDraft(draft);
    if (issue != null) {
      setState(() => _error = issue);
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ref.read(medicalRecordRepositoryProvider).create(draft);
      if (!mounted ||
          ref.read(workspaceAccessProvider)?.sessionIdentity != _identity) {
        return;
      }
      Navigator.of(context).pop(true);
    } catch (error) {
      if (!mounted ||
          ref.read(workspaceAccessProvider)?.sessionIdentity != _identity) {
        return;
      }
      setState(() {
        _error = error;
        _uncertain = _outcomeUnknown(error);
      });
    } finally {
      if (mounted &&
          ref.read(workspaceAccessProvider)?.sessionIdentity == _identity) {
        setState(() => _busy = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final access = ref.watch(workspaceAccessProvider);
    final outpatientEnabled = ref.watch(
      medicalRecordOutpatientUiEnabledProvider,
    );
    if (access?.sessionIdentity != _identity ||
        outpatientEnabled ||
        !const {'ADMIN', 'DOCTOR'}.contains(access?.role)) {
      return Scaffold(
        appBar: AppBar(title: const Text('Tạo hồ sơ')),
        body: const Center(
          child: Text('Tạo hồ sơ thủ công hiện không khả dụng.'),
        ),
      );
    }
    return Scaffold(
      appBar: AppBar(title: const Text('Tạo hồ sơ khám V0')),
      body: Form(
        key: _formKey,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            const Text(
              'Chỉ tạo hồ sơ thủ công khi quy trình ngoại trú V1 chưa được bật.',
            ),
            ReferencePicker(
              kind: ReferenceKind.patient,
              label: 'Bệnh nhân',
              value: _patientId,
              required: true,
              onChanged: (v) => setState(() {
                _patientId = v;
                _appointmentId = null;
              }),
            ),
            ReferencePicker(
              kind: ReferenceKind.department,
              label: 'Khoa',
              value: _departmentId,
              required: true,
              onChanged: (v) => setState(() {
                _departmentId = v;
                _doctorId = null;
              }),
            ),
            ReferencePicker(
              kind: ReferenceKind.doctor,
              label: 'Bác sĩ',
              value: _doctorId,
              scope: _departmentId,
              required: true,
              onChanged: (v) => setState(() => _doctorId = v),
            ),
            if (_patientId != null)
              ReferencePicker(
                kind: ReferenceKind.appointment,
                label: 'Lịch hẹn (nếu có)',
                value: _appointmentId,
                scope: _patientId,
                onChanged: (v) => setState(() => _appointmentId = v),
              ),
            OutlinedButton.icon(
              onPressed: _busy || _uncertain
                  ? null
                  : () async {
                      final date = await showDatePicker(
                        context: context,
                        initialDate: _date,
                        firstDate: DateTime(2000),
                        lastDate: DateTime.now(),
                      );
                      if (date != null && mounted) setState(() => _date = date);
                    },
              icon: const Icon(Icons.calendar_month),
              label: Text('Ngày khám: ${_dateString(_date)}'),
            ),
            TextFormField(
              controller: _symptoms,
              maxLength: 4000,
              maxLines: 3,
              decoration: const InputDecoration(labelText: 'Triệu chứng'),
              validator: (v) =>
                  (v?.length ?? 0) > 2000 ? 'Tối đa 2000 ký tự.' : null,
            ),
            TextFormField(
              controller: _diagnosisName,
              maxLength: 255,
              decoration: const InputDecoration(
                labelText: 'Chẩn đoán ban đầu *',
              ),
              validator: (v) =>
                  v == null || v.trim().isEmpty ? 'Nhập chẩn đoán.' : null,
            ),
            TextFormField(
              controller: _diagnosisCode,
              maxLength: 32,
              decoration: const InputDecoration(
                labelText: 'Mã ICD (không bắt buộc)',
              ),
            ),
            if (_error != null) _RetryMessage(error: _error!),
            if (_uncertain)
              const Text(
                'Kết quả tạo hồ sơ chưa rõ. Không gửi lại; hãy tra cứu bệnh nhân để xác nhận hồ sơ trước.',
                style: TextStyle(color: Colors.deepOrange),
              ),
            FilledButton.icon(
              onPressed: _busy || _uncertain ? null : _save,
              icon: _busy
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.save),
              label: const Text('Tạo hồ sơ'),
            ),
          ],
        ),
      ),
    );
  }
}

class MedicalRecordDetailPage extends ConsumerStatefulWidget {
  const MedicalRecordDetailPage({
    super.key,
    required this.recordId,
    this.onLabTestCreated,
  });
  final String recordId;
  final ValueChanged<LabTestReceipt>? onLabTestCreated;
  @override
  ConsumerState<MedicalRecordDetailPage> createState() =>
      _MedicalRecordDetailPageState();
}

class _MedicalRecordDetailPageState
    extends ConsumerState<MedicalRecordDetailPage> {
  MedicalRecord? _record;
  Object? _error;
  bool _loading = true, _busy = false, _uncertain = false;
  int _generation = 0;
  Object? _identity;

  @override
  void initState() {
    super.initState();
    _identity = ref.read(workspaceAccessProvider)?.sessionIdentity;
    Future.microtask(_load);
  }

  Future<void> _load() async {
    final generation = ++_generation;
    final identity = ref.read(workspaceAccessProvider)?.sessionIdentity;
    setState(() {
      _record = null;
      _error = null;
      _loading = true;
      _uncertain = false;
    });
    try {
      final value = await ref
          .read(medicalRecordRepositoryProvider)
          .getById(widget.recordId);
      if (mounted &&
          generation == _generation &&
          ref.read(workspaceAccessProvider)?.sessionIdentity == identity) {
        setState(() {
          _record = value;
          _loading = false;
          _identity = identity;
        });
      }
    } catch (error) {
      if (mounted &&
          generation == _generation &&
          ref.read(workspaceAccessProvider)?.sessionIdentity == identity) {
        setState(() {
          _error = error;
          _loading = false;
          _identity = identity;
        });
      }
    }
  }

  bool get _canRead => const {
    'ADMIN',
    'DOCTOR',
    'NURSE',
  }.contains(ref.read(workspaceAccessProvider)?.role);

  Future<void> _run<T>(
    Future<T> Function() command,
    void Function(T) onSuccess,
  ) async {
    if (_busy ||
        _uncertain ||
        ref.read(workspaceAccessProvider)?.sessionIdentity != _identity) {
      return;
    }
    final identity = ref.read(workspaceAccessProvider)?.sessionIdentity;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final result = await command();
      if (!mounted ||
          ref.read(workspaceAccessProvider)?.sessionIdentity != identity) {
        return;
      }
      onSuccess(result);
    } catch (error) {
      if (!mounted ||
          ref.read(workspaceAccessProvider)?.sessionIdentity != identity) {
        return;
      }
      setState(() {
        _error = error;
        _uncertain = _outcomeUnknown(error);
      });
    } finally {
      if (mounted &&
          ref.read(workspaceAccessProvider)?.sessionIdentity == identity) {
        setState(() => _busy = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final access = ref.watch(workspaceAccessProvider);
    final outpatientEnabled = ref.watch(
      medicalRecordOutpatientUiEnabledProvider,
    );
    if (access?.sessionIdentity != _identity) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) _load();
      });
      return const Scaffold(
        body: Center(child: Text('Đang cập nhật phiên đăng nhập…')),
      );
    }
    return Scaffold(
      appBar: AppBar(title: const Text('Hồ sơ khám')),
      body: !_canRead
          ? const Center(child: Text('Bạn không có quyền xem hồ sơ khám.'))
          : _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null && _record == null
          ? _RetryMessage(error: _error!, onRetry: _load)
          : _record == null
          ? const Center(child: Text('Không có dữ liệu hồ sơ.'))
          : _buildDetail(_record!, access?.role, outpatientEnabled),
    );
  }

  Widget _buildDetail(
    MedicalRecord record,
    String? role,
    bool outpatientEnabled,
  ) {
    final canEdit = canEditMedicalRecord(record, role: role);
    final canComplete = canCompleteMedicalRecord(
      record,
      role: role,
      outpatientEnabled: outpatientEnabled,
    );
    final canOrderLab = canOrderClinicalLab(
      record,
      role: role,
      outpatientEnabled: outpatientEnabled,
    );
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Ngày khám: ${record.examinationDate}',
                  style: Theme.of(context).textTheme.headlineSmall,
                ),
                const SizedBox(height: 8),
                Chip(
                  label: Text(
                    record.status == 'OPEN'
                        ? 'Đang khám'
                        : record.status == 'COMPLETED'
                        ? 'Đã hoàn tất'
                        : 'Trạng thái khác',
                  ),
                ),
                ReferenceLabel(
                  kind: ReferenceKind.record,
                  id: record.id,
                  label: 'Hồ sơ khám',
                ),
                ReferenceLabel(
                  kind: ReferenceKind.patient,
                  id: record.patientId,
                  label: 'Bệnh nhân',
                ),
                ReferenceLabel(
                  kind: ReferenceKind.doctor,
                  id: record.doctorId,
                  label: 'Bác sĩ',
                ),
                ReferenceLabel(
                  kind: ReferenceKind.department,
                  id: record.departmentId,
                  label: 'Khoa',
                ),
                if (record.appointmentId != null)
                  ReferenceLabel(
                    kind: ReferenceKind.appointment,
                    id: record.appointmentId!,
                    label: 'Lịch hẹn',
                  ),
                Text('Tạo lúc: ${record.createdAt}'),
                if (record.updatedAt != null)
                  Text('Cập nhật: ${record.updatedAt}'),
                if (record.completedAt != null)
                  Text('Hoàn tất: ${record.completedAt}'),
              ],
            ),
          ),
        ),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Triệu chứng',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                Text(
                  record.symptoms?.trim().isNotEmpty == true
                      ? record.symptoms!
                      : 'Chưa ghi nhận.',
                ),
                if (record.disposition != null)
                  Text(
                    'Hướng xử trí: ${_dispositionLabel(record.disposition!)}',
                  ),
                if (record.dispositionNote?.trim().isNotEmpty == true)
                  Text(record.dispositionNote!),
              ],
            ),
          ),
        ),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Chẩn đoán',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                if (record.diagnoses.isEmpty) const Text('Chưa có chẩn đoán.'),
                for (final diagnosis in record.diagnoses)
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    title: Text(diagnosis.name),
                    subtitle: Text(
                      [
                        if (diagnosis.icdCode != null)
                          'ICD ${diagnosis.icdCode}',
                        if (diagnosis.description?.isNotEmpty == true)
                          diagnosis.description!,
                      ].join(' · '),
                    ),
                  ),
              ],
            ),
          ),
        ),
        if (_error != null)
          _RetryMessage(error: _error!, onRetry: _uncertain ? _load : null),
        if (_uncertain)
          const Text(
            'Kết quả thao tác chưa rõ. Làm mới hồ sơ trước khi thử lại.',
            style: TextStyle(color: Colors.deepOrange),
          ),
        if (_busy) const LinearProgressIndicator(),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            if (canEdit)
              OutlinedButton.icon(
                onPressed: _busy || _uncertain
                    ? null
                    : () => _editSymptoms(record),
                icon: const Icon(Icons.edit_note),
                label: const Text('Sửa triệu chứng'),
              ),
            if (canEdit)
              OutlinedButton.icon(
                onPressed: _busy || _uncertain
                    ? null
                    : () => _addDiagnosis(record),
                icon: const Icon(Icons.add),
                label: const Text('Thêm chẩn đoán'),
              ),
            OutlinedButton.icon(
              onPressed: _busy ? null : _load,
              icon: const Icon(Icons.refresh),
              label: const Text('Làm mới hồ sơ'),
            ),
          ],
        ),
        if (outpatientEnabled && canOrderLab)
          _ClinicalLabWorkflowPanel(
            record: record,
            repository: ref.read(medicalRecordRepositoryProvider),
            onCreated: widget.onLabTestCreated,
          ),
        if (canComplete)
          _CompleteRecordCard(
            record: record,
            busy: _busy || _uncertain,
            onComplete: (disposition, note) => _run(
              () => ref
                  .read(medicalRecordRepositoryProvider)
                  .complete(record.id, disposition: disposition, note: note),
              (updated) => setState(() => _record = updated),
            ),
          ),
      ],
    );
  }

  Future<void> _editSymptoms(MedicalRecord record) async {
    final identity = ref.read(workspaceAccessProvider)?.sessionIdentity;
    final controller = TextEditingController(text: record.symptoms ?? '');
    final route = DialogRoute<String?>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Sửa triệu chứng'),
        content: TextField(
          controller: controller,
          maxLength: 4000,
          maxLines: 5,
          decoration: const InputDecoration(labelText: 'Triệu chứng'),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Hủy'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, controller.text),
            child: const Text('Lưu'),
          ),
        ],
      ),
    );
    final symptoms = await Navigator.of(context).push(route);
    await route.completed;
    controller.dispose();
    if (symptoms == null ||
        !mounted ||
        ref.read(workspaceAccessProvider)?.sessionIdentity != identity) {
      return;
    }
    await _run(
      () => ref
          .read(medicalRecordRepositoryProvider)
          .update(record.id, symptoms: symptoms),
      (updated) => setState(() => _record = updated),
    );
  }

  Future<void> _addDiagnosis(MedicalRecord record) async {
    final identity = ref.read(workspaceAccessProvider)?.sessionIdentity;
    final name = TextEditingController();
    final description = TextEditingController();
    final icd = TextEditingController();
    final route = DialogRoute<DiagnosisDraft>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Thêm chẩn đoán'),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: name,
                maxLength: 255,
                decoration: const InputDecoration(labelText: 'Tên chẩn đoán *'),
              ),
              TextField(
                controller: icd,
                maxLength: 32,
                decoration: const InputDecoration(labelText: 'Mã ICD'),
              ),
              TextField(
                controller: description,
                maxLength: 2000,
                maxLines: 3,
                decoration: const InputDecoration(labelText: 'Mô tả'),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Hủy'),
          ),
          FilledButton(
            onPressed: () {
              final value = DiagnosisDraft(
                diagnosisName: name.text,
                icdCode: icd.text,
                description: description.text,
              );
              final issue = validateDiagnosis(value);
              if (issue != null) {
                ScaffoldMessenger.of(context)
                    .showSnackBar(SnackBar(content: Text(issue)));
                return;
              }
              Navigator.pop(context, value);
            },
            child: const Text('Thêm'),
          ),
        ],
      ),
    );
    final draft = await Navigator.of(context).push(route);
    await route.completed;
    name.dispose();
    description.dispose();
    icd.dispose();
    if (draft == null ||
        !mounted ||
        ref.read(workspaceAccessProvider)?.sessionIdentity != identity) {
      return;
    }
    await _run(
      () => ref
          .read(medicalRecordRepositoryProvider)
          .addDiagnosis(record.id, draft),
      (diagnosis) {
        setState(
          () => _record = MedicalRecord(
            id: record.id,
            patientId: record.patientId,
            doctorId: record.doctorId,
            departmentId: record.departmentId,
            examinationDate: record.examinationDate,
            symptoms: record.symptoms,
            appointmentId: record.appointmentId,
            diagnoses: [...record.diagnoses, diagnosis],
            createdAt: record.createdAt,
            updatedAt: record.updatedAt,
            status: record.status,
            disposition: record.disposition,
            dispositionNote: record.dispositionNote,
            completedAt: record.completedAt,
          ),
        );
      },
    );
  }
}

class _CompleteRecordCard extends StatefulWidget {
  const _CompleteRecordCard({
    required this.record,
    required this.busy,
    required this.onComplete,
  });
  final MedicalRecord record;
  final bool busy;
  final Future<void> Function(String disposition, String? note) onComplete;
  @override
  State<_CompleteRecordCard> createState() => _CompleteRecordCardState();
}

class _CompleteRecordCardState extends State<_CompleteRecordCard> {
  final _note = TextEditingController();
  String _disposition = 'OUTPATIENT_FOLLOW_UP';
  bool _confirmed = false;
  String? _error;
  @override
  void dispose() {
    _note.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Card(
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Hoàn tất hồ sơ khám',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          DropdownButtonFormField<String>(
            initialValue: _disposition,
            decoration: const InputDecoration(labelText: 'Hướng xử trí'),
            items: const [
              DropdownMenuItem(
                value: 'OUTPATIENT_FOLLOW_UP',
                child: Text('Theo dõi ngoại trú'),
              ),
              DropdownMenuItem(
                value: 'PRESCRIPTION',
                child: Text('Kê đơn thuốc'),
              ),
              DropdownMenuItem(value: 'OTHER', child: Text('Khác')),
            ],
            onChanged: widget.busy
                ? null
                : (value) {
                    if (value != null) setState(() => _disposition = value);
                  },
          ),
          TextField(
            controller: _note,
            maxLength: 2000,
            maxLines: 3,
            decoration: const InputDecoration(
              labelText: 'Ghi chú (không bắt buộc)',
            ),
          ),
          CheckboxListTile(
            value: _confirmed,
            onChanged: widget.busy
                ? null
                : (value) => setState(() => _confirmed = value == true),
            contentPadding: EdgeInsets.zero,
            title: const Text(
              'Tôi đã kiểm tra chẩn đoán và muốn hoàn tất hồ sơ.',
            ),
          ),
          if (_error != null)
            Text(
              _error!,
              style: TextStyle(color: Theme.of(context).colorScheme.error),
            ),
          FilledButton.icon(
            onPressed:
                widget.busy || !_confirmed || widget.record.diagnoses.isEmpty
                ? null
                : () async {
                    if (_note.text.length > 2000) {
                      setState(() => _error = 'Ghi chú tối đa 2000 ký tự.');
                      return;
                    }
                    setState(() => _error = null);
                    await widget.onComplete(
                      _disposition,
                      _note.text.trim().isEmpty ? null : _note.text.trim(),
                    );
                  },
            icon: const Icon(Icons.task_alt),
            label: const Text('Hoàn tất hồ sơ'),
          ),
          if (widget.record.diagnoses.isEmpty)
            const Text('Thêm ít nhất một chẩn đoán trước khi hoàn tất.'),
        ],
      ),
    ),
  );
}

class _ClinicalLabWorkflowPanel extends ConsumerStatefulWidget {
  const _ClinicalLabWorkflowPanel({
    required this.record,
    required this.repository,
    this.onCreated,
  });
  final MedicalRecord record;
  final MedicalRecordRepository repository;
  final ValueChanged<LabTestReceipt>? onCreated;
  @override
  ConsumerState<_ClinicalLabWorkflowPanel> createState() =>
      _ClinicalLabWorkflowPanelState();
}

class _ClinicalLabWorkflowPanelState
    extends ConsumerState<_ClinicalLabWorkflowPanel> {
  final _labType = TextEditingController();
  final _priceCode = TextEditingController();
  DateTime _requestedDate = DateTime.now();
  ClinicalLabOrder? _sourceOrder;
  LabTestReceipt? _receipt;
  Object? _error;
  bool _busy = false,
      _sourceUncertain = false,
      _labUncertain = false,
      _labConflict = false;
  Object? _identity;
  @override
  void initState() {
    super.initState();
    _identity = ref.read(workspaceAccessProvider)?.sessionIdentity;
  }

  @override
  void dispose() {
    _labType.dispose();
    _priceCode.dispose();
    super.dispose();
  }

  Future<void> _createSourceOrder() async {
    if (_busy || _sourceUncertain || _sourceOrder != null) return;
    final draft = ClinicalLabOrderDraft(
      labType: _labType.text,
      requestedDate: _dateString(_requestedDate),
      priceCode: _priceCode.text,
    );
    final issue = validateClinicalLabOrder(draft);
    if (issue != null) {
      setState(() => _error = issue);
      return;
    }
    final identity = _identity;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final order = await widget.repository.createClinicalLabOrder(
        widget.record.id,
        draft,
      );
      if (mounted &&
          ref.read(workspaceAccessProvider)?.sessionIdentity == identity) {
        setState(() => _sourceOrder = order);
      }
    } catch (error) {
      if (mounted &&
          ref.read(workspaceAccessProvider)?.sessionIdentity == identity) {
        setState(() {
          _error = error;
          _sourceUncertain = _outcomeUnknown(error);
        });
      }
    } finally {
      if (mounted &&
          ref.read(workspaceAccessProvider)?.sessionIdentity == identity) {
        setState(() => _busy = false);
      }
    }
  }

  Future<void> _submitLabRequest() async {
    final sourceOrder = _sourceOrder;
    if (_busy ||
        _labUncertain ||
        _labConflict ||
        sourceOrder == null ||
        _receipt != null) {
      return;
    }
    final identity = _identity;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final receipt = await widget.repository.createLabRequestFromSourceOrder(
        sourceOrder,
      );
      if (mounted &&
          ref.read(workspaceAccessProvider)?.sessionIdentity == identity) {
        setState(() => _receipt = receipt);
        widget.onCreated?.call(receipt);
      }
    } catch (error) {
      if (mounted &&
          ref.read(workspaceAccessProvider)?.sessionIdentity == identity) {
        setState(() {
          _error = error;
          _labConflict = error is ApiFailure && error.status == 409;
          _labUncertain = _outcomeUnknown(error);
        });
      }
    } finally {
      if (mounted &&
          ref.read(workspaceAccessProvider)?.sessionIdentity == identity) {
        setState(() => _busy = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    if (ref.watch(workspaceAccessProvider)?.sessionIdentity != _identity) {
      return const Card(
        child: Padding(
          padding: EdgeInsets.all(16),
          child: Text('Phiên đăng nhập đã thay đổi.'),
        ),
      );
    }
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Chỉ định xét nghiệm',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const Text(
              'Chỉ định được gắn với hồ sơ và lịch khám. Dùng mã giá do cơ sở phê duyệt.',
            ),
            if (_sourceOrder == null) ...[
              TextField(
                controller: _labType,
                maxLength: 50,
                decoration: const InputDecoration(
                  labelText: 'Loại xét nghiệm *',
                ),
              ),
              TextField(
                controller: _priceCode,
                maxLength: 64,
                decoration: const InputDecoration(labelText: 'Mã giá *'),
              ),
              OutlinedButton.icon(
                onPressed: _busy || _sourceUncertain
                    ? null
                    : () async {
                        final selected = await showDatePicker(
                          context: context,
                          initialDate: _requestedDate,
                          firstDate: DateTime(2000),
                          lastDate: DateTime.now(),
                        );
                        if (selected != null && mounted) {
                          setState(() => _requestedDate = selected);
                        }
                      },
                icon: const Icon(Icons.calendar_month),
                label: Text('Ngày yêu cầu: ${_dateString(_requestedDate)}'),
              ),
              FilledButton(
                onPressed: _busy || _sourceUncertain
                    ? null
                    : _createSourceOrder,
                child: const Text('1. Lập chỉ định'),
              ),
              if (_sourceUncertain)
                const Text(
                  'Kết quả tạo chỉ định chưa rõ. Không gửi lại; xác nhận trạng thái với nhân viên xét nghiệm trước khi tiếp tục.',
                  style: TextStyle(color: Colors.deepOrange),
                ),
            ] else ...[
              Text(
                'Đã nhận chỉ định ${_sourceOrder!.labType} · ${_sourceOrder!.requestedDate}. Thông tin nguồn được giữ nguyên cho bước tiếp theo.',
              ),
              if (_receipt == null) ...[
                FilledButton(
                  onPressed: _busy || _labUncertain || _labConflict
                      ? null
                      : _submitLabRequest,
                  child: Text(
                    _busy ? 'Đang gửi…' : '2. Gửi yêu cầu xét nghiệm',
                  ),
                ),
                if (_labUncertain || _labConflict)
                  const Text(
                    'Chưa rõ trạng thái yêu cầu hoặc mã nguồn đã tồn tại. Đối soát trong hàng đợi xét nghiệm trước khi thao tác lại. Không lập chỉ định thay thế.',
                    style: TextStyle(color: Colors.deepOrange),
                  ),
              ] else
                const ListTile(
                  leading: Icon(Icons.check_circle_outline),
                  title: Text('Đã gửi yêu cầu xét nghiệm'),
                  subtitle: Text(
                    'Thanh toán và điều kiện thực hiện do hệ thống cập nhật riêng.',
                  ),
                ),
            ],
            if (_error != null) _RetryMessage(error: _error!),
          ],
        ),
      ),
    );
  }
}

class _RetryMessage extends StatelessWidget {
  const _RetryMessage({required this.error, this.onRetry});
  final Object error;
  final VoidCallback? onRetry;
  @override
  Widget build(BuildContext context) => Card(
    color: Theme.of(context).colorScheme.errorContainer,
    child: Padding(
      padding: const EdgeInsets.all(12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            error is ApiFailure
                ? (error as ApiFailure).message
                : error is ArgumentError
                ? (error as ArgumentError).message.toString()
                : 'Không thể tải hoặc lưu dữ liệu.',
          ),
          if (error is ApiFailure &&
              (error as ApiFailure).correlationId != null)
            Text('Mã hỗ trợ: ${(error as ApiFailure).correlationId}'),
          if (onRetry != null)
            TextButton.icon(
              onPressed: onRetry,
              icon: const Icon(Icons.refresh),
              label: const Text('Thử lại tải dữ liệu'),
            ),
        ],
      ),
    ),
  );
}

bool _outcomeUnknown(Object error) =>
    error is! ApiFailure ||
    error.status == null ||
    error.status == 408 ||
    (error.status ?? 0) >= 500;

String _dateString(DateTime date) =>
    '${date.year.toString().padLeft(4, '0')}-${date.month.toString().padLeft(2, '0')}-${date.day.toString().padLeft(2, '0')}';

String _dispositionLabel(String disposition) => switch (disposition) {
  'OUTPATIENT_FOLLOW_UP' => 'Theo dõi ngoại trú',
  'PRESCRIPTION' => 'Kê đơn thuốc',
  'OTHER' => 'Khác',
  _ => 'Khác',
};
