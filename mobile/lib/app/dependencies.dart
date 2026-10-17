import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../core/providers.dart';
import '../features/auth/data/gateway_auth_repository.dart';
import '../features/auth/presentation/providers.dart';
import '../features/appointment/data/gateway_appointment_repository.dart';
import '../features/appointment/presentation/appointment_page.dart';
import '../features/lab/data/gateway_lab_repository.dart';
import '../features/lab/presentation/lab_page.dart';

// Composition root is the only place that binds data adapters to domain ports.
final appOverrides = [
  authRepositoryProvider.overrideWith(
    (ref) => GatewayAuthRepository(
      ref.watch(apiClientProvider),
      const FlutterSecureStorage(),
      gateway: ref.watch(appConfigProvider).baseUrl,
    ),
  ),
  appointmentRepositoryProvider.overrideWith(
    (ref) => GatewayAppointmentRepository(ref.watch(apiClientProvider)),
  ),
  labRepositoryProvider.overrideWith(
    (ref) => GatewayLabRepository(ref.watch(apiClientProvider)),
  ),
];
