/// Client validation mirrors the public DTO; the server remains authoritative.
class PharmacyValidationException implements Exception {
  const PharmacyValidationException(this.fields);
  final Map<String, String> fields;
}

class CreateDrug {
  CreateDrug({
    required this.drugName,
    required this.unit,
    required this.price,
    required this.stockQuantity,
    required this.expiryDate,
    required this.lowStockThreshold,
    this.activeIngredient,
    this.manufacturer,
  });
  final String drugName, unit, price, expiryDate;
  final int stockQuantity;
  final int? lowStockThreshold;
  final String? activeIngredient, manufacturer;

  Map<String, String> errors({DateTime? today}) {
    final errors = <String, String>{};
    for (final field in {
      'drugName': (drugName, 150, true),
      'unit': (unit, 20, true),
      'activeIngredient': (activeIngredient ?? '', 150, false),
      'manufacturer': (manufacturer ?? '', 150, false),
    }.entries) {
      final (value, max, required) = field.value;
      if ((required && value.trim().isEmpty) || value.length > max) {
        errors[field.key] =
            'Nhập ${required ? 'từ 1 đến' : 'tối đa'} $max ký tự.';
      }
    }
    if (!RegExp(r'^\d{1,13}(\.\d{1,2})?$').hasMatch(price)) {
      errors['price'] =
          'Giá không âm, tối đa 13 chữ số nguyên và 2 số thập phân.';
    }
    for (final field in {
      'stockQuantity': stockQuantity,
      'lowStockThreshold': lowStockThreshold,
    }.entries) {
      if (field.value != null &&
          (field.value! < 0 || field.value! > 2147483647)) {
        errors[field.key] = 'Nhập số nguyên từ 0 đến 2147483647.';
      }
    }
    final date = DateTime.tryParse(expiryDate);
    final now = today ?? DateTime.now();
    if (!RegExp(r'^\d{4}-\d{2}-\d{2}$').hasMatch(expiryDate) ||
        date == null ||
        date.toIso8601String().substring(0, 10) != expiryDate ||
        date.isBefore(DateTime(now.year, now.month, now.day))) {
      errors['expiryDate'] = 'Nhập ngày YYYY-MM-DD hợp lệ, không ở quá khứ.';
    }
    return errors;
  }

  void validate() {
    final fields = errors();
    if (fields.isNotEmpty) throw PharmacyValidationException(fields);
  }
}

class AdjustDrugStock {
  const AdjustDrugStock({required this.quantity, this.reason});
  final int quantity;
  final String? reason;
  Map<String, String> errors() => {
    if (quantity == 0 || quantity < -2147483648 || quantity > 2147483647)
      'quantity':
          'Số điều chỉnh phải là số nguyên khác 0, trong giới hạn 32 bit.',
    if ((reason?.length ?? 0) > 255) 'reason': 'Lý do tối đa 255 ký tự.',
  };
  void validate() {
    final fields = errors();
    if (fields.isNotEmpty) throw PharmacyValidationException(fields);
  }
}
