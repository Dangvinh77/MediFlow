import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/domain/workspace_access.dart';
import 'package:mediflow_mobile/core/providers.dart';
import 'package:mediflow_mobile/features/notification/domain/notification.dart';
import 'package:mediflow_mobile/features/notification/presentation/notification_detail_page.dart';
import 'package:mediflow_mobile/features/notification/presentation/notification_page.dart';
import 'package:mediflow_mobile/features/notification/presentation/providers.dart';

import 'billing_notification_fixtures.dart';

import 'package:mediflow_mobile/core/widgets/reference_widgets.dart';

class NotificationReferences implements ReferenceReader {
  @override
  Future<ReferenceOption> read(ReferenceKind kind, String id) async =>
      ReferenceOption(id:id, name:'Nguyễn An');
  @override
  Future<ReferencePage> search(
    ReferenceKind kind, {
    String query = '',
    int page = 0,
    String? scope,
  }) async => ReferencePage([ReferenceOption(id:patientId, name:'Nguyễn An')], hasMore:false);
}

class _SwitchableSessionHost extends StatefulWidget {
  const _SwitchableSessionHost({
    required this.repository,
    required this.child,
    super.key,
  });
  final NotificationStub repository;
  final Widget child;

  @override
  State<_SwitchableSessionHost> createState() => _SwitchableSessionHostState();
}

class _SwitchableSessionHostState extends State<_SwitchableSessionHost> {
  WorkspaceAccess _access = WorkspaceAccess(
    sessionIdentity: Object(),
    role: 'ADMIN',
  );

  void switchSession() => setState(() {
    _access = WorkspaceAccess(sessionIdentity: Object(), role: 'ADMIN');
  });

  @override
  Widget build(BuildContext context) => ProviderScope(
    key: ValueKey(_access.sessionIdentity),
    overrides: [
      workspaceAccessProvider.overrideWithValue(_access),
      notificationRepositoryProvider.overrideWithValue(widget.repository),
      referenceReaderProvider.overrideWithValue(NotificationReferences()),
    ],
    child: widget.child,
  );
}

Finder fieldWithLabel(String label) => find.byWidgetPredicate(
  (widget) => widget is TextField && widget.decoration?.labelText == label,
);

Finder sendTab() => find.descendant(
  of: find.byType(TabBar),
  matching: find.text('Gửi thông báo'),
);

Finder sendButton() => find.widgetWithText(FilledButton, 'Gửi thông báo');

Future<void> enterField(WidgetTester tester, String label, String value) async {
  if (label == 'Mã bệnh nhân (UUID)') {
    await tester.tap(find.byTooltip('Chọn Bệnh nhân').last);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Nguyễn An').last);
    await tester.pumpAndSettle();
    return;
  }
  final finder = fieldWithLabel(label);
  await tester.ensureVisible(finder);
  await tester.enterText(finder, value);
}

Widget notificationHost(NotificationStub repository, {String role = 'ADMIN'}) =>
    ProviderScope(
      overrides: [
        workspaceAccessProvider.overrideWithValue(
          WorkspaceAccess(
            sessionIdentity: Object(),
            role: role,
            patientId: role == 'PATIENT' ? patientId : null,
          ),
        ),
        notificationRepositoryProvider.overrideWithValue(repository),
        referenceReaderProvider.overrideWithValue(NotificationReferences()),
      ],
      child: const MaterialApp(home: Scaffold(body: NotificationPage())),
    );

Future<void> openSendForm(
  WidgetTester tester,
  NotificationStub repository,
) async {
  await tester.pumpWidget(notificationHost(repository));
  await tester.pumpAndSettle();
  await tester.tap(sendTab());
  await tester.pumpAndSettle();
}

Future<void> fillInAppSendForm(WidgetTester tester) async {
  await enterField(tester, 'Mã bệnh nhân (UUID)', patientId);
  await enterField(tester, 'Tiêu đề', 'Kết quả khám');
  await enterField(tester, 'Nội dung', 'Vui lòng xem lịch hẹn.');
}

void main() {
  testWidgets(
    'PATIENT history uses only the signed patient reference and hides UUID',
    (tester) async {
      final repository = NotificationStub();
      await tester.pumpWidget(notificationHost(repository, role: 'PATIENT'));
      await tester.pumpAndSettle();
      expect(repository.historyCalls.single.patientId, patientId);
      expect(find.text(patientId), findsNothing);
      expect(find.text('Chưa có thông báo.'), findsOneWidget);
    },
  );

  testWidgets('ADMIN sends only after exact channel address validation', (
    tester,
  ) async {
    final repository = NotificationStub();
    await openSendForm(tester, repository);
    await tester.tap(find.text('Trong ứng dụng'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Email').last);
    await tester.pumpAndSettle();
    await fillInAppSendForm(tester);
    await enterField(tester, 'Email nhận', 'bad-address');
    await tester.tap(sendButton());
    await tester.pumpAndSettle();
    expect(repository.sends, isEmpty);

    await enterField(tester, 'Email nhận', 'patient@example.test');
    await tester.tap(sendButton());
    await tester.pumpAndSettle();
    expect(repository.sends, hasLength(1));
    expect(repository.sends.single.patientId, patientId);
    expect(repository.sends.single.channel, 'EMAIL');
    expect(repository.sends.single.recipientAddress, 'patient@example.test');
    expect(
      find.textContaining('Mã thông báo: $notificationId'),
      findsOneWidget,
    );
    final receipt = find.ancestor(
      of: find.textContaining('Mã thông báo: $notificationId'),
      matching: find.byType(Card),
    );
    expect(
      find.descendant(
        of: receipt,
        matching: find.textContaining('patient@example.test'),
      ),
      findsNothing,
    );
  });

  testWidgets('uncertain send lock survives a history tab switch', (
    tester,
  ) async {
    final repository = NotificationStub();
    repository.onSend = (_) => Future.error(Exception('unknown outcome'));
    await openSendForm(tester, repository);
    await fillInAppSendForm(tester);
    await tester.tap(sendButton());
    await tester.pumpAndSettle();
    expect(repository.sends, hasLength(1));
    expect(find.textContaining('Kết quả gửi chưa xác định'), findsOneWidget);

    await tester.tap(find.text('Lịch sử'));
    await tester.pumpAndSettle();
    await tester.tap(sendTab());
    await tester.pumpAndSettle();
    expect(find.textContaining('Kết quả gửi chưa xác định'), findsOneWidget);
    expect(
      tester.widget<TextField>(fieldWithLabel('Tiêu đề')).enabled,
      isFalse,
    );
    expect(repository.sends, hasLength(1));
  });

  testWidgets(
    'a new session disposes an unresolved send form and stale receipt',
    (tester) async {
      final repository = NotificationStub();
      final pendingSend = Completer<PatientNotification>();
      repository.onSend = (_) => pendingSend.future;
      final hostKey = GlobalKey<_SwitchableSessionHostState>();
      await tester.pumpWidget(
        _SwitchableSessionHost(
          key: hostKey,
          repository: repository,
          child: const MaterialApp(home: Scaffold(body: NotificationPage())),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(sendTab());
      await tester.pumpAndSettle();
      await fillInAppSendForm(tester);
      await tester.tap(sendButton());
      await tester.pump();
      expect(repository.sends, hasLength(1));

      hostKey.currentState!.switchSession();
      await tester.pumpAndSettle();
      await tester.tap(sendTab());
      await tester.pumpAndSettle();
      expect(
        tester.widget<TextField>(fieldWithLabel('Tiêu đề')).controller!.text,
        isEmpty,
      );

      pendingSend.complete(patientNotification());
      await tester.pumpAndSettle();
      expect(find.textContaining('Kết quả gửi:'), findsNothing);
      expect(
        find.textContaining('Mã thông báo: $notificationId'),
        findsNothing,
      );
    },
  );

  testWidgets('NURSE cannot call the detail API reserved to ADMIN/PATIENT', (
    tester,
  ) async {
    final repository = NotificationStub();
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          workspaceAccessProvider.overrideWithValue(
            WorkspaceAccess(sessionIdentity: Object(), role: 'NURSE'),
          ),
          notificationRepositoryProvider.overrideWithValue(repository),
          referenceReaderProvider.overrideWithValue(NotificationReferences()),
        ],
        child: const MaterialApp(
          home: Scaffold(
            body: NotificationDetailPage(notificationId: notificationId),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(
      find.text('Bạn không có quyền xem chi tiết thông báo.'),
      findsOneWidget,
    );
    expect(repository.detailCalls, isEmpty);
  });
}
