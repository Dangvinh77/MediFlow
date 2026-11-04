import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../../../core/widgets/reference_widgets.dart';
import '../domain/report.dart';

/// Provisional revenue by service line and visits by department, as charts.
class OperationsStatisticsView extends StatelessWidget {
  const OperationsStatisticsView(this.data, {super.key});
  final OperationsStatistics data;

  static List<(String, Color, double Function(OperationsFigures))> series(
    ColorScheme scheme,
    OperationsFigures totals,
  ) => [
    ('Khám bệnh', scheme.primary, (f) => f.examRevenue),
    ('Xét nghiệm', Colors.green.shade600, (f) => f.labRevenue),
    ('Thuốc', Colors.orange.shade700, (f) => f.prescriptionRevenue),
    if (totals.unclassifiedRevenue > 0)
      ('Chưa phân loại', scheme.outline, (f) => f.unclassifiedRevenue),
  ];

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final lines = series(scheme, data.totals);
    final text = Theme.of(context).textTheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            color: Colors.amber.withValues(alpha: 0.12),
            borderRadius: BorderRadius.circular(8),
          ),
          child: Text(
            'Số liệu tạm tính ${data.from} → ${data.to}, chưa đối soát. '
            'Doanh thu là tiền đã thu, chưa trừ hoàn tiền. '
            'Chỉ gồm dữ liệu đã đồng bộ về báo cáo, có thể còn thiếu sự kiện. '
            'Số 0 nghĩa là chưa ghi nhận dữ liệu, không khẳng định không có hoạt động.',
          ),
        ),
        const SizedBox(height: 12),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            _Total('Lượt khám hoàn tất', '${data.totals.completedVisits}'),
            for (final (label, _, value) in lines)
              _Total(label, '${money(value(data.totals))} ${data.currency}'),
            _Total(
              'Tiền đã hoàn',
              '${money(data.totals.refunds)} ${data.currency}',
            ),
          ],
        ),
        const SizedBox(height: 12),
        _ChartCard(
          title: 'Doanh thu theo ngày và loại dịch vụ (${data.currency})',
          legend: [for (final (label, color, _) in lines) (label, color)],
          child: CustomPaint(
            painter: _StackedPainter(data.days, [
              for (final (_, color, value) in lines) (color, value),
            ], scheme.onSurface),
          ),
        ),
        _ChartCard(
          title: 'Số lượt khám hoàn tất theo ngày',
          legend: [('Lượt khám', scheme.primary)],
          child: CustomPaint(
            painter: _StackedPainter(
              data.days,
              [(scheme.primary, (f) => f.completedVisits.toDouble())],
              scheme.onSurface,
              integerAxis: true,
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
                  'Theo khoa: lượt khám và doanh thu',
                  style: text.titleMedium,
                ),
                const SizedBox(height: 8),
                if (data.departments.isEmpty)
                  const Text(
                    'Chưa có lượt khám hoặc khoản thu nào trong phạm vi này.',
                  ),
                for (final row in data.departments)
                  _DepartmentRow(
                    row: row,
                    lines: lines,
                    currency: data.currency,
                    maxVisits: data.departments
                        .map((d) => d.completedVisits)
                        .fold(1, math.max),
                    maxRevenue: data.departments
                        .map((d) => d.revenue)
                        .fold(1.0, math.max),
                  ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

String money(double value) {
  final parts = value.toStringAsFixed(2).split('.');
  final whole = parts[0].replaceAllMapped(
    RegExp(r'\B(?=(\d{3})+(?!\d))'),
    (_) => '.',
  );
  final fraction = parts[1].replaceFirst(RegExp(r'0+$'), '');
  return fraction.isEmpty ? whole : '$whole,$fraction';
}

class _Total extends StatelessWidget {
  const _Total(this.label, this.value);
  final String label, value;
  @override
  Widget build(BuildContext context) => Container(
    constraints: const BoxConstraints(minWidth: 150),
    padding: const EdgeInsets.all(12),
    decoration: BoxDecoration(
      border: Border.all(color: Theme.of(context).colorScheme.outlineVariant),
      borderRadius: BorderRadius.circular(8),
    ),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: Theme.of(context).textTheme.bodySmall),
        Text(value, style: Theme.of(context).textTheme.titleMedium),
      ],
    ),
  );
}

class _ChartCard extends StatelessWidget {
  const _ChartCard({
    required this.title,
    required this.legend,
    required this.child,
  });
  final String title;
  final List<(String, Color)> legend;
  final Widget child;
  @override
  Widget build(BuildContext context) => Card(
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          Wrap(
            spacing: 12,
            children: [
              for (final (label, color) in legend)
                Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Container(width: 10, height: 10, color: color),
                    const SizedBox(width: 4),
                    Text(label),
                  ],
                ),
            ],
          ),
          Semantics(
            label: '$title. Tổng theo khoa ở bên dưới.',
            image: true,
            child: SizedBox(height: 180, width: double.infinity, child: child),
          ),
        ],
      ),
    ),
  );
}

class _StackedPainter extends CustomPainter {
  _StackedPainter(
    this.days,
    this.parts,
    this.textColor, {
    this.integerAxis = false,
  });
  final List<OperationsFigures> days;
  final List<(Color, double Function(OperationsFigures))> parts;
  final Color textColor;
  final bool integerAxis;

  @override
  void paint(Canvas canvas, Size size) {
    if (days.isEmpty) return;
    final totals = [
      for (final day in days) parts.fold(0.0, (sum, p) => sum + p.$2(day)),
    ];
    final high = math.max(1.0, totals.reduce(math.max));
    const left = 48.0, top = 12.0, bottomPad = 22.0;
    final width = math.max(1.0, size.width - left - 4);
    final height = size.height - top - bottomPad;
    final step = width / days.length;
    double y(double v) => top + (1 - v / high) * height;
    final grid = Paint()..color = textColor.withValues(alpha: 0.2);
    final ticks = integerAxis
        ? {0.0, (high / 2).roundToDouble(), high}
        : {0.0, high / 2, high};
    for (final value in ticks) {
      canvas.drawLine(
        Offset(left, y(value)),
        Offset(left + width, y(value)),
        grid,
      );
      _label(canvas, _short(value), Offset(0, y(value) - 7));
    }
    for (var i = 0; i < days.length; i++) {
      var base = 0.0;
      for (final (color, value) in parts) {
        final v = value(days[i]);
        if (v <= 0) continue;
        canvas.drawRect(
          Rect.fromLTRB(
            left + i * step + step * .15,
            y(base + v),
            left + i * step + step * .85,
            y(base),
          ),
          Paint()..color = color,
        );
        base += v;
      }
    }
    final stride = math.max(1, (days.length / 5).ceil());
    for (var i = 0; i < days.length; i += stride) {
      final label = days[i].label;
      _label(
        canvas,
        label.length >= 10
            ? '${label.substring(8, 10)}/${label.substring(5, 7)}'
            : label,
        Offset(left + i * step, size.height - 16),
      );
    }
  }

  void _label(Canvas canvas, String text, Offset at) {
    final painter = TextPainter(
      text: TextSpan(
        text: text,
        style: TextStyle(color: textColor, fontSize: 10),
      ),
      textDirection: TextDirection.ltr,
    )..layout();
    painter.paint(canvas, at);
  }

  static String _short(double v) => v >= 1000000
      ? '${(v / 1000000).toStringAsFixed(1)}tr'
      : v >= 1000
      ? '${(v / 1000).toStringAsFixed(0)}k'
      : v.toStringAsFixed(0);

  @override
  bool shouldRepaint(covariant _StackedPainter old) =>
      old.days != days ||
      old.parts != parts ||
      old.integerAxis != integerAxis ||
      old.textColor != textColor;
}

class _DepartmentRow extends StatelessWidget {
  const _DepartmentRow({
    required this.row,
    required this.lines,
    required this.currency,
    required this.maxVisits,
    required this.maxRevenue,
  });
  final OperationsFigures row;
  final List<(String, Color, double Function(OperationsFigures))> lines;
  final String currency;
  final int maxVisits;
  final double maxRevenue;

  Widget _bar(List<(Color, double)> parts, double max) {
    final used = parts.fold(0.0, (sum, p) => sum + p.$2);
    return SizedBox(
      height: 12,
      child: Row(
        children: [
          for (final (color, value) in parts)
            if (value > 0)
              Expanded(
                flex: math.max(1, (value / max * 1000).round()),
                child: Container(color: color),
              ),
          if (max - used > 0)
            Expanded(
              flex: math.max(1, ((max - used) / max * 1000).round()),
              child: const SizedBox(),
            ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          ReferenceLabel(
            kind: ReferenceKind.department,
            id: row.label,
            label: 'Khoa',
          ),
          Text('${row.completedVisits} lượt · ${money(row.revenue)} $currency'),
          const SizedBox(height: 4),
          _bar([
            (scheme.onSurface, row.completedVisits.toDouble()),
          ], maxVisits.toDouble()),
          const SizedBox(height: 4),
          _bar([
            for (final (_, color, value) in lines) (color, value(row)),
          ], maxRevenue),
          Wrap(
            spacing: 12,
            children: [
              for (final (label, _, value) in lines)
                Text('$label: ${money(value(row))}'),
            ],
          ),
        ],
      ),
    );
  }
}
