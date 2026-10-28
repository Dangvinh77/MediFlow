import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../features/auth/presentation/login_page.dart';
import '../features/auth/presentation/session_controller.dart';
import '../features/appointment/presentation/appointment_page.dart';
import '../features/lab/presentation/lab_page.dart';
import '../features/organization/presentation/organization_page.dart';
import '../features/patient/presentation/patient_page.dart';
import '../features/medical_record/presentation/medical_record_page.dart';
import '../features/pharmacy/presentation/pharmacy_page.dart';
import '../features/pharmacy/presentation/drug_forms.dart';
import '../features/pharmacy/presentation/create_prescription_page.dart';
import '../features/pharmacy/presentation/prescription_page.dart';
import '../features/auth/domain/session.dart';
import '../features/billing/presentation/billing_page.dart';
import '../features/notification/presentation/notification_page.dart';
import '../features/report/presentation/report_page.dart';
import 'app_shell.dart';
import 'modules.dart';
import 'support_page.dart';

class _RouterRefresh extends ChangeNotifier {
  void refresh() => notifyListeners();
}

final routerProvider = Provider<GoRouter>((ref) {
  final refresh = _RouterRefresh();
  ref.listen(sessionProvider, (_, _) => refresh.refresh());
  final router = GoRouter(
    refreshListenable: refresh,
    redirect: (context, state) {
      final auth = ref.read(sessionProvider);
      final path = state.uri.path;
      if (auth.isLoading) return null;
      final session = auth.asData?.value;
      if (session == null) return path == '/login' ? null : '/login';
      if (path == '/login') return '/';
      final module = modules.where((m) => m.matches(path)).firstOrNull;
      if (module != null && !module.allows(session.role)) return '/forbidden';
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
      ShellRoute(
        builder: (context, state, child) => Consumer(
          builder: (context, ref, _) {
            final auth = ref.watch(sessionProvider);
            if (auth.isLoading) {
              return const Scaffold(
                body: Center(child: CircularProgressIndicator()),
              );
            }
            if (auth.asData?.value == null) return const SizedBox.shrink();
            return AppShell(location: state.uri.path, child: child);
          },
        ),
        routes: [
          GoRoute(path: '/', builder: (_, _) => const HomePage()),
          GoRoute(
            path: '/appointments',
            builder: (_, _) => const AppointmentPage(),
          ),
          GoRoute(path: '/lab', builder: (_, _) => const LabPage()),
          GoRoute(
            path: '/organization',
            builder: (_, _) => const OrganizationPage(),
          ),
          GoRoute(path: '/patients', builder: (_, _) => const PatientPage()),
          GoRoute(
            path: '/records',
            builder: (_, _) => const MedicalRecordPage(),
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
              child: Text('Bạn không có quyền truy cập chức năng này.'),
            ),
          ),
        ],
      ),
    ],
    errorBuilder: (_, _) =>
        const Scaffold(body: Center(child: Text('Không tìm thấy trang.'))),
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
            child: Text(title, style: Theme.of(context).textTheme.titleLarge),
          ),
        ],
      ),
      Expanded(child: child),
    ],
  );
}
