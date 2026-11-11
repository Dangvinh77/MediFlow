class LabCatalog {
  const LabCatalog({required this.enabled, this.services = const []});

  final bool enabled;
  final List<LabCatalogService> services;
}

class LabCatalogService {
  const LabCatalogService({
    required this.serviceCode,
    required this.name,
    required this.displayGroup,
    required this.specimenType,
    required this.priceCode,
    required this.templateCode,
    required this.panel,
    this.preparation,
    this.turnaroundHours,
  });

  final String serviceCode;
  final String name;
  final String displayGroup;
  final String specimenType;
  final String? preparation;
  final int? turnaroundHours;
  final String priceCode;
  final String templateCode;
  final bool panel;
}

class LabTestTemplate {
  const LabTestTemplate({
    required this.testId,
    required this.serviceCode,
    required this.template,
  });

  final String testId;
  final String? serviceCode;
  final LabResultTemplate? template;
}

class LabResultTemplate {
  const LabResultTemplate({
    required this.templateCode,
    required this.version,
    required this.name,
    required this.fields,
  });

  final String templateCode;
  final int version;
  final String name;
  final List<LabTemplateField> fields;
}

class LabTemplateField {
  const LabTemplateField({
    required this.fieldCode,
    required this.label,
    required this.position,
    required this.required,
    required this.valueType,
    required this.unit,
    required this.allowedOptions,
    required this.allowsOperator,
    required this.ranges,
  });

  final String fieldCode;
  final String label;
  final int position;
  final bool required;
  final String valueType;
  final String? unit;
  final List<String>? allowedOptions;
  final bool allowsOperator;
  final List<LabReferenceRange> ranges;
}

class LabReferenceRange {
  const LabReferenceRange({
    this.sex,
    this.ageMinDays,
    this.ageMaxDays,
    this.low,
    this.high,
    this.criticalLow,
    this.criticalHigh,
    required this.reviewed,
  });

  final String? sex;
  final int? ageMinDays;
  final int? ageMaxDays;
  final String? low;
  final String? high;
  final String? criticalLow;
  final String? criticalHigh;
  final bool reviewed;
}

/// Optional Lab capabilities are kept separate so older workflow fakes and
/// legacy implementations continue to support the free-row form.
abstract interface class LabCatalogReader {
  Future<LabCatalog> catalog({String? group});
  Future<LabTestTemplate> template(String testId);
}
