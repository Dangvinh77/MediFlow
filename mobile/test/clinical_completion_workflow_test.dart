import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/domain/page_result.dart';
import 'package:mediflow_mobile/core/domain/workspace_access.dart';
import 'package:mediflow_mobile/core/network/api_failure.dart';
import 'package:mediflow_mobile/core/providers.dart';
import 'package:mediflow_mobile/core/widgets/reference_widgets.dart';
import 'package:mediflow_mobile/features/appointment/domain/appointment.dart';
import 'package:mediflow_mobile/features/appointment/presentation/appointment_page.dart';
import 'package:mediflow_mobile/features/medical_record/domain/medical_record.dart';
import 'package:mediflow_mobile/features/medical_record/presentation/medical_record_page.dart';

const patientId = '10000000-0000-4000-8000-000000000001';
const doctorId = '10000000-0000-4000-8000-000000000002';
const departmentId = '10000000-0000-4000-8000-000000000003';
const appointmentId = '10000000-0000-4000-8000-000000000004';
const recordId = '10000000-0000-4000-8000-000000000005';
const diagnosisId = '10000000-0000-4000-8000-000000000006';
const sourceOrderId = '10000000-0000-4000-8000-000000000007';

Appointment appointment({String reason = 'New session'}) => Appointment(
  id: appointmentId,
  patientId: patientId,
  doctorId: doctorId,
  departmentId: departmentId,
  date: '2026-10-22',
  time: '09:30:00',
  status: 'PENDING',
  createdAt: '2026-10-22T08:00:00Z',
  careContractVersion: 0,
  reason: reason,
);

MedicalRecord record({String symptoms = 'Headache'}) => MedicalRecord(
  id: recordId,
  patientId: patientId,
  doctorId: doctorId,
  departmentId: departmentId,
  examinationDate: '2026-10-22',
  symptoms: symptoms,
  appointmentId: appointmentId,
  diagnoses: const [Diagnosis(id: diagnosisId, name: 'Tension headache')],
  createdAt: '2026-10-22T08:00:00Z',
  status: 'OPEN',
);

class AppointmentFake implements AppointmentRepository {
  Future<PageResult<Appointment>> Function(int page)? onSearch;
  Future<Appointment> Function(String id)? onGet;
  int searches = 0;
  @override
  Future<PageResult<Appointment>> search({
    int page = 0,
    String? date,
    String? departmentId,
  }) {
    searches++;
    return onSearch?.call(page) ??
        Future.value(
          PageResult(
            items: [appointment()],
            number: page,
            totalPages: 1,
            totalElements: 1,
          ),
        );
  }

  @override
  Future<Appointment> getById(String appointmentId) =>
      onGet?.call(appointmentId) ?? Future.value(appointment());
  @override
  Future<Appointment> create(AppointmentDraft draft) async => appointment();
  @override
  Future<Appointment> update(
    String appointmentId,
    AppointmentEdit edit,
  ) async => appointment();
  @override
  Future<Appointment> checkIn(String appointmentId) async => appointment();
  @override
  Future<Appointment> startExam(String appointmentId) async => appointment();
  @override
  Future<Appointment> cancel(String appointmentId) async => appointment();
}

class MedicalRecordFake implements MedicalRecordRepository {
  Future<MedicalRecord> Function(String id)? onGet;
  Future<LabTestReceipt> Function(ClinicalLabOrder sourceOrder)? onSubmitLab;
  final sources = <(String, ClinicalLabOrderDraft)>[];
  final submissions = <ClinicalLabOrder>[];
  int updates = 0;
  @override
  Future<MedicalRecord> getById(String id) =>
      onGet?.call(id) ?? Future.value(record());
  @override
  Future<List<MedicalRecord>> byPatient(String id) async => [record()];
  @override
  Future<MedicalRecord> create(CreateMedicalRecord draft) async => record();
  @override
  Future<MedicalRecord> update(String id, {required String? symptoms}) async {
    updates++;
    return record();
  }

  @override
  Future<Diagnosis> addDiagnosis(String id, DiagnosisDraft diagnosis) async =>
      const Diagnosis(id: diagnosisId, name: 'Migraine');
  @override
  Future<MedicalRecord> complete(
    String id, {
    required String disposition,
    required String? note,
  }) async => record();
  @override
  Future<ClinicalLabOrder> createClinicalLabOrder(
    String id,
    ClinicalLabOrderDraft draft,
  ) async {
    sources.add((id, draft));
    return const ClinicalLabOrder(
      sourceOrderId: sourceOrderId,
      recordId: recordId,
      patientId: patientId,
      requestingDoctorId: doctorId,
      requestingDepartmentId: departmentId,
      careEpisodeType: 'OUTPATIENT_VISIT',
      careEpisodeId: appointmentId,
      labType: 'CBC',
      requestedDate: '2026-10-22',
      priceCode: 'CBC-APPROVED',
      orderedAt: '2026-10-22T09:00:00Z',
    );
  }

  @override
  Future<LabTestReceipt> createLabRequestFromSourceOrder(
    ClinicalLabOrder sourceOrder,
  ) {
    submissions.add(sourceOrder);
    return onSubmitLab?.call(sourceOrder) ??
        Future.value(
          const LabTestReceipt(testId: 'opaque-lab', status: 'PENDING'),
        );
  }
}

class ReferenceFake implements ReferenceReader {
  @override
  Future<ReferenceOption> read(ReferenceKind kind, String id) async =>
      ReferenceOption(
        id: id,
        name: switch (kind) {
          ReferenceKind.patient => 'Patient name',
          ReferenceKind.doctor || ReferenceKind.staff => 'Doctor name',
          ReferenceKind.department => 'Clinic department',
          ReferenceKind.record => 'Medical record',
          ReferenceKind.appointment => 'Appointment',
        },
      );
  @override
  Future<ReferencePage> search(
    ReferenceKind kind, {
    String query = '',
    int page = 0,
    String? scope,
  }) async => const ReferencePage([]);
}

Widget host({
  required Widget child,
  String role = 'DOCTOR',
  Object identity = 'session-one',
  AppointmentFake? appointments,
  MedicalRecordFake? records,
  bool outpatientEnabled = false,
}) => ProviderScope(
  overrides: [
    workspaceAccessProvider.overrideWithValue(
      WorkspaceAccess(role: role, sessionIdentity: identity),
    ),
    referenceReaderProvider.overrideWithValue(ReferenceFake()),
    if (appointments != null)
      appointmentRepositoryProvider.overrideWithValue(appointments),
    if (records != null)
      medicalRecordRepositoryProvider.overrideWithValue(records),
    outpatientUiEnabledProvider.overrideWithValue(outpatientEnabled),
    medicalRecordOutpatientUiEnabledProvider.overrideWithValue(
      outpatientEnabled,
    ),
  ],
  child: MaterialApp(home: Scaffold(body: child)),
);

void main() {
  testWidgets('symptom draft cannot cross a session change', (tester) async {
    final records = MedicalRecordFake();
    await tester.pumpWidget(
      host(
        child: const MedicalRecordDetailPage(recordId: recordId),
        records: records,
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Sửa triệu chứng'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), 'Old session draft');
    await tester.pumpWidget(
      host(
        child: const MedicalRecordDetailPage(recordId: recordId),
        records: records,
        identity: 'session-two',
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Lưu'));
    await tester.pumpAndSettle();
    expect(records.updates, 0);
  });

  testWidgets('appointment list reloads after saved creation', (tester) async {
    final appointments = AppointmentFake();
    await tester.pumpWidget(
      host(
        child: const AppointmentPage(),
        role: 'NURSE',
        appointments: appointments,
      ),
    );
    await tester.pumpAndSettle();
    expect(appointments.searches, 1);
    await tester.tap(find.text('Tạo lịch hẹn'));
    await tester.pumpAndSettle();
    appointments.onSearch = (page) async => PageResult(
      items: [appointment(reason: 'Created appointment')],
      number: page,
      totalPages: 1,
      totalElements: 1,
    );
    tester.state<NavigatorState>(find.byType(Navigator).first).pop(true);
    await tester.pumpAndSettle();
    expect(appointments.searches, 2);
    expect(find.text('Lý do: Created appointment'), findsOneWidget);
  });

  testWidgets('a new session cannot see a previous appointment search result', (
    tester,
  ) async {
    final oldResult = Completer<PageResult<Appointment>>();
    final appointments = AppointmentFake()
      ..onSearch = (page) => oldResult.future;
    await tester.pumpWidget(
      host(child: const AppointmentPage(), appointments: appointments),
    );
    await tester.pump();

    appointments.onSearch = (page) async => PageResult(
      items: [appointment(reason: 'Current session appointment')],
      number: page,
      totalPages: 1,
      totalElements: 1,
    );
    await tester.pumpWidget(
      host(
        child: const AppointmentPage(),
        appointments: appointments,
        identity: 'session-two',
      ),
    );
    await tester.pumpAndSettle();
    oldResult.complete(
      PageResult(
        items: [appointment(reason: 'Old private appointment')],
        number: 0,
        totalPages: 1,
        totalElements: 1,
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Lý do: Current session appointment'), findsOneWidget);
    expect(find.text('Lý do: Old private appointment'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('a new session cannot see a previous appointment detail', (
    tester,
  ) async {
    final oldResult = Completer<Appointment>();
    final appointments = AppointmentFake()..onGet = (_) => oldResult.future;
    await tester.pumpWidget(
      host(
        child: const AppointmentDetailPage(appointmentId: appointmentId),
        appointments: appointments,
      ),
    );
    await tester.pump();

    appointments.onGet = (_) async =>
        appointment(reason: 'Current session appointment');
    await tester.pumpWidget(
      host(
        child: const AppointmentDetailPage(appointmentId: appointmentId),
        appointments: appointments,
        identity: 'session-two',
      ),
    );
    await tester.pumpAndSettle();
    oldResult.complete(appointment(reason: 'Old private appointment'));
    await tester.pumpAndSettle();

    expect(find.text('Lý do: Current session appointment'), findsOneWidget);
    expect(find.text('Lý do: Old private appointment'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('a new session cannot see a previous medical record detail', (
    tester,
  ) async {
    final oldResult = Completer<MedicalRecord>();
    final records = MedicalRecordFake()..onGet = (_) => oldResult.future;
    await tester.pumpWidget(
      host(
        child: const MedicalRecordDetailPage(recordId: recordId),
        records: records,
      ),
    );
    await tester.pump();

    records.onGet = (_) async => record(symptoms: 'Current session symptom');
    await tester.pumpWidget(
      host(
        child: const MedicalRecordDetailPage(recordId: recordId),
        records: records,
        identity: 'session-two',
      ),
    );
    await tester.pumpAndSettle();
    oldResult.complete(record(symptoms: 'Old private symptom'));
    await tester.pumpAndSettle();

    expect(find.text('Current session symptom'), findsOneWidget);
    expect(find.text('Old private symptom'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'lab retry reuses the returned source order and never creates a replacement',
    (tester) async {
      final records = MedicalRecordFake();
      var attempts = 0;
      records.onSubmitLab = (order) async {
        attempts++;
        if (attempts == 1) {
          throw const ApiFailure(
            'Lab validation rejected request',
            status: 422,
          );
        }
        return const LabTestReceipt(testId: 'opaque-lab', status: 'PENDING');
      };
      await tester.pumpWidget(
        host(
          child: const MedicalRecordDetailPage(recordId: recordId),
          records: records,
          outpatientEnabled: true,
        ),
      );
      await tester.pumpAndSettle();
      await tester.scrollUntilVisible(
        find.text('Chỉ định xét nghiệm'),
        400,
        scrollable: find.byType(Scrollable).first,
      );
      await tester.enterText(find.byType(TextField).at(0), 'CBC');
      await tester.enterText(find.byType(TextField).at(1), 'CBC-APPROVED');
      await tester.tap(find.text('1. Lập chỉ định'));
      await tester.pumpAndSettle();
      expect(records.sources, hasLength(1));
      expect(
        find.textContaining('Thông tin nguồn được giữ nguyên'),
        findsOneWidget,
      );

      await tester.tap(find.text('2. Gửi yêu cầu xét nghiệm'));
      await tester.pumpAndSettle();
      expect(find.text('Lab validation rejected request'), findsOneWidget);
      expect(find.text('2. Gửi yêu cầu xét nghiệm'), findsOneWidget);
      await tester.tap(find.text('2. Gửi yêu cầu xét nghiệm'));
      await tester.pumpAndSettle();

      expect(records.sources, hasLength(1));
      expect(records.submissions, hasLength(2));
      expect(records.submissions.map((order) => order.sourceOrderId), [
        sourceOrderId,
        sourceOrderId,
      ]);
      expect(find.text('Đã gửi yêu cầu xét nghiệm'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'unknown lab submission outcome blocks resending without losing its source',
    (tester) async {
      final records = MedicalRecordFake()
        ..onSubmitLab = (_) async =>
            throw const ApiFailure('Unknown result', status: 503);
      await tester.pumpWidget(
        host(
          child: const MedicalRecordDetailPage(recordId: recordId),
          records: records,
          outpatientEnabled: true,
        ),
      );
      await tester.pumpAndSettle();
      await tester.scrollUntilVisible(
        find.text('Chỉ định xét nghiệm'),
        400,
        scrollable: find.byType(Scrollable).first,
      );
      await tester.enterText(find.byType(TextField).at(0), 'CBC');
      await tester.enterText(find.byType(TextField).at(1), 'CBC-APPROVED');
      await tester.tap(find.text('1. Lập chỉ định'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('2. Gửi yêu cầu xét nghiệm'));
      await tester.pumpAndSettle();
      final submitButton = tester.widget<FilledButton>(
        find.ancestor(
          of: find.text('2. Gửi yêu cầu xét nghiệm'),
          matching: find.byType(FilledButton),
        ),
      );
      expect(submitButton.onPressed, isNull);
      expect(records.sources, hasLength(1));
      expect(records.submissions, hasLength(1));
      expect(
        find.textContaining('Đối soát trong hàng đợi xét nghiệm'),
        findsOneWidget,
      );
      expect(tester.takeException(), isNull);
    },
  );
}
