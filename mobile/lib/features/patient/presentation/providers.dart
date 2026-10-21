import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../domain/patient.dart';

final patientRepositoryProvider = Provider<PatientRepository>(
  (ref) => throw StateError(
    'PatientRepository must be bound in app/dependencies.dart',
  ),
);
