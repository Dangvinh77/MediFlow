import '../../../core/domain/page_result.dart';

class PatientLabValue {
  const PatientLabValue(
    this.id,
    this.indicator,
    this.value,
    this.unit,
    this.referenceRange,
  );
  final String id, indicator, value;
  final String? unit, referenceRange;
}

class PatientLabResult {
  const PatientLabResult({
    required this.id,
    required this.recordId,
    required this.labType,
    required this.requestedDate,
    required this.performedDate,
    required this.results,
    this.conclusion,
  });
  final String id, recordId, labType, requestedDate, performedDate;
  final String? conclusion;
  final List<PatientLabValue> results;
}

abstract interface class PatientLabResultsRepository {
  Future<PageResult<PatientLabResult>> mine({int page = 0});
}

class ReadMyLabResults {
  const ReadMyLabResults(this.repository);
  final PatientLabResultsRepository repository;
  Future<PageResult<PatientLabResult>> call(int page) {
    if (page < 0) throw ArgumentError.value(page, 'page');
    return repository.mine(page: page);
  }
}
