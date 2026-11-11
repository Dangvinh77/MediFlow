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

Appointment appointment({
  String reason = 'New session',
  String status = 'PENDING',
  int careContractVersion = 0,
  String? examClearanceId,
}) => Appointment(
  id: appointmentId,
  patientId: patientId,
  doctorId: doctorId,
  departmentId: departmentId,
  date: '2026-10-22',
  time: '09:30:00',
  status: status,
  createdAt: '2026-10-22T08:00:00Z',
  careContractVersion: careContractVersion,
  examClearanceId: examClearanceId,
  reason: reason,
);

MedicalRecord record({String symptoms = 'Headache', String status = 'OPEN'}) =>
    MedicalRecord(
      id: recordId,
      patientId: patientId,
      doctorId: doctorId,
      departmentId: departmentId,
      examinationDate: '2026-10-22',
      symptoms: symptoms,
      appointmentId: appointmentId,
      diagnoses: const [Diagnosis(id: diagnosisId, name: 'Tension headache')],
      createdAt: '2026-10-22T08:00:00Z',
      status: status,
    );

class AppointmentFake implements AppointmentRepository {
  Future<PageResult<Appointment>> Function(int page)? onSearch;
  Future<Appointment> Function(String id)? onGet;
  Future<ScheduledExamReadiness> Function(String id)? onReadiness;
  int searches = 0, starts = 0, readinessReads = 0;
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
  Future<Appointment> startExam(String appointmentId) async {
    starts++;
    return appointment(status: 'IN_EXAM', careContractVersion: 2);
  }

  @override
  Future<ScheduledExamReadiness> examReadiness(String registrationId) {
    readinessReads++;
    return onReadiness?.call(registrationId) ??
        Future.value(
          const ScheduledExamReadiness(
            readyForExam: true,
            checkedInAt: '2026-10-22T08:00:00Z',
          ),
        );
  }

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
  Future<RecordCapabilities> capabilities(String recordId) async =>
      RecordCapabilities(
        recordId: recordId,
        patientId: patientId,
        canRead: true,
        canWriteContent: true,
        canComplete: true,
      );
  @override
  Future<void> declareAccess({
    required String patientId,
    required String purpose,
    String? reason,
  }) async {}
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
          ReferenceKind.room => 'Room',
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
  testWidgets(
    'record detail places the injected prescription workspace before completion and locks completed records',
    (tester) async {
      var receivedCanCreate = false;
      Widget prescriptionSlot(
        BuildContext context,
        MedicalRecord current, {
        required bool canCreate,
      }) {
        receivedCanCreate = canCreate;
        return Text('Prescription slot: $canCreate');
      }

      final records = MedicalRecordFake();
      await tester.pumpWidget(
        host(
          child: MedicalRecordDetailPage(
            recordId: recordId,
            prescriptionWorkspaceBuilder: prescriptionSlot,
          ),
          records: records,
          outpatientEnabled: true,
        ),
      );
      await tester.pumpAndSettle();

      expect(receivedCanCreate, isTrue);
      final detailScrollable = find.descendant(
        of: find.byType(ListView).first,
        matching: find.byType(Scrollable),
      );
      await tester.scrollUntilVisible(
        find.text('Đơn thuốc'),
        200,
        scrollable: detailScrollable.first,
      );
      await tester.pumpAndSettle();
      expect(find.text('Đơn thuốc'), findsOneWidget);
      expect(find.text('Prescription slot: true'), findsOneWidget);
      await tester.ensureVisible(find.text('Hoàn tất hồ sơ khám'));
      expect(
        tester.getTopLeft(find.text('Prescription slot: true')).dy,
        lessThan(tester.getTopLeft(find.text('Hoàn tất hồ sơ khám')).dy),
      );

      records.onGet = (_) async => record(status: 'COMPLETED');
      // Updating a fake does not reload the mounted record; exercise its refresh action.
      final refresh = find.text('Làm mới hồ sơ');
      await tester.scrollUntilVisible(
        refresh,
        -200,
        scrollable: detailScrollable.first,
      );
      await tester.tap(refresh);
      await tester.pumpAndSettle();
      expect(receivedCanCreate, isFalse);
      await tester.scrollUntilVisible(
        find.text('Prescription slot: false'),
        200,
        scrollable: detailScrollable.first,
      );
      expect(find.text('Prescription slot: false'), findsOneWidget);
      expect(find.text('Hoàn tất hồ sơ khám'), findsNothing);
    },
  );

  testWidgets(
    'visit resolver forwards the prescription workspace builder with the exact record',
    (tester) async {
      MedicalRecord? receivedRecord;
      Widget prescriptionSlot(
        BuildContext context,
        MedicalRecord current, {
        required bool canCreate,
      }) {
        receivedRecord = current;
        return Text('Resolved prescription: ${current.id}');
      }

      await tester.pumpWidget(
        host(
          child: MedicalRecordForVisitPage(
            patientId: patientId,
            visitId: appointmentId,
            prescriptionWorkspaceBuilder: prescriptionSlot,
          ),
          records: MedicalRecordFake(),
          outpatientEnabled: true,
        ),
      );
      await tester.pumpAndSettle();

      expect(receivedRecord?.id, recordId);
      final detailScrollable = find.descendant(
        of: find.byType(ListView).first,
        matching: find.byType(Scrollable),
      );
      await tester.scrollUntilVisible(
        find.text('Resolved prescription: $recordId'),
        200,
        scrollable: detailScrollable.first,
      );
      await tester.pumpAndSettle();
      expect(find.text('Resolved prescription: $recordId'), findsOneWidget);
    },
  );

  testWidgets('nurse cannot see or build the Pharmacy record workspace', (
    tester,
  ) async {
    var calls = 0;
    Widget prescriptionSlot(
      BuildContext context,
      MedicalRecord current, {
      required bool canCreate,
    }) {
      calls++;
      return const Text('Pharmacy record slot');
    }

    await tester.pumpWidget(
      host(
        child: MedicalRecordDetailPage(
          recordId: recordId,
          prescriptionWorkspaceBuilder: prescriptionSlot,
        ),
        role: 'NURSE',
        records: MedicalRecordFake(),
        outpatientEnabled: true,
      ),
    );
    await tester.pumpAndSettle();

    expect(calls, 0);
    expect(find.text('Đơn thuốc'), findsNothing);
    expect(find.text('Pharmacy record slot'), findsNothing);
  });

  testWidgets('symptom draft cannot cross a session change', (tester) async {
    await tester.binding.setSurfaceSize(const Size(800, 3000));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final records = MedicalRecordFake();
    await tester.pumpWidget(
      host(
        child: const MedicalRecordDetailPage(recordId: recordId),
        records: records,
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Sửa nội dung khám'));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('record-content-symptoms')),
      'Old session draft',
    );
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

  group('scheduled exam start', () {
    Appointment scheduled({String status = 'READY_FOR_EXAM'}) => appointment(
      status: status,
      careContractVersion: 2,
      examClearanceId: 'clearance',
    );

    Future<List<(String, String)>> pumpDetail(
      WidgetTester tester,
      AppointmentFake appointments, {
      String role = 'DOCTOR',
    }) async {
      final opened = <(String, String)>[];
      await tester.pumpWidget(
        host(
          child: AppointmentDetailPage(
            appointmentId: appointmentId,
            onOpenRecord: (patient, visit) => opened.add((patient, visit)),
          ),
          appointments: appointments,
          outpatientEnabled: true,
          role: role,
        ),
      );
      await tester.pumpAndSettle();
      return opened;
    }

    testWidgets('a doctor starts a ready registration and opens its record', (
      tester,
    ) async {
      final appointments = AppointmentFake()..onGet = (_) async => scheduled();
      final opened = await pumpDetail(tester, appointments);

      await tester.tap(find.text('Bắt đầu khám'));
      await tester.pumpAndSettle();

      expect(appointments.starts, 1);
      expect(opened, [(patientId, appointmentId)]);
    });

    testWidgets('a doctor waits while the patient has not arrived', (
      tester,
    ) async {
      final appointments = AppointmentFake()
        ..onGet = ((_) async => scheduled())
        ..onReadiness = (_) async => const ScheduledExamReadiness(
          readyForExam: false,
          checkedInAt: null,
        );
      await pumpDetail(tester, appointments);

      expect(find.text('Bắt đầu khám'), findsNothing);
      expect(find.textContaining('Chờ bệnh nhân đến quầy'), findsOneWidget);
    });

    testWidgets('a failed readiness read never offers the start action', (
      tester,
    ) async {
      final appointments = AppointmentFake()
        ..onGet = ((_) async => scheduled())
        ..onReadiness = ((_) async => throw const ApiFailure('Gateway lỗi'));
      await pumpDetail(tester, appointments);

      expect(find.text('Bắt đầu khám'), findsNothing);
      expect(
        find.textContaining('Chưa kiểm tra được điều kiện khám'),
        findsOneWidget,
      );
    });

    testWidgets('an admin cannot start a scheduled registration', (
      tester,
    ) async {
      final appointments = AppointmentFake()..onGet = (_) async => scheduled();
      await pumpDetail(tester, appointments, role: 'ADMIN');

      expect(find.text('Bắt đầu khám'), findsNothing);
      expect(appointments.readinessReads, 0);
    });

    testWidgets('a completed visit still opens its record', (tester) async {
      final appointments = AppointmentFake()
        ..onGet = (_) async => scheduled(status: 'COMPLETED');
      final opened = await pumpDetail(tester, appointments);

      await tester.tap(find.text('Mở hồ sơ khám'));
      await tester.pumpAndSettle();

      expect(opened, [(patientId, appointmentId)]);
    });
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
      await tester.tap(find.text('Gửi chỉ định xét nghiệm'));
      await tester.pumpAndSettle();
      expect(records.sources, hasLength(1));
      expect(
        find.textContaining('Thông tin nguồn được giữ nguyên'),
        findsOneWidget,
      );

      expect(find.text('Lab validation rejected request'), findsOneWidget);
      expect(find.text('Tiếp tục chuyển sang Lab'), findsOneWidget);
      await tester.tap(find.text('Tiếp tục chuyển sang Lab'));
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
      await tester.tap(find.text('Gửi chỉ định xét nghiệm'));
      await tester.pumpAndSettle();
      final submitButton = tester.widget<FilledButton>(
        find.ancestor(
          of: find.text('Tiếp tục chuyển sang Lab'),
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
