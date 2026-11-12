import '../../../core/l10n/app_l10n.dart';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_failure.dart';
import '../../../core/providers.dart';
import '../../../core/widgets/paged_list.dart';
import '../domain/patient_exam_result.dart';
import '../domain/record_collaboration.dart' show amendmentSectionLabels;
import 'medical_record_formatters.dart';
import 'follow_up_recommendation_panel.dart';

final patientExamResultsProvider = Provider<PatientExamResultsRepository>(
  (ref) =>
      throw StateError('Bind patient exam results in app/dependencies.dart'),
);

class MyExamResultsPage extends ConsumerWidget {
  const MyExamResultsPage({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final access = ref.watch(workspaceAccessProvider);
    if (access?.role != 'PATIENT') {
      return const Center(
        child: LText('Chức năng dành cho bệnh nhân đăng nhập.'),
      );
    }
    final repository = ref.watch(patientExamResultsProvider);
    final followUpEnabled =
        ref.watch(appConfigProvider).scheduledPaymentsEnabled &&
        ref.watch(followUpRecommendationRepositoryProvider) != null;
    return PagedList<PatientExamResult>(
      key: ValueKey((access!.sessionIdentity, repository)),
      load: ReadMyExamResults(repository).call,
      canRetry: (error) =>
          error is! ApiFailure || !const [401, 403].contains(error.status),
      itemBuilder: (context, result) => Card(
        child: ExpansionTile(
          title: LText('Khám ngày ${result.examinationDate}'),
          subtitle: LText(result.diagnoses.map((d) => d.name).join(', ')),
          expandedCrossAxisAlignment: CrossAxisAlignment.start,
          childrenPadding: const EdgeInsets.all(16),
          children: [
            if (result.effectiveCorrections.isNotEmpty) ...[
              const LText('Chỉnh sửa có hiệu lực', style: TextStyle(fontWeight: FontWeight.w600)),
              const LText('Nội dung bên dưới là giá trị đang có hiệu lực; bản gốc được giữ trong hồ sơ.'),
              for (final correction in result.effectiveCorrections) ...[
                const SizedBox(height: 8),
                LText('${amendmentSectionLabels[correction.section] ?? correction.section} · Nội dung gốc'),
                LText(correction.originalText),
                const LText('Nội dung đang có hiệu lực', style: TextStyle(fontWeight: FontWeight.w600)),
                LText(correction.correctedText),
                LText('Lý do đính chính: ${correction.reason}'),
                LText('Thời điểm áp dụng: ${formatClinicalInstant(correction.appliedAt)}'),
              ],
              const SizedBox(height: 12),
            ],
            if (result.symptoms case final symptoms?)
              LText('${result.effectiveCorrections.any((c) => c.section == 'SYMPTOMS') ? 'Triệu chứng ban đầu' : 'Triệu chứng'}: $symptoms'),
            for (final diagnosis in result.diagnoses) ...[
              const SizedBox(height: 8),
              LText('${result.effectiveCorrections.any((c) => c.section == 'DIAGNOSIS') ? 'Chẩn đoán ban đầu' : 'Chẩn đoán'}: ${diagnosis.name}'),
              if (diagnosis.icdCode case final code?) LText('Mã ICD: $code'),
              if (diagnosis.description case final description?)
                LText(description),
            ],
            const SizedBox(height: 12),
            LText('Hướng xử trí: ${_disposition(result.disposition)}'),
            if (result.dispositionNote case final note?) LText('${result.effectiveCorrections.any((c) => c.section == 'DISPOSITION_NOTE') ? 'Ghi chú hướng xử trí ban đầu: ' : ''}$note'),
            LText('Hoàn tất: ${result.completedAt}'),
            if (followUpEnabled) ...[
              const SizedBox(height: 12),
              FollowUpRecommendationPanel(
                key: ValueKey(result.id),
                recordId: result.id,
                canCreate: false,
                patientView: true,
              ),
            ],
          ],
        ),
      ),
    );
  }
}

String _disposition(String? value) => switch (value) {
  'OUTPATIENT_FOLLOW_UP' => 'Hẹn tái khám ngoại trú',
  'PRESCRIPTION' => 'Kê đơn thuốc',
  'ADMISSION' => 'Đề nghị nhập viện',
  'TRANSFER' => 'Chuyển cơ sở khác',
  'OTHER' => 'Hướng xử trí khác',
  null => 'Chưa có thông tin',
  _ => value,
};
