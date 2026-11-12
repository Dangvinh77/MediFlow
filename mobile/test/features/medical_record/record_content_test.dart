import 'dart:async';
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
import 'package:mediflow_mobile/core/providers.dart';
import 'package:mediflow_mobile/core/widgets/reference_widgets.dart';
import 'package:mediflow_mobile/features/medical_record/data/gateway_patient_allergy_repository.dart';
import 'package:mediflow_mobile/features/medical_record/data/gateway_patient_timeline_repository.dart';
import 'package:mediflow_mobile/features/medical_record/data/medical_record_dto.dart';
import 'package:mediflow_mobile/features/medical_record/domain/medical_record.dart';
import 'package:mediflow_mobile/features/medical_record/domain/patient_timeline.dart';
import 'package:mediflow_mobile/features/medical_record/presentation/medical_record_page.dart';
import 'package:mediflow_mobile/features/medical_record/presentation/patient_timeline_section.dart';
import 'package:mediflow_mobile/features/medical_record/presentation/patient_allergy_section.dart';
import 'package:mediflow_mobile/features/medical_record/presentation/record_documents_section.dart';

import '../../clinical_completion_workflow_test.dart' as fixtures;

const _patient = '10000000-0000-4000-8000-000000000001';
const _record = '10000000-0000-4000-8000-000000000002';
const _allergy = '10000000-0000-4000-8000-000000000003';
const _doctor = '10000000-0000-4000-8000-000000000004';
const _department = '10000000-0000-4000-8000-000000000005';

class RecordContentFake implements PatientTimelineRepository {
  Future<List<RecordDocument>> Function()? onDocuments;
  final updates = <({Map<String, String?> changes, String key})>[];

  @override
  Future<TimelinePage> timeline(
    String patientId, {
    int page = 0,
    int size = 20,
    String? departmentId,
    String? status,
  }) async => TimelinePage(
    entries: const [],
    page: page,
    size: size,
    totalPages: 0,
    totalElements: 0,
  );

  @override
  Future<List<RecordDocument>> documents(String recordId) =>
      onDocuments?.call() ?? Future.value(const []);

  @override
  Future<MedicalRecord> updateContent(
    String recordId, {
    required Map<String, String?> changes,
    required String idempotencyKey,
  }) async {
    updates.add((changes: Map.of(changes), key: idempotencyKey));
    return MedicalRecord(
      id: _record,
      patientId: _patient,
      doctorId: _doctor,
      departmentId: _department,
      examinationDate: '2026-11-10',
      symptoms: changes['symptoms'],
      chiefComplaint: changes['chiefComplaint'],
      presentIllness: changes['presentIllness'],
      pastHistory: changes['pastHistory'],
      allergyNote: changes['allergyNote'],
      physicalExam: changes['physicalExam'],
      advice: changes['advice'],
      diagnoses: const [],
      createdAt: '2026-11-10T10:00:00Z',
      status: 'OPEN',
    );
  }
}

class AllergyFake implements PatientAllergyRepository {
  Future<PatientAllergies> Function()? onList;
  Future<PatientAllergy> Function()? onAdd;
  Future<PatientAllergy> Function()? onChangeStatus;
  final added = <({String substance, String? reaction, String severity, String? sourceRecordId, String key})>[];
  final changed = <({String allergyId, String status, String reason, String key})>[];

  @override
  Future<PatientAllergies> list(String patientId) =>
      onList?.call() ?? Future.value(const PatientAllergies(active: [], inactive: []));

  @override
  Future<PatientAllergy> add(
    String patientId, {
    required String substance,
    String? reaction,
    required String severity,
    String? sourceRecordId,
    required String idempotencyKey,
  }) {
    added.add((
      substance: substance,
      reaction: reaction,
      severity: severity,
      sourceRecordId: sourceRecordId,
      key: idempotencyKey,
    ));
    return onAdd?.call() ?? Future.value(_allergyModel());
  }

  @override
  Future<PatientAllergy> changeStatus(
    String patientId,
    String allergyId, {
    required String status,
    required String reason,
    required String idempotencyKey,
  }) {
    changed.add((
      allergyId: allergyId,
      status: status,
      reason: reason,
      key: idempotencyKey,
    ));
    return onChangeStatus?.call() ?? Future.value(_allergyModel(status: status));
  }
}

PatientAllergy _allergyModel({String status = 'ACTIVE'}) => PatientAllergy(
  allergyId: _allergy,
  substance: 'Penicillin',
  reaction: 'Mẩn đỏ',
  severity: 'MODERATE',
  status: status,
  recordedByStaffId: _doctor,
  recordedAt: '2026-11-10T10:00:00Z',
  sourceRecordId: _record,
  statusReason: null,
  statusChangedByStaffId: null,
  statusChangedAt: null,
);

class MedicalRecordTransport implements HttpClientAdapter {
  final requests = <RequestOptions>[];
  Object? responseData;
  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    return ResponseBody.fromString(
      jsonEncode({'success': true, 'data': responseData}),
      200,
      headers: {Headers.contentTypeHeader: [Headers.jsonContentType]},
    );
  }

  @override
  void close({bool force = false}) {}
}

Widget _sectionHost({
  required PatientTimelineRepository timeline,
  required PatientAllergyRepository allergy,
  required Widget child,
  String role = 'DOCTOR',
}) => ProviderScope(
  overrides: [
    patientTimelineRepositoryProvider.overrideWithValue(timeline),
    patientAllergyRepositoryProvider.overrideWithValue(allergy),
    workspaceAccessProvider.overrideWithValue(
      WorkspaceAccess(role: role, sessionIdentity: 'session-one'),
    ),
  ],
  child: MaterialApp(home: Scaffold(body: child)),
);

void main() {
  group('structured record content', () {
    test('record DTO reads six clinical sections and responsible doctor', () {
      final record = MedicalRecordDto.fromJson({
        'recordId': _record,
        'patientId': _patient,
        'doctorId': _doctor,
        'departmentId': _department,
        'examinationDate': '2026-11-10',
        'diagnoses': <Object>[],
        'createdAt': '2026-11-10T10:00:00Z',
        'status': 'OPEN',
        'chiefComplaint': 'Đau đầu',
        'presentIllness': 'Đau từ sáng',
        'pastHistory': 'Tăng huyết áp',
        'allergyNote': 'Dị ứng thuốc',
        'physicalExam': 'Tỉnh táo',
        'advice': 'Uống đủ nước',
        'responsibleDoctorId': _allergy,
      });

      expect(record.chiefComplaint, 'Đau đầu');
      expect(record.presentIllness, 'Đau từ sáng');
      expect(record.pastHistory, 'Tăng huyết áp');
      expect(record.allergyNote, 'Dị ứng thuốc');
      expect(record.physicalExam, 'Tỉnh táo');
      expect(record.advice, 'Uống đủ nước');
      expect(record.responsibleDoctorId, _allergy);
    });

    test('record DTO reads the effective correction projection', () {
      final record = MedicalRecordDto.fromJson({
        ..._recordJson(symptoms: 'Đau đầu'),
        'effectiveCorrections': [
          {
            'section': 'SYMPTOMS',
            'originalText': 'Đau đầu',
            'correctedText': 'Đau đầu từ sáng',
            'reason': 'Bổ sung thời điểm khởi phát',
            'appliedAt': '2026-11-10T11:00:00Z',
          },
        ],
      });

      expect(record.effectiveCorrections.single.section, 'SYMPTOMS');
      expect(record.effectiveCorrections.single.originalText, 'Đau đầu');
      expect(record.effectiveCorrections.single.correctedText, 'Đau đầu từ sáng');
      expect(record.effectiveCorrections.single.reason, 'Bổ sung thời điểm khởi phát');
      expect(record.effectiveCorrections.single.appliedAt, '2026-11-10T11:00:00Z');
    });

    test('content patch contains only changed sections and preserves clears', () {
      final changes = changedRecordSections(
        original: const {
          'chiefComplaint': 'Đau đầu',
          'presentIllness': 'Đau từ sáng',
          'pastHistory': null,
        },
        edited: const {
          'chiefComplaint': 'Đau đầu',
          'presentIllness': '',
          'pastHistory': null,
        },
      );

      expect(changes, {'presentIllness': ''});
    });

    test('record update sends changed fields and idempotency key to gateway', () async {
      final transport = MedicalRecordTransport()
        ..responseData = _recordJson(symptoms: 'Đau đầu', chiefComplaint: 'Đau đầu');
      final dio = Dio()..httpClientAdapter = transport;
      final repository = GatewayPatientTimelineRepository(
        ApiClient(AppConfig('https://gateway.invalid/api'), dio: dio),
      );
      await repository.updateContent(
        _record,
        changes: const {'chiefComplaint': 'Đau đầu'},
        idempotencyKey: 'same-command-key',
      );

      final request = transport.requests.single;
      expect(request.path, 'v1/records/$_record');
      expect(request.method, 'PUT');
      expect(request.data, {'chiefComplaint': 'Đau đầu'});
      expect(request.headers['Idempotency-Key'], 'same-command-key');
    });

    testWidgets('documents label post-conclusion sources and open source route', (
      tester,
    ) async {
      final timeline = RecordContentFake()
        ..onDocuments = () async => [
          const RecordDocument(
            type: 'LAB',
            referenceId: _allergy,
            summary: 'Công thức máu',
            attachedAt: '2026-11-11T10:00:00Z',
            afterConclusion: true,
          ),
        ];
      final opened = <RecordDocument>[];
      await tester.pumpWidget(
        _sectionHost(
          timeline: timeline,
          allergy: AllergyFake(),
          child: RecordDocumentsSection(
            recordId: _record,
            onOpenSource: opened.add,
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Bổ sung sau khi kết thúc'), findsOneWidget);
      expect(find.text('Công thức máu'), findsOneWidget);
      await tester.tap(find.text('Công thức máu'));
      expect(opened, hasLength(1));
      expect(opened.single.referenceId, _allergy);
    });

    testWidgets('document outage shows retry and never an empty message', (
      tester,
    ) async {
      final timeline = RecordContentFake()
        ..onDocuments = () async => throw const ApiFailure('Mất kết nối');
      await tester.pumpWidget(
        _sectionHost(
          timeline: timeline,
          allergy: AllergyFake(),
          child: const RecordDocumentsSection(recordId: _record),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.textContaining('Không tải được tài liệu'), findsOneWidget);
      expect(find.text('Chưa có tài liệu của lượt khám.'), findsNothing);
      expect(find.byKey(const Key('record-documents-retry')), findsOneWidget);
    });

    testWidgets('empty documents show the explicit empty state', (tester) async {
      await tester.pumpWidget(
        _sectionHost(
          timeline: RecordContentFake(),
          allergy: AllergyFake(),
          child: const RecordDocumentsSection(recordId: _record),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Chưa có tài liệu của lượt khám.'), findsOneWidget);
    });

    testWidgets('document 403 shows no permission, not empty', (tester) async {
      final timeline = RecordContentFake()
        ..onDocuments = () async => throw const ApiFailure('Denied', status: 403);
      await tester.pumpWidget(
        _sectionHost(
          timeline: timeline,
          allergy: AllergyFake(),
          child: const RecordDocumentsSection(recordId: _record),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Bạn không có quyền xem tài liệu của hồ sơ này.'), findsOneWidget);
      expect(find.text('Chưa có tài liệu của lượt khám.'), findsNothing);
    });

    testWidgets('previous visit is reference only and has no copy action', (
      tester,
    ) async {
      await tester.binding.setSurfaceSize(const Size(800, 3000));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      final records = fixtures.MedicalRecordFake()
        ..onGet = (_) async => fixtures.record(status: 'COMPLETED');
      final allergy = AllergyFake()
        ..onList = () async => PatientAllergies(
          active: [_allergyModel()],
          inactive: const [],
        );
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            workspaceAccessProvider.overrideWithValue(
              WorkspaceAccess(role: 'DOCTOR', sessionIdentity: 'session-one'),
            ),
            referenceReaderProvider.overrideWithValue(fixtures.ReferenceFake()),
            medicalRecordRepositoryProvider.overrideWithValue(records),
            medicalRecordOutpatientUiEnabledProvider.overrideWithValue(false),
            patientTimelineRepositoryProvider.overrideWithValue(RecordContentFake()),
            patientAllergyRepositoryProvider.overrideWithValue(allergy),
          ],
          child: const MaterialApp(
            home: MedicalRecordDetailPage(
              recordId: _record,
              referenceOnly: true,
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.textContaining('Lượt trước — chỉ tham khảo'), findsOneWidget);
      expect(find.text('Sao chép nội dung lượt trước'), findsNothing);
      expect(find.text('Sửa nội dung khám'), findsNothing);
      expect(find.text('Penicillin'), findsOneWidget);
      expect(find.byKey(const Key('patient-allergy-add')), findsNothing);
      expect(find.byKey(const Key('allergy-entered-in-error')), findsNothing);
    });

    testWidgets('detail shows clinical sections in order and hides empty ones', (
      tester,
    ) async {
      await tester.binding.setSurfaceSize(const Size(800, 3000));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      final record = MedicalRecord(
        id: _record,
        patientId: _patient,
        doctorId: _doctor,
        departmentId: _department,
        examinationDate: '2026-11-10',
        diagnoses: const [],
        createdAt: '2026-11-10T10:00:00Z',
        status: 'COMPLETED',
        chiefComplaint: 'Đau đầu',
        presentIllness: null,
        pastHistory: 'Tăng huyết áp',
        allergyNote: 'Dị ứng thuốc',
        physicalExam: 'Tỉnh táo',
        advice: 'Uống đủ nước',
      );
      final records = fixtures.MedicalRecordFake()..onGet = (_) async => record;
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            workspaceAccessProvider.overrideWithValue(
              WorkspaceAccess(role: 'DOCTOR', sessionIdentity: 'session-one'),
            ),
            referenceReaderProvider.overrideWithValue(fixtures.ReferenceFake()),
            medicalRecordRepositoryProvider.overrideWithValue(records),
            medicalRecordOutpatientUiEnabledProvider.overrideWithValue(false),
            patientTimelineRepositoryProvider.overrideWithValue(RecordContentFake()),
            patientAllergyRepositoryProvider.overrideWithValue(AllergyFake()),
          ],
          child: const MaterialApp(home: MedicalRecordDetailPage(recordId: _record)),
        ),
      );
      await tester.pumpAndSettle();
      final visible = [
        tester.getTopLeft(find.text('Lý do khám')).dy,
        tester.getTopLeft(find.text('Tiền sử')).dy,
        tester.getTopLeft(find.text('Ghi chú dị ứng của lượt')).dy,
        tester.getTopLeft(find.text('Khám thực thể')).dy,
        tester.getTopLeft(find.text('Dặn dò')).dy,
      ];
      expect(visible, orderedEquals([...visible]..sort()));
      expect(find.text('Diễn biến bệnh'), findsNothing);
      expect(find.text('Chưa ghi nhận.'), findsNothing);
      expect(find.text('Tăng huyết áp'), findsOneWidget);
    });

    testWidgets('content editor sends only the changed field', (tester) async {
      await tester.binding.setSurfaceSize(const Size(800, 3000));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      final contentRepository = RecordContentFake();
      final records = fixtures.MedicalRecordFake()
        ..onGet = (_) async => MedicalRecord(
          id: _record,
          patientId: _patient,
          doctorId: _doctor,
          departmentId: _department,
          examinationDate: '2026-11-10',
          diagnoses: const [],
          createdAt: '2026-11-10T10:00:00Z',
          status: 'OPEN',
          chiefComplaint: 'Đau đầu',
        );
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            workspaceAccessProvider.overrideWithValue(
              WorkspaceAccess(role: 'DOCTOR', sessionIdentity: 'session-one'),
            ),
            referenceReaderProvider.overrideWithValue(fixtures.ReferenceFake()),
            medicalRecordRepositoryProvider.overrideWithValue(records),
            medicalRecordOutpatientUiEnabledProvider.overrideWithValue(false),
            patientTimelineRepositoryProvider.overrideWithValue(contentRepository),
            patientAllergyRepositoryProvider.overrideWithValue(AllergyFake()),
          ],
          child: const MaterialApp(home: MedicalRecordDetailPage(recordId: _record)),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Sửa nội dung khám'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byKey(const Key('record-content-chiefComplaint')), 'Đau ngực');
      await tester.tap(find.byKey(const Key('record-content-save')));
      await tester.pumpAndSettle();

      expect(contentRepository.updates, hasLength(1));
      expect(contentRepository.updates.single.changes, {'chiefComplaint': 'Đau ngực'});
      expect(contentRepository.updates.single.key, isNotEmpty);
    });
  });

  group('patient allergy register', () {
    test('allergy DTO preserves exact fields and active/inactive groups', () {
      final value = PatientAllergiesDto.fromJson({
        'active': [
          {
            'allergyId': _allergy,
            'substance': 'Penicillin',
            'reaction': 'Mẩn đỏ',
            'severity': 'MODERATE',
            'status': 'ACTIVE',
            'recordedByStaffId': _doctor,
            'recordedAt': '2026-11-10T10:00:00Z',
            'sourceRecordId': _record,
          },
        ],
        'inactive': <Object>[],
      });

      expect(value.active.single.substance, 'Penicillin');
      expect(value.inactive, isEmpty);
      expect(value.active.single.recordedByStaffId, _doctor);
    });

    test('allergy writes use the exact gateway paths and idempotency keys', () async {
      final transport = MedicalRecordTransport()
        ..responseData = _allergyJson(status: 'ACTIVE');
      final dio = Dio()..httpClientAdapter = transport;
      final repository = GatewayPatientAllergyRepository(
        ApiClient(AppConfig('https://gateway.invalid/api'), dio: dio),
      );
      await repository.add(
        _patient,
        substance: 'Penicillin',
        reaction: 'Mẩn đỏ',
        severity: 'MODERATE',
        sourceRecordId: _record,
        idempotencyKey: 'add-key',
      );
      transport.responseData = _allergyJson(status: 'ENTERED_IN_ERROR');
      await repository.changeStatus(
        _patient,
        _allergy,
        status: 'ENTERED_IN_ERROR',
        reason: 'Nhập nhầm',
        idempotencyKey: 'status-key',
      );

      expect(transport.requests[0].path, 'v2/patients/$_patient/allergies');
      expect(transport.requests[0].method, 'POST');
      expect(transport.requests[0].headers['Idempotency-Key'], 'add-key');
      expect(transport.requests[1].path,
          'v2/patients/$_patient/allergies/$_allergy/status');
      expect(transport.requests[1].method, 'PUT');
      expect(transport.requests[1].data, {
        'status': 'ENTERED_IN_ERROR',
        'reason': 'Nhập nhầm',
      });
      expect(transport.requests[1].headers['Idempotency-Key'], 'status-key');
    });

    testWidgets('active allergies appear above record details', (tester) async {
      final records = fixtures.MedicalRecordFake()
        ..onGet = (_) async => fixtures.record();
      final allergy = AllergyFake()
        ..onList = () async => PatientAllergies(
          active: [_allergyModel()],
          inactive: const [],
        );
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            workspaceAccessProvider.overrideWithValue(
              WorkspaceAccess(role: 'DOCTOR', sessionIdentity: 'session-one'),
            ),
            referenceReaderProvider.overrideWithValue(fixtures.ReferenceFake()),
            medicalRecordRepositoryProvider.overrideWithValue(records),
            medicalRecordOutpatientUiEnabledProvider.overrideWithValue(false),
            patientTimelineRepositoryProvider.overrideWithValue(RecordContentFake()),
            patientAllergyRepositoryProvider.overrideWithValue(allergy),
          ],
          child: const MaterialApp(home: MedicalRecordDetailPage(recordId: _record)),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Penicillin'), findsOneWidget);
      expect(
        tester.getTopLeft(find.text('Penicillin')).dy,
        lessThan(tester.getTopLeft(find.textContaining('Ngày khám:')).dy),
      );
    });

    testWidgets('empty active list says not recorded, not no allergy', (
      tester,
    ) async {
      await tester.pumpWidget(
        _sectionHost(
          timeline: RecordContentFake(),
          allergy: AllergyFake(),
          child: const PatientAllergySection(
            patientId: _patient,
            sourceRecordId: _record,
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Chưa ghi nhận dị ứng'), findsOneWidget);
      expect(find.textContaining('Không dị ứng'), findsNothing);
    });

    testWidgets('allergy outage shows retry and does not claim empty', (
      tester,
    ) async {
      final allergy = AllergyFake()
        ..onList = () async => throw const ApiFailure('Mất kết nối', status: 503);
      await tester.pumpWidget(
        _sectionHost(
          timeline: RecordContentFake(),
          allergy: allergy,
          child: const PatientAllergySection(
            patientId: _patient,
            sourceRecordId: _record,
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.textContaining('Không tải được sổ dị ứng'), findsOneWidget);
      expect(find.text('Chưa ghi nhận dị ứng'), findsNothing);
      expect(find.byKey(const Key('patient-allergy-retry')), findsOneWidget);
    });

    testWidgets('inactive allergies are collapsed', (tester) async {
      final allergy = AllergyFake()
        ..onList = () async => PatientAllergies(
          active: const [],
          inactive: [_allergyModel(status: 'RESOLVED')],
        );
      await tester.pumpWidget(
        _sectionHost(
          timeline: RecordContentFake(),
          allergy: allergy,
          child: const PatientAllergySection(
            patientId: _patient,
            sourceRecordId: _record,
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Penicillin'), findsNothing);
      await tester.tap(find.text('Dị ứng đã ngừng hoặc nhập nhầm'));
      await tester.pumpAndSettle();
      expect(find.text('Penicillin'), findsOneWidget);
    });

    testWidgets('nurse can add an allergy with source visit', (tester) async {
      final allergy = AllergyFake();
      await tester.pumpWidget(
        _sectionHost(
          timeline: RecordContentFake(),
          allergy: allergy,
          role: 'NURSE',
          child: const PatientAllergySection(
            patientId: _patient,
            sourceRecordId: _record,
            canAdd: true,
            canManageStatus: true,
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('patient-allergy-add')));
      await tester.pumpAndSettle();
      await tester.enterText(find.byKey(const Key('allergy-substance')), 'Penicillin');
      await tester.enterText(find.byKey(const Key('allergy-reaction')), 'Mẩn đỏ');
      await tester.tap(find.byKey(const Key('allergy-submit')));
      await tester.pumpAndSettle();
      expect(allergy.added.single.substance, 'Penicillin');
      expect(allergy.added.single.sourceRecordId, _record);
      expect(allergy.added.single.key, isNotEmpty);
    });

    testWidgets('doctor marks an allergy entered in error with a reason', (
      tester,
    ) async {
      await tester.binding.setSurfaceSize(const Size(800, 3000));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      final allergy = AllergyFake()
        ..onList = () async => PatientAllergies(active: [_allergyModel()], inactive: const []);
      await tester.pumpWidget(
        _sectionHost(
          timeline: RecordContentFake(),
          allergy: allergy,
          child: const PatientAllergySection(
            patientId: _patient,
            sourceRecordId: _record,
            canAdd: true,
            canManageStatus: true,
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('allergy-entered-in-error')));
      await tester.pumpAndSettle();
      await tester.enterText(find.byKey(const Key('allergy-status-reason')), 'Ghi sai chất gây dị ứng');
      await tester.pump();
      await tester.tap(find.byKey(const Key('allergy-status-submit')));
      await tester.pumpAndSettle();
      expect(allergy.changed.single.status, 'ENTERED_IN_ERROR');
      expect(allergy.changed.single.reason, 'Ghi sai chất gây dị ứng');
      expect(allergy.changed.single.key, isNotEmpty);
    });

    testWidgets('allergy 403 shows no permission state', (tester) async {
      final allergy = AllergyFake()
        ..onList = () async => throw const ApiFailure('Denied', status: 403);
      await tester.pumpWidget(
        _sectionHost(
          timeline: RecordContentFake(),
          allergy: allergy,
          child: const PatientAllergySection(
            patientId: _patient,
            sourceRecordId: _record,
            canAdd: true,
            canManageStatus: true,
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Bạn không có quyền xem sổ dị ứng của bệnh nhân này.'), findsOneWidget);
      expect(find.text('Chưa ghi nhận dị ứng'), findsNothing);
    });

    testWidgets('write retry keeps the same allergy payload and key', (
      tester,
    ) async {
      var attempts = 0;
      final allergy = AllergyFake()
        ..onAdd = () async {
          if (attempts++ == 0) throw const ApiFailure('Mất kết nối');
          return _allergyModel();
        };
      await tester.pumpWidget(
        _sectionHost(
          timeline: RecordContentFake(),
          allergy: allergy,
          child: const PatientAllergySection(
            patientId: _patient,
            sourceRecordId: _record,
            canAdd: true,
            canManageStatus: true,
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('patient-allergy-add')));
      await tester.pumpAndSettle();
      await tester.enterText(find.byKey(const Key('allergy-substance')), 'Penicillin');
      await tester.enterText(find.byKey(const Key('allergy-reaction')), 'Mẩn đỏ');
      await tester.tap(find.byKey(const Key('allergy-submit')));
      await tester.pumpAndSettle();
      expect(find.textContaining('Kết quả chưa rõ'), findsOneWidget);
      await tester.tap(find.byKey(const Key('allergy-submit')));
      await tester.pumpAndSettle();
      expect(allergy.added, hasLength(2));
      expect(allergy.added[0].key, allergy.added[1].key);
      expect(allergy.added[0].substance, allergy.added[1].substance);
    });
  });
}

Map<String, dynamic> _recordJson({String? symptoms, String? chiefComplaint}) => {
  'recordId': _record,
  'patientId': _patient,
  'doctorId': _doctor,
  'departmentId': _department,
  'examinationDate': '2026-11-10',
  'symptoms': symptoms,
  'chiefComplaint': chiefComplaint,
  'diagnoses': <Object>[],
  'createdAt': '2026-11-10T10:00:00Z',
  'status': 'OPEN',
};

Map<String, dynamic> _allergyJson({required String status}) => {
  'allergyId': _allergy,
  'substance': 'Penicillin',
  'reaction': 'Mẩn đỏ',
  'severity': 'MODERATE',
  'status': status,
  'recordedByStaffId': _doctor,
  'recordedAt': '2026-11-10T10:00:00Z',
  'sourceRecordId': _record,
};
