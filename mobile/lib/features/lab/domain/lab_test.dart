import '../../../core/domain/page_result.dart';

class LabTest {
  const LabTest({
    required this.id,
    required this.patientId,
    required this.recordId,
    required this.type,
    required this.requestedDate,
    required this.status,
    required this.paid,
  });
  final String id, patientId, recordId, type, requestedDate, status;
  final bool paid;
}

abstract interface class LabRepository {
  Future<PageResult<LabTest>> search({int page = 0, String? status});
}

class FindLabTests {
  const FindLabTests(this.repository);
  final LabRepository repository;
  static const statuses = [
    'PENDING',
    'AWAITING_PAYMENT',
    'READY',
    'IN_PROGRESS',
    'COMPLETED',
    'CANCELLED',
  ];
  Future<PageResult<LabTest>> call({int page = 0, String? status}) {
    if (page < 0 || (status != null && !statuses.contains(status))) {
      throw ArgumentError('Bộ lọc xét nghiệm không hợp lệ.');
    }
    return repository.search(page: page, status: status);
  }
}
