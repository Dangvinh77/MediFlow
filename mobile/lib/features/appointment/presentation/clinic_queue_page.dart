import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/providers.dart';
import '../../../core/widgets/reference_widgets.dart';
import '../../../core/widgets/request_state.dart';
import '../domain/clinic_queue.dart';
import 'vital_sign_sheet.dart';

final clinicIntakeEnabledProvider = Provider<bool>(
  (ref) => ref.watch(appConfigProvider).clinicIntakeEnabled,
);

final clinicQueueRepositoryProvider = Provider<ClinicQueueRepository>(
  (ref) => throw StateError('ClinicQueueRepository chưa được cấu hình.'),
);

/// Lives as long as the signed-in session, so a sheet closed after an unknown
/// outcome still resends under the same key.
final clinicCommandKeysProvider = Provider<ClinicCommandKeys>((ref) {
  ref.watch(workspaceAccessProvider.select((a) => a?.sessionIdentity));
  return ClinicCommandKeys();
});

const _groups = [
  ('Chờ cấp số phòng', ['WAITING_ROOM_TICKET']),
  ('Được vào khám', ['WAITING_CALL', 'CALLED']),
  ('Đang khám', ['IN_EXAM']),
  ('Vắng mặt', ['ABSENT']),
  ('Chưa đủ điều kiện', ['AWAITING_PAYMENT', 'NOT_ARRIVED']),
];

const _adjustmentLabels = {
  'LATE': 'Đến muộn',
  'NO_SHOW': 'Vắng mặt',
  'RETURNED': 'Đã quay lại',
  'PRIORITY': 'Ưu tiên',
};

class _DeskCommand {
  const _DeskCommand(this.scope, this.fingerprint, this.send);
  final String scope, fingerprint;
  final Future<ClinicQueueTicket> Function(String key) send;
}

class ClinicQueuePage extends ConsumerStatefulWidget {
  const ClinicQueuePage({super.key});

  @override
  ConsumerState<ClinicQueuePage> createState() => _ClinicQueuePageState();
}

class _ClinicQueuePageState extends ConsumerState<ClinicQueuePage> {
  Future<List<ClinicQueueEntry>>? _board;
  _DeskCommand? _pending;
  String? _pendingRegistration;
  String? _busyRegistration;
  String? _errorRegistration;
  Object? _error;
  String? _notice;

  bool get _allowed => const [
    'NURSE',
    'DOCTOR',
  ].contains(ref.read(workspaceAccessProvider)?.role);

  @override
  void initState() {
    super.initState();
    if (ref.read(clinicIntakeEnabledProvider) && _allowed) _load();
  }

  void _load() {
    _board = ref.read(clinicQueueRepositoryProvider).board(date: vietnamDate());
    if (mounted) setState(() {});
  }

  bool get _locked => _busyRegistration != null || _pending != null;

  Future<void> _run(
    ClinicQueueEntry entry,
    _DeskCommand command,
    String Function(ClinicQueueTicket) done,
  ) async {
    if (_busyRegistration != null) return;
    setState(() {
      _busyRegistration = entry.registrationId;
      _error = null;
      _errorRegistration = null;
      _notice = null;
    });
    final keys = ref.read(clinicCommandKeysProvider);
    try {
      final ticket = await command.send(
        keys.keyFor(command.scope, command.fingerprint),
      );
      keys.settle(command.scope);
      if (!mounted) return;
      setState(() {
        _pending = null;
        _pendingRegistration = null;
        _notice = done(ticket);
      });
      _load();
    } catch (error) {
      final unknown = clinicOutcomeUnknown(error);
      if (!unknown) keys.settle(command.scope);
      if (!mounted) return;
      setState(() {
        _error = friendlyClinicError(error);
        _errorRegistration = entry.registrationId;
        _pending = unknown ? command : null;
        _pendingRegistration = unknown ? entry.registrationId : null;
      });
      if (!unknown) _load();
    } finally {
      if (mounted) setState(() => _busyRegistration = null);
    }
  }

  void _issue(ClinicQueueEntry entry) {
    final repository = ref.read(clinicQueueRepositoryProvider);
    _run(
      entry,
      _DeskCommand(
        'clinic-room-ticket:${entry.registrationId}',
        entry.registrationId,
        (key) =>
            repository.issueTicket(entry.registrationId, idempotencyKey: key),
      ),
      (ticket) => 'Đã cấp số phòng ${ticket.roomQueueNumber}.',
    );
  }

  void _call(ClinicQueueEntry entry) {
    final repository = ref.read(clinicQueueRepositoryProvider);
    final ticketId = entry.ticketId!, revision = entry.ticketRevision!;
    _run(
      entry,
      _DeskCommand(
        'clinic-call:$ticketId',
        '$revision',
        (key) => repository.call(ticketId, revision, idempotencyKey: key),
      ),
      (ticket) => 'Đã gọi số phòng ${ticket.roomQueueNumber}.',
    );
  }

  Future<void> _adjust(ClinicQueueEntry entry, String type) async {
    final reason = await showDialog<String>(
      context: context,
      builder: (_) => _ReasonDialog(title: _adjustmentLabels[type]!),
    );
    if (reason == null || !mounted) return;
    final repository = ref.read(clinicQueueRepositoryProvider);
    final ticketId = entry.ticketId!, revision = entry.ticketRevision!;
    _run(
      entry,
      _DeskCommand(
        'clinic-adjust:$ticketId:$type',
        '$revision:$reason',
        (key) => repository.adjust(
          ticketId,
          revision,
          type,
          reason,
          idempotencyKey: key,
        ),
      ),
      (_) => 'Đã ghi nhận: ${_adjustmentLabels[type]}.',
    );
  }

  Future<void> _sheet(WidgetBuilder builder) => showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    builder: (sheet) => Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(sheet).bottom),
      child: SafeArea(child: builder(sheet)),
    ),
  );

  void _recordVitals(ClinicQueueEntry entry) => _sheet(
    (sheet) => VitalSignSheet(
      registrationId: entry.registrationId,
      onSaved: () {
        Navigator.of(sheet).pop();
        setState(() => _notice = 'Đã lưu sinh hiệu.');
        _load();
      },
    ),
  );

  void _viewVitals(ClinicQueueEntry entry) => _sheet(
    (_) => VitalSignHistorySheet(registrationId: entry.registrationId),
  );

  Widget _row(ClinicQueueEntry entry, String? role) {
    final inLine = entry.stage == 'WAITING_CALL' || entry.stage == 'CALLED';
    final absent = entry.stage == 'ABSENT';
    final retry =
        _pending != null && _pendingRegistration == entry.registrationId;
    final numbers = clinicNumbersText(entry.queueNumber, entry.roomQueueNumber);
    final hasTicket = entry.ticketId != null && entry.ticketRevision != null;
    Widget adjust(String type) => OutlinedButton(
      onPressed: _locked ? null : () => _adjust(entry, type),
      child: Text(_adjustmentLabels[type]!),
    );
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          ReferenceLabel(
            kind: ReferenceKind.patient,
            id: entry.patientId,
            label: 'Bệnh nhân',
          ),
          if (numbers.isNotEmpty) Text(numbers),
          if (entry.priority) const Text('Được ưu tiên'),
          if (entry.checkedInAt != null)
            Text(
              entry.vitalSignsRecorded
                  ? 'Đã có sinh hiệu'
                  : 'Chưa có sinh hiệu — vẫn có thể khám và đo sau',
            ),
          if (entry.continuationRequired)
            const Text(
              'Buổi khám đã hết giờ nhưng lượt khám còn mở, cần xử lý tiếp.',
            ),
          if (_errorRegistration == entry.registrationId && _error != null)
            RequestError(_error!),
          const SizedBox(height: 4),
          Wrap(
            spacing: 8,
            runSpacing: 4,
            children: [
              if (retry)
                FilledButton(
                  onPressed: _busyRegistration != null
                      ? null
                      : () => _run(
                          entry,
                          _pending!,
                          (_) => 'Đã gửi lại yêu cầu thành công.',
                        ),
                  child: const Text('Gửi lại cùng yêu cầu'),
                ),
              if (entry.stage == 'WAITING_ROOM_TICKET' && role == 'NURSE')
                FilledButton(
                  onPressed: _locked ? null : () => _issue(entry),
                  child: const Text('Cấp số phòng'),
                ),
              if (inLine && hasTicket) ...[
                FilledButton(
                  onPressed: _locked ? null : () => _call(entry),
                  child: Text(
                    entry.stage == 'CALLED' ? 'Gọi lại' : 'Gọi vào khám',
                  ),
                ),
                adjust('LATE'),
                adjust('NO_SHOW'),
              ],
              if (absent && hasTicket) adjust('RETURNED'),
              if ((inLine || absent) && hasTicket && !entry.priority)
                adjust('PRIORITY'),
              if (entry.checkedInAt != null && role == 'NURSE')
                OutlinedButton(
                  onPressed: _locked ? null : () => _recordVitals(entry),
                  child: const Text('Ghi sinh hiệu'),
                ),
              if (entry.checkedInAt != null)
                OutlinedButton(
                  onPressed: () => _viewVitals(entry),
                  child: const Text('Xem sinh hiệu'),
                ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _room(String roomId, List<ClinicQueueEntry> entries, String? role) =>
      Card(
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              ReferenceLabel(
                kind: ReferenceKind.room,
                id: roomId,
                label: 'Phòng khám',
              ),
              for (final group in _groups)
                if (entries.any((e) => group.$2.contains(e.stage))) ...[
                  const SizedBox(height: 8),
                  Text(group.$1, style: Theme.of(context).textTheme.titleSmall),
                  for (final entry in entries.where(
                    (e) => group.$2.contains(e.stage),
                  ))
                    _row(entry, role),
                ],
            ],
          ),
        ),
      );

  @override
  Widget build(BuildContext context) {
    if (!ref.watch(clinicIntakeEnabledProvider)) {
      return const Center(child: Text('Quầy điều dưỡng khoa chưa được mở.'));
    }
    final role = ref.watch(workspaceAccessProvider)?.role;
    if (!const ['NURSE', 'DOCTOR'].contains(role)) {
      return const Center(
        child: Text('Chức năng dành cho điều dưỡng và bác sĩ.'),
      );
    }
    return FutureBuilder<List<ClinicQueueEntry>>(
      future: _board,
      builder: (context, snapshot) {
        final entries = snapshot.data;
        if (snapshot.hasError && entries == null) {
          return RequestError(
            friendlyClinicError(snapshot.error!),
            onRetry: _load,
          );
        }
        if (entries == null) {
          return const Center(child: CircularProgressIndicator());
        }
        final rooms = {for (final entry in entries) entry.roomId};
        return RefreshIndicator(
          onRefresh: () async => _load(),
          child: ListView(
            padding: const EdgeInsets.all(12),
            children: [
              if (_notice != null) Text(_notice!),
              if (rooms.isEmpty)
                const Text('Hôm nay chưa có bệnh nhân trong hàng đợi.'),
              for (final roomId in rooms)
                _room(
                  roomId,
                  entries.where((e) => e.roomId == roomId).toList(),
                  role,
                ),
            ],
          ),
        );
      },
    );
  }
}

class _ReasonDialog extends StatefulWidget {
  const _ReasonDialog({required this.title});
  final String title;

  @override
  State<_ReasonDialog> createState() => _ReasonDialogState();
}

class _ReasonDialogState extends State<_ReasonDialog> {
  final _reason = TextEditingController();

  @override
  void dispose() {
    _reason.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: Text(widget.title),
    content: TextField(
      controller: _reason,
      autofocus: true,
      maxLength: 500,
      decoration: const InputDecoration(labelText: 'Lý do'),
      onChanged: (_) => setState(() {}),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.of(context).pop(),
        child: const Text('Quay lại'),
      ),
      FilledButton(
        onPressed: _reason.text.trim().isEmpty
            ? null
            : () => Navigator.of(context).pop(_reason.text.trim()),
        child: const Text('Xác nhận'),
      ),
    ],
  );
}
