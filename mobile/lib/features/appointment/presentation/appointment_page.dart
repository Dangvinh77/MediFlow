import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/widgets/paged_list.dart';
import '../domain/appointment.dart';

final appointmentRepositoryProvider = Provider<AppointmentRepository>(
  (ref) => throw StateError(
    'AppointmentRepository must be bound in app/dependencies.dart',
  ),
);
final findAppointmentsProvider = Provider(
  (ref) => FindAppointments(ref.watch(appointmentRepositoryProvider)),
);

class AppointmentPage extends ConsumerStatefulWidget {
  const AppointmentPage({super.key});
  @override
  ConsumerState<AppointmentPage> createState() => _AppointmentPageState();
}

class _AppointmentPageState extends ConsumerState<AppointmentPage> {
  String? _date;
  @override
  Widget build(BuildContext context) {
    final find = ref.watch(findAppointmentsProvider);
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.all(12),
          child: Wrap(
            spacing: 12,
            children: [
              OutlinedButton.icon(
                onPressed: () async {
                  final date = await showDatePicker(
                    context: context,
                    initialDate: DateTime.now(),
                    firstDate: DateTime(2000),
                    lastDate: DateTime(2100),
                  );
                  if (date != null && mounted) {
                    setState(
                      () => _date =
                          '${date.year}-${date.month.toString().padLeft(2, '0')}-${date.day.toString().padLeft(2, '0')}',
                    );
                  }
                },
                icon: const Icon(Icons.calendar_month),
                label: Text(_date ?? 'Chọn ngày hẹn'),
              ),
              TextButton(
                onPressed: _date == null
                    ? null
                    : () => setState(() => _date = null),
                child: const Text('Tất cả'),
              ),
            ],
          ),
        ),
        Expanded(
          child: PagedList<Appointment>(
            key: ValueKey(_date),
            load: (page) => find(page: page, date: _date),
            itemBuilder: (context, item) => Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '${item.date} · ${item.time}',
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    const SizedBox(height: 8),
                    Text(item.status),
                    Text('Bệnh nhân: ${item.patientId}'),
                    Text('Bác sĩ: ${item.doctorId}'),
                    if (item.reason != null) Text(item.reason!),
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
