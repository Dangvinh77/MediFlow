import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/domain/page_result.dart';
import 'package:mediflow_mobile/core/network/api_failure.dart';
import 'package:mediflow_mobile/features/pharmacy/presentation/create_prescription_page.dart';

import 'pharmacy_fixtures.dart';
import 'pharmacy_prescription_widget_test.dart' show host, Writer, fillRx, tap;

void main() {
  testWidgets('empty catalog with totalPages zero is visible', (tester) async {
    final repository = PharmacyStub()
      ..search = (_, _, _) async => const PageResult(
        items: [],
        number: 0,
        totalPages: 0,
        totalElements: 0,
      );
    await tester.pumpWidget(host(repository, const CreatePrescriptionPage()));
    await tester.pumpAndSettle();
    expect(find.text('Không tìm thấy thuốc.'), findsNothing);
    await tap(tester, 'Tìm trong danh mục');
    expect(find.text('Không tìm thấy thuốc.'), findsOneWidget);
  });
  for (final field in ['lines[0].quantity', 'lines.0.dosage', 'patientId']) {
    testWidgets(
      'server field $field appears and gets focus after explicit draft edit',
      (tester) async {
        final writer = Writer()
          ..send = (_) async => throw ApiFailure(
            'Validation rejected',
            status: 422,
            fields: {field: 'Giá trị cần kiểm tra'},
            correlationId: 'rx-validation-trace',
          );
        await tester.pumpWidget(
          host(PharmacyStub(), const CreatePrescriptionPage(), writer: writer),
        );
        await tester.pumpAndSettle();
        await fillRx(tester);
        await tap(tester, 'Tạo đơn V1');
        await tap(tester, 'Xác nhận kê đơn');
        await tap(tester, 'Sửa bản nháp bị từ chối');
        final label = field.contains('quantity')
            ? 'Số lượng dòng 1'
            : field.contains('dosage')
            ? 'Liều dùng dòng 1'
            : 'Mã bệnh nhân';
        final input = tester.widget<TextField>(
          find.widgetWithText(TextField, label),
        );
        expect(input.focusNode!.hasFocus, isTrue);
        expect(input.decoration!.errorText, 'Giá trị cần kiểm tra');
        expect(input.enabled, isTrue);
        expect(writer.intents.length, 1);
        expect(tester.takeException(), isNull);
      },
    );
  }
  for (final status in [null, 429]) {
    testWidgets(
      'retry $status preserves frozen payload even after declining retry confirmation',
      (tester) async {
        final writer = Writer()
          ..send = (_) async => throw ApiFailure('Try later', status: status);
        await tester.pumpWidget(
          host(PharmacyStub(), const CreatePrescriptionPage(), writer: writer),
        );
        await tester.pumpAndSettle();
        await fillRx(tester);
        await tap(tester, 'Tạo đơn V1');
        await tap(tester, 'Xác nhận kê đơn');
        await tap(tester, 'Thử lại cùng mã và nội dung');
        await tap(tester, 'Quay lại');
        expect(writer.intents.length, 1);
        await tap(tester, 'Thử lại cùng mã và nội dung');
        await tap(tester, 'Xác nhận kê đơn');
        expect(writer.intents.length, 2);
        expect(identical(writer.intents[0], writer.intents[1]), isTrue);
        expect(writer.intents[1].command.lines.single.quantity, 2);
      },
    );
  }
}
