import '../core/l10n/app_l10n.dart';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:mediflow_loader/mediflow_loader.dart';

import '../features/auth/presentation/login_page.dart';
import '../features/auth/presentation/account_recovery_page.dart';
import '../features/auth/presentation/change_password_page.dart';
import '../features/auth/presentation/session_controller.dart';
import '../features/appointment/presentation/appointment_page.dart';
import '../features/appointment/presentation/outpatient_schedule_pages.dart';
import '../features/appointment/presentation/clinic_queue_page.dart';
import '../features/appointment/presentation/reception_page.dart';
import '../features/appointment/presentation/schedule_management_pages.dart';
import '../features/lab/presentation/lab_page.dart';
import '../features/lab/presentation/lab_detail_page.dart';
import '../features/lab/presentation/my_lab_results_page.dart';
import '../features/medical_record/presentation/my_exam_results_page.dart';
import '../features/organization/presentation/organization_page.dart';
import '../features/organization/domain/profile_account.dart';
import '../features/organization/presentation/profile_account_panel.dart';
import '../features/patient/presentation/patient_page.dart';
import '../features/medical_record/presentation/medical_record_page.dart';
import '../features/medical_record/domain/patient_timeline.dart';
import '../features/pharmacy/presentation/pharmacy_page.dart';
import '../features/pharmacy/presentation/drug_forms.dart';
import '../features/pharmacy/presentation/create_prescription_page.dart';
import '../features/pharmacy/presentation/prescription_page.dart';
import '../features/auth/domain/session.dart';
import '../features/billing/presentation/billing_page.dart';
import '../features/notification/presentation/notification_page.dart';
import '../features/report/presentation/report_page.dart';
import '../features/self_profile/presentation/profile_page.dart';
import 'app_shell.dart';
import 'modules.dart';
import 'support_page.dart';

class _RouterRefresh extends ChangeNotifier {
  void refresh() => notifyListeners();
}

void _openRecordDocument(BuildContext context, RecordDocument document) {
  switch (document.type) {
    case 'LAB':
      Navigator.of(context).push<void>(
        MaterialPageRoute(
          builder: (_) => LabDetailPage(id: document.referenceId),
        ),
      );
      break;
    case 'PRESCRIPTION':
      context.go(
        '/pharmacy/prescriptions/${Uri.encodeComponent(document.referenceId)}',
      );
      break;
    default:
      break;
  }
}

void _openVisitRecord(BuildContext context, String patientId, String visitId) {
  Navigator.of(context).push<void>(
    MaterialPageRoute(
      builder: (_) => MedicalRecordForVisitPage(
        patientId: patientId,
        visitId: visitId,
        onOpenDocument: _openRecordDocument,
        prescriptionWorkspaceBuilder: (context, record, {required canCreate}) =>
            RecordPrescriptionPanel(
              recordId: record.id,
              patientId: record.patientId,
              canCreate: canCreate,
            ),
      ),
    ),
  );
}

final routerProvider = Provider<GoRouter>((ref) {
  final refresh = _RouterRefresh();
  ref.listen(sessionProvider, (_, _) => refresh.refresh());
  Widget patientWorkspace(
    BuildContext context, {
    String? patientId,
    bool create = false,
  }) => PatientPage(
    initialPatientId: patientId,
    initiallyCreate: create,
    accountPanelBuilder: (_, patient, access) => ProfileAccountPanel(
      owner: ProfileAccountOwner.patient(
        id: patient.id,
        active: !patient.archived,
      ),
      access: access,
      profileEmail: patient.email,
    ),
    onRegisterPatient: ref.read(outpatientUiEnabledProvider)
        ? (id) => context.go('/appointments/reception?patientId=$id')
        : null,
  );
  final router = GoRouter(
    refreshListenable: refresh,
    redirect: (context, state) {
      final auth = ref.read(sessionProvider);
      final path = state.uri.path;
      if (auth.isLoading) return null;
      final session = auth.asData?.value;
      final publicAuth = const [
        '/login',
        '/forgot-password',
        '/reset-password',
      ].contains(path);
      if (session == null) return publicAuth ? null : '/login';
      if (session.mustChangePassword && path != '/account/password') {
        return '/account/password';
      }
      if (publicAuth) return '/';
      if ((path == '/appointments/reception' || path == '/patients/new') &&
          ![UserRole.admin, UserRole.nurse].contains(session.role)) {
        return '/forbidden';
      }
      final module = modules.where((m) => m.matches(path)).firstOrNull;
      if (module != null && !module.allows(session.role)) return '/forbidden';
      // Like the web OrganizationWorkspace: only ADMIN administers the
      // organization; other staff opening it see their own profile.
      if (path == '/organization' && session.role != UserRole.admin) {
        return '/profile';
      }
      if (path == '/pharmacy/drugs/new' ||
          (path.startsWith('/pharmacy/drugs/') && path.endsWith('/stock'))) {
        if (![UserRole.admin, UserRole.pharmacist].contains(session.role)) {
          return '/forbidden';
        }
      }
      if (path == '/pharmacy/prescriptions/new' &&
          ![UserRole.admin, UserRole.doctor].contains(session.role)) {
        return '/forbidden';
      }
      return null;
    },
    routes: [
      GoRoute(path: '/login', builder: (_, _) => const LoginPage()),
      GoRoute(
        path: '/forgot-password',
        builder: (_, _) => const AccountRecoveryPage(),
      ),
      GoRoute(
        path: '/reset-password',
        builder: (_, state) => AccountRecoveryPage(
          reset: true,
          token: state.uri.queryParameters['token'] ?? '',
        ),
      ),
      ShellRoute(
        builder: (context, state, child) => Consumer(
          builder: (context, ref, _) {
            final auth = ref.watch(sessionProvider);
            if (auth.isLoading) {
              return const Scaffold(body: Center(child: MediFlowLoader()));
            }
            if (auth.asData?.value == null) return const SizedBox.shrink();
            return AppShell(location: state.uri.path, child: child);
          },
        ),
        routes: [
          GoRoute(path: '/', builder: (_, _) => const HomePage()),
          GoRoute(
            path: '/account/password',
            builder: (_, _) => const ChangePasswordPage(),
          ),
          GoRoute(path: '/profile', builder: (_, _) => const SelfProfilePage()),
          GoRoute(
            path: '/appointments',
            builder: (context, _) => AppointmentPage(
              onReception: () => context.go('/appointments/reception'),
              onOpenRecord: (patientId, visitId) =>
                  _openVisitRecord(context, patientId, visitId),
            ),
          ),
          GoRoute(
            path: '/appointments/my-calendar',
            builder: (context, _) => DoctorCalendarPage(
              onOpenRegistration: (context, registrationId) =>
                  Navigator.of(context).push<void>(
                    MaterialPageRoute(
                      builder: (_) => AppointmentDetailPage(
                        appointmentId: registrationId,
                        onOpenRecord: (patientId, visitId) =>
                            _openVisitRecord(context, patientId, visitId),
                      ),
                    ),
                  ),
            ),
          ),
          GoRoute(
            path: '/appointments/my-shifts',
            builder: (_, _) => const DoctorWorkShiftsPage(),
          ),
          GoRoute(
            path: '/appointments/schedule',
            builder: (_, _) => const ScheduleManagementPage(),
          ),
          GoRoute(
            path: '/appointments/department',
            builder: (_, _) => DepartmentManagementPage(
              reportBuilder: (departmentId) =>
                  ReportPage(lockedDepartmentId: departmentId),
            ),
          ),
          GoRoute(
            path: '/appointments/my-registration',
            builder: (_, _) => const PatientRegistrationsPage(),
          ),
          GoRoute(
            path: '/appointments/clinic-queue',
            builder: (_, _) => const ClinicQueuePage(),
          ),
          GoRoute(
            path: '/appointments/reception',
            builder: (context, state) => ReceptionPage(
              initialPatientId: state.uri.queryParameters['patientId'],
              onCreatePatient: () => context.go('/patients/new'),
              onOpenPatient: (id) => context.go('/patients/$id'),
            ),
          ),
          GoRoute(path: '/lab', builder: (_, _) => const LabPage()),
          GoRoute(
            path: '/my-results/exams',
            builder: (_, _) => const MyExamResultsPage(),
          ),
          GoRoute(
            path: '/my-results/lab',
            builder: (_, _) => const MyLabResultsPage(),
          ),
          GoRoute(
            path: '/organization',
            builder: (_, _) => const OrganizationPage(),
          ),
          GoRoute(
            path: '/patients',
            builder: (context, _) => patientWorkspace(context),
          ),
          GoRoute(
            path: '/patients/new',
            builder: (context, _) => patientWorkspace(context, create: true),
          ),
          GoRoute(
            path: '/patients/:id',
            builder: (context, state) => patientWorkspace(
              context,
              patientId: state.pathParameters['id'],
            ),
          ),
          GoRoute(
            path: '/records',
            builder: (context, _) => MedicalRecordPage(
              onOpenDocument: _openRecordDocument,
              prescriptionWorkspaceBuilder:
                  (context, record, {required canCreate}) =>
                      RecordPrescriptionPanel(
                        recordId: record.id,
                        patientId: record.patientId,
                        canCreate: canCreate,
                      ),
            ),
          ),
          GoRoute(
            path: '/pharmacy',
            builder: (context, _) => PharmacyPage(
              onOpenDrug: (id) => context.go('/pharmacy/drugs/$id'),
              onCreateDrug: () => context.go('/pharmacy/drugs/new'),
              onLookupPrescription: () => context.go('/pharmacy/prescriptions'),
              onCreatePrescription: () =>
                  context.go('/pharmacy/prescriptions/new'),
            ),
          ),
          GoRoute(
            path: '/pharmacy/drugs/new',
            builder: (context, _) => _PharmacyRoute(
              title: 'Thêm thuốc',
              child: CreateDrugPage(
                onCreated: (drug) => context.go('/pharmacy/drugs/${drug.id}'),
              ),
            ),
          ),
          GoRoute(
            path: '/pharmacy/drugs/:id/stock',
            builder: (_, state) => _PharmacyRoute(
              title: 'Điều chỉnh tồn kho',
              child: AdjustDrugStockPage(drugId: state.pathParameters['id']!),
            ),
          ),
          GoRoute(
            path: '/pharmacy/drugs/:id',
            builder: (_, state) => _PharmacyRoute(
              title: 'Chi tiết thuốc',
              child: DrugDetailPage(drugId: state.pathParameters['id']!),
            ),
          ),
          GoRoute(
            path: '/pharmacy/prescriptions/new',
            builder: (context, _) => _PharmacyRoute(
              title: 'Kê đơn ngoại trú',
              child: CreatePrescriptionPage(
                onCreated: (rx) =>
                    context.go('/pharmacy/prescriptions/${rx.id}'),
              ),
            ),
          ),
          GoRoute(
            path: '/pharmacy/prescriptions',
            builder: (_, _) => const _PharmacyRoute(
              title: 'Tra cứu đơn thuốc',
              child: PrescriptionLookupPage(),
            ),
          ),
          GoRoute(
            path: '/pharmacy/prescriptions/:id',
            builder: (_, state) => _PharmacyRoute(
              title: 'Chi tiết đơn thuốc',
              child: PrescriptionDetailPage(
                prescriptionId: state.pathParameters['id']!,
              ),
            ),
          ),
          GoRoute(path: '/billing', builder: (_, _) => const BillingPage()),
          GoRoute(path: '/support', builder: (_, _) => const SupportPage()),
          GoRoute(
            path: '/notifications',
            builder: (_, _) => const NotificationPage(),
          ),
          GoRoute(path: '/reports', builder: (_, _) => const ReportPage()),
          GoRoute(
            path: '/forbidden',
            builder: (_, _) => const Center(
              child: LText('Bạn không có quyền truy cập chức năng này.'),
            ),
          ),
        ],
      ),
    ],
    errorBuilder: (_, _) =>
        const Scaffold(body: Center(child: LText('Không tìm thấy trang.'))),
  );
  ref.onDispose(() {
    router.dispose();
    refresh.dispose();
  });
  return router;
});

class _PharmacyRoute extends StatelessWidget {
  const _PharmacyRoute({required this.title, required this.child});
  final String title;
  final Widget child;
  @override
  Widget build(BuildContext context) => Column(
    children: [
      Row(
        children: [
          BackButton(onPressed: () => context.go('/pharmacy')),
          Expanded(
            child: LText(title, style: Theme.of(context).textTheme.titleLarge),
          ),
        ],
      ),
      Expanded(child: child),
    ],
  );
}
