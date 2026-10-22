import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/config/app_config.dart';
import 'package:mediflow_mobile/core/domain/workspace_access.dart';
import 'package:mediflow_mobile/core/network/api_client.dart';
import 'package:mediflow_mobile/core/network/gateway_reference_reader.dart';
import 'package:mediflow_mobile/core/providers.dart';
import 'package:mediflow_mobile/core/widgets/reference_widgets.dart';

import 'pharmacy_fixtures.dart';

void main() {
  test(
    'reference reader validates identity and retains server names',
    () async {
      final adapter = PharmacyHttpAdapter();
      final client = ApiClient(
        AppConfig('http://localhost:8080/api'),
        dio: Dio()..httpClientAdapter = adapter,
      );
      addTearDown(client.close);
      final reader = GatewayReferenceReader(client);
      adapter.data = {'maBenhNhan': drugId, 'hoTen': 'Nguyễn Minh An'};
      expect(
        (await reader.read(ReferenceKind.patient, drugId)).name,
        'Nguyễn Minh An',
      );
      expect(adapter.requests.single.uri.path, '/api/v1/patients/$drugId');
      adapter.data = {'maBenhNhan': otherDrugId, 'hoTen': 'Tên sai'};
      await expectLater(
        reader.read(ReferenceKind.patient, drugId),
        throwsFormatException,
      );
      await expectLater(
        reader.read(ReferenceKind.staff, '../bad'),
        throwsArgumentError,
      );
      expect(adapter.requests.length, 2);
    },
  );

  testWidgets('anonymous reference labels never expose IDs or query patients', (
    tester,
  ) async {
    await tester.pumpWidget(
      const ProviderScope(
        child: MaterialApp(
          home: Scaffold(
            body: ReferenceLabel(
              kind: ReferenceKind.patient,
              id: drugId,
              label: 'Bệnh nhân',
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.textContaining(drugId), findsNothing);
    expect(find.textContaining('Không có quyền'), findsOneWidget);
  });

  testWidgets('authorized display resolves names without printing UUID', (
    tester,
  ) async {
    final adapter = PharmacyHttpAdapter()
      ..data = {'maBenhNhan': drugId, 'hoTen': 'Nguyễn Minh An'};
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
            const WorkspaceAccess(sessionIdentity: 'a', role: 'DOCTOR'),
          ),
        ],
        child: const MaterialApp(
          home: Scaffold(
            body: ReferenceLabel(
              kind: ReferenceKind.patient,
              id: drugId,
              label: 'Bệnh nhân',
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Bệnh nhân: Nguyễn Minh An'), findsOneWidget);
    expect(find.textContaining(drugId), findsNothing);
  });
}
