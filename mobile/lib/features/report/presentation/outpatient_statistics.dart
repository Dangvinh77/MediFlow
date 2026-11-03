import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../domain/report.dart';
import '../../../core/widgets/reference_widgets.dart';

/// The accessible detail cards remain the exact-value alternative to these trends.
class OutpatientStatistics extends StatelessWidget {
  const OutpatientStatistics(this.data, {super.key});
  final ReportData data;

  @override
  Widget build(BuildContext context) => Column(
    children: [
      _Trend(data.rows, [
        'Tiền thu',
        'Hoàn tiền',
        'Thu ròng',
      ], 'Dòng tiền ngoại trú (${data.currency})'),
      _Trend(data.rows, [
        'Lượt khám hoàn tất',
        'Xét nghiệm',
        'Đơn đã cấp',
      ], 'Hoạt động ngoại trú (lượt / xét nghiệm / đơn)'),
      const Text(
        'Thu ròng là tiền thu trừ hoàn tiền. Số lượng thuốc đã cấp nằm trong chi tiết từng kỳ.',
      ),
    ],
  );
}

class _Trend extends StatelessWidget {
  const _Trend(this.rows, this.keys, this.title);
  final List<ReportRow> rows;
  final List<String> keys;
  final String title;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final colors = [scheme.primary, scheme.error, scheme.tertiary];
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(title, style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 12),
            Wrap(
              spacing: 12,
              children: [
                for (var i = 0; i < keys.length; i++)
                  Text(
                    '${i + 1}. ${keys[i]}',
                    style: TextStyle(color: colors[i]),
                  ),
              ],
            ),
            if (rows.isNotEmpty)
              Semantics(
                label: '$title. Xem số liệu chính xác trong chi tiết từng kỳ.',
                image: true,
                child: SizedBox(
                  height: 190,
                  width: double.infinity,
                  child: CustomPaint(
                    painter: _TrendPainter(
                      rows,
                      keys,
                      colors,
                      scheme.onSurface,
                    ),
                  ),
                ),
              ),
            if (rows.isNotEmpty)
              Text(
                '${rows.first.title} → ${rows.last.title}',
                style: Theme.of(context).textTheme.bodySmall,
              ),
          ],
        ),
      ),
    );
  }
}

class _TrendPainter extends CustomPainter {
  _TrendPainter(this.rows, this.keys, this.colors, this.textColor);
  final List<ReportRow> rows;
  final List<String> keys;
  final List<Color> colors;
  final Color textColor;

  @override
  void paint(Canvas canvas, Size size) {
    final values = [
      for (final row in rows)
        for (final key in keys) row.metrics[key]!.toDouble(),
    ];
    final low = math.min(0.0, values.reduce(math.min));
    final high = math.max(1.0, values.reduce(math.max));
    const left = 54.0, top = 18.0;
    final width = math.max(1.0, size.width - left - 12);
    final height = size.height - top - 24;
    double y(double v) => top + (high - v) / (high - low) * height;
    double x(int i) =>
        left + (rows.length == 1 ? width / 2 : width * i / (rows.length - 1));
    for (final value in {low, 0.0, high}) {
      canvas.drawLine(
        Offset(left, y(value)),
        Offset(left + width, y(value)),
        Paint()..color = textColor.withValues(alpha: 0.2),
      );
      final label = value.abs() >= 1000000
          ? '${(value / 1000000).toStringAsFixed(1)}tr'
          : value.abs() >= 1000
          ? '${(value / 1000).toStringAsFixed(1)}k'
          : value.toStringAsFixed(0);
      final text = TextPainter(
        text: TextSpan(
          text: label,
          style: TextStyle(color: textColor, fontSize: 10),
        ),
        textDirection: TextDirection.ltr,
      )..layout(maxWidth: 50);
      text.paint(canvas, Offset(0, y(value) - 6));
    }
    for (var series = 0; series < keys.length; series++) {
      final path = Path();
      for (var i = 0; i < rows.length; i++) {
        final point = Offset(
          x(i),
          y(rows[i].metrics[keys[series]]!.toDouble()),
        );
        if (i == 0) {
          path.moveTo(point.dx, point.dy);
        } else {
          path.lineTo(point.dx, point.dy);
        }
        // Different marker sizes preserve identity when colours are hard to distinguish.
        canvas.drawCircle(point, 2.0 + series, Paint()..color = colors[series]);
      }
      canvas.drawPath(
        path,
        Paint()
          ..color = colors[series]
          ..strokeWidth = 2
          ..style = PaintingStyle.stroke,
      );
    }
  }

  @override
  bool shouldRepaint(covariant _TrendPainter old) => true;
}

class AvailableReportPeriods extends StatefulWidget {
  const AvailableReportPeriods({
    super.key,
    required this.repository,
    required this.departmentId,
    required this.monthly,
    required this.needsDepartment,
    required this.onSelect,
  });
  final ReportRepository repository;
  final String? departmentId;
  final bool monthly, needsDepartment;
  final ValueChanged<AvailableReportPeriod> onSelect;
  @override
  State<AvailableReportPeriods> createState() => _AvailableReportPeriodsState();
}

class _AvailableReportPeriodsState extends State<AvailableReportPeriods> {
  List<AvailableReportPeriod>? items;
  bool loading = false;
  bool failed = false;
  Future<void> load() async {
    setState(() {
      loading = true;
      failed = false;
      items = null;
    });
    try {
      final result = await widget.repository.availablePeriods(
        departmentId: widget.departmentId,
      );
      if (mounted) {
        setState(
          () => items = result
              .where((item) => !widget.monthly || item.isFullYear)
              .toList(),
        );
      }
    } catch (_) {
      if (mounted) setState(() => failed = true);
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      OutlinedButton(
        onPressed: loading || widget.needsDepartment ? null : load,
        child: Text(loading ? 'Đang tìm kỳ báo cáo…' : 'Tìm kỳ có dữ liệu'),
      ),
      if (widget.needsDepartment)
        const Text('Chọn khoa được phân quyền trước khi tìm kỳ báo cáo.'),
      if (failed)
        const Text(
          'Chưa tìm được kỳ báo cáo. Kiểm tra quyền truy cập và thử lại.',
        ),
      if (items != null && items!.isEmpty)
        const Text(
          'Chưa tìm thấy kỳ phù hợp trong các đối soát gần nhất. Điều này không có nghĩa hoạt động bằng 0.',
        ),
      for (final item in items ?? <AvailableReportPeriod>[])
        Card(
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('${item.from} – ${item.to}'),
                if (item.departmentId == null)
                  const Text('Toàn viện')
                else
                  ReferenceLabel(
                    kind: ReferenceKind.department,
                    id: item.departmentId!,
                    label: 'Khoa',
                  ),
                Text('${item.currency} · ${item.zoneId}'),
                if (item.syntheticData) const Text('Dữ liệu mô phỏng'),
                TextButton(
                  onPressed: () => widget.onSelect(item),
                  child: const Text('Dùng kỳ này'),
                ),
              ],
            ),
          ),
        ),
    ],
  );
}
