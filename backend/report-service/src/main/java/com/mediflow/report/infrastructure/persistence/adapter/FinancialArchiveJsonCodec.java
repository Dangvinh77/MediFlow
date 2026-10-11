package com.mediflow.report.infrastructure.persistence.adapter;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mediflow.report.application.port.out.FinancialArchiveCodecPort;
import com.mediflow.report.domain.exception.ReportRuleException;
import com.mediflow.report.domain.model.FinancialEvidenceArchive;
import org.springframework.stereotype.Component;

@Component
public class FinancialArchiveJsonCodec implements FinancialArchiveCodecPort {
    private final ObjectMapper mapper = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(20).maxStringLength(2 * 1024 * 1024).build())
            .build()).findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    @Override public byte[] encode(FinancialEvidenceArchive archive) {
        try {
            byte[] bytes = mapper.writeValueAsBytes(archive);
            if (bytes.length > FinancialEvidenceArchive.MAX_WIRE_BYTES) throw invalid();
            return bytes;
        } catch (java.io.IOException error) { throw invalid(); }
    }
    @Override public FinancialEvidenceArchive decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > FinancialEvidenceArchive.MAX_WIRE_BYTES) throw invalid();
        try { return mapper.readValue(bytes, FinancialEvidenceArchive.class); }
        catch (java.io.IOException | IllegalArgumentException error) { throw invalid(); }
    }
    private static ReportRuleException invalid() {
        return new ReportRuleException("REPORT_FINANCIAL_ARCHIVE_INVALID", "Invalid or damaged bounded financial evidence archive");
    }
}
