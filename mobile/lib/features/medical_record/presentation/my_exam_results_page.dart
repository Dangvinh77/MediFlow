import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_failure.dart';
import '../../../core/providers.dart';
import '../../../core/widgets/paged_list.dart';
import '../domain/patient_exam_result.dart';
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
        child: Text('Chức năng dành cho bệnh nhân đăng nhập.'),
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
          title: Text('Khám ngày ${result.examinationDate}'),
          subtitle: Text(result.diagnoses.map((d) => d.name).join(', ')),
          expandedCrossAxisAlignment: CrossAxisAlignment.start,
          childrenPadding: const EdgeInsets.all(16),
          children: [
            if (result.symptoms case final symptoms?)
              Text('Triệu chứng: $symptoms'),
            for (final diagnosis in result.diagnoses) ...[
              const SizedBox(height: 8),
              Text('Chẩn đoán: ${diagnosis.name}'),
              if (diagnosis.icdCode case final code?) Text('Mã ICD: $code'),
              if (diagnosis.description case final description?)
                Text(description),
            ],
            const SizedBox(height: 12),
            Text('Hướng xử trí: ${_disposition(result.disposition)}'),
            if (result.dispositionNote case final note?) Text(note),
            Text('Hoàn tất: ${result.completedAt}'),
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
