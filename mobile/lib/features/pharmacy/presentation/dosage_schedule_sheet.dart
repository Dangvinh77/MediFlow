import 'package:flutter/material.dart';

import '../domain/dosage_schedule.dart';

/// Returns the chosen schedule, or null when the doctor dismisses the sheet.
Future<DosageSchedule?> showDosageScheduleSheet(
  BuildContext context, {
  required String drugName,
  required String unit,
  required int stock,
  DosageSchedule? initial,
}) => showModalBottomSheet<DosageSchedule>(
  context: context,
  isScrollControlled: true,
  useSafeArea: true,
  builder: (_) => _DosageScheduleSheet(
    drugName: drugName,
    unit: unit,
    stock: stock,
    initial: initial ?? DosageSchedule.empty(),
  ),
);

class _DosageScheduleSheet extends StatefulWidget {
  const _DosageScheduleSheet({
    required this.drugName,
    required this.unit,
    required this.stock,
    required this.initial,
  });

  final String drugName, unit;
  final int stock;
  final DosageSchedule initial;

  @override
  State<_DosageScheduleSheet> createState() => _DosageScheduleSheetState();
}

class _DosageScheduleSheetState extends State<_DosageScheduleSheet> {
  late DosageSchedule schedule = widget.initial;
  late final days = TextEditingController(text: widget.initial.days);
  String? error;

  @override
  void dispose() {
    days.dispose();
    super.dispose();
  }

  void _update(DosageSchedule next) => setState(() {
    schedule = next;
    error = null;
  });

  void _updateRow(DoseRow row, DoseRow next) => _update(
    schedule.copyWith(
      rows: [for (final item in schedule.rows) item == row ? next : item],
    ),
  );

  void _apply() {
    final problem = schedule.validate();
    if (problem != null) {
      setState(() => error = problem);
      return;
    }
    Navigator.of(context).pop(schedule);
  }

  Widget _header(String text, {int flex = 1}) => Expanded(
    flex: flex,
    child: Text(text, style: Theme.of(context).textTheme.labelSmall),
  );

  Widget _row(DoseRow row) => Row(
    children: [
      Expanded(
        flex: 3,
        child: InkWell(
          onTap: () => _updateRow(row, row.copyWith(selected: !row.selected)),
          child: Row(
            children: [
              Checkbox(
                value: row.selected,
                visualDensity: VisualDensity.compact,
                onChanged: (value) =>
                    _updateRow(row, row.copyWith(selected: value == true)),
              ),
              Flexible(child: Text(row.session)),
            ],
          ),
        ),
      ),
      Expanded(
        flex: 4,
        child: row.selected
            ? DropdownButton<String>(
                key: ValueKey('dose-timing-${row.session}'),
                isExpanded: true,
                isDense: true,
                value: row.timing,
                items: [
                  for (final timing in doseTimings)
                    DropdownMenuItem(
                      value: timing,
                      child: Text(timing, overflow: TextOverflow.ellipsis),
                    ),
                ],
                onChanged: (value) =>
                    _updateRow(row, row.copyWith(timing: value)),
              )
            : const Text('—'),
      ),
      Expanded(
        flex: 4,
        child: row.selected
            ? Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  IconButton(
                    tooltip: 'Giảm liều buổi ${row.session}',
                    visualDensity: VisualDensity.compact,
                    icon: const Icon(Icons.remove),
                    onPressed: row.dose <= doseStep
                        ? null
                        : () => _updateRow(
                            row,
                            row.copyWith(dose: row.dose - doseStep),
                          ),
                  ),
                  Text(formatDose(row.dose)),
                  IconButton(
                    tooltip: 'Tăng liều buổi ${row.session}',
                    visualDensity: VisualDensity.compact,
                    icon: const Icon(Icons.add),
                    onPressed: row.dose >= maxDose
                        ? null
                        : () => _updateRow(
                            row,
                            row.copyWith(dose: row.dose + doseStep),
                          ),
                  ),
                ],
              )
            : const Align(alignment: Alignment.centerRight, child: Text('—')),
      ),
    ],
  );

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final valid = schedule.validate() == null;
    final suggestion = schedule.suggestedQuantity(widget.unit);
    final overStock = suggestion != null && suggestion > widget.stock;
    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(context).bottom),
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              'Cách dùng: ${widget.drugName}',
              style: theme.textTheme.titleMedium,
            ),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              children: [
                for (final preset in dosePresets.entries)
                  ActionChip(
                    label: Text(preset.key),
                    onPressed: () => _update(schedule.withPreset(preset.value)),
                  ),
              ],
            ),
            const SizedBox(height: 8),
            DecoratedBox(
              decoration: BoxDecoration(
                border: Border.all(color: theme.dividerColor),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 6),
                child: Column(
                  children: [
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 8),
                      child: Row(
                        children: [
                          _header('Buổi', flex: 3),
                          _header('Thời điểm dùng', flex: 4),
                          _header('Liều lượng', flex: 4),
                        ],
                      ),
                    ),
                    for (final row in schedule.rows) ...[
                      const Divider(height: 1),
                      _row(row),
                    ],
                  ],
                ),
              ),
            ),
            const SizedBox(height: 8),
            TextField(
              controller: days,
              keyboardType: TextInputType.number,
              decoration: InputDecoration(
                labelText: 'Số ngày dùng',
                isDense: true,
                helperText: suggestion == null
                    ? null
                    : 'Gợi ý số lượng: $suggestion ${widget.unit}'
                          '${overStock ? ' (vượt tồn ${widget.stock})' : ''}',
              ),
              onChanged: (value) => _update(schedule.copyWith(days: value)),
            ),
            const SizedBox(height: 8),
            Text('Cách dùng sẽ ghi vào toa', style: theme.textTheme.labelSmall),
            Text(valid ? schedule.format(widget.unit) : '—'),
            if (error != null)
              Text(error!, style: TextStyle(color: theme.colorScheme.error)),
            const SizedBox(height: 8),
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                TextButton(
                  onPressed: () => Navigator.of(context).pop(),
                  child: const Text('Hủy'),
                ),
                const SizedBox(width: 8),
                FilledButton(onPressed: _apply, child: const Text('Áp dụng')),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
