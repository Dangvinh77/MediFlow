import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/domain/page_result.dart';
import 'package:mediflow_mobile/core/domain/workspace_access.dart';
import 'package:mediflow_mobile/core/network/api_failure.dart';
import 'package:mediflow_mobile/core/providers.dart';
import 'package:mediflow_mobile/core/widgets/reference_widgets.dart';
import 'package:mediflow_mobile/features/appointment/domain/reception.dart';
import 'package:mediflow_mobile/features/appointment/presentation/appointment_page.dart';
import 'package:mediflow_mobile/features/appointment/presentation/reception_page.dart';

import 'reference_picker_test_support.dart';
import 'reception_transport_test.dart' as fixtures;

class ReceptionFake implements ReceptionRepository {
  Completer<ReceptionReceipt>? response;
  final intents = <ReceptionIntent>[];
  bool failRegistration = true, failRefresh = true, sessionDisappeared = false;
  @override
  Future<ReceptionReceipt> register(ReceptionIntent intent) async {
    intents.add(intent);
    if (response != null) return response!.future;
    if (failRegistration) throw const ApiFailure('Mất kết nối');
    return const ReceptionReceipt(
      id: fixtures.registrationId,
      patientId: fixtures.patientId,
      sessionId: fixtures.sessionId,
      doctorId: fixtures.doctorId,
      status: 'AWAITING_PAYMENT',
      expiresAt: '2026-11-04T03:00:00Z',
    );
  }

  @override
  Future<PageResult<ReceptionSession>> sessions({
    required String departmentId,
    required String fromDate,
    required String toDate,
    int page = 0,
  }) async {
    if (failRefresh) throw const ApiFailure('Chưa tải được số chỗ');
    return PageResult(
      items: sessionDisappeared ? [] : [fixtures.session],
      number: 0,
      totalPages: 1,
      totalElements: sessionDisappeared ? 0 : 1,
    );
  }

  @override
  Future<PageResult<ReceptionFollowUp>> followUpSources(
    String patientId, {
    int page = 0,
  }) async =>
      const PageResult(items: [], number: 0, totalPages: 0, totalElements: 0);
}

void main() {
  testWidgets('pending reception fits a narrow screen with enlarged text', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 700);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final container = ProviderContainer(
      overrides: [
        workspaceAccessProvider.overrideWithValue(
          WorkspaceAccess(sessionIdentity: Object(), role: 'NURSE'),
        ),
        outpatientUiEnabledProvider.overrideWithValue(true),
        referenceReaderProvider.overrideWithValue(
          ReferencePickerFake({
            ReferenceKind.patient: const [
              ReferenceOption(id: fixtures.patientId, name: 'Nguyễn An'),
            ],
            ReferenceKind.department: const [
              ReferenceOption(id: fixtures.departmentId, name: 'Nội tổng quát'),
            ],
          }),
        ),
      ],
    );
    addTearDown(container.dispose);
    container.read(receptionPendingProvider).intent = ReceptionIntent(
      const ReceptionCommand(
        patientId: fixtures.patientId,
        session: fixtures.session,
      ),
    );
    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: MaterialApp(
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(context)
                .copyWith(textScaler: const TextScaler.linear(2)),
            child: child!,
          ),
          home: const Scaffold(body: ReceptionPage()),
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(
      find.text('Đối soát cùng yêu cầu'),
      300,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.pumpAndSettle();
  });
  testWidgets('late registration response cannot enter a replacement session', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(1000, 2400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final repository = ReceptionFake()
      ..response = Completer<ReceptionReceipt>();
    final container = ProviderContainer(
      overrides: [
        workspaceAccessProvider.overrideWithValue(
          WorkspaceAccess(sessionIdentity: Object(), role: 'NURSE'),
        ),
        outpatientUiEnabledProvider.overrideWithValue(true),
        receptionRepositoryProvider.overrideWithValue(repository),
        referenceReaderProvider.overrideWithValue(
          ReferencePickerFake({
            ReferenceKind.patient: const [
              ReferenceOption(id: fixtures.patientId, name: 'Nguyễn An'),
            ],
            ReferenceKind.department: const [
              ReferenceOption(id: fixtures.departmentId, name: 'Nội'),
            ],
          }),
        ),
      ],
    );
    addTearDown(container.dispose);
    final oldPending = container.read(receptionPendingProvider)
      ..intent = ReceptionIntent(
        const ReceptionCommand(
          patientId: fixtures.patientId,
          session: fixtures.session,
        ),
      );
    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: const MaterialApp(home: Scaffold(body: ReceptionPage())),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Đối soát cùng yêu cầu'));
    await tester.pump();
    container.updateOverrides([
      workspaceAccessProvider.overrideWithValue(
        WorkspaceAccess(sessionIdentity: Object(), role: 'NURSE'),
      ),
      outpatientUiEnabledProvider.overrideWithValue(true),
      receptionRepositoryProvider.overrideWithValue(repository),
      referenceReaderProvider.overrideWithValue(ReferencePickerFake({})),
    ]);
    await tester.pump();
    repository.response!.complete(
      const ReceptionReceipt(
        id: fixtures.registrationId,
        patientId: fixtures.patientId,
        sessionId: fixtures.sessionId,
        doctorId: fixtures.doctorId,
        status: 'AWAITING_PAYMENT',
        expiresAt: '2026-11-04T03:00:00Z',
      ),
    );
    await tester.pumpAndSettle();
    expect(oldPending.receipt, isNull);
    expect(container.read(receptionPendingProvider), isNot(same(oldPending)));
    expect(find.text('Đã tạo phiếu · Chờ thu tiền'), findsNothing);
    expect(container.read(receptionPendingProvider).intent, isNull);
  });
  testWidgets(
    'uncertain registration keeps its key; success locks duplicate and refresh gates next patient',
    (tester) async {
      tester.view.physicalSize = const Size(1000, 2400);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final repository = ReceptionFake();
      final access = WorkspaceAccess(sessionIdentity: Object(), role: 'NURSE');
      final container = ProviderContainer(
        overrides: [
          workspaceAccessProvider.overrideWithValue(access),
          outpatientUiEnabledProvider.overrideWithValue(true),
          receptionRepositoryProvider.overrideWithValue(repository),
          referenceReaderProvider.overrideWithValue(
            ReferencePickerFake({
              ReferenceKind.patient: const [
                ReferenceOption(id: fixtures.patientId, name: 'Nguyễn An'),
              ],
              ReferenceKind.department: const [
                ReferenceOption(
                  id: fixtures.departmentId,
                  name: 'Nội tổng quát',
                ),
              ],
            }),
          ),
        ],
      );
      addTearDown(container.dispose);
      final pending = container.read(receptionPendingProvider);
      final intent = ReceptionIntent(
        const ReceptionCommand(
          patientId: fixtures.patientId,
          session: fixtures.session,
        ),
      );
      pending.intent = intent;
      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: const MaterialApp(home: Scaffold(body: ReceptionPage())),
        ),
      );
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.text('Đối soát cùng yêu cầu'));
      await tester.tap(find.text('Đối soát cùng yêu cầu'));
      await tester.pumpAndSettle();
      expect(repository.intents.single.key, intent.key);
      expect(pending.intent, same(intent));
      repository.failRegistration = false;
      await tester.ensureVisible(find.text('Đối soát cùng yêu cầu'));
      await tester.tap(find.text('Đối soát cùng yêu cầu'));
      await tester.pumpAndSettle();
      expect(repository.intents, hasLength(2));
      expect(repository.intents.last, same(intent));
      expect(pending.receipt?.status, 'AWAITING_PAYMENT');
      expect(find.text('Đối soát cùng yêu cầu'), findsNothing);
      final next = find.widgetWithText(
        FilledButton,
        'Tiếp nhận bệnh nhân tiếp theo',
      );
      await tester.ensureVisible(next);
      expect(tester.widget<FilledButton>(next).onPressed, isNull);
      repository.failRefresh = false;
      repository.sessionDisappeared = true;
      await tester.tap(find.text('Cập nhật số chỗ'));
      await tester.pumpAndSettle();
      await tester.ensureVisible(next);
      expect(tester.widget<FilledButton>(next).onPressed, isNotNull);
      await tester.tap(next);
      await tester.pumpAndSettle();
      expect(pending.intent, isNull);
      expect(pending.receipt, isNull);
      expect(repository.intents, hasLength(2));
      expect(find.text('3. Xác nhận đăng ký'), findsNothing);
    },
  );

  testWidgets('patient and doctor cannot open nurse reception', (tester) async {
    for (final role in ['PATIENT', 'DOCTOR']) {
      await tester.pumpWidget(
        ProviderScope(
          key: ValueKey(role),
          overrides: [
            workspaceAccessProvider.overrideWithValue(
              WorkspaceAccess(sessionIdentity: Object(), role: role),
            ),
            outpatientUiEnabledProvider.overrideWithValue(true),
          ],
          child: const MaterialApp(home: Scaffold(body: ReceptionPage())),
        ),
      );
      await tester.pumpAndSettle();
      expect(
        find.text('Bạn không có quyền tiếp nhận bệnh nhân.'),
        findsOneWidget,
      );
      expect(find.text('Đăng ký và giữ chỗ'), findsNothing);
    }
  });
}
