import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../domain/record_collaboration.dart';
import 'medical_record_formatters.dart';
import 'record_collaboration_section.dart';

/// Treatment courses of the patient, opened from or extended by this visit.
/// Hidden entirely when the server feature is off (404).
class TreatmentCoursesSection extends ConsumerStatefulWidget {
  const TreatmentCoursesSection({
    super.key,
    required this.patientId,
    required this.recordId,
    this.canWrite = true,
  });

  final String patientId, recordId;
  final bool canWrite;

  @override
  ConsumerState<TreatmentCoursesSection> createState() =>
      _TreatmentCoursesSectionState();
}

class _TreatmentCoursesSectionState
    extends ConsumerState<TreatmentCoursesSection> {
  bool _showOpenForm = false;
  String? _closingCourseId;

  @override
  Widget build(BuildContext context) {
    final repository = ref.watch(recordCollaborationRepositoryProvider);
    if (repository == null) return const SizedBox.shrink();
    return LoadPanel<TreatmentCourse>(
      id: 'courses',
      title: 'Đợt điều trị',
      noun: 'đợt điều trị',
      deniedText: 'Bạn không có quyền xem đợt điều trị của bệnh nhân này.',
      emptyText: 'Chưa có đợt điều trị.',
      load: () => repository.courses(widget.patientId),
      itemBuilder: (context, course, reload) =>
          _courseTile(context, repository, course, reload),
      footer: (context, reload) => !widget.canWrite
          ? const SizedBox.shrink()
          : _showOpenForm
          ? CommandForm(
              prefix: 'course-open',
              submitLabel: 'Mở đợt điều trị',
              fields: const [
                CommandField('problem', 'Vấn đề điều trị *', maxLength: 500),
                CommandField(
                  'openingReason',
                  'Lý do mở đợt *',
                  maxLength: 1000,
                  maxLines: 2,
                ),
              ],
              validate: (values) {
                if (values['problem']!.trim().isEmpty) {
                  return 'Nhập vấn đề điều trị.';
                }
                if (values['openingReason']!.trim().isEmpty) {
                  return 'Nhập lý do mở đợt điều trị.';
                }
                return null;
              },
              onSubmit: (values, key) => repository.openCourse(
                patientId: widget.patientId,
                problem: values['problem']!,
                openingReason: values['openingReason']!,
                recordId: widget.recordId,
                idempotencyKey: key,
              ),
              onDone: () {
                setState(() => _showOpenForm = false);
                reload();
              },
              onCancel: () => setState(() => _showOpenForm = false),
            )
          : Align(
              alignment: Alignment.centerLeft,
              child: OutlinedButton(
                key: const Key('toggle-course-open'),
                onPressed: () => setState(() => _showOpenForm = true),
                child: const Text('Mở đợt điều trị từ lượt khám này'),
              ),
            ),
    );
  }

  Widget _courseTile(
    BuildContext context,
    RecordCollaborationRepository repository,
    TreatmentCourse course,
    VoidCallback reload,
  ) {
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(course.problem, style: theme.textTheme.titleSmall),
        Text(
          '${course.isOpen ? 'Đang theo dõi' : 'Đã kết thúc'} · '
          '${course.recordIds.length} lượt khám · '
          'Lượt gần nhất ${course.lastVisitDate}',
          style: theme.textTheme.bodySmall,
        ),
        if (course.isOpen && course.reviewDue)
          Text(
            'Cần rà soát',
            style: TextStyle(color: theme.colorScheme.error),
          ),
        Text(course.openingReason),
        if (!course.isOpen) ...[
          Text(
            'Kết luận: ${treatmentCourseClosureLabels[course.closureDecision] ?? course.closureDecision ?? ''}',
          ),
          if (course.closureNote?.trim().isNotEmpty == true)
            Text(course.closureNote!),
          if (course.closedAt != null)
            Text(
              'Kết thúc lúc ${formatClinicalInstant(course.closedAt!)}',
              style: theme.textTheme.bodySmall,
            ),
        ],
        if (widget.canWrite && course.isOpen) ...[
          if (!course.recordIds.contains(widget.recordId))
            CommandForm(
              prefix: 'attach-${course.courseId}',
              submitKey: Key('course-attach-${course.courseId}'),
              submitLabel: 'Thêm lượt khám này vào đợt',
              fields: const [],
              validate: (_) => null,
              onSubmit: (_, key) => repository.attachVisit(
                courseId: course.courseId,
                recordId: widget.recordId,
                idempotencyKey: key,
              ),
              onDone: reload,
            ),
          if (_closingCourseId == course.courseId)
            CommandForm(
              prefix: 'course-close',
              submitLabel: 'Kết thúc đợt điều trị',
              fields: const [
                CommandField(
                  'decision',
                  'Kết luận *',
                  choices: treatmentCourseClosureLabels,
                ),
                CommandField(
                  'note',
                  'Ghi chú kết thúc *',
                  maxLength: 1000,
                  maxLines: 2,
                ),
              ],
              validate: (values) {
                if (values['decision']!.isEmpty) {
                  return 'Chọn kết luận của đợt điều trị.';
                }
                if (values['note']!.trim().isEmpty) {
                  return 'Nhập ghi chú kết thúc đợt điều trị.';
                }
                return null;
              },
              onSubmit: (values, key) => repository.closeCourse(
                courseId: course.courseId,
                decision: values['decision']!,
                note: values['note']!,
                idempotencyKey: key,
              ),
              onDone: () {
                setState(() => _closingCourseId = null);
                reload();
              },
              onCancel: () => setState(() => _closingCourseId = null),
            )
          else
            Align(
              alignment: Alignment.centerLeft,
              child: TextButton(
                key: Key('course-close-${course.courseId}'),
                onPressed: () =>
                    setState(() => _closingCourseId = course.courseId),
                child: const Text('Kết thúc đợt điều trị'),
              ),
            ),
        ],
      ],
    );
  }
}
