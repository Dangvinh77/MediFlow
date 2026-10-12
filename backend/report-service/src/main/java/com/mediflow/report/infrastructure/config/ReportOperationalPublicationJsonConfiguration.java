package com.mediflow.report.infrastructure.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name={"mediflow.features.care-finance-v2", "mediflow.report.operational-publication.enabled"}, havingValue="true")
public class ReportOperationalPublicationJsonConfiguration {
    @Bean Jackson2ObjectMapperBuilderCustomizer strictOperationalPublicationJson() {
        return builder -> builder.featuresToEnable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION,
                DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS, DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
}
