import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/network/api_failure.dart';
import 'package:mediflow_mobile/core/widgets/reference_widgets.dart';
import 'package:mediflow_mobile/features/medical_record/domain/medical_record.dart';
import 'package:mediflow_mobile/features/medical_record/presentation/medical_record_page.dart';

import 'clinical_completion_workflow_test.dart'
    show MedicalRecordFake, host, patientId, record, recordId;

const writable = RecordCapabilities(
  recordId: recordId,
  patientId: patientId,
  canRead: true,
  canWriteContent: true,
  canComplete: true,
);
const denied = RecordCapabilities(
  recordId: recordId,
  patientId: patientId,
  canRead: true,
  canWriteContent: false,
  canComplete: false,
  writeBlockedReason: 'NOT_ASSIGNED_DOCTOR',
);

class AccessFake extends MedicalRecordFake {
  RecordCapabilities capabilitiesResult = writable;
  bool capabilitiesFail = false;
  final declarations = <(String, String, String?)>[];

  @override
  Future<RecordCapabilities> capabilities(String recordId) async {
    if (capabilitiesFail) throw const ApiFailure('Lỗi', status: 500);
    return capabilitiesResult;
  }

  @override
  Future<void> declareAccess({
    required String patientId,
    required String purpose,
    String? reason,
  }) async {
    declarations.add((patientId, purpose, reason));
  }
}

Future<void> pumpDetail(
  WidgetTester tester,
  AccessFake records, {
  String role = 'DOCTOR',
}) async {
  await tester.binding.setSurfaceSize(const Size(800, 3000));
  addTearDown(() => tester.binding.setSurfaceSize(null));
  await tester.pumpWidget(
    host(
      child: const MedicalRecordDetailPage(recordId: recordId),
      role: role,
      records: records,
    ),
  );
  await tester.pumpAndSettle();
}

ApiFailure refusal(String code) => ApiFailure('Từ chối', status: 403, code: code);

void main() {
  testWidgets('shows edit actions when capability allows writing', (
    tester,
  ) async {
    await pumpDetail(tester, AccessFake());
    expect(find.text('Sửa nội dung khám'), findsOneWidget);
    expect(find.text('Thêm chẩn đoán'), findsOneWidget);
  });

  testWidgets('hides edit actions when capability denies writing', (
    tester,
  ) async {
    await pumpDetail(tester, AccessFake()..capabilitiesResult = denied);
    expect(find.text('Sửa nội dung khám'), findsNothing);
    expect(find.text('Thêm chẩn đoán'), findsNothing);
    expect(find.textContaining('Chỉ bác sĩ phụ trách'), findsOneWidget);
  });

  testWidgets('keeps the record read only when capabilities fail to load', (
    tester,
  ) async {
    await pumpDetail(tester, AccessFake()..capabilitiesFail = true);
    expect(find.text('Sửa nội dung khám'), findsNothing);
    expect(find.textContaining('Chưa xác định được quyền ghi'), findsOneWidget);
  });

  testWidgets('shows purpose form for RECORD_ACCESS_PURPOSE_REQUIRED', (
    tester,
  ) async {
    var attempts = 0;
    final records = AccessFake()
      ..capabilitiesResult = denied
      ..onGet = (_) async {
        if (attempts++ == 0) throw refusal('RECORD_ACCESS_PURPOSE_REQUIRED');
        return record();
      };
    await pumpDetail(tester, records);

    await tester.tap(find.widgetWithText(ChoiceChip, 'Khám bệnh'));
    await tester.pump();
    await tester.tap(find.widgetWithText(FilledButton, 'Xác nhận mục đích'));
    await tester.pumpAndSettle();

    expect(records.declarations, [(patientId, 'CONSULTATION', null)]);
    expect(attempts, 2);
    expect(find.byType(ChoiceChip), findsNothing);
  });

  testWidgets('nurse is offered emergency only', (tester) async {
    final records = AccessFake()
      ..onGet = (_) async => throw refusal('RECORD_ACCESS_PURPOSE_REQUIRED');
    await pumpDetail(tester, records, role: 'NURSE');
    expect(find.byType(ChoiceChip), findsOneWidget);
    expect(find.widgetWithText(ChoiceChip, 'Cấp cứu'), findsOneWidget);
  });

  testWidgets('asks for a reason only where one is required', (tester) async {
    final records = AccessFake()
      ..onGet = (_) async => throw refusal('RECORD_ACCESS_PURPOSE_REQUIRED');
    await pumpDetail(tester, records);
    expect(find.byType(TextField), findsNothing);
    await tester.tap(find.widgetWithText(ChoiceChip, 'Đánh giá chất lượng'));
    await tester.pump();
    expect(find.byType(TextField), findsOneWidget);
    final button = tester.widget<FilledButton>(
      find.widgetWithText(FilledButton, 'Xác nhận mục đích'),
    );
    expect(button.onPressed, isNull);
  });

  testWidgets('shows denied state for RECORD_ACCESS_DENIED', (tester) async {
    final records = AccessFake()
      ..onGet = (_) async => throw refusal('RECORD_ACCESS_DENIED');
    await pumpDetail(tester, records);
    expect(find.text('Bạn không có quyền xem hồ sơ khám này.'), findsOneWidget);
    expect(find.byType(ChoiceChip), findsNothing);
    expect(find.textContaining('Thử lại'), findsNothing);
  });

  testWidgets('shows completed examination wording', (tester) async {
    final records = AccessFake()
      ..capabilitiesResult = denied
      ..onGet = (_) async => record(status: 'COMPLETED');
    await pumpDetail(tester, records);
    expect(find.text('Đã hoàn tất khám'), findsOneWidget);
    expect(
      find.text(
        'Hoàn tất khám không đồng nghĩa đã ký xác nhận hoặc chuyển lưu trữ.',
      ),
      findsOneWidget,
    );
  });

  testWidgets('patient lookup offers the purpose form', (tester) async {
    await tester.pumpWidget(
      host(
        child: const MedicalRecordPage(),
        records: _LookupFake(),
      ),
    );
    await tester.pumpAndSettle();
    tester
        .widget<ReferencePicker>(find.byType(ReferencePicker))
        .onChanged!(patientId);
    await tester.pumpAndSettle();
    if (find.byType(ChoiceChip).evaluate().isEmpty) {
      await tester.tap(find.byType(FilledButton).first);
      await tester.pumpAndSettle();
    }
    expect(find.widgetWithText(ChoiceChip, 'Khám bệnh'), findsOneWidget);
  });

  testWidgets('purpose form fits 320px at text scale 2', (tester) async {
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    tester.platformDispatcher.textScaleFactorTestValue = 2;
    addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
    final records = AccessFake()
      ..onGet = (_) async => throw refusal('RECORD_ACCESS_PURPOSE_REQUIRED');
    await pumpDetail(tester, records);
    expect(tester.takeException(), isNull);
    expect(find.byType(ChoiceChip), findsWidgets);
  });
}

class _LookupFake extends AccessFake {
  @override
  Future<List<MedicalRecord>> byPatient(String id) async =>
      throw refusal('RECORD_ACCESS_PURPOSE_REQUIRED');
}
