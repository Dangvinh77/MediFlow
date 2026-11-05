import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/config/app_config.dart';
import 'package:mediflow_mobile/core/domain/workspace_access.dart';
import 'package:mediflow_mobile/core/network/api_client.dart';
import 'package:mediflow_mobile/core/network/api_failure.dart';
import 'package:mediflow_mobile/core/providers.dart';
import 'package:mediflow_mobile/core/widgets/reference_widgets.dart';
import 'package:mediflow_mobile/features/organization/data/gateway_organization_repository.dart';
import 'package:mediflow_mobile/features/organization/domain/organization.dart';
import 'package:mediflow_mobile/features/organization/presentation/account_detail_page.dart';
import 'package:mediflow_mobile/features/pharmacy/data/gateway_pharmacy_repository.dart';
import 'package:mediflow_mobile/features/pharmacy/domain/pharmacy_outbox.dart';
import 'package:mediflow_mobile/features/pharmacy/presentation/pharmacy_outbox_page.dart';
import 'package:mediflow_mobile/features/pharmacy/presentation/providers.dart';

import 'billing_notification_fixtures.dart';

const accountId = '10000000-0000-4000-8000-0000000000b1';
const staffId = '10000000-0000-4000-8000-0000000000b2';
const eventId = '10000000-0000-4000-8000-0000000000b3';

Account sampleAccount({
  bool active = true,
  String role = 'DOCTOR',
  String? staff = staffId,
}) => Account(
  id: accountId,
  username: 'doctor@example.test',
  staffId: staff,
  patientId: null,
  role: role,
  active: active,
  lastLoginAt: null,
  createdAt: '2026-11-05T01:00:00Z',
  updatedAt: '2026-11-05T01:00:00Z',
);

class References implements ReferenceReader {
  @override
  Future<ReferenceOption> read(ReferenceKind kind, String id) async =>
      ReferenceOption(id: id, name: 'Tên ${kind.name}');

  @override
  Future<ReferencePage> search(
    ReferenceKind kind, {
    String query = '',
    int page = 0,
    String? scope,
  }) async => const ReferencePage([]);
}

class FakeAccounts implements AccountDetailRepository {
  Account current = sampleAccount();
  final results = <Object>[];
  final calls = <(bool, bool)>[];
  int reads = 0;

  @override
  Future<Account> account(String id) async {
    reads++;
    return current;
  }

  @override
  Future<Account> setAccountStatus(
    String id,
    bool active, {
    bool replaceActiveAccount = false,
  }) async {
    calls.add((active, replaceActiveAccount));
    if (results.isNotEmpty) {
      final next = results.removeAt(0);
      if (next is Account) return next;
      throw next;
    }
    return current;
  }
}

class FakeOutbox implements PharmacyOutboxRepository {
  final results = <Object>[];
  final calls = <String>[];

  @override
  Future<PharmacyOutboxReplay> replayOutbox(String eventId) async {
    calls.add(eventId);
    final next = results.removeAt(0);
    if (next is PharmacyOutboxReplay) return next;
    throw next;
  }
}

Widget accountHost(FakeAccounts repository, {String role = 'ADMIN'}) =>
    ProviderScope(
      overrides: [
        workspaceAccessProvider.overrideWithValue(
          WorkspaceAccess(sessionIdentity: 's', role: role),
        ),
        referenceReaderProvider.overrideWithValue(References()),
        accountDetailRepositoryProvider.overrideWithValue(repository),
      ],
      child: const MaterialApp(
        home: Scaffold(body: AccountDetailPage(accountId: accountId)),
      ),
    );

Widget outboxHost(FakeOutbox repository, {String role = 'ADMIN'}) =>
    ProviderScope(
      overrides: [
        pharmacyAccessProvider.overrideWithValue(
          PharmacyAccess(role: role, sessionIdentity: 's'),
        ),
        pharmacyOutboxRepositoryProvider.overrideWithValue(repository),
      ],
      child: const MaterialApp(home: Scaffold(body: PharmacyOutboxPage())),
    );

void main() {
  group('gateway', () {
    late BillingNotificationHttpAdapter adapter;
    late ApiClient client;

    setUp(() {
      adapter = BillingNotificationHttpAdapter();
      client = ApiClient(
        AppConfig('http://localhost:8080/api'),
        dio: Dio()..httpClientAdapter = adapter,
        token: () => 'test-token',
      );
    });
    tearDown(() => client.close());

    test('only a confirmed reactivation sends the replace flag', () async {
      adapter.data = {
        'accountId': accountId,
        'username': 'doctor@example.test',
        'staffId': null,
        'patientId': null,
        'role': 'DOCTOR',
        'isActive': true,
        'lastLoginAt': null,
        'createdAt': '2026-11-05T01:00:00Z',
        'updatedAt': '2026-11-05T01:00:00Z',
      };
      final repository = GatewayOrganizationRepository(client);
      await repository.setAccountStatus(accountId, true);
      expect(adapter.requests.last.data, {'isActive': true});

      await repository.setAccountStatus(
        accountId,
        true,
        replaceActiveAccount: true,
      );
      expect(adapter.requests.last.data, {
        'isActive': true,
        'replaceActiveAccount': true,
      });
    });

    test('a replay for another event is rejected', () async {
      adapter.data = {'eventId': accountId, 'replayed': true};
      await expectLater(
        GatewayPharmacyRepository(client).replayOutbox(eventId),
        throwsFormatException,
      );
      expect(
        adapter.requests.single.uri.path,
        '/api/v1/pharmacy/admin/outbox/$eventId/replay',
      );
    });
  });

  group('account detail', () {
    late FakeAccounts repository;

    setUp(
      () => repository = FakeAccounts()..current = sampleAccount(staff: null),
    );

    testWidgets('shows identity and locks an active account', (tester) async {
      repository.results.add(sampleAccount(active: false, staff: null));
      await tester.pumpWidget(accountHost(repository));
      await tester.pumpAndSettle();

      expect(find.text('doctor@example.test'), findsOneWidget);
      expect(find.text('Bác sĩ'), findsOneWidget);
      expect(find.text('Chưa liên kết'), findsOneWidget);
      await tester.tap(find.text('Khóa tài khoản'));
      await tester.pumpAndSettle();

      expect(repository.calls.single, (false, false));
      expect(find.text('Mở lại tài khoản'), findsOneWidget);
      expect(find.text('Đã khóa tài khoản.'), findsOneWidget);
    });

    testWidgets('an active owner needs explicit reactivation', (tester) async {
      repository
        ..current = sampleAccount(active: false, staff: null)
        ..results.addAll([
          const ApiFailure(
            'Chủ hồ sơ đang có tài khoản khác.',
            status: 409,
            code: 'ORG_ACCOUNT_OWNER_ACTIVE',
          ),
          sampleAccount(staff: null),
        ]);
      await tester.pumpWidget(accountHost(repository));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Mở lại tài khoản'));
      await tester.pumpAndSettle();

      expect(find.text('Chủ hồ sơ đang có tài khoản khác.'), findsOneWidget);
      await tester.tap(find.text('Tiếp tục mở lại tài khoản'));
      await tester.pumpAndSettle();

      expect(repository.calls, [(true, false), (true, true)]);
    });

    testWidgets('a lost status change must be reloaded first', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      repository.results.add(const ApiFailure('Mất kết nối'));
      await tester.pumpWidget(accountHost(repository));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Khóa tài khoản'));
      await tester.pumpAndSettle();

      expect(find.text('Khóa tài khoản'), findsNothing);
      expect(find.textContaining('Chưa xác nhận được kết quả'), findsOneWidget);
      await tester.tap(find.text('Tải lại trạng thái'));
      await tester.pumpAndSettle();
      expect(repository.reads, 2);
      expect(find.text('Khóa tài khoản'), findsOneWidget);
    });

    testWidgets('a manager account links to its scope', (tester) async {
      repository.current = sampleAccount(role: 'MANAGER', staff: null);
      final opened = <String>[];
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            workspaceAccessProvider.overrideWithValue(
              const WorkspaceAccess(sessionIdentity: 's', role: 'ADMIN'),
            ),
            accountDetailRepositoryProvider.overrideWithValue(repository),
          ],
          child: MaterialApp(
            home: Scaffold(
              body: AccountDetailPage(
                accountId: accountId,
                onManagerScope: opened.add,
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Mở phạm vi quản lý lịch'));
      expect(opened, [accountId]);
    });

    testWidgets('other roles cannot open account detail', (tester) async {
      await tester.pumpWidget(accountHost(repository, role: 'MANAGER'));
      await tester.pumpAndSettle();

      expect(find.text('Bạn không có quyền xem tài khoản.'), findsOneWidget);
    });
  });

  group('pharmacy outbox', () {
    late FakeOutbox repository;

    setUp(() => repository = FakeOutbox());

    Future<void> submit(WidgetTester tester, String value) async {
      await tester.enterText(find.byType(TextField), value);
      await tester.tap(find.text('Xem xác nhận'));
      await tester.pumpAndSettle();
    }

    testWidgets('an invalid ID never opens confirmation', (tester) async {
      await tester.pumpWidget(outboxHost(repository));
      await tester.pumpAndSettle();
      await submit(tester, 'not-a-uuid');

      expect(find.textContaining('Mã yêu cầu không hợp lệ'), findsOneWidget);
      expect(find.byType(AlertDialog), findsNothing);
    });

    testWidgets('replay runs only after confirmation', (tester) async {
      repository.results.add(
        const PharmacyOutboxReplay(eventId: eventId, replayed: true),
      );
      await tester.pumpWidget(outboxHost(repository));
      await tester.pumpAndSettle();
      await submit(tester, eventId);

      expect(repository.calls, isEmpty);
      await tester.tap(find.text('Xác nhận khôi phục'));
      await tester.pumpAndSettle();

      expect(repository.calls, [eventId]);
      expect(find.text('Đã gửi yêu cầu đồng bộ lại'), findsOneWidget);
      await tester.tap(find.text('Khôi phục yêu cầu khác'));
      await tester.pumpAndSettle();
      expect(find.text('Xem xác nhận'), findsOneWidget);
    });

    testWidgets('cancelling confirmation sends nothing', (tester) async {
      await tester.pumpWidget(outboxHost(repository));
      await tester.pumpAndSettle();
      await submit(tester, eventId);
      await tester.tap(find.text('Hủy'));
      await tester.pumpAndSettle();

      expect(repository.calls, isEmpty);
    });

    testWidgets('a missing event gets a clear message', (tester) async {
      repository.results.add(const ApiFailure('x', status: 404));
      await tester.pumpWidget(outboxHost(repository));
      await tester.pumpAndSettle();
      await submit(tester, eventId);
      await tester.tap(find.text('Xác nhận khôi phục'));
      await tester.pumpAndSettle();

      expect(
        find.text('Không tìm thấy yêu cầu đồng bộ với mã đã nhập.'),
        findsOneWidget,
      );

      await tester.enterText(find.byType(TextField), 'sửa lại');
      await tester.pump();
      expect(
        find.text('Không tìm thấy yêu cầu đồng bộ với mã đã nhập.'),
        findsNothing,
      );
    });

    testWidgets('a lost permission blocks further attempts', (tester) async {
      repository.results.add(const ApiFailure('x', status: 403));
      await tester.pumpWidget(outboxHost(repository));
      await tester.pumpAndSettle();
      await submit(tester, eventId);
      await tester.tap(find.text('Xác nhận khôi phục'));
      await tester.pumpAndSettle();

      expect(
        tester
            .widget<FilledButton>(
              find.widgetWithText(FilledButton, 'Xem xác nhận'),
            )
            .onPressed,
        isNull,
      );
    });

    testWidgets('non-admins cannot use replay', (tester) async {
      await tester.pumpWidget(outboxHost(repository, role: 'PHARMACIST'));
      await tester.pumpAndSettle();

      expect(find.byType(TextField), findsNothing);
      expect(find.textContaining('không có quyền'), findsOneWidget);
    });
  });
}
