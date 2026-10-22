import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/config/app_config.dart';
import 'package:mediflow_mobile/core/network/api_client.dart';
import 'package:mediflow_mobile/core/network/api_failure.dart';
import 'package:mediflow_mobile/features/appointment/data/gateway_appointment_repository.dart';
import 'package:mediflow_mobile/features/appointment/data/appointment_dto.dart';
import 'package:mediflow_mobile/features/appointment/domain/appointment.dart';
import 'package:mediflow_mobile/features/medical_record/data/gateway_medical_record_repository.dart';
import 'package:mediflow_mobile/features/medical_record/domain/medical_record.dart';

const patientId = '10000000-0000-4000-8000-000000000001';
const doctorId = '10000000-0000-4000-8000-000000000002';
const departmentId = '10000000-0000-4000-8000-000000000003';
const appointmentId = '10000000-0000-4000-8000-000000000004';
const recordId = '10000000-0000-4000-8000-000000000005';
const diagnosisId = '10000000-0000-4000-8000-000000000006';
const sourceOrderId = '10000000-0000-4000-8000-000000000007';

class ClinicalRequest {
  ClinicalRequest(this.options);
  final RequestOptions options;
  String get method => options.method;
  Uri get uri => options.uri;
  Object? get data => options.data;
  Map<String, String> get headers => {
    for (final entry in options.headers.entries)
      entry.key: entry.value.toString(),
  };
}

class ClinicalAdapter implements HttpClientAdapter {
  final requests = <ClinicalRequest>[];
  Object? data;
  int status = 200;
  bool wrapSuccess = true;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(ClinicalRequest(options));
    final body = wrapSuccess && status < 400
        ? {'success': true, 'data': data, 'error': null}
        : data;
    return ResponseBody.fromString(
      jsonEncode(body),
      status,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

Map<String, dynamic> appointmentJson({
  String id = appointmentId,
  String status = 'PENDING',
  int version = 0,
}) => {
  'appointmentId': id,
  'patientId': patientId,
  'doctorId': doctorId,
  'departmentId': departmentId,
  'appointmentDate': '2026-10-22',
  'appointmentTime': '09:30:00',
  'status': status,
  'reason': 'Follow-up',
  'createdAt': '2026-10-20T08:00:00Z',
  'updatedAt': null,
  'careContractVersion': version,
  'examClearanceId': version == 1 ? 'clearance' : null,
  'examClearanceAt': null,
  'emergencyOverrideId': null,
  'examPriceCode': null,
  'checkedInAt': null,
  'examinationStartedAt': null,
  'completedAt': null,
};

Map<String, dynamic> recordJson({String id = recordId}) => {
  'recordId': id,
  'patientId': patientId,
  'doctorId': doctorId,
  'departmentId': departmentId,
  'examinationDate': '2026-10-22',
  'symptoms': 'Headache',
  'appointmentId': appointmentId,
  'diagnoses': [
    {
      'diagnosisId': diagnosisId,
      'diagnosisName': 'Tension headache',
      'description': null,
      'icdCode': 'G44.2',
    },
  ],
  'createdAt': '2026-10-22T08:00:00Z',
  'updatedAt': null,
  'status': 'OPEN',
  'disposition': null,
  'dispositionNote': null,
  'completedAt': null,
};

void main() {
  late ClinicalAdapter adapter;
  late ApiClient client;
  late GatewayAppointmentRepository appointments;
  late GatewayMedicalRecordRepository records;

  setUp(() {
    adapter = ClinicalAdapter();
    client = ApiClient(
      AppConfig('http://localhost:8080/api'),
      dio: Dio()..httpClientAdapter = adapter,
      token: () => 'clinical-session',
    );
    appointments = GatewayAppointmentRepository(client);
    records = GatewayMedicalRecordRepository(client);
  });
  tearDown(() => client.close());

  test(
    'appointment list uses Gateway query and decodes exact care version',
    () async {
      adapter.data = {
        'content': [appointmentJson(status: 'READY_FOR_EXAM', version: 1)],
        'number': 0,
        'totalPages': 1,
        'totalElements': 1,
      };
      final result = await appointments.search(
        page: 0,
        date: '2026-10-22',
        departmentId: departmentId,
      );

      expect(adapter.requests.single.method, 'GET');
      expect(adapter.requests.single.uri.path, '/api/v1/appointments');
      expect(adapter.requests.single.uri.queryParameters, {
        'page': '0',
        'size': '20',
        'appointmentDate': '2026-10-22',
        'departmentId': departmentId,
      });
      expect(result.items.single.careContractVersion, 1);
      expect(result.items.single.status, 'READY_FOR_EXAM');
      expect(
        adapter.requests.single.headers['Authorization'],
        'Bearer clinical-session',
      );
    },
  );

  test('appointment create, edit, check-in, start and cancel match live wire bodies', () async {
    adapter.data = appointmentJson();
    await appointments.create(
      AppointmentDraft(
        patientId: patientId,
        doctorId: doctorId,
        departmentId: departmentId,
        date: '2026-10-22',
        time: '09:30',
        reason: ' Follow-up ',
      ),
    );
    expect(adapter.requests.last.method, 'POST');
    expect(adapter.requests.last.uri.path, '/api/v1/appointments');
    expect(adapter.requests.last.data, {
      'patientId': patientId,
      'doctorId': doctorId,
      'departmentId': departmentId,
      'appointmentDate': '2026-10-22',
      'appointmentTime': '09:30',
      'reason': 'Follow-up',
    });

    await appointments.update(
      appointmentId,
      AppointmentEdit(date: '2026-10-23', time: '10:00', reason: ''),
    );
    expect(adapter.requests.last.method, 'PUT');
    expect(
      adapter.requests.last.uri.path,
      '/api/v1/appointments/$appointmentId',
    );
    expect(adapter.requests.last.data, {
      'appointmentDate': '2026-10-23',
      'appointmentTime': '10:00',
      'reason': null,
    });

    await appointments.checkIn(appointmentId);
    expect(
      adapter.requests.last.uri.path,
      '/api/v1/appointments/$appointmentId/check-in',
    );
    expect(adapter.requests.last.data, isNull);
    await appointments.startExam(appointmentId);
    expect(
      adapter.requests.last.uri.path,
      '/api/v1/appointments/$appointmentId/start-exam',
    );
    expect(adapter.requests.last.data, {'emergencyOverride': null});
    await appointments.cancel(appointmentId);
    expect(
      adapter.requests.last.uri.path,
      '/api/v1/appointments/$appointmentId/status',
    );
    expect(adapter.requests.last.data, {'status': 'CANCELLED'});
    expect(adapter.requests, hasLength(5));
  });

  test(
    'appointment identifiers and edits are validated before dispatch',
    () async {
      await expectLater(
        appointments.create(
          AppointmentDraft(
            patientId: 'patient-name',
            doctorId: doctorId,
            departmentId: departmentId,
            date: '2026-10-22',
            time: '09:30',
          ),
        ),
        throwsArgumentError,
      );
      await expectLater(
        appointments.update(
          appointmentId,
          AppointmentEdit(date: '', time: 'noon'),
        ),
        throwsArgumentError,
      );
      expect(adapter.requests, isEmpty);
    },
  );

  test(
    'appointment capabilities honor role, version, status and UI gate together',
    () {
      final legacy = AppointmentDto.fromJson(appointmentJson());
      final cleared = AppointmentDto.fromJson(
        appointmentJson(status: 'READY_FOR_EXAM', version: 1),
      );
      expect(
        canCheckInAppointment(legacy, role: 'NURSE', outpatientEnabled: true),
        isTrue,
      );
      expect(
        canCheckInAppointment(legacy, role: 'DOCTOR', outpatientEnabled: true),
        isFalse,
      );
      expect(
        canCheckInAppointment(legacy, role: 'NURSE', outpatientEnabled: false),
        isFalse,
      );
      expect(
        canStartAppointment(cleared, role: 'DOCTOR', outpatientEnabled: true),
        isTrue,
      );
      expect(
        canStartAppointment(cleared, role: 'NURSE', outpatientEnabled: true),
        isFalse,
      );
      expect(
        canStartAppointment(
          AppointmentDto.fromJson(
            appointmentJson(status: 'READY_FOR_EXAM', version: 0),
          ),
          role: 'DOCTOR',
          outpatientEnabled: true,
        ),
        isFalse,
      );
    },
  );

  test(
    'record reads, create and clinical updates keep exact contract fields',
    () async {
      adapter.data = recordJson();
      final record = await records.getById(recordId);
      expect(adapter.requests.last.uri.path, '/api/v1/records/$recordId');
      expect(record.diagnoses.single.icdCode, 'G44.2');

      adapter.data = [recordJson()];
      await records.byPatient(patientId);
      expect(
        adapter.requests.last.uri.path,
        '/api/v1/records/patient/$patientId',
      );

      adapter.data = recordJson();
      await records.create(
        CreateMedicalRecord(
          patientId: patientId,
          doctorId: doctorId,
          departmentId: departmentId,
          examinationDate: '2026-10-22',
          symptoms: ' Headache ',
          appointmentId: appointmentId,
          diagnoses: const [DiagnosisDraft(diagnosisName: 'Tension headache')],
        ),
      );
      expect(adapter.requests.last.uri.path, '/api/v1/records');
      expect(adapter.requests.last.data, {
        'patientId': patientId,
        'doctorId': doctorId,
        'departmentId': departmentId,
        'examinationDate': '2026-10-22',
        'symptoms': 'Headache',
        'appointmentId': appointmentId,
        'diagnoses': [
          {
            'diagnosisName': 'Tension headache',
            'description': null,
            'icdCode': null,
          },
        ],
      });

      await records.update(recordId, symptoms: ' Headache improved ');
      expect(adapter.requests.last.uri.path, '/api/v1/records/$recordId');
      expect(adapter.requests.last.data, {'symptoms': 'Headache improved'});
      adapter.data = {
        'diagnosisId': diagnosisId,
        'diagnosisName': 'Migraine',
        'description': null,
        'icdCode': null,
      };
      await records.addDiagnosis(
        recordId,
        const DiagnosisDraft(diagnosisName: 'Migraine'),
      );
      expect(
        adapter.requests.last.uri.path,
        '/api/v1/records/$recordId/diagnoses',
      );
      expect(adapter.requests.last.data, {
        'diagnosisName': 'Migraine',
        'description': null,
        'icdCode': null,
      });
      adapter.data = recordJson();
      await records.complete(
        recordId,
        disposition: 'OUTPATIENT_FOLLOW_UP',
        note: 'Review in 2 weeks',
      );
      expect(
        adapter.requests.last.uri.path,
        '/api/v1/records/$recordId/complete',
      );
      expect(adapter.requests.last.data, {
        'disposition': 'OUTPATIENT_FOLLOW_UP',
        'dispositionNote': 'Review in 2 weeks',
      });
    },
  );

  test(
    'clinical source order is mapped to Lab API using returned identities',
    () async {
      adapter.data = {
        'sourceOrderId': sourceOrderId,
        'recordId': recordId,
        'patientId': patientId,
        'requestingDoctorId': doctorId,
        'requestingDepartmentId': departmentId,
        'careEpisodeType': 'OUTPATIENT_VISIT',
        'careEpisodeId': appointmentId,
        'labType': 'CBC',
        'requestedDate': '2026-10-22',
        'priceCode': 'CBC-APPROVED',
        'orderedAt': '2026-10-22T08:00:00Z',
      };
      final source = await records.createClinicalLabOrder(
        recordId,
        const ClinicalLabOrderDraft(
          labType: ' CBC ',
          requestedDate: '2026-10-22',
          priceCode: ' CBC-APPROVED ',
        ),
      );
      expect(
        adapter.requests.single.uri.path,
        '/api/v1/records/$recordId/lab-orders',
      );
      expect(adapter.requests.single.data, {
        'labType': 'CBC',
        'requestedDate': '2026-10-22',
        'priceCode': 'CBC-APPROVED',
      });

      adapter.data = {'testId': 'test-opaque', 'status': 'PENDING'};
      final result = await records.createLabRequestFromSourceOrder(source);
      expect(result.testId, 'test-opaque');
      expect(adapter.requests.last.method, 'POST');
      expect(adapter.requests.last.uri.path, '/api/v1/lab');
      expect(adapter.requests.last.data, {
        'recordId': recordId,
        'patientId': patientId,
        'requestingDepartmentId': departmentId,
        'labType': 'CBC',
        'requestedDate': '2026-10-22',
        'sourceOrderId': sourceOrderId,
        'careEpisodeType': 'OUTPATIENT_VISIT',
        'careEpisodeId': appointmentId,
        'priceCode': 'CBC-APPROVED',
      });
      expect(adapter.requests, hasLength(2));
    },
  );

  test('wire errors keep backend code and are dispatched once', () async {
    adapter.status = 503;
    adapter.wrapSuccess = false;
    adapter.data = {
      'success': false,
      'error': {'code': 'CARE_GATE_CLOSED', 'message': 'Feature is disabled'},
      'correlationId': 'trace-clinical',
    };
    await expectLater(
      appointments.checkIn(appointmentId),
      throwsA(
        isA<ApiFailure>().having((e) => e.code, 'code', 'CARE_GATE_CLOSED'),
      ),
    );
    expect(adapter.requests, hasLength(1));
  });
}
