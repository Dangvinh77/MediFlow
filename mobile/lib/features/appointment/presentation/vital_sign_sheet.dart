import '../../../core/l10n/app_l10n.dart';

import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/widgets/request_state.dart';
import '../domain/clinic_queue.dart';
import 'clinic_queue_page.dart';

class VitalSignSheet extends ConsumerStatefulWidget {
  const VitalSignSheet({super.key, required this.registrationId, this.onSaved});
  final String registrationId;
  final VoidCallback? onSaved;

  @override
  ConsumerState<VitalSignSheet> createState() => _VitalSignSheetState();
}

/// One indicator with the device's measurable range, mirrored from Clinical's
/// VitalSignObservation. The range only catches typing mistakes; it never
/// judges whether a value is clinically abnormal.
class _Field {
  const _Field(
    this.name,
    this.label,
    this.unit,
    this.min,
    this.max, {
    this.decimals = 0,
  });
  final String name, label, unit;
  final num min, max;
  final int decimals;
  bool get decimal => decimals > 0;
  String get scale => '$min – $max';
}

const _fields = [
  _Field('pulseBpm', 'Mạch (lần/phút)', 'lần/phút', 20, 250),
  _Field('systolicMmhg', 'Huyết áp tâm thu (mmHg)', 'mmHg', 50, 300),
  _Field('diastolicMmhg', 'Huyết áp tâm trương (mmHg)', 'mmHg', 20, 200),
  _Field('temperatureC', 'Nhiệt độ (°C)', '°C', 30, 45, decimals: 1),
  _Field('respiratoryRate', 'Nhịp thở (lần/phút)', 'lần/phút', 5, 80),
  _Field('spo2Percent', 'SpO2 (%)', '%', 50, 100),
  _Field('weightKg', 'Cân nặng (kg)', 'kg', 0.5, 400, decimals: 2),
];

/// The problem with one entered value, or null. Empty is allowed: a reason covers it.
String? vitalFieldError(String name, String raw, String systolic) {
  final field = _fields.firstWhere((item) => item.name == name);
  final text = raw.trim();
  if (text.isEmpty) return null;
  if (text.contains(',')) {
    return '${field.label}: dùng dấu chấm cho phần thập phân, ví dụ 36.5.';
  }
  if (!field.decimal && !RegExp(r'^\d+$').hasMatch(text)) {
    return '${field.label}: cần nhập số nguyên.';
  }
  if (field.decimal && !RegExp(r'^\d+(\.\d+)?$').hasMatch(text)) {
    return '${field.label}: cần nhập một số.';
  }
  final fraction = text.contains('.') ? text.split('.')[1].length : 0;
  if (fraction > field.decimals) {
    return '${field.label}: tối đa ${field.decimals} chữ số thập phân.';
  }
  final value = num.parse(text);
  if (value < field.min || value > field.max) {
    return '${field.label}: ngoài thang đo ${field.scale} ${field.unit}.';
  }
  final upper = int.tryParse(systolic.trim());
  if (name == 'diastolicMmhg' && upper != null && value >= upper) {
    return 'Huyết áp tâm trương phải nhỏ hơn tâm thu.';
  }
  return null;
}

class _VitalSignSheetState extends ConsumerState<VitalSignSheet> {
  final _controllers = {
    for (final field in _fields) field.name: TextEditingController(),
  };
  final _reason = TextEditingController();
  String? _issue;
  Object? _error;
  VitalSignInput? _pending;
  bool _busy = false;
  // Errors show under each field once the nurse tried to save, or while typing after that.
  bool _checked = false;

  String get _scope => 'vitals:${widget.registrationId}';

  @override
  void dispose() {
    for (final controller in _controllers.values) {
      controller.dispose();
    }
    _reason.dispose();
    super.dispose();
  }

  /// Returns the parsed form, or stores why it cannot be sent.
  VitalSignInput? _read() {
    final values = <String, num?>{};
    for (final field in _fields) {
      final raw = _controllers[field.name]!.text.trim();
      if (raw.isEmpty) {
        values[field.name] = null;
        continue;
      }
      final problem = vitalFieldError(
        field.name,
        raw,
        _controllers['systolicMmhg']!.text,
      );
      if (problem != null) {
        // The detail is shown under the field itself.
        _issue = 'Kiểm tra lại các chỉ số được đánh dấu đỏ.';
        _checked = true;
        return null;
      }
      values[field.name] = field.decimal ? double.parse(raw) : int.parse(raw);
    }
    final reason = _reason.text.trim();
    final input = VitalSignInput(
      pulseBpm: values['pulseBpm'] as int?,
      systolicMmhg: values['systolicMmhg'] as int?,
      diastolicMmhg: values['diastolicMmhg'] as int?,
      temperatureC: values['temperatureC'] as double?,
      respiratoryRate: values['respiratoryRate'] as int?,
      spo2Percent: values['spo2Percent'] as int?,
      weightKg: values['weightKg'] as double?,
      notMeasuredReason: reason.isEmpty ? null : reason,
    );
    _issue = input.validate();
    return _issue == null ? input : null;
  }

  Future<void> _submit() async {
    if (_busy) return;
    final input = _pending ?? _read();
    if (input == null) {
      setState(() => _error = null);
      return;
    }
    setState(() {
      _busy = true;
      _issue = null;
      _error = null;
    });
    final keys = ref.read(clinicCommandKeysProvider);
    try {
      await ref
          .read(clinicQueueRepositoryProvider)
          .recordVitals(
            widget.registrationId,
            input,
            idempotencyKey: keys.keyFor(_scope, jsonEncode(input.toJson())),
          );
      keys.settle(_scope);
      if (!mounted) return;
      setState(() => _pending = null);
      widget.onSaved?.call();
    } catch (error) {
      final unknown = clinicOutcomeUnknown(error);
      if (!unknown) keys.settle(_scope);
      if (mounted) {
        setState(() {
          _error = friendlyClinicError(error);
          _pending = unknown ? input : null;
        });
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final locked = _busy || _pending != null;
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          LText(
            'Ghi sinh hiệu',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: 4),
          const LText(
            'Thang đo là giới hạn đo được của thiết bị, dùng để bắt lỗi nhập liệu. Dùng dấu chấm cho số thập phân.',
          ),
          for (final field in _fields) ...[
            const SizedBox(height: 8),
            TextField(
              key: ValueKey('vital-${field.name}'),
              controller: _controllers[field.name],
              enabled: !locked,
              onChanged: (_) {
                if (_checked) setState(() {});
              },
              keyboardType: TextInputType.numberWithOptions(
                decimal: field.decimal,
              ),
              decoration: InputDecoration(
                labelText: AppL10n.text(context, field.label),
                suffixText: AppL10n.text(context, field.unit),
                helperText:
                    '${AppL10n.text(context, 'Thang đo')}: ${field.scale} · ${AppL10n.text(context, field.decimal ? 'Tối đa ${field.decimals} chữ số thập phân' : 'Số nguyên')}',
                errorText: !_checked
                    ? null
                    : AppL10n.text(
                        context,
                        vitalFieldError(
                          field.name,
                          _controllers[field.name]!.text,
                          _controllers['systolicMmhg']!.text,
                        ),
                      ),
                border: const OutlineInputBorder(),
              ),
            ),
          ],
          const SizedBox(height: 8),
          TextField(
            controller: _reason,
            enabled: !locked,
            maxLength: 500,
            maxLines: 2,
            decoration: InputDecoration(
              labelText: AppL10n.text(context, 'Lý do chưa đo đủ'),
              border: OutlineInputBorder(),
            ),
          ),
          if (_issue != null)
            LText(
              _issue!,
              style: TextStyle(color: Theme.of(context).colorScheme.error),
            ),
          if (_error != null) RequestError(_error!),
          if (_pending != null)
            const LText(
              'Chưa xác định sinh hiệu đã được lưu hay chưa. Gửi lại đúng yêu cầu để tránh ghi trùng.',
            ),
          const SizedBox(height: 8),
          FilledButton(
            onPressed: _busy ? null : _submit,
            child: LText(
              _pending != null ? 'Gửi lại cùng yêu cầu' : 'Lưu sinh hiệu',
            ),
          ),
        ],
      ),
    );
  }
}

class VitalSignHistorySheet extends ConsumerStatefulWidget {
  const VitalSignHistorySheet({super.key, required this.registrationId});
  final String registrationId;

  @override
  ConsumerState<VitalSignHistorySheet> createState() =>
      _VitalSignHistorySheetState();
}

class _VitalSignHistorySheetState extends ConsumerState<VitalSignHistorySheet> {
  late Future<List<VitalSignRecord>> _rows = _load();

  Future<List<VitalSignRecord>> _load() =>
      ref.read(clinicQueueRepositoryProvider).listVitals(widget.registrationId);

  static String _measure(num? value, String unit) =>
      value == null ? 'Chưa đo' : '$value $unit';

  String _summary(VitalSignInput row) {
    final systolic = row.systolicMmhg, diastolic = row.diastolicMmhg;
    final pressure = systolic == null || diastolic == null
        ? 'Chưa đo'
        : '$systolic/$diastolic mmHg';
    return 'Mạch ${_measure(row.pulseBpm, 'lần/phút')} · Huyết áp $pressure · '
        'Nhiệt độ ${_measure(row.temperatureC, '°C')} · '
        'Nhịp thở ${_measure(row.respiratoryRate, 'lần/phút')} · '
        'SpO2 ${_measure(row.spo2Percent, '%')} · '
        'Cân nặng ${_measure(row.weightKg, 'kg')}';
  }

  @override
  Widget build(BuildContext context) => FutureBuilder<List<VitalSignRecord>>(
    future: _rows,
    builder: (context, snapshot) {
      if (snapshot.hasError) {
        return RequestError(
          snapshot.error!,
          onRetry: () => setState(() => _rows = _load()),
        );
      }
      final rows = snapshot.data;
      if (rows == null) {
        return const Padding(
          padding: EdgeInsets.all(24),
          child: Center(child: CircularProgressIndicator()),
        );
      }
      return SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            LText(
              'Sinh hiệu đã ghi',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 8),
            if (rows.isEmpty)
              const LText(
                'Chưa có sinh hiệu cho lượt khám này. Vẫn có thể bắt đầu khám và đo sau.',
              ),
            for (final row in rows)
              Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    LText(row.observedAt),
                    LText(_summary(row.input)),
                    if (row.input.notMeasuredReason != null)
                      LText('Lý do chưa đo đủ: ${row.input.notMeasuredReason}'),
                  ],
                ),
              ),
          ],
        ),
      );
    },
  );
}
