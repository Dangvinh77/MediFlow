package com.mediflow.pharmacy.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Feature gate reserved for separately approved V2 activation; defaults to disabled. */
@ConfigurationProperties(prefix = "mediflow.features")
public record CareFinanceFeatureProperties(boolean careFinanceV2) {
}
