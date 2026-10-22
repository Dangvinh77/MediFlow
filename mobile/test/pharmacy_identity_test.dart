import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/config/app_config.dart';
import 'package:mediflow_mobile/core/network/api_client.dart';
import 'package:mediflow_mobile/core/network/api_failure.dart';
import 'package:mediflow_mobile/features/pharmacy/data/gateway_pharmacy_repository.dart';
import 'package:mediflow_mobile/features/pharmacy/domain/pharmacy_identity.dart';
import 'package:mediflow_mobile/features/pharmacy/presentation/pharmacy_boundary.dart';
import 'package:mediflow_mobile/features/pharmacy/presentation/pharmacy_identity_label.dart';
import 'package:mediflow_mobile/features/pharmacy/presentation/providers.dart';

import 'pharmacy_fixtures.dart';

class IdentityRepository extends PharmacyStub
    implements PharmacyIdentityReader {
  final calls = <(PharmacyIdentityKind, String)>[];
  Future<String> Function(PharmacyIdentityKind, String)? lookup;
  @override
  Future<String> identityName(PharmacyIdentityKind kind, String id) {
    calls.add((kind, id));
    return lookup?.call(kind, id) ?? Future.value('Tên $id');
  }
}

Widget labels(String id) => Column(
  children: [
    PharmacyIdentityLabel(
      kind: PharmacyIdentityKind.patient,
      id: id,
      label: 'Bệnh nhân',
    ),
    PharmacyIdentityLabel(
      kind: PharmacyIdentityKind.patient,
      id: id,
      label: 'Bệnh nhân',
    ),
  ],
);

Future<void> settleNames(WidgetTester tester) async {
  await tester.pump(const Duration(milliseconds: 350));
  await tester.pumpAndSettle();
}

void main() {
  test(
    'public identity transport uses exact wire fields and unchanged UUIDs',
    () async {
      final adapter = PharmacyHttpAdapter();
      final client = ApiClient(
        AppConfig('http://localhost:8080/api'),
        dio: Dio()..httpClientAdapter = adapter,
      );
      addTearDown(client.close);
      final repository = GatewayPharmacyRepository(client);
      for (final (kind, path, idField, nameField, name) in [
        (
          PharmacyIdentityKind.patient,
          'patients',
          'maBenhNhan',
          'hoTen',
          'Nguyễn An',
        ),
        (
          PharmacyIdentityKind.doctor,
          'org/staff',
          'staffId',
          'fullName',
          'Bác sĩ Bình',
        ),
        (
          PharmacyIdentityKind.department,
          'org/departments',
          'departmentId',
          'departmentName',
          'Khoa Nội',
        ),
      ]) {
        adapter.data = {idField: drugId, nameField: name};
        expect(await repository.identityName(kind, drugId), name);
        expect(adapter.requests.last.uri.path, '/api/v1/$path/$drugId');
        expect(adapter.requests.last.method, 'GET');
        adapter.data = {idField: otherDrugId, nameField: '$name thứ hai'};
        expect(
          await repository.identityName(kind, otherDrugId),
          '$name thứ hai',
        );
        adapter.data = {idField: otherDrugId, nameField: 'Sai đối tượng'};
        await expectLater(
          repository.identityName(kind, drugId),
          throwsFormatException,
        );
      }
      final count = adapter.requests.length;
      await expectLater(
        repository.identityName(PharmacyIdentityKind.patient, '../bad'),
        throwsArgumentError,
      );
      expect(adapter.requests.length, count);
    },
  );

  testWidgets('deduplicates visible names and keeps UUIDs secondary', (
    tester,
  ) async {
    final repository = IdentityRepository();
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          pharmacyRepositoryProvider.overrideWithValue(repository),
          pharmacyAccessProvider.overrideWithValue(
            const PharmacyAccess(role: 'DOCTOR', sessionIdentity: 'one'),
          ),
        ],
        child: MaterialApp(home: Scaffold(body: labels(drugId))),
      ),
    );
    await settleNames(tester);
    expect(find.text('Bệnh nhân: Tên $drugId'), findsNWidgets(2));
    expect(find.text(drugId), findsNWidgets(2));
    expect(repository.calls, [(PharmacyIdentityKind.patient, drugId)]);
  });

  testWidgets('PHARMACIST never calls forbidden public identities', (
    tester,
  ) async {
    final repository = IdentityRepository();
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          pharmacyRepositoryProvider.overrideWithValue(repository),
          pharmacyAccessProvider.overrideWithValue(
            const PharmacyAccess(role: 'PHARMACIST', sessionIdentity: 'one'),
          ),
        ],
        child: MaterialApp(home: Scaffold(body: labels(drugId))),
      ),
    );
    await settleNames(tester);
    expect(repository.calls, isEmpty);
    expect(
      find.text('Bệnh nhân: Tài khoản chưa được cấp quyền tra cứu tên'),
      findsNWidgets(2),
    );
    expect(find.text(drugId), findsNWidgets(2));
  });

  for (final status in [403, 404, 503]) {
    testWidgets('lookup $status preserves the resource ID', (tester) async {
      final repository = IdentityRepository()
        ..lookup = (_, _) =>
            Future.error(ApiFailure('Failure', status: status));
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            pharmacyRepositoryProvider.overrideWithValue(repository),
            pharmacyAccessProvider.overrideWithValue(
              const PharmacyAccess(role: 'ADMIN', sessionIdentity: 'one'),
            ),
          ],
          child: MaterialApp(home: Scaffold(body: labels(drugId))),
        ),
      );
      await settleNames(tester);
      expect(find.text(drugId), findsNWidgets(2));
      expect(find.textContaining('Đang tra cứu'), findsNothing);
      expect(find.textContaining('Tên $drugId'), findsNothing);
      expect(repository.calls.length, 1);
      expect(
        find.text('Tra cứu Bệnh nhân lại'),
        status == 503 ? findsNWidgets(2) : findsNothing,
      );
    });
  }

  testWidgets(
    'a previous patient response never replaces the selected patient',
    (tester) async {
      final old = Completer<String>();
      final repository = IdentityRepository()
        ..lookup = (_, id) =>
            id == drugId ? old.future : Future.value('Bệnh nhân mới');
      Widget host(String id) => ProviderScope(
        overrides: [
          pharmacyRepositoryProvider.overrideWithValue(repository),
          pharmacyAccessProvider.overrideWithValue(
            const PharmacyAccess(role: 'DOCTOR', sessionIdentity: 'one'),
          ),
        ],
        child: MaterialApp(home: Scaffold(body: labels(id))),
      );
      await tester.pumpWidget(host(drugId));
      await settleNames(tester);
      await tester.pumpWidget(host(otherDrugId));
      await settleNames(tester);
      old.complete('Bệnh nhân cũ');
      await tester.pumpAndSettle();
      expect(find.text('Bệnh nhân: Bệnh nhân mới'), findsNWidgets(2));
      expect(find.textContaining('Bệnh nhân cũ'), findsNothing);
    },
  );

  testWidgets(
    'logout and new same-role session discard cached names and late results',
    (tester) async {
      final old = Completer<String>();
      PharmacyAccess? access = const PharmacyAccess(
        role: 'DOCTOR',
        sessionIdentity: 'one',
      );
      final repository = IdentityRepository()
        ..lookup = (_, _) => access?.sessionIdentity == 'one'
            ? old.future
            : Future.value('Phiên mới');
      final container = ProviderContainer(
        overrides: [
          pharmacyRepositoryProvider.overrideWithValue(repository),
          pharmacyAccessProvider.overrideWith((ref) => access),
        ],
      );
      addTearDown(container.dispose);
      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: MaterialApp(
            home: Scaffold(
              body: PharmacyBoundary(builder: (_) => labels(drugId)),
            ),
          ),
        ),
      );
      await settleNames(tester);
      access = null;
      container.invalidate(pharmacyAccessProvider);
      await tester.pump();
      expect(find.text(drugId), findsNothing);
      access = const PharmacyAccess(role: 'DOCTOR', sessionIdentity: 'two');
      container.invalidate(pharmacyAccessProvider);
      await tester.pump();
      await settleNames(tester);
      old.complete('Phiên cũ');
      await tester.pumpAndSettle();
      expect(find.text('Bệnh nhân: Phiên mới'), findsNWidgets(2));
      expect(find.textContaining('Phiên cũ'), findsNothing);
      expect(repository.calls.length, 2);
    },
  );
}
