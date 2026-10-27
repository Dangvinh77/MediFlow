import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/widgets/reference_widgets.dart';
import 'package:mediflow_mobile/features/pharmacy/domain/prescribing_source.dart';
import 'package:mediflow_mobile/features/pharmacy/presentation/create_prescription_page.dart';

import 'pharmacy_fixtures.dart';
import 'pharmacy_prescription_widget_test.dart'
    show host, Writer, tap, pumpCommand;
import 'reference_picker_test_support.dart';

PrescribingSource source({
  String patient = otherDrugId,
  String record = drugId,
  List<String> prices = const ['RX-CONFIRMED'],
}) => PrescribingSource(
  recordId: record,
  patientId: patient,
  doctorId: drugId,
  departmentId: otherDrugId,
  careEpisodeId: rxId,
  priceCodes: prices,
);

class SourceRepository extends PharmacyStub implements PrescribingSourceReader {
  final reads = <String>[];
  Future<PrescribingSource> Function(String)? load;
  @override
  Future<PrescribingSource> prescribingSource(String id) {
    reads.add(id);
    return load?.call(id) ?? Future.value(source(record: id));
  }
}

class PagedReferences extends ReferencePickerFake {
  PagedReferences()
    : super({
        ReferenceKind.patient: [
          const ReferenceOption(id: otherDrugId, name: 'An'),
        ],
      });
  @override
  Future<ReferencePage> search(
    ReferenceKind kind, {
    String query = '',
    int page = 0,
    String? scope,
  }) {
    if (kind == ReferenceKind.record) {
      return Future.value(
        ReferencePage([
          ReferenceOption(
            id: page == 0 ? drugId : rxId,
            name: 'Ho so ${page + 1}',
          ),
        ], hasMore: page == 0),
      );
    }
    return super.search(kind, query: query, page: page, scope: scope);
  }
}

void main() {
  testWidgets(
    'one visible record is not auto-selected when another page exists',
    (tester) async {
      final repository = SourceRepository();
      await tester.pumpWidget(
        host(
          repository,
          const CreatePrescriptionPage(),
          writer: Writer(),
          referenceReader: PagedReferences(),
        ),
      );
      await tester.pumpAndSettle();
      await pickReference(tester, label: 'Bệnh nhân', name: 'An');
      expect(repository.reads, isEmpty);
      await tap(tester, 'Tải thêm hồ sơ');
      expect(repository.reads, isEmpty);
      final dropdown = tester.widget<DropdownButton<String>>(
        find.byType(DropdownButton<String>),
      );
      expect(dropdown.items!.map((item) => item.value), [drugId, rxId]);
    },
  );
  testWidgets(
    'single record auto-links doctor, department and price and sends those exact IDs',
    (tester) async {
      final repository = SourceRepository();
      final writer = Writer();
      await tester.pumpWidget(
        host(repository, const CreatePrescriptionPage(), writer: writer),
      );
      await tester.pumpAndSettle();
      await pickReference(tester, label: 'Bệnh nhân', name: 'Nguyen An');
      expect(repository.reads, [drugId]);
      expect(find.text('Ho so An'), findsOneWidget);
      expect(find.text('Bác sĩ phụ trách: Tran Binh'), findsOneWidget);
      expect(find.text('Khoa khám: Khoa Ngoai tru'), findsOneWidget);
      expect(find.byTooltip('Chọn Khoa kê đơn'), findsNothing);
      expect(find.byTooltip('Chọn Bác sĩ kê đơn'), findsNothing);
      expect(find.widgetWithText(TextField, 'Mã giá Billing'), findsNothing);
      await tap(tester, 'Tìm trong danh mục');
      final choose = find.byTooltip('Chọn Paracetamol 500 mg');
      await tester.ensureVisible(choose);
      await tester.tap(choose);
      await tester.pumpAndSettle();
      await tap(tester, 'Tạo đơn thuốc');
      await tap(tester, 'Xác nhận kê đơn');
      await pumpCommand(tester);
      expect(writer.intents, hasLength(1));
      final context = writer.intents.single.command.context;
      expect(context.patientId, otherDrugId);
      expect(context.doctorId, drugId);
      expect(context.departmentId, otherDrugId);
      expect(context.careEpisodeId, rxId);
      expect(context.priceCode, 'RX-CONFIRMED');
    },
  );

  testWidgets('multiple records require a patient-filtered selection', (
    tester,
  ) async {
    final repository = SourceRepository();
    final refs = ReferencePickerFake({
      ReferenceKind.patient: [
        const ReferenceOption(id: otherDrugId, name: 'An'),
      ],
      ReferenceKind.record: [
        const ReferenceOption(id: drugId, name: 'Ho so 1'),
        const ReferenceOption(id: rxId, name: 'Ho so 2'),
      ],
      ReferenceKind.doctor: [const ReferenceOption(id: drugId, name: 'Binh')],
      ReferenceKind.department: [
        const ReferenceOption(id: otherDrugId, name: 'Noi'),
      ],
      ReferenceKind.appointment: [
        const ReferenceOption(id: rxId, name: 'Luot kham'),
      ],
    });
    await tester.pumpWidget(
      host(
        repository,
        const CreatePrescriptionPage(),
        writer: Writer(),
        referenceReader: refs,
      ),
    );
    await tester.pumpAndSettle();
    await pickReference(tester, label: 'Bệnh nhân', name: 'An');
    expect(repository.reads, isEmpty);
    expect(refs.searches.last.scope, otherDrugId);
    await tester.tap(find.byType(DropdownButton<String>));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Ho so 2').last);
    await tester.pumpAndSettle();
    expect(repository.reads, [rxId]);
  });

  testWidgets('changing patient discards a late context and blocks create', (
    tester,
  ) async {
    final pending = Completer<PrescribingSource>();
    final repository = SourceRepository()..load = (_) => pending.future;
    final writer = Writer();
    final refs = ReferencePickerFake({
      ReferenceKind.patient: [
        const ReferenceOption(id: otherDrugId, name: 'An'),
        const ReferenceOption(id: rxId, name: 'Binh'),
      ],
      ReferenceKind.record: [const ReferenceOption(id: drugId, name: 'Ho so')],
    });
    await tester.pumpWidget(
      host(
        repository,
        const CreatePrescriptionPage(),
        writer: writer,
        referenceReader: refs,
      ),
    );
    await tester.pumpAndSettle();
    // Choose without settling the deliberately pending progress indicator.
    await tester.tap(find.byTooltip('Chọn Bệnh nhân'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('An').last);
    await tester.pump();
    await tester.tap(find.byTooltip('Chọn Bệnh nhân'));
    await tester.pump();
    await tester.pump(const Duration(seconds: 1));
    await tester.tap(find.text('Binh').last);
    await tester.pump();
    pending.complete(source());
    await tester.pumpAndSettle();
    expect(find.text('Hồ sơ không thuộc bệnh nhân đã chọn.'), findsOneWidget);
    expect(find.textContaining('Bác sĩ phụ trách:'), findsNothing);
    await tap(tester, 'Tạo đơn thuốc');
    expect(writer.intents, isEmpty);
  });

  testWidgets(
    'source failure exposes retry and cannot submit without verified context',
    (tester) async {
      var fail = true;
      final repository = SourceRepository()
        ..load = (_) async {
          if (fail) throw const FormatException('Nguồn chưa sẵn sàng');
          return source();
        };
      final writer = Writer();
      await tester.pumpWidget(
        host(repository, const CreatePrescriptionPage(), writer: writer),
      );
      await tester.pumpAndSettle();
      await pickReference(tester, label: 'Bệnh nhân', name: 'Nguyen An');
      expect(find.text('Nguồn chưa sẵn sàng'), findsOneWidget);
      await tap(tester, 'Tạo đơn thuốc');
      expect(writer.intents, isEmpty);
      fail = false;
      await tap(tester, 'Thử lại');
      expect(find.text('Khoa khám: Khoa Ngoai tru'), findsOneWidget);
    },
  );
}
