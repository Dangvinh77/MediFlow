import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../domain/pharmacy_repository.dart';

// Composition root watches auth and replaces identity on every session change.
// The identity is opaque: never use a bearer token as a widget key.
class PharmacyAccess {
  const PharmacyAccess({required this.role, required this.sessionIdentity});
  final String role;
  final Object sessionIdentity;
  bool get canRead => const {'ADMIN', 'DOCTOR', 'PHARMACIST'}.contains(role);
}

final pharmacyAccessProvider = Provider<PharmacyAccess?>((ref) => null);
final pharmacyRepositoryProvider = Provider<PharmacyRepository?>((ref) => null);
