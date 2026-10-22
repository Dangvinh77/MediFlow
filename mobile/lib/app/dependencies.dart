import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/providers.dart';
import '../core/domain/workspace_access.dart';
import '../features/auth/data/gateway_auth_repository.dart';
import '../features/auth/presentation/session_controller.dart';
import '../features/medical_record/data/gateway_medical_record_repository.dart';
import '../features/medical_record/presentation/medical_record_page.dart';
import '../features/appointment/data/gateway_appointment_repository.dart';
import '../features/appointment/presentation/appointment_page.dart';
import '../features/lab/data/gateway_lab_repository.dart';
import '../features/lab/presentation/lab_page.dart';
import '../features/lab/presentation/lab_detail_page.dart';
import '../features/lab/data/gateway_lab_workflow.dart';
import '../features/report/data/gateway_report_repository.dart';
import '../features/report/presentation/report_page.dart';
import '../features/patient/data/gateway_patient_repository.dart';
import '../features/patient/presentation/providers.dart';
import '../features/organization/data/gateway_organization_repository.dart';
import '../features/organization/presentation/providers.dart';
import '../features/billing/data/gateway_billing_repository.dart';
import '../features/billing/presentation/providers.dart';
import '../features/notification/data/gateway_notification_repository.dart';
import '../features/notification/presentation/providers.dart';
import '../features/pharmacy/data/gateway_pharmacy_repository.dart';
import '../features/pharmacy/presentation/providers.dart';

final _pharmacyAdapterProvider = Provider(
  (ref) => GatewayPharmacyRepository(ref.watch(apiClientProvider)),
);

// Composition root is the only place that binds data adapters to domain ports.
final appOverrides = [
  medicalRecordRepositoryProvider.overrideWith(
    (ref) => GatewayMedicalRecordRepository(ref.watch(apiClientProvider)),
  ),
  reportRepositoryProvider.overrideWith(
    (ref) => GatewayReportRepository(ref.watch(apiClientProvider)),
  ),
  labWorkflowProvider.overrideWith(
    (ref) => GatewayLabWorkflow(ref.watch(apiClientProvider)),
  ),
  pharmacyRepositoryProvider.overrideWith(
    (ref) => ref.watch(_pharmacyAdapterProvider),
  ),
  pharmacyAccessProvider.overrideWith((ref) {
    final session = ref.watch(sessionProvider).asData?.value;
    return session == null
        ? null
        : PharmacyAccess(role: session.role.wire, sessionIdentity: Object());
  }),
  pharmacyModeProvider.overrideWith(
    (ref) => switch (ref.watch(appConfigProvider).pharmacyMode) {
      'outpatient' => PharmacyMode.outpatient,
      'legacy' => PharmacyMode.legacy,
      _ => PharmacyMode.unavailable,
    },
  ),
  outpatientPrescriptionWriterProvider.overrideWith(
    (ref) => ref.watch(pharmacyModeProvider) == PharmacyMode.outpatient
        ? ref.watch(_pharmacyAdapterProvider)
        : null,
  ),
  prescriptionDispenserProvider.overrideWith(
    (ref) => ref.watch(pharmacyModeProvider) == PharmacyMode.unavailable
        ? null
        : ref.watch(_pharmacyAdapterProvider),
  ),
  workspaceAccessProvider.overrideWith((ref) {
    final session = ref.watch(sessionProvider).asData?.value;
    if (session == null) return null;
    return WorkspaceAccess(
      sessionIdentity: Object(),
      role: session.role.wire,
      patientId: session.patientId,
      staffId: session.staffId,
    );
  }),
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
  patientRepositoryProvider.overrideWith(
    (ref) => GatewayPatientRepository(ref.watch(apiClientProvider)),
  ),
  organizationRepositoryProvider.overrideWith(
    (ref) => GatewayOrganizationRepository(ref.watch(apiClientProvider)),
  ),
  billingRepositoryProvider.overrideWith(
    (ref) => GatewayBillingRepository(ref.watch(apiClientProvider)),
  ),
  notificationRepositoryProvider.overrideWith(
    (ref) => GatewayNotificationRepository(ref.watch(apiClientProvider)),
  ),
];
