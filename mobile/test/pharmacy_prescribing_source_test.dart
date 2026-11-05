import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/domain/page_result.dart';
import 'package:mediflow_mobile/core/network/api_failure.dart';
import 'package:mediflow_mobile/core/widgets/reference_widgets.dart';
import 'package:mediflow_mobile/features/pharmacy/data/pharmacy_dto.dart';
import 'package:mediflow_mobile/features/pharmacy/domain/prescription.dart';
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
    'record panel reads by the authoritative patient and shows only this record',
    (tester) async {
      final repository = SourceRepository()
        ..prescriptionSearch = (_, _, _, page, size) async => PageResult(
          items: [
            PharmacyDto.prescription({
              ...prescriptionJson(),
              'prescriptionId': rxId,
              'recordId': drugId,
              'patientId': otherDrugId,
            }),
            PharmacyDto.prescription({
              ...prescriptionJson(),
              'prescriptionId': otherDrugId,
              'recordId': rxId,
              'patientId': otherDrugId,
            }),
          ],
          number: page,
          totalPages: 1,
          totalElements: 2,
        );
      await tester.pumpWidget(
        host(
          repository,
          ListView(
            children: const [
              RecordPrescriptionPanel(
                recordId: drugId,
                patientId: otherDrugId,
                canCreate: false,
              ),
            ],
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(repository.prescriptionSearches.single.patientId, otherDrugId);
      expect(repository.prescriptionSearches.single.page, 0);
      expect(find.textContaining('Mã đơn: $rxId'), findsOneWidget);
      expect(find.textContaining('Mã đơn: $otherDrugId'), findsNothing);
      expect(find.text('Đơn thuốc · 20/10/2026'), findsOneWidget);
      expect(find.textContaining('ACTIVE'), findsNothing);
      expect(find.byTooltip('Xem chi tiết đơn thuốc'), findsOneWidget);
      expect(find.text('Kê đơn ngoại trú'), findsNothing);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('record panel drops prescription rows when the session changes', (
    tester,
  ) async {
    final repository = SourceRepository()
      ..prescriptionSearch = (_, patientId, _, page, _) async => PageResult(
        items: [
          PharmacyDto.prescription({
            ...prescriptionJson(),
            'recordId': drugId,
            'patientId': patientId,
          }),
        ],
        number: page,
        totalPages: 1,
        totalElements: 1,
      );
    Widget page(String identity) => host(
      repository,
      ListView(
        children: const [
          RecordPrescriptionPanel(
            recordId: drugId,
            patientId: otherDrugId,
            canCreate: false,
          ),
        ],
      ),
      identity: identity,
    );

    await tester.pumpWidget(page('session-one'));
    await tester.pumpAndSettle();
    expect(find.textContaining('Mã đơn: $rxId'), findsOneWidget);

    repository.prescriptionSearch = (_, _, _, page, _) async => PageResult(
      items: const [],
      number: page,
      totalPages: 1,
      totalElements: 0,
    );
    await tester.pumpWidget(page('session-two'));
    await tester.pumpAndSettle();

    expect(repository.prescriptionSearches, hasLength(2));
    expect(find.textContaining('Mã đơn: $rxId'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'record workspace preloads the exact source and locks patient and record',
    (tester) async {
      final repository = SourceRepository();
      final references = PagedReferences();
      await tester.pumpWidget(
        host(
          repository,
          ListView(
            children: const [
              CreatePrescriptionPage(
                initialPatientId: otherDrugId,
                initialRecordId: drugId,
                embedded: true,
              ),
            ],
          ),
          writer: Writer(),
          referenceReader: references,
        ),
      );
      await tester.pumpAndSettle();

      expect(repository.reads, [drugId]);
      expect(references.searches, isEmpty);
      expect(find.byTooltip('Chọn Bệnh nhân'), findsNothing);
      expect(find.byType(DropdownButton<String>), findsNothing);
      expect(
        find.text('Đã liên kết lượt khám ngoại trú từ hồ sơ.'),
        findsOneWidget,
      );
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('missing or closed record context cannot create a prescription', (
    tester,
  ) async {
    final repository = SourceRepository()
      ..load = (_) async => throw const FormatException(
        'Hồ sơ đã đóng hoặc chưa có nguồn kê đơn ngoại trú hợp lệ.',
      );
    final writer = Writer();
    await tester.pumpWidget(
      host(
        repository,
        ListView(
          children: const [
            CreatePrescriptionPage(
              initialPatientId: otherDrugId,
              initialRecordId: drugId,
              embedded: true,
            ),
          ],
        ),
        writer: writer,
      ),
    );
    await tester.pumpAndSettle();

    expect(find.textContaining('Chưa đọc được thông tin.'), findsOneWidget);
    await tap(tester, 'Tạo đơn thuốc');
    expect(writer.intents, isEmpty);
    expect(find.byTooltip('Chọn Bệnh nhân'), findsNothing);
  });

  testWidgets(
    'embedded creation keeps the returned prescription receipt in the record',
    (tester) async {
      final writer = Writer()
        ..send = (_) async => PharmacyDto.prescription({
          ...prescriptionJson(),
          'recordId': drugId,
          'patientId': otherDrugId,
          'doctorId': drugId,
          'departmentId': otherDrugId,
        });
      var createdCallbacks = 0;
      await tester.pumpWidget(
        host(
          SourceRepository(),
          ListView(
            children: [
              CreatePrescriptionPage(
                initialPatientId: otherDrugId,
                initialRecordId: drugId,
                embedded: true,
                onCreated: (_) => createdCallbacks++,
              ),
            ],
          ),
          writer: writer,
        ),
      );
      await tester.pumpAndSettle();
      await tap(tester, 'Tìm trong danh mục');
      final choose = find.byTooltip('Chọn Paracetamol 500 mg');
      await tester.ensureVisible(choose);
      await tester.tap(choose);
      await tester.pumpAndSettle();
      await tap(tester, 'Tạo đơn thuốc');
      await tap(tester, 'Xác nhận kê đơn');
      await pumpCommand(tester);

      expect(createdCallbacks, 1);
      expect(find.text('Đã tạo đơn thuốc thành công.'), findsOneWidget);
      expect(find.text('Mã đơn thuốc: $rxId'), findsOneWidget);
      expect(find.text('Tổng tiền theo máy chủ: 5000.5 đồng'), findsOneWidget);
    },
  );

  testWidgets('a late create receipt from a previous session is discarded', (
    tester,
  ) async {
    final pending = Completer<Prescription>();
    final writer = Writer()..send = (_) => pending.future;
    final repository = SourceRepository();
    var createdCallbacks = 0;
    Widget page(String identity) => host(
      repository,
      ListView(
        children: [
          CreatePrescriptionPage(
            initialPatientId: otherDrugId,
            initialRecordId: drugId,
            embedded: true,
            onCreated: (_) => createdCallbacks++,
          ),
        ],
      ),
      writer: writer,
      identity: identity,
    );

    await tester.pumpWidget(page('session-one'));
    await tester.pumpAndSettle();
    await tap(tester, 'Tìm trong danh mục');
    final choose = find.byTooltip('Chọn Paracetamol 500 mg');
    await tester.ensureVisible(choose);
    await tester.tap(choose);
    await tester.pumpAndSettle();
    await tap(tester, 'Tạo đơn thuốc');
    await tester.tap(find.text('Xác nhận kê đơn'));
    await tester.pump();
    await tester.pump(const Duration(seconds: 1));
    expect(writer.intents, hasLength(1));

    await tester.pumpWidget(page('session-two'));
    await tester.pumpAndSettle();
    pending.complete(
      PharmacyDto.prescription({
        ...prescriptionJson(),
        'recordId': drugId,
        'patientId': otherDrugId,
        'doctorId': drugId,
        'departmentId': otherDrugId,
      }),
    );
    await tester.pumpAndSettle();

    expect(createdCallbacks, 0);
    expect(find.text('Đã tạo đơn thuốc thành công.'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'unknown embedded create outcome blocks leaving and retries the same intent',
    (tester) async {
      var attempts = 0;
      final writer = Writer()
        ..send = (_) async {
          attempts++;
          if (attempts == 1) {
            throw const ApiFailure('Unknown create outcome', status: 503);
          }
          return PharmacyDto.prescription({
            ...prescriptionJson(),
            'recordId': drugId,
            'patientId': otherDrugId,
            'doctorId': drugId,
            'departmentId': otherDrugId,
          });
        };
      final repository = SourceRepository();
      final recordOpen = ValueNotifier(true);
      await tester.pumpWidget(
        host(
          repository,
          ValueListenableBuilder<bool>(
            valueListenable: recordOpen,
            builder: (context, canCreate, _) => ListView(
              children: [
                RecordPrescriptionPanel(
                  recordId: drugId,
                  patientId: otherDrugId,
                  canCreate: canCreate,
                ),
              ],
            ),
          ),
          writer: writer,
        ),
      );
      final navigator = tester.state<NavigatorState>(
        find.byType(Navigator).first,
      );
      navigator.push<void>(
        MaterialPageRoute<void>(
          builder: (_) => Scaffold(
            key: const ValueKey('prescription-source-page'),
            body: ValueListenableBuilder<bool>(
              valueListenable: recordOpen,
              builder: (context, canCreate, _) => ListView(
                children: [
                  RecordPrescriptionPanel(
                    recordId: drugId,
                    patientId: otherDrugId,
                    canCreate: canCreate,
                  ),
                ],
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tap(tester, 'Tìm trong danh mục');
      final choose = find.byTooltip('Chọn Paracetamol 500 mg');
      await tester.ensureVisible(choose);
      await tester.tap(choose);
      await tester.pumpAndSettle();
      await tap(tester, 'Tạo đơn thuốc');
      await tap(tester, 'Xác nhận kê đơn');
      await pumpCommand(tester);
      final original = writer.intents.single;

      await navigator.maybePop();
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('prescription-source-page')),
        findsOneWidget,
      );
      expect(
        find.textContaining('Chưa rõ đơn đã được tạo hay chưa.'),
        findsOneWidget,
      );
      recordOpen.value = false;
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('prescription-source-page')),
        findsOneWidget,
      );
      expect(
        find.textContaining('Chưa rõ đơn đã được tạo hay chưa.'),
        findsOneWidget,
      );
      await tester.pump(const Duration(seconds: 5));
      final retryLabel = find.text('Thử gửi lại đơn này');
      await tester.ensureVisible(retryLabel);
      await tester.pumpAndSettle();
      await tester.tap(retryLabel);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Xác nhận kê đơn'));
      await tester.pumpAndSettle();
      await pumpCommand(tester);

      expect(writer.intents, hasLength(2));
      expect(identical(writer.intents.last, original), isTrue);
      expect(writer.intents.last.key, original.key);
      expect(find.text('Mã đơn thuốc: $rxId'), findsOneWidget);
      await navigator.maybePop();
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('prescription-source-page')),
        findsNothing,
      );
      recordOpen.dispose();
    },
  );

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
      expect(
        find.text('Chưa đọc được thông tin. Vui lòng làm mới và thử lại.'),
        findsOneWidget,
      );
      await tap(tester, 'Tạo đơn thuốc');
      expect(writer.intents, isEmpty);
      fail = false;
      await tap(tester, 'Thử lại');
      expect(find.text('Khoa khám: Khoa Ngoai tru'), findsOneWidget);
    },
  );
}
