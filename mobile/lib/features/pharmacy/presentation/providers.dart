import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../domain/pharmacy_repository.dart';
import '../domain/pharmacy_identity.dart';
import '../domain/prescription_commands.dart';
export '../domain/pharmacy_mode.dart';
import '../domain/pharmacy_mode.dart';

// Composition root watches auth and replaces identity on every session change.
// The identity is opaque: never use a bearer token as a widget key.
class PharmacyAccess {
  const PharmacyAccess({required this.role, required this.sessionIdentity});
  final String role;
  final Object sessionIdentity;
  bool get canRead => const {'ADMIN', 'DOCTOR', 'PHARMACIST'}.contains(role);
  bool get canManageDrugs => const {'ADMIN', 'PHARMACIST'}.contains(role);
  bool get canPrescribe => const {'ADMIN', 'DOCTOR'}.contains(role);
}

// Explicit composition input; never inferred from a prescription GET response.
final pharmacyModeProvider = Provider<PharmacyMode>(
  (ref) => PharmacyMode.unavailable,
);

final pharmacyAccessProvider = Provider<PharmacyAccess?>((ref) => null);
final pharmacyRepositoryProvider = Provider<PharmacyRepository?>((ref) => null);

// Shared among mounted consumers, disposed on navigation/session change. No global PII cache.
final pharmacyIdentityNameProvider = FutureProvider.autoDispose
    .family<String, (PharmacyIdentityKind, String)>((ref, identity) async {
      final access = ref.watch(pharmacyAccessProvider);
      final repository = ref.watch(pharmacyRepositoryProvider);
      if (access == null || !access.canPrescribe) {
        throw StateError('Tài khoản chưa được cấp quyền tra cứu tên');
      }
      if (repository is! PharmacyIdentityReader) {
        throw StateError('Chưa kết nối nguồn tra cứu tên');
      }
      await Future<void>.delayed(const Duration(milliseconds: 300));
      if (!ref.mounted) throw StateError('Phiên tra cứu đã thay đổi');
      return (repository as PharmacyIdentityReader).identityName(
        identity.$1,
        identity.$2,
      );
    }, retry: (_, _) => null);
// Feature defaults fail closed; the app root binds verified transport by mode.
final outpatientPrescriptionWriterProvider =
    Provider<OutpatientPrescriptionWriter?>((ref) => null);
final prescriptionDispenserProvider = Provider<PrescriptionDispenser?>(
  (ref) => null,
);
