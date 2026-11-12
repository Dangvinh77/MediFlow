import '../../../core/domain/page_result.dart';

class PatientDiagnosis {
  const PatientDiagnosis(this.id, this.name, this.description, this.icdCode);
  final String id, name;
  final String? description, icdCode;
}

class PatientEffectiveCorrection {
  const PatientEffectiveCorrection({
    required this.section,
    required this.originalText,
    required this.correctedText,
    required this.reason,
    required this.appliedAt,
  });

  final String section, originalText, correctedText, reason, appliedAt;
}

/// Released patient summary, deliberately separate from the staff record model.
class PatientExamResult {
  const PatientExamResult({
    required this.id,
    required this.doctorId,
    required this.departmentId,
    required this.examinationDate,
    required this.completedAt,
    required this.diagnoses,
    this.symptoms,
    this.disposition,
    this.dispositionNote,
    this.effectiveCorrections = const [],
  });
  final String id, doctorId, departmentId, examinationDate, completedAt;
  final String? symptoms, disposition, dispositionNote;
  final List<PatientDiagnosis> diagnoses;
  final List<PatientEffectiveCorrection> effectiveCorrections;
}

abstract interface class PatientExamResultsRepository {
  Future<PageResult<PatientExamResult>> mine({int page = 0});
}

class ReadMyExamResults {
  const ReadMyExamResults(this.repository);
  final PatientExamResultsRepository repository;
  Future<PageResult<PatientExamResult>> call(int page) {
    if (page < 0) throw ArgumentError.value(page, 'page');
    return repository.mine(page: page);
  }
}
