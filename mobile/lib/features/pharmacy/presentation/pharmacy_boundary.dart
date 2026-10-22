import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../domain/pharmacy_repository.dart';
import 'providers.dart';

class PharmacyBoundary extends ConsumerWidget {
  const PharmacyBoundary({
    super.key,
    required this.builder,
    this.manageDrugs = false,
  });
  final Widget Function(PharmacyRepository) builder;
  final bool manageDrugs;
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final access = ref.watch(pharmacyAccessProvider);
    final repository = ref.watch(pharmacyRepositoryProvider);
    final mode = ref.watch(pharmacyModeProvider);
    final writer = ref.watch(outpatientPrescriptionWriterProvider);
    final dispenser = ref.watch(prescriptionDispenserProvider);
    if (access == null || repository == null) {
      return const Center(
        child: Padding(
          padding: EdgeInsets.all(24),
          child: Text('Chức năng Dược chưa sẵn sàng trên ứng dụng này.'),
        ),
      );
    }
    if (!access.canRead || (manageDrugs && !access.canManageDrugs)) {
      return Center(
        child: Text(
          manageDrugs
              ? 'Bạn không có quyền thực hiện thao tác Dược này.'
              : 'Bạn không có quyền xem Dược.',
        ),
      );
    }
    return KeyedSubtree(
      key: ValueKey((
        access.sessionIdentity,
        access.role,
        repository,
        mode,
        writer,
        dispenser,
      )),
      child: builder(repository),
    );
  }
}
