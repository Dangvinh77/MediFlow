import '../../../core/l10n/app_l10n.dart';

import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/widgets/reference_widgets.dart';
import '../../../core/widgets/request_state.dart';
import '../domain/clinic_queue.dart';
import '../domain/outpatient_schedule.dart';
import '../domain/reception.dart';
import 'clinic_queue_page.dart';
import 'outpatient_schedule_pages.dart';
import 'reception_page.dart';

class RescheduleSheet extends ConsumerStatefulWidget {
  const RescheduleSheet({
    super.key,
    required this.registrationId,
    required this.currentSessionId,
    this.onDone,
  });
  final String registrationId, currentSessionId;
  final VoidCallback? onDone;

  @override
  ConsumerState<RescheduleSheet> createState() => _RescheduleSheetState();
}

typedef _RescheduleOptions = (
  OutpatientRegistration registration,
  List<ReceptionSession> sessions,
);

class _RescheduleSheetState extends ConsumerState<RescheduleSheet> {
  late Future<_RescheduleOptions> _options = _load();
  final _reason = TextEditingController();
  ReceptionSession? _target;
  bool _confirmed = false, _busy = false;
  String? _issue;
  Object? _error;
  RescheduleInput? _pending;

  String get _scope => 'reschedule:${widget.registrationId}';

  Future<_RescheduleOptions> _load() async {
    final registration = await ref
        .read(outpatientScheduleRepositoryProvider)
        .registration(widget.registrationId);
    final today = DateTime.now().toUtc().add(const Duration(hours: 7));
    final page = await ref
        .read(receptionRepositoryProvider)
        .sessions(
          departmentId: registration.departmentId,
          fromDate: vietnamDate(),
          toDate: today
              .add(const Duration(days: 30))
              .toIso8601String()
              .substring(0, 10),
        );
    return (
      registration,
      page.items
          .where(
            (item) => item.canRegister && item.id != widget.currentSessionId,
          )
          .toList(),
    );
  }

  @override
  void dispose() {
    _reason.dispose();
    super.dispose();
  }

  Future<void> _submit(OutpatientRegistration registration) async {
    if (_busy) return;
    final input =
        _pending ??
        RescheduleInput(
          expectedRevision: registration.revision,
          targetSessionId: _target?.id ?? '',
          reason: _reason.text,
          patientConfirmed: _confirmed,
        );
    final issue = input.validate();
    setState(() {
      _issue = issue;
      _error = null;
    });
    if (issue != null) return;
    setState(() => _busy = true);
    final keys = ref.read(clinicCommandKeysProvider);
    try {
      await ref
          .read(clinicQueueRepositoryProvider)
          .reschedule(
            widget.registrationId,
            input,
            idempotencyKey: keys.keyFor(_scope, jsonEncode(input.toJson())),
          );
      keys.settle(_scope);
      if (!mounted) return;
      setState(() => _pending = null);
      widget.onDone?.call();
    } catch (error) {
      final unknown = clinicOutcomeUnknown(error);
      if (!unknown) keys.settle(_scope);
      if (mounted) {
        setState(() {
          _error = error;
          _pending = unknown ? input : null;
        });
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  static String _clock(String value) =>
      value.length > 5 ? value.substring(0, 5) : value;

  @override
  Widget build(BuildContext context) => FutureBuilder<_RescheduleOptions>(
    future: _options,
    builder: (context, snapshot) {
      if (snapshot.hasError) {
        return RequestError(
          snapshot.error!,
          onRetry: () => setState(() => _options = _load()),
        );
      }
      final options = snapshot.data;
      if (options == null) {
        return const Padding(
          padding: EdgeInsets.all(24),
          child: Center(child: CircularProgressIndicator()),
        );
      }
      final locked = _busy || _pending != null;
      return SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            LText('Đổi lịch', style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 8),
            const LText('Buổi khám mới'),
            if (options.$2.isEmpty)
              const LText('Không có buổi nào còn chỗ trong 30 ngày tới.'),
            for (final item in options.$2)
              ListTile(
                contentPadding: EdgeInsets.zero,
                enabled: !locked,
                selected: item.id == _target?.id,
                trailing: item.id == _target?.id
                    ? const Icon(Icons.check)
                    : null,
                title: LText(
                  '${item.date} · ${_clock(item.startTime)}–${_clock(item.endTime)} · Còn ${item.availableCount}/${item.capacity} chỗ',
                ),
                subtitle: ReferenceLabel(
                  kind: ReferenceKind.doctor,
                  id: item.doctorId,
                  label: 'Bác sĩ',
                ),
                onTap: () => setState(() => _target = item),
              ),
            const SizedBox(height: 8),
            TextField(
              controller: _reason,
              enabled: !locked,
              maxLength: 500,
              maxLines: 2,
              decoration: InputDecoration(
                labelText: AppL10n.text(context, 'Lý do đổi lịch'),
                border: OutlineInputBorder(),
              ),
            ),
            CheckboxListTile(
              contentPadding: EdgeInsets.zero,
              controlAffinity: ListTileControlAffinity.leading,
              value: _confirmed,
              title: const LText('Bệnh nhân đã đồng ý với lịch mới.'),
              onChanged: locked
                  ? null
                  : (value) => setState(() => _confirmed = value ?? false),
            ),
            if (_issue != null)
              LText(
                _issue!,
                style: TextStyle(color: Theme.of(context).colorScheme.error),
              ),
            if (_error != null) RequestError(_error!),
            if (_pending != null)
              const LText(
                'Chưa xác định lịch đã được đổi hay chưa. Gửi lại đúng yêu cầu để tránh xử lý trùng.',
              ),
            const SizedBox(height: 8),
            FilledButton(
              onPressed: _busy ? null : () => _submit(options.$1),
              child: LText(
                _pending != null ? 'Gửi lại cùng yêu cầu' : 'Xác nhận đổi lịch',
              ),
            ),
          ],
        ),
      );
    },
  );
}

class ScheduleImpactSheet extends StatelessWidget {
  const ScheduleImpactSheet({
    super.key,
    required this.impact,
    this.confirmLabel,
  });
  final ScheduleImpact impact;
  final String? confirmLabel;

  @override
  Widget build(BuildContext context) => SingleChildScrollView(
    padding: const EdgeInsets.all(16),
    child: Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        LText(
          'Bệnh nhân bị ảnh hưởng',
          style: Theme.of(context).textTheme.titleMedium,
        ),
        const SizedBox(height: 4),
        LText(
          'Đang giữ chỗ: ${impact.heldCount} · Đang thanh toán: ${impact.paymentLockedCount} · Đã xác nhận: ${impact.confirmedCount} · Đã dùng: ${impact.consumedCount}',
        ),
        const SizedBox(height: 8),
        for (final row in impact.registrations)
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                ReferenceLabel(
                  kind: ReferenceKind.patient,
                  id: row.patientId,
                  label: 'Bệnh nhân',
                ),
                LText(
                  [
                    registrationStatusLabels[row.status] ?? row.status,
                    clinicNumbersText(row.queueNumber, row.roomQueueNumber),
                  ].where((part) => part.isNotEmpty).join(' · '),
                ),
              ],
            ),
          ),
        if (confirmLabel != null)
          SizedBox(
            width: double.infinity,
            child: FilledButton(
              onPressed: () => Navigator.of(context).pop(true),
              child: LText(confirmLabel!),
            ),
          ),
      ],
    ),
  );
}

/// Shows who a schedule change affects. With [confirmLabel], resolves true only
/// when the list loaded and the user went ahead.
Future<bool> showScheduleImpact(
  BuildContext context,
  Future<ScheduleImpact> impact, {
  String? confirmLabel,
}) async {
  final confirmed = await showModalBottomSheet<bool>(
    context: context,
    isScrollControlled: true,
    builder: (_) => SafeArea(
      child: FutureBuilder<ScheduleImpact>(
        future: impact,
        builder: (context, snapshot) {
          if (snapshot.hasError) return RequestError(snapshot.error!);
          final data = snapshot.data;
          if (data == null) {
            return const Padding(
              padding: EdgeInsets.all(24),
              child: Center(child: CircularProgressIndicator()),
            );
          }
          return ScheduleImpactSheet(impact: data, confirmLabel: confirmLabel);
        },
      ),
    ),
  );
  return confirmed == true;
}
