import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/config/app_config.dart';
import 'package:mediflow_mobile/core/domain/workspace_access.dart';
import 'package:mediflow_mobile/core/network/api_client.dart';
import 'package:mediflow_mobile/core/network/api_failure.dart';
import 'package:mediflow_mobile/features/organization/data/gateway_profile_account_repository.dart';
import 'package:mediflow_mobile/features/organization/data/profile_account_dto.dart';
import 'package:mediflow_mobile/features/organization/domain/organization.dart';
import 'package:mediflow_mobile/features/organization/domain/profile_account.dart';
import 'package:mediflow_mobile/features/organization/presentation/profile_account_panel.dart';
import 'package:mediflow_mobile/features/organization/presentation/providers.dart';
import 'package:mediflow_mobile/core/providers.dart';

const staffId = '44444444-4444-4444-8444-444444444444';
const patientId = '66666666-6666-4666-8666-666666666666';
const accountId = '55555555-5555-4555-8555-555555555555';

Map<String, dynamic> accountJson() => {
  'accountId': accountId,
  'username': 'patient01@example.org',
  'staffId': null,
  'patientId': patientId,
  'role': 'PATIENT',
  'isActive': true,
  'lastLoginAt': null,
  'createdAt': '2026-11-01T10:00:00Z',
  'updatedAt': '2026-11-01T10:00:00Z',
};

Account sampleAccount() => const Account(
  id: accountId,
  username: 'patient01@example.org',
  staffId: null,
  patientId: patientId,
  role: 'PATIENT',
  active: true,
  lastLoginAt: null,
  createdAt: '2026-11-01T10:00:00Z',
  updatedAt: '2026-11-01T10:00:00Z',
);

class ProfileAccountStub implements ProfileAccountRepository {
  ProfileAccountSnapshot snapshot = const ProfileAccountSnapshot(
    account: null,
    profileEmail: 'patient01@example.org',
    emailSyncPending: false,
  );
  Object? profileError;
  ProfileMailDelivery? latestDelivery;
  final resetKeys = <String>[];
  final issueKeys = <String>[];
  int profileReads = 0, patientIssues = 0, patientEmailAttempts = 0;
  Future<ProfileMailDelivery> Function(String key)? resetPatientAction;
  bool failNextPatientEmailBeforeApply = false;
  final emailChanges = <(String, String, String)>[];

  @override
  Future<void> updatePatientEmail({
    required String patientId,
    required String email,
    required String reason,
  }) async {
    patientEmailAttempts++;
    if (failNextPatientEmailBeforeApply) {
      failNextPatientEmailBeforeApply = false;
      throw const ApiFailure('Lost response');
    }
    emailChanges.add((patientId, email, reason));
    snapshot = ProfileAccountSnapshot(
      account: Account(
        id: accountId,
        username: email,
        staffId: null,
        patientId: patientId,
        role: 'PATIENT',
        active: true,
        lastLoginAt: null,
        createdAt: '2026-11-01T10:00:00Z',
        updatedAt: '2026-11-01T10:00:00Z',
      ),
      profileEmail: email,
      emailSyncPending: false,
    );
  }

  @override
  Future<ProfileAccountSnapshot> patientProfile(String id) async {
    profileReads++;
    if (profileError case final error?) throw error;
    return snapshot;
  }

  @override
  Future<ProfileAccountSnapshot> staffProfile(String id) async => snapshot;

  @override
  Future<void> issuePatientAccount({
    required String patientId,
    required String idempotencyKey,
  }) async {
    issueKeys.add(idempotencyKey);
    patientIssues++;
    snapshot = ProfileAccountSnapshot(
      account: sampleAccount(),
      profileEmail: 'patient01@example.org',
      emailSyncPending: false,
    );
  }

  @override
  Future<void> issueStaffAccount({
    required String staffId,
    required String username,
    required String role,
    required String idempotencyKey,
  }) async => throw UnimplementedError();

  @override
  Future<ProfileMailDelivery> resetPatientPassword(
    String id,
    String idempotencyKey,
  ) async {
    resetKeys.add(idempotencyKey);
    final action = resetPatientAction;
    if (action == null) {
      return const ProfileMailDelivery(
        messageId: 'mail-1',
        status: ProfileMailStatus.pending,
      );
    }
    return action(idempotencyKey);
  }

  @override
  Future<ProfileMailDelivery?> latestPatientMailDelivery(String id) async =>
      latestDelivery;

  @override
  Future<ProfileMailDelivery> retryPatientMailDelivery(
    String id,
    String messageId,
  ) async => const ProfileMailDelivery(
    messageId: 'mail-1',
    status: ProfileMailStatus.pending,
  );

  @override
  Future<ProfileMailDelivery?> latestStaffMailDelivery(
    String accountId,
  ) async => null;

  @override
  Future<ProfileMailDelivery> resetStaffPassword(
    String accountId,
    String idempotencyKey,
  ) async => throw UnimplementedError();

  @override
  Future<ProfileMailDelivery> retryStaffMailDelivery(
    String accountId,
    String messageId,
  ) async => throw UnimplementedError();

  @override
  Future<void> updateStaffEmail({
    required String accountId,
    required String email,
    required String reason,
  }) async => throw UnimplementedError();
}

class ProfileAccountAdapter implements HttpClientAdapter {
  final requests = <RequestOptions>[];
  Map<String, dynamic>? staffAccount;
  Map<String, dynamic>? patientAccount;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    final data = switch ('${options.method} ${options.uri.path}') {
      'GET /api/v1/org/profile-accounts/staff/$staffId' => {
        'account':
            staffAccount ??
            (accountJson()
              ..['staffId'] = staffId
              ..['patientId'] = null
              ..['role'] = 'ADMIN'),
        'profileEmail': 'staff@example.org',
        'emailSyncPending': false,
      },
      'GET /api/v1/org/profile-accounts/patient/$patientId' => {
        'account': patientAccount ?? accountJson(),
        'profileEmail': 'patient01@example.org',
        'emailSyncPending': true,
      },
      'POST /api/v1/org/patient-accounts' => {
        'accountId': accountId,
        'patientId': patientId,
        'username': 'patient01@example.org',
        'mustChangePassword': true,
      },
      'PUT /api/v1/org/patient-accounts/$patientId/password' => {
        'messageId': 'mail-1',
        'status': 'PENDING',
      },
      'PUT /api/v1/org/patient-accounts/$patientId/email' =>
        patientAccount ?? (accountJson()..['username'] = 'updated@example.org'),
      'GET /api/v1/org/patient-accounts/$patientId/mail-delivery' => {
        'messageId': 'mail-1',
        'status': 'FAILED',
      },
      'POST /api/v1/org/patient-accounts/$patientId/mail-delivery/mail-1/retry' =>
        {'messageId': 'mail-1', 'status': 'PENDING'},
      _ => throw StateError(
        'Unexpected request: ${options.method} ${options.uri}',
      ),
    };
    return ResponseBody.fromString(
      jsonEncode({'success': true, 'data': data}),
      200,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

void main() {
  test(
    'patient email command validates owner and normalizes the wire email',
    () async {
      final adapter = ProfileAccountAdapter();
      final client = ApiClient(
        AppConfig('http://localhost:8080/api'),
        dio: Dio()..httpClientAdapter = adapter,
        token: () => 'token',
      );
      addTearDown(client.close);
      final repository = GatewayProfileAccountRepository(client);
      await UpdatePatientLoginEmail(repository).call(
        patientId: patientId,
        email: ' Updated@Example.org ',
        reason: ' Patient verified ',
      );
      expect(
        adapter.requests.single.uri.path,
        '/api/v1/org/patient-accounts/$patientId/email',
      );
      expect(adapter.requests.single.data, {
        'email': 'updated@example.org',
        'reason': 'Patient verified',
      });
      adapter.patientAccount = accountJson()..['patientId'] = staffId;
      await expectLater(
        repository.updatePatientEmail(
          patientId: patientId,
          email: 'updated@example.org',
          reason: 'Verified',
        ),
        throwsFormatException,
      );
    },
  );
  for (final repair in [false, true]) {
    testWidgets(
      repair
          ? 'nurse repairs a mismatched patient profile email'
          : 'nurse changes patient login email with an audited reason',
      (tester) async {
        final repository = ProfileAccountStub()
          ..snapshot = ProfileAccountSnapshot(
            account: sampleAccount(),
            profileEmail: repair
                ? 'legacy@example.org'
                : 'patient01@example.org',
            emailSyncPending: repair,
          );
        const access = WorkspaceAccess(
          sessionIdentity: 'patient-email-session',
          role: 'NURSE',
        );
        await tester.pumpWidget(
          ProviderScope(
            overrides: [
              profileAccountRepositoryProvider.overrideWithValue(repository),
              workspaceAccessProvider.overrideWithValue(access),
            ],
            child: const MaterialApp(
              home: Scaffold(
                body: SingleChildScrollView(
                  child: ProfileAccountPanel(
                    owner: ProfileAccountOwner.patient(id: patientId),
                    access: access,
                  ),
                ),
              ),
            ),
          ),
        );
        await tester.pumpAndSettle();
        expect(
          find.widgetWithText(TextField, 'Email đăng nhập mới'),
          findsOneWidget,
        );
        await tester.enterText(
          find.widgetWithText(TextField, 'Email đăng nhập mới'),
          repair ? 'patient01@example.org' : 'updated@example.org',
        );
        await tester.enterText(
          find.widgetWithText(TextField, 'Lý do thay đổi email'),
          'Bệnh nhân xác nhận',
        );
        final save = find.text('Lưu email mới');
        await tester.pump();
        await tester.ensureVisible(save);
        await tester.tap(save);
        await tester.pumpAndSettle();
        expect(repository.emailChanges, [
          (
            patientId,
            repair ? 'patient01@example.org' : 'updated@example.org',
            'Bệnh nhân xác nhận',
          ),
        ]);
        expect(
          repository.snapshot.account?.username,
          repair ? 'patient01@example.org' : 'updated@example.org',
        );
        expect(
          find.textContaining(
            repair ? 'Đã yêu cầu đồng bộ' : 'Đã cập nhật email đăng nhập',
          ),
          findsOneWidget,
        );
      },
    );
  }

  testWidgets(
    'uncertain same-username repair can be safely resubmitted after lookup',
    (tester) async {
      final repository = ProfileAccountStub()
        ..snapshot = ProfileAccountSnapshot(
          account: sampleAccount(),
          profileEmail: 'legacy@example.org',
          emailSyncPending: true,
        )
        ..failNextPatientEmailBeforeApply = true;
      const access = WorkspaceAccess(
        sessionIdentity: 'patient-email-repair-session',
        role: 'NURSE',
      );
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            profileAccountRepositoryProvider.overrideWithValue(repository),
            workspaceAccessProvider.overrideWithValue(access),
          ],
          child: const MaterialApp(
            home: Scaffold(
              body: SingleChildScrollView(
                child: ProfileAccountPanel(
                  owner: ProfileAccountOwner.patient(id: patientId),
                  access: access,
                ),
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      await tester.enterText(
        find.widgetWithText(TextField, 'Email đăng nhập mới'),
        'patient01@example.org',
      );
      await tester.enterText(
        find.widgetWithText(TextField, 'Lý do thay đổi email'),
        'Đã xác minh lại email',
      );
      await tester.pump();
      await tester.ensureVisible(find.text('Lưu email mới'));
      await tester.tap(find.text('Lưu email mới'));
      await tester.pumpAndSettle();
      expect(repository.patientEmailAttempts, 1);
      expect(
        find.textContaining('Chưa xác nhận được kết quả đổi email'),
        findsOneWidget,
      );

      await tester.ensureVisible(find.text('Kiểm tra kết quả đổi email'));
      await tester.tap(find.text('Kiểm tra kết quả đổi email'));
      await tester.pumpAndSettle();
      expect(
        find.textContaining('Chưa xác nhận đồng bộ'),
        findsOneWidget,
      );
      expect(find.text('Đã xác nhận email đăng nhập mới.'), findsNothing);
      expect(find.text('Lưu email mới'), findsOneWidget);
      expect(
        tester
            .widget<ButtonStyleButton>(
              find.ancestor(
                of: find.text('Cấp lại mật khẩu tạm qua email'),
                matching: find.byWidgetPredicate(
                  (widget) => widget is ButtonStyleButton,
                ),
              ),
            )
            .onPressed,
        isNull,
      );

      await tester.ensureVisible(find.text('Lưu email mới'));
      await tester.tap(find.text('Lưu email mới'));
      await tester.pumpAndSettle();
      expect(repository.patientEmailAttempts, 2);
      expect(repository.emailChanges, [
        (patientId, 'patient01@example.org', 'Đã xác minh lại email'),
      ]);
      expect(repository.snapshot.emailSyncPending, isFalse);
      expect(find.textContaining('Đã yêu cầu đồng bộ'), findsOneWidget);
    },
  );

  test(
    'profile DTO distinguishes a missing account from a pending email sync',
    () {
      final missing = ProfileAccountDto.profile({
        'account': null,
        'profileEmail': 'patient@example.org',
        'emailSyncPending': false,
      });
      final pending = ProfileAccountDto.profile({
        'account': accountJson(),
        'profileEmail': 'updated@example.org',
        'emailSyncPending': true,
      });

      expect(missing.account, isNull);
      expect(missing.profileEmail, 'patient@example.org');
      expect(pending.account?.username, 'patient01@example.org');
      expect(pending.profileEmail, 'updated@example.org');
      expect(pending.emailSyncPending, isTrue);
    },
  );

  test('profile DTO ignores secret-shaped extra fields', () {
    final responseAccount = accountJson()
      ..['password'] = 'must-never-be-shown'
      ..['passwordHash'] = 'must-never-be-shown';
    final profile = ProfileAccountDto.profile({
      'account': responseAccount,
      'profileEmail': null,
      'emailSyncPending': false,
    });

    expect(profile.account?.username, 'patient01@example.org');
    expect(profile.toString(), isNot(contains('must-never-be-shown')));
  });

  test('patient account commands use scoped paths and idempotency keys', () async {
    final adapter = ProfileAccountAdapter();
    final client = ApiClient(
      AppConfig('http://localhost:8080/api'),
      dio: Dio()..httpClientAdapter = adapter,
      token: () => 'test-token',
    );
    addTearDown(client.close);
    final repository = GatewayProfileAccountRepository(client);

    final staffProfile = await repository.staffProfile(staffId);
    final profile = await repository.patientProfile(patientId);
    await repository.issuePatientAccount(
      patientId: patientId,
      idempotencyKey: 'issue-key',
    );
    final reset = await repository.resetPatientPassword(patientId, 'reset-key');
    final latest = await repository.latestPatientMailDelivery(patientId);
    final retried = await repository.retryPatientMailDelivery(
      patientId,
      latest!.messageId,
    );

    expect(profile.emailSyncPending, isTrue);
    expect(staffProfile.account?.staffId, staffId);
    expect(reset.status, ProfileMailStatus.pending);
    expect(retried.messageId, 'mail-1');
    expect(
      adapter.requests.map(
        (request) => '${request.method} ${request.uri.path}',
      ),
      [
        'GET /api/v1/org/profile-accounts/staff/$staffId',
        'GET /api/v1/org/profile-accounts/patient/$patientId',
        'POST /api/v1/org/patient-accounts',
        'PUT /api/v1/org/patient-accounts/$patientId/password',
        'GET /api/v1/org/patient-accounts/$patientId/mail-delivery',
        'POST /api/v1/org/patient-accounts/$patientId/mail-delivery/mail-1/retry',
      ],
    );
    expect(adapter.requests[2].data, {'patientId': patientId});
    expect(adapter.requests[2].headers['Idempotency-Key'], 'issue-key');
    expect(adapter.requests[3].data, <String, Object?>{});
    expect(adapter.requests[3].headers['Idempotency-Key'], 'reset-key');
    expect(
      adapter.requests.any(
        (request) => jsonEncode(request.data).contains('password'),
      ),
      isFalse,
    );
  });

  test(
    'profile lookup rejects an account owned by a different profile',
    () async {
      final adapter = ProfileAccountAdapter();
      adapter.patientAccount = accountJson()..['patientId'] = staffId;
      final client = ApiClient(
        AppConfig('http://localhost:8080/api'),
        dio: Dio()..httpClientAdapter = adapter,
        token: () => 'test-token',
      );
      addTearDown(client.close);
      final repository = GatewayProfileAccountRepository(client);

      await expectLater(
        repository.patientProfile(patientId),
        throwsFormatException,
      );

      final wrongStaffAdapter = ProfileAccountAdapter();
      wrongStaffAdapter.staffAccount = accountJson()
        ..['staffId'] = patientId
        ..['role'] = 'DOCTOR';
      final wrongStaffClient = ApiClient(
        AppConfig('http://localhost:8080/api'),
        dio: Dio()..httpClientAdapter = wrongStaffAdapter,
        token: () => 'test-token',
      );
      addTearDown(wrongStaffClient.close);

      await expectLater(
        GatewayProfileAccountRepository(wrongStaffClient).staffProfile(staffId),
        throwsFormatException,
      );
    },
  );

  testWidgets('no account is separate from profile lookup failure', (
    tester,
  ) async {
    final repository = ProfileAccountStub();
    const access = WorkspaceAccess(
      sessionIdentity: 'nurse-session',
      role: 'NURSE',
    );
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          profileAccountRepositoryProvider.overrideWithValue(repository),
          workspaceAccessProvider.overrideWithValue(access),
        ],
        child: const MaterialApp(
          home: Scaffold(
            body: SingleChildScrollView(
              child: ProfileAccountPanel(
                owner: ProfileAccountOwner.patient(id: patientId),
                access: access,
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Chưa có tài khoản đăng nhập'), findsOneWidget);
    expect(find.text('Cấp tài khoản và gửi mật khẩu tạm'), findsOneWidget);

    repository.profileError = const ApiFailure('Profile lookup unavailable');
    await tester.tap(find.byTooltip('Tải lại thông tin tài khoản'));
    await tester.pumpAndSettle();
    expect(find.text('Profile lookup unavailable'), findsOneWidget);
    expect(find.text('Chưa có tài khoản đăng nhập'), findsNothing);
    expect(repository.profileReads, 2);
  });

  testWidgets('nurse issues a patient account and never sees a password', (
    tester,
  ) async {
    final repository = ProfileAccountStub();
    const access = WorkspaceAccess(
      sessionIdentity: 'patient-issue-session',
      role: 'NURSE',
    );
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          profileAccountRepositoryProvider.overrideWithValue(repository),
          workspaceAccessProvider.overrideWithValue(access),
          profileAccountIdempotencyKeyFactoryProvider.overrideWithValue(
            () => 'issue-key',
          ),
        ],
        child: const MaterialApp(
          home: Scaffold(
            body: SingleChildScrollView(
              child: ProfileAccountPanel(
                owner: ProfileAccountOwner.patient(id: patientId),
                access: access,
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Cấp tài khoản và gửi mật khẩu tạm'));
    await tester.pumpAndSettle();

    expect(repository.patientIssues, 1);
    expect(repository.issueKeys, ['issue-key']);
    expect(
      find.text('Tài khoản đã được cấp. Mật khẩu tạm được gửi qua email.'),
      findsOneWidget,
    );
    expect(find.textContaining('password'), findsNothing);
    expect(find.textContaining('must-never-be-shown'), findsNothing);
  });

  testWidgets('archived patient account panel is read only', (tester) async {
    final repository = ProfileAccountStub()
      ..snapshot = ProfileAccountSnapshot(
        account: sampleAccount(),
        profileEmail: 'patient01@example.org',
        emailSyncPending: false,
      );
    const access = WorkspaceAccess(
      sessionIdentity: 'archived-account-session',
      role: 'NURSE',
    );
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          profileAccountRepositoryProvider.overrideWithValue(repository),
          workspaceAccessProvider.overrideWithValue(access),
        ],
        child: const MaterialApp(
          home: Scaffold(
            body: SingleChildScrollView(
              child: ProfileAccountPanel(
                owner: ProfileAccountOwner.patient(
                  id: patientId,
                  active: false,
                ),
                access: access,
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final reset = tester.widget<OutlinedButton>(
      find.widgetWithText(OutlinedButton, 'Cấp lại mật khẩu tạm qua email'),
    );
    expect(reset.onPressed, isNull);
    expect(find.textContaining('đã lưu trữ'), findsOneWidget);
  });

  testWidgets('patient account reset is blocked while profile email differs', (
    tester,
  ) async {
    final repository = ProfileAccountStub()
      ..snapshot = ProfileAccountSnapshot(
        account: sampleAccount(),
        profileEmail: 'different@example.org',
        emailSyncPending: false,
      );
    const access = WorkspaceAccess(
      sessionIdentity: 'email-mismatch-session',
      role: 'NURSE',
    );
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          profileAccountRepositoryProvider.overrideWithValue(repository),
          workspaceAccessProvider.overrideWithValue(access),
        ],
        child: const MaterialApp(
          home: Scaffold(
            body: SingleChildScrollView(
              child: ProfileAccountPanel(
                owner: ProfileAccountOwner.patient(id: patientId),
                access: access,
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final reset = tester.widget<OutlinedButton>(
      find.widgetWithText(OutlinedButton, 'Cấp lại mật khẩu tạm qua email'),
    );
    expect(reset.onPressed, isNull);
    expect(find.textContaining('chưa khớp'), findsOneWidget);
  });

  testWidgets(
    'uncertain patient reset retries with the original idempotency key',
    (tester) async {
      final repository = ProfileAccountStub()
        ..snapshot = ProfileAccountSnapshot(
          account: sampleAccount(),
          profileEmail: 'patient01@example.org',
          emailSyncPending: false,
        )
        ..latestDelivery = const ProfileMailDelivery(
          messageId: 'mail-old',
          status: ProfileMailStatus.sent,
        );
      var attempts = 0;
      repository.resetPatientAction = (key) async {
        attempts++;
        if (attempts == 1) throw const ApiFailure('Timed out');
        return const ProfileMailDelivery(
          messageId: 'mail-new',
          status: ProfileMailStatus.pending,
        );
      };
      const access = WorkspaceAccess(
        sessionIdentity: 'patient-reset-session',
        role: 'NURSE',
      );
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            profileAccountRepositoryProvider.overrideWithValue(repository),
            workspaceAccessProvider.overrideWithValue(access),
            profileAccountIdempotencyKeyFactoryProvider.overrideWithValue(
              () => 'reset-key',
            ),
          ],
          child: const MaterialApp(
            home: Scaffold(
              body: SingleChildScrollView(
                child: ProfileAccountPanel(
                  owner: ProfileAccountOwner.patient(id: patientId),
                  access: access,
                ),
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Cấp lại mật khẩu tạm qua email'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Xác nhận đặt lại'));
      await tester.pumpAndSettle();
      expect(find.text('Timed out'), findsOneWidget);

      await tester.tap(find.text('Đối soát lần cấp lại mật khẩu vừa rồi'));
      await tester.pumpAndSettle();
      expect(repository.resetKeys, ['reset-key', 'reset-key']);
      expect(
        find.text('Đã gửi yêu cầu cấp mật khẩu tạm qua email.'),
        findsOneWidget,
      );
    },
  );

  testWidgets('pending mail status turns into sent without a manual check', (
    tester,
  ) async {
    final repository = ProfileAccountStub()
      ..snapshot = ProfileAccountSnapshot(
        account: sampleAccount(),
        profileEmail: 'patient01@example.org',
        emailSyncPending: false,
      )
      ..latestDelivery = const ProfileMailDelivery(
        messageId: 'mail-1',
        status: ProfileMailStatus.pending,
      );
    const access = WorkspaceAccess(
      sessionIdentity: 'patient-mail-poll-session',
      role: 'NURSE',
    );
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          profileAccountRepositoryProvider.overrideWithValue(repository),
          workspaceAccessProvider.overrideWithValue(access),
        ],
        child: const MaterialApp(
          home: Scaffold(
            body: SingleChildScrollView(
              child: ProfileAccountPanel(
                owner: ProfileAccountOwner.patient(id: patientId),
                access: access,
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Thư mật khẩu tạm: Đang gửi…'), findsOneWidget);
    expect(find.textContaining('Kiểm tra trạng thái'), findsNothing);

    repository.latestDelivery = const ProfileMailDelivery(
      messageId: 'mail-1',
      status: ProfileMailStatus.sent,
    );
    await tester.pump(const Duration(seconds: 2));
    await tester.pumpAndSettle();
    expect(find.text('Thư mật khẩu tạm: Gửi thành công'), findsOneWidget);
  });
}
