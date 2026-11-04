import 'package:flutter/material.dart';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/providers.dart';
import '../../../core/network/api_failure.dart';
import '../../../core/widgets/reference_widgets.dart';
import '../../../core/widgets/request_state.dart';
import '../domain/report.dart';
import 'operations_statistics.dart';
import 'outpatient_statistics.dart';

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
  ReportKind.operations: 'Doanh thu & lượt khám theo khoa',
};

List<ReportKind> availableReportKinds({required bool outpatientEnabled}) => [
  for (final kind in ReportKind.values)
    if (outpatientEnabled ||
        (kind != ReportKind.outpatientDaily &&
            kind != ReportKind.outpatientMonthly &&
            kind != ReportKind.operations))
      kind,
];

Object reportDisplayError(Object error, ReportKind kind) {
  if (error is! ApiFailure) {
    return const FormatException(
      'Chưa đọc được báo cáo. Vui lòng làm mới và thử lại.',
    );
  }
  final message = error.code == 'REPORT_NOT_FOUND' || error.status == 404
      ? (kind == ReportKind.outpatientDaily ||
                kind == ReportKind.outpatientMonthly
            ? 'Chưa có dữ liệu đã đối soát phù hợp hoặc báo cáo chưa được bật. Đây không phải số hoạt động bằng 0.'
            : 'Chưa có dữ liệu báo cáo cho khoảng đã chọn.')
      : switch (error.status) {
          400 || 422 => 'Ngày hoặc khoa chưa phù hợp. Vui lòng kiểm tra lại.',
          401 => 'Phiên đăng nhập đã hết. Vui lòng đăng nhập lại.',
          403 => 'Tài khoản này chưa được phép xem báo cáo.',
          _ => 'Chưa tải được báo cáo. Vui lòng thử lại sau.',
        };
  return ApiFailure(
    message,
    status: error.status,
    code: error.code,
    correlationId: error.correlationId,
  );
}

class ReportPage extends ConsumerWidget {
  const ReportPage({
    super.key,
    this.outpatientEnabled = const bool.fromEnvironment(
      'MEDIFLOW_REPORT_OUTPATIENT_UI_ENABLED',
    ),
  });
  final bool outpatientEnabled;
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final access = ref.watch(workspaceAccessProvider);
    if (access == null || !const ['ADMIN', 'MANAGER'].contains(access.role)) {
      return const Center(child: Text('Bạn không có quyền xem báo cáo.'));
    }
    return _ReportWorkspace(
      key: ObjectKey(access.sessionIdentity),
      repository: ref.watch(reportRepositoryProvider),
      outpatientEnabled: outpatientEnabled,
      needsDepartment: access.role == 'MANAGER',
    );
  }
}

class _ReportWorkspace extends StatefulWidget {
  const _ReportWorkspace({
    super.key,
    required this.repository,
    required this.outpatientEnabled,
    required this.needsDepartment,
  });
  final ReportRepository repository;
  final bool outpatientEnabled, needsDepartment;
  @override
  State<_ReportWorkspace> createState() => _ReportWorkspaceState();
}

class _ReportWorkspaceState extends State<_ReportWorkspace> {
  // In outpatient MVP the legacy aggregates stay empty; open on live operations instead.
  late ReportKind kind = widget.outpatientEnabled
      ? ReportKind.operations
      : ReportKind.daily;
  late final from = TextEditingController(
        text: kind == ReportKind.operations
            ? DateTime(
                DateTime.now().year,
                DateTime.now().month,
              ).toIso8601String().substring(0, 10)
            : DateTime.now().toIso8601String().substring(0, 10),
      ),
      to = TextEditingController(
        text: DateTime.now().toIso8601String().substring(0, 10),
      );
  String? department;
  ReportData? data;
  OperationsStatistics? operations;
  Object? error;
  bool loading = false;
  int generation = 0;
  @override
  void initState() {
    super.initState();
    if (kind == ReportKind.operations && !widget.needsDepartment) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) load();
      });
    }
  }

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
      operations = null;
    });
    try {
      final query = ReportQuery(
        kind: kind,
        from: from.text,
        to: to.text,
        departmentId: department,
      );
      if (kind == ReportKind.operations) {
        if (widget.needsDepartment && department == null) {
          throw ArgumentError('Chọn khoa được phân quyền.');
        }
        final result = await widget.repository.statistics(query);
        if (mounted && current == generation) {
          setState(() => operations = result);
        }
        return;
      }
      final result = await widget.repository.read(query);
      if (mounted && current == generation) setState(() => data = result);
    } catch (e) {
      if (mounted && current == generation) {
        setState(
          () => error = e is ArgumentError
              ? const FormatException(
                  'Ngày hoặc khoa chưa phù hợp. Vui lòng kiểm tra lại.',
                )
              : reportDisplayError(e, kind),
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
      operations = null;
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
          for (final k in availableReportKinds(
            outpatientEnabled: widget.outpatientEnabled,
          ))
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
        decoration: InputDecoration(
          labelText: switch (kind) {
            ReportKind.daily => 'Ngày báo cáo',
            ReportKind.monthly => 'Chọn tháng',
            ReportKind.outpatientMonthly => 'Chọn năm',
            _ => 'Từ ngày',
          },
          suffixIcon: const Icon(Icons.calendar_month),
        ),
      ),
      if (kind == ReportKind.topMedicines ||
          kind == ReportKind.outpatientDaily ||
          kind == ReportKind.operations) ...[
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
      if (kind == ReportKind.outpatientDaily ||
          kind == ReportKind.outpatientMonthly)
        AvailableReportPeriods(
          key: ValueKey('$kind:$department'),
          repository: widget.repository,
          departmentId: department,
          monthly: kind == ReportKind.outpatientMonthly,
          needsDepartment: widget.needsDepartment && department == null,
          onSelect: (period) {
            from.text = period.from;
            to.text = period.to;
            department = period.departmentId;
            changed();
          },
        ),
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
      if (operations != null) ...[
        const SizedBox(height: 24),
        OperationsStatisticsView(operations!),
      ],
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
        if (data!.currency != null && data!.rows.isNotEmpty)
          OutpatientStatistics(data!),
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
