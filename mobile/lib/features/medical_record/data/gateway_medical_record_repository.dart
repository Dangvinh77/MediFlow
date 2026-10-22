import '../../../core/network/api_client.dart';
import '../domain/medical_record.dart';
import 'medical_record_dto.dart';

class GatewayMedicalRecordRepository implements MedicalRecordRepository {
  const GatewayMedicalRecordRepository(this.client);
  final ApiClient client;

  @override
  Future<MedicalRecord> getById(String recordId) async {
    _requireId(recordId, 'recordId');
    return _readRecord(
      await client.get('/v1/records/${Uri.encodeComponent(recordId)}'),
      recordId,
    );
  }

  @override
  Future<List<MedicalRecord>> byPatient(String patientId) async {
    _requireId(patientId, 'patientId');
    final value = await client.get(
      '/v1/records/patient/${Uri.encodeComponent(patientId)}',
    );
    if (value is! List) {
      throw const FormatException('Phản hồi hồ sơ bệnh nhân không hợp lệ.');
    }
    final records = value
        .map((item) {
          if (item is! Map<String, dynamic>) {
            throw const FormatException('Dữ liệu hồ sơ không hợp lệ.');
          }
          final record = MedicalRecordDto.fromJson(item);
          if (record.patientId != patientId) {
            throw const FormatException(
              'Hồ sơ trả về không thuộc bệnh nhân đã chọn.',
            );
          }
          return record;
        })
        .toList(growable: false);
    return records;
  }

  @override
  Future<MedicalRecord> create(CreateMedicalRecord draft) async {
    final issue = validateRecordDraft(draft);
    if (issue != null) throw ArgumentError(issue);
    return _readRecord(
      await client.post('/v1/records', {
        'patientId': draft.patientId.trim(),
        'doctorId': draft.doctorId.trim(),
        'departmentId': draft.departmentId.trim(),
        'examinationDate': draft.examinationDate,
        'symptoms': _clean(draft.symptoms),
        'appointmentId': draft.appointmentId,
        'diagnoses': draft.diagnoses
            .map(_diagnosisBody)
            .toList(growable: false),
      }),
    );
  }

  @override
  Future<MedicalRecord> update(
    String recordId, {
    required String? symptoms,
  }) async {
    _requireId(recordId, 'recordId');
    if ((symptoms?.trim().length ?? 0) > 4000) {
      throw ArgumentError('Triệu chứng không được dài quá 4000 ký tự.');
    }
    return _readRecord(
      await client.put('/v1/records/${Uri.encodeComponent(recordId)}', {
        'symptoms': _clean(symptoms),
      }),
      recordId,
    );
  }

  @override
  Future<Diagnosis> addDiagnosis(
    String recordId,
    DiagnosisDraft diagnosis,
  ) async {
    _requireId(recordId, 'recordId');
    final issue = validateDiagnosis(diagnosis);
    if (issue != null) throw ArgumentError(issue);
    final value = await client.post(
      '/v1/records/${Uri.encodeComponent(recordId)}/diagnoses',
      _diagnosisBody(diagnosis),
    );
    if (value is! Map<String, dynamic>) {
      throw const FormatException('Phản hồi chẩn đoán không hợp lệ.');
    }
    final result = DiagnosisDto.fromJson(value);
    return result;
  }

  @override
  Future<MedicalRecord> complete(
    String recordId, {
    required String disposition,
    required String? note,
  }) async {
    _requireId(recordId, 'recordId');
    if (!const {
      'OUTPATIENT_FOLLOW_UP',
      'PRESCRIPTION',
      'OTHER',
    }.contains(disposition)) {
      throw ArgumentError.value(disposition, 'disposition');
    }
    if ((note?.trim().length ?? 0) > 2000) {
      throw ArgumentError('Ghi chú không được dài quá 2000 ký tự.');
    }
    return _readRecord(
      await client.put(
        '/v1/records/${Uri.encodeComponent(recordId)}/complete',
        {'disposition': disposition, 'dispositionNote': _clean(note)},
      ),
      recordId,
    );
  }

  @override
  Future<ClinicalLabOrder> createClinicalLabOrder(
    String recordId,
    ClinicalLabOrderDraft draft,
  ) async {
    _requireId(recordId, 'recordId');
    final issue = validateClinicalLabOrder(draft);
    if (issue != null) throw ArgumentError(issue);
    final value = await client.post(
      '/v1/records/${Uri.encodeComponent(recordId)}/lab-orders',
      {
        'labType': draft.labType.trim(),
        'requestedDate': draft.requestedDate,
        'priceCode': draft.priceCode.trim(),
      },
    );
    if (value is! Map<String, dynamic>) {
      throw const FormatException('Phản hồi chỉ định xét nghiệm không hợp lệ.');
    }
    final order = ClinicalLabOrderDto.fromJson(value);
    if (order.recordId != recordId ||
        order.careEpisodeType != 'OUTPATIENT_VISIT') {
      throw const FormatException(
        'Chỉ định trả về không khớp hồ sơ ngoại trú đã chọn.',
      );
    }
    return order;
  }

  @override
  Future<LabTestReceipt> createLabRequestFromSourceOrder(
    ClinicalLabOrder sourceOrder,
  ) async {
    _requireId(sourceOrder.sourceOrderId, 'sourceOrderId');
    _requireId(sourceOrder.recordId, 'recordId');
    _requireId(sourceOrder.patientId, 'patientId');
    _requireId(sourceOrder.requestingDepartmentId, 'requestingDepartmentId');
    _requireId(sourceOrder.careEpisodeId, 'careEpisodeId');
    if (sourceOrder.careEpisodeType != 'OUTPATIENT_VISIT' ||
        sourceOrder.labType.trim().isEmpty ||
        sourceOrder.priceCode.trim().isEmpty) {
      throw ArgumentError('Nguồn chỉ định không hợp lệ cho hồ sơ ngoại trú.');
    }
    final value = await client.post('/v1/lab', {
      'recordId': sourceOrder.recordId,
      'patientId': sourceOrder.patientId,
      'requestingDepartmentId': sourceOrder.requestingDepartmentId,
      'labType': sourceOrder.labType,
      'requestedDate': sourceOrder.requestedDate,
      'sourceOrderId': sourceOrder.sourceOrderId,
      'careEpisodeType': sourceOrder.careEpisodeType,
      'careEpisodeId': sourceOrder.careEpisodeId,
      'priceCode': sourceOrder.priceCode,
    });
    if (value is! Map<String, dynamic>) {
      throw const FormatException('Phản hồi tạo xét nghiệm không hợp lệ.');
    }
    final receipt = LabTestReceiptDto.fromJson(value);
    return receipt;
  }

  static MedicalRecord _readRecord(Object? value, [String? expectedId]) {
    if (value is! Map<String, dynamic>) {
      throw const FormatException('Phản hồi hồ sơ khám không hợp lệ.');
    }
    final record = MedicalRecordDto.fromJson(value);
    if (expectedId != null && record.id != expectedId) {
      throw const FormatException('Mã hồ sơ trả về không khớp yêu cầu.');
    }
    return record;
  }

  static Map<String, dynamic> _diagnosisBody(DiagnosisDraft draft) => {
    'diagnosisName': draft.diagnosisName.trim(),
    'description': _clean(draft.description),
    'icdCode': _clean(draft.icdCode),
  };

  static String? _clean(String? value) {
    final trimmed = value?.trim();
    return trimmed == null || trimmed.isEmpty ? null : trimmed;
  }

  static void _requireId(String id, String field) {
    if (!isMedicalRecordUuid(id)) throw ArgumentError.value(id, field);
  }
}
