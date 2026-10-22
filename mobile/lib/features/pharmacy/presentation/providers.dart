import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../domain/pharmacy_repository.dart';
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
// Feature defaults fail closed; the app root binds verified transport by mode.
final outpatientPrescriptionWriterProvider =
    Provider<OutpatientPrescriptionWriter?>((ref) => null);
final prescriptionDispenserProvider = Provider<PrescriptionDispenser?>(
  (ref) => null,
);
