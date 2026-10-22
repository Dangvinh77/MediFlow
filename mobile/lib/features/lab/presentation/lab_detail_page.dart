import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/providers.dart';
import '../../../core/network/api_failure.dart';
import '../../../core/widgets/reference_widgets.dart';
import '../../../core/widgets/request_state.dart';
import '../domain/lab_workflow.dart';

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
      key: ObjectKey(access.sessionIdentity),
      id: id,
      role: access.role,
      actorId: access.staffId,
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
    this.actorId,
  });
  final String id, role;
  final LabWorkflow workflow;
  final String? actorId;
  @override
  State<_LabDetail> createState() => _LabDetailState();
}

class _LabDetailState extends State<_LabDetail> {
  LabDetail? value;
  Object? error, commandError;
  bool busy = false, uncertain = false;
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
    setState(() {
      busy = true;
      error = null;
    });
    try {
      final data = await widget.workflow.detail(widget.id);
      if (mounted) {
        setState(() {
          value = data;
          uncertain = false;
        });
      }
    } catch (e) {
      if (mounted) setState(() => error = e);
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> command(String title, Future<LabDetail> Function() call) async {
    if (busy || uncertain) return;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(title),
        content: const Text('Thao tác sẽ cập nhật xét nghiệm trên hệ thống.'),
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
    setState(() {
      busy = true;
      commandError = null;
    });
    try {
      final data = await call();
      if (mounted) setState(() => value = data);
    } catch (e) {
      if (mounted) {
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
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final v = value;
    return Scaffold(
      appBar: AppBar(title: const Text('Chi tiết xét nghiệm')),
      body: error != null
          ? RequestError(error!, onRetry: load)
          : v == null
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(20),
              children: [
                Text(v.type, style: Theme.of(context).textTheme.headlineSmall),
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
                Chip(label: Text(v.status)),
                Text('Ngày yêu cầu: ${v.requestedDate}'),
                if (v.performedDate != null)
                  Text('Ngày thực hiện: ${v.performedDate}'),
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
                        'Tham chiếu: ${row.referenceRange ?? 'Chưa cung cấp'}',
                      ),
                      trailing: Text('${row.value} ${row.unit ?? ''}'),
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
                if (const ['ADMIN', 'LAB_TECH'].contains(widget.role)) ...[
                  if (v.status == 'READY' || v.status == 'PENDING')
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
                              _field(rows[index].range, 'Khoảng tham chiếu'),
                              if (rows.length > 1)
                                TextButton.icon(
                                  onPressed: busy || uncertain
                                      ? null
                                      : () => setState(() {
                                          rows.removeAt(index).dispose();
                                        }),
                                  icon: const Icon(Icons.remove_circle_outline),
                                  label: const Text('Xóa chỉ số'),
                                ),
                            ],
                          ),
                        ),
                      ),
                    OutlinedButton.icon(
                      onPressed: busy || uncertain
                          ? null
                          : () =>
                                setState(() => rows.add(_ResultControllers())),
                      icon: const Icon(Icons.add),
                      label: const Text('Thêm chỉ số'),
                    ),
                    _field(date, 'Ngày thực hiện (YYYY-MM-DD)'),
                    _field(conclusion, 'Kết luận'),
                    FilledButton(
                      onPressed: busy || uncertain
                          ? null
                          : () => command(
                              'Lưu kết quả xét nghiệm',
                              () => widget.workflow.results(
                                v.id,
                                [for (final row in rows) row.draft],
                                conclusion.text,
                                date.text,
                              ),
                            ),
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
                  const Divider(),
                  _field(reason, 'Lý do hủy'),
                  const Text('Người thực hiện được lấy từ phiên đăng nhập.'),
                  OutlinedButton(
                    onPressed:
                        busy ||
                            uncertain ||
                            widget.actorId == null ||
                            reason.text.trim().isEmpty
                        ? null
                        : () => command(
                            'Hủy xét nghiệm',
                            () => widget.workflow.cancel(
                              v.id,
                              reason.text,
                              widget.actorId!,
                            ),
                          ),
                    child: const Text('Hủy xét nghiệm'),
                  ),
                ],
                if (busy) const LinearProgressIndicator(),
              ],
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
