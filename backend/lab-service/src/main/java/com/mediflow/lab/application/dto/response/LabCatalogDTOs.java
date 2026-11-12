package com.mediflow.lab.application.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.mediflow.lab.domain.model.LabReferenceRange;
import com.mediflow.lab.domain.model.LabResultTemplate;
import com.mediflow.lab.domain.model.LabServiceCatalogItem;
import com.mediflow.lab.domain.model.LabTemplateField;

/** Wire shapes of the v2.0 Lab catalogue. */
public final class LabCatalogDTOs {
    private LabCatalogDTOs() {
    }

    public record LabCatalogItemDTO(String serviceCode, String name, String displayGroup, String specimenType,
            String preparation, Integer turnaroundHours, String priceCode, String templateCode, boolean panel) {
        public static LabCatalogItemDTO of(LabServiceCatalogItem item) {
            return new LabCatalogItemDTO(item.serviceCode(), item.name(), item.displayGroup(), item.specimenType(),
                    item.preparation(), item.turnaroundHours(), item.priceCode(), item.templateCode(), item.panel());
        }
    }

    public record LabReferenceRangeDTO(String sex, Integer ageMinDays, Integer ageMaxDays, BigDecimal low,
            BigDecimal high, BigDecimal criticalLow, BigDecimal criticalHigh, boolean reviewed) {
        public static LabReferenceRangeDTO of(LabReferenceRange range) {
            return new LabReferenceRangeDTO(range.sex(), range.ageMinDays(), range.ageMaxDays(), range.low(),
                    range.high(), range.criticalLow(), range.criticalHigh(), range.reviewed());
        }
    }

    public record LabTemplateFieldDTO(String fieldCode, String label, int position, boolean required, String valueType,
            String unit, List<String> allowedOptions, boolean allowsOperator, List<LabReferenceRangeDTO> ranges) {
        public static LabTemplateFieldDTO of(LabTemplateField field) {
            return new LabTemplateFieldDTO(field.fieldCode(), field.label(), field.position(), field.required(),
                    field.valueType().name(), field.unit(), field.allowedOptions(), field.allowsOperator(),
                    field.ranges().stream().map(LabReferenceRangeDTO::of).toList());
        }
    }

    public record LabTemplateDTO(String templateCode, int version, String name, List<LabTemplateFieldDTO> fields) {
        public static LabTemplateDTO of(LabResultTemplate template) {
            return new LabTemplateDTO(template.templateCode(), template.version(), template.name(),
                    template.fields().stream().map(LabTemplateFieldDTO::of).toList());
        }
    }

    /** The template a test was created with; {@code template} is null for a test created without the catalogue. */
    public record LabTestTemplateDTO(UUID testId, String serviceCode, LabTemplateDTO template) {
    }
}
