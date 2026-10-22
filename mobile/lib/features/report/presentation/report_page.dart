import 'package:flutter/material.dart';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/providers.dart';
import '../../../core/widgets/reference_widgets.dart';
import '../../../core/widgets/request_state.dart';
import '../domain/report.dart';

final reportRepositoryProvider = Provider<ReportRepository>(
  (ref) => throw StateError(
    'ReportRepository must be bound in app/dependencies.dart',
  ),
);
const reportTitles = {
  ReportKind.daily: 'Báo cáo ngày',
  ReportKind.monthly: 'Báo cáo tháng',
  ReportKind.topMedicines: 'Thuốc sử dụng nhiều',
  ReportKind.outpatientDaily: 'Ngoại trú theo ngày',
  ReportKind.outpatientMonthly: 'Ngoại trú theo tháng',
};

class ReportPage extends ConsumerWidget {
  const ReportPage({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final access = ref.watch(workspaceAccessProvider);
    if (access == null || !const ['ADMIN', 'MANAGER'].contains(access.role)) {
      return const Center(child: Text('Bạn không có quyền xem báo cáo.'));
    }
    return _ReportWorkspace(
      key: ObjectKey(access.sessionIdentity),
      repository: ref.watch(reportRepositoryProvider),
    );
  }
}

class _ReportWorkspace extends StatefulWidget {
  const _ReportWorkspace({super.key, required this.repository});
  final ReportRepository repository;
  @override
  State<_ReportWorkspace> createState() => _ReportWorkspaceState();
}

class _ReportWorkspaceState extends State<_ReportWorkspace> {
  ReportKind kind = ReportKind.daily;
  final from = TextEditingController(
        text: DateTime.now().toIso8601String().substring(0, 10),
      ),
      to = TextEditingController(
        text: DateTime.now().toIso8601String().substring(0, 10),
      );
  String? department;
  ReportData? data;
  Object? error;
  bool loading = false;
  int generation = 0;
  @override
  void dispose() {
    generation++;
    from.dispose();
    to.dispose();
    super.dispose();
  }

  Future<void> load() async {
    final current = ++generation;
    setState(() {
      loading = true;
      error = null;
      data = null;
    });
    try {
      final result = await widget.repository.read(
        ReportQuery(
          kind: kind,
          from: from.text,
          to: to.text,
          departmentId: department,
        ),
      );
      if (mounted && current == generation) setState(() => data = result);
    } catch (e) {
      if (mounted && current == generation) {
        setState(
          () => error = e is ArgumentError
              ? FormatException(e.message.toString())
              : e,
        );
      }
    } finally {
      if (mounted && current == generation) setState(() => loading = false);
    }
  }

  void changed() {
    generation++;
    setState(() {
      data = null;
      error = null;
      loading = false;
    });
  }

  Future<void> date(TextEditingController controller) async {
    final value = await showDatePicker(
      context: context,
      initialDate: DateTime.tryParse(controller.text) ?? DateTime.now(),
      firstDate: DateTime(2020),
      lastDate: DateTime(2100),
    );
    if (value != null && mounted) {
      controller.text = value.toIso8601String().substring(0, 10);
      changed();
    }
  }

  @override
  Widget build(BuildContext context) => ListView(
    padding: const EdgeInsets.all(20),
    children: [
      Text(
        'Thống kê hoạt động',
        style: Theme.of(context).textTheme.headlineSmall,
      ),
      const SizedBox(height: 16),
      DropdownButtonFormField<ReportKind>(
        initialValue: kind,
        items: [
          for (final k in ReportKind.values)
            DropdownMenuItem(value: k, child: Text(reportTitles[k]!)),
        ],
        onChanged: loading
            ? null
            : (k) {
                kind = k!;
                changed();
              },
        decoration: const InputDecoration(labelText: 'Loại báo cáo'),
      ),
      const SizedBox(height: 12),
      TextField(
        controller: from,
        readOnly: true,
        onTap: () => date(from),
        decoration: const InputDecoration(
          labelText: 'Ngày bắt đầu / tháng / năm',
          suffixIcon: Icon(Icons.calendar_month),
        ),
      ),
      if (kind == ReportKind.topMedicines ||
          kind == ReportKind.outpatientDaily) ...[
        const SizedBox(height: 12),
        TextField(
          controller: to,
          readOnly: true,
          onTap: () => date(to),
          decoration: const InputDecoration(
            labelText: 'Đến ngày',
            suffixIcon: Icon(Icons.calendar_month),
          ),
        ),
      ],
      ReferencePicker(
        kind: ReferenceKind.department,
        label: 'Khoa phòng (tất cả nếu bỏ trống)',
        value: department,
        onChanged: loading
            ? null
            : (v) {
                department = v;
                changed();
              },
      ),
      const SizedBox(height: 12),
      FilledButton.icon(
        onPressed: loading ? null : load,
        icon: const Icon(Icons.bar_chart),
        label: const Text('Xem báo cáo'),
      ),
      if (loading)
        const Padding(
          padding: EdgeInsets.all(24),
          child: Center(child: CircularProgressIndicator()),
        ),
      if (error != null) RequestError(error!, onRetry: load),
      if (data != null) ...[
        const SizedBox(height: 24),
        Text(data!.title, style: Theme.of(context).textTheme.titleLarge),
        if (data!.departmentId != null)
          ReferenceLabel(
            kind: ReferenceKind.department,
            id: data!.departmentId!,
            label: 'Khoa',
          ),
        if (data!.note != null)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 12),
            child: Text(data!.note!),
          ),
        if (data!.rows.isEmpty)
          const Padding(
            padding: EdgeInsets.all(24),
            child: Text('Chưa có số liệu trong khoảng đã chọn.'),
          ),
        for (final row in data!.rows)
          Card(
            margin: const EdgeInsets.symmetric(vertical: 8),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    row.title,
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  const Divider(),
                  for (final metric in row.metrics.entries)
                    Padding(
                      padding: const EdgeInsets.symmetric(vertical: 5),
                      child: Row(
                        children: [
                          Expanded(child: Text(metric.key)),
                          Text(
                            _number(metric.value),
                            style: Theme.of(context).textTheme.titleMedium,
                          ),
                        ],
                      ),
                    ),
                ],
              ),
            ),
          ),
      ],
    ],
  );
  String _number(num value) => value
      .toStringAsFixed(value % 1 == 0 ? 0 : 2)
      .replaceAllMapped(RegExp(r'\B(?=(\d{3})+(?!\d))'), (_) => '.');
}
