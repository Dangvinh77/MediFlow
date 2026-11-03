import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/config/app_config.dart';
import 'package:mediflow_mobile/core/domain/page_result.dart';
import 'package:mediflow_mobile/core/domain/workspace_access.dart';
import 'package:mediflow_mobile/core/network/api_client.dart';
import 'package:mediflow_mobile/core/network/gateway_reference_reader.dart';
import 'package:mediflow_mobile/core/providers.dart';
import 'package:mediflow_mobile/core/widgets/reference_widgets.dart';
import 'package:mediflow_mobile/features/billing/data/gateway_billing_repository.dart';
import 'package:mediflow_mobile/features/billing/domain/payment_queue.dart';
import 'package:mediflow_mobile/features/billing/presentation/payment_queue_page.dart';
import 'package:mediflow_mobile/features/lab/presentation/lab_detail_page.dart';
import 'package:mediflow_mobile/features/lab/domain/lab_workflow.dart';
import 'package:mediflow_mobile/features/medical_record/presentation/medical_record_page.dart';

import 'billing_notification_fixtures.dart' as billing;
import 'clinical_completion_workflow_test.dart' as clinical;
import 'lab_report_session_test.dart' as lab;
import 'reference_picker_test_support.dart';

class Queue implements PaymentQueueRepository {
  @override
  Future<PageResult<PaymentQueueItem>> paymentQueue({
    String? patientId,
    String? purpose,
    int page = 0,
  }) async => PageResult(
    items: [
      PaymentQueueItem(
        request: billing.paymentDiscovery(),
        createdAt: '2026-11-04T01:00:00Z',
      ),
    ],
    number: 0,
    totalPages: 1,
    totalElements: 1,
  );
}

void main() {
  for (final role in ['LAB_TECH', 'CASHIER', 'PHARMACIST']) {
    testWidgets(
      '$role resolves patient name and date of birth through minimal directory',
      (tester) async {
        final adapter = billing.BillingNotificationHttpAdapter()
          ..data = {
            'maBenhNhan': billing.patientId,
            'hoTen': 'Nguyễn An',
            'ngaySinh': '1986-04-12',
          };
        final client = ApiClient(
          AppConfig('http://localhost:8080/api'),
          dio: Dio()..httpClientAdapter = adapter,
        );
        addTearDown(client.close);
        await tester.pumpWidget(
          ProviderScope(
            overrides: [
              apiClientProvider.overrideWithValue(client),
              workspaceAccessProvider.overrideWithValue(
                WorkspaceAccess(sessionIdentity: role, role: role),
              ),
            ],
            child: const MaterialApp(
              home: Scaffold(
                body: ReferenceLabel(
                  kind: ReferenceKind.patient,
                  id: billing.patientId,
                  label: 'Bệnh nhân',
                ),
              ),
            ),
          ),
        );
        await tester.pumpAndSettle();
        expect(find.text('Bệnh nhân: Nguyễn An · 12/04/1986'), findsOneWidget);
        expect(
          adapter.requests.single.uri.path,
          '/api/v1/patients/directory/${billing.patientId}',
        );
        expect(canReadReference(role, ReferenceKind.record), isFalse);
        expect(canSearchReference(role, ReferenceKind.doctor), isFalse);
      },
    );
  }

  test(
    'care labels validate identity and never request full staff details',
    () async {
      final adapter = billing.BillingNotificationHttpAdapter()
        ..data = {'id': billing.patientId, 'label': 'Khoa Nội'};
      final client = ApiClient(
        AppConfig('http://localhost:8080/api'),
        dio: Dio()..httpClientAdapter = adapter,
      );
      addTearDown(client.close);
      final reader = GatewayReferenceReader(client, role: 'LAB_TECH');
      expect(
        (await reader.read(ReferenceKind.department, billing.patientId)).name,
        'Khoa Nội',
      );
      expect(
        adapter.requests.single.uri.path,
        '/api/v1/org/care-labels/department/${billing.patientId}',
      );
      adapter.data = {'id': billing.sourceId, 'label': 'Tên sai'};
      await expectLater(
        reader.read(ReferenceKind.department, billing.patientId),
        throwsFormatException,
      );
    },
  );

  test(
    'queue adapter sends supported filters and parses nested discovery',
    () async {
      final adapter = billing.BillingNotificationHttpAdapter()
        ..data = {
          'content': [
            {
              'request': {
                ...billing.paymentDiscoveryJson(),
                'purpose': 'LAB_TEST',
              },
              'createdAt': '2026-11-04T01:00:00Z',
            },
          ],
          'number': 1,
          'totalPages': 2,
          'totalElements': 21,
        };
      final client = ApiClient(
        AppConfig('http://localhost:8080/api'),
        dio: Dio()..httpClientAdapter = adapter,
      );
      addTearDown(client.close);
      final repository = GatewayBillingRepository(client);
      final result = await repository.paymentQueue(
        patientId: billing.patientId,
        purpose: 'LAB_TEST',
        page: 1,
      );
      expect(result.items.single.request.remainingAmount.minorUnits, 7525);
      expect(adapter.requests.single.uri.queryParameters, {
        'page': '1',
        'size': '20',
        'patientId': billing.patientId,
        'purpose': 'LAB_TEST',
      });
      await expectLater(
        repository.paymentQueue(purpose: 'EXAM'),
        throwsArgumentError,
      );
      expect(adapter.requests, hasLength(1));
      adapter.data = {
        'content': [
          {
            'request': billing.paymentDiscoveryJson(),
            'createdAt': '2026-11-04T01:00:00Z',
          },
        ],
        'number': 0,
        'totalPages': 1,
        'totalElements': 1,
      };
      await expectLater(
        repository.paymentQueue(purpose: 'LAB_TEST'),
        throwsFormatException,
      );
      adapter.data = {
        'sourceId': billing.sourceId,
        'sourceType': 'LAB_TEST',
        'status': 'APPLIED',
        'rejectionCode': null,
        'refundsDue': [
          {
            'originalTransactionId': billing.transactionId,
            'amount': 1000,
            'currency': 'VND',
          },
        ],
      };
      expect(
        (await repository.labRefundsDue(billing.sourceId))
            .refundsDue
            .single
            .amount
            .minorUnits,
        100000,
      );
      adapter.data = {
        'sourceId': billing.patientId,
        'sourceType': 'LAB_TEST',
        'refundsDue': [],
      };
      await expectLater(
        repository.labRefundsDue(billing.sourceId),
        throwsFormatException,
      );
    },
  );

  testWidgets(
    'selecting a queue item discovers its exact source without typing identifiers',
    (tester) async {
      tester.view.physicalSize = const Size(320, 900);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final repository = billing.BillingStub();
      final refs = ReferencePickerFake({
        for (final kind in ReferenceKind.values)
          kind: [
            const ReferenceOption(
              id: billing.patientId,
              name: 'Nguyễn An',
              detail: '1986-04-12',
            ),
          ],
      });
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            workspaceAccessProvider.overrideWithValue(
              const WorkspaceAccess(
                sessionIdentity: 'cashier',
                role: 'CASHIER',
              ),
            ),
            referenceReaderProvider.overrideWithValue(refs),
          ],
          child: MaterialApp(
            home: Scaffold(
              body: PaymentQueuePage(repository: repository, queue: Queue()),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.text('Đối chiếu và thu tiền'));
      await tester.tap(find.text('Đối chiếu và thu tiền'));
      expect(tester.takeException(), isNull);
      await tester.pumpAndSettle();
      expect(repository.paymentDiscoveries, hasLength(1));
      expect(repository.paymentDiscoveries.single.patientId, billing.patientId);
      expect(repository.paymentDiscoveries.single.sourceId, billing.sourceId);
      expect(find.text('Mã nguồn (sourceId)'), findsNothing);
      expect(repository.completedPayments, isEmpty);
    },
  );

  testWidgets('Lab cannot start a request without financial clearance', (
    tester,
  ) async {
    final workflow = lab.Workflow()
      ..read = () async => const LabDetail(
        id: billing.sourceId,
        patientId: billing.patientId,
        recordId: billing.sourceId,
        departmentId: billing.sourceId,
        type: 'Công thức máu',
        requestedDate: '2026-11-04',
        status: 'PENDING',
        paid: false,
        version: 1,
      );
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          workspaceAccessProvider.overrideWithValue(
            const WorkspaceAccess(sessionIdentity: 'lab', role: 'LAB_TECH'),
          ),
          labWorkflowProvider.overrideWithValue(workflow),
        ],
        child: const MaterialApp(home: LabDetailPage(id: billing.sourceId)),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Bắt đầu thực hiện'), findsNothing);
    expect(find.textContaining('Chờ xác nhận viện phí'), findsOneWidget);
    expect(workflow.starts, 0);
  });

  testWidgets(
    'visit resolver rejects a patient record from a different appointment',
    (tester) async {
      final repository = clinical.MedicalRecordFake();
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            workspaceAccessProvider.overrideWithValue(
              const WorkspaceAccess(sessionIdentity: 'doctor', role: 'DOCTOR'),
            ),
            medicalRecordRepositoryProvider.overrideWithValue(repository),
          ],
          child: const MaterialApp(
            home: MedicalRecordForVisitPage(
              patientId: clinical.patientId,
              visitId: 'different-visit',
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.byType(MedicalRecordDetailPage), findsNothing);
      expect(find.textContaining('Chưa xác minh'), findsOneWidget);
    },
  );
}
