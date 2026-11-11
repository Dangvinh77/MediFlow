import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/medical_colors.dart';
import '../../../core/widgets/medical_ui.dart';
import '../../../core/widgets/paged_list.dart';
import '../../../core/providers.dart';
import '../../../core/widgets/reference_widgets.dart';
import 'lab_detail_page.dart';
import 'lab_labels.dart';
import 'lab_review_page.dart';
import '../domain/lab_test.dart';

final labRepositoryProvider = Provider<LabRepository>(
  (ref) =>
      throw StateError('LabRepository must be bound in app/dependencies.dart'),
);
final findLabTestsProvider = Provider(
  (ref) => FindLabTests(ref.watch(labRepositoryProvider)),
);

class LabPage extends ConsumerStatefulWidget {
  const LabPage({super.key});
  @override
  ConsumerState<LabPage> createState() => _LabPageState();
}

class _LabPageState extends ConsumerState<LabPage> {
  String? _status;
  Object? _identity;
  void _open(BuildContext context, String title, Widget page) {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => Scaffold(
          appBar: AppBar(title: Text(title)),
          body: page,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final find = ref.watch(findLabTestsProvider);
    final access = ref.watch(workspaceAccessProvider);
    if (_identity != access?.sessionIdentity) {
      _identity = access?.sessionIdentity;
      _status = access?.role == 'LAB_TECH' ? 'READY' : null;
    }
    final role = access?.role;
    final canWorklist = role == 'LAB_TECH' || role == 'ADMIN';
    final canCritical = role == 'LAB_TECH' || role == 'DOCTOR';
    return Column(
      children: [
        if (canWorklist || canCritical)
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
            child: Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                if (canWorklist)
                  OutlinedButton.icon(
                    onPressed: () => _open(
                      context,
                      'Danh sách việc xét nghiệm',
                      const LabWorklistPage(),
                    ),
                    icon: const Icon(Icons.checklist),
                    label: const Text('Danh sách việc'),
                  ),
                if (canCritical)
                  OutlinedButton.icon(
                    onPressed: () => _open(
                      context,
                      'Cảnh báo kết quả nguy cấp',
                      const CriticalNoticesPage(),
                    ),
                    icon: const Icon(Icons.warning_amber_rounded),
                    label: const Text('Cảnh báo nguy cấp'),
                  ),
              ],
            ),
          ),
        Padding(
          padding: const EdgeInsets.all(16),
          child: DropdownButtonFormField<String>(
            initialValue: _status ?? '',
            decoration: const InputDecoration(labelText: 'Trạng thái'),
            items: [
              const DropdownMenuItem(value: '', child: Text('Tất cả')),
              for (final status in FindLabTests.statuses)
                DropdownMenuItem(
                  value: status,
                  child: Text(labStatusLabel(status)),
                ),
            ],
            onChanged: (status) =>
                setState(() => _status = status == '' ? null : status),
          ),
        ),
        Expanded(
          child: PagedList<LabTest>(
            key: ValueKey((_status, access?.sessionIdentity)),
            load: (page) => find(page: page, status: _status),
            itemBuilder: (context, item) => Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      spacing: 12,
                      children: [
                        const IconBadge(
                          Icons.science_outlined,
                          color: Wayfinding.lab,
                        ),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                item.type,
                                style: Theme.of(context).textTheme.titleMedium,
                              ),
                              Text(
                                'Ngày yêu cầu: ${labDateLabel(item.requestedDate)}',
                                style: Theme.of(context).textTheme.bodySmall
                                    ?.copyWith(
                                      color: context.medical.mutedText,
                                    ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 10),
                    StatusPill(
                      labStatusLabel(item.status),
                      tone: statusToneOf(item.status),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      'Xem chi tiết để kiểm tra đối soát viện phí',
                      style: Theme.of(context).textTheme.bodySmall
                          ?.copyWith(color: context.medical.mutedText),
                    ),
                    ReferenceLabel(
                      kind: ReferenceKind.patient,
                      id: item.patientId,
                      label: 'Bệnh nhân',
                    ),
                    if (access != null)
                      TextButton.icon(
                        onPressed: () => Navigator.of(context).push(
                          MaterialPageRoute<void>(
                            builder: (_) => LabDetailPage(id: item.id),
                          ),
                        ),
                        icon: const Icon(Icons.chevron_right),
                        label: const Text('Xem chi tiết'),
                      ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }
}
