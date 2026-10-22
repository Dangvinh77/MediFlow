import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_failure.dart';
import '../../../core/providers.dart';
import '../../../core/widgets/paged_list.dart';
import '../../../core/widgets/reference_widgets.dart';
import '../domain/appointment.dart';

final appointmentRepositoryProvider = Provider<AppointmentRepository>(
  (ref) => throw StateError(
    'AppointmentRepository must be bound in app/dependencies.dart',
  ),
);
final findAppointmentsProvider = Provider(
  (ref) => FindAppointments(ref.watch(appointmentRepositoryProvider)),
);
final outpatientUiEnabledProvider = Provider<bool>(
  (ref) => const bool.fromEnvironment(
    'MEDIFLOW_OUTPATIENT_UI_ENABLED',
    defaultValue: false,
  ),
);

class AppointmentPage extends ConsumerStatefulWidget {
  const AppointmentPage({super.key});
  @override
  ConsumerState<AppointmentPage> createState() => _AppointmentPageState();
}

class _AppointmentPageState extends ConsumerState<AppointmentPage> {
  int _revision = 0;
  String? _date;
  String? _departmentId;

  @override
  Widget build(BuildContext context) {
    final access = ref.watch(workspaceAccessProvider);
    if (access == null) return const _SignInRequired();
    if (!const {'ADMIN', 'MANAGER', 'DOCTOR', 'NURSE'}.contains(access.role)) {
      return const Center(child: Text('Bạn không có quyền xem lịch hẹn.'));
    }
    final findAppointments = ref.watch(findAppointmentsProvider);
    final canCreate = const {'ADMIN', 'NURSE'}.contains(access.role);
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.all(12),
          child: Wrap(
            spacing: 12,
            runSpacing: 8,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              OutlinedButton.icon(
                onPressed: () async {
                  final date = await showDatePicker(
                    context: context,
                    initialDate: _date == null
                        ? DateTime.now()
                        : DateTime.parse(_date!),
                    firstDate: DateTime(2000),
                    lastDate: DateTime(2100),
                  );
                  if (date != null && mounted) {
                    setState(() => _date = _dateString(date));
                  }
                },
                icon: const Icon(Icons.calendar_month),
                label: Text(_date ?? 'Chọn ngày hẹn'),
              ),
              SizedBox(
                width: 250,
                child: ReferencePicker(
                  kind: ReferenceKind.department,
                  label: 'Lọc theo khoa',
                  value: _departmentId,
                  onChanged: (value) => setState(() => _departmentId = value),
                ),
              ),
              TextButton(
                onPressed: _date == null && _departmentId == null
                    ? null
                    : () => setState(() {
                        _date = null;
                        _departmentId = null;
                      }),
                child: const Text('Bỏ lọc'),
              ),
              if (canCreate)
                FilledButton.icon(
                  onPressed: () async {
                    final saved = await Navigator.of(context).push<bool>(
                      MaterialPageRoute(
                        builder: (_) => const AppointmentEditorPage(),
                      ),
                    );
                    if (saved == true && mounted) setState(() => _revision++);
                  },
                  icon: const Icon(Icons.add),
                  label: const Text('Tạo lịch hẹn'),
                ),
            ],
          ),
        ),
        Expanded(
          child: PagedList<Appointment>(
            key: ValueKey((
              access.sessionIdentity,
              _date,
              _departmentId,
              _revision,
            )),
            load: (page) => findAppointments(
              page: page,
              date: _date,
              departmentId: _departmentId,
            ),
            canRetry: (error) =>
                error is! ApiFailure ||
                !const {401, 403, 404}.contains(error.status),
            itemBuilder: (context, appointment) => _AppointmentCard(
              appointment: appointment,
              onOpen: const {'ADMIN', 'DOCTOR', 'NURSE'}.contains(access.role)
                  ? () => Navigator.of(context).push<void>(
                      MaterialPageRoute(
                        builder: (_) => AppointmentDetailPage(
                          appointmentId: appointment.id,
                        ),
                      ),
                    )
                  : null,
            ),
          ),
        ),
      ],
    );
  }
}

class _AppointmentCard extends StatelessWidget {
  const _AppointmentCard({required this.appointment, this.onOpen});
  final Appointment appointment;
  final VoidCallback? onOpen;

  @override
  Widget build(BuildContext context) => Card(
    child: InkWell(
      onTap: onOpen,
      borderRadius: BorderRadius.circular(12),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    '${appointment.date} · ${appointment.time}',
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                ),
                Chip(label: Text(_appointmentStatus(appointment.status))),
              ],
            ),
            ReferenceLabel(
              kind: ReferenceKind.patient,
              id: appointment.patientId,
              label: 'Bệnh nhân',
            ),
            ReferenceLabel(
              kind: ReferenceKind.doctor,
              id: appointment.doctorId,
              label: 'Bác sĩ',
            ),
            ReferenceLabel(
              kind: ReferenceKind.department,
              id: appointment.departmentId,
              label: 'Khoa',
            ),
            if (appointment.reason?.trim().isNotEmpty == true)
              Padding(
                padding: const EdgeInsets.only(top: 4),
                child: Text('Lý do: ${appointment.reason}'),
              ),
            if (onOpen != null)
              Align(
                alignment: Alignment.centerRight,
                child: TextButton.icon(
                  onPressed: onOpen,
                  icon: const Icon(Icons.open_in_new),
                  label: const Text('Mở lịch hẹn'),
                ),
              ),
          ],
        ),
      ),
    ),
  );
}

class AppointmentDetailPage extends ConsumerStatefulWidget {
  const AppointmentDetailPage({super.key, required this.appointmentId});
  final String appointmentId;

  @override
  ConsumerState<AppointmentDetailPage> createState() =>
      _AppointmentDetailPageState();
}

class _AppointmentDetailPageState extends ConsumerState<AppointmentDetailPage> {
  Appointment? _appointment;
  Object? _error;
  bool _loading = true;
  bool _busy = false;
  bool _uncertain = false;
  int _generation = 0;
  Object? _sessionIdentity;

  @override
  void initState() {
    super.initState();
    _sessionIdentity = ref.read(workspaceAccessProvider)?.sessionIdentity;
    _load();
  }

  @override
  void didUpdateWidget(covariant AppointmentDetailPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.appointmentId != widget.appointmentId) _load();
  }

  Future<void> _load() async {
    final generation = ++_generation;
    _sessionIdentity = ref.read(workspaceAccessProvider)?.sessionIdentity;
    setState(() {
      _appointment = null;
      _error = null;
      _loading = true;
      _uncertain = false;
    });
    try {
      final appointment = await ref
          .read(appointmentRepositoryProvider)
          .getById(widget.appointmentId);
      if (_isCurrent(generation)) {
        setState(() {
          _appointment = appointment;
          _loading = false;
        });
      }
    } catch (error) {
      if (_isCurrent(generation)) {
        setState(() {
          _error = error;
          _loading = false;
        });
      }
    }
  }

  bool _isCurrent(int generation) =>
      mounted &&
      generation == _generation &&
      ref.read(workspaceAccessProvider)?.sessionIdentity == _sessionIdentity;

  bool get _canRead => const {
    'ADMIN',
    'DOCTOR',
    'NURSE',
  }.contains(ref.read(workspaceAccessProvider)?.role);

  Future<void> _runCommand(Future<Appointment> Function() command) async {
    if (_busy ||
        _uncertain ||
        _appointment == null ||
        !_isCurrent(_generation)) {
      return;
    }
    final identity = _sessionIdentity;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final updated = await command();
      if (!mounted ||
          ref.read(workspaceAccessProvider)?.sessionIdentity != identity) {
        return;
      }
      setState(() => _appointment = updated);
    } catch (error) {
      if (!mounted ||
          ref.read(workspaceAccessProvider)?.sessionIdentity != identity) {
        return;
      }
      final unknown =
          error is! ApiFailure ||
          error.status == null ||
          error.status == 408 ||
          (error.status ?? 0) >= 500;
      setState(() {
        _error = error;
        _uncertain = unknown;
      });
    } finally {
      if (mounted &&
          ref.read(workspaceAccessProvider)?.sessionIdentity == identity) {
        setState(() => _busy = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final access = ref.watch(workspaceAccessProvider);
    final outpatientEnabled = ref.watch(outpatientUiEnabledProvider);
    if (access?.sessionIdentity != _sessionIdentity) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) _load();
      });
      return const Scaffold(
        body: Center(child: Text('Đang cập nhật phiên đăng nhập…')),
      );
    }
    return Scaffold(
      appBar: AppBar(title: const Text('Chi tiết lịch hẹn')),
      body: !_canRead
          ? const Center(child: Text('Bạn không có quyền xem lịch hẹn.'))
          : _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null && _appointment == null
          ? _ErrorPanel(error: _error!, onRetry: _load)
          : _appointment == null
          ? const Center(child: Text('Không có dữ liệu lịch hẹn.'))
          : _buildDetail(_appointment!, access?.role, outpatientEnabled),
    );
  }

  Widget _buildDetail(
    Appointment appointment,
    String? role,
    bool outpatientEnabled,
  ) {
    final canEdit = canEditAppointment(appointment, role: role);
    final canCheckIn = canCheckInAppointment(
      appointment,
      role: role,
      outpatientEnabled: outpatientEnabled,
    );
    final canStart = canStartAppointment(
      appointment,
      role: role,
      outpatientEnabled: outpatientEnabled,
    );
    final canCancel = canCancelAppointment(appointment, role: role);
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '${appointment.date} · ${appointment.time}',
                  style: Theme.of(context).textTheme.headlineSmall,
                ),
                const SizedBox(height: 8),
                Chip(label: Text(_appointmentStatus(appointment.status))),
                ReferenceLabel(
                  kind: ReferenceKind.patient,
                  id: appointment.patientId,
                  label: 'Bệnh nhân',
                ),
                ReferenceLabel(
                  kind: ReferenceKind.doctor,
                  id: appointment.doctorId,
                  label: 'Bác sĩ',
                ),
                ReferenceLabel(
                  kind: ReferenceKind.department,
                  id: appointment.departmentId,
                  label: 'Khoa',
                ),
                if (appointment.reason?.trim().isNotEmpty == true)
                  Padding(
                    padding: const EdgeInsets.only(top: 8),
                    child: Text('Lý do: ${appointment.reason}'),
                  ),
              ],
            ),
          ),
        ),
        if (_error != null)
          _ErrorPanel(error: _error!, onRetry: _uncertain ? _load : null),
        if (_uncertain)
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 8),
            child: Text(
              'Kết quả thao tác chưa rõ. Làm mới lịch hẹn trước khi thử lại.',
              style: TextStyle(color: Colors.deepOrange),
            ),
          ),
        if (appointment.status == 'IN_EXAM')
          const Card(
            child: Padding(
              padding: EdgeInsets.all(16),
              child: Text('Đang khám. Hồ sơ khám được gắn với lịch hẹn này.'),
            ),
          ),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            if (canEdit)
              OutlinedButton.icon(
                onPressed: _busy || _uncertain
                    ? null
                    : () async {
                        final saved = await Navigator.of(context).push<bool>(
                          MaterialPageRoute(
                            builder: (_) =>
                                AppointmentEditorPage(appointment: appointment),
                          ),
                        );
                        if (saved == true) _load();
                      },
                icon: const Icon(Icons.edit),
                label: const Text('Sửa lịch hẹn'),
              ),
            if (canCheckIn)
              FilledButton.icon(
                onPressed: _busy || _uncertain
                    ? null
                    : () => _runCommand(
                        () => ref
                            .read(appointmentRepositoryProvider)
                            .checkIn(appointment.id),
                      ),
                icon: const Icon(Icons.how_to_reg),
                label: const Text('Tiếp nhận'),
              ),
            if (canStart)
              FilledButton.icon(
                onPressed: _busy || _uncertain
                    ? null
                    : () => _runCommand(
                        () => ref
                            .read(appointmentRepositoryProvider)
                            .startExam(appointment.id),
                      ),
                icon: const Icon(Icons.medical_services_outlined),
                label: const Text('Bắt đầu khám'),
              ),
            if (canCancel)
              OutlinedButton.icon(
                onPressed: _busy || _uncertain
                    ? null
                    : () => _confirmCancel(appointment),
                icon: const Icon(Icons.cancel_outlined),
                label: const Text('Hủy lịch hẹn'),
              ),
            OutlinedButton.icon(
              onPressed: _busy ? null : _load,
              icon: const Icon(Icons.refresh),
              label: const Text('Làm mới'),
            ),
          ],
        ),
        if (_busy)
          const Padding(
            padding: EdgeInsets.all(12),
            child: LinearProgressIndicator(),
          ),
        if (!outpatientEnabled)
          const Padding(
            padding: EdgeInsets.only(top: 12),
            child: Text(
              'Các thao tác tiếp nhận và bắt đầu khám chưa được bật cho ứng dụng này.',
            ),
          ),
      ],
    );
  }

  Future<void> _confirmCancel(Appointment appointment) async {
    final identity = ref.read(workspaceAccessProvider)?.sessionIdentity;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Hủy lịch hẹn?'),
        content: const Text('Hành động này sẽ được xác nhận trên máy chủ.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Quay lại'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Xác nhận hủy'),
          ),
        ],
      ),
    );
    if (confirmed == true &&
        mounted &&
        ref.read(workspaceAccessProvider)?.sessionIdentity == identity) {
      await _runCommand(
        () => ref.read(appointmentRepositoryProvider).cancel(appointment.id),
      );
    }
  }
}

class AppointmentEditorPage extends ConsumerStatefulWidget {
  const AppointmentEditorPage({super.key, this.appointment});
  final Appointment? appointment;
  @override
  ConsumerState<AppointmentEditorPage> createState() =>
      _AppointmentEditorPageState();
}

class _AppointmentEditorPageState extends ConsumerState<AppointmentEditorPage> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _reason;
  String? _patientId, _doctorId, _departmentId, _date, _time;
  String? _error;
  bool _busy = false, _uncertain = false;
  Object? _identity;

  @override
  void initState() {
    super.initState();
    final appointment = widget.appointment;
    _patientId = appointment?.patientId;
    _doctorId = appointment?.doctorId;
    _departmentId = appointment?.departmentId;
    _date = appointment?.date;
    _time = appointment == null ? null : _timeForPicker(appointment.time);
    _reason = TextEditingController(text: appointment?.reason ?? '');
    _identity = ref.read(workspaceAccessProvider)?.sessionIdentity;
  }

  @override
  void dispose() {
    _reason.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (_busy || _uncertain || !_formKey.currentState!.validate()) return;
    final access = ref.read(workspaceAccessProvider);
    final allowed = widget.appointment == null
        ? const {'ADMIN', 'NURSE'}.contains(access?.role)
        : const {'ADMIN', 'DOCTOR', 'NURSE'}.contains(access?.role);
    if (!allowed || access?.sessionIdentity != _identity) return;
    final draft = AppointmentDraft(
      patientId: _patientId ?? '',
      doctorId: _doctorId ?? '',
      departmentId: _departmentId ?? '',
      date: _date ?? '',
      time: _time ?? '',
      reason: _reason.text,
    );
    final issue = widget.appointment == null
        ? validateAppointmentDraft(draft)
        : validateAppointmentEdit(
            AppointmentEdit(
              date: draft.date,
              time: draft.time,
              reason: draft.reason,
            ),
          );
    if (issue != null) {
      setState(() => _error = issue);
      return;
    }
    final identity = _identity;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      if (widget.appointment == null) {
        await ref.read(appointmentRepositoryProvider).create(draft);
      } else {
        await ref
            .read(appointmentRepositoryProvider)
            .update(
              widget.appointment!.id,
              AppointmentEdit(
                date: draft.date,
                time: draft.time,
                reason: draft.reason,
              ),
            );
      }
      if (!mounted ||
          ref.read(workspaceAccessProvider)?.sessionIdentity != identity) {
        return;
      }
      Navigator.of(context).pop(true);
    } catch (error) {
      if (!mounted ||
          ref.read(workspaceAccessProvider)?.sessionIdentity != identity) {
        return;
      }
      final unknown =
          error is! ApiFailure ||
          error.status == null ||
          error.status == 408 ||
          (error.status ?? 0) >= 500;
      setState(() {
        _error = _message(error);
        _uncertain = unknown;
      });
    } finally {
      if (mounted &&
          ref.read(workspaceAccessProvider)?.sessionIdentity == identity) {
        setState(() => _busy = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final access = ref.watch(workspaceAccessProvider);
    if (access?.sessionIdentity != _identity) {
      return Scaffold(
        appBar: AppBar(title: const Text('Lịch hẹn')),
        body: const Center(child: Text('Phiên đăng nhập đã thay đổi.')),
      );
    }
    final canWrite = widget.appointment == null
        ? const {'ADMIN', 'NURSE'}.contains(access?.role)
        : const {'ADMIN', 'DOCTOR', 'NURSE'}.contains(access?.role);
    if (!canWrite) {
      return Scaffold(
        appBar: AppBar(title: const Text('Lịch hẹn')),
        body: const Center(child: Text('Bạn không có quyền sửa lịch hẹn.')),
      );
    }
    return Scaffold(
      appBar: AppBar(
        title: Text(
          widget.appointment == null ? 'Tạo lịch hẹn' : 'Sửa lịch hẹn',
        ),
      ),
      body: Form(
        key: _formKey,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            if (widget.appointment == null) ...[
              ReferencePicker(
                kind: ReferenceKind.patient,
                label: 'Bệnh nhân',
                value: _patientId,
                required: true,
                onChanged: (v) => setState(() => _patientId = v),
              ),
              ReferencePicker(
                kind: ReferenceKind.department,
                label: 'Khoa',
                value: _departmentId,
                required: true,
                onChanged: (v) => setState(() {
                  _departmentId = v;
                  _doctorId = null;
                }),
              ),
              ReferencePicker(
                kind: ReferenceKind.doctor,
                label: 'Bác sĩ',
                value: _doctorId,
                scope: _departmentId,
                required: true,
                onChanged: (v) => setState(() => _doctorId = v),
              ),
            ],
            OutlinedButton.icon(
              onPressed: _busy || _uncertain
                  ? null
                  : () async {
                      final initial = _date == null
                          ? DateTime.now()
                          : DateTime.parse(_date!);
                      final chosen = await showDatePicker(
                        context: context,
                        initialDate: initial,
                        firstDate: DateTime.now(),
                        lastDate: DateTime(2100),
                      );
                      if (chosen != null && mounted) {
                        setState(() => _date = _dateString(chosen));
                      }
                    },
              icon: const Icon(Icons.calendar_month),
              label: Text(
                _date == null ? 'Chọn ngày hẹn *' : 'Ngày hẹn: $_date',
              ),
            ),
            OutlinedButton.icon(
              onPressed: _busy || _uncertain
                  ? null
                  : () async {
                      final initial = _time == null
                          ? TimeOfDay.now()
                          : _parseTime(_time!);
                      final chosen = await showTimePicker(
                        context: context,
                        initialTime: initial,
                      );
                      if (chosen != null && mounted) {
                        setState(
                          () => _time =
                              '${chosen.hour.toString().padLeft(2, '0')}:${chosen.minute.toString().padLeft(2, '0')}',
                        );
                      }
                    },
              icon: const Icon(Icons.schedule),
              label: Text(_time == null ? 'Chọn giờ hẹn *' : 'Giờ hẹn: $_time'),
            ),
            TextFormField(
              controller: _reason,
              maxLength: 1000,
              maxLines: 3,
              decoration: const InputDecoration(
                labelText: 'Lý do',
                alignLabelWithHint: true,
              ),
              validator: (value) => (value?.length ?? 0) > 1000
                  ? 'Lý do không được dài quá 1000 ký tự.'
                  : null,
            ),
            if (_error != null) _InlineError(_error!),
            if (_uncertain)
              const Text(
                'Kết quả chưa rõ. Đóng trang và làm mới danh sách trước khi tạo hoặc sửa lại.',
                style: TextStyle(color: Colors.deepOrange),
              ),
            const SizedBox(height: 12),
            FilledButton.icon(
              onPressed: _busy || _uncertain ? null : _save,
              icon: _busy
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.save),
              label: Text(
                widget.appointment == null ? 'Tạo lịch hẹn' : 'Lưu thay đổi',
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _SignInRequired extends StatelessWidget {
  const _SignInRequired();
  @override
  Widget build(BuildContext context) =>
      const Center(child: Text('Đăng nhập để xem lịch hẹn.'));
}

class _ErrorPanel extends StatelessWidget {
  const _ErrorPanel({required this.error, this.onRetry});
  final Object error;
  final VoidCallback? onRetry;
  @override
  Widget build(BuildContext context) => Card(
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(_message(error)),
          if (error is ApiFailure &&
              (error as ApiFailure).correlationId != null)
            Text('Mã hỗ trợ: ${(error as ApiFailure).correlationId}'),
          if (onRetry != null)
            TextButton.icon(
              onPressed: onRetry,
              icon: const Icon(Icons.refresh),
              label: const Text('Làm mới'),
            ),
        ],
      ),
    ),
  );
}

class _InlineError extends StatelessWidget {
  const _InlineError(this.message);
  final String message;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 8),
    child: Text(
      message,
      style: TextStyle(color: Theme.of(context).colorScheme.error),
    ),
  );
}

String _message(Object error) => error is ApiFailure
    ? error.message
    : error is ArgumentError
    ? error.message.toString()
    : 'Không thể hoàn tất yêu cầu. Hãy kiểm tra kết nối rồi làm mới dữ liệu.';

String _appointmentStatus(String status) => switch (status) {
  'PENDING' => 'Chờ tiếp nhận',
  'ARRIVED' => 'Đã đến',
  'AWAITING_PAYMENT' => 'Chờ thanh toán',
  'READY_FOR_EXAM' => 'Sẵn sàng khám',
  'IN_EXAM' => 'Đang khám',
  'COMPLETED' => 'Đã hoàn tất',
  'CANCELLED' => 'Đã hủy',
  _ => 'Trạng thái khác',
};

String _dateString(DateTime date) =>
    '${date.year.toString().padLeft(4, '0')}-${date.month.toString().padLeft(2, '0')}-${date.day.toString().padLeft(2, '0')}';

TimeOfDay _parseTime(String value) {
  final parts = value.split(':');
  return TimeOfDay(
    hour: int.tryParse(parts.first) ?? 9,
    minute: parts.length > 1 ? int.tryParse(parts[1]) ?? 0 : 0,
  );
}

String _timeForPicker(String value) {
  final parts = value.split(':');
  if (parts.length < 2) return '09:00';
  return '${parts[0].padLeft(2, '0')}:${parts[1].padLeft(2, '0')}';
}
