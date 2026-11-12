import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/providers.dart';
import '../core/domain/workspace_access.dart';
import '../features/auth/data/gateway_auth_repository.dart';
import '../features/auth/data/gateway_account_recovery.dart';
import '../features/auth/presentation/account_recovery_page.dart';
import '../features/auth/data/gateway_password_repository.dart';
import '../features/auth/presentation/change_password_page.dart';
import '../features/auth/presentation/session_controller.dart';
import '../features/medical_record/data/gateway_medical_record_repository.dart';
import '../features/medical_record/presentation/medical_record_page.dart';
import '../features/appointment/data/gateway_appointment_repository.dart';
import '../features/appointment/presentation/appointment_page.dart';
import '../features/appointment/data/gateway_reception_repository.dart';
import '../features/appointment/data/gateway_clinic_queue_repository.dart';
import '../features/appointment/presentation/clinic_queue_page.dart';
import '../features/appointment/presentation/reception_page.dart';
import '../features/lab/data/gateway_lab_repository.dart';
import '../features/lab/data/gateway_patient_lab_results.dart';
import '../features/lab/presentation/my_lab_results_page.dart';
import '../features/medical_record/data/gateway_patient_exam_results.dart';
import '../features/medical_record/presentation/my_exam_results_page.dart';
import '../features/lab/presentation/lab_page.dart';
import '../features/lab/presentation/lab_detail_page.dart';
import '../features/lab/data/gateway_lab_workflow.dart';
import '../features/appointment/data/gateway_open_visit_repository.dart';
import '../features/appointment/presentation/open_visit_notice.dart';
import '../features/medical_record/data/gateway_exam_lifecycle_repository.dart';
import '../features/medical_record/data/gateway_patient_allergy_repository.dart';
import '../features/medical_record/data/gateway_patient_timeline_repository.dart';
import '../features/medical_record/data/gateway_record_collaboration_repository.dart';
import '../features/medical_record/presentation/record_collaboration_section.dart';
import '../features/medical_record/presentation/record_lab_orders_section.dart';
import '../features/medical_record/presentation/patient_allergy_section.dart';
import '../features/medical_record/presentation/patient_timeline_section.dart';
import '../features/report/data/gateway_report_repository.dart';
import '../features/report/presentation/report_page.dart';
import '../features/patient/data/gateway_patient_repository.dart';
import '../features/patient/presentation/providers.dart';
import '../features/organization/data/gateway_organization_repository.dart';
import '../features/organization/data/gateway_profile_account_repository.dart';
import '../features/organization/presentation/providers.dart';
import '../features/billing/data/gateway_billing_repository.dart';
import '../features/billing/data/secure_lab_payment_recovery_store.dart';
import '../features/billing/data/secure_scheduled_payment_recovery_store.dart';
import '../features/billing/domain/billing_models.dart';
import '../features/billing/presentation/providers.dart';
import '../features/notification/data/gateway_notification_repository.dart';
import '../features/notification/presentation/providers.dart';
import '../features/appointment/data/gateway_outpatient_schedule_repository.dart';
import '../features/appointment/presentation/outpatient_schedule_pages.dart';
import '../features/appointment/data/gateway_schedule_management_repository.dart';
import '../features/appointment/presentation/schedule_management_pages.dart';
import '../features/medical_record/data/gateway_follow_up_recommendation_repository.dart';
import '../features/medical_record/presentation/follow_up_recommendation_panel.dart';
import '../features/organization/presentation/account_detail_page.dart';
import '../features/pharmacy/presentation/pharmacy_outbox_page.dart';
import '../features/pharmacy/data/gateway_pharmacy_repository.dart';
import '../features/pharmacy/presentation/providers.dart';
import '../features/self_profile/data/gateway_self_profile_repository.dart';
import '../features/self_profile/presentation/providers.dart';

final _pharmacyAdapterProvider = Provider(
  (ref) => GatewayPharmacyRepository(ref.watch(apiClientProvider)),
);

// Composition root is the only place that binds data adapters to domain ports.
final appOverrides = [
  examLifecycleRepositoryProvider.overrideWith(
    (ref) => GatewayExamLifecycleRepository(ref.watch(apiClientProvider)),
  ),
  patientTimelineRepositoryProvider.overrideWith(
    (ref) => GatewayPatientTimelineRepository(ref.watch(apiClientProvider)),
  ),
  recordCollaborationRepositoryProvider.overrideWith(
    (ref) => GatewayRecordCollaborationRepository(ref.watch(apiClientProvider)),
  ),
  patientAllergyRepositoryProvider.overrideWith(
    (ref) => GatewayPatientAllergyRepository(ref.watch(apiClientProvider)),
  ),
  recordLabCancelProvider.overrideWith(
    (ref) => (testId, reason) async {
      await ref.read(labWorkflowProvider).cancel(testId, reason);
    },
  ),
  openVisitRepositoryProvider.overrideWith(
    (ref) => GatewayOpenVisitRepository(ref.watch(apiClientProvider)),
  ),
  profileAccountRepositoryProvider.overrideWith(
    (ref) => GatewayProfileAccountRepository(ref.watch(apiClientProvider)),
  ),
  outpatientScheduleRepositoryProvider.overrideWith(
    (ref) => GatewayOutpatientScheduleRepository(ref.watch(apiClientProvider)),
  ),
  followUpRecommendationRepositoryProvider.overrideWith(
    (ref) =>
        GatewayFollowUpRecommendationRepository(ref.watch(apiClientProvider)),
  ),
  scheduleManagementRepositoryProvider.overrideWith(
    (ref) => GatewayScheduleManagementRepository(ref.watch(apiClientProvider)),
  ),
  clinicQueueRepositoryProvider.overrideWith(
    (ref) => GatewayClinicQueueRepository(ref.watch(apiClientProvider)),
  ),
  receptionRepositoryProvider.overrideWith(
    (ref) => GatewayReceptionRepository(ref.watch(apiClientProvider)),
  ),
  outpatientRefundRepositoryProvider.overrideWith(
    (ref) => GatewayBillingRepository(ref.watch(apiClientProvider)),
  ),
  paymentQueueRepositoryProvider.overrideWith(
    (ref) => GatewayBillingRepository(ref.watch(apiClientProvider)),
  ),
  labQuotePaymentRepositoryProvider.overrideWith(
    (ref) => GatewayBillingRepository(ref.watch(apiClientProvider)),
  ),
  labPaymentRecoveryStoreProvider.overrideWith((ref) {
    final accountId = ref.watch(workspaceAccessProvider)?.accountId;
    if (accountId == null || !isBillingUuid(accountId)) return null;
    return SecureLabPaymentRecoveryStore(
      const FlutterSecureStorage(),
      accountId: accountId,
    );
  }),
  scheduledPaymentRepositoryProvider.overrideWith(
    (ref) => GatewayBillingRepository(ref.watch(apiClientProvider)),
  ),
  scheduledPaymentRecoveryStoreProvider.overrideWith((ref) {
    final accountId = ref.watch(workspaceAccessProvider)?.accountId;
    if (accountId == null || !isBillingUuid(accountId)) return null;
    return SecureScheduledPaymentRecoveryStore(
      const FlutterSecureStorage(),
      accountId: accountId,
    );
  }),
  accountRecoveryProvider.overrideWith(
    (ref) => GatewayAccountRecovery(ref.watch(apiClientProvider)),
  ),
  passwordRepositoryProvider.overrideWith(
    (ref) => GatewayPasswordRepository(ref.watch(apiClientProvider)),
  ),
  patientExamResultsProvider.overrideWith(
    (ref) => GatewayPatientExamResults(ref.watch(apiClientProvider)),
  ),
  patientLabResultsProvider.overrideWith(
    (ref) => GatewayPatientLabResults(ref.watch(apiClientProvider)),
  ),
  medicalRecordRepositoryProvider.overrideWith(
    (ref) => GatewayMedicalRecordRepository(ref.watch(apiClientProvider)),
  ),
  reportRepositoryProvider.overrideWith(
    (ref) => GatewayReportRepository(ref.watch(apiClientProvider)),
  ),
  labWorkflowProvider.overrideWith(
    (ref) => GatewayLabWorkflow(ref.watch(apiClientProvider)),
  ),
  pharmacyOutboxRepositoryProvider.overrideWith(
    (ref) => ref.watch(_pharmacyAdapterProvider),
  ),
  accountDetailRepositoryProvider.overrideWith(
    (ref) => GatewayOrganizationRepository(ref.watch(apiClientProvider)),
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
      accountId: session.accountId,
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
  selfProfileRepositoryProvider.overrideWith(
    (ref) => GatewaySelfProfileRepository(ref.watch(apiClientProvider)),
  ),
  billingRepositoryProvider.overrideWith(
    (ref) => GatewayBillingRepository(ref.watch(apiClientProvider)),
  ),
  notificationRepositoryProvider.overrideWith(
    (ref) => GatewayNotificationRepository(ref.watch(apiClientProvider)),
  ),
];
