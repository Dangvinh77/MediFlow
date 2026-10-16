package com.mediflow.report.infrastructure.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Opt-in management commands require an explicit, unambiguous JSON evidence decision. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name={"mediflow.features.care-finance-v2", "mediflow.report.financial-publication.enabled"}, havingValue="true")
@org.springframework.context.annotation.Profile("!mvp-outpatient & !outpatient-mvp")
public class ReportFinancialPublicationJsonConfiguration {
    @Bean Jackson2ObjectMapperBuilderCustomizer strictFinancialPublicationJson() {
        return builder -> builder.featuresToEnable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION,
                DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS,
                DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
}
