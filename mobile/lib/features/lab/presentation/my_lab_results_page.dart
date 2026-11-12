import '../../../core/l10n/app_l10n.dart';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_failure.dart';
import '../../../core/providers.dart';
import '../../../core/widgets/paged_list.dart';
import '../domain/patient_lab_result.dart';

final patientLabResultsProvider = Provider<PatientLabResultsRepository>(
  (ref) =>
      throw StateError('Bind patient lab results in app/dependencies.dart'),
);

class MyLabResultsPage extends ConsumerWidget {
  const MyLabResultsPage({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final access = ref.watch(workspaceAccessProvider);
    if (access?.role != 'PATIENT') {
      return const Center(
        child: LText('Chức năng dành cho bệnh nhân đăng nhập.'),
      );
    }
    final repository = ref.watch(patientLabResultsProvider);
    return PagedList<PatientLabResult>(
      key: ValueKey((access!.sessionIdentity, repository)),
      load: ReadMyLabResults(repository).call,
      canRetry: (error) =>
          error is! ApiFailure || !const [401, 403].contains(error.status),
      itemBuilder: (context, result) => Card(
        child: ExpansionTile(
          title: LText(result.labType),
          subtitle: LText('Thực hiện: ${result.performedDate}'),
          expandedCrossAxisAlignment: CrossAxisAlignment.start,
          childrenPadding: const EdgeInsets.all(16),
          children: [
            LText('Ngày yêu cầu: ${result.requestedDate}'),
            for (final value in result.results)
              ListTile(
                contentPadding: EdgeInsets.zero,
                title: LText(value.indicator),
                subtitle: LText(
                  [
                    'Kết quả: ${value.value}${value.unit == null ? '' : ' ${value.unit}'}',
                    if (value.referenceRange != null)
                      'Khoảng tham chiếu: ${value.referenceRange}',
                  ].join('\n'),
                ),
              ),
            if (result.conclusion case final conclusion?)
              LText('Kết luận: $conclusion'),
          ],
        ),
      ),
    );
  }
}
