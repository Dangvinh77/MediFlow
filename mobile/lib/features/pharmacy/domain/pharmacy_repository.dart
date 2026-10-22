import '../../../core/domain/page_result.dart';
import 'drug.dart';
import 'prescription.dart';
import 'drug_commands.dart';
import 'prescription_commands.dart';

abstract interface class PharmacyRepository {
  Future<PageResult<Drug>> searchDrugs({
    String? keyword,
    int page = 0,
    int size = 20,
  });
  Future<Drug> getDrug(String id);
  Future<Prescription> getPrescription(String id);
  Future<Drug> createDrug(CreateDrug command);
  Future<Drug> adjustStock(String id, AdjustDrugStock command);
  Future<CancelPrescriptionResult> cancelPrescription(
    String id,
    CancelPrescription command,
  );
}

bool isPharmacyId(String value) => RegExp(
  r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$',
).hasMatch(value);

class FindDrugs {
  const FindDrugs(this.repository);
  final PharmacyRepository repository;
  Future<PageResult<Drug>> call({
    String? keyword,
    int page = 0,
    int size = 20,
  }) {
    if (page < 0 || size < 1 || size > 100) {
      throw ArgumentError('Trang hoặc kích thước trang không hợp lệ.');
    }
    final normalized = keyword?.trim();
    return repository.searchDrugs(
      keyword: normalized == null || normalized.isEmpty ? null : normalized,
      page: page,
      size: size,
    );
  }
}

class ReadDrug {
  const ReadDrug(this.repository);
  final PharmacyRepository repository;
  Future<Drug> call(String id) {
    if (!isPharmacyId(id)) throw ArgumentError('Mã thuốc phải là UUID hợp lệ.');
    return repository.getDrug(id);
  }
}

class ReadPrescription {
  const ReadPrescription(this.repository);
  final PharmacyRepository repository;
  Future<Prescription> call(String id) {
    if (!isPharmacyId(id)) {
      throw ArgumentError('Mã đơn thuốc phải là UUID hợp lệ.');
    }
    return repository.getPrescription(id);
  }
}
