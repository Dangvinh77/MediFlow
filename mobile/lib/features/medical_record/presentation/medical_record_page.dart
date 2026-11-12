import '../../../core/l10n/app_l10n.dart';

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mediflow_loader/mediflow_loader.dart';

import '../../../core/widgets/medical_ui.dart';

import '../../../core/network/api_failure.dart';
import '../../../core/providers.dart';
import '../../../core/widgets/reference_widgets.dart';
import '../domain/exam_lifecycle.dart';
import '../domain/medical_record.dart';
import '../domain/patient_timeline.dart';
import '../domain/record_collaboration.dart' show amendmentSectionLabels;
import 'medical_record_formatters.dart';
import 'close_record_sheet.dart';
import 'follow_up_recommendation_panel.dart';
import 'patient_allergy_section.dart';
import 'patient_timeline_section.dart';
import 'record_lab_orders_section.dart';
import 'record_collaboration_section.dart';
import 'record_documents_section.dart';
import 'treatment_courses_section.dart';

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

typedef MedicalRecordPrescriptionWorkspaceBuilder = Widget Function(
  BuildContext context,
  MedicalRecord record, {
  required bool canCreate,
});

typedef MedicalRecordDocumentRouteBuilder = void Function(
  BuildContext context,
  RecordDocument document,
);

class _PendingRecordContentUpdate {
  const _PendingRecordContentUpdate({
    required this.recordId,
    required this.changes,
    required this.idempotencyKey,
  });

  final String recordId, idempotencyKey;
  final Map<String, String?> changes;
}

class MedicalRecordPage extends ConsumerStatefulWidget {
  const MedicalRecordPage({
    super.key,
    this.onLabTestCreated,
    this.prescriptionWorkspaceBuilder,
    this.onOpenDocument,
  });
  final ValueChanged<LabTestReceipt>? onLabTestCreated;
  final MedicalRecordPrescriptionWorkspaceBuilder? prescriptionWorkspaceBuilder;
  final MedicalRecordDocumentRouteBuilder? onOpenDocument;
  @override
  ConsumerState<MedicalRecordPage> createState() => _MedicalRecordPageState();
}

/// Resolves the exact visit through Clinical; a patient name never selects a record.
class MedicalRecordForVisitPage extends ConsumerWidget {
  const MedicalRecordForVisitPage({
    super.key,
    required this.patientId,
    required this.visitId,
    this.prescriptionWorkspaceBuilder,
    this.onOpenDocument,
  });
  final String patientId, visitId;
  final MedicalRecordPrescriptionWorkspaceBuilder? prescriptionWorkspaceBuilder;
  final MedicalRecordDocumentRouteBuilder? onOpenDocument;
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final access = ref.watch(workspaceAccessProvider);
    if (access == null ||
        !const ['ADMIN', 'DOCTOR', 'NURSE'].contains(access.role)) {
      return const Scaffold(
        body: Center(child: LText('Bạn không có quyền xem hồ sơ khám.')),
      );
    }
    return _VisitRecordResolver(
      key: ValueKey((access.sessionIdentity, patientId, visitId)),
      patientId: patientId,
      visitId: visitId,
      repository: ref.watch(medicalRecordRepositoryProvider),
      prescriptionWorkspaceBuilder: prescriptionWorkspaceBuilder,
      onOpenDocument: onOpenDocument,
    );
  }
}

class _VisitRecordResolver extends StatefulWidget {
  const _VisitRecordResolver({
    super.key,
    required this.patientId,
    required this.visitId,
    required this.repository,
    this.prescriptionWorkspaceBuilder,
    this.onOpenDocument,
  });
  final String patientId, visitId;
  final MedicalRecordRepository repository;
  final MedicalRecordPrescriptionWorkspaceBuilder? prescriptionWorkspaceBuilder;
  final MedicalRecordDocumentRouteBuilder? onOpenDocument;
  @override
  State<_VisitRecordResolver> createState() => _VisitRecordResolverState();
}

class _VisitRecordResolverState extends State<_VisitRecordResolver> {
  late Future<MedicalRecord> _record = _resolve();
  Future<MedicalRecord> _resolve() async {
    final records = await widget.repository.byPatient(widget.patientId);
    final matches = records
        .where(
          (record) =>
              record.patientId == widget.patientId &&
              record.appointmentId == widget.visitId,
        )
        .toList();
    if (matches.length != 1) {
      throw const ApiFailure(
        'Chưa xác minh được một hồ sơ duy nhất của lượt khám. Hãy tải lại; không tạo hồ sơ mới.',
      );
    }
    return matches.single;
  }

  @override
  Widget build(BuildContext context) => FutureBuilder<MedicalRecord>(
    future: _record,
    builder: (context, snapshot) {
      if (snapshot.hasData) {
        return MedicalRecordDetailPage(
          recordId: snapshot.data!.id,
          prescriptionWorkspaceBuilder: widget.prescriptionWorkspaceBuilder,
          onOpenDocument: widget.onOpenDocument,
        );
      }
      return Scaffold(
        appBar: AppBar(title: const LText('Hồ sơ của lượt khám')),
        body: snapshot.hasError
            ? _RetryMessage(
                error: snapshot.error!,
                onRetry: () => setState(() => _record = _resolve()),
              )
            : const Center(child: MediFlowLoader()),
      );
    },
  );
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
      return const Center(child: LText('Đăng nhập để tra cứu hồ sơ khám.'));
    }
    if (!const {'ADMIN', 'DOCTOR', 'NURSE'}.contains(access.role)) {
      return const Center(
        child: LText('Bạn không có quyền tra cứu hồ sơ khám.'),
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
                  label: const LText('Làm mới hồ sơ'),
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
                  label: const LText('Tạo hồ sơ V0'),
                ),
            ],
          ),
        ),
        if (outpatientEnabled)
          const Padding(
            padding: EdgeInsets.symmetric(horizontal: 16, vertical: 4),
            child: Align(
              alignment: Alignment.centerLeft,
              child: LText('Hồ sơ được tạo khi bắt đầu khám từ lịch hẹn.'),
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
    if (_loading) return const Center(child: MediFlowLoader());
    if (_error != null) {
      return _recordAccessRefusal(
            error: _error!,
            patientId: _patientId,
            role: ref.read(workspaceAccessProvider)?.role,
            deniedText: 'Bạn không có quyền xem hồ sơ khám của bệnh nhân này.',
            onDeclared: () => _loadPatient(_patientId!),
          ) ??
          _RetryMessage(
            error: _error!,
            onRetry: _patientId == null
                ? null
                : () => _loadPatient(_patientId!),
          );
    }
    if (_patientId == null) {
      return const Center(
        child: LText('Chọn bệnh nhân theo tên để xem hồ sơ khám.'),
      );
    }
    if (_records == null) return const SizedBox.shrink();
    final patientId = _patientId!;
    return PatientTimelineSection(
      patientId: patientId,
      errorBuilder: (error) =>
          _recordAccessRefusal(
            error: error,
            patientId: patientId,
            role: ref.read(workspaceAccessProvider)?.role,
            deniedText: 'Bạn không có quyền xem hồ sơ khám của bệnh nhân này.',
            onDeclared: () => _loadPatient(patientId),
          ) ??
          _RetryMessage(error: error, onRetry: () => _loadPatient(patientId)),
      onOpenRecord: (entry) => Navigator.of(context).push<void>(
        MaterialPageRoute(
          builder: (_) => MedicalRecordDetailPage(
            recordId: entry.recordId,
            onLabTestCreated: widget.onLabTestCreated,
            prescriptionWorkspaceBuilder: widget.prescriptionWorkspaceBuilder,
            onOpenDocument: widget.onOpenDocument,
          ),
        ),
      ),
    );
  }
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
        appBar: AppBar(title: const LText('Tạo hồ sơ')),
        body: const Center(
          child: LText('Tạo hồ sơ thủ công hiện không khả dụng.'),
        ),
      );
    }
    return Scaffold(
      appBar: AppBar(title: const LText('Tạo hồ sơ khám V0')),
      body: Form(
        key: _formKey,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            const LText(
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
              label: LText('Ngày khám: ${_dateString(_date)}'),
            ),
            TextFormField(
              controller: _symptoms,
              maxLength: 4000,
              maxLines: 3,
              decoration: InputDecoration(
                labelText: AppL10n.text(context, 'Triệu chứng'),
              ),
              validator: (v) =>
                  (v?.length ?? 0) > 2000 ? 'Tối đa 2000 ký tự.' : null,
            ),
            TextFormField(
              controller: _diagnosisName,
              maxLength: 255,
              decoration: InputDecoration(
                labelText: AppL10n.text(context, 'Chẩn đoán ban đầu *'),
              ),
              validator: (v) =>
                  v == null || v.trim().isEmpty ? 'Nhập chẩn đoán.' : null,
            ),
            TextFormField(
              controller: _diagnosisCode,
              maxLength: 32,
              decoration: InputDecoration(
                labelText: AppL10n.text(context, 'Mã ICD (không bắt buộc)'),
              ),
            ),
            if (_error != null) _RetryMessage(error: _error!),
            if (_uncertain)
              const NoticeBox(
                'Kết quả tạo hồ sơ chưa rõ. Không gửi lại; hãy tra cứu bệnh nhân để xác nhận hồ sơ trước.',
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
              label: const LText('Tạo hồ sơ'),
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
    this.prescriptionWorkspaceBuilder,
    this.onOpenDocument,
    this.referenceOnly = false,
  });
  final String recordId;
  final ValueChanged<LabTestReceipt>? onLabTestCreated;
  final MedicalRecordPrescriptionWorkspaceBuilder? prescriptionWorkspaceBuilder;
  final MedicalRecordDocumentRouteBuilder? onOpenDocument;
  final bool referenceOnly;
  @override
  ConsumerState<MedicalRecordDetailPage> createState() =>
      _MedicalRecordDetailPageState();
}

class _MedicalRecordDetailPageState
    extends ConsumerState<MedicalRecordDetailPage> {
  MedicalRecord? _record;
  RecordCapabilities? _capabilities;
  Object? _error;
  bool _loading = true, _busy = false, _uncertain = false;
  int _generation = 0, _labOrdersRevision = 0;
  Object? _identity;
  _PendingRecordContentUpdate? _pendingContentUpdate;

  @override
  void initState() {
    super.initState();
    _identity = ref.read(workspaceAccessProvider)?.sessionIdentity;
    Future.microtask(_load);
  }

  Future<void> _closeRecord(MedicalRecord record) async {
    final repository = ref.read(examLifecycleRepositoryProvider);
    final identity = ref.read(workspaceAccessProvider)?.sessionIdentity;
    if (repository == null || _busy || _uncertain) return;
    final closed = await showCloseRecordSheet(
      context,
      record: record,
      repository: repository,
    );
    if (closed != null &&
        mounted &&
        ref.read(workspaceAccessProvider)?.sessionIdentity == identity) {
      setState(() => _record = closed);
    }
  }

  /// A capability failure leaves the record read only; it never blocks reading.
  Future<RecordCapabilities?> _capabilitiesOrNull(
    MedicalRecordRepository repository,
  ) async {
    try {
      return await repository.capabilities(widget.recordId);
    } catch (_) {
      return null;
    }
  }

  Future<void> _load() async {
    final generation = ++_generation;
    final identity = ref.read(workspaceAccessProvider)?.sessionIdentity;
    setState(() {
      _record = null;
      _capabilities = null;
      _error = null;
      _loading = true;
      _uncertain = false;
    });
    final repository = ref.read(medicalRecordRepositoryProvider);
    final capabilitiesRequest = _capabilitiesOrNull(repository);
    try {
      final value = await repository.getById(widget.recordId);
      final capabilities = await capabilitiesRequest;
      if (mounted &&
          generation == _generation &&
          ref.read(workspaceAccessProvider)?.sessionIdentity == identity) {
        setState(() {
          _record = value;
          _capabilities = capabilities;
          _loading = false;
          _identity = identity;
        });
      }
    } catch (error) {
      final capabilities = await capabilitiesRequest;
      if (mounted &&
          generation == _generation &&
          ref.read(workspaceAccessProvider)?.sessionIdentity == identity) {
        setState(() {
          _capabilities = capabilities;
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
        body: Center(child: LText('Đang cập nhật phiên đăng nhập…')),
      );
    }
    return Scaffold(
      appBar: AppBar(title: const LText('Hồ sơ khám')),
      body: !_canRead
          ? const Center(child: LText('Bạn không có quyền xem hồ sơ khám.'))
          : _loading
          ? const Center(child: MediFlowLoader())
          : _error != null && _record == null
          ? _recordAccessRefusal(
                  error: _error!,
                  patientId: _capabilities?.patientId,
                  role: access?.role,
                  deniedText: 'Bạn không có quyền xem hồ sơ khám này.',
                  onDeclared: _load,
                ) ??
                _RetryMessage(error: _error!, onRetry: _load)
          : _record == null
          ? const Center(child: LText('Không có dữ liệu hồ sơ.'))
          : _buildDetail(_record!, access?.role, outpatientEnabled),
    );
  }

  Widget _buildDetail(
    MedicalRecord record,
    String? role,
    bool outpatientEnabled,
  ) {
    final capabilities = _capabilities;
    final canEdit =
        !widget.referenceOnly &&
        record.status == 'OPEN' &&
        capabilities?.canWriteContent == true;
    final canComplete =
        !widget.referenceOnly &&
        capabilities?.canComplete == true &&
        canCompleteMedicalRecord(
          record,
          role: role,
          outpatientEnabled: outpatientEnabled,
        );
    final canOrderLab =
        canEdit &&
        canOrderClinicalLab(
          record,
          role: role,
          outpatientEnabled: outpatientEnabled,
        );
    final canCreatePrescription = outpatientEnabled && canEdit;
    final lifecycle = ref.watch(examLifecycleRepositoryProvider);
    final canClose =
        !widget.referenceOnly &&
        lifecycle != null &&
        capabilities?.canComplete == true &&
        canCloseWithoutConclusion(
          record,
          role: role,
          outpatientEnabled: outpatientEnabled,
        );
    final readOnlyNotice = record.status != 'OPEN'
        ? null
        : capabilities == null
        ? 'Chưa xác định được quyền ghi, hồ sơ đang ở chế độ chỉ đọc.'
        : switch (capabilities.writeBlockedReason) {
            'NOT_ASSIGNED_DOCTOR' => 'Bạn đang xem hồ sơ do bác sĩ khác phụ trách. Chỉ bác sĩ phụ trách mới được ghi vào hồ sơ này.',
            'NOT_IN_EXAM' => 'Lượt khám chưa ở trạng thái đang khám nên chưa thể ghi vào hồ sơ.',
            _ => null,
          };
    final prescriptionBuilder =
        !widget.referenceOnly && const {'ADMIN', 'DOCTOR'}.contains(role)
        ? widget.prescriptionWorkspaceBuilder
        : null;
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        PatientAllergySection(
          key: ValueKey(('patient-allergies', record.patientId)),
          patientId: record.patientId,
          sourceRecordId: record.id,
          canAdd:
              !widget.referenceOnly && const {'DOCTOR', 'NURSE'}.contains(role),
          canManageStatus: !widget.referenceOnly && role == 'DOCTOR',
        ),
        if (widget.referenceOnly)
          const NoticeBox(
            'Lượt trước — chỉ tham khảo. Không thể thay đổi dữ liệu.',
          ),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                LText(
                  'Ngày khám: ${record.examinationDate}',
                  style: Theme.of(context).textTheme.headlineSmall,
                ),
                const SizedBox(height: 8),
                StatusPill(
                  _recordStatusLabel(record.status),
                  tone: statusToneOf(record.status),
                ),
                if (record.status == 'COMPLETED')
                  const LText(
                    'Hoàn tất khám không đồng nghĩa đã ký xác nhận hoặc chuyển lưu trữ.',
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
                LText('Tạo lúc: ${record.createdAt}'),
                if (record.updatedAt != null)
                  LText('Cập nhật: ${record.updatedAt}'),
                if (record.completedAt != null)
                  LText('Hoàn tất: ${record.completedAt}'),
                if (record.closureReason != null)
                  LText(
                    'Lý do: ${recordClosureReasonLabels[record.closureReason] ?? record.closureReason}',
                  ),
                if (record.closureNote?.trim().isNotEmpty == true)
                  LText(record.closureNote!),
                if (record.closedAt != null)
                  LText('Kết thúc lúc: ${record.closedAt}'),
                if (record.previousRecordId case final previous?)
                  TextButton(
                    onPressed: () => Navigator.of(context).push<void>(
                      MaterialPageRoute(
                        builder: (_) => MedicalRecordDetailPage(
                          recordId: previous,
                          prescriptionWorkspaceBuilder:
                              widget.prescriptionWorkspaceBuilder,
                          onOpenDocument: widget.onOpenDocument,
                          referenceOnly: true,
                        ),
                      ),
                    ),
                    child: const LText('Lượt trước — chỉ tham khảo'),
                  ),
              ],
            ),
          ),
        ),
        ..._clinicalContentSections(record),
        if (record.effectiveCorrections.isNotEmpty)
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const LText('Chỉnh sửa có hiệu lực', style: TextStyle(fontWeight: FontWeight.w600)),
                  const LText('Bản gốc được giữ nguyên. Nội dung sửa bên dưới là giá trị đang có hiệu lực.'),
                  for (final correction in record.effectiveCorrections) ...[
                    const Divider(),
                    LText(amendmentSectionLabels[correction.section] ?? correction.section,
                        style: Theme.of(context).textTheme.titleSmall),
                    const LText('Nội dung gốc'),
                    LText(correction.originalText),
                    const LText('Nội dung đang có hiệu lực', style: TextStyle(fontWeight: FontWeight.w600)),
                    LText(correction.correctedText),
                    LText('Lý do đính chính: ${correction.reason}'),
                    LText('Thời điểm áp dụng: ${formatClinicalInstant(correction.appliedAt)}'),
                  ],
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
                LText(
                  record.effectiveCorrections.any((c) => c.section == 'DIAGNOSIS')
                      ? 'Chẩn đoán ban đầu'
                      : 'Chẩn đoán',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                if (record.diagnoses.isEmpty) const LText('Chưa có chẩn đoán.'),
                for (final diagnosis in record.diagnoses)
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    title: LText(diagnosis.name),
                    subtitle: LText(
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
        RecordDocumentsSection(
          recordId: record.id,
          onOpenSource: widget.onOpenDocument == null
              ? null
              : (document) => widget.onOpenDocument!(context, document),
        ),
        if (!widget.referenceOnly) ...[
          TreatmentCoursesSection(
            key: ValueKey(('treatment-courses', record.patientId)),
            patientId: record.patientId,
            recordId: record.id,
          ),
          RecordCollaborationSection(
            key: ValueKey(('record-collaboration', record.id)),
            recordId: record.id,
            capabilities: _capabilities,
          ),
        ],
        if (record.status == 'COMPLETED' &&
            ref.watch(appConfigProvider).scheduledPaymentsEnabled &&
            ref.watch(followUpRecommendationRepositoryProvider) != null)
          FollowUpRecommendationPanel(
            key: ValueKey(record.id),
            recordId: record.id,
            canCreate: role == 'DOCTOR',
          ),
        if (readOnlyNotice != null) NoticeBox(readOnlyNotice),
        if (_error != null)
          _RetryMessage(error: _error!, onRetry: _uncertain ? _load : null),
        if (_uncertain && _pendingContentUpdate == null)
          const NoticeBox(
            'Kết quả thao tác chưa rõ. Làm mới hồ sơ trước khi thử lại.',
          ),
        if (_pendingContentUpdate case final pending?)
          OutlinedButton.icon(
            key: const Key('record-content-retry'),
            onPressed: _busy ? null : () => _sendContentUpdate(pending),
            icon: const Icon(Icons.refresh),
            label: const LText('Gửi lại cùng yêu cầu'),
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
                    : () => _editContent(record),
                icon: const Icon(Icons.edit_note),
                label: const LText('Sửa nội dung khám'),
              ),
            if (canEdit)
              OutlinedButton.icon(
                onPressed: _busy || _uncertain
                    ? null
                    : () => _addDiagnosis(record),
                icon: const Icon(Icons.add),
                label: const LText('Thêm chẩn đoán'),
              ),
            OutlinedButton.icon(
              onPressed: _busy ? null : _load,
              icon: const Icon(Icons.refresh),
              label: const LText('Làm mới hồ sơ'),
            ),
          ],
        ),
        if (outpatientEnabled && canOrderLab)
          _ClinicalLabWorkflowPanel(
            record: record,
            repository: ref.read(medicalRecordRepositoryProvider),
            onCreated: (receipt) {
              setState(() => _labOrdersRevision++);
              widget.onLabTestCreated?.call(receipt);
            },
          ),
        if (outpatientEnabled &&
            lifecycle != null &&
            record.appointmentId?.trim().isNotEmpty == true)
          RecordLabOrdersSection(
            key: ValueKey((record.id, _identity, _labOrdersRevision)),
            recordId: record.id,
            canChange: canEdit,
          ),
        if (prescriptionBuilder case final buildWorkspace?)
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  LText(
                    'Đơn thuốc',
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  const SizedBox(height: 8),
                  KeyedSubtree(
                    key: ValueKey((record.id, _identity)),
                    child: buildWorkspace(
                      context,
                      record,
                      canCreate: canCreatePrescription,
                    ),
                  ),
                ],
              ),
            ),
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
        if (canClose)
          OutlinedButton(
            onPressed: _busy || _uncertain ? null : () => _closeRecord(record),
            child: const LText('Kết thúc không kết luận'),
          ),
      ],
    );
  }

  List<Widget> _clinicalContentSections(MedicalRecord record) {
    final amended = record.effectiveCorrections.map((value) => value.section).toSet();
    return [
    if (record.symptoms?.trim().isNotEmpty == true)
      _clinicalSection(amended.contains('SYMPTOMS') ? 'Triệu chứng ban đầu' : 'Triệu chứng', record.symptoms!),
    if (record.chiefComplaint?.trim().isNotEmpty == true)
      _clinicalSection(amended.contains('CHIEF_COMPLAINT') ? 'Lý do khám ban đầu' : 'Lý do khám', record.chiefComplaint!),
    if (record.presentIllness?.trim().isNotEmpty == true)
      _clinicalSection(amended.contains('PRESENT_ILLNESS') ? 'Diễn biến bệnh ban đầu' : 'Diễn biến bệnh', record.presentIllness!),
    if (record.pastHistory?.trim().isNotEmpty == true)
      _clinicalSection(amended.contains('PAST_HISTORY') ? 'Tiền sử ban đầu' : 'Tiền sử', record.pastHistory!),
    if (record.allergyNote?.trim().isNotEmpty == true)
      _clinicalSection(amended.contains('ALLERGY_NOTE') ? 'Ghi chú dị ứng ban đầu' : 'Ghi chú dị ứng của lượt', record.allergyNote!),
    if (record.physicalExam?.trim().isNotEmpty == true)
      _clinicalSection(amended.contains('PHYSICAL_EXAM') ? 'Khám thực thể ban đầu' : 'Khám thực thể', record.physicalExam!),
    if (record.advice?.trim().isNotEmpty == true)
      _clinicalSection(amended.contains('ADVICE') ? 'Dặn dò ban đầu' : 'Dặn dò', record.advice!),
    if (record.disposition != null ||
        record.dispositionNote?.trim().isNotEmpty == true)
      _clinicalSection(
        amended.contains('DISPOSITION_NOTE') ? 'Hướng xử trí ban đầu' : 'Hướng xử trí',
        [
          if (record.disposition != null)
            _dispositionLabel(record.disposition!),
          if (record.dispositionNote?.trim().isNotEmpty == true)
            record.dispositionNote!,
        ].join('\n'),
      ),
  ];
  }

  Widget _clinicalSection(String title, String content) => Card(
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          LText(title, style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 4),
          LText(content),
        ],
      ),
    ),
  );

  Future<void> _editContent(MedicalRecord record) async {
    final identity = ref.read(workspaceAccessProvider)?.sessionIdentity;
    final original = <String, String?>{
      'symptoms': record.symptoms,
      'chiefComplaint': record.chiefComplaint,
      'presentIllness': record.presentIllness,
      'pastHistory': record.pastHistory,
      'allergyNote': record.allergyNote,
      'physicalExam': record.physicalExam,
      'advice': record.advice,
    };
    const labels = <String, String>{
      'symptoms': 'Triệu chứng',
      'chiefComplaint': 'Lý do khám',
      'presentIllness': 'Diễn biến bệnh',
      'pastHistory': 'Tiền sử',
      'allergyNote': 'Ghi chú dị ứng của lượt',
      'physicalExam': 'Khám thực thể',
      'advice': 'Dặn dò',
    };
    final controllers = {
      for (final field in recordContentFields)
        field: TextEditingController(text: original[field] ?? ''),
    };
    final route = DialogRoute<Map<String, String?>>(
      context: context,
      builder: (context) => AlertDialog(
        title: const LText('Sửa nội dung khám'),
        content: SizedBox(
          width: 560,
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                for (final field in recordContentFields)
                  TextField(
                    key: Key('record-content-$field'),
                    controller: controllers[field],
                    maxLength: 4000,
                    maxLines: 3,
                    decoration: InputDecoration(
                      labelText: AppL10n.text(context, labels[field]),
                    ),
                  ),
              ],
            ),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const LText('Hủy'),
          ),
          FilledButton(
            key: const Key('record-content-save'),
            onPressed: () => Navigator.pop(context, <String, String?>{
              for (final field in recordContentFields)
                field:
                    controllers[field]!.text.isEmpty && original[field] == null
                    ? null
                    : controllers[field]!.text,
            }),
            child: const LText('Lưu'),
          ),
        ],
      ),
    );
    final edited = await Navigator.of(context).push(route);
    await route.completed;
    for (final controller in controllers.values) {
      controller.dispose();
    }
    if (edited == null ||
        !mounted ||
        ref.read(workspaceAccessProvider)?.sessionIdentity != identity) {
      return;
    }
    final changes = changedRecordSections(original: original, edited: edited);
    if (changes.isEmpty) return;
    await _sendContentUpdate(
      _PendingRecordContentUpdate(
        recordId: record.id,
        changes: Map.unmodifiable(changes),
        idempotencyKey: newExamCommandKey(),
      ),
    );
  }

  Future<void> _sendContentUpdate(_PendingRecordContentUpdate intent) async {
    if (_busy ||
        _identity != ref.read(workspaceAccessProvider)?.sessionIdentity ||
        widget.referenceOnly ||
        _record?.status != 'OPEN') {
      return;
    }
    final identity = ref.read(workspaceAccessProvider)?.sessionIdentity;
    setState(() {
      _pendingContentUpdate = intent;
      _busy = true;
      _error = null;
    });
    try {
      final repository = ref.read(patientTimelineRepositoryProvider);
      if (repository == null) {
        throw StateError('Không có kết nối để cập nhật nội dung hồ sơ.');
      }
      final updated = await repository.updateContent(
        intent.recordId,
        changes: intent.changes,
        idempotencyKey: intent.idempotencyKey,
      );
      if (!mounted ||
          ref.read(workspaceAccessProvider)?.sessionIdentity != identity) {
        return;
      }
      setState(() {
        _record = updated;
        _pendingContentUpdate = null;
        _uncertain = false;
        _error = null;
      });
    } catch (error) {
      if (!mounted ||
          ref.read(workspaceAccessProvider)?.sessionIdentity != identity) {
        return;
      }
      final unknown = _outcomeUnknown(error);
      setState(() {
        _error = error;
        _uncertain = unknown;
        if (!unknown) _pendingContentUpdate = null;
      });
    } finally {
      if (mounted &&
          ref.read(workspaceAccessProvider)?.sessionIdentity == identity) {
        setState(() => _busy = false);
      }
    }
  }

  Future<void> _addDiagnosis(MedicalRecord record) async {
    final identity = ref.read(workspaceAccessProvider)?.sessionIdentity;
    final name = TextEditingController();
    final description = TextEditingController();
    final icd = TextEditingController();
    final route = DialogRoute<DiagnosisDraft>(
      context: context,
      builder: (context) => AlertDialog(
        title: const LText('Thêm chẩn đoán'),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: name,
                maxLength: 255,
                decoration: InputDecoration(
                  labelText: AppL10n.text(context, 'Tên chẩn đoán *'),
                ),
              ),
              TextField(
                controller: icd,
                maxLength: 32,
                decoration: InputDecoration(
                  labelText: AppL10n.text(context, 'Mã ICD'),
                ),
              ),
              TextField(
                controller: description,
                maxLength: 2000,
                maxLines: 3,
                decoration: InputDecoration(
                  labelText: AppL10n.text(context, 'Mô tả'),
                ),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const LText('Hủy'),
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
                    .showSnackBar(SnackBar(content: LText(issue)));
                return;
              }
              Navigator.pop(context, value);
            },
            child: const LText('Thêm'),
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
          LText(
            'Hoàn tất hồ sơ khám',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          DropdownButtonFormField<String>(
            initialValue: _disposition,
            decoration: InputDecoration(
              labelText: AppL10n.text(context, 'Hướng xử trí'),
            ),
            items: const [
              DropdownMenuItem(
                value: 'OUTPATIENT_FOLLOW_UP',
                child: LText('Theo dõi ngoại trú'),
              ),
              DropdownMenuItem(
                value: 'PRESCRIPTION',
                child: LText('Kê đơn thuốc'),
              ),
              DropdownMenuItem(value: 'OTHER', child: LText('Khác')),
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
            decoration: InputDecoration(
              labelText: AppL10n.text(context, 'Ghi chú (không bắt buộc)'),
            ),
          ),
          CheckboxListTile(
            value: _confirmed,
            onChanged: widget.busy
                ? null
                : (value) => setState(() => _confirmed = value == true),
            contentPadding: EdgeInsets.zero,
            title: const LText(
              'Tôi đã kiểm tra chẩn đoán và muốn hoàn tất hồ sơ.',
            ),
          ),
          if (_error != null)
            LText(
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
            label: const LText('Hoàn tất hồ sơ'),
          ),
          if (widget.record.diagnoses.isEmpty)
            const LText('Thêm ít nhất một chẩn đoán trước khi hoàn tất.'),
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

class _PendingLabProgress {
  const _PendingLabProgress(
    this.order,
    this.sourceUncertain,
    this.labUncertain,
    this.labConflict,
    this.error,
  );
  final ClinicalLabOrder? order;
  final bool sourceUncertain, labUncertain, labConflict;
  final Object? error;
}

final _pendingLabProgressProvider = Provider((ref) {
  ref.watch(workspaceAccessProvider);
  return <String, _PendingLabProgress>{};
});

class _ClinicalLabWorkflowPanelState
    extends ConsumerState<_ClinicalLabWorkflowPanel> {
  final _labType = TextEditingController();
  final _priceCode = TextEditingController();
  final List<ClinicalLabCatalogItem> _catalogItems = [];
  late final ClinicalLabCatalogRepository? _catalogRepository;
  DateTime _requestedDate = DateTime.now();
  String? _selectedCatalogGroup;
  ClinicalLabCatalogItem? _selectedCatalogItem;
  ClinicalLabOrder? _sourceOrder;
  LabTestReceipt? _receipt;
  Object? _error;
  Object? _catalogError;
  bool _busy = false,
      _catalogLoading = false,
      _catalogDisabled = true,
      _sourceUncertain = false,
      _labUncertain = false,
      _labConflict = false;
  Object? _identity;
  late Map<String, _PendingLabProgress> _progress;
  @override
  void initState() {
    super.initState();
    _identity = ref.read(workspaceAccessProvider)?.sessionIdentity;
    _catalogRepository = widget.repository is ClinicalLabCatalogRepository
        ? widget.repository as ClinicalLabCatalogRepository
        : null;
    _progress = ref.read(_pendingLabProgressProvider);
    final pending = _progress[widget.record.id];
    if (pending != null) {
      _sourceOrder = pending.order;
      _sourceUncertain = pending.sourceUncertain;
      _labUncertain = pending.labUncertain;
      _labConflict = pending.labConflict;
      _error = pending.error;
    }
    if (_sourceOrder == null && _catalogRepository != null) {
      _catalogLoading = true;
      unawaited(_loadCatalog());
    }
  }

  Future<void> _loadCatalog() async {
    final identity = _identity;
    try {
      final items = await _catalogRepository!.labCatalog();
      if (!_sessionIsCurrent(identity)) return;
      setState(() {
        _catalogLoading = false;
        _catalogDisabled = items == null;
        _catalogItems
          ..clear()
          ..addAll(items ?? const []);
        if (_catalogItems.isNotEmpty) {
          _selectedCatalogGroup = _catalogGroups.first;
        }
      });
    } catch (error) {
      if (!_sessionIsCurrent(identity)) return;
      setState(() {
        _catalogLoading = false;
        _catalogDisabled = true;
        _catalogError = error;
      });
    }
  }

  bool _sessionIsCurrent(Object? identity) =>
      mounted && ref.read(workspaceAccessProvider)?.sessionIdentity == identity;

  List<String> get _catalogGroups =>
      _catalogItems.map((item) => item.displayGroup).toSet().toList()..sort();

  List<ClinicalLabCatalogItem> get _selectedGroupItems => _catalogItems
      .where((item) => item.displayGroup == _selectedCatalogGroup)
      .toList(growable: false);

  void _selectCatalogGroup(String? group) {
    setState(() {
      _selectedCatalogGroup = group;
      _selectedCatalogItem = null;
      _labType.clear();
      _priceCode.clear();
      _error = null;
    });
  }

  void _selectCatalogItem(ClinicalLabCatalogItem? item) {
    setState(() {
      _selectedCatalogItem = item;
      _labType.text = item?.name ?? '';
      _priceCode.text = item?.priceCode ?? '';
      _error = null;
    });
  }

  static const _catalogGroupLabels = {
    'HEMATOLOGY': 'Huyết học',
    'BIOCHEMISTRY': 'Sinh hóa',
    'IMMUNOLOGY': 'Miễn dịch',
    'URINALYSIS': 'Nước tiểu',
    'MICROBIOLOGY': 'Vi sinh',
  };

  String _groupLabel(String group) => _catalogGroupLabels[group] ?? group;

  String _metadata(String? value) =>
      value == null || value.trim().isEmpty ? 'Chưa có thông tin' : value;

  @override
  void dispose() {
    if (_receipt == null && (_sourceOrder != null || _sourceUncertain)) {
      _progress[widget.record.id] = _PendingLabProgress(
        _sourceOrder,
        _sourceUncertain,
        _labUncertain,
        _labConflict,
        _error,
      );
    } else {
      _progress.remove(widget.record.id);
    }
    _labType.dispose();
    _priceCode.dispose();
    super.dispose();
  }

  Future<void> _createSourceOrder() async {
    if (_busy || _sourceUncertain || _sourceOrder != null) return;
    if (!_catalogDisabled && _selectedCatalogItem == null) {
      setState(() => _error = 'Chọn dịch vụ xét nghiệm trước khi gửi.');
      return;
    }
    final draft = ClinicalLabOrderDraft(
      labType: _labType.text,
      requestedDate: _dateString(_requestedDate),
      priceCode: _priceCode.text,
      serviceCode: _catalogDisabled ? null : _selectedCatalogItem?.serviceCode,
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
        setState(() => _busy = false);
        await _submitLabRequest();
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
          child: LText('Phiên đăng nhập đã thay đổi.'),
        ),
      );
    }
    return PopScope(
      canPop: !_busy,
      child: Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              LText(
                'Chỉ định xét nghiệm',
                style: Theme.of(context).textTheme.titleMedium,
              ),
              const LText(
                'Chỉ định được gắn với hồ sơ và lịch khám. Dùng mã giá do cơ sở phê duyệt.',
              ),
              if (_sourceOrder == null) ...[
                if (_catalogError != null) _RetryMessage(error: _catalogError!),
                if (_catalogLoading)
                  const Padding(
                    padding: EdgeInsets.symmetric(vertical: 16),
                    child: Center(child: MediFlowLoader()),
                  ),
                if (!_catalogLoading &&
                    !_catalogDisabled &&
                    _catalogItems.isEmpty)
                  const NoticeBox(
                    'Danh mục chưa có dịch vụ xét nghiệm để chọn.',
                  ),
                if (!_catalogLoading &&
                    !_catalogDisabled &&
                    _catalogItems.isNotEmpty) ...[
                  DropdownButtonFormField<String>(
                    key: const ValueKey('clinical-lab-catalog-group'),
                    initialValue: _selectedCatalogGroup,
                    decoration: InputDecoration(
                      labelText: AppL10n.text(context, 'Nhóm xét nghiệm'),
                    ),
                    items: [
                      for (final group in _catalogGroups)
                        DropdownMenuItem(
                          value: group,
                          child: LText(_groupLabel(group)),
                        ),
                    ],
                    onChanged: _busy ? null : _selectCatalogGroup,
                  ),
                  DropdownButtonFormField<ClinicalLabCatalogItem>(
                    key: ValueKey(
                      'clinical-lab-catalog-service-$_selectedCatalogGroup',
                    ),
                    initialValue: _selectedCatalogItem,
                    decoration: InputDecoration(
                      labelText: AppL10n.text(context, 'Dịch vụ xét nghiệm'),
                    ),
                    items: [
                      for (final item in _selectedGroupItems)
                        DropdownMenuItem(value: item, child: LText(item.name)),
                    ],
                    onChanged: _busy ? null : _selectCatalogItem,
                  ),
                  if (_selectedCatalogItem != null) ...[
                    LText('Bệnh phẩm: ${_selectedCatalogItem!.specimenType}'),
                    LText(
                      'Chuẩn bị: ${_metadata(_selectedCatalogItem!.preparation)}',
                    ),
                    LText(
                      'Thời gian trả kết quả: ${_selectedCatalogItem!.turnaroundHours == null ? 'Chưa có thông tin' : '${_selectedCatalogItem!.turnaroundHours} giờ'}',
                    ),
                    LText('Mã giá: ${_selectedCatalogItem!.priceCode}'),
                    if (_selectedCatalogItem!.panel)
                      const LText(
                        'Dịch vụ bảng được gửi trong một đơn xét nghiệm.',
                      ),
                  ],
                ],
                if (!_catalogLoading && _catalogDisabled)
                  TextField(
                    key: const ValueKey('clinical-lab-manual-type'),
                    controller: _labType,
                    maxLength: 50,
                    decoration: InputDecoration(
                      labelText: AppL10n.text(context, 'Loại xét nghiệm *'),
                    ),
                  ),
                if (!_catalogLoading && _catalogDisabled)
                  TextField(
                    key: const ValueKey('clinical-lab-manual-price'),
                    controller: _priceCode,
                    maxLength: 64,
                    decoration: InputDecoration(
                      labelText: AppL10n.text(context, 'Mã giá *'),
                    ),
                  ),
                OutlinedButton.icon(
                  onPressed: _busy || _sourceUncertain || _catalogLoading
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
                  label: LText('Ngày yêu cầu: ${_dateString(_requestedDate)}'),
                ),
                FilledButton(
                  key: const ValueKey('clinical-lab-submit'),
                  onPressed:
                      _busy ||
                          _sourceUncertain ||
                          _catalogLoading ||
                          (!_catalogDisabled && _selectedCatalogItem == null)
                      ? null
                      : _createSourceOrder,
                  child: const LText('Gửi chỉ định xét nghiệm'),
                ),
                if (_sourceUncertain)
                  const NoticeBox(
                    'Chưa rõ kết quả lập chỉ định trong hồ sơ. Không lập lại; cần bộ phận hỗ trợ đối soát hồ sơ. Lab có thể chưa nhận được yêu cầu.',
                  ),
              ] else ...[
                LText(
                  'Đã nhận chỉ định ${_sourceOrder!.labType} · ${_sourceOrder!.requestedDate}. Thông tin nguồn được giữ nguyên cho bước tiếp theo.',
                ),
                if (_receipt == null) ...[
                  FilledButton(
                    onPressed: _busy || _labUncertain || _labConflict
                        ? null
                        : _submitLabRequest,
                    child: LText(
                      _busy ? 'Đang gửi…' : 'Tiếp tục chuyển sang Lab',
                    ),
                  ),
                  if (_labUncertain || _labConflict)
                    const NoticeBox(
                      'Chưa rõ trạng thái yêu cầu hoặc mã nguồn đã tồn tại. Đối soát trong hàng đợi xét nghiệm trước khi thao tác lại. Không lập chỉ định thay thế.',
                    ),
                ] else
                  const ListTile(
                    leading: Icon(Icons.check_circle_outline),
                    title: LText('Đã gửi yêu cầu xét nghiệm'),
                    subtitle: LText(
                      'Thanh toán và điều kiện thực hiện do hệ thống cập nhật riêng.',
                    ),
                  ),
              ],
              if (_error != null) _RetryMessage(error: _error!),
            ],
          ),
        ),
      ),
    );
  }
}

/// Turns a read refusal into its own screen; returns null for every other failure.
Widget? _recordAccessRefusal({
  required Object error,
  required String? patientId,
  required String? role,
  required String deniedText,
  required VoidCallback onDeclared,
}) {
  if (error is! ApiFailure) return null;
  if (error.code == 'RECORD_ACCESS_DENIED') {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Align(alignment: Alignment.topCenter, child: LText(deniedText)),
    );
  }
  if (error.code != 'RECORD_ACCESS_PURPOSE_REQUIRED') return null;
  if (patientId == null) {
    return const Padding(
      padding: EdgeInsets.all(16),
      child: NoticeBox(
        'Chưa xác định được bệnh nhân của hồ sơ. Hãy tra cứu lại từ danh sách hồ sơ.',
      ),
    );
  }
  return _RecordAccessPurposeForm(
    patientId: patientId,
    role: role,
    onDeclared: onDeclared,
  );
}

class _RecordAccessPurposeForm extends ConsumerStatefulWidget {
  const _RecordAccessPurposeForm({
    required this.patientId,
    required this.role,
    required this.onDeclared,
  });
  final String patientId;
  final String? role;
  final VoidCallback onDeclared;

  @override
  ConsumerState<_RecordAccessPurposeForm> createState() =>
      _RecordAccessPurposeFormState();
}

class _RecordAccessPurposeFormState
    extends ConsumerState<_RecordAccessPurposeForm> {
  static const _labels = {
    'CONSULTATION': 'Khám bệnh',
    'CARE_CONTINUITY': 'Tiếp nối điều trị',
    'QUALITY_REVIEW': 'Đánh giá chất lượng',
    'EMERGENCY': 'Cấp cứu',
    'RECORD_MANAGEMENT': 'Quản lý hồ sơ',
  };
  static const _byRole = {
    'DOCTOR': [
      'CONSULTATION',
      'CARE_CONTINUITY',
      'QUALITY_REVIEW',
      'EMERGENCY',
    ],
    'ADMIN': ['RECORD_MANAGEMENT', 'QUALITY_REVIEW'],
    'NURSE': ['EMERGENCY'],
  };
  static const _needsReason = {'QUALITY_REVIEW', 'RECORD_MANAGEMENT'};
  static const _minReasonLength = 10;

  final _reason = TextEditingController();
  String? _purpose;
  Object? _error;
  bool _saving = false;

  @override
  void dispose() {
    _reason.dispose();
    super.dispose();
  }

  bool get _reasonRequired => _needsReason.contains(_purpose);
  bool get _ready =>
      _purpose != null &&
      !_saving &&
      (!_reasonRequired || _reason.text.trim().length >= _minReasonLength);

  Future<void> _submit() async {
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      await ref
          .read(medicalRecordRepositoryProvider)
          .declareAccess(
            patientId: widget.patientId,
            purpose: _purpose!,
            reason: _reasonRequired ? _reason.text.trim() : null,
          );
      if (mounted) widget.onDeclared();
    } catch (error) {
      if (mounted) setState(() => _error = error);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) => SingleChildScrollView(
    padding: const EdgeInsets.all(16),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      spacing: 12,
      children: [
        const LText('Bạn cần khai báo mục đích trước khi xem hồ sơ này.'),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            for (final purpose in _byRole[widget.role] ?? const <String>[])
              ChoiceChip(
                label: LText(_labels[purpose]!),
                selected: _purpose == purpose,
                onSelected: _saving
                    ? null
                    : (_) => setState(() {
                        _purpose = purpose;
                        _error = null;
                      }),
              ),
          ],
        ),
        if (_reasonRequired)
          TextField(
            controller: _reason,
            maxLength: 500,
            maxLines: 3,
            onChanged: (_) => setState(() {}),
            decoration: InputDecoration(
              labelText: AppL10n.text(
                context,
                'Lý do (ít nhất $_minReasonLength ký tự)',
              ),
            ),
          ),
        if (_error != null) _RetryMessage(error: _error!),
        FilledButton(
          onPressed: _ready ? _submit : null,
          child: const LText('Xác nhận mục đích'),
        ),
      ],
    ),
  );
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
          LText(
            error is ApiFailure
                ? (error as ApiFailure).message
                : error is ArgumentError
                ? (error as ArgumentError).message.toString()
                : 'Không thể tải hoặc lưu dữ liệu.',
          ),
          if (error is ApiFailure &&
              (error as ApiFailure).correlationId != null)
            LText('Mã hỗ trợ: ${(error as ApiFailure).correlationId}'),
          if (onRetry != null)
            TextButton.icon(
              onPressed: onRetry,
              icon: const Icon(Icons.refresh),
              label: const LText('Thử lại tải dữ liệu'),
            ),
        ],
      ),
    ),
  );
}

String _recordStatusLabel(String status) => switch (status) {
  'OPEN' => 'Đang khám',
  'COMPLETED' => 'Đã hoàn tất khám',
  'CLOSED_WITHOUT_CONCLUSION' => 'Kết thúc không kết luận',
  _ => 'Trạng thái khác',
};

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
