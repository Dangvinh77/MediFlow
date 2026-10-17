import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/widgets/paged_list.dart';
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
  @override
  Widget build(BuildContext context) {
    final find = ref.watch(findLabTestsProvider);
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
                DropdownMenuItem(value: status, child: Text(status)),
            ],
            onChanged: (status) =>
                setState(() => _status = status == '' ? null : status),
          ),
        ),
        Expanded(
          child: PagedList<LabTest>(
            key: ValueKey(_status),
            load: (page) => find(page: page, status: _status),
            itemBuilder: (context, item) => Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      item.type,
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    Text('Ngày yêu cầu: ${item.requestedDate}'),
                    Text('Trạng thái: ${item.status}'),
                    Text('Đã thanh toán: ${item.paid ? 'Có' : 'Chưa'}'),
                    Text('Bệnh nhân: ${item.patientId}'),
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
