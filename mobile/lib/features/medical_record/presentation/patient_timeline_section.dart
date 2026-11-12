import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mediflow_loader/mediflow_loader.dart';

import '../../../core/network/api_failure.dart';
import '../../../core/widgets/medical_ui.dart';
import '../domain/patient_timeline.dart';

final patientTimelineRepositoryProvider =
    Provider<PatientTimelineRepository?>((ref) => null);

class PatientTimelineSection extends ConsumerStatefulWidget {
  const PatientTimelineSection({
    super.key,
    required this.patientId,
    this.onOpenRecord,
    this.errorBuilder,
  });
  final String patientId;
  final ValueChanged<TimelineEntry>? onOpenRecord;
  final Widget Function(Object error)? errorBuilder;

  @override
  ConsumerState<PatientTimelineSection> createState() =>
      _PatientTimelineSectionState();
}

class _PatientTimelineSectionState extends ConsumerState<PatientTimelineSection> {
  TimelinePage? _result;
  Object? _error;
  bool _loading = true;
  int _page = 0, _generation = 0;
  String? _departmentId, _status;

  @override
  void initState() {
    super.initState();
    Future.microtask(_load);
  }

  @override
  void didUpdateWidget(covariant PatientTimelineSection oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.patientId != oldWidget.patientId) {
      _result = null;
      _error = null;
      _page = 0;
      _departmentId = null;
      _status = null;
      _loading = true;
      Future.microtask(_load);
    }
  }

  Future<void> _load({int? page}) async {
    final repository = ref.read(patientTimelineRepositoryProvider);
    if (repository == null) {
      setState(() {
        _error = StateError('Không có kết nối dòng thời gian.');
        _result = null;
        _loading = false;
      });
      return;
    }
    final generation = ++_generation;
    final patientId = widget.patientId;
    final targetPage = page ?? _page;
    setState(() {
      _loading = true;
      _error = null;
      _result = null;
      _page = targetPage;
    });
    try {
      final result = await repository.timeline(
        patientId,
        page: targetPage,
        size: 20,
        departmentId: _departmentId,
        status: _status,
      );
      if (mounted &&
          generation == _generation &&
          patientId == widget.patientId) {
        setState(() {
          _result = result;
          _loading = false;
        });
      }
    } catch (error) {
      if (mounted &&
          generation == _generation &&
          patientId == widget.patientId) {
        setState(() {
          _error = error;
          _loading = false;
        });
      }
    }
  }

  Future<void> _changeFilter({String? departmentId, String? status}) async {
    setState(() {
      if (departmentId != null) {
        _departmentId = departmentId.isEmpty ? null : departmentId;
      }
      if (status != null) _status = status == 'ALL' ? null : status;
      _page = 0;
    });
    await _load(page: 0);
  }

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      Padding(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
        child: Text(
          'Dòng thời gian lượt khám',
          style: Theme.of(context).textTheme.titleMedium,
        ),
      ),
      if (_loading)
        const Expanded(child: Center(child: MediFlowLoader()))
      else if (_error != null)
        Expanded(child: widget.errorBuilder?.call(_error!) ?? _failure())
      else if (_result case final result?)
        Expanded(child: _timeline(result)),
    ],
  );

  Widget _failure() {
    if (_error is ApiFailure && (_error as ApiFailure).status == 403) {
      return const Center(
        child: Padding(
          padding: EdgeInsets.all(20),
          child: Text('Bạn không có quyền xem hồ sơ của bệnh nhân này.'),
        ),
      );
    }
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('Chưa tải được dòng thời gian. Hãy thử lại.'),
            TextButton.icon(
              key: const Key('timeline-retry'),
              onPressed: _load,
              icon: const Icon(Icons.refresh),
              label: const Text('Thử lại'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _timeline(TimelinePage result) {
    if (result.entries.isEmpty) {
      return const Center(child: Text('Chưa có lượt khám.'));
    }
    final departments = result.entries.map((entry) => entry.departmentId).toSet();
    return ListView(
      key: const Key('patient-timeline'),
      padding: const EdgeInsets.fromLTRB(16, 4, 16, 16),
      children: [
        Wrap(
          spacing: 12,
          runSpacing: 8,
          children: [
            DropdownButton<String>(
              key: const Key('timeline-department-filter'),
              value: _departmentId ?? '',
              items: [
                const DropdownMenuItem(value: '', child: Text('Tất cả khoa')),
                for (final departmentId in departments)
                  DropdownMenuItem(value: departmentId, child: Text(departmentId)),
              ],
              onChanged: (value) => _changeFilter(departmentId: value ?? ''),
            ),
            DropdownButton<String>(
              key: const Key('timeline-status-filter'),
              value: _status ?? 'ALL',
              items: const [
                DropdownMenuItem(value: 'ALL', child: Text('Mọi trạng thái')),
                DropdownMenuItem(value: 'OPEN', child: Text('Đang khám')),
                DropdownMenuItem(value: 'COMPLETED', child: Text('Đã hoàn tất khám')),
                DropdownMenuItem(
                  value: 'CLOSED_WITHOUT_CONCLUSION',
                  child: Text('Kết thúc không kết luận'),
                ),
              ],
              onChanged: (value) => _changeFilter(status: value ?? 'ALL'),
            ),
          ],
        ),
        for (final entry in result.entries)
          Card(
            child: ListTile(
              key: ValueKey(entry.recordId),
              title: Text('Ngày khám: ${entry.examinationDate}'),
              subtitle: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Khoa: ${entry.departmentId}'),
                  Text('Bác sĩ mở hồ sơ: ${entry.doctorId}'),
                  if (entry.responsibleDoctorId != null &&
                      entry.responsibleDoctorId != entry.doctorId)
                    Text('Bác sĩ phụ trách: ${entry.responsibleDoctorId}'),
                  Text(entry.visitKindLabel),
                  if (entry.previousRecordId != null)
                    const Text('Lượt trước — chỉ tham khảo'),
                ],
              ),
              trailing: StatusPill(
                _statusLabel(entry.status),
                tone: statusToneOf(entry.status),
              ),
              onTap: widget.onOpenRecord == null
                  ? null
                  : () => widget.onOpenRecord!(entry),
            ),
          ),
        if (result.totalPages > 1)
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text('Trang ${result.page + 1} / ${result.totalPages}'),
              TextButton(
                key: const Key('timeline-next-page'),
                onPressed: _loading || !result.hasNext
                    ? null
                    : () => _load(page: _page + 1),
                child: const Text('Trang tiếp'),
              ),
            ],
          ),
      ],
    );
  }

  String _statusLabel(String status) => switch (status) {
    'OPEN' => 'Đang khám',
    'COMPLETED' => 'Đã hoàn tất khám',
    'CLOSED_WITHOUT_CONCLUSION' => 'Kết thúc không kết luận',
    _ => status,
  };
}
