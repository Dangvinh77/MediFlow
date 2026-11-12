import '../../../core/l10n/app_l10n.dart';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mediflow_loader/mediflow_loader.dart';

import '../../../core/network/api_failure.dart';
import '../../../core/widgets/medical_ui.dart';
import '../domain/exam_lifecycle.dart';
import '../domain/patient_timeline.dart';
import 'medical_record_formatters.dart';

final patientAllergyRepositoryProvider = Provider<PatientAllergyRepository?>(
  (ref) => null,
);

class PatientAllergySection extends ConsumerStatefulWidget {
  const PatientAllergySection({
    super.key,
    required this.patientId,
    required this.sourceRecordId,
    this.canAdd = true,
    this.canManageStatus = true,
  });
  final String patientId, sourceRecordId;
  final bool canAdd, canManageStatus;

  @override
  ConsumerState<PatientAllergySection> createState() =>
      _PatientAllergySectionState();
}

class _PatientAllergySectionState extends ConsumerState<PatientAllergySection> {
  PatientAllergies? _allergies;
  Object? _error;
  String? _commandError;
  bool _loading = true, _showAdd = false, _addBusy = false;
  int _generation = 0;
  final _substance = TextEditingController();
  final _reaction = TextEditingController();
  final _statusReason = TextEditingController();
  String _severity = 'UNKNOWN';
  String? _editingStatusId;
  String? _editingStatusValue;
  _AllergyAddIntent? _addIntent;
  _AllergyStatusIntent? _statusIntent;
  bool _statusBusy = false;

  @override
  void initState() {
    super.initState();
    Future.microtask(_load);
  }

  @override
  void didUpdateWidget(covariant PatientAllergySection oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.patientId != oldWidget.patientId) {
      _allergies = null;
      _error = null;
      _loading = true;
      _generation++;
      _addIntent = null;
      _statusIntent = null;
      _editingStatusId = null;
      Future.microtask(_load);
    }
  }

  @override
  void dispose() {
    _substance.dispose();
    _reaction.dispose();
    _statusReason.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    final repository = ref.read(patientAllergyRepositoryProvider);
    final generation = ++_generation;
    final patientId = widget.patientId;
    setState(() {
      _allergies = null;
      _error = null;
      _loading = true;
    });
    try {
      if (repository == null) throw StateError('Không có kết nối sổ dị ứng.');
      final result = await repository.list(patientId);
      if (mounted &&
          generation == _generation &&
          patientId == widget.patientId) {
        setState(() {
          _allergies = result;
          _loading = false;
        });
      }
    } catch (error) {
      if (mounted &&
          generation == _generation &&
          patientId == widget.patientId) {
        setState(() {
          _error = error;
          _loading = false;
        });
      }
    }
  }

  Future<void> _saveAllergy() async {
    if (_addBusy) return;
    final repository = ref.read(patientAllergyRepositoryProvider);
    if (repository == null) return;
    final intent =
        _addIntent ??
        _AllergyAddIntent(
          substance: _substance.text.trim(),
          reaction: _reaction.text.trim().isEmpty
              ? null
              : _reaction.text.trim(),
          severity: _severity,
          sourceRecordId: widget.sourceRecordId,
          key: newExamCommandKey(),
        );
    if (intent.substance.isEmpty || intent.substance.length > 200) {
      setState(
        () => _commandError = 'Tên dị nguyên phải có từ 1 đến 200 ký tự.',
      );
      return;
    }
    if ((intent.reaction?.length ?? 0) > 500) {
      setState(() => _commandError = 'Phản ứng không được dài quá 500 ký tự.');
      return;
    }
    setState(() {
      _addIntent = intent;
      _addBusy = true;
      _commandError = null;
    });
    try {
      await repository.add(
        widget.patientId,
        substance: intent.substance,
        reaction: intent.reaction,
        severity: intent.severity,
        sourceRecordId: intent.sourceRecordId,
        idempotencyKey: intent.key,
      );
      if (!mounted) return;
      setState(() {
        _addIntent = null;
        _showAdd = false;
        _substance.clear();
        _reaction.clear();
        _severity = 'UNKNOWN';
      });
      await _load();
    } catch (error) {
      if (!mounted) return;
      final unknown = _outcomeUnknown(error);
      setState(() {
        _commandError = unknown
            ? 'Kết quả chưa rõ. Gửi lại cùng yêu cầu để kiểm tra kết quả.'
            : _message(error);
        if (!unknown) _addIntent = null;
      });
    } finally {
      if (mounted) setState(() => _addBusy = false);
    }
  }

  void _startStatusChange(PatientAllergy allergy, String status) {
    setState(() {
      _editingStatusId = allergy.allergyId;
      _editingStatusValue = status;
      _statusReason.clear();
      _statusIntent = null;
      _commandError = null;
    });
  }

  Future<void> _saveStatus(PatientAllergy allergy, String status) async {
    if (_statusBusy) return;
    final repository = ref.read(patientAllergyRepositoryProvider);
    if (repository == null) return;
    final reason = _statusIntent?.reason ?? _statusReason.text.trim();
    if (reason.isEmpty || reason.length > 500) {
      setState(() => _commandError = 'Nhập lý do từ 1 đến 500 ký tự.');
      return;
    }
    final intent =
        _statusIntent ??
        _AllergyStatusIntent(
          allergyId: allergy.allergyId,
          status: status,
          reason: reason,
          key: newExamCommandKey(),
        );
    setState(() {
      _statusIntent = intent;
      _statusBusy = true;
      _commandError = null;
    });
    try {
      await repository.changeStatus(
        widget.patientId,
        intent.allergyId,
        status: intent.status,
        reason: intent.reason,
        idempotencyKey: intent.key,
      );
      if (!mounted) return;
      setState(() {
        _statusIntent = null;
        _editingStatusId = null;
        _editingStatusValue = null;
      });
      await _load();
    } catch (error) {
      if (!mounted) return;
      final unknown = _outcomeUnknown(error);
      setState(() {
        _commandError = unknown
            ? 'Kết quả chưa rõ. Gửi lại cùng yêu cầu để kiểm tra kết quả.'
            : _message(error);
        if (!unknown) _statusIntent = null;
      });
    } finally {
      if (mounted) setState(() => _statusBusy = false);
    }
  }

  @override
  Widget build(BuildContext context) => Card(
    key: const Key('patient-allergy-section'),
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          LText(
            'Dị ứng của bệnh nhân',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: 8),
          if (_loading)
            const Center(child: MediFlowLoader())
          else if (_error != null)
            _loadFailure()
          else if (_allergies case final allergies?) ...[
            if (allergies.active.isEmpty)
              const LText('Chưa ghi nhận dị ứng')
            else
              for (final allergy in allergies.active) _allergyRow(allergy),
            if (_commandError != null) NoticeBox(_commandError!),
            if (widget.canAdd) ...[
              if (!_showAdd)
                Align(
                  alignment: Alignment.centerLeft,
                  child: TextButton.icon(
                    key: const Key('patient-allergy-add'),
                    onPressed: () => setState(() {
                      _showAdd = true;
                      _commandError = null;
                    }),
                    icon: const Icon(Icons.add),
                    label: const LText('Thêm dị ứng'),
                  ),
                )
              else
                _addForm(),
            ],
            if (allergies.inactive.isNotEmpty)
              ExpansionTile(
                tilePadding: EdgeInsets.zero,
                title: const LText('Dị ứng đã ngừng hoặc nhập nhầm'),
                children: [
                  for (final allergy in allergies.inactive)
                    _allergyRow(allergy, inactive: true),
                ],
              ),
          ],
        ],
      ),
    ),
  );

  Widget _allergyRow(PatientAllergy allergy, {bool inactive = false}) {
    final editing = _editingStatusId == allergy.allergyId;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          ListTile(
            contentPadding: EdgeInsets.zero,
            title: LText(allergy.substance),
            subtitle: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (allergy.reaction?.isNotEmpty == true)
                  LText('Phản ứng: ${allergy.reaction}'),
                LText('Mức độ: ${_severityLabel(allergy.severity)}'),
                LText('Ghi nhận: ${formatClinicalInstant(allergy.recordedAt)}'),
                if (allergy.sourceRecordId != null)
                  LText('Nguồn hồ sơ: ${allergy.sourceRecordId}'),
                if (inactive) ...[
                  LText('Trạng thái: ${_statusLabel(allergy.status)}'),
                  if (allergy.statusReason?.isNotEmpty == true)
                    LText('Lý do: ${allergy.statusReason}'),
                  if (allergy.statusChangedByStaffId != null)
                    LText('Cập nhật bởi: ${allergy.statusChangedByStaffId}'),
                  if (allergy.statusChangedAt != null)
                    LText(
                      'Cập nhật lúc: ${formatClinicalInstant(allergy.statusChangedAt!)}',
                    ),
                ],
              ],
            ),
            trailing: !widget.canManageStatus || inactive
                ? null
                : Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      IconButton(
                        key: const Key('allergy-entered-in-error'),
                        tooltip: AppL10n.text(context, 'Đánh dấu nhập nhầm'),
                        onPressed: _statusBusy
                            ? null
                            : () => _startStatusChange(
                                allergy,
                                'ENTERED_IN_ERROR',
                              ),
                        icon: const Icon(Icons.report_gmailerrorred_outlined),
                      ),
                      IconButton(
                        key: const Key('allergy-resolved'),
                        tooltip: AppL10n.text(
                          context,
                          'Đánh dấu đã hết dị ứng',
                        ),
                        onPressed: _statusBusy
                            ? null
                            : () => _startStatusChange(allergy, 'RESOLVED'),
                        icon: const Icon(Icons.check_circle_outline),
                      ),
                    ],
                  ),
          ),
          if (editing && widget.canManageStatus)
            Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  TextField(
                    key: const Key('allergy-status-reason'),
                    controller: _statusReason,
                    onChanged: (_) => setState(() {}),
                    enabled: !_statusBusy && _statusIntent == null,
                    maxLength: 500,
                    decoration: InputDecoration(
                      labelText: AppL10n.text(
                        context,
                        _editingStatusValue == 'RESOLVED'
                            ? 'Lý do cập nhật trạng thái dị ứng *'
                            : 'Lý do nhập nhầm dị ứng *',
                      ),
                    ),
                  ),
                  Wrap(
                    spacing: 8,
                    children: [
                      FilledButton(
                        key: const Key('allergy-status-submit'),
                        onPressed:
                            _statusBusy ||
                                (_statusIntent == null &&
                                    _statusReason.text.trim().isEmpty)
                            ? null
                            : () => _saveStatus(
                                allergy,
                                _editingStatusValue ?? 'ENTERED_IN_ERROR',
                              ),
                        child: LText(
                          _statusIntent != null
                              ? 'Gửi lại cùng yêu cầu'
                              : _editingStatusValue == 'RESOLVED'
                              ? 'Xác nhận đã hết dị ứng'
                              : 'Xác nhận nhập nhầm',
                        ),
                      ),
                      TextButton(
                        onPressed: _statusBusy
                            ? null
                            : () => setState(() {
                                _editingStatusId = null;
                                _editingStatusValue = null;
                                _statusIntent = null;
                              }),
                        child: const LText('Hủy'),
                      ),
                    ],
                  ),
                ],
              ),
            ),
        ],
      ),
    );
  }

  Widget _addForm() => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      TextField(
        key: const Key('allergy-substance'),
        controller: _substance,
        onChanged: (_) => setState(() {}),
        enabled: !_addBusy && _addIntent == null,
        maxLength: 200,
        decoration: InputDecoration(
          labelText: AppL10n.text(context, 'Tên dị nguyên *'),
        ),
      ),
      TextField(
        key: const Key('allergy-reaction'),
        controller: _reaction,
        enabled: !_addBusy && _addIntent == null,
        maxLength: 500,
        decoration: InputDecoration(
          labelText: AppL10n.text(context, 'Phản ứng'),
        ),
      ),
      DropdownButtonFormField<String>(
        key: const Key('allergy-severity'),
        initialValue: _severity,
        decoration: InputDecoration(labelText: AppL10n.text(context, 'Mức độ')),
        items: const [
          DropdownMenuItem(value: 'MILD', child: LText('Nhẹ')),
          DropdownMenuItem(value: 'MODERATE', child: LText('Vừa')),
          DropdownMenuItem(value: 'SEVERE', child: LText('Nặng')),
          DropdownMenuItem(value: 'UNKNOWN', child: LText('Chưa rõ')),
        ],
        onChanged: _addBusy || _addIntent != null
            ? null
            : (value) => setState(() => _severity = value ?? 'UNKNOWN'),
      ),
      Wrap(
        spacing: 8,
        children: [
          FilledButton(
            key: const Key('allergy-submit'),
            onPressed:
                _addBusy ||
                    (_addIntent == null && _substance.text.trim().isEmpty)
                ? null
                : _saveAllergy,
            child: LText(
              _addIntent == null ? 'Lưu dị ứng' : 'Gửi lại cùng yêu cầu',
            ),
          ),
          if (_addIntent == null)
            TextButton(
              onPressed: _addBusy
                  ? null
                  : () => setState(() {
                      _showAdd = false;
                      _commandError = null;
                    }),
              child: const LText('Hủy'),
            ),
        ],
      ),
    ],
  );

  Widget _loadFailure() {
    if (_error is ApiFailure && (_error as ApiFailure).status == 403) {
      return const LText('Bạn không có quyền xem sổ dị ứng của bệnh nhân này.');
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const LText('Không tải được sổ dị ứng của bệnh nhân.'),
        TextButton.icon(
          key: const Key('patient-allergy-retry'),
          onPressed: _load,
          icon: const Icon(Icons.refresh),
          label: const LText('Thử lại'),
        ),
      ],
    );
  }

  String _severityLabel(String value) => switch (value) {
    'MILD' => 'Nhẹ',
    'MODERATE' => 'Vừa',
    'SEVERE' => 'Nặng',
    _ => 'Chưa rõ',
  };

  String _statusLabel(String value) => switch (value) {
    'RESOLVED' => 'Đã hết dị ứng',
    'ENTERED_IN_ERROR' => 'Đã đánh dấu nhập nhầm',
    _ => value,
  };

  String _message(Object error) => error is ApiFailure
      ? error.message
      : error is ArgumentError
      ? error.message.toString()
      : 'Không thể lưu dữ liệu dị ứng.';

  bool _outcomeUnknown(Object error) =>
      error is! ApiFailure ||
      error.status == null ||
      error.status == 408 ||
      (error.status ?? 0) >= 500;
}

class _AllergyAddIntent {
  const _AllergyAddIntent({
    required this.substance,
    required this.reaction,
    required this.severity,
    required this.sourceRecordId,
    required this.key,
  });
  final String substance, severity, key;
  final String? reaction, sourceRecordId;
}

class _AllergyStatusIntent {
  const _AllergyStatusIntent({
    required this.allergyId,
    required this.status,
    required this.reason,
    required this.key,
  });
  final String allergyId, status, reason, key;
}
