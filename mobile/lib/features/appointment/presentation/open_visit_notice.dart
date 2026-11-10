import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/widgets/medical_ui.dart';
import '../../../core/widgets/reference_widgets.dart';
import '../domain/open_visit.dart';

final openVisitRepositoryProvider = Provider<OpenVisitRepository?>(
  (ref) => null,
);

/// Warns reception about unfinished examinations. A failed check never blocks:
/// the server decides on submit.
class OpenVisitNotice extends StatefulWidget {
  const OpenVisitNotice({
    super.key,
    required this.repository,
    required this.patientId,
    required this.departmentId,
    required this.controller,
    required this.onSameDepartmentChanged,
    this.forceReason = false,
    this.enabled = true,
  });
  final OpenVisitRepository repository;
  final String patientId, departmentId;
  final TextEditingController controller;
  final ValueChanged<bool> onSameDepartmentChanged;
  final bool forceReason, enabled;
  @override
  State<OpenVisitNotice> createState() => _OpenVisitNoticeState();
}

class _OpenVisitNoticeState extends State<OpenVisitNotice> {
  List<OpenVisit> _visits = const [];
  bool _failed = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final visits = await widget.repository.openVisits(widget.patientId);
      if (!mounted) return;
      setState(() => _visits = visits);
      widget.onSameDepartmentChanged(_sameDepartment);
    } catch (_) {
      if (mounted) setState(() => _failed = true);
    }
  }

  bool get _sameDepartment =>
      _visits.any((visit) => visit.departmentId == widget.departmentId);

  @override
  Widget build(BuildContext context) {
    final askReason = _sameDepartment || widget.forceReason;
    if (!askReason && !_failed && _visits.isEmpty) {
      return const SizedBox.shrink();
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (_failed)
          const NoticeBox(
            'Không kiểm tra được lượt khám đang mở của bệnh nhân. Vẫn có thể đăng ký, hệ thống sẽ kiểm tra khi gửi.',
          ),
        if (_visits.isNotEmpty) ...[
          NoticeBox('Bệnh nhân còn ${_visits.length} lượt khám chưa kết thúc.'),
          for (final visit in _visits)
            ReferenceLabel(
              kind: ReferenceKind.department,
              id: visit.departmentId,
              label: visit.departmentId == widget.departmentId
                  ? 'Cùng khoa · ${visit.sessionDate}'
                  : visit.sessionDate,
            ),
        ],
        if (askReason)
          TextField(
            key: const Key('reception-independent-reason'),
            controller: widget.controller,
            enabled: widget.enabled,
            maxLength: 500,
            decoration: const InputDecoration(
              labelText: 'Lý do đăng ký thêm lượt trong khoa này',
            ),
          ),
      ],
    );
  }
}

/// Examinations the doctor started and has not ended, including earlier days.
class DoctorOpenVisitsSection extends ConsumerStatefulWidget {
  const DoctorOpenVisitsSection({super.key, required this.onOpen});
  final ValueChanged<OpenVisit> onOpen;
  @override
  ConsumerState<DoctorOpenVisitsSection> createState() =>
      _DoctorOpenVisitsSectionState();
}

class _DoctorOpenVisitsSectionState
    extends ConsumerState<DoctorOpenVisitsSection> {
  List<OpenVisit>? _visits;
  bool _failed = false;

  @override
  void initState() {
    super.initState();
    Future.microtask(_load);
  }

  Future<void> _load() async {
    final repository = ref.read(openVisitRepositoryProvider);
    if (repository == null) return;
    if (mounted) setState(() => _failed = false);
    try {
      final visits = await repository.myOpenVisits();
      if (mounted) setState(() => _visits = visits);
    } catch (_) {
      if (mounted) setState(() => _failed = true);
    }
  }

  @override
  Widget build(BuildContext context) {
    final visits = _visits;
    if (_failed) {
      return Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12),
        child: Row(
          children: [
            const Expanded(
              child: Text('Không tải được lượt khám chưa kết thúc.'),
            ),
            TextButton(onPressed: _load, child: const Text('Thử lại')),
          ],
        ),
      );
    }
    if (visits == null || visits.isEmpty) return const SizedBox.shrink();
    return ExpansionTile(
      initiallyExpanded: true,
      title: Text('Lượt khám chưa kết thúc (${visits.length})'),
      children: [
        ConstrainedBox(
          constraints: const BoxConstraints(maxHeight: 200),
          child: ListView(
            shrinkWrap: true,
            children: [
              for (final visit in visits)
                ListTile(
                  title: Text(visit.sessionDate),
                  subtitle: ReferenceLabel(
                    kind: ReferenceKind.department,
                    id: visit.departmentId,
                    label: 'Khoa',
                  ),
                  trailing: TextButton(
                    onPressed: () => widget.onOpen(visit),
                    child: const Text('Tiếp tục khám'),
                  ),
                ),
            ],
          ),
        ),
      ],
    );
  }
}
