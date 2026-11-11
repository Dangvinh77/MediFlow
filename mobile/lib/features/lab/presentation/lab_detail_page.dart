import 'package:flutter/material.dart';

import '../../../core/widgets/medical_ui.dart';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mediflow_loader/mediflow_loader.dart';

import '../../../core/providers.dart';
import '../../../core/network/api_failure.dart';
import '../../../core/widgets/reference_widgets.dart';
import '../../../core/widgets/request_state.dart';
import '../domain/lab_catalog.dart';
import '../domain/lab_workflow.dart';
import 'lab_labels.dart';
import 'lab_template_result_entry.dart';

final labWorkflowProvider = Provider<LabWorkflow>(
  (ref) =>
      throw StateError('LabWorkflow must be bound in app/dependencies.dart'),
);

class LabDetailPage extends ConsumerWidget {
  const LabDetailPage({super.key, required this.id});
  final String id;
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final access = ref.watch(workspaceAccessProvider);
    if (access == null) return const SizedBox.shrink();
    return _LabDetail(
      key: ValueKey((ObjectKey(access.sessionIdentity), id)),
      id: id,
      role: access.role,
      workflow: ref.watch(labWorkflowProvider),
    );
  }
}

class _LabDetail extends StatefulWidget {
  const _LabDetail({
    super.key,
    required this.id,
    required this.role,
    required this.workflow,
  });
  final String id, role;
  final LabWorkflow workflow;
  @override
  State<_LabDetail> createState() => _LabDetailState();
}

class _LabDetailState extends State<_LabDetail> {
  LabDetail? value;
  Object? error, commandError;
  Object? _templateError;
  LabResultTemplate? _resultTemplate;
  String? _templateTestId;
  String? _resolvedTemplateTestId;
  int _templateRequest = 0;
  bool _templateLoading = false;
  bool _structuredDraftDirty = false;
  final _structuredEntryKey = GlobalKey<LabTemplateResultEntryState>();
  bool busy = false, uncertain = false;
  bool _allowLeave = false;
  bool get _hasDraft =>
      value?.status == 'IN_PROGRESS' &&
      (_structuredDraftDirty ||
          conclusion.text.isNotEmpty ||
          rows.any(
            (row) =>
                row.indicator.text.isNotEmpty ||
                row.value.text.isNotEmpty ||
                row.unit.text.isNotEmpty ||
                row.range.text.isNotEmpty,
          ));
  final rows = <_ResultControllers>[_ResultControllers()];
  final conclusion = TextEditingController(), reason = TextEditingController();
  final date = TextEditingController(
    text: DateTime.now().toIso8601String().substring(0, 10),
  );
  @override
  void initState() {
    super.initState();
    load();
  }

  @override
  void dispose() {
    for (final row in rows) {
      row.dispose();
    }
    conclusion.dispose();
    reason.dispose();
    date.dispose();
    super.dispose();
  }

  Future<void> load() async {
    if (busy) return;
    final requestedId = widget.id;
    setState(() {
      busy = true;
      error = null;
    });
    try {
      final data = await widget.workflow.detail(requestedId);
      if (mounted && widget.id == requestedId) {
        setState(() {
          value = data;
          uncertain = false;
        });
        await _syncTemplate(data);
      }
    } catch (e) {
      if (mounted && widget.id == requestedId) setState(() => error = e);
    } finally {
      if (mounted && widget.id == requestedId) setState(() => busy = false);
    }
  }

  Future<void> _syncTemplate(LabDetail detail, {bool retry = false}) async {
    final reader = widget.workflow;
    final requestedId = detail.id;
    final request = ++_templateRequest;
    if (detail.status != 'IN_PROGRESS') {
      setState(() {
        _templateTestId = null;
        _resolvedTemplateTestId = null;
        _resultTemplate = null;
        _templateError = null;
        _templateLoading = false;
        _structuredDraftDirty = false;
      });
      return;
    }
    if (!retry &&
        _resolvedTemplateTestId == requestedId &&
        _templateTestId == requestedId) {
      return;
    }
    final previousTemplateId = _templateTestId;
    setState(() {
      _templateTestId = requestedId;
      _templateError = null;
      _templateLoading = true;
      if (previousTemplateId != requestedId) {
        _resultTemplate = null;
        _structuredDraftDirty = false;
      }
    });
    try {
      final response = await reader.template(requestedId);
      if (!mounted ||
          widget.id != requestedId ||
          request != _templateRequest ||
          value?.id != requestedId) {
        return;
      }
      setState(() {
        _resultTemplate = response.template;
        _templateTestId = requestedId;
        _resolvedTemplateTestId = requestedId;
        _templateLoading = false;
        _templateError = null;
        _structuredDraftDirty = false;
      });
    } catch (e) {
      if (!mounted ||
          widget.id != requestedId ||
          request != _templateRequest ||
          value?.id != requestedId) {
        return;
      }
      setState(() {
        _templateTestId = requestedId;
        _resolvedTemplateTestId = null;
        _templateLoading = false;
        _templateError = e;
      });
    }
  }

  Future<void> command(String title, Future<LabDetail> Function() call) async {
    if (busy || uncertain) return;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(title),
        scrollable: true,
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (value != null)
              ReferenceLabel(
                kind: ReferenceKind.patient,
                id: value!.patientId,
                label: 'Bệnh nhân',
              ),
            if (value != null) Text(value!.type),
            Text(
              title == 'Hủy xét nghiệm'
                  ? 'Lý do: ${reason.text.trim()}'
                  : 'Kiểm tra đúng bệnh nhân và nội dung trước khi xác nhận.',
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Quay lại'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Xác nhận'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted || busy || uncertain) return;
    final commandId = widget.id;
    setState(() {
      busy = true;
      commandError = null;
    });
    try {
      final data = await call();
      if (mounted && widget.id == commandId) {
        setState(() => value = data);
        await _syncTemplate(data);
      }
    } catch (e) {
      if (mounted && widget.id == commandId) {
        setState(() {
          commandError = e;
          uncertain =
              e is! ArgumentError &&
              (e is! ApiFailure ||
                  e.status == null ||
                  e.status == 408 ||
                  e.status! >= 500);
        });
      }
    } finally {
      if (mounted && widget.id == commandId) setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final v = value;
    return PopScope(
      canPop: !busy && !uncertain && (!_hasDraft || _allowLeave),
      onPopInvokedWithResult: (didPop, result) async {
        if (didPop || busy || uncertain || !_hasDraft) return;
        final leave = await showDialog<bool>(
          context: context,
          builder: (context) => AlertDialog(
            title: const Text('Kết quả chưa lưu'),
            content: const Text('Rời màn hình sẽ bỏ các chỉ số đang nhập.'),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context, false),
                child: const Text('Tiếp tục nhập'),
              ),
              TextButton(
                onPressed: () => Navigator.pop(context, true),
                child: const Text('Bỏ bản nháp'),
              ),
            ],
          ),
        );
        if (leave == true && mounted) {
          setState(() => _allowLeave = true);
          WidgetsBinding.instance.addPostFrameCallback((_) {
            if (mounted) Navigator.of(context).pop();
          });
        }
      },
      child: Scaffold(
        appBar: AppBar(title: const Text('Chi tiết xét nghiệm')),
        body: v == null
            ? error != null
                  ? RequestError(error!, onRetry: load)
                  : const Center(child: MediFlowLoader())
            : ListView(
                padding: const EdgeInsets.all(20),
                children: [
                  if (error != null) RequestError(error!, onRetry: load),
                  Text(
                    v.type,
                    style: Theme.of(context).textTheme.headlineSmall,
                  ),
                  const SizedBox(height: 16),
                  ReferenceLabel(
                    kind: ReferenceKind.patient,
                    id: v.patientId,
                    label: 'Bệnh nhân',
                  ),
                  ReferenceLabel(
                    kind: ReferenceKind.department,
                    id: v.departmentId,
                    label: 'Khoa chỉ định',
                  ),
                  ReferenceLabel(
                    kind: ReferenceKind.record,
                    id: v.recordId,
                    label: 'Hồ sơ khám',
                  ),
                  const SizedBox(height: 12),
                  StatusPill(
                    labStatusLabel(v.status),
                    tone: statusToneOf(v.status),
                  ),
                  const SizedBox(height: 8),
                  Text('Ngày yêu cầu: ${labDateLabel(v.requestedDate)}'),
                  if (v.performedDate != null)
                    Text('Ngày thực hiện: ${labDateLabel(v.performedDate!)}'),
                  Text(
                    v.version == 1
                        ? 'Đối soát viện phí: ${v.clearanceId != null ? 'Đã có xác nhận' : 'Chưa có xác nhận'}'
                        : 'Thanh toán: ${v.paid ? 'Đã thanh toán' : 'Chưa thanh toán'}',
                  ),
                  if (v.conclusion != null)
                    Padding(
                      padding: const EdgeInsets.symmetric(vertical: 12),
                      child: Text('Kết luận: ${v.conclusion}'),
                    ),
                  for (final row in v.results)
                    Card(
                      child: ListTile(
                        title: Text(row.indicator),
                        subtitle: Text(
                          'Kết quả: ${row.value} ${row.unit ?? ''}\n'
                          'Tham chiếu: ${row.referenceRange ?? 'Chưa cung cấp'}',
                        ),
                      ),
                    ),
                  if (commandError != null) RequestError(commandError!),
                  if (uncertain)
                    const Text(
                      'Chưa xác định kết quả thao tác. Hãy tải lại trạng thái trước khi tiếp tục.',
                    ),
                  OutlinedButton.icon(
                    onPressed: busy ? null : load,
                    icon: const Icon(Icons.refresh),
                    label: const Text('Tải lại trạng thái'),
                  ),
                  if (uncertain) const Text('Các thao tác đã được tạm khóa.'),
                  if (!uncertain &&
                      !busy &&
                      (v.status == 'READY' || v.status == 'PENDING') &&
                      !(v.version == 1 ? v.clearanceId != null : v.paid))
                    const Text(
                      'Chờ xác nhận viện phí trước khi bắt đầu thực hiện.',
                    ),
                  if (const ['ADMIN', 'LAB_TECH'].contains(widget.role)) ...[
                    if ((v.status == 'READY' || v.status == 'PENDING') &&
                        (v.version == 1 ? v.clearanceId != null : v.paid))
                      Padding(
                        padding: const EdgeInsets.symmetric(vertical: 12),
                        child: FilledButton.icon(
                          onPressed: busy || uncertain
                              ? null
                              : () => command(
                                  'Bắt đầu xét nghiệm',
                                  () => widget.workflow.start(v.id),
                                ),
                          icon: const Icon(Icons.play_arrow),
                          label: const Text('Bắt đầu thực hiện'),
                        ),
                      ),
                    if (v.status == 'IN_PROGRESS') ...[
                      const Divider(),
                      Text(
                        'Nhập kết quả',
                        style: Theme.of(context).textTheme.titleLarge,
                      ),
                      if (_templateError != null)
                        RequestError(
                          _templateError!,
                          onRetry: () => _syncTemplate(v, retry: true),
                        )
                      else if (_templateLoading)
                        const Padding(
                          padding: EdgeInsets.all(16),
                          child: Center(child: MediFlowLoader()),
                        )
                      else if (_resultTemplate != null)
                        LabTemplateResultEntry(
                          key: _structuredEntryKey,
                          template: _resultTemplate!,
                          enabled: !busy && !uncertain,
                          onDraftChanged: (dirty) {
                            if (_structuredDraftDirty != dirty) {
                              setState(() => _structuredDraftDirty = dirty);
                            }
                          },
                        )
                      else ...[
                        for (var index = 0; index < rows.length; index++)
                          Card(
                            child: Padding(
                              padding: const EdgeInsets.all(12),
                              child: Column(
                                children: [
                                  Text('Chỉ số ${index + 1}'),
                                  _field(rows[index].indicator, 'Tên chỉ số'),
                                  _field(rows[index].value, 'Giá trị'),
                                  _field(rows[index].unit, 'Đơn vị'),
                                  _field(
                                    rows[index].range,
                                    'Khoảng tham chiếu',
                                  ),
                                  if (rows.length > 1)
                                    TextButton.icon(
                                      onPressed: busy || uncertain
                                          ? null
                                          : () => setState(() {
                                              rows.removeAt(index).dispose();
                                            }),
                                      icon: const Icon(
                                        Icons.remove_circle_outline,
                                      ),
                                      label: const Text('Xóa chỉ số'),
                                    ),
                                ],
                              ),
                            ),
                          ),
                        OutlinedButton.icon(
                          onPressed: busy || uncertain
                              ? null
                              : () => setState(
                                  () => rows.add(_ResultControllers()),
                                ),
                          icon: const Icon(Icons.add),
                          label: const Text('Thêm chỉ số'),
                        ),
                      ],
                      OutlinedButton.icon(
                        onPressed: busy || uncertain
                            ? null
                            : () async {
                                final picked = await showDatePicker(
                                  context: context,
                                  initialDate:
                                      DateTime.tryParse(date.text) ??
                                      DateTime.now(),
                                  firstDate: DateTime(2000),
                                  lastDate: DateTime.now(),
                                );
                                if (picked != null && mounted) {
                                  setState(
                                    () => date.text = picked
                                        .toIso8601String()
                                        .substring(0, 10),
                                  );
                                }
                              },
                        icon: const Icon(Icons.calendar_month),
                        label: Text(
                          'Ngày thực hiện: ${labDateLabel(date.text)}',
                        ),
                      ),
                      _field(conclusion, 'Kết luận'),
                      FilledButton(
                        onPressed:
                            busy ||
                                uncertain ||
                                _templateLoading ||
                                _templateError != null
                            ? null
                            : () => _submitResults(v),
                        child: const Text('Lưu kết quả'),
                      ),
                    ],
                  ],
                  if (const [
                        'ADMIN',
                        'DOCTOR',
                        'LAB_TECH',
                      ].contains(widget.role) &&
                      !const ['COMPLETED', 'CANCELLED'].contains(v.status)) ...[
                    ExpansionTile(
                      title: const Text('Thao tác khác'),
                      children: [
                        _field(reason, 'Lý do hủy'),
                        const Text(
                          'Người thực hiện được lấy từ phiên đăng nhập.',
                        ),
                        OutlinedButton(
                          onPressed:
                              busy || uncertain || reason.text.trim().isEmpty
                              ? null
                              : () => command(
                                  'Hủy xét nghiệm',
                                  () =>
                                      widget.workflow.cancel(v.id, reason.text),
                                ),
                          child: const Text('Hủy xét nghiệm'),
                        ),
                      ],
                    ),
                  ],
                  if (busy) const LinearProgressIndicator(),
                ],
              ),
      ),
    );
  }

  Future<void> _submitResults(LabDetail detail) async {
    final results = _resultTemplate == null
        ? [for (final row in rows) row.draft]
        : _structuredEntryKey.currentState?.buildResults();
    if (results == null || results.isEmpty) return;
    await command(
      'Lưu kết quả xét nghiệm',
      () => widget.workflow.results(
        detail.id,
        results,
        conclusion.text,
        date.text,
      ),
    );
  }

  Widget _field(TextEditingController c, String label) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 8),
    child: TextField(
      controller: c,
      onChanged: (_) => setState(() {}),
      enabled: !busy,
      decoration: InputDecoration(
        labelText: label,
        border: const OutlineInputBorder(),
      ),
    ),
  );
}

class _ResultControllers {
  final indicator = TextEditingController(),
      value = TextEditingController(),
      unit = TextEditingController(),
      range = TextEditingController();
  LabResultDraft get draft => LabResultDraft(
    indicator: indicator.text,
    value: value.text,
    unit: unit.text.trim().isEmpty ? null : unit.text.trim(),
    referenceRange: range.text.trim().isEmpty ? null : range.text.trim(),
  );
  void dispose() {
    for (final c in [indicator, value, unit, range]) {
      c.dispose();
    }
  }
}
