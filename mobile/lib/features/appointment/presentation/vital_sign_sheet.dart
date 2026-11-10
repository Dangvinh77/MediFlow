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

class _Field {
  const _Field(this.name, this.label, {this.decimal = false});
  final String name, label;
  final bool decimal;
}

const _fields = [
  _Field('pulseBpm', 'Mạch (lần/phút)'),
  _Field('systolicMmhg', 'Huyết áp tâm thu (mmHg)'),
  _Field('diastolicMmhg', 'Huyết áp tâm trương (mmHg)'),
  _Field('temperatureC', 'Nhiệt độ (°C)', decimal: true),
  _Field('respiratoryRate', 'Nhịp thở (lần/phút)'),
  _Field('spo2Percent', 'SpO2 (%)'),
  _Field('weightKg', 'Cân nặng (kg)', decimal: true),
];

class _VitalSignSheetState extends ConsumerState<VitalSignSheet> {
  final _controllers = {
    for (final field in _fields) field.name: TextEditingController(),
  };
  final _reason = TextEditingController();
  String? _issue;
  Object? _error;
  VitalSignInput? _pending;
  bool _busy = false;

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
      if (raw.contains(',')) {
        _issue =
            '${field.label}: dùng dấu chấm cho phần thập phân, ví dụ 36.5.';
        return null;
      }
      final pattern = field.decimal
          ? RegExp(r'^\d+(\.\d+)?$')
          : RegExp(r'^\d+$');
      if (!pattern.hasMatch(raw)) {
        _issue =
            '${field.label}: ${field.decimal ? 'cần nhập một số.' : 'cần nhập số nguyên.'}';
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
          Text('Ghi sinh hiệu', style: Theme.of(context).textTheme.titleMedium),
          for (final field in _fields) ...[
            const SizedBox(height: 8),
            TextField(
              controller: _controllers[field.name],
              enabled: !locked,
              keyboardType: TextInputType.numberWithOptions(
                decimal: field.decimal,
              ),
              decoration: InputDecoration(
                labelText: field.label,
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
            decoration: const InputDecoration(
              labelText: 'Lý do chưa đo đủ',
              border: OutlineInputBorder(),
            ),
          ),
          if (_issue != null)
            Text(
              _issue!,
              style: TextStyle(color: Theme.of(context).colorScheme.error),
            ),
          if (_error != null) RequestError(_error!),
          if (_pending != null)
            const Text(
              'Chưa xác định sinh hiệu đã được lưu hay chưa. Gửi lại đúng yêu cầu để tránh ghi trùng.',
            ),
          const SizedBox(height: 8),
          FilledButton(
            onPressed: _busy ? null : _submit,
            child: Text(
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
            Text(
              'Sinh hiệu đã ghi',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 8),
            if (rows.isEmpty)
              const Text(
                'Chưa có sinh hiệu cho lượt khám này. Vẫn có thể bắt đầu khám và đo sau.',
              ),
            for (final row in rows)
              Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(row.observedAt),
                    Text(_summary(row.input)),
                    if (row.input.notMeasuredReason != null)
                      Text('Lý do chưa đo đủ: ${row.input.notMeasuredReason}'),
                  ],
                ),
              ),
          ],
        ),
      );
    },
  );
}
