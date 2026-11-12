import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mediflow_loader/mediflow_loader.dart';

import '../../../core/domain/follow_up_recommendation.dart';
import '../../../core/network/api_failure.dart';
import '../../../core/providers.dart';
import '../../../core/widgets/paged_list.dart';
import '../../../core/widgets/reference_widgets.dart';
import '../../../core/widgets/request_state.dart';
import '../domain/clinic_queue.dart';
import '../domain/outpatient_schedule.dart';
import 'clinic_queue_page.dart';
import 'reschedule_sheet.dart';
import '../domain/reception.dart';

final outpatientScheduleRepositoryProvider =
    Provider<OutpatientScheduleRepository>(
      (ref) =>
          throw StateError('Bind outpatient schedule in app/dependencies.dart'),
    );

const _notOpen = 'Lịch khám chưa được mở.';

class _ScheduleFilter extends StatelessWidget {
  const _ScheduleFilter({
    required this.week,
    required this.date,
    required this.onWeekChanged,
    required this.onDateChanged,
  });

  final bool week;
  final DateTime date;
  final ValueChanged<bool> onWeekChanged;
  final ValueChanged<DateTime> onDateChanged;

  @override
  Widget build(BuildContext context) => Wrap(
    spacing: 12,
    runSpacing: 8,
    crossAxisAlignment: WrapCrossAlignment.center,
    children: [
      SegmentedButton<bool>(
        segments: const [
          ButtonSegment(value: false, label: Text('Theo ngày')),
          ButtonSegment(value: true, label: Text('Theo tuần')),
        ],
        selected: {week},
        onSelectionChanged: (value) => onWeekChanged(value.single),
      ),
      OutlinedButton.icon(
        onPressed: () async {
          final picked = await showDatePicker(
            context: context,
            initialDate: date,
            firstDate: DateTime(date.year - 1),
            lastDate: DateTime(date.year + 2),
          );
          if (picked != null) onDateChanged(picked);
        },
        icon: const Icon(Icons.event),
        label: Text(displayDate(isoDate(date))),
      ),
    ],
  );
}

/// The signed-in doctor's sessions. The account decides whose calendar loads.
class DoctorCalendarPage extends ConsumerStatefulWidget {
  const DoctorCalendarPage({super.key, this.onOpenRegistration});
  final void Function(BuildContext context, String registrationId)?
  onOpenRegistration;

  @override
  ConsumerState<DoctorCalendarPage> createState() => _DoctorCalendarPageState();
}

class _DoctorCalendarPageState extends ConsumerState<DoctorCalendarPage> {
  bool _week = false;
  DateTime _date = clinicToday();
  late Future<DoctorCalendar> _calendar = _load();

  Future<DoctorCalendar> _load() {
    final repository = ref.read(outpatientScheduleRepositoryProvider);
    return _week
        ? repository.myCalendar(weekStart: isoDate(mondayOfWeek(_date)))
        : repository.myCalendar(date: isoDate(_date));
  }

  void _reload() {
    final next = _load();
    setState(() {
      _calendar = next;
    });
  }

  @override
  Widget build(BuildContext context) {
    final access = ref.watch(workspaceAccessProvider);
    if (access?.role != 'DOCTOR') {
      return const Center(child: Text('Chức năng dành cho bác sĩ.'));
    }
    if (!ref.watch(appConfigProvider).scheduledPaymentsEnabled) {
      return const Center(child: Text(_notOpen));
    }
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text(
          'Lịch khám của tôi',
          style: Theme.of(context).textTheme.titleLarge,
        ),
        const SizedBox(height: 12),
        _ScheduleFilter(
          week: _week,
          date: _date,
          onWeekChanged: (value) {
            _week = value;
            _reload();
          },
          onDateChanged: (value) {
            _date = value;
            _reload();
          },
        ),
        const SizedBox(height: 12),
        FutureBuilder<DoctorCalendar>(
          future: _calendar,
          builder: (context, snapshot) {
            if (snapshot.hasError) {
              return RequestError(snapshot.error!, onRetry: _reload);
            }
            final calendar = snapshot.data;
            if (calendar == null ||
                snapshot.connectionState != ConnectionState.done) {
              return const Center(child: MediFlowLoader());
            }
            if (calendar.sessions.isEmpty) {
              return const Text('Không có buổi khám trong khoảng này.');
            }
            return Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                for (final session in calendar.sessions)
                  _SessionCard(
                    session: session,
                    onOpenRegistration: widget.onOpenRegistration,
                  ),
              ],
            );
          },
        ),
      ],
    );
  }
}

class _SessionCard extends StatelessWidget {
  const _SessionCard({required this.session, this.onOpenRegistration});
  final ScheduleSession session;
  final void Function(BuildContext context, String registrationId)?
  onOpenRegistration;

  @override
  Widget build(BuildContext context) => Card(
    child: Padding(
      padding: const EdgeInsets.all(12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '${displayDate(session.date)} · ${session.startTime}–${session.endTime}',
            style: Theme.of(context).textTheme.titleSmall,
          ),
          Text(sessionStatusLabels[session.status] ?? session.status),
          ReferenceLabel(
            kind: ReferenceKind.department,
            id: session.departmentId,
            label: 'Khoa',
          ),
          ReferenceLabel(
            kind: ReferenceKind.room,
            id: session.roomId,
            label: 'Phòng khám',
          ),
          Text(
            '${session.confirmedCount} đã xác nhận · ${session.availableCount} còn chỗ',
          ),
          const SizedBox(height: 8),
          OutlinedButton(
            onPressed: () => Navigator.of(context).push<void>(
              MaterialPageRoute(
                builder: (_) => SessionRegistrationsPage(
                  sessionId: session.id,
                  title:
                      '${displayDate(session.date)} · ${session.startTime}–${session.endTime}',
                  onOpenRegistration: onOpenRegistration,
                ),
              ),
            ),
            child: const Text('Xem phiếu của buổi'),
          ),
        ],
      ),
    ),
  );
}

class DoctorWorkShiftsPage extends ConsumerStatefulWidget {
  const DoctorWorkShiftsPage({super.key});

  @override
  ConsumerState<DoctorWorkShiftsPage> createState() =>
      _DoctorWorkShiftsPageState();
}

class _DoctorWorkShiftsPageState extends ConsumerState<DoctorWorkShiftsPage> {
  bool _week = false;
  DateTime _date = clinicToday();
  late Future<List<DoctorWorkShift>> _shifts = _load();

  Future<List<DoctorWorkShift>> _load() {
    final from = _week ? mondayOfWeek(_date) : _date;
    final to = _week ? from.add(const Duration(days: 6)) : _date;
    return ref
        .read(outpatientScheduleRepositoryProvider)
        .myWorkShifts(isoDate(from), isoDate(to));
  }

  void _reload() {
    final next = _load();
    setState(() {
      _shifts = next;
    });
  }

  @override
  Widget build(BuildContext context) {
    final access = ref.watch(workspaceAccessProvider);
    if (access?.role != 'DOCTOR') {
      return const Center(child: Text('Chức năng dành cho bác sĩ.'));
    }
    if (!ref.watch(appConfigProvider).scheduledPaymentsEnabled) {
      return const Center(child: Text('Lịch làm việc chưa được bật.'));
    }
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text(
          'Lịch làm việc của tôi',
          style: Theme.of(context).textTheme.titleLarge,
        ),
        const SizedBox(height: 4),
        const Text(
          'Phân công giờ và phòng của bạn. Ca làm việc và buổi khám được quản lý riêng.',
        ),
        const SizedBox(height: 12),
        _ScheduleFilter(
          week: _week,
          date: _date,
          onWeekChanged: (value) {
            _week = value;
            _reload();
          },
          onDateChanged: (value) {
            _date = value;
            _reload();
          },
        ),
        const SizedBox(height: 12),
        FutureBuilder<List<DoctorWorkShift>>(
          future: _shifts,
          builder: (context, snapshot) {
            if (snapshot.hasError) {
              return RequestError(snapshot.error!, onRetry: _reload);
            }
            final shifts = snapshot.data;
            if (shifts == null ||
                snapshot.connectionState != ConnectionState.done) {
              return const Center(child: MediFlowLoader());
            }
            if (shifts.isEmpty) {
              return const Text('Không có ca làm việc trong khoảng này.');
            }
            return Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                for (final shift in shifts)
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(12),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            '${displayDate(shift.date)} · ${shift.startTime}–${shift.endTime}',
                            style: Theme.of(context).textTheme.titleSmall,
                          ),
                          ReferenceLabel(
                            kind: ReferenceKind.department,
                            id: shift.departmentId,
                            label: 'Khoa',
                          ),
                          ReferenceLabel(
                            kind: ReferenceKind.room,
                            id: shift.roomId,
                            label: 'Phòng khám',
                          ),
                          if (shift.status == 'CANCELLED')
                            const Text('Ca đã hủy'),
                        ],
                      ),
                    ),
                  ),
              ],
            );
          },
        ),
      ],
    );
  }
}

class _PendingArrival {
  const _PendingArrival(this.revision, this.key, this.correlationId);
  final int revision;
  final String key, correlationId;
}

/// Registrations of one session. Nurses also record arrival from here, because
/// a prebooked patient cannot be examined until staff confirm they are present.
class SessionRegistrationsPage extends ConsumerStatefulWidget {
  const SessionRegistrationsPage({
    super.key,
    required this.sessionId,
    required this.title,
    this.canMarkArrival = false,
    this.onOpenRegistration,
  });

  final String sessionId, title;
  final bool canMarkArrival;
  final void Function(BuildContext context, String registrationId)?
  onOpenRegistration;

  @override
  ConsumerState<SessionRegistrationsPage> createState() =>
      _SessionRegistrationsPageState();
}

class _SessionRegistrationsPageState
    extends ConsumerState<SessionRegistrationsPage> {
  int _revision = 0;
  String? _busyId, _errorId;
  Object? _error;
  final _pending = <String, _PendingArrival>{};

  bool _unknownOutcome(Object error) =>
      error is! ApiFailure ||
      error.status == null ||
      error.status == 408 ||
      error.status! >= 500;

  Future<void> _markArrival(RegistrationRow row) async {
    if (_busyId != null) return;
    final repository = ref.read(outpatientScheduleRepositoryProvider);
    setState(() {
      _busyId = row.id;
      _errorId = null;
      _error = null;
    });
    try {
      var pending = _pending[row.id];
      if (pending == null) {
        final current = await repository.registration(row.id);
        if (current.checkedInAt != null) {
          if (mounted) setState(() => _revision++);
          return;
        }
        if (current.status != 'CONFIRMED') {
          throw const ApiFailure(
            'Phiếu không còn ở trạng thái đã xác nhận. Hãy tải lại danh sách trước khi ghi nhận đến.',
            status: 409,
          );
        }
        pending = _PendingArrival(
          current.revision,
          newReceptionKey(),
          newReceptionKey(),
        );
        _pending[row.id] = pending;
      }
      await repository.markArrival(
        row.id,
        expectedRevision: pending.revision,
        idempotencyKey: pending.key,
        correlationId: pending.correlationId,
      );
      _pending.remove(row.id);
      if (mounted) setState(() => _revision++);
    } catch (error) {
      if (!_unknownOutcome(error)) _pending.remove(row.id);
      if (mounted) {
        setState(() {
          _errorId = row.id;
          _error = error;
        });
      }
    } finally {
      if (mounted) setState(() => _busyId = null);
    }
  }

  Future<void> _reschedule(RegistrationRow row) => showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    builder: (sheet) => Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(sheet).bottom),
      child: SafeArea(
        child: RescheduleSheet(
          registrationId: row.id,
          currentSessionId: widget.sessionId,
          onDone: () {
            Navigator.of(sheet).pop();
            setState(() => _revision++);
          },
        ),
      ),
    ),
  );

  @override
  Widget build(BuildContext context) {
    final repository = ref.watch(outpatientScheduleRepositoryProvider);
    final clinicIntake = ref.watch(clinicIntakeEnabledProvider);
    return Scaffold(
      appBar: AppBar(title: Text('Phiếu buổi ${widget.title}')),
      body: PagedList<RegistrationRow>(
        key: ValueKey((widget.sessionId, _revision)),
        load: (page) =>
            repository.sessionRegistrations(widget.sessionId, page: page),
        itemBuilder: (context, row) => Card(
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                ReferenceLabel(
                  kind: ReferenceKind.patient,
                  id: row.patientId,
                  label: 'Bệnh nhân',
                ),
                Text(
                  '${row.visitType == 'FOLLOW_UP' ? 'Tái khám' : 'Khám mới'} · ${registrationStatusLabels[row.status] ?? row.status}',
                ),
                Text(
                  '${row.checkedInAt != null ? 'Đã đến' : 'Chưa đến'} · ${_rowNumbers(row, clinicIntake)}',
                ),
                if (_errorId == row.id && _error != null) RequestError(_error!),
                if (widget.canMarkArrival &&
                    row.checkedInAt == null &&
                    row.status == 'CONFIRMED') ...[
                  const SizedBox(height: 8),
                  FilledButton(
                    onPressed: _busyId == null ? () => _markArrival(row) : null,
                    child: Text(
                      _pending.containsKey(row.id)
                          ? 'Gửi lại ghi nhận đã đến'
                          : 'Ghi nhận đã đến',
                    ),
                  ),
                ],
                if (clinicIntake &&
                    widget.canMarkArrival &&
                    const [
                      'AWAITING_PAYMENT',
                      'CONFIRMED',
                    ].contains(row.status)) ...[
                  const SizedBox(height: 8),
                  OutlinedButton(
                    onPressed: _busyId == null ? () => _reschedule(row) : null,
                    child: const Text('Đổi lịch'),
                  ),
                ],
                if (widget.onOpenRegistration != null) ...[
                  const SizedBox(height: 8),
                  OutlinedButton(
                    onPressed: () =>
                        widget.onOpenRegistration!(context, row.id),
                    child: const Text('Xem phiếu'),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}

String _rowNumbers(RegistrationRow row, bool clinicIntake) {
  if (clinicIntake) {
    final numbers = clinicNumbersText(row.queueNumber, row.roomQueueNumber);
    return numbers.isEmpty ? 'Chưa có số' : numbers;
  }
  return row.queueNumber == null ? 'Chưa có số' : 'Số ${row.queueNumber}';
}

class PatientRegistrationsPage extends ConsumerWidget {
  const PatientRegistrationsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final access = ref.watch(workspaceAccessProvider);
    if (access?.role != 'PATIENT') {
      return const Center(
        child: Text('Chức năng dành cho bệnh nhân đăng nhập.'),
      );
    }
    if (!ref.watch(appConfigProvider).scheduledPaymentsEnabled) {
      return const Center(child: Text('Tra cứu phiếu lịch khám chưa được mở.'));
    }
    final repository = ref.watch(outpatientScheduleRepositoryProvider);
    return PagedList<OutpatientRegistration>(
      key: ValueKey((access!.sessionIdentity, repository)),
      load: (page) => repository.myRegistrations(page: page),
      canRetry: (error) =>
          error is! ApiFailure || !const [401, 403].contains(error.status),
      itemBuilder: (context, item) => _RegistrationCard(
        item,
        clinicIntake: ref.watch(clinicIntakeEnabledProvider),
      ),
    );
  }
}

class _RegistrationCard extends StatelessWidget {
  const _RegistrationCard(this.item, {required this.clinicIntake});
  final OutpatientRegistration item;
  final bool clinicIntake;

  @override
  Widget build(BuildContext context) => Card(
    child: Padding(
      padding: const EdgeInsets.all(12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '${item.visitType == 'FOLLOW_UP' ? 'Tái khám' : 'Khám mới'} · ${displayDate(item.sessionDate)} · ${item.startTime}–${item.endTime}',
            style: Theme.of(context).textTheme.titleSmall,
          ),
          Text(registrationStatusLabels[item.status] ?? item.status),
          const SizedBox(height: 4),
          ReferenceLabel(
            kind: ReferenceKind.department,
            id: item.departmentId,
            label: 'Khoa',
          ),
          ReferenceLabel(
            kind: ReferenceKind.doctor,
            id: item.doctorId,
            label: 'Bác sĩ',
          ),
          ReferenceLabel(
            kind: ReferenceKind.room,
            id: item.roomId,
            label: 'Phòng khám',
          ),
          Text(
            'Hình thức: ${item.receptionMode == 'AT_COUNTER' ? 'Tiếp nhận tại quầy' : 'Đăng ký trước'}',
          ),
          Text(
            '${queueNumberLabel(clinicIntake)}: ${item.queueNumber ?? 'Chưa cấp số trước khi xác nhận'}',
          ),
          if (clinicIntake && item.roomQueueNumber != null)
            Text('$roomQueueNumberLabel: ${item.roomQueueNumber}'),
          if (item.status == 'AWAITING_PAYMENT') ...[
            Text(
              'Hạn thanh toán giữ chỗ: ${_localTime(item.reservationExpiresAt)}',
            ),
            const SizedBox(height: 8),
            const Text(
              'Phiếu đang chờ thanh toán và xác nhận. Lịch hẹn chưa được xác nhận.',
            ),
          ],
          if (item.status == 'CONFIRMED') ...[
            const SizedBox(height: 8),
            Text(
              item.checkedInAt != null
                  ? 'Đã ghi nhận bạn có mặt. Vui lòng chờ gọi tên theo hướng dẫn tại phòng khám.'
                  : 'Lịch khám đã xác nhận. Vui lòng đến quầy tiếp nhận vào ngày hẹn để ghi nhận có mặt.',
            ),
          ],
          if (item.followUp case final followUp?) ...[
            const SizedBox(height: 8),
            _RegistrationFollowUp(followUp),
          ],
        ],
      ),
    ),
  );
}

class _RegistrationFollowUp extends StatelessWidget {
  const _RegistrationFollowUp(this.followUp);
  final FollowUpRecommendation followUp;

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Text('Đề xuất tái khám', style: Theme.of(context).textTheme.titleSmall),
      Text(
        'Khoảng ngày khuyến nghị: ${displayDate(followUp.recommendedFrom)} – ${displayDate(followUp.recommendedThrough)}',
      ),
      Text('Lý do: ${followUp.reason}'),
      if (followUp.preparationInstructions case final instructions?)
        Text('Hướng dẫn chuẩn bị: $instructions'),
    ],
  );
}

String _localTime(String raw) {
  final value = DateTime.tryParse(raw)?.toLocal();
  if (value == null) return '—';
  String two(int number) => number.toString().padLeft(2, '0');
  return '${two(value.hour)}:${two(value.minute)} '
      '${two(value.day)}/${two(value.month)}/${value.year}';
}
