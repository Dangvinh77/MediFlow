import 'package:flutter/material.dart';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mediflow_loader/mediflow_loader.dart';

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
  if (outpatientEnabled) ...[
    ReportKind.operations,
    ReportKind.outpatientMonthly,
    ReportKind.outpatientDaily,
  ],
  for (final kind in ReportKind.values)
    if (kind != ReportKind.outpatientDaily &&
        kind != ReportKind.outpatientMonthly &&
        kind != ReportKind.operations)
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
    this.lockedDepartmentId,
    this.outpatientEnabled = const bool.fromEnvironment(
      'MEDIFLOW_REPORT_OUTPATIENT_UI_ENABLED',
    ),
  });

  /// Set for a department head; they read this department and no other.
  final String? lockedDepartmentId;
  final bool outpatientEnabled;
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final access = ref.watch(workspaceAccessProvider);
    final allowed =
        access != null &&
        (access.role == 'ADMIN' ||
            (access.role == 'DOCTOR' && lockedDepartmentId != null));
    if (!allowed) {
      return const Center(child: Text('Bạn không có quyền xem báo cáo.'));
    }
    return _ReportWorkspace(
      key: ValueKey((access.sessionIdentity, lockedDepartmentId)),
      repository: ref.watch(reportRepositoryProvider),
      outpatientEnabled: outpatientEnabled,
      lockedDepartmentId: lockedDepartmentId,
    );
  }
}

class _ReportWorkspace extends StatefulWidget {
  const _ReportWorkspace({
    super.key,
    required this.repository,
    required this.outpatientEnabled,
    this.lockedDepartmentId,
  });
  final ReportRepository repository;
  final bool outpatientEnabled;
  final String? lockedDepartmentId;
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
  late String? department = widget.lockedDepartmentId;
  ReportData? data;
  OperationsStatistics? operations;
  Object? error;
  bool loading = false;
  int generation = 0;
  @override
  void initState() {
    super.initState();
    if (kind == ReportKind.operations) {
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

  bool get isReconciledReport =>
      kind == ReportKind.outpatientDaily ||
      kind == ReportKind.outpatientMonthly;

  String get selectedPeriodSummary => kind == ReportKind.outpatientMonthly
      ? 'Năm ${from.text.length >= 4 ? from.text.substring(0, 4) : from.text}'
      : '${from.text} → ${to.text}';

  List<Widget> dateFilters() => [
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
  ];

  Widget departmentFilter() => widget.lockedDepartmentId != null
      ? ReferenceLabel(
          kind: ReferenceKind.department,
          id: widget.lockedDepartmentId!,
          label: 'Khoa phụ trách',
        )
      : ReferencePicker(
          kind: ReferenceKind.department,
          label: 'Khoa phòng (tất cả nếu bỏ trống)',
          value: department,
          onChanged: loading
              ? null
              : (value) {
                  department = value;
                  changed();
                },
        );

  Widget availablePeriods() => AvailableReportPeriods(
    key: ValueKey('$kind:$department'),
    repository: widget.repository,
    departmentId: department,
    monthly: kind == ReportKind.outpatientMonthly,
    onSelect: (period) {
      from.text = period.from;
      to.text = period.to;
      department = widget.lockedDepartmentId ?? period.departmentId;
      changed();
      load();
    },
  );

  String outpatientPeriodSummary(ReportRow row, String currency) {
    String value(String key) =>
        row.metrics[key] == null ? '—' : _number(row.metrics[key]!);
    return '${value('Lượt khám hoàn tất')} lượt · '
        '${value('Xét nghiệm')} xét nghiệm · '
        '${value('Đơn đã cấp')} đơn · '
        'Thu ròng: ${value('Thu ròng')} $currency';
  }

  Widget reconciledResult(BuildContext context, ReportData report) {
    final synthetic = report.note?.startsWith('Dữ liệu mô phỏng') ?? false;
    final currency = report.currency ?? '—';
    final text = Theme.of(context).textTheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  crossAxisAlignment: WrapCrossAlignment.center,
                  children: [
                    Text(report.title, style: text.titleLarge),
                    const Chip(
                      avatar: Icon(Icons.verified_outlined, size: 18),
                      label: Text('Đã đối soát'),
                    ),
                    if (synthetic)
                      const Chip(
                        avatar: Icon(Icons.science_outlined, size: 18),
                        label: Text('Dữ liệu mô phỏng'),
                      ),
                  ],
                ),
                Text('${report.rows.length} kỳ · $currency'),
                if (report.departmentId != null)
                  ReferenceLabel(
                    kind: ReferenceKind.department,
                    id: report.departmentId!,
                    label: 'Khoa',
                  ),
              ],
            ),
          ),
        ),
        if (report.note != null)
          Card(
            child: ExpansionTile(
              title: const Text('Nguồn và đối soát'),
              children: [
                Padding(
                  padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                  child: Align(
                    alignment: Alignment.centerLeft,
                    child: Text(report.note!),
                  ),
                ),
              ],
            ),
          ),
        if (report.rows.isNotEmpty)
          Card(
            child: ExpansionTile(
              title: const Text('Xu hướng đã đối soát'),
              subtitle: const Text('Mở để xem dòng tiền và hoạt động theo kỳ'),
              children: [
                Padding(
                  padding: const EdgeInsets.all(12),
                  child: OutpatientStatistics(report),
                ),
              ],
            ),
          ),
        if (report.rows.isEmpty)
          const Padding(
            padding: EdgeInsets.all(16),
            child: Text('Chưa có số liệu trong khoảng đã chọn.'),
          ),
        for (final row in report.rows)
          Card(
            margin: const EdgeInsets.symmetric(vertical: 6),
            child: ExpansionTile(
              title: Text(row.title),
              subtitle: Text(outpatientPeriodSummary(row, currency)),
              children: [
                Padding(
                  padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                  child: Column(
                    children: [
                      for (final metric in row.metrics.entries)
                        Padding(
                          padding: const EdgeInsets.symmetric(vertical: 5),
                          child: Row(
                            children: [
                              Expanded(child: Text(metric.key)),
                              Text(
                                _number(metric.value),
                                style: text.titleMedium,
                              ),
                            ],
                          ),
                        ),
                    ],
                  ),
                ),
              ],
            ),
          ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final filters = dateFilters();
    final departmentInput = departmentFilter();
    final isOutpatient = isReconciledReport;
    return ListView(
      padding: const EdgeInsets.all(20),
      children: [
        Text(
          'Thống kê hoạt động',
          style: Theme.of(context).textTheme.headlineSmall,
        ),
        const SizedBox(height: 16),
        DropdownButtonFormField<ReportKind>(
          initialValue: kind,
          isExpanded: true,
          items: [
            for (final k in availableReportKinds(
              outpatientEnabled: widget.outpatientEnabled,
            ))
              DropdownMenuItem(
                value: k,
                child: Text(
                  reportTitles[k]!,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
          ],
          onChanged: loading
              ? null
              : (k) {
                  kind = k!;
                  changed();
                },
          decoration: const InputDecoration(labelText: 'Loại báo cáo'),
        ),
        const SizedBox(height: 8),
        if (isOutpatient)
          Card(
            child: ExpansionTile(
              key: ValueKey('reconciled-filters-$kind'),
              title: const Text('Bộ lọc đối soát'),
              subtitle: Text(
                '$selectedPeriodSummary · ${widget.lockedDepartmentId != null
                    ? 'Khoa phụ trách'
                    : department == null
                    ? 'Toàn viện'
                    : 'Theo khoa đã chọn'}',
              ),
              childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
              children: [
                ...filters,
                const SizedBox(height: 12),
                departmentInput,
                const SizedBox(height: 12),
                availablePeriods(),
              ],
            ),
          )
        else ...[
          ...filters,
          const SizedBox(height: 12),
          departmentInput,
        ],
        const SizedBox(height: 12),
        FilledButton.icon(
          onPressed: loading ? null : load,
          icon: const Icon(Icons.bar_chart),
          label: const Text('Xem báo cáo'),
        ),
        if (loading)
          const Padding(
            padding: EdgeInsets.all(24),
            child: Center(child: MediFlowLoader()),
          ),
        if (error != null) RequestError(error!, onRetry: load),
        if (operations != null) ...[
          const SizedBox(height: 24),
          OperationsStatisticsView(operations!),
        ],
        if (data != null && isOutpatient) ...[
          const SizedBox(height: 12),
          reconciledResult(context, data!),
        ],
        if (data != null && !isOutpatient) ...[
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
  }

  String _number(num value) => value
      .toStringAsFixed(value % 1 == 0 ? 0 : 2)
      .replaceAllMapped(RegExp(r'\B(?=(\d{3})+(?!\d))'), (_) => '.');
}
