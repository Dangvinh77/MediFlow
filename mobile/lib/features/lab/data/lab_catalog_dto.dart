import '../domain/lab_catalog.dart';

abstract final class LabCatalogDto {
  static LabCatalog catalog(Object? raw) {
    if (raw is! List) {
      throw const FormatException('Danh mục xét nghiệm không hợp lệ.');
    }
    return LabCatalog(
      enabled: true,
      services: [for (final item in raw) _service(item)],
    );
  }

  static LabTestTemplate testTemplate(Object? raw, String expectedTestId) {
    final json = _map(raw, 'Mẫu kết quả xét nghiệm không hợp lệ.');
    final testId = _string(json, 'testId');
    if (testId != expectedTestId) {
      throw const FormatException('Mẫu kết quả không khớp xét nghiệm.');
    }
    final templateRaw = json['template'];
    return LabTestTemplate(
      testId: testId,
      serviceCode: _optionalString(json, 'serviceCode'),
      template: templateRaw == null ? null : _template(templateRaw),
    );
  }

  static LabCatalogService _service(Object? raw) {
    final json = _map(raw, 'Dịch vụ xét nghiệm không hợp lệ.');
    final turnaround = json['turnaroundHours'];
    if (turnaround != null && turnaround is! int) {
      throw const FormatException('Thời gian xét nghiệm không hợp lệ.');
    }
    final panel = json['panel'];
    if (panel is! bool) {
      throw const FormatException('Loại dịch vụ xét nghiệm không hợp lệ.');
    }
    return LabCatalogService(
      serviceCode: _string(json, 'serviceCode'),
      name: _string(json, 'name'),
      displayGroup: _string(json, 'displayGroup'),
      specimenType: _string(json, 'specimenType'),
      preparation: _optionalString(json, 'preparation'),
      turnaroundHours: turnaround as int?,
      priceCode: _string(json, 'priceCode'),
      templateCode: _string(json, 'templateCode'),
      panel: panel,
    );
  }

  static LabResultTemplate _template(Object? raw) {
    final json = _map(raw, 'Mẫu kết quả xét nghiệm không hợp lệ.');
    final version = json['version'];
    final fields = json['fields'];
    if (version is! int || fields is! List) {
      throw const FormatException('Mẫu kết quả xét nghiệm không hợp lệ.');
    }
    final parsedFields = [for (final field in fields) _field(field)]
      ..sort((a, b) {
        final order = a.position.compareTo(b.position);
        return order == 0 ? a.fieldCode.compareTo(b.fieldCode) : order;
      });
    return LabResultTemplate(
      templateCode: _string(json, 'templateCode'),
      version: version,
      name: _string(json, 'name'),
      fields: parsedFields,
    );
  }

  static LabTemplateField _field(Object? raw) {
    final json = _map(raw, 'Chỉ số xét nghiệm không hợp lệ.');
    final position = json['position'];
    final required = json['required'];
    final allowsOperator = json['allowsOperator'];
    final valueType = _string(json, 'valueType');
    if (position is! int ||
        required is! bool ||
        allowsOperator is! bool ||
        !const {
          'QUANTITATIVE',
          'QUALITATIVE',
          'SEMI_QUANTITATIVE',
          'TEXT',
        }.contains(valueType)) {
      throw const FormatException('Chỉ số xét nghiệm không hợp lệ.');
    }
    final rawOptions = json['allowedOptions'];
    List<String>? options;
    if (rawOptions != null) {
      if (rawOptions is! List || rawOptions.any((value) => value is! String)) {
        throw const FormatException('Lựa chọn kết quả không hợp lệ.');
      }
      options = [for (final value in rawOptions) value as String];
    }
    final rawRanges = json['ranges'];
    if (rawRanges is! List) {
      throw const FormatException('Khoảng tham chiếu không hợp lệ.');
    }
    return LabTemplateField(
      fieldCode: _string(json, 'fieldCode'),
      label: _string(json, 'label'),
      position: position,
      required: required,
      valueType: valueType,
      unit: _optionalString(json, 'unit'),
      allowedOptions: options,
      allowsOperator: allowsOperator,
      ranges: [for (final range in rawRanges) _range(range)],
    );
  }

  static LabReferenceRange _range(Object? raw) {
    final json = _map(raw, 'Khoảng tham chiếu không hợp lệ.');
    final reviewed = json['reviewed'];
    if (reviewed is! bool) {
      throw const FormatException('Khoảng tham chiếu không hợp lệ.');
    }
    return LabReferenceRange(
      sex: _optionalString(json, 'sex'),
      ageMinDays: _optionalInt(json, 'ageMinDays'),
      ageMaxDays: _optionalInt(json, 'ageMaxDays'),
      low: _optionalNumberText(json, 'low'),
      high: _optionalNumberText(json, 'high'),
      criticalLow: _optionalNumberText(json, 'criticalLow'),
      criticalHigh: _optionalNumberText(json, 'criticalHigh'),
      reviewed: reviewed,
    );
  }

  static Map<String, dynamic> _map(Object? raw, String message) {
    if (raw is! Map) throw FormatException(message);
    return Map<String, dynamic>.from(raw);
  }

  static String _string(Map<String, dynamic> json, String key) {
    final value = json[key];
    if (value is! String || value.trim().isEmpty) {
      throw FormatException('Dữ liệu $key không hợp lệ.');
    }
    return value;
  }

  static String? _optionalString(Map<String, dynamic> json, String key) {
    final value = json[key];
    if (value == null) return null;
    if (value is! String) {
      throw FormatException('Dữ liệu $key không hợp lệ.');
    }
    return value;
  }

  static int? _optionalInt(Map<String, dynamic> json, String key) {
    final value = json[key];
    if (value == null) return null;
    if (value is! int) throw FormatException('Dữ liệu $key không hợp lệ.');
    return value;
  }

  static String? _optionalNumberText(Map<String, dynamic> json, String key) {
    final value = json[key];
    if (value == null) return null;
    if (value is! num && value is! String) {
      throw FormatException('Dữ liệu $key không hợp lệ.');
    }
    return value.toString();
  }
}
