import '../../../core/l10n/app_l10n.dart';

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
  late final duration = TextEditingController(text: widget.initial.duration);
  String? error;

  @override
  void dispose() {
    duration.dispose();
    super.dispose();
  }

  void _applyDurationPreset((String, DurationUnit) preset) {
    duration.text = preset.$1;
    _update(schedule.copyWith(duration: preset.$1, durationUnit: preset.$2));
  }

  Widget _durationSection(ThemeData theme, int? suggestion, bool overStock) {
    final total = schedule.totalDays;
    final notice = schedule.longPrescriptionNotice;
    final hints = [
      if (total != null && schedule.durationUnit != DurationUnit.day)
        AppL10n.text(context, '= $total ngày'),
      if (suggestion != null)
        AppL10n.text(
          context,
          'Gợi ý số lượng: $suggestion ${widget.unit}'
          '${overStock ? ' (vượt tồn ${widget.stock})' : ''}',
        ),
    ];
    return InputDecorator(
      decoration: InputDecoration(
        labelText: AppL10n.text(context, 'Thời gian dùng'),
        border: const OutlineInputBorder(),
        isDense: true,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Wrap(
            spacing: 8,
            runSpacing: 4,
            children: [
              for (final preset in durationPresets.entries)
                ChoiceChip(
                  label: LText(preset.key),
                  selected:
                      schedule.duration == preset.value.$1 &&
                      schedule.durationUnit == preset.value.$2,
                  onSelected: (_) => _applyDurationPreset(preset.value),
                ),
            ],
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              SizedBox(
                width: 96,
                child: TextField(
                  key: const ValueKey('dosage-duration'),
                  controller: duration,
                  keyboardType: TextInputType.number,
                  decoration: InputDecoration(
                    labelText: AppL10n.text(context, 'Số lượng thời gian dùng'),
                    isDense: true,
                  ),
                  onChanged: (value) =>
                      _update(schedule.copyWith(duration: value)),
                ),
              ),
              const SizedBox(width: 12),
              DropdownButton<DurationUnit>(
                key: const ValueKey('dosage-duration-unit'),
                value: schedule.durationUnit,
                items: [
                  for (final unit in DurationUnit.values)
                    DropdownMenuItem(value: unit, child: LText(unit.label)),
                ],
                onChanged: (value) =>
                    _update(schedule.copyWith(durationUnit: value)),
              ),
            ],
          ),
          if (hints.isNotEmpty) ...[
            const SizedBox(height: 4),
            Text(
              hints.join(' · '),
              style: theme.textTheme.bodySmall?.copyWith(
                color: overStock
                    ? theme.colorScheme.error
                    : theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ],
          if (notice != null) ...[
            const SizedBox(height: 4),
            LText(
              notice,
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.colorScheme.tertiary,
              ),
            ),
          ],
        ],
      ),
    );
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
    child: LText(text, style: Theme.of(context).textTheme.labelSmall),
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
              Flexible(child: LText(row.session)),
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
                      child: LText(timing, overflow: TextOverflow.ellipsis),
                    ),
                ],
                onChanged: (value) =>
                    _updateRow(row, row.copyWith(timing: value)),
              )
            : const LText('—'),
      ),
      Expanded(
        flex: 4,
        child: row.selected
            ? Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  IconButton(
                    tooltip: AppL10n.text(
                      context,
                      'Giảm liều buổi ${row.session}',
                    ),
                    visualDensity: VisualDensity.compact,
                    icon: const Icon(Icons.remove),
                    onPressed: row.dose <= doseStep
                        ? null
                        : () => _updateRow(
                            row,
                            row.copyWith(dose: row.dose - doseStep),
                          ),
                  ),
                  LText(formatDose(row.dose)),
                  IconButton(
                    tooltip: AppL10n.text(
                      context,
                      'Tăng liều buổi ${row.session}',
                    ),
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
            : const Align(alignment: Alignment.centerRight, child: LText('—')),
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
            LText(
              'Cách dùng: ${widget.drugName}',
              style: theme.textTheme.titleMedium,
            ),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              children: [
                for (final preset in dosePresets.entries)
                  ActionChip(
                    label: LText(preset.key),
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
            _durationSection(theme, suggestion, overStock),
            const SizedBox(height: 8),
            LText(
              'Cách dùng sẽ ghi vào toa',
              style: theme.textTheme.labelSmall,
            ),
            LText(valid ? schedule.format(widget.unit) : '—'),
            if (error != null)
              LText(error!, style: TextStyle(color: theme.colorScheme.error)),
            const SizedBox(height: 8),
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                TextButton(
                  onPressed: () => Navigator.of(context).pop(),
                  child: const LText('Hủy'),
                ),
                const SizedBox(width: 8),
                FilledButton(onPressed: _apply, child: const LText('Áp dụng')),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
