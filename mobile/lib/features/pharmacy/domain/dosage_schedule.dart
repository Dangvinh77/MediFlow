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

/// Outpatient prescriptions run at most 90 days (TT 26/2025/TT-BYT).
const maxDoseDays = 90;

/// Beyond this, the drug must be for a stable chronic disease on the
/// long-prescription list.
const standardMaxDoseDays = 30;

/// Treatment length as written on Vietnamese prescriptions; one
/// "tháng thuốc" is 30 days.
enum DurationUnit {
  day('Ngày', 'ngày', 1),
  week('Tuần', 'tuần', 7),
  month('Tháng', 'tháng', 30);

  const DurationUnit(this.unit, this.label, this.days);

  final String unit, label;
  final int days;
}

/// Quick picks: label → (amount, unit).
const durationPresets = <String, (String, DurationUnit)>{
  '1 tuần': ('1', DurationUnit.week),
  '2 tuần': ('2', DurationUnit.week),
  '1 tháng': ('1', DurationUnit.month),
  '2 tháng': ('2', DurationUnit.month),
  '3 tháng': ('3', DurationUnit.month),
};

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
  const DosageSchedule({
    required this.rows,
    this.duration = '',
    this.durationUnit = DurationUnit.day,
  });

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

  /// How many [durationUnit]s the patient takes the drug; empty when unstated.
  final String duration;
  final DurationUnit durationUnit;

  DosageSchedule copyWith({
    List<DoseRow>? rows,
    String? duration,
    DurationUnit? durationUnit,
  }) => DosageSchedule(
    rows: rows ?? this.rows,
    duration: duration ?? this.duration,
    durationUnit: durationUnit ?? this.durationUnit,
  );

  DosageSchedule withPreset(List<String> sessions) => copyWith(
    rows: [
      for (final row in rows)
        row.copyWith(selected: sessions.contains(row.session)),
    ],
  );

  /// Total treatment days, or null when the duration is blank, malformed or
  /// over the outpatient limit.
  int? get totalDays {
    final text = duration.trim();
    if (!RegExp(r'^\d+$').hasMatch(text)) return null;
    final amount = int.tryParse(text);
    if (amount == null || amount < 1) return null;
    final value = amount * durationUnit.days;
    return value <= maxDoseDays ? value : null;
  }

  /// Shown, not blocking: over 30 days is only for an eligible stable
  /// chronic disease.
  String? get longPrescriptionNotice {
    final value = totalDays;
    return value != null && value > standardMaxDoseDays
        ? 'Đơn trên 30 ngày chỉ dùng cho bệnh mạn tính ổn định thuộc danh mục được kê đơn dài ngày.'
        : null;
  }

  String _durationText(int value) => durationUnit == DurationUnit.day
      ? '$value ngày'
      : '${duration.trim()} ${durationUnit.label} – $value ngày';

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
    if (duration.trim().isNotEmpty && totalDays == null) {
      return 'Thời gian dùng phải là số nguyên, tối đa 90 ngày (12 tuần hoặc 3 tháng).';
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
    final length = totalDays;
    return '${parts.join('; ')}'
        '${length == null ? '' : ' (${_durationText(length)})'}';
  }

  /// Scheduled doses × days, rounded up. As-needed doses are never counted.
  int? suggestedQuantity(String unit) {
    final length = totalDays;
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
