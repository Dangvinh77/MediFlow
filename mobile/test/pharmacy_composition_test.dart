import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/app/app.dart';
import 'package:mediflow_mobile/app/dependencies.dart';
import 'package:mediflow_mobile/app/router.dart';
import 'package:mediflow_mobile/core/config/app_config.dart';
import 'package:mediflow_mobile/core/network/api_client.dart';
import 'package:mediflow_mobile/core/providers.dart';
import 'package:mediflow_mobile/features/auth/domain/session.dart';
import 'package:mediflow_mobile/features/auth/presentation/session_controller.dart';
import 'package:mediflow_mobile/features/pharmacy/data/gateway_pharmacy_repository.dart';
import 'package:mediflow_mobile/features/pharmacy/presentation/providers.dart';

import 'pharmacy_fixtures.dart';
import 'workspace_access_test.dart' show TestSession;

void main() {
  for (final mode in ['unavailable', 'unexpected', 'legacy', 'outpatient']) {
    test(
      'composition explicitly binds Pharmacy capabilities for $mode',
      () async {
        final container = ProviderContainer(
          overrides: [
            ...appOverrides,
            sessionProvider.overrideWith(TestSession.new),
            appConfigProvider.overrideWithValue(
              AppConfig('http://localhost:8080/api', pharmacyMode: mode),
            ),
          ],
        );
        addTearDown(container.dispose);
        await container.read(sessionProvider.future);
        expect(
          container.read(pharmacyRepositoryProvider),
          isA<GatewayPharmacyRepository>(),
        );
        expect(
          container.read(outpatientPrescriptionWriterProvider) != null,
          mode == 'outpatient',
        );
        expect(
          container.read(prescriptionDispenserProvider) != null,
          ['legacy', 'outpatient'].contains(mode),
        );
        final session = container.read(sessionProvider.notifier) as TestSession;
        session.replace(
          const Session(
            accessToken: 'first',
            refreshToken: 'r',
            role: UserRole.admin,
          ),
        );
        final first = container.read(pharmacyAccessProvider)!.sessionIdentity;
        session.replace(
          const Session(
            accessToken: 'second',
            refreshToken: 'r',
            role: UserRole.admin,
          ),
        );
        expect(
          identical(
            first,
            container.read(pharmacyAccessProvider)!.sessionIdentity,
          ),
          isFalse,
        );
      },
    );
  }
  testWidgets(
    'root routes reach real adapter, static new routes and nested role deny',
    (tester) async {
      final adapter = PharmacyHttpAdapter();
      final client = ApiClient(
        AppConfig('http://localhost:8080/api'),
        dio: Dio()..httpClientAdapter = adapter,
      );
      final container = ProviderContainer(
        overrides: [
          ...appOverrides,
          sessionProvider.overrideWith(TestSession.new),
          apiClientProvider.overrideWithValue(client),
          appConfigProvider.overrideWithValue(
            AppConfig('http://localhost:8080/api', pharmacyMode: 'outpatient'),
          ),
        ],
      );
      addTearDown(() {
        container.dispose();
        client.close();
      });
      await container.read(sessionProvider.future);
      final session = container.read(sessionProvider.notifier) as TestSession;
      session.replace(
        const Session(
          accessToken: 'admin',
          refreshToken: 'r',
          role: UserRole.admin,
        ),
      );
      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: const MediFlowApp(),
        ),
      );
      await tester.pumpAndSettle();
      final router = container.read(routerProvider);
      router.go('/pharmacy');
      await tester.pumpAndSettle();
      expect(adapter.requests.single.uri.path, '/api/v1/pharmacy/drugs');
      expect(find.text('Paracetamol 500 mg'), findsOneWidget);
      router.go('/pharmacy/drugs/new');
      await tester.pumpAndSettle();
      expect(find.widgetWithText(TextField, 'Tên thuốc'), findsOneWidget);
      expect(adapter.requests, hasLength(1));
      router.go('/pharmacy/prescriptions/new');
      await tester.pumpAndSettle();
      expect(
        find.text('Chức năng kê đơn ngoại trú chưa sẵn sàng.'),
        findsNothing,
      );
      session.replace(
        const Session(
          accessToken: 'pharmacist',
          refreshToken: 'r',
          role: UserRole.pharmacist,
        ),
      );
      await tester.pumpAndSettle();
      expect(
        find.text('Bạn không có quyền truy cập chức năng này.'),
        findsOneWidget,
      );
      session.replace(
        const Session(
          accessToken: 'nurse',
          refreshToken: 'r',
          role: UserRole.nurse,
        ),
      );
      router.go('/pharmacy/drugs/$drugId/stock');
      await tester.pumpAndSettle();
      expect(
        find.text('Bạn không có quyền truy cập chức năng này.'),
        findsOneWidget,
      );
      expect(tester.takeException(), isNull);
    },
  );
}
