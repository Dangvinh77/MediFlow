import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/medical_colors.dart';
import '../../../core/widgets/medical_ui.dart';
import '../../../core/widgets/paged_list.dart';
import '../../../core/providers.dart';
import '../../../core/widgets/reference_widgets.dart';
import 'lab_detail_page.dart';
import 'lab_labels.dart';
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
  @override
  Widget build(BuildContext context) {
    final find = ref.watch(findLabTestsProvider);
    final access = ref.watch(workspaceAccessProvider);
    if (_identity != access?.sessionIdentity) {
      _identity = access?.sessionIdentity;
      _status = access?.role == 'LAB_TECH' ? 'READY' : null;
    }
    return Column(
      children: [
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
