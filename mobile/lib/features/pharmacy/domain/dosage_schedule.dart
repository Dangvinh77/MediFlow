const doseSessions = ['Sáng', 'Trưa', 'Chiều', 'Tối', 'Khi cần'];
const asNeededSession = 'Khi cần';

const doseTimings = [
  'Trước ăn',
  'Sau ăn',
  'Trong bữa ăn',
  'Lúc đói',
  'Trước khi ngủ',
  'Khi có triệu chứng',
  'Không phụ thuộc bữa ăn',
];

const dosePresets = <String, List<String>>{
  'Sáng - Tối': ['Sáng', 'Tối'],
  'Sáng - Trưa - Tối': ['Sáng', 'Trưa', 'Tối'],
  'Chỉ tối': ['Tối'],
  'Khi cần': [asNeededSession],
};

const doseStep = 0.5;
const maxDose = 20.0;
const maxDoseDays = 365;

/// Units dispensed per dose; a bottle or tube cannot be derived from dose × days.
const _countableUnits = {'viên', 'viên nén', 'viên nang', 'gói', 'ống', 'nang'};

class DoseRow {
  const DoseRow({
    required this.session,
    this.selected = false,
    required this.timing,
    this.dose = 1,
  });

  final String session, timing;
  final bool selected;
  final double dose;

  DoseRow copyWith({bool? selected, String? timing, double? dose}) => DoseRow(
    session: session,
    selected: selected ?? this.selected,
    timing: timing ?? this.timing,
    dose: dose ?? this.dose,
  );
}

class DosageSchedule {
  const DosageSchedule({required this.rows, this.days = ''});

  factory DosageSchedule.empty() => DosageSchedule(
    rows: [
      for (final session in doseSessions)
        DoseRow(
          session: session,
          timing: session == asNeededSession ? 'Khi có triệu chứng' : 'Sau ăn',
        ),
    ],
  );

  final List<DoseRow> rows;
  final String days;

  DosageSchedule copyWith({List<DoseRow>? rows, String? days}) =>
      DosageSchedule(rows: rows ?? this.rows, days: days ?? this.days);

  DosageSchedule withPreset(List<String> sessions) => copyWith(
    rows: [
      for (final row in rows)
        row.copyWith(selected: sessions.contains(row.session)),
    ],
  );

  int? get _parsedDays {
    final text = days.trim();
    if (!RegExp(r'^\d+$').hasMatch(text)) return null;
    final value = int.tryParse(text);
    return value != null && value >= 1 && value <= maxDoseDays ? value : null;
  }

  /// Returns the message to show, or null when the schedule can be applied.
  String? validate() {
    final selected = rows.where((row) => row.selected);
    if (selected.isEmpty) return 'Chọn ít nhất một buổi dùng.';
    if (selected.any(
      (row) =>
          row.dose <= 0 || row.dose > maxDose || (row.dose / doseStep) % 1 != 0,
    )) {
      return 'Liều mỗi buổi phải từ 1/2 đến 20, theo bước 1/2.';
    }
    if (days.trim().isNotEmpty && _parsedDays == null) {
      return 'Số ngày dùng phải từ 1 đến $maxDoseDays.';
    }
    return null;
  }

  /// Produces the read-only "Cách dùng" text stored in the line's dosage.
  String format(String unit) {
    final unitText = unit.trim().toLowerCase();
    final parts = rows
        .where((row) => row.selected)
        .map(
          (row) =>
              '${row.session} ${formatDose(row.dose)}'
              '${unitText.isEmpty ? '' : ' $unitText'} '
              '${row.timing.toLowerCase()}',
        );
    final length = _parsedDays;
    return '${parts.join('; ')}${length == null ? '' : ' ($length ngày)'}';
  }

  /// Scheduled doses × days, rounded up. As-needed doses are never counted.
  int? suggestedQuantity(String unit) {
    final length = _parsedDays;
    if (length == null ||
        !_countableUnits.contains(unit.trim().toLowerCase())) {
      return null;
    }
    final daily = rows
        .where((row) => row.selected && row.session != asNeededSession)
        .fold<double>(0, (sum, row) => sum + row.dose);
    return daily > 0 ? (daily * length).ceil() : null;
  }
}

String formatDose(double dose) {
  if (dose == 0.5) return '1/2';
  final text = dose == dose.roundToDouble()
      ? dose.toInt().toString()
      : dose.toString();
  return text.replaceAll('.', ',');
}
